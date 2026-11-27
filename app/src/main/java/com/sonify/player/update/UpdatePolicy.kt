package com.sonify.player.update

import java.util.Locale

/**
 * 纯 Kotlin 版本升级策略、强制更新穿透与 SHA-256 完整性判定工具（100% JVM 可单测）。
 */
object UpdatePolicy {

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    const val FORCE_CHECK_INTERVAL_MS = 30L * 60L * 1000L

    data class Evaluation(
        val hasUpdate: Boolean,
        val shouldPrompt: Boolean,
        val isForced: Boolean,
    )

    /** 常规自动更新提示检查（受用户开关与 12 小时节流控制） */
    fun shouldRunAutoCheck(
        autoCheckEnabled: Boolean,
        lastCheckMs: Long,
        nowMs: Long,
        intervalMs: Long = AUTO_CHECK_INTERVAL_MS,
    ): Boolean {
        if (!autoCheckEnabled) return false
        if (lastCheckMs <= 0L) return true
        return (nowMs - lastCheckMs) >= intervalMs
    }

    /**
     * 轻量静默清单拉取判定：即使处于 12 小时冷却期或用户关闭普通自动提示，
     * 启动/后台回前台超过 30 分钟仍可静默拉取一次清单以保障紧急「强制更新」及时触达。
     */
    fun shouldFetchManifestSilently(
        lastCheckMs: Long,
        nowMs: Long,
        isColdStart: Boolean,
        forceIntervalMs: Long = FORCE_CHECK_INTERVAL_MS,
    ): Boolean {
        if (isColdStart) return true
        if (lastCheckMs <= 0L) return true
        return (nowMs - lastCheckMs) >= forceIntervalMs
    }

    fun evaluate(
        currentVersionCode: Int,
        release: ReleaseInfo,
        ignoredVersionCode: Int,
        isManualCheck: Boolean,
        allowNormalAutoPrompt: Boolean = true,
    ): Evaluation {
        val hasUpdate = release.versionCode > currentVersionCode
        if (!hasUpdate) {
            return Evaluation(hasUpdate = false, shouldPrompt = false, isForced = false)
        }
        val isForced = release.forceUpdate || (currentVersionCode < release.minSupportedVersionCode)
        val shouldPrompt = isManualCheck || isForced || (allowNormalAutoPrompt && release.versionCode != ignoredVersionCode)
        return Evaluation(hasUpdate = true, shouldPrompt = shouldPrompt, isForced = isForced)
    }

    fun verifySha256(expectedHex: String, actualHex: String): Boolean {
        val exp = expectedHex.trim().lowercase()
        val act = actualHex.trim().lowercase()
        if (exp.length != 64 || act.length != 64) return false
        return exp == act
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) {
            return if (Locale.getDefault().language.equals("zh", ignoreCase = true)) "未知大小" else "Unknown size"
        }
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) "${"%.1f".format(mb)} MB" else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }

    fun formatProgressBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        return formatSize(bytes)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) "${"%.1f".format(mb)} MB/s" else "${(bytesPerSec / 1024L).coerceAtLeast(1L)} KB/s"
    }
}
