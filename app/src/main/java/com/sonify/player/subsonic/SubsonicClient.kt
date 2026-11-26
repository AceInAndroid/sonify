package com.sonify.player.subsonic

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Subsonic / OpenSubsonic REST 客户端：
 * - 标准 HTTPS 使用系统默认安全校验证书的 OkHttpClient；
 * - 仅当用户对特定服务器显式勾选「信任自签名证书」时才使用隔离的 unsafeHttpClient；
 * - 所有 REST 呼叫均在 Dispatchers.IO 执行，绝不把密码/Token 写入日志。
 */
class SubsonicClient {

    val standardHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val selfSignedHttpClient: OkHttpClient by lazy {
        buildTrustAllClient(standardHttpClient)
    }

    fun httpClientFor(allowSelfSignedTls: Boolean): OkHttpClient =
        if (allowSelfSignedTls) selfSignedHttpClient else standardHttpClient

    suspend fun ping(creds: SubsonicCredentials): SubsonicPingResult =
        SubsonicParser.parsePing(executeJson(creds, "ping"))

    suspend fun getMusicFolders(creds: SubsonicCredentials): List<SubsonicMusicFolder> =
        SubsonicParser.parseMusicFolders(executeJson(creds, "getMusicFolders"))

    suspend fun getAlbumList2(
        creds: SubsonicCredentials,
        type: String,
        size: Int = 40,
        offset: Int = 0,
        musicFolderId: String? = null,
    ): List<SubsonicAlbum> {
        val params = linkedMapOf(
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString(),
        )
        if (!musicFolderId.isNullOrBlank()) params["musicFolderId"] = musicFolderId
        return SubsonicParser.parseAlbumList2(executeJson(creds, "getAlbumList2", params))
    }

    suspend fun getAlbum(creds: SubsonicCredentials, albumId: String): Pair<SubsonicAlbum, List<SubsonicSong>> =
        SubsonicParser.parseAlbumDetail(executeJson(creds, "getAlbum", mapOf("id" to albumId)))

    suspend fun getArtists(
        creds: SubsonicCredentials,
        musicFolderId: String? = null,
    ): List<SubsonicArtist> {
        val params = if (!musicFolderId.isNullOrBlank()) mapOf("musicFolderId" to musicFolderId) else emptyMap()
        return SubsonicParser.parseArtists(executeJson(creds, "getArtists", params))
    }

    suspend fun getArtist(creds: SubsonicCredentials, artistId: String): Pair<SubsonicArtist, List<SubsonicAlbum>> =
        SubsonicParser.parseArtistDetail(executeJson(creds, "getArtist", mapOf("id" to artistId)))

    suspend fun getPlaylists(creds: SubsonicCredentials): List<SubsonicPlaylist> =
        SubsonicParser.parsePlaylists(executeJson(creds, "getPlaylists"))

    suspend fun getPlaylist(creds: SubsonicCredentials, playlistId: String): Pair<SubsonicPlaylist, List<SubsonicSong>> =
        SubsonicParser.parsePlaylistDetail(executeJson(creds, "getPlaylist", mapOf("id" to playlistId)))

    suspend fun getStarred2(
        creds: SubsonicCredentials,
        musicFolderId: String? = null,
    ): Pair<List<SubsonicAlbum>, List<SubsonicSong>> {
        val params = if (!musicFolderId.isNullOrBlank()) mapOf("musicFolderId" to musicFolderId) else emptyMap()
        return SubsonicParser.parseStarred2(executeJson(creds, "getStarred2", params))
    }

    suspend fun getRandomSongs(
        creds: SubsonicCredentials,
        size: Int = 40,
        musicFolderId: String? = null,
    ): List<SubsonicSong> {
        val params = linkedMapOf("size" to size.toString())
        if (!musicFolderId.isNullOrBlank()) params["musicFolderId"] = musicFolderId
        return SubsonicParser.parseRandomSongs(executeJson(creds, "getRandomSongs", params))
    }

    suspend fun search3(
        creds: SubsonicCredentials,
        query: String,
        songCount: Int = 30,
        albumCount: Int = 20,
        artistCount: Int = 15,
        musicFolderId: String? = null,
    ): SubsonicSearchResult {
        val params = linkedMapOf(
            "query" to query,
            "songCount" to songCount.toString(),
            "albumCount" to albumCount.toString(),
            "artistCount" to artistCount.toString(),
        )
        if (!musicFolderId.isNullOrBlank()) params["musicFolderId"] = musicFolderId
        return SubsonicParser.parseSearch3(executeJson(creds, "search3", params))
    }

    suspend fun setStarred(creds: SubsonicCredentials, songId: String, starred: Boolean) {
        val endpoint = if (starred) "star" else "unstar"
        SubsonicParser.unwrapResponse(executeJson(creds, endpoint, mapOf("id" to songId)))
    }

    suspend fun scrobble(creds: SubsonicCredentials, songId: String, submission: Boolean) {
        SubsonicParser.unwrapResponse(
            executeJson(
                creds,
                "scrobble",
                mapOf(
                    "id" to songId,
                    "submission" to submission.toString(),
                    "time" to System.currentTimeMillis().toString(),
                ),
            )
        )
    }

    /** 在 Subsonic 服务器上新建歌单（可选同时添加初始歌曲） */
    suspend fun createPlaylist(
        creds: SubsonicCredentials,
        name: String,
        songIds: List<String> = emptyList(),
    ): SubsonicPlaylist? {
        val params = mutableListOf("name" to name.trim())
        songIds.forEach { params += "songId" to it }
        val raw = executeJsonMulti(creds, "createPlaylist", params)
        val resp = SubsonicParser.unwrapResponse(raw)
        val pl = resp.optJSONObject("playlist") ?: return null
        val id = pl.optString("id", "").ifBlank { return null }
        return SubsonicPlaylist(
            id = id,
            name = pl.optString("name", name),
            songCount = pl.optInt("songCount", songIds.size),
            durationSec = pl.optInt("duration", 0),
            coverArtId = pl.optString("coverArt", "").takeIf { it.isNotBlank() },
            owner = pl.optString("owner", creds.username),
            comment = pl.optString("comment", ""),
        )
    }

    /** 更新 Subsonic 服务端歌单（改名、改简介、批量加歌、批量按索引移除） */
    suspend fun updatePlaylist(
        creds: SubsonicCredentials,
        playlistId: String,
        name: String? = null,
        comment: String? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    ) {
        val params = mutableListOf("playlistId" to playlistId)
        if (name != null) params += "name" to name.trim()
        if (comment != null) params += "comment" to comment.trim()
        songIdsToAdd.forEach { params += "songIdToAdd" to it }
        songIndexesToRemove.sortedDescending().forEach { params += "songIndexToRemove" to it.toString() }
        SubsonicParser.unwrapResponse(executeJsonMulti(creds, "updatePlaylist", params))
    }

    /** 删除 Subsonic 服务端歌单 */
    suspend fun deletePlaylist(creds: SubsonicCredentials, playlistId: String) {
        SubsonicParser.unwrapResponse(executeJson(creds, "deletePlaylist", mapOf("id" to playlistId)))
    }

    fun fetchBinarySync(url: HttpUrl, allowSelfSignedTls: Boolean): ByteArray? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Sonify-Android/0.2")
            .get()
            .build()
        return httpClientFor(allowSelfSignedTls).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val ct = resp.header("Content-Type").orEmpty()
            // 服务端报错可能返回 JSON/XML 文本，避免当图片解码
            if (ct.contains("json", ignoreCase = true) || ct.contains("xml", ignoreCase = true)) {
                return null
            }
            resp.body?.bytes()
        }
    }

    private suspend fun executeJson(
        creds: SubsonicCredentials,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
    ): String = executeJsonMulti(
        creds = creds,
        endpoint = endpoint,
        params = params.entries.map { it.key to it.value },
    )

    private suspend fun executeJsonMulti(
        creds: SubsonicCredentials,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val url = SubsonicAuth.buildEndpointUrlMulti(creds, endpoint, params)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Sonify-Android/0.2")
            .get()
            .build()
        val client = httpClientFor(creds.allowSelfSignedTls)
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message.ifBlank { "请求失败" }}")
                }
                body
            }
        } catch (e: SubsonicException) {
            throw e
        } catch (e: Exception) {
            val detail = when (e) {
                is javax.net.ssl.SSLHandshakeException ->
                    "HTTPS 证书校验失败（若为私有自签名证书，请在高级选项开启「信任自签名证书」）"
                is java.net.UnknownHostException ->
                    "无法解析服务器域名，请检查网络或地址拼写"
                is java.net.ConnectException ->
                    "无法连接到服务器端口，请确认服务器已启动且网络互通"
                is java.net.SocketTimeoutException ->
                    "连接服务器超时，请稍后重试"
                else -> e.message ?: "网络请求失败"
            }
            throw IOException(detail, e)
        }
    }

    companion object {
        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        private fun buildTrustAllClient(base: OkHttpClient): OkHttpClient {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }
            return base.newBuilder()
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
    }
}
