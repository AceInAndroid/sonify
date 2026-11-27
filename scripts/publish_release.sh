#!/usr/bin/env bash
set -euo pipefail

# Sonify 一键发布脚本：计算 SHA-256、生成 latest.json 并同步至上海云服务器 43.142.250.231
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
VERSION_CODE=""
VERSION_NAME=""
NOTES=""
FORCE_UPDATE="false"
MIN_SUPPORTED="1"
SSH_TARGET="root@43.142.250.231"
REMOTE_DIR="/var/lib/sonify-server"
BASE_URL="https://sonify.zhangbing.tech"
DRY_RUN="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK_PATH="$2"; shift 2 ;;
    --version-code) VERSION_CODE="$2"; shift 2 ;;
    --version-name) VERSION_NAME="$2"; shift 2 ;;
    --notes) NOTES="$2"; shift 2 ;;
    --force) FORCE_UPDATE="true"; shift ;;
    --min-supported) MIN_SUPPORTED="$2"; shift 2 ;;
    --ssh) SSH_TARGET="$2"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$VERSION_CODE" || -z "$VERSION_NAME" ]]; then
  echo "Usage: $0 --version-code <N> --version-name <X.Y.Z> [--apk <path>] [--notes <text>] [--force] [--ssh user@host] [--dry-run]"
  exit 1
fi
[[ -f "$APK_PATH" ]] || { echo "APK not found: $APK_PATH"; exit 1; }

APK_NAME="sonify-v${VERSION_NAME}-arm64-v8a.apk"
SHA256=$(shasum -a 256 "$APK_PATH" | awk '{print $1}')
FILE_SIZE=$(wc -c < "$APK_PATH" | tr -d ' ')
PUBLISHED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

TMP_JSON=$(mktemp)
trap 'rm -f "$TMP_JSON"' EXIT

python3 - "$TMP_JSON" "$VERSION_CODE" "$VERSION_NAME" "$MIN_SUPPORTED" "$FORCE_UPDATE" "$NOTES" "$BASE_URL/releases/$APK_NAME" "$FILE_SIZE" "$SHA256" "$PUBLISHED_AT" <<'PY'
import json, sys
out, vc, vn, min_vc, force, notes, url, sz, sha, pub = sys.argv[1:]
notes = notes.replace("\\n", "\n")
manifest = {
    "channel": "stable",
    "versionCode": int(vc),
    "versionName": vn,
    "minSupportedVersionCode": int(min_vc),
    "forceUpdate": force.lower() == "true",
    "releaseNotes": notes,
    "apkUrl": url,
    "fileSize": int(sz),
    "sha256": sha,
    "publishedAt": pub,
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] Manifest generated above; skipped scp upload."
  exit 0
fi

echo "Uploading $APK_NAME ($FILE_SIZE bytes) to $SSH_TARGET:$REMOTE_DIR ..."
scp "$APK_PATH" "${SSH_TARGET}:${REMOTE_DIR}/releases/${APK_NAME}"
scp "$TMP_JSON" "${SSH_TARGET}:${REMOTE_DIR}/latest.json"
echo "Release v${VERSION_NAME} (code ${VERSION_CODE}) published to ${BASE_URL}!"
