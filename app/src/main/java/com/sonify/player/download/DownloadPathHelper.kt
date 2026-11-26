package com.sonify.player.download

import com.sonify.player.data.StreamQuality

/**
 * 离线下载文件路径与文件名清洗纯函数工具（100% JVM 可单测）：
 * 防止非法文件名字符、路径穿越（../）与空目录段。
 */
object DownloadPathHelper {

    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val MULTI_SPACES = Regex("""\s+""")

    fun sanitizeSegment(raw: String, fallback: String): String {
        var clean = raw
            .replace("..", "_")
            .replace(ILLEGAL_CHARS, "_")
            .replace(MULTI_SPACES, " ")
            .trim()
            .trim('.')
        if (clean.length > 80) clean = clean.take(80).trim()
        if (clean.trim('_', '.', ' ').isEmpty()) return fallback
        return clean
    }

    fun resolveExtension(formatLabel: String, quality: StreamQuality, contentType: String = ""): String {
        if (quality != StreamQuality.RAW && quality.format.isNotBlank()) {
            return quality.format.lowercase()
        }
        val fmt = formatLabel.trim().lowercase()
        val fromFormat = when (fmt) {
            "flac", "wav", "mp3", "m4a", "aac", "ogg", "opus", "alac", "aiff", "wv", "ape" -> fmt
            else -> ""
        }
        if (fromFormat.isNotEmpty()) return fromFormat
        return when {
            contentType.contains("flac", ignoreCase = true) -> "flac"
            contentType.contains("wav", ignoreCase = true) -> "wav"
            contentType.contains("opus", ignoreCase = true) -> "opus"
            contentType.contains("ogg", ignoreCase = true) -> "ogg"
            contentType.contains("mp4", ignoreCase = true) || contentType.contains("aac", ignoreCase = true) -> "m4a"
            contentType.contains("mpeg", ignoreCase = true) -> "mp3"
            else -> "flac"
        }
    }

    /** 生成规范相对路径：<艺人>/<专辑>/<曲号> - <歌名>.<扩展名> */
    fun buildRelativePath(
        artist: String,
        album: String,
        trackNumber: Int,
        title: String,
        extension: String,
    ): String {
        val safeArtist = sanitizeSegment(artist, "Unknown Artist")
        val safeAlbum = sanitizeSegment(album, "Unknown Album")
        val safeTitle = sanitizeSegment(title, "Track")
        val prefix = if (trackNumber in 1..999) "%02d - ".format(trackNumber) else ""
        val ext = sanitizeSegment(extension, "flac").lowercase()
        return "$safeArtist/$safeAlbum/$prefix$safeTitle.$ext"
    }
}
