package com.sonify.player

import com.sonify.player.lyrics.EmbeddedLyricsExtractor
import com.sonify.player.lyrics.LrcLibClient
import com.sonify.player.lyrics.LyricsSource
import com.sonify.player.subsonic.SubsonicParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LyricsSourceTest {

    @Test
    fun extractFlacLyrics_readsVorbisCommentLyricsTag() {
        val flacBytes = buildMinimalFlacWithLyrics("[00:08.00]So What - Live")
        val extracted = EmbeddedLyricsExtractor.extractFlacLyrics(ByteArrayInputStream(flacBytes))
        assertEquals("[00:08.00]So What - Live", extracted)
    }

    @Test
    fun parseSubsonicStructuredLyrics_buildsSyncedDocument() {
        val json = """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "lyricsList": {
                  "structuredLyrics": [
                    {
                      "synced": true,
                      "offset": 0,
                      "line": [
                        { "start": 12000, "value": "First synced line" },
                        { "start": 25500, "value": "Second synced line" }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val doc = SubsonicParser.parseLyricsBySongId(json)
        assertNotNull(doc)
        assertTrue(doc!!.isSynced)
        assertEquals(LyricsSource.SUBSONIC, doc.source)
        assertEquals(2, doc.lines.size)
        assertEquals(12_000L, doc.lines[0].timeMs)
        assertEquals("Second synced line", doc.lines[1].text)
    }

    @Test
    fun lrcLibParser_prioritizesSyncedCandidates() {
        val json = """
            [
              {
                "id": 1,
                "trackName": "Test",
                "artistName": "Artist",
                "albumName": "Album",
                "duration": 210.0,
                "plainLyrics": "Plain only",
                "syncedLyrics": null
              },
              {
                "id": 2,
                "trackName": "Test",
                "artistName": "Artist",
                "albumName": "Album Deluxe",
                "duration": 211.0,
                "plainLyrics": "Plain too",
                "syncedLyrics": "[00:05.00]Synced line"
              }
            ]
        """.trimIndent()
        val list = LrcLibClient.parseSearchCandidates(json)
        assertEquals(2, list.size)
        // 带同步时间戳的排第一
        assertEquals(2L, list[0].id)
        assertTrue(list[0].hasSynced)
    }

    private fun buildMinimalFlacWithLyrics(lyrics: String): ByteArray {
        val comment = "LYRICS=$lyrics".toByteArray(Charsets.UTF_8)
        val vendor = "Sonify".toByteArray(Charsets.UTF_8)
        val vc = ByteArrayOutputStream().apply {
            writeLe32(vendor.size)
            write(vendor)
            writeLe32(1)
            writeLe32(comment.size)
            write(comment)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            // isLast = true (0x80) | type = 4 (VORBIS_COMMENT)
            write(0x84)
            write((vc.size ushr 16) and 0xFF)
            write((vc.size ushr 8) and 0xFF)
            write(vc.size and 0xFF)
            write(vc)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe32(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 24) and 0xFF)
    }
}
