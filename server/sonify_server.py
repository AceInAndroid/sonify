#!/usr/bin/env python3
"""
Sonify Release Update & Crash Diagnostics Server
零第三方依赖（纯 Python 3 标准库），适配腾讯云上海四区 AgentWeb (43.142.250.231 / sonify.zhangbing.tech)。

Endpoints:
  GET  /api/v1/health
  GET  /api/v1/update/latest?channel=stable&abi=arm64-v8a&versionCode=2
  POST /api/v1/crash/report
  GET  /releases/<apk-file>  (supports HTTP Range)
"""
import argparse
import collections
import datetime
import http.server
import json
import os
import re
import time
import urllib.parse

MAX_CRASH_BODY = 64 * 1024
RATE_LIMIT_WINDOW = 60.0
RATE_LIMIT_MAX = 30
SAFE_FILENAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")


class RateLimiter:
    def __init__(self, max_req: int, window_sec: float):
        self.max_req = max_req
        self.window_sec = window_sec
        self.buckets = collections.defaultdict(collections.deque)

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        q = self.buckets[key]
        while q and (now - q[0]) > self.window_sec:
            q.popleft()
        if len(q) >= self.max_req:
            return False
        q.append(now)
        return True


def make_handler(data_dir: str, releases_dir: str, limiter: RateLimiter):
    crashes_dir = os.path.join(data_dir, "crashes")
    os.makedirs(crashes_dir, exist_ok=True)
    os.makedirs(releases_dir, exist_ok=True)

    class SonifyHandler(http.server.BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"[{ts}] {self.client_address[0]} {fmt % args}", flush=True)

        def _client_ip(self) -> str:
            fwd = self.headers.get("X-Forwarded-For", "").split(",")[0].strip()
            return fwd or self.client_address[0]

        def _send_json(self, status: int, obj: dict):
            raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            qs = urllib.parse.parse_qs(parsed.query)

            if path == "/api/v1/health":
                self._send_json(200, {
                    "status": "ok",
                    "service": "sonify-server",
                    "time": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                })
                return

            if path == "/api/v1/update/latest":
                channel = (qs.get("channel") or ["stable"])[0].strip().lower()
                if not re.match(r"^[a-z0-9_-]{1,20}$", channel):
                    channel = "stable"
                candidates = [
                    os.path.join(data_dir, f"latest_{channel}.json"),
                    os.path.join(data_dir, "latest.json"),
                ]
                manifest_path = next((p for p in candidates if os.path.isfile(p)), None)
                if not manifest_path:
                    self._send_json(404, {"error": "no_release_manifest", "message": "尚未发布任何版本"})
                    return
                try:
                    with open(manifest_path, "r", encoding="utf-8") as f:
                        manifest = json.load(f)
                    self._send_json(200, manifest)
                except Exception as exc:
                    self._send_json(500, {"error": "manifest_read_error", "message": str(exc)})
                return

            if path.startswith("/releases/"):
                fname = path[len("/releases/"):]
                if not SAFE_FILENAME_RE.match(fname):
                    self._send_json(400, {"error": "invalid_filename"})
                    return
                fpath = os.path.join(releases_dir, fname)
                if not os.path.isfile(fpath):
                    self._send_json(404, {"error": "not_found"})
                    return
                self._serve_file_with_range(fpath)
                return

            self._send_json(404, {"error": "not_found"})

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path != "/api/v1/crash/report":
                self._send_json(404, {"error": "not_found"})
                return

            if not limiter.allow(self._client_ip()):
                self._send_json(429, {"error": "rate_limited"})
                return

            length = int(self.headers.get("Content-Length", "0") or "0")
            if length <= 0 or length > MAX_CRASH_BODY:
                self._send_json(413 if length > MAX_CRASH_BODY else 400, {"error": "invalid_payload_size"})
                return

            raw = self.rfile.read(length)
            try:
                payload = json.loads(raw.decode("utf-8"))
                if not isinstance(payload, dict):
                    raise ValueError("JSON root must be an object")
            except Exception as exc:
                self._send_json(400, {"error": "invalid_json", "message": str(exc)})
                return

            payload["receivedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            payload["clientIp"] = self._client_ip()
            day = datetime.datetime.now().strftime("%Y-%m-%d")
            out_file = os.path.join(crashes_dir, f"crashes-{day}.jsonl")
            with open(out_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(payload, ensure_ascii=False) + "\n")

            self._send_json(200, {"ok": True, "id": payload.get("id", "")})

        def _serve_file_with_range(self, fpath: str):
            total = os.path.getsize(fpath)
            range_hdr = self.headers.get("Range")
            start, end = 0, total - 1
            status = 200
            if range_hdr and range_hdr.startswith("bytes="):
                spec = range_hdr[6:].split(",")[0].strip()
                parts = spec.split("-")
                try:
                    if parts[0]:
                        start = int(parts[0])
                    if len(parts) > 1 and parts[1]:
                        end = min(int(parts[1]), total - 1)
                    if start > end or start >= total:
                        self.send_response(416)
                        self.send_header("Content-Range", f"bytes */{total}")
                        self.end_headers()
                        return
                    status = 206
                except ValueError:
                    start, end, status = 0, total - 1, 200

            length = end - start + 1
            self.send_response(status)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(length))
            if status == 206:
                self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
            self.end_headers()

            with open(fpath, "rb") as f:
                f.seek(start)
                remaining = length
                while remaining > 0:
                    chunk = f.read(min(65536, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)

    return SonifyHandler


def main():
    parser = argparse.ArgumentParser(description="Sonify Update & Crash Server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--data-dir", default="/var/lib/sonify-server")
    parser.add_argument("--releases-dir", default=None)
    args = parser.parse_args()

    releases_dir = args.releases_dir or os.path.join(args.data_dir, "releases")
    os.makedirs(args.data_dir, exist_ok=True)
    os.makedirs(releases_dir, exist_ok=True)
    limiter = RateLimiter(RATE_LIMIT_MAX, RATE_LIMIT_WINDOW)
    server = http.server.ThreadingHTTPServer(
        (args.host, args.port),
        make_handler(args.data_dir, releases_dir, limiter),
    )
    print(f"Sonify server listening on http://{args.host}:{args.port} (data={args.data_dir})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
