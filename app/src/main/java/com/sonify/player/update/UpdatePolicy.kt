package com.sonify.player.update

/**
 * 纯 Kotlin 版本升级策略与 SHA-256 完整性判定工具（100% JVM 可单测）。
 */
object UpdatePolicy {

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    data class Evaluation(
        val hasUpdate: Boolean,
        val shouldPrompt: Boolean,
        val isForced: Boolean,
    )

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

    fun evaluate(
        currentVersionCode: Int,
        release: ReleaseInfo,
        ignoredVersionCode: Int,
        isManualCheck: Boolean,
    ): Evaluation {
        val hasUpdate = release.versionCode > currentVersionCode
        if (!hasUpdate) {
            return Evaluation(hasUpdate = false, shouldPrompt = false, isForced = false)
        }
        val isForced = release.forceUpdate || (currentVersionCode < release.minSupportedVersionCode)
        val shouldPrompt = isManualCheck || isForced || (release.versionCode != ignoredVersionCode)
        return Evaluation(hasUpdate = true, shouldPrompt = shouldPrompt, isForced = isForced)
    }

    fun verifySha256(expectedHex: String, actualHex: String): Boolean {
        val exp = expectedHex.trim().lowercase()
        val act = actualHex.trim().lowercase()
        if (exp.length != 64 || act.length != 64) return false
        return exp == act
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "未知大小"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) "${"%.1f".format(mb)} MB" else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) "${"%.1f".format(mb)} MB/s" else "${(bytesPerSec / 1024L).coerceAtLeast(1L)} KB/s"
    }
}
