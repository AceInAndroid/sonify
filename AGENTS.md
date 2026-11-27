# Sonify Engineering & Real-Device QA Gate Rules

This file defines mandatory engineering rules for all contributors and AI coding agents working in the **Sonify** (`com.sonify.player`) repository.

---

## 1. Mandatory Real-Device QA Release Gate（铁律：真机实测通过后再上线）

**Every new feature, UI modification, audio/USB change, or bug fix MUST pass real-device verification BEFORE `git commit` / `git push`.**

Required pre-push sequence (see `docs/testing/RELEASE_GATE.md` for full checklist):

1. **Test Case Maintenance**: Add or update affected test cases in `docs/testing/TEST_CASES.md` (with clear IDs, steps, and expected UI/data results).
2. **Automated Unit Tests**: Run `./gradlew :app:testDebugUnitTest` and confirm **100% pass (0 failures, 0 errors)**.
3. **Debug APK Build & Install**: Build `./gradlew :app:assembleDebug` and install onto the physical Android test device (`3917JR`, Android 10 / API 29, or active connected ADB device).
4. **Real-Device End-to-End & Visual Verification**:
   - Exercise every changed user flow on the physical device via ADB (`uiautomator dump`, `input tap/swipe/text`, live HTTP/network/file checks where applicable).
   - Capture screenshots (`adb exec-out screencap -p` into a temporary scratch folder outside the repo) and visually inspect layout, typography, contrast, clipping, overlap, and API 29 compatibility.
   - Verify `adb logcat -d -s 'AndroidRuntime:E' 'FATAL:E'` contains **zero crashes and zero ANRs**.
   - Clean up any temporary test accounts, playlists, or test audio files created on the device during QA.
5. **Test Report Recording**: Record the verification summary in `docs/testing/reports/` (or append to the current release report).
6. **Blocked Device Policy**:
   - If the physical test device is **offline or PIN-locked**, **STOP and ask the user to connect/unlock the device**.
   - Never bypass lockscreens, never pretend real-device tests ran, and **never push unverified production changes** unless the user explicitly waives the real-device gate for that specific commit.

---

## 2. Privacy & Repository Hygiene

- Never commit debug/release APKs, `local.properties`, credentials, personal Subsonic server URLs/passwords, or raw screenshots containing personal notifications/status-bar info.
- Keep temporary QA screenshots and helper scripts in scratch directories outside the Git working tree.

---

## 3. Target Compatibility & Architecture Guardrails

- `minSdk = 24`, primary physical verification baseline: **Android 10 (API 29)** `arm64-v8a` (`3917JR`) + external USB DAC (**Moondrop MOONRIVER 3**, UAC2 High-Speed).
- Do not rely solely on Android 12+ (`API 31+`) visual APIs such as `Modifier.blur()` for core visuals; always provide smooth, GPU-friendly fallbacks that look crisp at 60fps on API 29.
- Never weaken the 4-layer USB hearing protection ceilings or bypass safe startup volume attenuation.
- Reference documents:
  - Test Plan: `docs/testing/TEST_PLAN.md`
  - Test Cases: `docs/testing/TEST_CASES.md`
  - Release Gate SOP: `docs/testing/RELEASE_GATE.md`
  - QA Reports: `docs/testing/reports/`
  - Multi-Language Standard & Key Catalog: `docs/i18n/I18N_STANDARD.md`, `docs/i18n/STRING_KEY_CATALOG.md` (run `python3 scripts/check_i18n.py --strict --sync-doc` whenever strings change)
