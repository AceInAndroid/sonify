package com.sonify.player.subsonic

import com.sonify.player.data.SubsonicTrackCacheEntity
import com.sonify.player.data.TrackEntity

/**
 * 虚拟 URI 约定（避免把服务器地址或认证凭据写入 DB / MediaMetadata / Logcat）：
 * - 远程曲目：subsonic://<serverId>/<songId>
 * - 远程封面：subsonic-cover://<serverId>/<coverArtId>
 */
object SubsonicUri {
    const val TRACK_SCHEME = "subsonic"
    const val COVER_SCHEME = "subsonic-cover"

    data class TrackRef(val serverId: Long, val songId: String)
    data class CoverRef(val serverId: Long, val coverArtId: String)

    fun trackUri(serverId: Long, songId: String): String =
        "$TRACK_SCHEME://$serverId/${encodeSegment(songId)}"

    fun coverUri(serverId: Long, coverArtId: String): String =
        "$COVER_SCHEME://$serverId/${encodeSegment(coverArtId)}"

    fun isTrackUri(path: String?): Boolean =
        path != null && path.startsWith("$TRACK_SCHEME://")

    fun isCoverUri(path: String?): Boolean =
        path != null && path.startsWith("$COVER_SCHEME://")

    fun parseTrackUri(uri: String?): TrackRef? {
        if (uri.isNullOrBlank() || !uri.startsWith("$TRACK_SCHEME://")) return null
        val rest = uri.removePrefix("$TRACK_SCHEME://")
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash >= rest.lastIndex) return null
        val serverId = rest.substring(0, slash).toLongOrNull() ?: return null
        val songId = decodeSegment(rest.substring(slash + 1))
        if (songId.isBlank()) return null
        return TrackRef(serverId, songId)
    }

    fun parseCoverUri(uri: String?): CoverRef? {
        if (uri.isNullOrBlank() || !uri.startsWith("$COVER_SCHEME://")) return null
        val rest = uri.removePrefix("$COVER_SCHEME://")
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash >= rest.lastIndex) return null
        val serverId = rest.substring(0, slash).toLongOrNull() ?: return null
        val coverId = decodeSegment(rest.substring(slash + 1))
        if (coverId.isBlank()) return null
        return CoverRef(serverId, coverId)
    }

    private fun encodeSegment(value: String): String {
        val sb = StringBuilder(value.length)
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val safe = (c in 'a'.code..'z'.code) ||
                (c in 'A'.code..'Z'.code) ||
                (c in '0'.code..'9'.code) ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (safe) sb.append(c.toChar())
            else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }

    private fun decodeSegment(encoded: String): String {
        val out = ArrayList<Byte>(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val ch = encoded[i]
            if (ch == '%' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.add(hex.toByte())
                    i += 3
                    continue
                }
            }
            for (b in ch.toString().toByteArray(Charsets.UTF_8)) out.add(b)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}

data class SubsonicCredentials(
    val serverId: Long = 0L,
    val baseUrl: String,
    val username: String,
    val password: String,
    val useLegacyAuth: Boolean = false,
    val allowSelfSignedTls: Boolean = false,
)

data class SubsonicPingResult(
    val apiVersion: String,
    val serverType: String,
    val serverVersion: String,
    val openSubsonic: Boolean,
) {
    fun displayBadge(): String {
        val parts = mutableListOf<String>()
        if (serverType.isNotBlank()) {
            val prettyType = serverType.replaceFirstChar { it.uppercase() }
            parts += if (serverVersion.isNotBlank()) "$prettyType $serverVersion" else prettyType
        } else if (serverVersion.isNotBlank()) {
            parts += serverVersion
        }
        if (apiVersion.isNotBlank()) parts += "API $apiVersion"
        return parts.joinToString(" · ").ifBlank { "Subsonic" }
    }
}

data class SubsonicMusicFolder(
    val id: String,
    val name: String,
)

data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String = "",
    val coverArtId: String? = null,
    val songCount: Int = 0,
    val durationSec: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val starred: Boolean = false,
) {
    fun coverUri(serverId: Long): String? =
        coverArtId?.takeIf { it.isNotBlank() }?.let { SubsonicUri.coverUri(serverId, it) }
}

data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
) {
    fun coverUri(serverId: Long): String? =
        coverArtId?.takeIf { it.isNotBlank() }?.let { SubsonicUri.coverUri(serverId, it) }
}

data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val durationSec: Int = 0,
    val coverArtId: String? = null,
    val owner: String = "",
    val comment: String = "",
) {
    fun coverUri(serverId: Long): String? =
        coverArtId?.takeIf { it.isNotBlank() }?.let { SubsonicUri.coverUri(serverId, it) }
}

data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val durationSec: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val bitrateKbps: Int = 0,
    val suffix: String = "",
    val contentType: String = "",
    val sizeBytes: Long = 0L,
    val coverArtId: String? = null,
    val starred: Boolean = false,
) {
    fun formatLabel(): String {
        val ext = suffix.trim().uppercase()
        if (ext.isNotEmpty()) return ext
        return when {
            contentType.contains("flac", ignoreCase = true) -> "FLAC"
            contentType.contains("wav", ignoreCase = true) -> "WAV"
            contentType.contains("alac", ignoreCase = true) -> "ALAC"
            contentType.contains("mp4", ignoreCase = true) || contentType.contains("aac", ignoreCase = true) -> "M4A"
            contentType.contains("opus", ignoreCase = true) -> "OPUS"
            contentType.contains("ogg", ignoreCase = true) -> "OGG"
            contentType.contains("mpeg", ignoreCase = true) -> "MP3"
            else -> "STREAM"
        }
    }

    fun toCacheEntity(serverId: Long): SubsonicTrackCacheEntity {
        val coverUri = coverArtId?.takeIf { it.isNotBlank() }?.let { SubsonicUri.coverUri(serverId, it) }
        return SubsonicTrackCacheEntity(
            path = SubsonicUri.trackUri(serverId, id),
            serverId = serverId,
            remoteId = id,
            title = title.ifBlank { "未知歌曲" },
            artist = artist.ifBlank { "未知艺术家" },
            album = album.ifBlank { "未知专辑" },
            albumArtist = albumArtist,
            albumId = albumId,
            artistId = artistId,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationSec.coerceAtLeast(0) * 1000L,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels.coerceAtLeast(1),
            bitrateKbps = bitrateKbps,
            format = formatLabel(),
            fileSize = sizeBytes.coerceAtLeast(0L),
            coverPath = coverUri,
            isFavorite = starred,
        )
    }

    fun toTrackEntity(serverId: Long): TrackEntity = toCacheEntity(serverId).toTrackEntity()
}

data class SubsonicSearchResult(
    val songs: List<SubsonicSong> = emptyList(),
    val albums: List<SubsonicAlbum> = emptyList(),
    val artists: List<SubsonicArtist> = emptyList(),
)

class SubsonicException(
    val code: Int,
    val serverMessage: String,
    cause: Throwable? = null,
) : Exception(friendlyMessage(code, serverMessage), cause) {

    companion object {
        fun friendlyMessage(code: Int, raw: String): String {
            val isZh = java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)
            return when (code) {
                10 -> if (isZh) "缺少必要请求参数（$code）" else "Missing required parameter ($code)"
                20 -> if (isZh) "客户端协议版本过低，请更新 Sonify（$code）" else "Client protocol too old — please update Sonify ($code)"
                30 -> if (isZh) "服务器协议版本过旧，不支持当前请求（$code）" else "Server protocol too old for this request ($code)"
                40 -> if (isZh) "用户名或密码错误（40）" else "Wrong username or password (40)"
                41 -> if (isZh) "该服务器不支持 Token 认证，请在高级选项开启「旧版认证」（41）" else "Token auth unsupported — enable Legacy Auth in Advanced Options (41)"
                50 -> if (isZh) "当前账号无访问权限（50）" else "Account lacks permission for this action (50)"
                60 -> if (isZh) "服务器试用期已结束（60）" else "Server trial period has ended (60)"
                70 -> if (isZh) "请求的曲目或资源不存在（70）" else "Requested track or resource not found (70)"
                else -> raw.ifBlank { if (isZh) "服务器返回错误（code=$code）" else "Server returned error (code=$code)" }
            }
        }
    }
}
