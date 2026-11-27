package com.sonify.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri

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
 * 内置版本更新日志条目，保证离线或已是最新版时也可在应用内查看完整变更历史。
 */
data class ChangelogEntry(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val summary: String,
    val highlights: List<String>,
)

object ReleaseNotesCatalog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionName = "0.2.0",
            versionCode = 2,
            releaseDate = "2026-11-27",
            summary = "全局跨源搜索、独立进程崩溃恢复、强制更新与日活面板",
            highlights = listOf(
                "全屏跨源全局搜索：覆盖本地歌曲/专辑/艺人/歌单、Subsonic 远程曲库与 .lrc 歌词搜歌，支持简繁互通归一化与音质规格检索",
                "独立 :crash 进程崩溃拦截与 3 秒自动重启，自动安全释放 USB DAC 流并脱敏路径与账号信息",
                "应用内安全更新与全生命周期强制更新保护：支持更新内容/历史日志、GitHub 发布页跳转与安装前强制 SHA-256 校验",
                "隐私优先匿名日活统计（仅随机 UUID，设置页可一键关闭）与云端 /admin 运营数据面板",
                "完善开源许可协议：明确个人非商用开源、禁止未经授权的商业用途",
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

    fun formatCurrentNotes(serverRelease: ReleaseInfo?, currentVersionName: String): String {
        val remote = serverRelease?.releaseNotes?.trim().orEmpty()
        if (remote.isNotEmpty()) return remote
        val matched = entries.firstOrNull { it.versionName == currentVersionName } ?: entries.first()
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
