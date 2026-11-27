# Sonify 多语言（i18n）工程规范与贡献指南

本文档定义 **Sonify** 的多语言资源标准、字段命名规则、专有音频术语表与新语种接入流程。所有新增页面、新功能与社区翻译贡献（Pull Request）均须遵循本规范。

> 完整中英字段对照表见 **[STRING_KEY_CATALOG.md](STRING_KEY_CATALOG.md)**（由 `scripts/check_i18n.py --sync-doc` 自动同步生成）。

---

## 1. 资源目录与语种回退标准

Sonify 采用 Android 国际通用资源布局，以 **English** 作为默认回退语种：

| 资源目录 | 对应语种 | 说明 |
|---|---|---|
| `app/src/main/res/values/strings.xml` | **English (`en`)** | **唯一基准源（Default Fallback）**。所有尚未单独适配的系统语言（如 `fr`、`de`、`es`）自动安全回退至英文。 |
| `app/src/main/res/values-zh/strings.xml` | **简体中文 (`zh-CN`)** | 覆盖 `zh-CN`、`zh-SG`、`zh-Hans`，并在社区贡献 `values-zh-rTW` 前为繁体中文系统提供中文兜底。 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 繁體中文（待社区贡献） | 预留标准目录 |
| `app/src/main/res/values-ja/strings.xml` | 日本語（待社区贡献） | 预留标准目录 |
| `app/src/main/res/values-ko/strings.xml` | 한국어（待社区贡献） | 预留标准目录 |

---

## 2. 运行时语言切换架构

- **状态存储**：`UserPreferences`（DataStore）保存 `AppLanguage`（`system` / `zh-CN` / `en`），并由 `SonifyLocaleManager` 原子同步至 `filesDir/app_language.txt`（供冷启动 `attachBaseContext()` 与独立 `:crash` 崩溃恢复进程无锁读取）。
- **零中断热切换**：Compose 根节点通过 `ProvideAppLanguage` 重写 `LocalContext` 与 `LocalConfiguration`，切换语言在 **1 帧（< 16ms）内完成**，不重建 Activity、不断开 USB DAC、不中断无损音频播放。
- **Android 13+ 系统同步**：通过 `res/xml/locales_config.xml` 与 `LocaleManager.applicationLocales` 保持系统「应用语言」设置同步。
- **母语展示原则**：语言选择弹窗中，各语言选项必须始终以其**母语名称**展示（如 `简体中文`、`English`），避免用户误切换至不认识的语言后无法切回。

---

## 3. String Key 命名规范

严格采用 **`<module>_<feature>_<element>`** 全小写下划线格式（正则 `^[a-z][a-z0-9_]*$`）：

| 模块前缀 | 适用范围 | 规范示例 |
|---|---|---|
| `app_` | 应用名称与核心标识 | `app_name` |
| `common_` | 跨页面通用操作与缺省词 | `common_cancel`, `common_confirm`, `common_unknown_artist` |
| `tab_` | 底部主导航标签 | `tab_library`, `tab_usb`, `tab_settings` |
| `lib_` | 音乐库、筛选胶囊、扫描与断点卡片 | `lib_title`, `lib_resume_last`, `lib_filter_albums` |
| `player_` | 黑胶播放页、迷你条、队列、输出设备与音量 HUD | `player_zen_vinyl_hint`, `player_output_usb_exclusive` |
| `lyrics_` | 沉浸歌词页、样式调参、在线匹配与导入 | `lyrics_no_lyrics`, `lyrics_search_online` |
| `usb_` | USB DAC 诊断仪表、时钟/Feedback 遥测、测试音 | `usb_title`, `usb_status_connected` |
| `settings_` | 设置页分组标题、设置项标题/副标题、对话框 | `settings_language_title`, `settings_group_audio` |
| `pref_` | 设置枚举项的 label 与 subtitle（`@StringRes` 绑定） | `pref_volume_mode_auto_label`, `pref_canvas_oled_label` |
| `playlist_` | 本地/远程歌单、多选批量操作、排序、离线下载 | `playlist_create_title`, `playlist_download_all` |
| `subsonic_` | Subsonic 多账号、登录与远程曲库 | `subsonic_add_server`, `subsonic_test_connection` |
| `search_` | 全屏跨源全局搜索 | `search_placeholder`, `search_section_lyrics` |
| `wifi_` | WiFi 局域网传歌（App 与 Web 双端） | `wifi_title`, `wifi_start_server` |
| `update_` | 应用内更新、强制更新锁死、更新日志与 GitHub 链接 | `update_forced_title`, `update_changelog_title` |
| `crash_` | 独立 `:crash` 恢复页与本地崩溃记录 | `crash_title`, `crash_auto_restart_countdown` |
| `lock_` | 黑胶锁屏页 | `lock_swipe_up_to_unlock` |
| `notif_` | 媒体播放与传歌前台通知 | `notif_channel_name`, `notif_action_favorite` |
| `toast_` | 操作反馈轻量 Toast | `toast_added_to_playlist`, `toast_copied` |
| `err_` | 网络/校验/权限/解析等用户可见报错 | `err_update_sha256_mismatch`, `err_network_generic` |

**禁止事项**：
- 禁止使用无语义编号（如 `text_1`, `msg_2`）。
- 禁止在 UI 层硬编码任何面向用户的中英文字符串（`Log.d/i/w/e` 调试日志除外）。
- 禁止跨模块强行复用语义不同但碰巧同字的 Key（防止在英文/德文等语种下词性冲突）。

---

## 4. 占位符、转义与 UI 排版规则

1. **多参数必须带位置编号**：包含 ≥ 2 个动态参数时，一律使用 `%1$s`、`%2$d`（不可写裸 `%s` / `%d`），方便不同语种自由调整词序。
2. **XML 转义规范**：
   - 英文单引号 `'` 必须转义为 `\'`（如 `Can\'t connect`）。
   - `&` 必须转义为 `&amp;`，`<` 转义为 `&lt;`。
   - 换行使用 `\n`，省略号统一使用 Unicode `…`（非 `...`）。
   - 百分号在含占位符的字符串中需写 `%%`（如 `%1$d%%`）。
3. **英文长文案布局安全**：英文平均比中文长 **1.3×–1.8×**。所有按钮、胶囊标签、列表标题须合理配置 `maxLines`、`TextOverflow.Ellipsis` 或 `Row` 自适应留白，避免在 Android 10 `1080×2340` 基准屏出现裁切或错位。

---

## 5. HiFi 专有术语表（禁止随意意译）

以下术语在所有语种中均保持统一拼写：

| 术语 | 规范写法 | 说明 |
|---|---|---|
| 应用名称 | `Sonify` | 品牌名，不翻译 |
| 独占直通概念 | `Bit-Perfect` | 音频发烧友通用术语，中英文均保留 |
| USB 音频协议 | `UAC1` / `UAC2` / `USB DAC` | 不翻译 |
| 音频编码与指标 | `PCM` / `FLAC` / `WAV` / `dBFS` / `dB` / `kHz` / `Hz` / `ppm` | 统一单位大小写 |
| 协议与服务名 | `Subsonic` / `OpenSubsonic` / `Navidrome` / `LRCLIB` / `GitHub` / `SHA-256` | 不翻译 |
| 引擎组件 | `libusb` / `AudioFlinger` / `Feature Unit` | 专业技术词保持原词 |

---

## 6. 五步新增一门新语言（社区贡献指南）

以新增 **日语（`ja`）** 为例：

1. **复制基准文件**：将 `app/src/main/res/values/strings.xml` 复制到 `app/src/main/res/values-ja/strings.xml`，逐条翻译（保持所有 `%1$s` / `%2$d` 占位符不变）。
2. **注册语言选项**：
   - 在 `app/src/main/java/com/sonify/player/i18n/AppLanguage.kt` 新增枚举项 `JA("ja", "ja", "日本語")`；
   - 在 `SonifyLocaleManager.resolveLocale()` 增加对应分支；
   - 在 `app/src/main/res/xml/locales_config.xml` 新增 `<locale android:name="ja" />`。
3. **（可选）补充内置更新日志**：在 `app/src/main/java/com/sonify/player/update/ReleaseNotesCatalog.kt` 为新语言补充版本摘要。
4. **运行自动化校验与文档同步**：
   ```bash
   chmod +x scripts/check_i18n.py
   python3 scripts/check_i18n.py --strict --sync-doc
   ./gradlew :app:testDebugUnitTest
   ```
   确保 Key 100% 齐平、占位符 0 偏差、单元测试全绿。
5. **真机切换验收**：在设置页切换至新语言，重点检查音乐库、播放页、USB 仪表与设置页无文案重叠或截断后即可提交 PR。
