# Sonify 多语言字段标准对照字典（String Key Catalog）

> **自动同步文档**：由 `python3 scripts/check_i18n.py --sync-doc` 直接根据 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml` 生成，请勿手工修改本表以免漂移。规范细则见 [I18N_STANDARD.md](I18N_STANDARD.md)。

- **基准语言（`values/strings.xml`）**：English（共 **893** 条 Key）
- **已完整适配语种**：`en`（默认回退）、`zh`（简体中文，共 **893** 条 Key）

## 模块索引

| 前缀 | 模块说明 | Key 数量 |
|---|---|---|
| `app_*` | 应用标识 / App Identity | 1 |
| `common_*` | 通用文案与按钮 / Common | 46 |
| `tab_*` | 底部导航 / Navigation Tabs | 3 |
| `lib_*` | 音乐库 / Music Library | 53 |
| `player_*` | 黑胶播放与输出 / Now Playing & Audio Output | 53 |
| `lyrics_*` | 沉浸同步歌词 / Immersive Lyrics | 55 |
| `usb_*` | USB DAC 诊断仪表 / USB DAC Diagnostics | 46 |
| `settings_*` | 设置页 / Settings | 170 |
| `pref_*` | 偏好枚举选项 / Preference Enums | 95 |
| `playlist_*` | 歌单与离线下载 / Playlists & Downloads | 68 |
| `subsonic_*` | Subsonic 媒体服务器 / Subsonic Server | 68 |
| `search_*` | 全屏全局搜索 / Global Search | 33 |
| `wifi_*` | WiFi 局域网传歌 / WiFi Transfer | 33 |
| `update_*` | 应用内更新与日志 / In-App Update & Changelog | 30 |
| `crash_*` | 崩溃拦截与恢复 / Crash Recovery | 22 |
| `lock_*` | 黑胶锁屏 / Vinyl Lockscreen | 5 |
| `notif_*` | 通知栏 / Notifications | 8 |
| `toast_*` | 轻量提示 / Toasts | 32 |
| `err_*` | 错误提示 / Error Messages | 72 |

---

## `app_*` — 应用标识 / App Identity（1 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `app_name` | Sonify | Sonify | — |

---

## `common_*` — 通用文案与按钮 / Common（46 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `common_cancel` | Cancel | 取消 | — |
| `common_confirm` | Confirm | 确认 | — |
| `common_close` | Close | 关闭 | — |
| `common_save` | Save | 保存 | — |
| `common_create` | Create | 创建 | — |
| `common_delete` | Delete | 删除 | — |
| `common_edit` | Edit | 编辑 | — |
| `common_retry` | Retry | 重试 | — |
| `common_clear` | Clear | 清空 | — |
| `common_search` | Search | 搜索 | — |
| `common_play` | Play | 播放 | — |
| `common_pause` | Pause | 暂停 | — |
| `common_play_now` | Play Now | 立即播放 | — |
| `common_play_all` | Play All | 播放全部 | — |
| `common_shuffle` | Shuffle | 随机 | — |
| `common_back` | Back | 返回 | — |
| `common_more_actions` | More options | 更多操作 | — |
| `common_unknown_artist` | Unknown Artist | 未知艺术家 | — |
| `common_unknown_album` | Unknown Album | 未知专辑 | — |
| `common_unknown_track` | Unknown Track | 未知歌曲 | — |
| `common_unnamed_playlist` | Untitled Playlist | 未命名歌单 | — |
| `common_selected` | Selected | 已选 | — |
| `common_checked` | Selected | 已选中 | — |
| `common_manage` | Manage | 管理 | — |
| `common_all` | All | 全部 | — |
| `common_multi_select` | Select | 多选 | — |
| `common_select_all` | Select All | 全选 | — |
| `common_deselect_all` | Deselect All | 取消全选 | — |
| `common_copy` | Copy | 复制 | — |
| `common_got_it` | Got It | 知道了 | — |
| `common_default` | Default | 默认 | — |
| `common_reset_default` | Reset | 恢复默认 | — |
| `common_cover` | Cover | 封面 | — |
| `common_album_cover` | Album Cover | 专辑封面 | — |
| `common_view_all` | View All | 查看全部 | — |
| `common_download` | Download | 下载 | — |
| `common_downloaded` | Offline | 已离线 | — |
| `common_downloaded_full` | Downloaded offline | 已离线下载 | — |
| `common_tracks_count` | %1$d tracks | %1$d 首歌曲 | `%1$d` |
| `common_tracks_short` | %1$d tracks | %1$d 首 | `%1$d` |
| `common_albums_count` | %1$d albums | %1$d 张专辑 | `%1$d` |
| `common_approx_mins` | · ~%1$d min | · 约 %1$d 分钟 | `%1$d` |
| `common_approx_db` | ~%1$.0f dB | 约 %1$.0f dB | `%1$.0f` |
| `common_pct_recommended` | %1$d%% (Recommended) | %1$d%%（推荐） | `%1$d` |
| `common_pct_with_db` | %1$d%% (~%2$s dB) | %1$d%% (约 %2$s dB) | `%1$d`, `%2$s` |
| `common_local_tracks_count` | %1$d local tracks | %1$d 首本地曲目 | `%1$d` |

---

## `tab_*` — 底部导航 / Navigation Tabs（3 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `tab_library` | Library | 音乐库 | — |
| `tab_usb` | USB DAC | USB 设备 | — |
| `tab_settings` | Settings | 设置 | — |

---

## `lib_*` — 音乐库 / Music Library（53 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `lib_title` | Library | 音乐库 | — |
| `lib_filter_songs` | Songs | 歌曲 | — |
| `lib_filter_playlists` | Playlists | 歌单 | — |
| `lib_filter_albums` | Albums | 专辑 | — |
| `lib_filter_artists` | Artists | 艺术家 | — |
| `lib_filter_folders` | Folders | 文件夹 | — |
| `lib_filter_favorites` | Favorites | 已收藏 | — |
| `lib_filter_downloads` | Downloads | 已下载 | — |
| `lib_source_local` | Local | 本地音乐 | — |
| `lib_login_subsonic` | Sign in Subsonic | 登录 Subsonic | — |
| `lib_manage_accounts` | Accounts | 管理账号 | — |
| `lib_action_downloads` | Downloads | 下载管理 | — |
| `lib_action_global_search` | Global Search | 全局搜索 | — |
| `lib_action_refresh_remote` | Refresh remote library | 刷新远程曲库 | — |
| `lib_action_manage_subsonic` | Manage Subsonic accounts | 管理 Subsonic 账号 | — |
| `lib_action_cancel_scan` | Cancel scan | 取消扫描 | — |
| `lib_action_scan_local` | Scan local music | 扫描本地音乐 | — |
| `lib_action_import_audio` | Import audio files | 导入音频文件 | — |
| `lib_search_local_hint` | Filter songs, playlists, albums, artists… | 搜索本地歌曲、歌单、专辑、艺人… | — |
| `lib_scanning_progress` | Scanning %1$d/%2$d | 正在扫描 %1$d/%2$d | `%1$d`, `%2$d` |
| `lib_scanning_traversing` | Scanning directories… | 正在遍历目录… | — |
| `lib_scan_done` | Scan complete: %1$d tracks (%2$d added/updated) | 扫描完成：共 %1$d 首（新增/更新 %2$d 首） | `%1$d`, `%2$d` |
| `lib_scan_cancelled` | Scan cancelled | 已取消扫描 | — |
| `lib_scan_failed` | Scan failed: %1$s | 扫描失败：%1$s | `%1$s` |
| `lib_imported_count` | Imported %1$d tracks | 已导入 %1$d 首歌曲 | `%1$d` |
| `lib_resume_last` | Resume Last Session | 继续上次播放 | — |
| `lib_resume_play` | Resume | 继续播放 | — |
| `lib_continue_listening` | Continue Listening | 继续聆听 | — |
| `lib_all_songs` | All Songs | 全部歌曲 | — |
| `lib_shuffle_all` | Shuffle All | 全部随机 | — |
| `lib_new_playlist` | New Playlist | 新建歌单 | — |
| `lib_empty_playlists_title` | No Playlists Yet | 还没有自建歌单 | — |
| `lib_empty_playlists_desc` | Mix local lossless and Subsonic tracks with auto 2×2 album collage or custom gallery covers. | 支持混收本地无损与 Subsonic 曲目，自动拼出 2×2 专辑四宫格封面或自选相册封面 | — |
| `lib_create_playlist_now` | Create Playlist | 立即新建歌单 | — |
| `lib_play_playlist` | Play playlist | 播放歌单 | — |
| `lib_album_meta` | %1$s · %2$d tracks | %1$s · %2$d 首 | `%1$s`, `%2$d` |
| `lib_artist_meta` | %1$d albums · %2$d tracks | %1$d 张专辑 · %2$d 首 | `%1$d`, `%2$d` |
| `lib_folder_meta` | %1$s · %2$d tracks | %1$s · %2$d 首 | `%1$s`, `%2$d` |
| `lib_empty_favorites` | No favorited local tracks yet | 还没有收藏的本地歌曲 | — |
| `lib_favorites_count` | %1$d favorites | 共收藏 %1$d 首 | `%1$d` |
| `lib_empty_downloads_title` | No Offline Downloads Yet | 还没有离线下载的歌曲 | — |
| `lib_empty_downloads_desc` | Download tracks, playlists, or albums from Subsonic for zero-data Bit-Perfect offline playback. | 在 Subsonic 曲库点击单曲下载、歌单下载或专辑下载后，离线原盘无损文件将显示在这里，断网亦可 Bit-Perfect 直通播放 | — |
| `lib_view_active_downloads` | Active Downloads (%1$d) | 查看下载中（%1$d） | `%1$d` |
| `lib_offline_summary` | %1$d offline · %2$s | 已离线 %1$d 首 · %2$s | `%1$d`, `%2$s` |
| `lib_offline_downloads_label` | Offline Downloads | 离线下载 | — |
| `lib_sort_songs_title` | Sort Songs | 歌曲列表排序 | — |
| `lib_batch_add_to_playlist` | Add to playlist | 批量加入歌单 | — |
| `lib_empty_local_title` | No Local Lossless Music Yet | 还没有本地无损音乐 | — |
| `lib_empty_local_perm_desc` | Grant storage access to scan FLAC / WAV lossless albums in /sdcard/Music. | 授予存储读取权限后，即可一键扫描 /sdcard/Music 中的 FLAC / WAV 无损专辑。 | — |
| `lib_empty_local_scan_desc` | Tap below to scan local folders or import tracks from the file picker. | 点击下方按钮扫描本地目录，或直接从系统文件管理器导入歌曲。 | — |
| `lib_grant_and_scan` | Grant Permission & Scan | 授予权限并扫描 | — |
| `lib_import_via_picker` | Or import via file picker | 或通过文件选择器导入 | — |
| `lib_shuffle_suffix` | %1$s · Shuffle | %1$s · 随机 | `%1$s` |

---

## `player_*` — 黑胶播放与输出 / Now Playing & Audio Output（53 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `player_collapse` | Collapse player | 收起播放页 | — |
| `player_playing_from` | PLAYING FROM | 正在播放自 | — |
| `player_immersive_on_hint` | Immersive mode on · Tap disc for Zen Vinyl | 沉浸音乐模式已开启 · 轻点唱片可进入纯享摆台 | — |
| `player_immersive_off_hint` | Exited immersive mode | 已退出沉浸音乐模式 | — |
| `player_immersive_toggle` | Immersive Mode | 沉浸音乐模式 | — |
| `player_zen_vinyl_hint` | Zen Vinyl · Tap anywhere to restore controls | 纯享黑胶模式 · 轻点任意处恢复操控 | — |
| `player_usb_exclusive_spec` | USB Exclusive %1$s/%2$db | USB 独占 %1$s/%2$db | `%1$s`, `%2$d` |
| `player_usb_exclusive_dev` | USB Exclusive · %1$s | USB 独占 · %1$s | `%1$s` |
| `player_usb_exclusive_short` | USB Exclusive | USB 独占 | — |
| `player_system_output` | Device Output | 本机输出 | — |
| `player_favorite` | Favorite | 收藏 | — |
| `player_unfavorite` | Remove favorite | 取消收藏 | — |
| `player_favorite_song` | Favorite track | 收藏歌曲 | — |
| `player_shuffle_mode` | Shuffle | 随机播放 | — |
| `player_prev` | Previous | 上一曲 | — |
| `player_next` | Next | 下一曲 | — |
| `player_repeat_mode` | Repeat mode | 循环模式 | — |
| `player_repeat_all` | Repeat All | 列表循环 | — |
| `player_repeat_one` | Repeat One | 单曲循环 | — |
| `player_repeat_off` | Sequential | 顺序播放 | — |
| `player_fixed_direct_banner` | Fixed 0 dBFS Pass-Through (ear guard bypassed — use external amp) | 固定直通 0 dBFS（已绕过护耳上限，请用外接耳放调音量） | — |
| `player_mute_toggle` | Toggle mute | 静音切换 | — |
| `player_muted` | Muted | 静音 | — |
| `player_engine_muted` | %1$s · Muted | %1$s · 静音 | `%1$s` |
| `player_ear_guard_on` | Ear Guard %1$d%% | 护耳上限 %1$d%% | `%1$d` |
| `player_ear_guard_off` | Ear Guard Off | 护耳未开启 | — |
| `player_output_device` | Output device | 输出设备 | — |
| `player_lyrics` | Lyrics | 歌词 | — |
| `player_download_track` | Download track | 下载歌曲 | — |
| `player_add_to_playlist` | Add to playlist | 加入歌单 | — |
| `player_queue` | Queue | 播放队列 | — |
| `player_queue_header` | Up Next (%1$d tracks) | 当前队列（%1$d 首） | `%1$d` |
| `player_vol_hud_limit` | Ear guard limit %1$d%% reached (adjustable in Settings) | 已达耳朵保护上限 %1$d%%（可在设置中调整） | `%1$d` |
| `player_vol_hud_direct` | Fixed 0 dBFS pass-through active — adjust volume on external amp | 当前为固定直通 0 dBFS 模式，请用外接耳放调节音量 | — |
| `player_output_sheet_title` | Audio Output & Signal Path | 音频输出与信号链路 | — |
| `player_output_usb_locked` | Bit-Perfect USB Exclusive locked (Android mixer bypassed) | Bit-Perfect USB 独占已锁定（绕过 Android 混音） | — |
| `player_output_android_route` | Routing through standard Android audio pipeline | 当前经由 Android 系统音频通路输出 | — |
| `player_output_source` | Source | 音频源 | — |
| `player_output_idle` | Not playing | 未在播放 | — |
| `player_output_negotiated` | Output | 输出协商 | — |
| `player_output_usb_ready` | Connected %1$s (standby) | 已连接 %1$s（待起播） | `%1$s` |
| `player_output_no_feedback` | No feedback | 无 feedback | — |
| `player_output_clock_feedback` | Clock | 时钟反馈 | — |
| `player_output_clock_detail` | DAC %1$s · Buf %2$.0f ms · Underruns %3$d/%4$d | 设备 %1$s · 缓冲 %2$.0f ms · 欠载 %3$d/%4$d | `%1$s`, `%2$.0f`, `%3$d`, `%4$d` |
| `player_output_select` | Select Output Device | 选择输出设备 | — |
| `player_output_usb_sub` | USB Exclusive · UAC%1$d · %2$d sample rates | USB 独占 · UAC%1$d · %2$d 档采样率 | `%1$d`, `%2$d` |
| `player_output_usb_vid_pid` | VID 0x%1$04X · PID 0x%2$04X (Tap to grant & claim) | VID 0x%1$04X · PID 0x%2$04X（点击授权并独占连接） | `%1$04X`, `%2$04X` |
| `player_output_local_title` | Phone Speaker / 3.5mm / Bluetooth | 本机扬声器 / 有线耳机 / 蓝牙 | — |
| `player_output_local_sub` | Use Android AudioTrack (exit USB Exclusive) | 走系统 AudioTrack（退出 USB 独占模式） | — |
| `player_focus_call` | In call or system focus active · Will resume automatically | 正在通话或系统占用焦点 · 结束后自动开始播放 | — |
| `player_focus_ducking` | Ducking (-12 dB) for prompt · Will restore volume automatically | 导航/提示音避让中 (-12 dB) · 结束后自动恢复原音量 | — |
| `player_focus_transient` | Audio focus temporarily paused · Will resume automatically | 音频焦点被临时占用 · 结束后自动恢复播放 | — |
| `player_focus_lost` | Audio focus taken by another app · Tap play to reclaim | 音频焦点已被其他应用接管 · 点击播放可重新抢占 | — |

---

## `lyrics_*` — 沉浸同步歌词 / Immersive Lyrics（55 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `lyrics_zen_badge` | Zen Lyrics | 纯享歌词 | — |
| `lyrics_vinyl_btn` | Vinyl | 黑胶 | — |
| `lyrics_searching` | Searching for synced lyrics… | 正在查找匹配歌词… | — |
| `lyrics_empty_title` | No Lyrics Found | 暂未找到歌词 | — |
| `lyrics_empty_desc` | Search the free open-source LRCLIB database or import a local .lrc file. | 可在线检索免费开源歌词库（LRCLIB），或直接导入本地 .lrc 歌词文件 | — |
| `lyrics_search_online` | Search Online | 在线搜词 | — |
| `lyrics_import_lrc` | Import .lrc | 导入 .lrc | — |
| `lyrics_plain_notice` | Plain lyrics (unsynced) | 纯文本歌词（无时间轴） | — |
| `lyrics_find_synced` | Find Synced | 搜同步词 | — |
| `lyrics_back_to_active` | Back to current | 回到当前句 | — |
| `lyrics_offset` | Offset | 偏移 | — |
| `lyrics_style` | Style | 样式 | — |
| `lyrics_find` | Search | 搜词 | — |
| `lyrics_sheet_search_title` | Search & Replace Lyrics | 搜索与更换歌词 | — |
| `lyrics_sheet_search_sub` | Source: LRCLIB open-source synced lyrics / local .lrc | 数据源：LRCLIB 开源同步歌词库 / 本地 .lrc | — |
| `lyrics_search_hint` | Enter song title or artist… | 输入歌名 / 艺人关键词… | — |
| `lyrics_no_candidates` | No matching lyrics found. Try shorter keywords or import a local .lrc. | 未搜到匹配候选，可尝试精简关键词或导入本地 .lrc | — |
| `lyrics_tag_synced` | Synced | 同步 | — |
| `lyrics_tag_plain` | Plain | 纯文本 | — |
| `lyrics_restore_auto` | Restore Auto-Match | 恢复自动匹配 | — |
| `lyrics_style_sheet_title` | Immersive Lyrics Style | 沉浸歌词样式 | — |
| `lyrics_style_sheet_sub` | Live preview updates on the lyrics screen above | 调节参数，上方歌词实时生效 | — |
| `lyrics_style_backdrop` | Ambient Backdrop | 氛围背景 | — |
| `lyrics_style_highlight` | Highlight Effect | 高亮动效 | — |
| `lyrics_style_font` | Typography | 歌词字体 | — |
| `lyrics_style_font_size` | Font Size | 字号大小 | — |
| `lyrics_style_spacing` | Line Spacing | 行距留白 | — |
| `lyrics_style_alignment` | Alignment | 排版对齐 | — |
| `lyrics_style_bold_active` | Bold Active Line | 当前演唱句加粗强调 | — |
| `lyrics_style_show_translation` | Show Bilingual Translation | 显示双语歌词译文 | — |
| `lyrics_style_auto_hide` | Zen Lyrics (hide controls after 5s) | 沉浸纯享态（5 秒无操作隐藏控件） | — |
| `lyrics_preview_badge` | Live Preview · %1$dsp | 实时预览 · %1$dsp | `%1$d` |
| `lyrics_preview_line1` | Needle drops onto spinning warm vinyl | 针尖轻落入黑胶微光 | — |
| `lyrics_preview_line2` | Melody and aurora blend softly in the dark | 旋律与光影在夜色里缓缓相融 | — |
| `lyrics_preview_line3` | Every bit-perfect frame preserves the live room | 每一帧采样都还原现场呼吸 | — |
| `lyrics_backdrop_aurora_short` | Fluid Aurora | 流体多色光晕 | — |
| `lyrics_backdrop_blur_short` | Soft Blur | 封面暗调虚化 | — |
| `lyrics_backdrop_dark_short` | Minimal Dark | 极简深黑 | — |
| `lyrics_hl_sweep_short` | Sweep Glow | 逐句流光扫色 | — |
| `lyrics_hl_pure_short` | Pure Glow | 柔光纯白 | — |
| `lyrics_hl_accent_short` | Accent Tint | 主题强调色 | — |
| `lyrics_hl_capsule_short` | Glass Pill | 玻璃聚焦条 | — |
| `lyrics_font_sans_short` | Modern Sans | 现代无衬线 | — |
| `lyrics_font_serif_short` | Vinyl Serif | 文艺黑胶衬线 | — |
| `lyrics_font_rounded_short` | Soft Wide | 温润舒展 | — |
| `lyrics_font_mono_short` | Studio Mono | 录音室等宽 | — |
| `lyrics_spacing_compact_short` | Compact | 紧凑 | — |
| `lyrics_spacing_comfort_short` | Balanced | 舒展 | — |
| `lyrics_spacing_relaxed_short` | Relaxed | 宽松 | — |
| `lyrics_source_custom` | Custom LRC | 自定义 LRC | — |
| `lyrics_source_sidecar` | Local LRC | 本地 LRC | — |
| `lyrics_source_embedded` | Embedded | 音频内嵌 | — |
| `lyrics_source_subsonic` | Subsonic | Subsonic | — |
| `lyrics_source_lrclib` | LRCLIB Online | LRCLIB 在线 | — |
| `lyrics_source_none` | No Lyrics | 无歌词 | — |

---

## `usb_*` — USB DAC 诊断仪表 / USB DAC Diagnostics（46 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `usb_title` | USB DAC | USB 设备 | — |
| `usb_scan` | Scan USB | 扫描 USB | — |
| `usb_status_streaming` | USB Exclusive Streaming Active | USB 独占输出运行中 | — |
| `usb_status_ready` | USB DAC Connected & Ready | USB DAC 已连接就绪 | — |
| `usb_status_disconnected` | No USB DAC Connected | 未连接 USB DAC | — |
| `usb_disconnect` | Disconnect | 断开 | — |
| `usb_metric_rate` | Sample Rate | 协商采样率 | — |
| `usb_metric_standby` | Standby | 待机 | — |
| `usb_metric_bits` | Depth / Slot | 位深 / 容器 | — |
| `usb_metric_feedback` | Async Feedback | Feedback 实测 | — |
| `usb_metric_buffer` | Buffer / Underrun | 缓冲 / 欠载 | — |
| `usb_empty_hint` | Plug in a USB DAC via OTG and tap below to grant exclusive Bit-Perfect access. Falls back to device audio when unplugged. | 通过 OTG 接入 USB DAC 后，点击下方设备授权即可启用 Bit-Perfect 独占驱动。未连接时将自动回退至本机音频输出。 | — |
| `usb_exclusive_title` | USB Exclusive Output (Bit-Perfect) | USB 独占输出（Bit-Perfect） | — |
| `usb_exclusive_desc` | Bypass Android AudioFlinger resampling with direct iso URBs | 绕过 Android AudioFlinger 重采样，直接发 iso URB | — |
| `usb_detected_header` | Detected USB Audio Devices (%1$d) | 已检测到的 USB 音频设备（%1$d） | `%1$d` |
| `usb_none_detected` | No USB Audio Class device detected. Check cable & OTG setting. | 未检测到 USB Audio Class 设备。请确认 DAC 已插紧且手机已开启 OTG。 | — |
| `usb_connected` | Connected | 已连接 | — |
| `usb_connect` | Connect | 连接 | — |
| `usb_diag_title` | Diagnostics & Test Tone | 工程诊断与测试音工具 | — |
| `usb_diag_sub` | UAC descriptors / Alt Setting / 440Hz & 1kHz test tone / Logs | UAC 描述符 / Alt Setting / 440Hz & 1kHz 纯音测试 / 日志 | — |
| `usb_diag_uac_info` | UAC%1$d · AC IF %2$d · | UAC%1$d · AC 接口 %2$d · | `%1$d`, `%2$d` |
| `usb_diag_no_clock` | No dedicated clock | 无独立 Clock | — |
| `usb_tone_rate` | Test Tone Rate | 测试音采样率 | — |
| `usb_tone_amp` | Amplitude | 测试音幅度 | — |
| `usb_tone_stopped` | Test tone stopped | 测试音已停止 | — |
| `usb_tone_playing_440` | Playing 440Hz @ %1$d Hz | 播放 440Hz @ %1$d Hz | `%1$d` |
| `usb_tone_playing_1k` | Playing 1kHz @ %1$d Hz | 播放 1kHz @ %1$d Hz | `%1$d` |
| `usb_tone_failed` | Failed to start test tone | 测试音启动失败 | — |
| `usb_tone_stop_btn` | Stop Tone | 停止测试音 | — |
| `usb_tone_play_440` | Play 440Hz | 播放 440Hz | — |
| `usb_tone_play_1k` | Play 1kHz | 播放 1kHz | — |
| `usb_no_live_stats` | No live stream stats | 无实时统计 | — |
| `usb_recent_logs` | Recent Logs | 最近日志 | — |
| `usb_log_attached` | USB audio device attached (%1$d total) | 检测到 USB 音频设备接入（共 %1$d 台） | `%1$d` |
| `usb_log_detached` | USB device unplugged | USB 设备已拔出 | — |
| `usb_log_native_ok` | Native USB audio library loaded | native 音频库已加载 | — |
| `usb_log_native_fail` | Failed to load native library (check arm64-v8a ABI) | native 库加载失败，请检查 ABI (arm64-v8a) | — |
| `usb_log_req_perm` | Requesting USB permission: %1$s | 请求权限: %1$s | `%1$s` |
| `usb_log_perm_denied` | USB permission denied | USB 权限被拒绝 | — |
| `usb_log_open_null` | openDevice returned null (permission denied or busy) | openDevice 返回 null（权限不足或被其他进程占用） | — |
| `usb_log_claim_fail` | claimInterface(if=%1$d) failed — kernel driver may still hold it | claimInterface(if=%1$d, force=true) 失败，内核驱动可能占着 | `%1$d` |
| `usb_log_no_audio_if` | Device exposes no USB Audio Class interface | 设备没有音频类接口，无法以 UAC 模式使用 | — |
| `usb_log_open_fail` | Open failed: %1$s | 打开失败: %1$s | `%1$s` |
| `usb_log_desc_parse_fail` | Failed to parse UAC descriptor JSON | 描述符 JSON 解析失败 | — |
| `usb_log_connected_hw` | Connected %1$s (UAC%2$d, %3$d rates, HW vol %4$.1f..%5$.1f dB) | 已连接 %1$s (UAC%2$d, %3$d 档采样率, 支持硬件音量 %4$.1f..%5$.1fdB) | `%1$s`, `%2$d`, `%3$d`, `%4$.1f`, `%5$.1f` |
| `usb_log_connected_sw` | Connected %1$s (UAC%2$d, %3$d rates, log SW vol) | 已连接 %1$s (UAC%2$d, %3$d 档采样率, 软件对数音量) | `%1$s`, `%2$d`, `%3$d` |

---

## `settings_*` — 设置页 / Settings（170 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `settings_title` | Settings | 设置 | — |
| `settings_group_audio` | Audio Output & USB Exclusive | 音频输出与 USB 独占 | — |
| `settings_usb_exclusive_title` | USB Exclusive Output (Bit-Perfect) | USB 独占输出（Bit-Perfect） | — |
| `settings_usb_exclusive_sub` | Bypass system resampling and stream directly via libusb | 绕过系统重采样，经 libusb 直送 USB DAC | — |
| `settings_sample_rate` | Sample Rate Policy | 采样率策略 | — |
| `settings_bit_depth` | Output Bit Depth | 输出位深 | — |
| `settings_bit_depth_sheet` | Output Bit Depth Preference | 输出位深偏好 | — |
| `settings_buffer_size` | Ring Buffer Size | 环形缓冲大小 | — |
| `settings_volume_mode` | USB Volume Control Mode | USB 音量控制模式 | — |
| `settings_ear_guard_title` | Hearing Protection Ceiling | 耳朵音量保护 | — |
| `settings_ear_guard_on_sub` | Capped at %1$d%% (%2$s dB) to prevent accidental ear blasts | 已锁定最大上限 %1$d%% (%2$s dB)，防止误触炸耳 | `%1$d`, `%2$s` |
| `settings_ear_guard_off_sub` | Disabled (up to 100%% / 0 dBFS — protect your hearing) | 已关闭（可调节至 100%% / 0 dBFS，请注意保护听力） | — |
| `settings_max_vol_title` | Maximum Volume Limit | 最大音量保护上限 | — |
| `settings_max_vol_sheet` | Set Hearing Protection Ceiling | 设置耳朵音量保护上限 | — |
| `settings_max_vol_opt_default` | Recommended default (~%1$.0f dB, comfortable for IEMs) | 推荐默认（约 %1$.0f dB，耳塞舒适上限） | `%1$.0f` |
| `settings_max_vol_opt_high` | Very high level (~%1$.0f dB, high-impedance headphones only) | 极高电平（约 %1$.0f dB，仅建议高阻大耳） | `%1$.0f` |
| `settings_safe_replug_title` | Reset to Safe Volume on DAC Replug | 重新接入 DAC 回落至安全音量 | — |
| `settings_safe_replug_sub` | Automatically attenuate to %1$d%% if last level exceeded 35%% | 重插 DAC 时若上次高于 35%%，自动回落至 %1$d%% 起步 | `%1$d` |
| `settings_safe_startup_title` | Safe Startup Volume | 安全起步音量 | — |
| `settings_safe_startup_sheet` | DAC Safe Startup Volume | 接入 DAC 安全起步音量 | — |
| `settings_vol_step_title` | Hardware Volume Key Step | 物理音量键步进 | — |
| `settings_vol_step_sub` | %1$d%% per press (physical keys hijacked in USB Exclusive mode) | 每按一次增减 %1$d%%（USB 独占模式接管物理键） | `%1$d` |
| `settings_vol_step_sheet` | Volume Key Step Size | 物理音量键单步增量 | — |
| `settings_vol_step_1_label` | 1%% (Ultra-Fine · 100 steps) | 1%%（极精细 · 100 级） | — |
| `settings_vol_step_1_sub` | ~0.6 dB per step | 每格约 0.6 dB | — |
| `settings_vol_step_2_label` | 2%% (Recommended · 50 steps) | 2%%（推荐 · 50 级） | — |
| `settings_vol_step_2_sub` | ~1.2 dB per step, smooth & responsive | 每格约 1.2 dB，细腻且顺手 | — |
| `settings_vol_step_5_label` | 5%% (Fast · 20 steps) | 5%%（快速 · 20 级） | — |
| `settings_vol_step_5_sub` | ~3.0 dB per step | 每格约 3.0 dB | — |
| `settings_feedback_title` | Async Feedback Clock Pacing | Feedback 异步时钟闭环微调 | — |
| `settings_feedback_sub` | Lock host packet pacing to DAC crystal oscillator to prevent drift | 跟随 DAC 反馈端点自动对齐晶振速率，防止长播漂移 | — |
| `settings_reset_alt_title` | Reset Alt Setting Before Rate Switch | 切采样率前复位 Alt Setting | — |
| `settings_reset_alt_sub` | Compatibility workaround for certain XMOS DACs (alt=0 first) | 兼容部分 XMOS 方案 DAC（切率时先切 alt=0） | — |
| `settings_group_playback` | Playback Control | 播放控制 | — |
| `settings_audio_focus` | System Audio Focus Policy | 系统音频焦点策略 | — |
| `settings_audio_focus_sheet` | Audio Focus Interaction Policy | 系统音频焦点交互策略 | — |
| `settings_gapless_title` | Gapless Playback | 无缝播放（Gapless） | — |
| `settings_gapless_sub` | Keep USB stream alive seamlessly across same-sample-rate tracks | 同采样率连续曲目保持 USB 流不断开直接续流 | — |
| `settings_fade_title` | Click-Free Soft Fade | 防爆音淡入淡出 | — |
| `settings_fade_sub` | Apply 5ms smooth ramp on pause, seek, and sample-rate changes | 暂停、Seek、切率时施加 5ms 平滑淡变 | — |
| `settings_unplug_pause_title` | Auto-Pause on Unplug | 耳机与 USB 拔出自动暂停 | — |
| `settings_unplug_pause_sub` | Immediately pause playback when headphones or USB DAC disconnect | 检测到输出设备断开时立即暂停播放 | — |
| `settings_remember_pos_title` | Remember Playback Position | 记住播放断点 | — |
| `settings_remember_pos_sub` | Show Resume card on launch without auto-playing audio | 重启后在首页显示「继续上次播放」卡片，不自动出声 | — |
| `settings_group_library` | Library & Import | 资料库与导入 | — |
| `settings_wifi_title` | WiFi LAN Music Transfer | WiFi 局域网传歌 | — |
| `settings_wifi_running_sub` | Running · %1$s (open on mobile or PC browser) | 运行中 · %1$s（手机/PC 浏览器均可访问） | `%1$s` |
| `settings_wifi_idle_sub` | Scan QR or drag lossless folders from browser with dedup & history | 手机或电脑浏览器扫码/局域网传无损，支持防重与上传历史 | — |
| `settings_scan_dirs` | Scan Directories | 扫描目录管理 | — |
| `settings_import_mode` | Track Import Mode | 歌曲导入方式 | — |
| `settings_min_duration` | Filter Short Audio | 过滤短音频 | — |
| `settings_group_subsonic` | Media Server (Subsonic) | 媒体服务器（Subsonic） | — |
| `settings_subsonic_accounts` | Servers & Accounts | 服务器与账号管理 | — |
| `settings_subsonic_not_signed_in` | Not signed in · Supports Navidrome / Subsonic / Airsonic / Gonic | 尚未登录 · 支持 Navidrome / Subsonic / Airsonic / Gonic 等 | — |
| `settings_subsonic_signed_in` | %1$d saved · Active: %2$s (%3$s) | 已保存 %1$d 个账号 · 当前：%2$s（%3$s） | `%1$d`, `%2$s`, `%3$s` |
| `settings_subsonic_wifi_q` | Wi-Fi Streaming Quality | Wi-Fi 流媒体音质 | — |
| `settings_subsonic_cell_q` | Cellular Streaming Quality | 移动网络流媒体音质 | — |
| `settings_subsonic_scrobble_title` | Sync Play History (Scrobble) | 同步播放记录（Scrobble） | — |
| `settings_subsonic_scrobble_sub` | Report Now Playing and completed plays to Subsonic server | 向 Subsonic 服务器上报正在播放与听完计数 | — |
| `settings_dl_storage` | Offline Download Location | 歌曲/歌单下载存储位置 | — |
| `settings_dl_storage_sheet` | Offline Download Storage | 离线下载存储位置 | — |
| `settings_dl_quality` | Offline Download Quality | 离线下载音质 | — |
| `settings_dl_wifi_only_title` | Download Over Wi-Fi Only | 仅 Wi-Fi 下载歌曲与歌单 | — |
| `settings_dl_wifi_only_sub` | Prompt for confirmation before downloading on cellular data | 移动网络点击下载时先弹窗确认，防止意外消耗流量 | — |
| `settings_dl_manager_title` | Offline Downloads Manager | 离线下载文件管理 | — |
| `settings_dl_manager_empty` | No remote tracks downloaded yet | 尚未下载任何远程歌曲 | — |
| `settings_dl_manager_summary` | %1$d offline (%2$s) · View queue & storage | 已离线 %1$d 首（共 %2$s）· 查看队列与清理 | `%1$d`, `%2$s` |
| `settings_cover_cache_title` | Clear Remote Cover Cache | 清除远程封面缓存 | — |
| `settings_cache_empty` | Cache is empty | 当前无缓存 | — |
| `settings_cover_cache_mb` | %1$.1f MB used · Tap to clear | 已占用 %1$.1f MB · 点击清理 | `%1$.1f` |
| `settings_cover_cache_kb` | %1$d KB used · Tap to clear | 已占用 %1$d KB · 点击清理 | `%1$d` |
| `settings_group_appearance` | Appearance, Language & Vinyl | 外观、语言与黑胶风格 | — |
| `settings_language_title` | Language | 应用语言 | — |
| `settings_language_sheet_title` | App Language | 选择应用语言 | — |
| `settings_language_follow_system` | Follow System | 跟随系统 | — |
| `settings_language_follow_system_sub` | Automatically match system language (currently %1$s) | 自动匹配系统语言（当前：%1$s） | `%1$s` |
| `settings_language_zh_sub` | Simplified Chinese | 简体中文 | — |
| `settings_language_en_sub` | English | English（英文） | — |
| `settings_cover_style` | Now Playing Visual Style | 播放页视觉样式 | — |
| `settings_dark_canvas` | Dark Background Canvas | 深色背景底色 | — |
| `settings_accent_style` | Accent Color | 主题强调色 | — |
| `settings_ambient_bg_title` | Dynamic Cover Ambient Glow | 封面动态氛围背景 | — |
| `settings_ambient_bg_sub` | Extract album palette for dark ambient gradients on Player & MiniBar | 提取专辑主色渲染播放页与 MiniPlayer 暗调渐变 | — |
| `settings_immersive_title` | Immersive Music Mode | 沉浸音乐模式 | — |
| `settings_immersive_sub` | Hide status & nav bars for distraction-free edge-to-edge vinyl listening | 隐藏状态栏与导航栏，边到边无干扰黑胶听音（边缘内滑呼出系统栏） | — |
| `settings_immersive_scope` | Immersive Scope | 沉浸生效范围 | — |
| `settings_immersive_scope_sheet` | Immersive Mode Scope | 沉浸音乐模式生效范围 | — |
| `settings_hide_status_title` | Hide Status Bar | 隐藏顶部状态栏 | — |
| `settings_hide_status_sub` | Hide clock, battery, and notification icons | 隐藏时间、电量与顶部通知图标 | — |
| `settings_hide_nav_title` | Hide Navigation Bar | 隐藏底部导航栏 | — |
| `settings_hide_nav_sub` | Hide back/home/recents buttons or gesture pill | 隐藏返回/主页/多任务三键或手势条 | — |
| `settings_keep_screen_on_title` | Keep Screen On in Immersive | 沉浸时保持屏幕常亮 | — |
| `settings_keep_screen_on_sub` | Active only while playing on Now Playing — ideal for desktop DAC stand | 仅播放页播放中生效，适合接 USB DAC 作桌面黑胶摆台 | — |
| `settings_group_lyrics` | Lyrics & Immersive Style | 歌词与沉浸样式 | — |
| `settings_lyrics_backdrop` | Lyrics Backdrop Style | 沉浸背景风格 | — |
| `settings_lyrics_backdrop_sheet` | Immersive Lyrics Backdrop | 歌词沉浸背景风格 | — |
| `settings_lyrics_highlight` | Active Line Highlight | 当前句高亮动效 | — |
| `settings_lyrics_highlight_sheet` | Active Lyric Highlight Effect | 歌词当前句高亮动效 | — |
| `settings_lyrics_font` | Lyrics Font Family | 歌词字体风格 | — |
| `settings_lyrics_size` | Lyrics Font Size | 歌词字号 | — |
| `settings_lyrics_size_small` | S | 小 | — |
| `settings_lyrics_size_large` | L | 大 | — |
| `settings_lyrics_spacing` | Line Spacing | 行距与留白 | — |
| `settings_lyrics_spacing_sheet` | Lyrics Line Spacing | 歌词行距与留白 | — |
| `settings_lyrics_spacing_detail` | Gap %1$ddp · %2$.2fx line height | 句间距 %1$ddp · %2$.2fx 行高 | `%1$d`, `%2$.2f` |
| `settings_lyrics_align` | Lyrics Alignment | 歌词排版对齐 | — |
| `settings_lyrics_bold_title` | Bold Active Line | 当前演唱句加粗强调 | — |
| `settings_lyrics_bold_sub` | Use bold weight for current line; off for softer poetic styling | 开启时高亮句使用粗体聚焦；关闭时采用柔和字重更显诗意 | — |
| `settings_lyrics_trans_title` | Show Bilingual Translation | 显示双语歌词译文 | — |
| `settings_lyrics_trans_sub` | Display translated lines below original text for bilingual .lrc files | 检测到同时间戳双语 LRC 时在原文下方呈现译文 | — |
| `settings_lyrics_autohide_title` | Zen Lyrics Auto-Hide | 沉浸纯享态（自动隐藏控件） | — |
| `settings_lyrics_autohide_sub` | Fade out chrome after 5s of inactivity; tap anywhere to wake | 全屏歌词播放 5 秒无操作自动淡出顶/底栏，仅留流光与歌词，轻点即唤醒 | — |
| `settings_lyrics_mini_title` | Single-Line Lyrics Preview | 播放页单行歌词预览 | — |
| `settings_lyrics_mini_sub` | Show live active lyric below the vinyl disc; tap to open full lyrics | 在黑胶唱片下方轻盈显示当前演唱句，点击可展开全屏沉浸歌词 | — |
| `settings_lyrics_auto_online_title` | Auto-Match Online Lyrics (LRCLIB) | 自动在线匹配歌词（LRCLIB） | — |
| `settings_lyrics_auto_online_sub` | Automatically fetch and cache synced lyrics when none exist locally | 本地与 Subsonic 无歌词时自动向开源免费库匹配并缓存离线 | — |
| `settings_lyrics_cache_title` | Clear Lyrics Cache | 清除歌词缓存 | — |
| `settings_lyrics_cache_kb` | %1$d KB used · Tap to clear downloaded & cached lyrics | 已占用 %1$d KB · 点击清空已下载与缓存的歌词 | `%1$d` |
| `settings_group_background` | Notifications, Lockscreen & Background | 通知、锁屏与后台保活 | — |
| `settings_lockscreen_title` | Vinyl Lockscreen | 启用黑胶锁屏界面 | — |
| `settings_lockscreen_sub` | Show Sonify vinyl cover on lockscreen while playing; swipe up to dismiss | 播放时熄屏再亮屏呈现 Sonify 黑胶锁屏页，上滑即可退出 | — |
| `settings_notif_spec_title` | Show HiFi Spec in Notification | 通知栏显示 HiFi 音频规格 | — |
| `settings_notif_spec_sub` | Display live USB Exclusive sample rate & bit depth in media notification | 在通知栏与原生锁屏实时显示 USB 独占采样率/位深 | — |
| `settings_notif_perm_title` | Notification Settings | 系统通知栏权限设置 | — |
| `settings_notif_perm_sub` | Ensure playback controls stay visible on lockscreen and shade | 确保锁屏与通知栏媒体控制常驻不被系统隐藏 | — |
| `settings_battery_title` | Battery Optimization Whitelist | 后台电池优化白名单 | — |
| `settings_battery_on` | Whitelisted (stable screen-off playback) | 已加入白名单（息屏播放更稳定） | — |
| `settings_battery_off` | Not whitelisted · Tap to grant unrestricted background access | 未加入，点击前往系统设置授权 | — |
| `settings_keepalive_title` | Screen-Off Keep-Alive Guide | 息屏防杀后台保活指引 | — |
| `settings_keepalive_sub` | Tips to prevent OEM background killers from stopping audio | 查看各品牌手机后台不中断设置建议 | — |
| `settings_wakelock_title` | CPU WakeLock While Playing | 播放时保持 CPU 唤醒（WakeLock） | — |
| `settings_wakelock_sub` | Zero underruns with screen off; automatically released on pause | 配合音频高优先级调度，息屏持续发包零欠载，暂停自动释放 | — |
| `settings_group_update` | Updates, Diagnostics & About | 应用更新与崩溃诊断 | — |
| `settings_check_update_title` | Check for Updates | 检查应用更新 | — |
| `settings_check_update_new` | New version v%1$s available · Tap to view notes & upgrade | 发现新版本 v%1$s · 点击查看日志并安全升级 | `%1$s` |
| `settings_check_update_cur` | Current v%1$s (build %2$d) · SHA-256 verified | 当前版本 v%1$s (build %2$d) · SHA-256 校验 | `%1$s`, `%2$d` |
| `settings_changelog_title` | Release Changelog | 版本更新日志 | — |
| `settings_changelog_sub` | View v%1$s and full version history | 查看 v%1$s 及历史版本更新内容与特性说明 | `%1$s` |
| `settings_github_title` | GitHub Repository | GitHub 开源主页 | — |
| `settings_github_sub` | github.com/AceInAndroid/sonify · Non-commercial only | github.com/AceInAndroid/sonify · 仅限个人非商用（禁止商用） | — |
| `settings_auto_update_title` | Auto-Check Updates | 自动检查更新 | — |
| `settings_auto_update_sub` | Silently check every 12 hours on launch without interrupting playback | 启动时按 12 小时节流静默检查，不打扰听音 | — |
| `settings_auto_crash_title` | Auto-Report Sanitized Crash Logs | 自动上报脱敏崩溃日志 | — |
| `settings_auto_crash_sub` | Uploads only stack trace & device model — paths & credentials stripped | 发生闪退后仅上报异常堆栈与设备型号，自动剥离路径与账号信息 | — |
| `settings_analytics_title` | Anonymous DAU & Feature Telemetry | 匿名日活与功能统计 | — |
| `settings_analytics_sub` | Tracks only anonymous install ID, version & USB DAC model — zero privacy data | 仅统计匿名装机日活、版本与 USB DAC 型号，不含歌名、路径或个人隐私 | — |
| `settings_crash_logs_title` | Local Crash Records | 查看本地崩溃记录 | — |
| `settings_crash_logs_count` | %1$d sanitized reports saved · Copy or upload manually | 已保存 %1$d 条脱敏诊断记录 · 支持复制或手动补报 | `%1$d` |
| `settings_crash_logs_empty` | No crash reports · Isolated :crash recovery guard active | 当前无本地崩溃记录 · 独立进程恢复守护运行中 | — |
| `settings_debug_server_title` | Update & Diagnostics Server (Debug) | 更新与诊断服务地址（Debug） | — |
| `settings_debug_crash_title` | Simulate Test Crash (Debug) | 模拟一次测试崩溃（Debug） | — |
| `settings_debug_crash_sub` | Verify :crash recovery screen, 3s auto-restart & sanitized upload | 用于真机验证独立 :crash 进程恢复页、3 秒自动重启与日志上报 | — |
| `settings_license_footer` | License: Sonify Non-Commercial v1.0 (No Commercial Use) · Tap to view | 开源协议：Sonify Non-Commercial v1.0（禁止商用） · 点击查看 | — |
| `settings_warn_direct_title` | High-Volume Hearing Warning | 高音量炸耳警告 | — |
| `settings_warn_direct_msg` | Fixed 100%% Pass-Through drives the USB DAC at full 0 dBFS, bypassing ear protection! Only use with an external headphone amp or preamp. Continue? | 开启「固定直通 100%%」将以满幅 0 dBFS 直接驱动 USB DAC，音量保护将被绕过！仅限外接独立耳放/功放时使用，直推耳机/耳塞极可能损伤听力。确认继续吗？ | — |
| `settings_warn_raise_title` | Raise Volume Limit to %1$d%%? | 确认提高音量上限至 %1$d%%？ | `%1$d` |
| `settings_warn_raise_msg` | Levels above 80%% (~%1$s dB) can be extremely loud on sensitive IEMs. Remove earphones before confirming. | 超过 80%%（约 %1$s dB）在高灵敏度耳塞上可能非常响亮，建议先摘下耳机再确认。 | `%1$s` |
| `settings_warn_disable_title` | Disable Hearing Protection? | 确认关闭耳朵音量保护？ | — |
| `settings_warn_disable_msg` | Disabling allows volume up to 100%% (0 dBFS). Accidental button presses may cause sudden loud output. | 关闭后音量可调至 100%%（0 dBFS），误触物理按键或拖动滑块可能造成突然大音量。建议保持开启。 | — |
| `settings_warn_confirm` | I Understand the Risk | 我已确认风险 | — |
| `settings_keepalive_dialog_title` | Background Keep-Alive Tips | 后台息屏保活建议 | — |
| `settings_keepalive_dialog_body` | For glitch-free screen-off USB Exclusive playback, we recommend 3 steps: ↵  ↵ 1. Enable Battery Optimization Whitelist (Unrestricted) on this page. ↵ 2. Allow background activity & auto-start for Sonify in system app info. ↵ 3. Lock the Sonify card in Recents to prevent one-tap memory cleaners. ↵  ↵ (Note: Sonify already runs USB audio threads at high priority with playback WakeLock.) | 为了保证息屏连续播放 USB 独占无损音频不断流，建议完成以下 3 步： ↵  ↵ 1. 开启本页「后台电池优化白名单」（设为无限制）。 ↵ 2. 在系统应用管理中允许 Sonify「后台高耗电 / 自启动」。 ↵ 3. 在最近任务列表中下拉或长按锁定 Sonify 卡片，防止一键清理误杀。 ↵  ↵ （注：Sonify 已自动为 USB 音频线程启用高优先级调度与播放期 WakeLock。） | — |
| `settings_scan_dirs_remove` | Remove directory | 删除目录 | — |
| `settings_scan_now` | Scan Now | 立即扫描 | — |
| `settings_add_and_scan` | Add & Scan | 添加并扫描 | — |
| `settings_debug_server_dialog_title` | Update & Diagnostics Endpoint | 更新与诊断服务地址 | — |
| `settings_debug_server_dialog_desc` | Default official endpoint: %1$s. Enter an http:// or https:// root URL to point to a LAN or private mirror. | 默认对接官方自建服务 %1$s；如需切换局域网测试或私有镜像，可在此填写以 http:// 或 https:// 开头的根地址。 | `%1$s` |
| `settings_debug_crash_dialog_title` | Simulate a Test Crash? | 模拟一次测试崩溃？ | — |
| `settings_debug_crash_dialog_msg` | Throws a test exception on the main thread to verify SonifyCrashHandler, :crash recovery page, 3s auto-restart, and sanitized report upload. | 将在主线程抛出测试异常以验证 SonifyCrashHandler 拦截、独立 :crash 进程恢复页、3 秒自动重启及脱敏日志保存/上报流程。 | — |
| `settings_debug_crash_confirm` | Trigger Now | 立即模拟 | — |

---

## `pref_*` — 偏好枚举选项 / Preference Enums（95 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `pref_sr_follow` | Follow Source (Recommended) | 跟随源文件（推荐） | — |
| `pref_sr_44100` | Fixed 44.1 kHz | 固定 44.1 kHz | — |
| `pref_sr_48000` | Fixed 48 kHz | 固定 48 kHz | — |
| `pref_sr_96000` | Fixed 96 kHz | 固定 96 kHz | — |
| `pref_sr_192000` | Fixed 192 kHz | 固定 192 kHz | — |
| `pref_bits_auto` | Auto Match (Recommended) | 自动匹配（推荐） | — |
| `pref_bits_24` | 24-bit (Portable DAC Compatible) | 24-bit（兼容便携 DAC） | — |
| `pref_bits_32` | 32-bit Container | 32-bit 容器 | — |
| `pref_buf_low` | Low Latency (100 ms) | 低延迟（100 ms） | — |
| `pref_buf_balanced` | Balanced (180 ms · Recommended) | 均衡（180 ms · 推荐） | — |
| `pref_buf_safe` | Anti-Jitter (300 ms) | 抗抖动（300 ms） | — |
| `pref_vol_auto_label` | Smart Hardware First (Recommended) | 智能硬件优先（推荐） | — |
| `pref_vol_auto_sub` | Uses DAC hardware volume for Bit-Perfect output; falls back to log software attenuation | 优先调 DAC 硬件音量保持 Bit-Perfect，无硬件音量时自动切对数软件音量 | — |
| `pref_vol_sw_label` | Force Log Software Volume | 强制对数软件音量 | — |
| `pref_vol_sw_sub` | Always apply 32-bit float logarithmic dB attenuation in C++ native layer | 始终在 C++ 端用对数 dB 曲线做 32-bit 高精度衰减 | — |
| `pref_vol_direct_label` | Fixed 100%% Pass-Through (External Amp Only) | 固定直通 100%%（仅外接耳放） | — |
| `pref_vol_direct_sub` | Full 0 dBFS output — hearing hazard on direct IEMs; external amp/preamp only | 0 dBFS 满幅输出，直推耳塞有炸耳风险，仅限外接独立耳放/前级 | — |
| `pref_vol_engine_hw` | HW Vol | 硬件音量 | — |
| `pref_vol_engine_sw` | SW Log Vol | 对数软件音量 | — |
| `pref_vol_engine_direct` | Direct 0 dBFS | 直通 0 dBFS | — |
| `pref_import_ref` | Reference Original Path | 仅引用原路径 | — |
| `pref_import_copy` | Copy to HiFiMusic Library | 拷贝到 HiFiMusic 资料库 | — |
| `pref_dur_none` | No Filter | 不过滤 | — |
| `pref_dur_15` | Ignore under 15s | 忽略短于 15 秒 | — |
| `pref_dur_30` | Ignore under 30s | 忽略短于 30 秒 | — |
| `pref_cover_vinyl` | Spinning Vinyl Disc | 黑胶唱片（旋转） | — |
| `pref_cover_square` | Minimal Square Art | 极简方形封面 | — |
| `pref_canvas_charcoal` | Spotify Charcoal (#121212) | Spotify 炭黑 (#121212) | — |
| `pref_canvas_oled` | Pure OLED Black (#000000) | OLED 纯黑 (#000000) | — |
| `pref_accent_gold` | Vinyl Warm Gold (#E5A93C) | 黑胶暖金 (#E5A93C) | — |
| `pref_accent_green` | Spotify Green (#1DB954) | Spotify 翠绿 (#1DB954) | — |
| `pref_imm_player_label` | Now Playing & Lockscreen (Recommended) | 仅播放页与锁屏（推荐） | — |
| `pref_imm_player_sub` | Auto-hide system bars on Player & Lockscreen; restore on Library & Settings | 进入黑胶播放页与锁屏时自动隐藏系统栏，回到音乐库与设置自动恢复 | — |
| `pref_imm_global_label` | Entire App | 全局应用 | — |
| `pref_imm_global_sub` | Hide selected system bars everywhere; swipe edge to reveal temporarily | 在 Sonify 所有页面均隐藏所选系统栏，边缘滑动可临时呼出 | — |
| `pref_lrc_size_small` | Compact | 紧凑 | — |
| `pref_lrc_size_medium` | Standard (Recommended) | 标准（推荐） | — |
| `pref_lrc_size_large` | Large | 大字号 | — |
| `pref_lrc_align_left` | Spotify Left-Aligned | Spotify 左对齐 | — |
| `pref_lrc_align_center` | Classic Centered | 经典居中 | — |
| `pref_lrc_bg_aurora_label` | Fluid Multi-Color Aurora (Recommended) | 流体多色氛围光晕（推荐） | — |
| `pref_lrc_bg_aurora_sub` | Three-color drifting ambient orbs from album art, smooth 60fps on all devices | 提取封面三色动态漂移光斑，随播放呼吸律动，全机型 60fps 丝滑渲染 | — |
| `pref_lrc_bg_blur_label` | Soft Blurred Cover | 封面暗调虚化底图 | — |
| `pref_lrc_bg_blur_sub` | Softened album cover blended with dark vignette like vinyl sleeve art | 柔化专辑封面与暗角渐晕融合，呈现黑胶唱片内页质感 | — |
| `pref_lrc_bg_dark_label` | Minimal Dark | 极简深黑 | — |
| `pref_lrc_bg_dark_sub` | Clean dark canvas with subtle accent tint for zero-distraction night reading | 纯净暗调基底叠加微弱主色晕染，夜间零干扰阅读 | — |
| `pref_lrc_hl_sweep_label` | Progressive Sweep (Recommended) | 逐句流光扫色（推荐） | — |
| `pref_lrc_hl_sweep_sub` | Smooth left-to-right sweep synced with vocals & enhanced LRC word tags | 高光随演唱节奏从左向右丝滑扫过，支持增强型 LRC 逐字时间戳 | — |
| `pref_lrc_hl_pure_label` | Pure Soft Glow | 柔光纯白 | — |
| `pref_lrc_hl_pure_sub` | Crisp bright white active line with subtle album-palette outer glow | 纯净高亮白字叠加专辑主色柔和外发光 | — |
| `pref_lrc_hl_accent_label` | Theme Accent Tint | 主题强调色 | — |
| `pref_lrc_hl_accent_sub` | Highlight the active lyric with your Vinyl Gold or Spotify Green accent | 使用当前黑胶暖金或 Spotify 翠绿点亮演唱句 | — |
| `pref_lrc_hl_capsule_label` | Frosted Glass Pill | 暗调玻璃聚焦条 | — |
| `pref_lrc_hl_capsule_sub` | Adds a translucent dark glass capsule behind the active lyric line | 为当前演唱句叠加半透明磨砂聚焦胶囊，焦点醒目 | — |
| `pref_lrc_font_sans_label` | Modern Sans (Recommended) | 现代无衬线（推荐） | — |
| `pref_lrc_font_sans_sub` | Crisp Spotify editorial clarity | Spotify 编辑风清晰利落 | — |
| `pref_lrc_font_sans_sample` | Modern Sans · Editorial | 流光岁月 · Modern Sans | — |
| `pref_lrc_font_serif_label` | Editorial Vinyl Serif | 文艺黑胶衬线 | — |
| `pref_lrc_font_serif_sub` | Classic vinyl liner notes aesthetic | 黑胶内页与古典杂志叙事质感 | — |
| `pref_lrc_font_serif_sample` | Editorial Serif · Classic | 流光岁月 · Editorial Serif | — |
| `pref_lrc_font_rounded_label` | Soft Wide | 温润舒展体 | — |
| `pref_lrc_font_rounded_sub` | Relaxed letter spacing & gentle rhythm | 舒展字距与柔和呼吸感 | — |
| `pref_lrc_font_rounded_sample` | Soft Wide · Airy | 流光岁月 · Soft Wide | — |
| `pref_lrc_font_mono_label` | Studio Master Mono | 母带录音室等宽 | — |
| `pref_lrc_font_mono_sub` | Pro studio timecode typography | 专业录音室提词与时间码美学 | — |
| `pref_lrc_font_mono_sample` | Studio Mono · 24bit | 流光岁月 · Studio Mono | — |
| `pref_lrc_space_compact` | Compact | 紧凑 | — |
| `pref_lrc_space_comfort` | Balanced (Recommended) | 舒展（推荐） | — |
| `pref_lrc_space_relaxed` | Relaxed | 宽松 | — |
| `pref_stream_raw_label` | Original Lossless Raw (Recommended) | 原盘无损直通（推荐） | — |
| `pref_stream_raw_sub` | Zero server transcoding (format=raw), ideal for Bit-Perfect USB DAC | 不经服务端转码（format=raw），完美匹配 USB DAC Bit-Perfect 独占 | — |
| `pref_stream_mp3_320_label` | High Quality MP3 320 kbps | 高音质 MP3 320 kbps | — |
| `pref_stream_mp3_320_sub` | Balanced quality & bandwidth for cellular streaming | 兼顾音质与带宽，适合移动网络聆听 | — |
| `pref_stream_opus_192_label` | Efficient Opus 192 kbps | 流畅 Opus 192 kbps | — |
| `pref_stream_opus_192_sub` | High compression efficiency for weak signal or data saving | 高压缩效率，适合弱网或省流量场景 | — |
| `pref_stream_mp3_192_label` | Data Saver MP3 192 kbps | 省流量 MP3 192 kbps | — |
| `pref_stream_mp3_192_sub` | Widely compatible transcoding to save cellular data | 通用兼容转码档位，显著减少蜂窝流量消耗 | — |
| `pref_dl_public_label` | Public Music (/sdcard/Music/SonifyDownloads) | 公开目录（/sdcard/Music/SonifyDownloads） | — |
| `pref_dl_public_sub` | Organized by Artist/Album, visible in file managers (falls back to private if denied) | 文件按艺人/专辑归档，文件管理器可见并自动纳入本地库（无写权限时自动回落私有目录） | — |
| `pref_dl_private_label` | App Private Storage | App 私有离线目录 | — |
| `pref_dl_private_sub` | Managed inside Sonify only without writing to public Music folder | 仅限 Sonify 内部离线播放与管理，不写入手机公共 Music 目录 | — |
| `pref_focus_duck_label` | Standard · Duck -12 dB (Recommended) | 标准模式 · 压低避让（推荐） | — |
| `pref_focus_duck_sub` | Pauses for calls; temporarily ducks -12 dB for navigation & prompts | 播歌抢占其他应用；来电/语音暂停并自动恢复；导航/提示音临时压低 -12 dB | — |
| `pref_focus_pause_label` | Pure · Pause on Prompt | 纯净模式 · 暂停避让 | — |
| `pref_focus_pause_sub` | Pauses instead of ducking during alerts/navigation, then resumes | 遇到提示音或导航播报时暂停播放而非降音量，结束后自动续播 | — |
| `pref_focus_ignore_label` | Immersive · Ignore Short Alerts | 沉浸模式 · 忽略短提示音 | — |
| `pref_focus_ignore_sub` | Ignores brief notifications without ducking; pauses only for calls/media | 忽略消息提示音不断流不降音量，仅来电或视频等强打断时礼让暂停 | — |
| `pref_sort_custom` | Default Order | 默认顺序 | — |
| `pref_sort_title` | Title | 标题 | — |
| `pref_sort_album` | Album | 专辑 | — |
| `pref_sort_artist` | Artist | 艺人 | — |
| `pref_sort_recent_added` | Recently Added | 最近添加 | — |
| `pref_sort_recent_updated` | Recently Updated | 最近更新 | — |
| `pref_sort_playlist_name` | Playlist Name | 歌单名称 | — |
| `pref_sort_track_count` | Track Count | 曲目数量 | — |

---

## `playlist_*` — 歌单与离线下载 / Playlists & Downloads（68 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `playlist_edit_title` | Edit Playlist | 编辑歌单 | — |
| `playlist_create_title` | New Playlist | 新建歌单 | — |
| `playlist_cover_custom_desc` | Custom playlist cover | 歌单自定义封面 | — |
| `playlist_cover_desc` | Playlist cover | 歌单封面 | — |
| `playlist_cover_selected` | Selected cover | 已选封面 | — |
| `playlist_cover_pick` | Choose from Gallery | 从相册选择封面 | — |
| `playlist_cover_reset` | Reset to 2×2 Collage | 恢复自动四宫格封面 | — |
| `playlist_cover_hint` | Auto-generates a 2×2 album collage if unset | 未选择时自动拼出 2×2 专辑四宫格 | — |
| `playlist_type_local` | Local Playlist (Mixed) | 本地歌单（可混收） | — |
| `playlist_name_label` | Playlist Name * | 歌单名称 * | — |
| `playlist_name_hint` | e.g., Late Night Jazz / Lossless Audiophile | 例如：深夜爵士黑胶 / 无损试音精选 | — |
| `playlist_desc_label` | Description (Optional) | 歌单简介（选填） | — |
| `playlist_add_title` | Add to Playlist | 加入歌单 | — |
| `playlist_batch_add_title` | Add %1$d Tracks to Playlist | 批量加入歌单（%1$d 首） | `%1$d` |
| `playlist_create_and_add` | New Playlist & Add | 新建歌单并加入 | — |
| `playlist_local_section` | Local Playlists (Local + Remote) | 本地歌单（支持本地与远程混收） | — |
| `playlist_remote_section` | Server Playlists · %1$s | 服务器歌单 · %1$s | `%1$s` |
| `playlist_remote_meta` | %1$d tracks · Server Sync | %1$d 首 · 服务器同步 | `%1$d` |
| `playlist_sort_title` | Sort By | 排序方式 | — |
| `playlist_sort_asc` | Ascending | 升序 | — |
| `playlist_sort_desc` | Descending | 降序 | — |
| `playlist_action_download_offline` | Download Offline (Lossless) | 下载到本地（离线无损） | — |
| `playlist_action_delete_offline` | Delete Offline File | 删除已下载离线文件 | — |
| `playlist_action_remove_here` | Remove from Playlist | 从当前歌单移除 | — |
| `playlist_selected_count` | Selected %1$d / %2$d | 已选择 %1$d / %2$d 首 | `%1$d`, `%2$d` |
| `playlist_exit_multi` | Exit selection | 退出多选 | — |
| `playlist_remove_from` | Remove | 移出歌单 | — |
| `playlist_picker_title` | Add Tracks to \"%1$s\" | 添加歌曲到「%1$s」 | `%1$s` |
| `playlist_picker_search` | Search title, artist, album… | 搜索歌名、艺人、专辑… | — |
| `playlist_picker_empty` | No matching tracks found | 没有找到可添加的歌曲 | — |
| `playlist_picker_already_in` | Already in playlist · %1$s | 已在歌单中 · %1$s | `%1$s` |
| `playlist_picker_prompt` | Select tracks to add | 请勾选要添加的歌曲 | — |
| `playlist_picker_confirm` | Add (%1$d tracks) | 确认添加（%1$d 首） | `%1$d` |
| `playlist_dl_title` | Offline Downloads | 离线下载管理 | — |
| `playlist_dl_summary` | %1$d offline · %2$s used | 已离线 %1$d 首 · 占用 %2$s | `%1$d`, `%2$s` |
| `playlist_dl_cancel_all` | Cancel All | 全部取消 | — |
| `playlist_dl_clear_done` | Clear Done | 清理记录 | — |
| `playlist_dl_no_active` | No active download tasks | 当前没有进行中的下载任务 | — |
| `playlist_dl_queued` | Queued… | 排队等待中… | — |
| `playlist_dl_progress` | Downloading %1$d%% | 下载中 %1$d%% | `%1$d` |
| `playlist_dl_downloading` | Downloading… | 下载中… | — |
| `playlist_dl_done` | Completed · Ready for offline playback | 已完成 · 已支持离线无损播放 | — |
| `playlist_dl_failed` | Download failed | 下载失败 | — |
| `playlist_dl_clear_all_btn` | Clear All Offline Files (Free %1$s) | 清空全部离线文件（释放 %1$s） | `%1$s` |
| `playlist_dl_clear_dialog_title` | Clear All Offline Downloads | 清空全部离线下载 | — |
| `playlist_dl_clear_dialog_msg` | Delete all %1$d offline files and free %2$s? Original files on your Subsonic server will not be affected. | 确定删除全部 %1$d 首已下载离线文件吗？这会释放 %2$s 本地空间，不影响 Subsonic 服务器上的原文件。 | `%1$d`, `%2$s` |
| `playlist_dl_clear_confirm` | Clear All | 确认清空 | — |
| `playlist_detail_reorder` | Reorder | 调整顺序 | — |
| `playlist_detail_batch` | Batch manage | 批量管理 | — |
| `playlist_detail_delete` | Delete Playlist | 删除歌单 | — |
| `playlist_detail_remote_prefix` | Server Playlist · | 服务器歌单 · | — |
| `playlist_detail_local_prefix` | Local Playlist · | 本地歌单 · | — |
| `playlist_detail_add_short` | Add | 加歌 | — |
| `playlist_detail_download_all` | Download playlist | 下载歌单 | — |
| `playlist_detail_shuffle` | Shuffle playlist | 歌单随机播放 | — |
| `playlist_detail_reorder_hint` | Reordering: tap up/down arrows, then tap the top icon again when done | 排序微调中：点击右侧上下箭头调整顺序，再次点击顶栏排序图标完成 | — |
| `playlist_detail_empty_title` | This Playlist Is Empty | 歌单里还没有歌曲 | — |
| `playlist_detail_empty_desc` | Tap below to add tracks from your library | 点击下方按钮从音乐库挑选歌曲加入 | — |
| `playlist_detail_add_tracks` | Add Tracks | 添加歌曲 | — |
| `playlist_detail_sort_title` | Sort Playlist Tracks | 歌单曲目排序 | — |
| `playlist_detail_delete_remote_msg` | Delete server playlist \"%1$s\"? This syncs to your Subsonic server without deleting audio files. | 确定删除服务器歌单「%1$s」吗？此操作将同步到 Subsonic 服务器，不会删除歌曲原文件。 | `%1$s` |
| `playlist_detail_delete_local_msg` | Delete playlist \"%1$s\"? Only the playlist is removed; audio files remain intact. | 确定删除歌单「%1$s」吗？此操作仅移除歌单，不会删除任何本地或远程音频文件。 | `%1$s` |
| `playlist_detail_delete_confirm` | Delete | 确认删除 | — |
| `playlist_detail_move_up` | Move up | 上移 | — |
| `playlist_detail_move_down` | Move down | 下移 | — |
| `playlist_cellular_title` | On Cellular Network | 当前为移动网络 | — |
| `playlist_cellular_msg` | \"Download over Wi-Fi only\" is enabled. Allow downloading %1$d track(s) over cellular data this time? | 您已开启「仅 Wi-Fi 下载」，本次待下载 %1$d 首无损/音频曲目。是否允许本次使用移动流量继续下载？ | `%1$d` |
| `playlist_cellular_continue` | Download Anyway | 继续下载 | — |

---

## `subsonic_*` — Subsonic 媒体服务器 / Subsonic Server（68 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `subsonic_sec_newest` | Newest | 最新专辑 | — |
| `subsonic_sec_albums` | Albums | 全部专辑 | — |
| `subsonic_sec_artists` | Artists | 艺术家 | — |
| `subsonic_sec_playlists` | Playlists | 歌单 | — |
| `subsonic_sec_starred` | Starred | 已收藏 | — |
| `subsonic_sec_random` | Random | 随机发现 | — |
| `subsonic_sheet_title` | Subsonic Media Servers | Subsonic 媒体服务器 | — |
| `subsonic_sheet_edit` | Edit Server Account | 编辑服务器账号 | — |
| `subsonic_sheet_login` | Sign In to Subsonic | 登录 Subsonic 媒体库 | — |
| `subsonic_sheet_compat` | Compatible with Navidrome / Subsonic / Airsonic / Gonic / OpenSubsonic | 兼容 Navidrome / Subsonic / Airsonic / Gonic / OpenSubsonic | — |
| `subsonic_add_account` | Add Account | 添加账号 | — |
| `subsonic_account_list` | Accounts | 账号列表 | — |
| `subsonic_no_accounts` | No Subsonic server accounts signed in yet | 尚未登录任何 Subsonic 服务器账号 | — |
| `subsonic_active_badge` | Active | 当前使用 | — |
| `subsonic_test_conn` | Test connection | 测试连接 | — |
| `subsonic_test_short` | Test | 测试 | — |
| `subsonic_remove_desc` | Sign out & remove | 退出删除 | — |
| `subsonic_remove_short` | Remove | 移除 | — |
| `subsonic_field_url` | Server URL * | 服务器地址 * | — |
| `subsonic_field_user` | Username * | 用户名 * | — |
| `subsonic_field_pass_edit` | Password (leave blank to keep saved) | 密码（留空保持原已保存密码） | — |
| `subsonic_field_pass` | Password * | 密码 * | — |
| `subsonic_hide_pass` | Hide password | 隐藏密码 | — |
| `subsonic_show_pass` | Show password | 显示密码 | — |
| `subsonic_field_name` | Display Name (Optional) | 显示名称（选填） | — |
| `subsonic_field_name_hint` | e.g., Home Navidrome / Private NAS | 例如：家里 Navidrome / 私有云 NAS | — |
| `subsonic_keystore_note` | Passwords are encrypted locally with Android Keystore AES-256-GCM. Requests use salted MD5 tokens by default. | 密码将使用 Android Keystore AES-256-GCM 硬件密钥加密保存在本地，请求默认采用随机盐值 MD5 Token 签名。 | — |
| `subsonic_advanced_opts` | Advanced Protocol & TLS Options | 高级协议与证书选项 | — |
| `subsonic_legacy_auth_title` | Legacy Authentication (p=enc:) | 旧版认证兼容（p=enc:） | — |
| `subsonic_legacy_auth_sub` | Enable only if legacy Subsonic or LDAP rejects salted tokens | 仅当旧版 Subsonic 或 LDAP 账号拒绝随机盐 Token 时开启 | — |
| `subsonic_self_signed_title` | Trust Self-Signed HTTPS Cert | 信任自签名 HTTPS 证书 | — |
| `subsonic_self_signed_sub` | Scoped to this server only — useful for private NAS reverse proxies | 仅对该服务器生效，适用于自建 NAS 私有 HTTPS 反向代理 | — |
| `subsonic_verifying_saving` | Verifying connection & encrypting credentials… | 正在校验连接并加密保存… | — |
| `subsonic_btn_verify_update` | Verify & Update Account | 校验并更新账号 | — |
| `subsonic_btn_test_save` | Test Connection & Sign In | 测试连接并保存登录 | — |
| `subsonic_delete_title` | Sign Out & Remove Account | 退出并移除账号 | — |
| `subsonic_delete_msg` | Remove \"%1$s\" (%2$s)? Local encrypted credentials and cached metadata will be cleared; server files stay untouched. | 确定移除「%1$s」（%2$s）吗？这将清除本地已加密保存的凭据及该服务器的远程元数据与封面缓存，不影响服务器上的音乐文件。 | `%1$s`, `%2$s` |
| `subsonic_delete_confirm` | Remove | 确认移除 | — |
| `subsonic_creator_meta` | By %1$s · %2$s | 创建者：%1$s · %2$s | `%1$s`, `%2$s` |
| `subsonic_search_results_title` | %1$s · Search Results | %1$s · 搜索结果 | `%1$s` |
| `subsonic_all_folders` | All Folders | 全部目录 | — |
| `subsonic_loading_from` | Loading library from %1$s… | 正在从 %1$s 拉取曲库… | `%1$s` |
| `subsonic_empty_artists` | No artists found on server | 服务器中暂无艺术家 | — |
| `subsonic_playlists_header` | Server Playlists (%1$d) | 服务器歌单（%1$d） | `%1$d` |
| `subsonic_new_playlist` | New Server Playlist | 新建服务器歌单 | — |
| `subsonic_empty_playlists` | No server playlists yet — tap top right to create | 服务器中暂无歌单，点击右上角即可新建 | — |
| `subsonic_empty_starred` | No starred tracks or albums on server yet | 还没有在服务器收藏歌曲或专辑 | — |
| `subsonic_starred_albums` | Starred Albums (%1$d) | 收藏专辑（%1$d） | `%1$d` |
| `subsonic_starred_tracks` | Starred Tracks (%1$d) | 收藏歌曲（%1$d） | `%1$d` |
| `subsonic_starred_queue_label` | %1$s · Starred | %1$s · 已收藏 | `%1$s` |
| `subsonic_empty_random` | Could not fetch random tracks from server | 未能从服务器获取随机曲目 | — |
| `subsonic_random_header` | %1$d Random Picks | 随机挑选 %1$d 首 | `%1$d` |
| `subsonic_shuffle_refresh` | Refresh | 换一批 | — |
| `subsonic_random_queue_label` | %1$s · Random Mix | %1$s · 随机发现 | `%1$s` |
| `subsonic_empty_albums` | No albums found on server | 服务器中暂无专辑 | — |
| `subsonic_search_hint` | Search songs, albums, artists on %1$s… | 在 %1$s 中搜索歌曲、专辑、艺术家… | `%1$s` |
| `subsonic_close_search` | Close search | 关闭搜索 | — |
| `subsonic_search_prompt` | Type keywords to search remote library live | 输入关键词实时检索服务器曲库 | — |
| `subsonic_search_empty` | No results matching \"%1$s\" | 未找到与「%1$s」匹配的结果 | `%1$s` |
| `subsonic_search_artists` | Artists (%1$d) | 艺术家（%1$d） | `%1$d` |
| `subsonic_search_albums` | Albums (%1$d) | 专辑（%1$d） | `%1$d` |
| `subsonic_search_songs` | Songs (%1$d) | 歌曲（%1$d） | `%1$d` |
| `subsonic_batch_select` | Batch select | 批量选择 | — |
| `subsonic_download_album` | Download album | 下载专辑 | — |
| `subsonic_connect_failed_title` | Unable to Reach Media Server | 无法连接媒体服务器 | — |
| `subsonic_retry_connect` | Retry | 重试连接 | — |
| `subsonic_check_account` | Accounts | 检查账号 | — |
| `subsonic_music_folder_fallback` | Music Folder %1$s | 音乐目录 %1$s | `%1$s` |

---

## `search_*` — 全屏全局搜索 / Global Search（33 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `search_tab_all` | All | 全部 | — |
| `search_tab_songs` | Songs | 歌曲 | — |
| `search_tab_albums` | Albums | 专辑 | — |
| `search_tab_artists` | Artists | 艺人 | — |
| `search_tab_playlists` | Playlists | 歌单 | — |
| `search_tab_remote` | Remote | 远程 | — |
| `search_tab_lyrics` | Lyrics | 歌词 | — |
| `search_back_to_search` | Back to search | 返回搜索 | — |
| `search_hint_with_remote` | Search local/remote songs, albums, playlists, lyrics… | 搜本地/远程歌曲、专辑、歌单、歌词（简繁互通） | — |
| `search_hint_local` | Search songs, artists, albums, playlists, lyrics… | 搜歌曲、艺人、专辑、歌单、歌词（简繁互通） | — |
| `search_recent_title` | Recent Searches | 最近搜索 | — |
| `search_delete_history` | Remove history | 删除历史 | — |
| `search_quick_discover` | Quick Discovery | 快捷发现 | — |
| `search_quick_flac_title` | FLAC Lossless | FLAC 无损 | — |
| `search_quick_flac_sub` | Find lossless tracks fast | 快速检索无损曲目 | — |
| `search_quick_fav_title` | Favorites | 已收藏 | — |
| `search_quick_fav_sub` | %1$d favorites | %1$d 首喜爱 | `%1$d` |
| `search_quick_hires_title` | Hi-Res Masters | Hi-Res 高解析母带 | — |
| `search_quick_hires_sub` | %1$d tracks (>48kHz / 24-bit) | %1$d 首 (>48kHz / 24-bit) | `%1$d` |
| `search_quick_hires_queue` | Hi-Res Masters | Hi-Res 母带 | — |
| `search_empty_title` | No music matching \"%1$s\" | 未找到与「%1$s」相关的音乐 | `%1$s` |
| `search_empty_sub` | Searches titles, artists, albums, playlists, and cached .lrc lyrics | 支持简繁体互通搜索歌名、艺人、专辑、歌单与本地歌词 | — |
| `search_sec_local_songs` | Local Songs (%1$d) | 本地歌曲（%1$d） | `%1$d` |
| `search_queue_label` | Search: %1$s | 搜索：%1$s | `%1$s` |
| `search_sec_albums` | Albums (%1$d) | 专辑（%1$d） | `%1$d` |
| `search_sec_artists` | Artists (%1$d) | 艺术家（%1$d） | `%1$d` |
| `search_sec_playlists` | Playlists (%1$d) | 自建歌单（%1$d） | `%1$d` |
| `search_remote_loading` | Searching remote server \"%1$s\"… | 正在检索远程服务器「%1$s」… | `%1$s` |
| `search_sec_remote` | Subsonic · %1$s (%2$d) | Subsonic 远程 · %1$s（%2$d） | `%1$s`, `%2$d` |
| `search_remote_queue_label` | Remote Search: %1$s | 远程搜索：%1$s | `%1$s` |
| `search_remote_album_sub` | Remote Album · %1$s | 远程专辑 · %1$s | `%1$s` |
| `search_sec_lyrics` | Lyrics Matches (%1$d) | 歌词命中（%1$d） | `%1$d` |
| `search_lyrics_queue_label` | Lyrics Search | 歌词搜索 | — |

---

## `wifi_*` — WiFi 局域网传歌 / WiFi Transfer（33 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `wifi_filter_all` | All | 全部 | — |
| `wifi_filter_ok` | Success | 成功 | — |
| `wifi_filter_dup` | Skipped | 重复跳过 | — |
| `wifi_filter_fail` | Failed | 失败 | — |
| `wifi_title` | WiFi LAN Transfer | WiFi 局域网传歌 | — |
| `wifi_queue_label` | WiFi Transfer | WiFi 传歌 | — |
| `wifi_running_sub` | Active · Uploaded %1$d · Dedup Skipped %2$d | 服务运行中 · 本次成功 %1$d · 防重跳过 %2$d | `%1$d`, `%2$d` |
| `wifi_idle_sub` | Drag & drop lossless audio from phone or PC browser over WiFi | 开启后可在手机或 PC 浏览器直接拖拽上传无损音频 | — |
| `wifi_qr_hint` | Scan QR on mobile or open this URL on PC on the same WiFi | 同一 WiFi 下手机扫码或电脑浏览器打开以下地址 | — |
| `wifi_copied_url` | Copied URL: %1$s | 已复制链接：%1$s | `%1$s` |
| `wifi_copy_url` | Copy URL | 复制链接 | — |
| `wifi_refresh_ip` | Refresh IP | 刷新 IP | — |
| `wifi_pin_label` | Temporary PIN: | 临时访问 PIN： | — |
| `wifi_pin_regen` | Regenerate | 换一个 | — |
| `wifi_storage_info` | Folder: %1$s · Free %2$s | 接收目录：%1$s · 剩余 %2$s | `%1$s`, `%2$s` |
| `wifi_dedup_title` | Auto-Skip Duplicate Uploads | 防重复上传自动跳过 | — |
| `wifi_dedup_sub` | Checks SHA-256 fingerprint & library before transfer to save bandwidth | 上传前先比对内容指纹与本地曲库，重复文件零流量跳过 | — |
| `wifi_pin_toggle_title` | Require 4-Digit Temporary PIN | 启用 4 位临时 PIN 访问码 | — |
| `wifi_pin_toggle_sub` | Prevent unauthorized uploads on shared public WiFi networks | 在公共 WiFi 环境下防止他人误打开上传页面 | — |
| `wifi_history_header` | Upload History (%1$d) | 上传历史（%1$d） | `%1$d` |
| `wifi_history_clear` | Clear | 清空历史 | — |
| `wifi_history_empty` | No upload history yet | 暂无上传历史记录 | — |
| `wifi_del_dialog_title` | Delete Upload Record | 删除上传记录 | — |
| `wifi_del_dialog_msg_with_file` | Delete only the history entry for \"%1$s\", or also delete the local audio file? | 请选择仅删除「%1$s」的历史记录，或同时删除本地音频文件。 | `%1$s` |
| `wifi_del_dialog_msg_only` | Delete the upload history record for \"%1$s\"? | 确定删除「%1$s」的上传记录吗？ | `%1$s` |
| `wifi_del_with_file` | Delete File Too | 删记录并删文件 | — |
| `wifi_del_record_only` | Record Only | 仅删记录 | — |
| `wifi_clear_dialog_title` | Clear Upload History | 清空上传历史 | — |
| `wifi_clear_dialog_msg` | Clear all history entries? Uploaded lossless audio files on your phone will be kept. | 仅清空全部历史记录（保留手机上已传好的无损音频文件），确认继续吗？ | — |
| `wifi_dup_of` | Duplicate of \"%1$s\" | 与「%1$s」内容重复 | `%1$s` |
| `wifi_client_lan` | LAN Client | 局域网客户端 | — |
| `wifi_client_android` | Android Phone | Android 手机 | — |
| `wifi_client_phone` | Phone | 手机 | — |

---

## `update_*` — 应用内更新与日志 / In-App Update & Changelog（30 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `update_title_forced_ready` | Critical Update Verified | 重要更新校验通过 | — |
| `update_title_forced` | Critical Update Required | 重要版本升级（必须更新） | — |
| `update_title_uptodate` | Up to Date | 已是最新版本 | — |
| `update_title_error` | Update Check Failed | 更新检查异常 | — |
| `update_title_ready` | Package Verified | 安装包校验通过 | — |
| `update_title_normal` | App Update | 应用更新 | — |
| `update_forced_banner` | This version is no longer supported or contains critical fixes. Please upgrade to continue using Sonify. | 当前版本已停止兼容或包含关键安全修复，请完成升级后继续使用 Sonify。 | — |
| `update_checking_msg` | Contacting update server for latest release… | 正在连接更新服务器查询最新版本… | — |
| `update_current_notes_label` | Current Version Highlights | 当前版本更新内容 | — |
| `update_verifying_msg` | Verifying APK SHA-256 integrity… | 正在校验安装包 SHA-256 完整性… | — |
| `update_perm_needed` | Enable \"Install unknown apps\" for Sonify in system settings, then return to install. | 请先在系统设置中开启「允许 Sonify 安装未知应用」，返回后点击立即安装 | — |
| `update_sha_passed` | SHA-256 verified. Tap below to launch system installer. | SHA-256 校验通过，点击下方立即调起系统安装器 | — |
| `update_btn_download` | Download Update | 立即下载更新 | — |
| `update_btn_grant_perm` | Grant Install Permission | 去开启安装权限 | — |
| `update_btn_install` | Install Now | 立即安装 | — |
| `update_btn_exit` | Exit App | 退出应用 | — |
| `update_btn_skip` | Skip | 跳过此版 | — |
| `update_btn_later` | Later | 稍后 | — |
| `update_btn_cancel_dl` | Cancel Download | 取消下载 | — |
| `update_changelog_title` | Release Changelog | 版本更新日志 | — |
| `update_badge_current` | Current | 当前 | — |
| `update_license_notice` | License: Sonify Non-Commercial v1.0 (Personal non-commercial use only; commercial use prohibited). | 开源协议：Sonify Non-Commercial v1.0（仅限个人非商用，禁止任何未经授权的商业用途） | — |
| `update_btn_github_releases` | GitHub Releases | GitHub 发布页 | — |
| `update_notes_fallback` | Includes stability improvements and bug fixes. | 包含体验优化与稳定性修复。 | — |
| `update_notes_header` | What's New | 本次更新内容 | — |
| `update_published_at` | Released %1$s | 发布于 %1$s | `%1$s` |
| `update_chip_changelog` | Changelog | 更新日志 | — |
| `update_chip_releases` | Releases | 发布页 | — |
| `update_chip_github` | GitHub | GitHub | — |
| `update_unknown_size` | Unknown size | 未知大小 | — |

---

## `crash_*` — 崩溃拦截与恢复 / Crash Recovery（22 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `crash_loop_title` | Repeated Crashes Detected | 检测到连续异常，已暂停自动重启 | — |
| `crash_normal_title` | Sonify Recovered Safely | Sonify 遇到意外并已保护现场 | — |
| `crash_loop_desc` | %1$d crashes occurred within 30s. Auto-restart paused to prevent crash loops. Upload logs or clear cache before restarting. | 30 秒内连续触发 %1$d 次异常，为防止无限闪退已进入安全恢复页。建议上报日志或清理临时缓存后重启。 | `%1$d` |
| `crash_normal_desc` | Audio output was safely released and a sanitized crash report was saved. Restarting automatically… | 已安全释放音频输出并保存脱敏崩溃日志，即将自动为你重新拉起播放器。 | — |
| `crash_countdown_paused` | Auto-restart paused | 已暂停自动拉起 | — |
| `crash_countdown_running` | Restarting in %1$ds… | %1$d 秒后自动重新拉起… | `%1$d` |
| `crash_btn_pause` | Pause | 暂停查看 | — |
| `crash_btn_restart` | Restart Sonify Now | 立即重启 Sonify | — |
| `crash_btn_clear_restart` | Clear Cache & Restart | 清理临时缓存并重启 | — |
| `crash_btn_uploading` | Uploading… | 上报中… | — |
| `crash_btn_upload` | Upload Report | 上报脱敏日志 | — |
| `crash_btn_copied` | Copied | 已复制堆栈 | — |
| `crash_btn_copy` | Copy Trace | 复制崩溃堆栈 | — |
| `crash_badge_uploaded` | Uploaded | 已上报 | — |
| `crash_logs_title` | Local Crash Reports (%1$d) | 本地崩溃诊断记录（%1$d） | `%1$d` |
| `crash_logs_sub` | Paths & credentials are stripped automatically. Copy or upload below. | 已自动脱敏路径与凭据，可复制或上报分析 | — |
| `crash_logs_empty` | No crash reports — running smoothly. | 暂无崩溃记录，当前运行非常稳定。 | — |
| `crash_btn_reupload` | Re-upload | 重报 | — |
| `crash_btn_upload_short` | Upload | 上报 | — |
| `crash_upload_ok` | Sanitized crash log uploaded | 脱敏日志已上报 | — |
| `crash_upload_http_err` | Upload failed (HTTP %1$d) | 上报失败（HTTP %1$d） | `%1$d` |
| `crash_upload_net_err` | Unable to reach server: %1$s | 暂未连上服务器：%1$s | `%1$s` |

---

## `lock_*` — 黑胶锁屏 / Vinyl Lockscreen（5 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `lock_date_pattern` | EEE, MMM d | M月d日 · EEEE | — |
| `lock_waiting` | Ready to Play | 等待播放 | — |
| `lock_system_vol` | System | 系统 | — |
| `lock_unlock` | Unlock & open Sonify | 解锁进入 Sonify | — |
| `lock_swipe_up` | Swipe up to exit lockscreen | 向上滑动退出锁屏 | — |

---

## `notif_*` — 通知栏 / Notifications（8 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `notif_channel_name` | Sonify Audio Playback | Sonify 音频播放 | — |
| `notif_lockscreen_channel` | Sonify Vinyl Lockscreen | Sonify 黑胶锁屏 | — |
| `notif_lockscreen_playing` | Vinyl lockscreen active | 黑胶锁屏播放中 | — |
| `notif_library_empty` | Library is empty | 媒体库为空 | — |
| `notif_wifi_running` | Sonify WiFi Transfer Active | Sonify WiFi 传歌运行中 | — |
| `notif_wifi_visit` | Open %1$s in browser | 浏览器访问 %1$s | `%1$s` |
| `notif_wifi_waiting` | Waiting for lossless uploads on LAN | 等待局域网设备上传无损音乐 | — |
| `notif_wifi_stop` | Stop Server | 停止服务 | — |

---

## `toast_*` — 轻量提示 / Toasts（32 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `toast_not_indexed_yet` | File not indexed yet, please try again shortly | 文件尚未索引，请稍后再试 | — |
| `toast_deleted_record_and_file` | Deleted record & local file | 已删除记录与本地文件 | — |
| `toast_deleted_record` | Deleted upload record | 已删除上传记录 | — |
| `toast_cleared_history` | Cleared upload history | 已清空上传历史 | — |
| `toast_playlist_created_with_tracks` | Created \"%1$s\" and added %2$d tracks | 已创建歌单「%1$s」并加入 %2$d 首歌曲 | `%1$s`, `%2$d` |
| `toast_playlist_created` | Created playlist \"%1$s\" | 已创建歌单「%1$s」 | `%1$s` |
| `toast_playlist_updated` | Playlist updated | 歌单信息已更新 | — |
| `toast_playlist_deleted` | Deleted playlist \"%1$s\" | 已删除歌单「%1$s」 | `%1$s` |
| `toast_added_with_dups` | Added %1$d to \"%2$s\" (%3$d duplicates skipped) | 已加入「%2$s」%1$d 首（跳过 %3$d 首重复） | `%1$d`, `%2$s`, `%3$d` |
| `toast_added_tracks` | Added %1$d tracks to \"%2$s\" | 已加入「%2$s」%1$d 首歌曲 | `%1$d`, `%2$s` |
| `toast_already_in_playlist` | Selected tracks are already in \"%1$s\" | 所选歌曲已在「%1$s」中 | `%1$s` |
| `toast_removed_tracks` | Removed %1$d tracks from playlist | 已从歌单移除 %1$d 首歌曲 | `%1$d` |
| `toast_remote_pl_created` | Created server playlist \"%1$s\" | 已在服务器创建歌单「%1$s」 | `%1$s` |
| `toast_remote_pl_updated` | Synced server playlist | 已同步更新服务器歌单 | — |
| `toast_remote_pl_added` | Added %1$d tracks to server playlist \"%2$s\" | 已添加 %1$d 首歌曲到服务器歌单「%2$s」 | `%1$d`, `%2$s` |
| `toast_remote_pl_same_server` | Only tracks from the same Subsonic server can be added | 仅支持添加来自同一 Subsonic 服务器的歌曲 | — |
| `toast_remote_pl_removed` | Removed %1$d tracks from server playlist | 已从服务器歌单移除 %1$d 首歌曲 | `%1$d` |
| `toast_remote_pl_deleted` | Deleted server playlist \"%1$s\" | 已删除服务器歌单「%1$s」 | `%1$s` |
| `toast_dl_queued_with_skip` | Queued %1$d tracks (%2$d already offline skipped) | 已加入下载队列 %1$d 首（%2$d 首已下载跳过） | `%1$d`, `%2$d` |
| `toast_dl_started` | Started downloading %1$d tracks | 已开始下载 %1$d 首歌曲 | `%1$d` |
| `toast_dl_all_offline` | All selected remote tracks are already offline | 所选远程歌曲均已离线下载 | — |
| `toast_dl_all_local` | Selected tracks are already local | 所选均为本地歌曲，无需下载 | — |
| `toast_dl_deleted_file` | Deleted offline file \"%1$s\" | 已删除离线文件「%1$s」 | `%1$s` |
| `toast_dl_cleared_all` | Cleared all offline downloads | 已清空全部离线下载文件 | — |
| `toast_lyrics_applied` | Applied online lyrics (%1$s) | 已应用在线歌词（%1$s） | `%1$s` |
| `toast_lyrics_imported` | Imported local lyrics (%1$d lines) | 已导入本地歌词（%1$d 句） | `%1$d` |
| `toast_lyrics_restored` | Restored automatic lyrics matching | 已恢复自动匹配歌词 | — |
| `toast_crash_cleared` | Cleared local crash reports | 已清空本地崩溃记录 | — |
| `toast_crash_copied` | Copied sanitized stack trace | 已复制脱敏崩溃堆栈 | — |
| `toast_subsonic_saved` | Connected & saved · %1$s | 已连接并保存 · %1$s | `%1$s` |
| `toast_subsonic_ok` | Connection OK · %1$s | 连接正常 · %1$s | `%1$s` |
| `toast_subsonic_removed` | Signed out and removed account | 已退出并删除该服务器账号 | — |

---

## `err_*` — 错误提示 / Error Messages（72 条）

| Key | English (`values/`) | 简体中文 (`values-zh/`) | 占位符 |
|---|---|---|---|
| `err_network_generic` | Network connection error | 网络连接异常 | — |
| `err_playlist_name_empty` | Playlist name cannot be empty | 歌单名称不能为空 | — |
| `err_playlist_create_fail` | Failed to create playlist | 创建歌单失败 | — |
| `err_playlist_update_fail` | Failed to update playlist | 更新歌单失败 | — |
| `err_playlist_delete_fail` | Failed to delete playlist | 删除歌单失败 | — |
| `err_playlist_add_fail` | Failed to add to playlist | 加入歌单失败 | — |
| `err_playlist_remove_fail` | Failed to remove tracks | 移除歌曲失败 | — |
| `err_remote_pl_create_fail` | Failed to create server playlist | 创建服务器歌单失败 | — |
| `err_remote_pl_update_fail` | Failed to update server playlist | 更新服务器歌单失败 | — |
| `err_remote_pl_add_fail` | Failed to add to server playlist | 添加到服务器歌单失败 | — |
| `err_remote_pl_remove_fail` | Failed to remove from server playlist | 移除服务器歌单曲目失败 | — |
| `err_remote_pl_delete_fail` | Failed to delete server playlist | 删除服务器歌单失败 | — |
| `err_lyrics_search_fail` | Online lyrics search failed — check network | 在线搜词失败，请检查网络连接 | — |
| `err_lyrics_parse_fail` | Unable to parse selected lyrics file | 无法解析所选歌词文件 | — |
| `err_lyrics_http` | Lyrics service returned HTTP %1$d | 在线搜词服务响应异常（HTTP %1$d） | `%1$d` |
| `err_subsonic_no_creds` | Server credentials not found | 未找到服务器凭据 | — |
| `err_subsonic_creds_invalid` | Server credentials unavailable, please sign in again | 服务器账号凭据不可用，请重新登录 | — |
| `err_subsonic_load_lib` | Failed to load remote library | 加载远程曲库失败 | — |
| `err_subsonic_load_album` | Failed to load album tracks | 加载专辑曲目失败 | — |
| `err_subsonic_load_artist` | Failed to load artist albums | 加载艺术家专辑失败 | — |
| `err_subsonic_load_playlist` | Failed to load playlist tracks | 加载歌单曲目失败 | — |
| `err_subsonic_search` | Remote search failed | 远程搜索失败 | — |
| `err_subsonic_connect` | Failed to connect to server | 连接服务器失败 | — |
| `err_subsonic_test` | Connection test failed | 测试连接失败 | — |
| `err_subsonic_user_empty` | Username cannot be empty | 用户名不能为空 | — |
| `err_subsonic_pass_empty` | Password cannot be empty | 密码不能为空 | — |
| `err_subsonic_url_empty` | Server URL cannot be empty | 服务器地址不能为空 | — |
| `err_subsonic_url_invalid` | Invalid server URL: %1$s | 服务器地址格式不正确：%1$s | `%1$s` |
| `err_subsonic_tls` | HTTPS certificate validation failed (enable \"Trust Self-Signed Cert\" in Advanced Options if self-hosted) | HTTPS 证书校验失败（若为私有自签名证书，请在高级选项开启「信任自签名证书」） | — |
| `err_subsonic_dns` | Cannot resolve server hostname — check network or URL | 无法解析服务器域名，请检查网络或地址拼写 | — |
| `err_subsonic_conn_refused` | Cannot connect to server port — verify server is running | 无法连接到服务器端口，请确认服务器已启动且网络互通 | — |
| `err_subsonic_timeout` | Server connection timed out — please retry | 连接服务器超时，请稍后重试 | — |
| `err_subsonic_request_fail` | Network request failed | 网络请求失败 | — |
| `err_subsonic_non_json` | Server returned non-JSON response — check URL | 服务器返回了非 JSON 响应，请检查服务器地址是否正确 | — |
| `err_subsonic_bad_format` | Missing subsonic-response field — not a Subsonic-compatible server | 响应中缺少 subsonic-response 字段，可能不是 Subsonic 兼容服务器 | — |
| `err_subsonic_api_10` | Missing required parameter (%1$d) | 缺少必要请求参数（%1$d） | `%1$d` |
| `err_subsonic_api_20` | Client protocol too old — please update Sonify (%1$d) | 客户端协议版本过低，请更新 Sonify（%1$d） | `%1$d` |
| `err_subsonic_api_30` | Server protocol too old for this request (%1$d) | 服务器协议版本过旧，不支持当前请求（%1$d） | `%1$d` |
| `err_subsonic_api_40` | Wrong username or password (40) | 用户名或密码错误（40） | — |
| `err_subsonic_api_41` | Token auth unsupported — enable Legacy Auth in Advanced Options (41) | 该服务器不支持 Token 认证，请在高级选项开启「旧版认证」（41） | — |
| `err_subsonic_api_50` | Account lacks permission for this action (50) | 当前账号无访问权限（50） | — |
| `err_subsonic_api_60` | Server trial period has ended (60) | 服务器试用期已结束（60） | — |
| `err_subsonic_api_70` | Requested track or resource not found (70) | 请求的曲目或资源不存在（70） | — |
| `err_subsonic_api_other` | Server returned error (code=%1$d) | 服务器返回错误（code=%1$d） | `%1$d` |
| `err_dl_invalid_uri` | Invalid remote track URI | 无效的远程曲目路径 | — |
| `err_dl_http` | HTTP %1$d: Download failed | HTTP %1$d: 下载失败 | `%1$d` |
| `err_dl_forbidden` | Server denied download (download permission may be disabled) | 服务器拒绝下载该曲目（可能未授予下载权限） | — |
| `err_dl_empty_body` | Empty response body | 空响应体 | — |
| `err_dl_empty_file` | Downloaded file is empty | 下载文件为空 | — |
| `err_update_http` | Update server returned HTTP %1$d | 更新服务返回 HTTP %1$d | `%1$d` |
| `err_update_manifest` | Invalid release manifest from server | 服务端返回的版本清单格式无效 | — |
| `err_update_check_fail` | Update check failed: %1$s | 检查更新失败：%1$s | `%1$s` |
| `err_update_dl_http` | Download failed (HTTP %1$d) | 下载失败（HTTP %1$d） | `%1$d` |
| `err_update_dl_empty` | Empty APK response body | 安装包响应体为空 | — |
| `err_update_sha256` | APK SHA-256 integrity check failed; file deleted for safety. Please retry. | 安装包 SHA-256 完整性校验未通过，已自动删除以策安全，请重试。 | — |
| `err_update_dl_interrupted` | Download interrupted: %1$s | 更新下载中断：%1$s | `%1$s` |
| `err_wifi_no_lan` | No LAN WiFi address detected — verify WiFi is connected | 未检测到局域网 WiFi 地址，请确认手机已连接 WiFi | — |
| `err_wifi_start_fail` | Failed to start WiFi server | 启动服务失败 | — |
| `err_wifi_bad_pin` | Incorrect PIN — check the 4-digit code on your phone | PIN 码错误，请核对手机屏幕上的 4 位访问码 | — |
| `err_wifi_need_pin` | Enter the 4-digit PIN shown on your phone | 请输入手机端显示的 4 位访问码 | — |
| `err_wifi_unsupported_type` | Unsupported file format (audio, .lrc & covers only) | 不支持的文件格式（仅支持常见无损/音频、.lrc 与封面图片） | — |
| `err_wifi_dup_uploaded` | Identical to an already uploaded file | 内容与已上传文件完全一致 | — |
| `err_wifi_dup_library` | Track already exists in local library | 本地音乐库已存在相同曲目 | — |
| `err_wifi_dup_dir` | Same filename & size already exists in target folder | 目标目录已存在同名同大小文件 | — |
| `err_wifi_unsupported_name` | Unsupported file type: %1$s | 不支持的文件类型：%1$s | `%1$s` |
| `err_wifi_empty_file` | Received empty file | 收到空文件 | — |
| `err_wifi_dup_skipped` | Duplicate skipped (matches \"%1$s\") | 重复文件已自动跳过（与「%1$s」一致） | `%1$s` |
| `err_wifi_upload_ok` | Uploaded & indexed | 上传并入库成功 | — |
| `err_wifi_write_fail` | Failed to write file | 写入文件失败 | — |
| `err_wifi_client_abort` | Client disconnected before upload completed | 客户端连接提前断开 | — |
| `err_wifi_bad_history_id` | Invalid history record ID | 无效的历史记录 ID | — |
| `err_wifi_server_err` | Internal server error | 服务器内部错误 | — |
