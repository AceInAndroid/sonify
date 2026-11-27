package com.sonify.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Sonify 官方仓库与版本日志链接常量。
 */
object ProjectLinks {
    const val GITHUB_REPO_URL = "https://github.com/AceInAndroid/sonify"
    const val GITHUB_RELEASES_URL = "https://github.com/AceInAndroid/sonify/releases"
    const val GITHUB_CHANGELOG_URL = "https://github.com/AceInAndroid/sonify/blob/main/CHANGELOG.md"
    const val GITHUB_LICENSE_URL = "https://github.com/AceInAndroid/sonify/blob/main/LICENSE"

    fun openExternalUrl(context: Context, rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

/**
 * 内置版本更新日志条目，保证离线或已是最新版时也可在应用内查看中英双语变更历史。
 */
data class ChangelogEntry(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val summary: String,
    val highlights: List<String>,
)

object ReleaseNotesCatalog {
    val entriesZh: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionName = "1.0.0",
            versionCode = 100,
            releaseDate = "2026-11-27",
            summary = "首个正式发行版 · 多架构正式包、中英双语与全功能闭环",
            highlights = listOf(
                "正式发布 arm64-v8a、armeabi-v7a 与全架构通用（universal）签名安装包",
                "中英双语 1 帧零中断热切换（跟随系统 / 简体中文 / English），覆盖全部页面、弹窗、锁屏、:crash 恢复页与 WiFi 传歌 Web",
                "完整集成 Bit-Perfect USB 独占引擎、四重护耳保护、流光同步歌词、Subsonic 无损直通、WiFi 传歌、全屏全局搜索、崩溃自愈与应用内 SHA-256 安全更新",
                "采用 Sonify 非商用开源许可协议 v1.0（禁止商用），并配套多语言标准规范与字段字典",
            ),
        ),
        ChangelogEntry(
            versionName = "0.2.0",
            versionCode = 2,
            releaseDate = "2026-11-27",
            summary = "全局跨源搜索、独立进程崩溃恢复、强制更新、日活面板与双语支持",
            highlights = listOf(
                "全屏跨源全局搜索：覆盖本地歌曲/专辑/艺人/歌单、Subsonic 远程曲库与 .lrc 歌词搜歌，支持简繁互通归一化与音质规格检索",
                "独立 :crash 进程崩溃拦截与 3 秒自动重启，自动安全释放 USB DAC 流并脱敏路径与账号信息",
                "应用内安全更新与全生命周期强制更新保护：支持更新内容/历史日志、GitHub 发布页跳转与安装前强制 SHA-256 校验",
                "隐私优先匿名日活统计（仅随机 UUID，设置页可一键关闭）与云端 /admin 运营数据面板",
                "新增应用内中英双语即时无缝切换，并完善非商用开源许可协议（禁止商用）",
            ),
        ),
        ChangelogEntry(
            versionName = "0.1.0",
            versionCode = 1,
            releaseDate = "2026-11-26",
            summary = "首个公开版本 · Bit-Perfect USB 独占与黑胶沉浸体验",
            highlights = listOf(
                "自研 C++17 + libusb 1.0.27 用户态 UAC1/UAC2 独占驱动，支持异步 Feedback 闭环与 24-bit/32-bit 无损通路",
                "UAC 硬件音量优先调节、全场景物理音量键接管与四重防炸耳护耳上限保护",
                "Spotify Dark 旋转黑胶播放页、纯享黑胶态、沉浸音乐模式、5 键通知栏与黑胶锁屏",
                "支持 Subsonic/OpenSubsonic 多服务器无损直通、本地/远程歌单与离线下载、双端 WiFi 传歌与五级流光同步歌词",
            ),
        ),
    )

    val entriesEn: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionName = "1.0.0",
            versionCode = 100,
            releaseDate = "2026-11-27",
            summary = "Official 1.0 Release · Multi-ABI APKs, Bilingual UI & Full HiFi Suite",
            highlights = listOf(
                "Official signed release APKs for arm64-v8a (ARMv8), armeabi-v7a (ARMv7), and universal architectures",
                "Zero-interruption English / Simplified Chinese 1-frame switching across all screens, dialogs, lockscreen, :crash recovery, and WiFi transfer Web UI",
                "Complete HiFi suite: Bit-Perfect UAC1/UAC2 USB driver, 4-layer ear guard, aurora synced lyrics, Subsonic lossless streaming & downloads, global search, crash auto-restart & SHA-256 updates",
                "Released under Sonify Non-Commercial License v1.0 with standardized i18n contributor docs & key catalog",
            ),
        ),
        ChangelogEntry(
            versionName = "0.2.0",
            versionCode = 2,
            releaseDate = "2026-11-27",
            summary = "Global Search, Isolated Crash Recovery, Force Update, Analytics & Bilingual UI",
            highlights = listOf(
                "Full-screen cross-source global search across local library, playlists, Subsonic remote servers, and cached .lrc lyrics with spec filtering",
                "Isolated :crash recovery process with safe USB DAC teardown, 3-second auto-restart, loop breaker, and sanitized crash reporting",
                "In-app updates with mandatory SHA-256 verification, full-lifecycle Force Update lock, in-app changelog, and GitHub links",
                "Privacy-first anonymous DAU telemetry (random UUID only, opt-out toggle in Settings) and authenticated /admin ops dashboard",
                "Added instant zero-interruption English / Simplified Chinese switching and Sonify Non-Commercial License v1.0",
            ),
        ),
        ChangelogEntry(
            versionName = "0.1.0",
            versionCode = 1,
            releaseDate = "2026-11-26",
            summary = "Initial Public Release · Bit-Perfect USB Exclusive & Vinyl Immersion",
            highlights = listOf(
                "Custom C++17 + libusb 1.0.27 userspace UAC1/UAC2 driver with async feedback pacing and 24-bit/32-bit lossless pipeline",
                "DAC hardware volume control, full physical volume key hijacking, and 4-layer ear protection ceilings",
                "Spotify-dark spinning vinyl Now Playing, Zen Vinyl mode, immersive system bars, 5-button notification, and Vinyl Lockscreen",
                "Subsonic/OpenSubsonic lossless streaming & offline downloads, local/remote playlists, dual-mode WiFi transfer, and synced aurora lyrics",
            ),
        ),
    )

    /** 默认列表（保留兼容旧调用与单测） */
    val entries: List<ChangelogEntry> get() = entriesZh

    fun entriesFor(locale: Locale = Locale.getDefault()): List<ChangelogEntry> =
        if (locale.language.equals("zh", ignoreCase = true)) entriesZh else entriesEn

    fun formatCurrentNotes(
        serverRelease: ReleaseInfo?,
        currentVersionName: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val isZh = locale.language.equals("zh", ignoreCase = true)
        val remote = serverRelease?.releaseNotes?.trim().orEmpty()
        // If UI is English and remote notes contain Chinese, prefer built-in English notes for current version
        val useRemote = remote.isNotEmpty() && (isZh || remote.none { it in '\u4e00'..'\u9fff' })
        if (useRemote) return remote
        val list = entriesFor(locale)
        val matched = list.firstOrNull { it.versionName == currentVersionName } ?: list.first()
        return matched.highlights.joinToString("\n") { "- $it" }
    }

    fun formatPublishedDate(rawIso: String): String {
        val trimmed = rawIso.trim()
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            return trimmed.substring(0, 10)
        }
        return ""
    }
}
