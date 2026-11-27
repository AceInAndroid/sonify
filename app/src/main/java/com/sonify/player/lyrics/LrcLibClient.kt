package com.sonify.player.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 开源免费同步歌词库 LRCLIB（https://lrclib.net）客户端：
 * - 无需 API Key，无商业风控；
 * - 支持按 歌名+艺人+专辑+时长 精准匹配，以及自由关键词搜索候选列表。
 */
class LrcLibClient(
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val http: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getExact(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int,
    ): OnlineLyricCandidate? = withContext(Dispatchers.IO) {
        val cleanTitle = trackName.trim()
        val cleanArtist = artistName.trim()
        if (cleanTitle.isEmpty()) return@withContext null

        val urlBuilder = "$BASE_URL/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", cleanTitle)
        if (cleanArtist.isNotEmpty() && cleanArtist != "未知艺术家" && cleanArtist != "Unknown Artist") {
            urlBuilder.addQueryParameter("artist_name", cleanArtist)
        }
        val cleanAlbum = albumName.trim()
        if (cleanAlbum.isNotEmpty() && cleanAlbum != "未知专辑" && cleanAlbum != "Unknown Album") {
            urlBuilder.addQueryParameter("album_name", cleanAlbum)
        }
        if (durationSec > 0) {
            urlBuilder.addQueryParameter("duration", durationSec.toString())
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                parseSingleCandidate(body)
            }
        }.getOrNull()
    }

    suspend fun search(query: String): List<OnlineLyricCandidate> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val url = "$BASE_URL/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val isZh = java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)
                throw IllegalStateException(
                    if (isZh) "在线搜词服务响应异常（HTTP ${resp.code}）"
                    else "Lyrics service returned HTTP ${resp.code}"
                )
            }
            val body = resp.body?.string().orEmpty()
            parseSearchCandidates(body)
        }
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "Sonify/0.2 (https://github.com/AceInAndroid/sonify)"

        fun parseSingleCandidate(rawJson: String): OnlineLyricCandidate? {
            val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            return parseCandidateObj(obj)
        }

        fun parseSearchCandidates(rawJson: String): List<OnlineLyricCandidate> {
            val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
            val list = buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { parseCandidateObj(it) }?.let { add(it) }
                }
            }
            // 优先把带同步时间戳的候选排在前面
            return list.sortedByDescending { it.hasSynced }
        }

        private fun parseCandidateObj(obj: JSONObject): OnlineLyricCandidate? {
            val synced = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
            val plain = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
            if (synced == null && plain == null) return null
            return OnlineLyricCandidate(
                id = obj.optLong("id", 0L),
                trackName = obj.optString("trackName", obj.optString("name", "")),
                artistName = obj.optString("artistName", ""),
                albumName = obj.optString("albumName", ""),
                durationSec = obj.optDouble("duration", 0.0).toInt().coerceAtLeast(0),
                syncedLyrics = synced,
                plainLyrics = plain,
            )
        }
    }
}
