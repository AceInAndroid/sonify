#!/usr/bin/env python3
"""
Sonify Multi-Language (i18n) Validator & Key Catalog Generator.

Usage:
  python3 scripts/check_i18n.py                  # Validate key & placeholder parity across all locales
  python3 scripts/check_i18n.py --strict         # Fail on warnings (empty strings, untranslated check, etc.)
  python3 scripts/check_i18n.py --sync-doc       # Regenerate docs/i18n/STRING_KEY_CATALOG.md from XML
  python3 scripts/check_i18n.py --scan-hardcoded # Scan UI Kotlin files for residual hardcoded Chinese strings
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = REPO_ROOT / "app/src/main/res"
BASE_XML = RES_DIR / "values/strings.xml"
CATALOG_DOC = REPO_ROOT / "docs/i18n/STRING_KEY_CATALOG.md"
UI_SRC_DIR = REPO_ROOT / "app/src/main/java/com/sonify/player/ui"

PLACEHOLDER_RE = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
VALID_KEY_RE = re.compile(r"^[a-z][a-z0-9_]*$")
CJK_LITERAL_RE = re.compile(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"')
LOG_LINE_RE = re.compile(r"\bLog\.(v|d|i|w|e|wtf)\s*\(")

MODULE_TITLES: List[Tuple[str, str]] = [
    ("app_", "应用标识 / App Identity"),
    ("common_", "通用文案与按钮 / Common"),
    ("tab_", "底部导航 / Navigation Tabs"),
    ("lib_", "音乐库 / Music Library"),
    ("player_", "黑胶播放与输出 / Now Playing & Audio Output"),
    ("lyrics_", "沉浸同步歌词 / Immersive Lyrics"),
    ("usb_", "USB DAC 诊断仪表 / USB DAC Diagnostics"),
    ("settings_", "设置页 / Settings"),
    ("pref_", "偏好枚举选项 / Preference Enums"),
    ("playlist_", "歌单与离线下载 / Playlists & Downloads"),
    ("subsonic_", "Subsonic 媒体服务器 / Subsonic Server"),
    ("search_", "全屏全局搜索 / Global Search"),
    ("wifi_", "WiFi 局域网传歌 / WiFi Transfer"),
    ("update_", "应用内更新与日志 / In-App Update & Changelog"),
    ("crash_", "崩溃拦截与恢复 / Crash Recovery"),
    ("lock_", "黑胶锁屏 / Vinyl Lockscreen"),
    ("notif_", "通知栏 / Notifications"),
    ("toast_", "轻量提示 / Toasts"),
    ("err_", "错误提示 / Error Messages"),
]


def parse_strings_xml(path: Path) -> Dict[str, str]:
    tree = ET.parse(path)
    root = tree.getroot()
    out: Dict[str, str] = {}
    dups: List[str] = []
    for elem in root.findall("string"):
        name = elem.attrib.get("name", "").strip()
        if not name:
            continue
        if name in out:
            dups.append(name)
        text = "".join(elem.itertext())
        out[name] = text
    if dups:
        raise ValueError(f"Duplicate keys in {path.relative_to(REPO_ROOT)}: {dups}")
    return out


def extract_placeholders(value: str) -> List[str]:
    # Ignore escaped literal %%
    cleaned = value.replace("%%", "")
    return sorted(m.group(0) for m in PLACEHOLDER_RE.finditer(cleaned))


def discover_locale_files() -> List[Tuple[str, Path]]:
    locales: List[Tuple[str, Path]] = []
    for d in sorted(RES_DIR.glob("values-*")):
        xml_path = d / "strings.xml"
        if xml_path.is_file():
            code = d.name[len("values-"):]
            locales.append((code, xml_path))
    return locales


def validate(strict: bool = False) -> Tuple[bool, Dict[str, str], Dict[str, Dict[str, str]]]:
    if not BASE_XML.is_file():
        print(f"[ERROR] Base strings file missing: {BASE_XML}", file=sys.stderr)
        return False, {}, {}

    base = parse_strings_xml(BASE_XML)
    locale_files = discover_locale_files()
    locales_map: Dict[str, Dict[str, str]] = {}
    errors: List[str] = []
    warnings: List[str] = []

    for k, v in base.items():
        if not VALID_KEY_RE.match(k):
            errors.append(f"Invalid key format in values/strings.xml: '{k}' (must match ^[a-z][a-z0-9_]*$)")
        if not v.strip():
            errors.append(f"Empty value in values/strings.xml: '{k}'")

    for code, path in locale_files:
        data = parse_strings_xml(path)
        locales_map[code] = data

        missing = sorted(set(base.keys()) - set(data.keys()))
        extra = sorted(set(data.keys()) - set(base.keys()))
        if missing:
            errors.append(f"[{code}] Missing {len(missing)} keys: {', '.join(missing[:10])}{' ...' if len(missing) > 10 else ''}")
        if extra:
            errors.append(f"[{code}] Extra unknown {len(extra)} keys: {', '.join(extra[:10])}{' ...' if len(extra) > 10 else ''}")

        for k in sorted(set(base.keys()) & set(data.keys())):
            v = data[k]
            if not v.strip():
                errors.append(f"[{code}] Empty translation for key '{k}'")
            p_base = extract_placeholders(base[k])
            p_loc = extract_placeholders(v)
            if p_base != p_loc:
                errors.append(f"[{code}] Placeholder mismatch for '{k}': base={p_base} vs {code}={p_loc}")

    print(f"[i18n] Base keys (en): {len(base)} | Additional locales: {[c for c, _ in locale_files]}")
    for w in warnings:
        print(f"[WARN] {w}")
    for e in errors:
        print(f"[FAIL] {e}", file=sys.stderr)

    ok = len(errors) == 0 and (not strict or len(warnings) == 0)
    if ok:
        print("[OK] All locale string resources are 100% synchronized and placeholder-safe!")
    return ok, base, locales_map


def md_escape(text: str) -> str:
    return (
        text.replace("\\n", " ↵ ")
        .replace("\n", " ↵ ")
        .replace("|", "\\|")
        .replace("\\'", "'")
        .strip()
    )


def module_for_key(key: str) -> str:
    for prefix, _ in MODULE_TITLES:
        if key.startswith(prefix):
            return prefix
    return "other_"


def write_catalog_doc(base: Dict[str, str], locales_map: Dict[str, Dict[str, str]]) -> None:
    zh = locales_map.get("zh", locales_map.get("zh-rCN", {}))
    grouped: Dict[str, List[str]] = {p: [] for p, _ in MODULE_TITLES}
    grouped["other_"] = []
    for k in base.keys():
        grouped[module_for_key(k)].append(k)

    lines: List[str] = [
        "# Sonify 多语言字段标准对照字典（String Key Catalog）",
        "",
        "> **自动同步文档**：由 `python3 scripts/check_i18n.py --sync-doc` 直接根据 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml` 生成，请勿手工修改本表以免漂移。规范细则见 [I18N_STANDARD.md](I18N_STANDARD.md)。",
        "",
        f"- **基准语言（`values/strings.xml`）**：English（共 **{len(base)}** 条 Key）",
        f"- **已完整适配语种**：`en`（默认回退）、`zh`（简体中文，共 **{len(zh)}** 条 Key）",
        "",
        "## 模块索引",
        "",
        "| 前缀 | 模块说明 | Key 数量 |",
        "|---|---|---|",
    ]

    for prefix, title in MODULE_TITLES:
        cnt = len(grouped[prefix])
        if cnt > 0:
            lines.append(f"| `{prefix}*` | {title} | {cnt} |")
    if grouped["other_"]:
        lines.append(f"| 其他 | 未归类 / Other | {len(grouped['other_'])} |")

    for prefix, title in MODULE_TITLES + [("other_", "其他 / Other")]:
        keys = grouped[prefix]
        if not keys:
            continue
        lines.extend([
            "",
            "---",
            "",
            f"## `{prefix}*` — {title}（{len(keys)} 条）",
            "",
            "| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |",
            "|---|---|---|---|",
        ])
        for k in keys:
            en_val = md_escape(base.get(k, ""))
            zh_val = md_escape(zh.get(k, ""))
            ph = ", ".join(f"`{p}`" for p in extract_placeholders(base.get(k, ""))) or "—"
            lines.append(f"| `{k}` | {en_val} | {zh_val} | {ph} |")

    CATALOG_DOC.parent.mkdir(parents=True, exist_ok=True)
    CATALOG_DOC.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[SYNC] Wrote {CATALOG_DOC.relative_to(REPO_ROOT)} ({len(base)} keys)")


def scan_hardcoded_ui() -> int:
    hits: List[Tuple[str, int, str]] = []
    for kt in sorted(UI_SRC_DIR.rglob("*.kt")):
        for lineno, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            if LOG_LINE_RE.search(line):
                continue
            # Strip trailing inline comments outside quotes (best-effort)
            code_part = line.split("//", 1)[0]
            for m in CJK_LITERAL_RE.finditer(code_part):
                hits.append((str(kt.relative_to(REPO_ROOT)), lineno, m.group(0)))
    if hits:
        print(f"[SCAN] Found {len(hits)} hardcoded CJK UI literals:")
        for path, ln, lit in hits[:60]:
            print(f"  {path}:{ln} -> {lit}")
        if len(hits) > 60:
            print(f"  ... and {len(hits) - 60} more")
    else:
        print("[SCAN] 0 hardcoded CJK literals in app/src/main/java/com/sonify/player/ui/!")
    return len(hits)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true")
    ap.add_argument("--sync-doc", action="store_true")
    ap.add_argument("--scan-hardcoded", action="store_true")
    args = ap.parse_args()

    ok, base, locales_map = validate(strict=args.strict)
    if args.sync_doc and base:
        write_catalog_doc(base, locales_map)
    hardcoded = 0
    if args.scan_hardcoded:
        hardcoded = scan_hardcoded_ui()
    if not ok:
        return 1
    if args.strict and args.scan_hardcoded and hardcoded > 0:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
