package com.sonify.player.lyrics

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.UserPreferences
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 五级瀑布歌词仓库（内存 LruCache + 磁盘 filesDir/lyrics_cache/ 持久化，零 Room 迁移）：
 * 1. 用户自选/导入缓存（含单曲时间偏移 offsetMs）
 * 2. 同目录同名 .lrc（多编码防乱码 + 双语合并）
 * 3. 音频内嵌歌词标签（FLAC / MP3 / M4A）
 * 4. Subsonic / OpenSubsonic 服务端同步/纯文本歌词
 * 5. LRCLIB 开源免费在线精准匹配（受设置页「自动在线匹配歌词」开关控制）
 * （预留 V2 AI 听歌打轴 Provider 扩展点）
 */
class LyricsRepository(
    private val context: Context,
    private val prefs: UserPreferences,
    private val subsonicRepo: SubsonicRepository,
    private val lrcLibClient: LrcLibClient = LrcLibClient(subsonicRepo.client.standardHttpClient),
) {
    private val memCache = LruCache<String, LyricsDocument>(32)

    private fun cacheDir(): File = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }

    private fun hashKey(trackPath: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(trackPath.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun lrcCacheFile(trackPath: String): File = File(cacheDir(), "${hashKey(trackPath)}.lrc")
    private fun metaCacheFile(trackPath: String): File = File(cacheDir(), "${hashKey(trackPath)}.meta.json")

    suspend fun loadLyrics(track: TrackEntity, forceOnline: Boolean = false): LyricsDocument? =
        withContext(Dispatchers.IO) {
            val savedMeta = readMeta(track.path)
            val savedOffset = savedMeta?.offsetMs ?: 0L

            if (!forceOnline) {
                memCache.get(track.path)?.let { return@withContext it.copy(offsetMs = savedOffset) }
            }

            // 1. 用户自选/在线命中/手动导入的本地缓存（优先级最高）
            val cachedFile = lrcCacheFile(track.path)
            if (!forceOnline && cachedFile.isFile && cachedFile.length() > 0L) {
                val src = savedMeta?.source ?: LyricsSource.USER_CUSTOM
                val raw = LrcParser.decodeBytes(cachedFile.readBytes())
                val doc = LrcParser.parse(raw, src, savedOffset)
                if (doc != null && !doc.isEmpty) {
                    memCache.put(track.path, doc)
                    return@withContext doc
                }
            }

            // 2. 同目录同名 .lrc 文件（本地曲目）
            if (!forceOnline && !SubsonicUri.isTrackUri(track.path)) {
                val lrcFile = resolveSidecarLrcFile(track)
                if (lrcFile != null && lrcFile.isFile && lrcFile.canRead()) {
                    val raw = LrcParser.decodeBytes(lrcFile.readBytes())
                    val doc = LrcParser.parse(raw, LyricsSource.LOCAL_LRC, savedOffset)
                    if (doc != null && !doc.isEmpty) {
                        memCache.put(track.path, doc)
                        return@withContext doc
                    }
                }
            }

            // 3. 音频内嵌歌词标签（FLAC / MP3 / M4A）
            if (!forceOnline && !SubsonicUri.isTrackUri(track.path)) {
                val audioFile = File(track.path)
                val embedded = EmbeddedLyricsExtractor.extractFromFile(audioFile)
                if (!embedded.isNullOrBlank()) {
                    val doc = LrcParser.parse(embedded, LyricsSource.EMBEDDED_TAG, savedOffset)
                    if (doc != null && !doc.isEmpty) {
                        memCache.put(track.path, doc)
                        return@withContext doc
                    }
                }
            }

            // 4. Subsonic / OpenSubsonic 服务端歌词
            val subRef = SubsonicUri.parseTrackUri(track.path)
            if (!forceOnline && subRef != null) {
                val creds = subsonicRepo.getCredentials(subRef.serverId)
                if (creds != null) {
                    val remoteDoc = subsonicRepo.client.getLyricsForSong(
                        creds = creds,
                        songId = subRef.songId,
                        artist = track.artist,
                        title = track.title,
                    )
                    if (remoteDoc != null && !remoteDoc.isEmpty) {
                        val withOffset = remoteDoc.copy(offsetMs = savedOffset)
                        writeCache(track.path, withOffset.rawLrc, LyricsSource.SUBSONIC, savedOffset)
                        memCache.put(track.path, withOffset)
                        return@withContext withOffset
                    }
                }
            }

            // 5. LRCLIB 免费在线自动匹配（受设置项控制，或用户主动 forceOnline）
            if (forceOnline || prefs.current.lyricsAutoOnlineSearch) {
                val durationSec = (track.durationMs / 1000L).toInt().coerceAtLeast(0)
                val exact = lrcLibClient.getExact(
                    trackName = track.title,
                    artistName = track.artist,
                    albumName = track.album,
                    durationSec = durationSec,
                )
                val candidate = exact ?: run {
                    // 精准匹配失败时用 "歌名 艺人" 搜索一次，取最贴近时长的一条
                    val q = listOf(track.title, track.artist.takeIf { it != "未知艺术家" }.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    val list = runCatching { lrcLibClient.search(q) }.getOrDefault(emptyList())
                    list.firstOrNull { c ->
                        durationSec <= 0 || c.durationSec <= 0 || kotlin.math.abs(c.durationSec - durationSec) <= 8
                    } ?: list.firstOrNull()
                }

                if (candidate != null && candidate.bestText.isNotBlank()) {
                    val doc = LrcParser.parse(candidate.bestText, LyricsSource.LRCLIB, savedOffset)
                    if (doc != null && !doc.isEmpty) {
                        writeCache(track.path, candidate.bestText, LyricsSource.LRCLIB, savedOffset)
                        memCache.put(track.path, doc)
                        return@withContext doc
                    }
                }
            }

            null
        }

    suspend fun searchOnlineCandidates(query: String): List<OnlineLyricCandidate> =
        lrcLibClient.search(query)

    suspend fun applyOnlineCandidate(track: TrackEntity, candidate: OnlineLyricCandidate): LyricsDocument? =
        withContext(Dispatchers.IO) {
            val raw = candidate.bestText
            if (raw.isBlank()) return@withContext null
            val offset = readMeta(track.path)?.offsetMs ?: 0L
            val doc = LrcParser.parse(raw, LyricsSource.LRCLIB, offset) ?: return@withContext null
            writeCache(track.path, raw, LyricsSource.LRCLIB, offset)
            memCache.put(track.path, doc)
            doc
        }

    suspend fun importLocalLrc(track: TrackEntity, uri: Uri): LyricsDocument? =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val raw = LrcParser.decodeBytes(bytes)
            val offset = readMeta(track.path)?.offsetMs ?: 0L
            val doc = LrcParser.parse(raw, LyricsSource.USER_CUSTOM, offset) ?: return@withContext null
            writeCache(track.path, raw, LyricsSource.USER_CUSTOM, offset)
            memCache.put(track.path, doc)
            doc
        }

    suspend fun setOffset(trackPath: String, newOffsetMs: Long): Long = withContext(Dispatchers.IO) {
        val clamped = newOffsetMs.coerceIn(-15_000L, 15_000L)
        val existingMeta = readMeta(trackPath)
        writeMeta(
            trackPath = trackPath,
            source = existingMeta?.source ?: memCache.get(trackPath)?.source ?: LyricsSource.LOCAL_LRC,
            offsetMs = clamped,
        )
        memCache.get(trackPath)?.let { cur ->
            memCache.put(trackPath, cur.copy(offsetMs = clamped))
        }
        clamped
    }

    suspend fun clearTrackOverride(track: TrackEntity): LyricsDocument? = withContext(Dispatchers.IO) {
        runCatching { lrcCacheFile(track.path).delete() }
        runCatching { metaCacheFile(track.path).delete() }
        memCache.remove(track.path)
        loadLyrics(track, forceOnline = false)
    }

    fun getCacheSizeBytes(): Long {
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        if (!dir.isDirectory) return 0L
        return dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    fun clearAllCache() {
        memCache.evictAll()
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun resolveSidecarLrcFile(track: TrackEntity): File? {
        track.lrcPath?.let { p ->
            val f = File(p)
            if (f.isFile) return f
        }
        val audio = File(track.path)
        val parent = audio.parentFile ?: return null
        val sibling = File(parent, "${audio.nameWithoutExtension}.lrc")
        return sibling.takeIf { it.isFile }
    }

    private fun writeCache(trackPath: String, rawLrc: String, source: LyricsSource, offsetMs: Long) {
        runCatching {
            lrcCacheFile(trackPath).writeText(rawLrc, Charsets.UTF_8)
            writeMeta(trackPath, source, offsetMs)
        }
    }

    private fun writeMeta(trackPath: String, source: LyricsSource, offsetMs: Long) {
        runCatching {
            val obj = JSONObject()
                .put("source", source.name)
                .put("offsetMs", offsetMs)
                .put("updatedAt", System.currentTimeMillis())
            metaCacheFile(trackPath).writeText(obj.toString(), Charsets.UTF_8)
        }
    }

    private data class SavedLyricsMeta(
        val source: LyricsSource,
        val offsetMs: Long,
    )

    private fun readMeta(trackPath: String): SavedLyricsMeta? {
        val f = metaCacheFile(trackPath)
        if (!f.isFile) return null
        return runCatching {
            val obj = JSONObject(f.readText(Charsets.UTF_8))
            val src = runCatching { LyricsSource.valueOf(obj.optString("source", "")) }
                .getOrDefault(LyricsSource.USER_CUSTOM)
            SavedLyricsMeta(
                source = src,
                offsetMs = obj.optLong("offsetMs", 0L),
            )
        }.getOrNull()
    }

    companion object {
        private const val CACHE_DIR_NAME = "lyrics_cache"
    }
}
