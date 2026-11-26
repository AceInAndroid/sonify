package com.sonify.player.subsonic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Subsonic / OpenSubsonic JSON 响应解析器（无 Android 框架依赖，便于 JVM 单元测试）。
 * 兼容标准 Subsonic v1.16.1 与 OpenSubsonic 扩展字段（samplingRate / bitDepth / channelCount / serverVersion / type）。
 */
object SubsonicParser {

    fun unwrapResponse(rawJson: String): JSONObject {
        val root = runCatching { JSONObject(rawJson) }.getOrElse {
            throw SubsonicException(-1, "服务器返回了非 JSON 响应，请检查服务器地址是否正确", it)
        }
        val resp = root.optJSONObject("subsonic-response")
            ?: throw SubsonicException(-1, "响应中缺少 subsonic-response 字段，可能不是 Subsonic 兼容服务器")
        val status = resp.optString("status", "")
        if (!status.equals("ok", ignoreCase = true)) {
            val err = resp.optJSONObject("error")
            val code = err?.optInt("code", -1) ?: -1
            val msg = err?.optString("message", "").orEmpty()
            throw SubsonicException(code, msg)
        }
        return resp
    }

    fun parsePing(rawJson: String): SubsonicPingResult {
        val resp = unwrapResponse(rawJson)
        return SubsonicPingResult(
            apiVersion = resp.optString("version", ""),
            serverType = resp.optString("type", ""),
            serverVersion = resp.optString("serverVersion", ""),
            openSubsonic = resp.optBoolean("openSubsonic", false),
        )
    }

    fun parseMusicFolders(rawJson: String): List<SubsonicMusicFolder> {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("musicFolders") ?: return emptyList()
        return optObjects(container, "musicFolder").mapNotNull { obj ->
            val id = obj.optString("id", "").ifBlank { obj.optInt("id", -1).takeIf { it >= 0 }?.toString() ?: "" }
            if (id.isBlank()) return@mapNotNull null
            SubsonicMusicFolder(
                id = id,
                name = obj.optString("name", "音乐目录 $id"),
            )
        }
    }

    fun parseAlbumList2(rawJson: String): List<SubsonicAlbum> {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("albumList2") ?: return emptyList()
        return optObjects(container, "album").mapNotNull { parseAlbumObj(it) }
    }

    fun parseAlbumDetail(rawJson: String): Pair<SubsonicAlbum, List<SubsonicSong>> {
        val resp = unwrapResponse(rawJson)
        val albumObj = resp.optJSONObject("album")
            ?: throw SubsonicException(70, "专辑详情不存在")
        val album = parseAlbumObj(albumObj) ?: throw SubsonicException(70, "专辑数据无效")
        val songs = optObjects(albumObj, "song").mapNotNull { parseSongObj(it) }
        return album to songs
    }

    fun parseArtists(rawJson: String): List<SubsonicArtist> {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("artists") ?: return emptyList()
        val indexes = optObjects(container, "index")
        val result = mutableListOf<SubsonicArtist>()
        for (idx in indexes) {
            for (artistObj in optObjects(idx, "artist")) {
                parseArtistObj(artistObj)?.let { result += it }
            }
        }
        return result
    }

    fun parseArtistDetail(rawJson: String): Pair<SubsonicArtist, List<SubsonicAlbum>> {
        val resp = unwrapResponse(rawJson)
        val artistObj = resp.optJSONObject("artist")
            ?: throw SubsonicException(70, "艺术家详情不存在")
        val artist = parseArtistObj(artistObj) ?: throw SubsonicException(70, "艺术家数据无效")
        val albums = optObjects(artistObj, "album").mapNotNull { parseAlbumObj(it) }
        return artist to albums
    }

    fun parsePlaylists(rawJson: String): List<SubsonicPlaylist> {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("playlists") ?: return emptyList()
        return optObjects(container, "playlist").mapNotNull { parsePlaylistObj(it) }
    }

    fun parsePlaylistDetail(rawJson: String): Pair<SubsonicPlaylist, List<SubsonicSong>> {
        val resp = unwrapResponse(rawJson)
        val plObj = resp.optJSONObject("playlist")
            ?: throw SubsonicException(70, "歌单不存在")
        val playlist = parsePlaylistObj(plObj) ?: throw SubsonicException(70, "歌单数据无效")
        val songs = optObjects(plObj, "entry").mapNotNull { parseSongObj(it) }
        return playlist to songs
    }

    fun parseStarred2(rawJson: String): Pair<List<SubsonicAlbum>, List<SubsonicSong>> {
        val resp = unwrapResponse(rawJson)
        val starred = resp.optJSONObject("starred2") ?: return emptyList<SubsonicAlbum>() to emptyList()
        val albums = optObjects(starred, "album").mapNotNull { parseAlbumObj(it) }
        val songs = optObjects(starred, "song").mapNotNull { parseSongObj(it) }
        return albums to songs
    }

    fun parseRandomSongs(rawJson: String): List<SubsonicSong> {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("randomSongs") ?: return emptyList()
        return optObjects(container, "song").mapNotNull { parseSongObj(it) }
    }

    fun parseSearch3(rawJson: String): SubsonicSearchResult {
        val resp = unwrapResponse(rawJson)
        val container = resp.optJSONObject("searchResult3") ?: return SubsonicSearchResult()
        return SubsonicSearchResult(
            songs = optObjects(container, "song").mapNotNull { parseSongObj(it) },
            albums = optObjects(container, "album").mapNotNull { parseAlbumObj(it) },
            artists = optObjects(container, "artist").mapNotNull { parseArtistObj(it) },
        )
    }

    private fun parseAlbumObj(obj: JSONObject): SubsonicAlbum? {
        val id = obj.optString("id", "")
        if (id.isBlank()) return null
        return SubsonicAlbum(
            id = id,
            name = obj.optString("name", obj.optString("title", "未知专辑")),
            artist = obj.optString("artist", "未知艺术家"),
            artistId = obj.optString("artistId", ""),
            coverArtId = obj.optString("coverArt", "").takeIf { it.isNotBlank() },
            songCount = obj.optInt("songCount", 0),
            durationSec = obj.optInt("duration", 0),
            year = obj.optInt("year", 0),
            genre = obj.optString("genre", ""),
            starred = obj.optString("starred", "").isNotBlank(),
        )
    }

    private fun parseArtistObj(obj: JSONObject): SubsonicArtist? {
        val id = obj.optString("id", "")
        if (id.isBlank()) return null
        return SubsonicArtist(
            id = id,
            name = obj.optString("name", "未知艺术家"),
            albumCount = obj.optInt("albumCount", 0),
            coverArtId = obj.optString("coverArt", "").takeIf { it.isNotBlank() },
        )
    }

    private fun parsePlaylistObj(obj: JSONObject): SubsonicPlaylist? {
        val id = obj.optString("id", "")
        if (id.isBlank()) return null
        return SubsonicPlaylist(
            id = id,
            name = obj.optString("name", "未命名歌单"),
            songCount = obj.optInt("songCount", 0),
            durationSec = obj.optInt("duration", 0),
            coverArtId = obj.optString("coverArt", "").takeIf { it.isNotBlank() },
            owner = obj.optString("owner", ""),
            comment = obj.optString("comment", ""),
        )
    }

    private fun parseSongObj(obj: JSONObject): SubsonicSong? {
        val id = obj.optString("id", "")
        if (id.isBlank()) return null
        val displayAlbumArtist = obj.optString("displayAlbumArtist", "")
            .ifBlank { obj.optString("albumArtist", "") }
        return SubsonicSong(
            id = id,
            title = obj.optString("title", "未知歌曲"),
            artist = obj.optString("artist", "未知艺术家"),
            album = obj.optString("album", "未知专辑"),
            albumArtist = displayAlbumArtist,
            albumId = obj.optString("albumId", ""),
            artistId = obj.optString("artistId", ""),
            trackNumber = obj.optInt("track", 0),
            discNumber = obj.optInt("discNumber", 0),
            durationSec = obj.optInt("duration", 0),
            sampleRate = obj.optInt("samplingRate", 0),
            bitDepth = obj.optInt("bitDepth", 0),
            channels = obj.optInt("channelCount", 2).coerceAtLeast(1),
            bitrateKbps = obj.optInt("bitRate", 0),
            suffix = obj.optString("suffix", ""),
            contentType = obj.optString("contentType", ""),
            sizeBytes = obj.optLong("size", 0L),
            coverArtId = obj.optString("coverArt", "").takeIf { it.isNotBlank() },
            starred = obj.optString("starred", "").isNotBlank(),
        )
    }

    /** 兼容部分老旧 Subsonic 实现仅返回单个 JSONObject 而非 JSONArray 的情况 */
    private fun optObjects(parent: JSONObject, key: String): List<JSONObject> {
        val raw = parent.opt(key) ?: return emptyList()
        return when (raw) {
            is JSONArray -> buildList(raw.length()) {
                for (i in 0 until raw.length()) {
                    raw.optJSONObject(i)?.let { add(it) }
                }
            }
            is JSONObject -> listOf(raw)
            else -> emptyList()
        }
    }
}
