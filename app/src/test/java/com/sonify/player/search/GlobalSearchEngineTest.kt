package com.sonify.player.search

import com.sonify.player.data.PlaylistEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlobalSearchEngineTest {

    private fun track(
        path: String,
        title: String,
        artist: String,
        album: String,
        format: String = "FLAC",
        sampleRate: Int = 44100,
        bitDepth: Int = 16,
        lrcPath: String? = null,
    ) = TrackEntity(
        path = path,
        title = title,
        artist = artist,
        album = album,
        format = format,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        lrcPath = lrcPath,
    )

    @Test
    fun normalizer_matchesTraditionalChineseWithSimplifiedQueryAndHighlightsCorrectly() {
        val display = "周杰倫 - 愛情懸崖 (葉惠美)"
        val tokens = SearchNormalizer.tokenize("周杰伦 爱情悬崖")
        assertEquals(listOf("周杰伦", "爱情悬崖"), tokens)

        val ranges = SearchNormalizer.findHighlightRanges(display, tokens)
        assertEquals(2, ranges.size)
        assertEquals("周杰倫", display.substring(ranges[0]))
        assertEquals("愛情懸崖", display.substring(ranges[1]))
    }

    @Test
    fun searchLocal_ranksExactTitleAheadOfArtistAndSupportsMultiTokenAndFormat() {
        val tracks = listOf(
            track("/music/1.flac", "愛情懸崖", "周杰倫", "葉惠美", "FLAC", 96000, 24),
            track("/music/2.flac", "晴天", "周杰倫", "葉惠美", "FLAC", 44100, 16),
            track("/music/3.wav", "稻香", "周杰倫", "魔杰座", "WAV", 44100, 16),
            track("/music/4.flac", "晴天 (Live)", "林俊傑", "精選輯", "FLAC", 48000, 24),
        )
        val playlists = listOf(
            PlaylistSummary(
                playlist = PlaylistEntity(id = 1, name = "周杰倫無損必聽", description = "葉惠美精選"),
                trackCount = 2,
                totalDurationMs = 500_000L,
                autoCovers = emptyList(),
                remoteTrackCount = 0,
            )
        )

        // Simplified multi-token query hitting Traditional tracks, albums, artists, and playlists
        val res = GlobalSearchEngine.searchLocal("周杰伦 叶惠美", tracks, playlists)
        assertEquals(2, res.songs.size)
        assertEquals(1, res.albums.size)
        assertEquals("葉惠美", res.albums.first().title)
        assertEquals(1, res.playlists.size)

        // Exact title match ranks before prefix/contains match
        val titleRes = GlobalSearchEngine.searchLocal("晴天", tracks, playlists)
        assertEquals(2, titleRes.songs.size)
        assertEquals("晴天", titleRes.songs[0].title)
        assertEquals("晴天 (Live)", titleRes.songs[1].title)

        // Format + spec query works (e.g. "FLAC 96k")
        val hiRes = GlobalSearchEngine.searchLocal("flac 96k", tracks, playlists)
        assertEquals(1, hiRes.songs.size)
        assertEquals("愛情懸崖", hiRes.songs.first().title)
    }

    @Test
    fun searchLyricsFiles_findsSnippetAndTimestampFromSidecarLrc() {
        val tempDir = File.createTempFile("sonify_search_lrc", "").apply {
            delete()
            mkdirs()
        }
        try {
            val lrcFile = File(tempDir, "晴天.lrc")
            lrcFile.writeText(
                """
                [00:28.50]故事的小黃花 從出生那年就飄著
                [00:35.20]童年的盪鞦韆 隨記憶一直晃到現在
                """.trimIndent(),
                Charsets.UTF_8,
            )
            val audioPath = File(tempDir, "晴天.flac").absolutePath
            val t = track(audioPath, "晴天", "周杰倫", "葉惠美")

            // Search in Simplified Chinese against Traditional LRC text
            val hits = GlobalSearchEngine.searchLyricsFiles("故事 小黄花", listOf(t))
            assertEquals(1, hits.size)
            assertEquals(28_500L, hits[0].timeMs)
            assertTrue(hits[0].matchedLine.contains("小黃花"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
