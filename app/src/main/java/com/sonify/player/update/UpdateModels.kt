package com.sonify.player.update

import org.json.JSONObject
import java.io.File

/**
 * 服务端返回的最新发行版本元数据（GET /api/v1/update/latest）。
 */
data class ReleaseInfo(
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val forceUpdate: Boolean,
    val releaseNotes: String,
    val apkUrl: String,
    val fileSize: Long,
    val sha256: String,
    val publishedAt: String,
) {
    companion object {
        fun fromJson(raw: String): ReleaseInfo? = runCatching {
            val o = JSONObject(raw)
            val code = o.optInt("versionCode", 0)
            val name = o.optString("versionName", "").trim()
            val url = o.optString("apkUrl", "").trim()
            if (code <= 0 || name.isEmpty() || url.isEmpty()) return null
            ReleaseInfo(
                channel = o.optString("channel", "stable"),
                versionCode = code,
                versionName = name,
                minSupportedVersionCode = o.optInt("minSupportedVersionCode", 0),
                forceUpdate = o.optBoolean("forceUpdate", false),
                releaseNotes = o.optString("releaseNotes", ""),
                apkUrl = url,
                fileSize = o.optLong("fileSize", 0L),
                sha256 = o.optString("sha256", "").trim().lowercase(),
                publishedAt = o.optString("publishedAt", ""),
            )
        }.getOrNull()
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMs: Long) : UpdateState
    data class Available(val release: ReleaseInfo, val isForced: Boolean) : UpdateState
    data class Downloading(
        val release: ReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    data class Verifying(val release: ReleaseInfo) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateState
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateState
}
