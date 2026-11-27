#!/usr/bin/env python3
"""
Sonify Release Update, Crash Diagnostics & Privacy-First Device Analytics Server
零第三方依赖（纯 Python 3 标准库 + sqlite3 WAL），适配腾讯云上海四区 AgentWeb (43.142.250.231 / sonify.zhangbing.tech)。

Public Endpoints:
  GET  /api/v1/health
  GET  /api/v1/update/latest?channel=stable&abi=arm64-v8a&versionCode=2
  POST /api/v1/crash/report
  POST /api/v1/telemetry/events
  GET  /releases/<apk-file>                (supports HTTP Range)

Admin Endpoints (Bearer token or signed HttpOnly session cookie):
  GET  /admin                              (Spotify-Dark interactive DAU & Ops Dashboard)
  POST /api/v1/admin/login
  POST /api/v1/admin/logout
  GET  /api/v1/admin/overview?days=14
  GET  /api/v1/admin/crashes?limit=30
  POST /api/v1/admin/release/policy
"""
import argparse
import collections
import datetime
import hashlib
import hmac
import http.cookies
import http.server
import json
import os
import re
import sqlite3
import threading
import time
import urllib.parse

MAX_CRASH_BODY = 64 * 1024
MAX_TELEMETRY_BODY = 64 * 1024
MAX_ADMIN_BODY = 32 * 1024
MAX_EVENTS_PER_BATCH = 50
EVENT_RETENTION_DAYS = 90
RATE_LIMIT_WINDOW = 60.0
RATE_LIMIT_MAX = 40
LOGIN_RATE_LIMIT_MAX = 10
SESSION_TTL_SEC = 7 * 24 * 3600
SESSION_COOKIE_NAME = "sonify_admin_session"

SAFE_FILENAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
SAFE_EVENT_RE = re.compile(r"^[a-z][a-z0-9_]{1,47}$")
SAFE_UUID_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
FORBIDDEN_PROP_KEYS = frozenset({
    "path", "filepath", "file_path", "url", "uri", "host", "server_url",
    "password", "pass", "token", "salt", "pin", "secret", "cookie",
    "query", "keyword", "search_text", "title", "track_title", "filename",
    "imei", "oaid", "android_id", "mac", "phone", "email", "username",
})
BJ_TZ = datetime.timezone(datetime.timedelta(hours=8))


def bj_day_str(ts_ms: int | None = None) -> str:
    if ts_ms is None:
        dt = datetime.datetime.now(BJ_TZ)
    else:
        dt = datetime.datetime.fromtimestamp(ts_ms / 1000.0, tz=BJ_TZ)
    return dt.strftime("%Y-%m-%d")


def sanitize_props(raw_props) -> dict:
    if not isinstance(raw_props, dict):
        return {}
    clean = {}
    for k, v in list(raw_props.items())[:20]:
        key = str(k).strip().lower()
        if not key or len(key) > 40 or key in FORBIDDEN_PROP_KEYS:
            continue
        if isinstance(v, (bool, int, float)):
            clean[key] = v
        elif isinstance(v, str):
            s = v.strip()[:120]
            if "/storage/" in s or "/sdcard/" in s or "http://" in s or "https://" in s:
                continue
            clean[key] = s
    return clean


class RateLimiter:
    def __init__(self, max_req: int, window_sec: float):
        self.max_req = max_req
        self.window_sec = window_sec
        self.buckets = collections.defaultdict(collections.deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            q = self.buckets[key]
            while q and (now - q[0]) > self.window_sec:
                q.popleft()
            if len(q) >= self.max_req:
                return False
            q.append(now)
            return True


class AnalyticsStore:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._lock = threading.Lock()
        self._last_prune_day = ""
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=10.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        return conn

    def _init_db(self):
        with self._lock:
            with self._connect() as conn:
                conn.executescript(
                    """
                    CREATE TABLE IF NOT EXISTS devices (
                        device_id TEXT PRIMARY KEY,
                        first_seen_at INTEGER NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        first_seen_day TEXT NOT NULL,
                        last_seen_day TEXT NOT NULL,
                        app_version TEXT NOT NULL DEFAULT '',
                        version_code INTEGER NOT NULL DEFAULT 0,
                        device_model TEXT NOT NULL DEFAULT '',
                        manufacturer TEXT NOT NULL DEFAULT '',
                        android_sdk INTEGER NOT NULL DEFAULT 0,
                        abi TEXT NOT NULL DEFAULT '',
                        last_dac TEXT NOT NULL DEFAULT '',
                        channel TEXT NOT NULL DEFAULT 'stable'
                    );

                    CREATE TABLE IF NOT EXISTS daily_active (
                        day TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        app_version TEXT NOT NULL DEFAULT '',
                        version_code INTEGER NOT NULL DEFAULT 0,
                        last_dac TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY (day, device_id)
                    );
                    CREATE INDEX IF NOT EXISTS idx_daily_active_day ON daily_active(day);

                    CREATE TABLE IF NOT EXISTS events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ts_ms INTEGER NOT NULL,
                        day TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        event_name TEXT NOT NULL,
                        app_version TEXT NOT NULL DEFAULT '',
                        version_code INTEGER NOT NULL DEFAULT 0,
                        props_json TEXT NOT NULL DEFAULT '{}'
                    );
                    CREATE INDEX IF NOT EXISTS idx_events_day_name ON events(day, event_name);
                    CREATE INDEX IF NOT EXISTS idx_events_device ON events(device_id);
                    """
                )

    def ingest_batch(self, device: dict, events: list) -> int:
        install_id = str(device.get("installId") or "").strip()
        if not SAFE_UUID_RE.match(install_id):
            raise ValueError("invalid installId")

        now_ms = int(time.time() * 1000)
        today = bj_day_str(now_ms)
        app_ver = str(device.get("appVersion") or "").strip()[:32]
        ver_code = int(device.get("versionCode") or 0)
        model = str(device.get("deviceModel") or "").strip()[:80]
        manufacturer = str(device.get("manufacturer") or "").strip()[:60]
        sdk = int(device.get("androidSdk") or 0)
        abi = str(device.get("abi") or "").strip()[:32]
        dac = str(device.get("usbDac") or "").strip()[:80]
        channel = str(device.get("channel") or "stable").strip()[:24]

        accepted_rows = []
        for ev in events[:MAX_EVENTS_PER_BATCH]:
            if not isinstance(ev, dict):
                continue
            name = str(ev.get("name") or "").strip().lower()
            if not SAFE_EVENT_RE.match(name):
                continue
            ts_ms = int(ev.get("tsMs") or now_ms)
            # Clamp unreasonable timestamps (older than 14 days or > 1 day in future)
            if abs(now_ms - ts_ms) > 14 * 86400 * 1000:
                ts_ms = now_ms
            ev_day = bj_day_str(ts_ms)
            props = sanitize_props(ev.get("props"))
            if name == "usb_dac_connected" and props.get("dac_name"):
                dac = str(props["dac_name"])[:80]
            accepted_rows.append((ts_ms, ev_day, install_id, name, app_ver, ver_code, json.dumps(props, ensure_ascii=False)))

        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO devices (
                        device_id, first_seen_at, last_seen_at, first_seen_day, last_seen_day,
                        app_version, version_code, device_model, manufacturer, android_sdk, abi, last_dac, channel
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(device_id) DO UPDATE SET
                        last_seen_at = excluded.last_seen_at,
                        last_seen_day = excluded.last_seen_day,
                        app_version = CASE WHEN excluded.app_version != '' THEN excluded.app_version ELSE devices.app_version END,
                        version_code = CASE WHEN excluded.version_code > 0 THEN excluded.version_code ELSE devices.version_code END,
                        device_model = CASE WHEN excluded.device_model != '' THEN excluded.device_model ELSE devices.device_model END,
                        manufacturer = CASE WHEN excluded.manufacturer != '' THEN excluded.manufacturer ELSE devices.manufacturer END,
                        android_sdk = CASE WHEN excluded.android_sdk > 0 THEN excluded.android_sdk ELSE devices.android_sdk END,
                        abi = CASE WHEN excluded.abi != '' THEN excluded.abi ELSE devices.abi END,
                        last_dac = CASE WHEN excluded.last_dac != '' THEN excluded.last_dac ELSE devices.last_dac END
                    """,
                    (install_id, now_ms, now_ms, today, today, app_ver, ver_code, model, manufacturer, sdk, abi, dac, channel),
                )

                active_days = {today} | {r[1] for r in accepted_rows}
                for d in active_days:
                    conn.execute(
                        """
                        INSERT INTO daily_active (day, device_id, app_version, version_code, last_dac)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(day, device_id) DO UPDATE SET
                            app_version = CASE WHEN excluded.app_version != '' THEN excluded.app_version ELSE daily_active.app_version END,
                            version_code = CASE WHEN excluded.version_code > 0 THEN excluded.version_code ELSE daily_active.version_code END,
                            last_dac = CASE WHEN excluded.last_dac != '' THEN excluded.last_dac ELSE daily_active.last_dac END
                        """,
                        (d, install_id, app_ver, ver_code, dac),
                    )

                if accepted_rows:
                    conn.executemany(
                        """
                        INSERT INTO events (ts_ms, day, device_id, event_name, app_version, version_code, props_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        accepted_rows,
                    )

                # Daily auto-prune of raw events older than 90 days
                if self._last_prune_day != today:
                    cutoff_day = (datetime.datetime.now(BJ_TZ) - datetime.timedelta(days=EVENT_RETENTION_DAYS)).strftime("%Y-%m-%d")
                    conn.execute("DELETE FROM events WHERE day < ?", (cutoff_day,))
                    self._last_prune_day = today

        return len(accepted_rows)

    def _retention_rate(self, conn: sqlite3.Connection, offset_days: int, lookback_days: int = 14) -> dict:
        today_dt = datetime.datetime.now(BJ_TZ).date()
        cohort_end = today_dt - datetime.timedelta(days=offset_days)
        cohort_start = cohort_end - datetime.timedelta(days=lookback_days - 1)
        cohort_rows = conn.execute(
            "SELECT device_id, first_seen_day FROM devices WHERE first_seen_day BETWEEN ? AND ?",
            (cohort_start.isoformat(), cohort_end.isoformat()),
        ).fetchall()
        total = len(cohort_rows)
        if total == 0:
            return {"rate": None, "retained": 0, "cohort": 0}
        retained = 0
        for row in cohort_rows:
            d0 = datetime.date.fromisoformat(row["first_seen_day"])
            target_day = (d0 + datetime.timedelta(days=offset_days)).isoformat()
            hit = conn.execute(
                "SELECT 1 FROM daily_active WHERE day = ? AND device_id = ? LIMIT 1",
                (target_day, row["device_id"]),
            ).fetchone()
            if hit:
                retained += 1
        return {"rate": round(retained * 100.0 / total, 1), "retained": retained, "cohort": total}

    def query_overview(self, days: int, crashes_dir: str, manifest: dict | None) -> dict:
        days = max(7, min(90, days))
        now_bj = datetime.datetime.now(BJ_TZ).date()
        today = now_bj.isoformat()
        yesterday = (now_bj - datetime.timedelta(days=1)).isoformat()
        day_7 = (now_bj - datetime.timedelta(days=6)).isoformat()
        day_30 = (now_bj - datetime.timedelta(days=29)).isoformat()
        range_start = (now_bj - datetime.timedelta(days=days - 1)).isoformat()

        with self._lock:
            with self._connect() as conn:
                dau_today = conn.execute("SELECT COUNT(*) FROM daily_active WHERE day = ?", (today,)).fetchone()[0]
                dau_yesterday = conn.execute("SELECT COUNT(*) FROM daily_active WHERE day = ?", (yesterday,)).fetchone()[0]
                new_today = conn.execute("SELECT COUNT(*) FROM devices WHERE first_seen_day = ?", (today,)).fetchone()[0]
                wau = conn.execute("SELECT COUNT(DISTINCT device_id) FROM daily_active WHERE day >= ?", (day_7,)).fetchone()[0]
                mau = conn.execute("SELECT COUNT(DISTINCT device_id) FROM daily_active WHERE day >= ?", (day_30,)).fetchone()[0]
                total_devices = conn.execute("SELECT COUNT(*) FROM devices").fetchone()[0]

                d1_ret = self._retention_rate(conn, 1)
                d7_ret = self._retention_rate(conn, 7)

                dau_map = {
                    r["day"]: r["cnt"]
                    for r in conn.execute(
                        "SELECT day, COUNT(*) AS cnt FROM daily_active WHERE day >= ? GROUP BY day",
                        (range_start,),
                    ).fetchall()
                }
                new_map = {
                    r["first_seen_day"]: r["cnt"]
                    for r in conn.execute(
                        "SELECT first_seen_day, COUNT(*) AS cnt FROM devices WHERE first_seen_day >= ? GROUP BY first_seen_day",
                        (range_start,),
                    ).fetchall()
                }

                trend = []
                for i in range(days):
                    d_str = (now_bj - datetime.timedelta(days=days - 1 - i)).isoformat()
                    trend.append({
                        "day": d_str,
                        "dau": dau_map.get(d_str, 0),
                        "newDevices": new_map.get(d_str, 0),
                    })

                versions = [
                    {"version": f"v{r['app_version'] or '?'} ({r['version_code']})", "versionCode": r["version_code"], "devices": r["cnt"]}
                    for r in conn.execute(
                        """
                        SELECT app_version, version_code, COUNT(*) AS cnt
                        FROM devices
                        WHERE last_seen_day >= ?
                        GROUP BY app_version, version_code
                        ORDER BY version_code DESC, cnt DESC
                        LIMIT 12
                        """,
                        (day_30,),
                    ).fetchall()
                ]

                sdks = [
                    {"sdk": r["android_sdk"], "devices": r["cnt"]}
                    for r in conn.execute(
                        """
                        SELECT android_sdk, COUNT(*) AS cnt
                        FROM devices
                        WHERE last_seen_day >= ? AND android_sdk > 0
                        GROUP BY android_sdk
                        ORDER BY cnt DESC
                        LIMIT 10
                        """,
                        (day_30,),
                    ).fetchall()
                ]

                models = [
                    {"model": (f"{r['manufacturer']} {r['device_model']}".strip() or "Unknown"), "devices": r["cnt"]}
                    for r in conn.execute(
                        """
                        SELECT manufacturer, device_model, COUNT(*) AS cnt
                        FROM devices
                        WHERE last_seen_day >= ?
                        GROUP BY manufacturer, device_model
                        ORDER BY cnt DESC
                        LIMIT 10
                        """,
                        (day_30,),
                    ).fetchall()
                ]

                dacs = [
                    {"dac": r["last_dac"], "devices": r["cnt"]}
                    for r in conn.execute(
                        """
                        SELECT last_dac, COUNT(*) AS cnt
                        FROM devices
                        WHERE last_seen_day >= ? AND last_dac != '' AND lower(last_dac) != 'none'
                        GROUP BY last_dac
                        ORDER BY cnt DESC
                        LIMIT 10
                        """,
                        (day_30,),
                    ).fetchall()
                ]

                top_events = [
                    {"name": r["event_name"], "count": r["cnt"], "devices": r["dev_cnt"]}
                    for r in conn.execute(
                        """
                        SELECT event_name, COUNT(*) AS cnt, COUNT(DISTINCT device_id) AS dev_cnt
                        FROM events
                        WHERE day >= ?
                        GROUP BY event_name
                        ORDER BY cnt DESC
                        LIMIT 20
                        """,
                        (day_7,),
                    ).fetchall()
                ]

                recent_devices = [
                    {
                        "deviceId": r["device_id"][:8],
                        "model": f"{r['manufacturer']} {r['device_model']}".strip(),
                        "sdk": r["android_sdk"],
                        "version": f"v{r['app_version']} ({r['version_code']})",
                        "dac": r["last_dac"] or "本机输出",
                        "lastSeenDay": r["last_seen_day"],
                    }
                    for r in conn.execute(
                        """
                        SELECT device_id, manufacturer, device_model, android_sdk, app_version, version_code, last_dac, last_seen_day
                        FROM devices
                        ORDER BY last_seen_at DESC
                        LIMIT 15
                        """
                    ).fetchall()
                ]

        crashes_today = count_crashes_for_day(crashes_dir, today)
        return {
            "generatedAt": datetime.datetime.now(BJ_TZ).isoformat(),
            "days": days,
            "kpi": {
                "dauToday": dau_today,
                "dauYesterday": dau_yesterday,
                "newToday": new_today,
                "wau": wau,
                "mau": mau,
                "totalDevices": total_devices,
                "crashesToday": crashes_today,
                "d1Retention": d1_ret,
                "d7Retention": d7_ret,
            },
            "trend": trend,
            "versions": versions,
            "sdks": sdks,
            "models": models,
            "dacs": dacs,
            "topEvents": top_events,
            "recentDevices": recent_devices,
            "release": manifest,
        }


def count_crashes_for_day(crashes_dir: str, day: str) -> int:
    fpath = os.path.join(crashes_dir, f"crashes-{day}.jsonl")
    if not os.path.isfile(fpath):
        return 0
    try:
        with open(fpath, "r", encoding="utf-8") as f:
            return sum(1 for line in f if line.strip())
    except Exception:
        return 0


def read_recent_crashes(crashes_dir: str, limit: int = 30) -> list:
    if not os.path.isdir(crashes_dir):
        return []
    files = sorted(
        (os.path.join(crashes_dir, n) for n in os.listdir(crashes_dir) if n.startswith("crashes-") and n.endswith(".jsonl")),
        reverse=True,
    )
    items = []
    for fpath in files[:14]:
        try:
            with open(fpath, "r", encoding="utf-8") as f:
                lines = [ln.strip() for ln in f if ln.strip()]
            for ln in reversed(lines):
                try:
                    items.append(json.loads(ln))
                except Exception:
                    continue
                if len(items) >= limit:
                    return items
        except Exception:
            continue
    return items


def sign_session(token: str, exp: int) -> str:
    msg = f"{exp}".encode("utf-8")
    sig = hmac.new(token.encode("utf-8"), msg, hashlib.sha256).hexdigest()
    return f"{exp}.{sig}"


def verify_session(cookie_val: str, token: str) -> bool:
    if not cookie_val or not token or "." not in cookie_val:
        return False
    exp_s, sig = cookie_val.split(".", 1)
    try:
        exp = int(exp_s)
    except ValueError:
        return False
    if exp < int(time.time()):
        return False
    expected = hmac.new(token.encode("utf-8"), exp_s.encode("utf-8"), hashlib.sha256).hexdigest()
    return hmac.compare_digest(sig, expected)


def make_handler(
    data_dir: str,
    releases_dir: str,
    store: AnalyticsStore,
    limiter: RateLimiter,
    login_limiter: RateLimiter,
    admin_token: str,
    dashboard_html_path: str,
):
    crashes_dir = os.path.join(data_dir, "crashes")
    os.makedirs(crashes_dir, exist_ok=True)
    os.makedirs(releases_dir, exist_ok=True)

    def read_manifest(channel: str = "stable") -> tuple[str | None, dict | None]:
        candidates = [
            os.path.join(data_dir, f"latest_{channel}.json"),
            os.path.join(data_dir, "latest.json"),
        ]
        manifest_path = next((p for p in candidates if os.path.isfile(p)), None)
        if not manifest_path:
            return None, None
        with open(manifest_path, "r", encoding="utf-8") as f:
            return manifest_path, json.load(f)

    class SonifyHandler(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            ts = datetime.datetime.now(BJ_TZ).strftime("%Y-%m-%d %H:%M:%S")
            print(f"[{ts}] {self._client_ip()} {fmt % args}", flush=True)

        def _client_ip(self) -> str:
            cf = self.headers.get("CF-Connecting-IP", "").strip()
            fwd = self.headers.get("X-Forwarded-For", "").split(",")[0].strip()
            real_ip = self.headers.get("X-Real-IP", "").strip()
            return cf or fwd or real_ip or self.client_address[0]

        def _send_json(self, status: int, obj: dict, extra_headers: dict | None = None):
            raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            if extra_headers:
                for k, v in extra_headers.items():
                    self.send_header(k, v)
            self.end_headers()
            self.wfile.write(raw)

        def _send_html(self, status: int, html: str):
            raw = html.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(raw)

        def _is_admin_authed(self) -> bool:
            if not admin_token:
                return False
            auth = self.headers.get("Authorization", "")
            if auth.startswith("Bearer "):
                candidate = auth[7:].strip()
                if hmac.compare_digest(candidate, admin_token):
                    return True
            raw_cookie = self.headers.get("Cookie", "")
            if raw_cookie:
                jar = http.cookies.SimpleCookie()
                try:
                    jar.load(raw_cookie)
                    morsel = jar.get(SESSION_COOKIE_NAME)
                    if morsel and verify_session(morsel.value, admin_token):
                        return True
                except Exception:
                    pass
            return False

        def _read_json_body(self, max_bytes: int) -> tuple[dict | None, int, str]:
            length = int(self.headers.get("Content-Length", "0") or "0")
            if length <= 0 or length > max_bytes:
                return None, (413 if length > max_bytes else 400), "invalid_payload_size"
            raw = self.rfile.read(length)
            try:
                payload = json.loads(raw.decode("utf-8"))
                if not isinstance(payload, dict):
                    return None, 400, "json_root_must_be_object"
                return payload, 200, ""
            except Exception as exc:
                return None, 400, f"invalid_json: {exc}"

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
                try:
                    _, manifest = read_manifest(channel)
                except Exception as exc:
                    self._send_json(500, {"error": "manifest_read_error", "message": str(exc)})
                    return
                if not manifest:
                    self._send_json(404, {"error": "no_release_manifest", "message": "尚未发布任何版本"})
                    return
                self._send_json(200, manifest)
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

            if path in ("/admin", "/admin/"):
                if not os.path.isfile(dashboard_html_path):
                    self._send_html(500, "<h1>admin_dashboard.html not found</h1>")
                    return
                with open(dashboard_html_path, "r", encoding="utf-8") as f:
                    self._send_html(200, f.read())
                return

            if path == "/api/v1/admin/overview":
                if not self._is_admin_authed():
                    self._send_json(401, {"error": "unauthorized"})
                    return
                try:
                    days = int((qs.get("days") or ["14"])[0])
                except ValueError:
                    days = 14
                _, manifest = read_manifest("stable")
                data = store.query_overview(days=days, crashes_dir=crashes_dir, manifest=manifest)
                self._send_json(200, data)
                return

            if path == "/api/v1/admin/crashes":
                if not self._is_admin_authed():
                    self._send_json(401, {"error": "unauthorized"})
                    return
                try:
                    limit = max(1, min(100, int((qs.get("limit") or ["30"])[0])))
                except ValueError:
                    limit = 30
                self._send_json(200, {"items": read_recent_crashes(crashes_dir, limit=limit)})
                return

            self._send_json(404, {"error": "not_found"})

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path

            if path == "/api/v1/crash/report":
                if not limiter.allow(self._client_ip()):
                    self._send_json(429, {"error": "rate_limited"})
                    return
                payload, code, err = self._read_json_body(MAX_CRASH_BODY)
                if payload is None:
                    self._send_json(code, {"error": err})
                    return
                payload["receivedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
                payload["clientIp"] = self._client_ip()
                day = bj_day_str()
                out_file = os.path.join(crashes_dir, f"crashes-{day}.jsonl")
                with open(out_file, "a", encoding="utf-8") as f:
                    f.write(json.dumps(payload, ensure_ascii=False) + "\n")
                self._send_json(200, {"ok": True, "id": payload.get("id", "")})
                return

            if path == "/api/v1/telemetry/events":
                if not limiter.allow(self._client_ip()):
                    self._send_json(429, {"error": "rate_limited"})
                    return
                payload, code, err = self._read_json_body(MAX_TELEMETRY_BODY)
                if payload is None:
                    self._send_json(code, {"error": err})
                    return
                device = payload.get("device")
                events = payload.get("events")
                if not isinstance(device, dict) or not isinstance(events, list):
                    self._send_json(400, {"error": "invalid_schema"})
                    return
                try:
                    accepted = store.ingest_batch(device, events)
                except ValueError as exc:
                    self._send_json(400, {"error": str(exc)})
                    return
                except Exception as exc:
                    self._send_json(500, {"error": "db_error", "message": str(exc)})
                    return
                self._send_json(200, {"ok": True, "accepted": accepted})
                return

            if path == "/api/v1/admin/login":
                if not login_limiter.allow(self._client_ip()):
                    self._send_json(429, {"error": "too_many_attempts"})
                    return
                if not admin_token:
                    self._send_json(503, {"error": "admin_token_not_configured"})
                    return
                payload, code, err = self._read_json_body(MAX_ADMIN_BODY)
                if payload is None:
                    self._send_json(code, {"error": err})
                    return
                supplied = str(payload.get("token") or "").strip()
                if not hmac.compare_digest(supplied, admin_token):
                    self._send_json(401, {"error": "invalid_token"})
                    return
                exp = int(time.time()) + SESSION_TTL_SEC
                cookie_val = sign_session(admin_token, exp)
                cookie_hdr = f"{SESSION_COOKIE_NAME}={cookie_val}; Path=/; Max-Age={SESSION_TTL_SEC}; HttpOnly; SameSite=Strict"
                if self.headers.get("X-Forwarded-Proto", "").lower() == "https" or self.headers.get("Host", "").endswith("zhangbing.tech"):
                    cookie_hdr += "; Secure"
                self._send_json(200, {"ok": True}, extra_headers={"Set-Cookie": cookie_hdr})
                return

            if path == "/api/v1/admin/logout":
                self._send_json(
                    200,
                    {"ok": True},
                    extra_headers={"Set-Cookie": f"{SESSION_COOKIE_NAME}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"},
                )
                return

            if path == "/api/v1/admin/release/policy":
                if not self._is_admin_authed():
                    self._send_json(401, {"error": "unauthorized"})
                    return
                payload, code, err = self._read_json_body(MAX_ADMIN_BODY)
                if payload is None:
                    self._send_json(code, {"error": err})
                    return
                manifest_path, manifest = read_manifest("stable")
                if not manifest or not manifest_path:
                    self._send_json(404, {"error": "no_release_manifest"})
                    return
                if "forceUpdate" in payload:
                    manifest["forceUpdate"] = bool(payload["forceUpdate"])
                if "minSupportedVersionCode" in payload:
                    manifest["minSupportedVersionCode"] = max(1, int(payload["minSupportedVersionCode"]))
                if "releaseNotes" in payload and isinstance(payload["releaseNotes"], str):
                    manifest["releaseNotes"] = payload["releaseNotes"][:4000]
                if "changelogUrl" in payload and isinstance(payload["changelogUrl"], str):
                    manifest["changelogUrl"] = payload["changelogUrl"].strip()[:512]
                if "repoUrl" in payload and isinstance(payload["repoUrl"], str):
                    manifest["repoUrl"] = payload["repoUrl"].strip()[:512]
                tmp_path = f"{manifest_path}.tmp"
                with open(tmp_path, "w", encoding="utf-8") as f:
                    json.dump(manifest, f, ensure_ascii=False, indent=2)
                os.replace(tmp_path, manifest_path)
                self._send_json(200, {"ok": True, "release": manifest})
                return

            self._send_json(404, {"error": "not_found"})

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
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            if status == 206:
                self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
            self.end_headers()

            try:
                with open(fpath, "rb") as f:
                    f.seek(start)
                    remaining = length
                    while remaining > 0:
                        chunk = f.read(min(65536, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        remaining -= len(chunk)
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass

    return SonifyHandler


def load_admin_token(arg_token: str | None, token_file: str | None) -> str:
    if arg_token:
        return arg_token.strip()
    env_tok = os.environ.get("SONIFY_ADMIN_TOKEN", "").strip()
    if env_tok:
        return env_tok
    if token_file and os.path.isfile(token_file):
        with open(token_file, "r", encoding="utf-8") as f:
            return f.read().strip()
    return ""


def main():
    parser = argparse.ArgumentParser(description="Sonify Update, Crash & Analytics Server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--data-dir", default="/var/lib/sonify-server")
    parser.add_argument("--releases-dir", default=None)
    parser.add_argument("--admin-token", default=None)
    parser.add_argument("--admin-token-file", default=None)
    parser.add_argument(
        "--dashboard-html",
        default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "admin_dashboard.html"),
    )
    args = parser.parse_args()

    releases_dir = args.releases_dir or os.path.join(args.data_dir, "releases")
    os.makedirs(args.data_dir, exist_ok=True)
    os.makedirs(releases_dir, exist_ok=True)

    db_path = os.path.join(args.data_dir, "analytics.db")
    store = AnalyticsStore(db_path)
    admin_token = load_admin_token(args.admin_token, args.admin_token_file)

    limiter = RateLimiter(RATE_LIMIT_MAX, RATE_LIMIT_WINDOW)
    login_limiter = RateLimiter(LOGIN_RATE_LIMIT_MAX, RATE_LIMIT_WINDOW)
    server = http.server.ThreadingHTTPServer(
        (args.host, args.port),
        make_handler(args.data_dir, releases_dir, store, limiter, login_limiter, admin_token, args.dashboard_html),
    )
    print(
        f"Sonify server listening on http://{args.host}:{args.port} "
        f"(data={args.data_dir}, db={db_path}, admin={'enabled' if admin_token else 'disabled'})",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
