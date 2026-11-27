package com.sonify.player.search

import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSummary
import com.sonify.player.lyrics.LrcParser
import java.io.File

data class SearchAlbumHit(
    val title: String,
    val artist: String,
    val coverPath: String?,
    val tracks: List<TrackEntity>,
    val score: Int,
)

data class SearchArtistHit(
    val name: String,
    val coverPath: String?,
    val tracks: List<TrackEntity>,
    val score: Int,
)

data class SearchLyricHit(
    val track: TrackEntity,
    val matchedLine: String,
    val timeMs: Long,
    val score: Int,
)

data class LocalGlobalSearchResult(
    val query: String,
    val tokens: List<String>,
    val songs: List<TrackEntity> = emptyList(),
    val albums: List<SearchAlbumHit> = emptyList(),
    val artists: List<SearchArtistHit> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val lyricHits: List<SearchLyricHit> = emptyList(),
) {
    val totalCount: Int
        get() = songs.size + albums.size + artists.size + playlists.size + lyricHits.size
}

/**
 * 纯 Kotlin 全屏跨源聚合检索引擎（100% JVM 可单测）：
 * - 简繁互通 + 多词联合匹配 + 相关性加权打分排序；
 * - 覆盖本地歌曲、专辑、艺术家、自建歌单与本地 `.lrc` 歌词搜歌。
 */
object GlobalSearchEngine {

    fun searchLocal(
        query: String,
        tracks: List<TrackEntity>,
        playlists: List<PlaylistSummary>,
        maxSongs: Int = 80,
    ): LocalGlobalSearchResult {
        val tokens = SearchNormalizer.tokenize(query)
        if (tokens.isEmpty()) {
            return LocalGlobalSearchResult(query = query, tokens = emptyList())
        }

        // 1. 歌曲加权匹配（支持歌名、艺人、专辑、音质规格如 FLAC / 96k / 24b）
        val scoredSongs = ArrayList<Pair<TrackEntity, Int>>()
        for (t in tracks) {
            val nTitle = SearchNormalizer.normalize1To1(t.title)
            val nArtist = SearchNormalizer.normalize1To1(t.artist)
            val nAlbum = SearchNormalizer.normalize1To1(t.album)
            val nSpec = SearchNormalizer.normalize1To1(t.formatBadgeShort())
            val combined = "$nTitle $nArtist $nAlbum $nSpec"

            if (!tokens.all { combined.contains(it) }) continue

            var score = 0
            for (tok in tokens) {
                score += when {
                    nTitle == tok -> 120
                    nTitle.startsWith(tok) -> 90
                    nTitle.contains(tok) -> 65
                    nArtist == tok -> 60
                    nArtist.startsWith(tok) -> 50
                    nArtist.contains(tok) -> 42
                    nAlbum.startsWith(tok) -> 38
                    nAlbum.contains(tok) -> 30
                    else -> 18
                }
            }
            if (t.isFavorite) score += 4
            scoredSongs += t to score
        }
        val topSongs = scoredSongs
            .sortedWith(compareByDescending<Pair<TrackEntity, Int>> { it.second }.thenBy { it.first.title })
            .take(maxSongs)
            .map { it.first }

        // 2. 专辑聚合命中
        val albums = tracks.groupBy { it.album }
            .mapNotNull { (albumName, list) ->
                if (albumName.isBlank()) return@mapNotNull null
                val artistName = list.firstOrNull()?.albumArtist?.ifBlank { list.first().artist }.orEmpty()
                val nAlbum = SearchNormalizer.normalize1To1(albumName)
                val nArtist = SearchNormalizer.normalize1To1(artistName)
                val combined = "$nAlbum $nArtist"
                if (!tokens.all { combined.contains(it) }) return@mapNotNull null
                var score = 0
                for (tok in tokens) {
                    score += when {
                        nAlbum == tok -> 110
                        nAlbum.startsWith(tok) -> 85
                        nAlbum.contains(tok) -> 60
                        nArtist.contains(tok) -> 35
                        else -> 15
                    }
                }
                val sorted = list.sortedWith(compareBy<TrackEntity> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title })
                SearchAlbumHit(
                    title = albumName,
                    artist = artistName,
                    coverPath = sorted.firstNotNullOfOrNull { it.coverPath },
                    tracks = sorted,
                    score = score,
                )
            }
            .sortedByDescending { it.score }
            .take(24)

        // 3. 艺术家聚合命中
        val artists = tracks.groupBy { it.artist }
            .mapNotNull { (artistName, list) ->
                if (artistName.isBlank()) return@mapNotNull null
                val nArtist = SearchNormalizer.normalize1To1(artistName)
                if (!tokens.all { nArtist.contains(it) }) return@mapNotNull null
                var score = 0
                for (tok in tokens) {
                    score += when {
                        nArtist == tok -> 110
                        nArtist.startsWith(tok) -> 85
                        else -> 55
                    }
                }
                SearchArtistHit(
                    name = artistName,
                    coverPath = list.firstNotNullOfOrNull { it.coverPath },
                    tracks = list,
                    score = score,
                )
            }
            .sortedByDescending { it.score }
            .take(20)

        // 4. 本地歌单命中
        val matchedPlaylists = playlists
            .mapNotNull { pl ->
                val nName = SearchNormalizer.normalize1To1(pl.playlist.name)
                val nDesc = SearchNormalizer.normalize1To1(pl.playlist.description)
                val combined = "$nName $nDesc"
                if (!tokens.all { combined.contains(it) }) return@mapNotNull null
                var score = 0
                for (tok in tokens) {
                    score += when {
                        nName == tok -> 100
                        nName.startsWith(tok) -> 80
                        nName.contains(tok) -> 60
                        else -> 30
                    }
                }
                pl to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(20)

        return LocalGlobalSearchResult(
            query = query,
            tokens = tokens,
            songs = topSongs,
            albums = albums,
            artists = artists,
            playlists = matchedPlaylists,
        )
    }

    /**
     * 基于本地同目录 `.lrc` 与已缓存歌词文件做「歌词搜歌」（建议在 Dispatchers.IO 调用）。
     */
    fun searchLyricsFiles(
        query: String,
        tracks: List<TrackEntity>,
        lyricsCacheDir: File? = null,
        maxHits: Int = 20,
    ): List<SearchLyricHit> {
        val tokens = SearchNormalizer.tokenize(query)
        val joined = tokens.joinToString("")
        if (tokens.isEmpty() || joined.length < 2) return emptyList()

        val hits = ArrayList<SearchLyricHit>()
        for (t in tracks) {
            if (hits.size >= maxHits) break
            val lrcFile = resolveLocalLrcFile(t, lyricsCacheDir) ?: continue
            val text = runCatching {
                LrcParser.decodeBytes(lrcFile.readBytes())
            }.getOrNull() ?: continue
            val doc = LrcParser.parse(text, com.sonify.player.lyrics.LyricsSource.LOCAL_LRC) ?: continue
            for (line in doc.lines) {
                val combined = SearchNormalizer.normalize1To1(
                    if (line.translation.isNullOrBlank()) line.text else "${line.text} ${line.translation}"
                )
                if (tokens.all { combined.contains(it) }) {
                    hits += SearchLyricHit(
                        track = t,
                        matchedLine = line.text,
                        timeMs = line.timeMs,
                        score = if (combined.contains(joined)) 90 else 65,
                    )
                    break
                }
            }
        }
        return hits.sortedByDescending { it.score }
    }

    private fun resolveLocalLrcFile(track: TrackEntity, cacheDir: File?): File? {
        track.lrcPath?.let { p ->
            val f = File(p)
            if (f.isFile) return f
        }
        if (!track.path.startsWith("subsonic://")) {
            val audioFile = File(track.path)
            val parent = audioFile.parentFile
            if (parent != null && parent.isDirectory) {
                val sidecar = File(parent, "${audioFile.nameWithoutExtension}.lrc")
                if (sidecar.isFile) return sidecar
            }
        }
        if (cacheDir != null && cacheDir.isDirectory) {
            val hash = track.path.hashCode().toUInt().toString(16)
            cacheDir.listFiles { f -> f.isFile && f.name.contains(hash) && f.name.endsWith(".lrc") }
                ?.firstOrNull()
                ?.let { return it }
        }
        return null
    }
}
