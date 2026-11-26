package com.sonify.player

import com.sonify.player.data.StreamQuality
import com.sonify.player.download.DownloadPathHelper
import com.sonify.player.subsonic.SubsonicAuth
import com.sonify.player.subsonic.SubsonicCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadPathHelperTest {

    @Test
    fun sanitizeSegment_stripsIllegalCharsAndTraversal() {
        val cleaned = DownloadPathHelper.sanitizeSegment("../AC/DC: Live*?", "Fallback")
        assertFalse(cleaned.contains(".."))
        assertFalse(cleaned.contains("/"))
        assertFalse(cleaned.contains(":"))
        assertFalse(cleaned.contains("*"))
        assertEquals("Fallback", DownloadPathHelper.sanitizeSegment("   ...  ", "Fallback"))
    }

    @Test
    fun buildRelativePath_formatsTrackNumberAndExtension() {
        val rel = DownloadPathHelper.buildRelativePath(
            artist = "坂本龙一",
            album = "1996",
            trackNumber = 3,
            title = "Merry Christmas Mr. Lawrence",
            extension = "FLAC",
        )
        assertEquals("坂本龙一/1996/03 - Merry Christmas Mr. Lawrence.flac", rel)
    }

    @Test
    fun resolveExtension_usesRawFormatOrTranscodeTarget() {
        assertEquals("flac", DownloadPathHelper.resolveExtension("FLAC", StreamQuality.RAW))
        assertEquals("mp3", DownloadPathHelper.resolveExtension("FLAC", StreamQuality.MP3_320))
        assertEquals("opus", DownloadPathHelper.resolveExtension("WAV", StreamQuality.OPUS_192))
        assertEquals("wav", DownloadPathHelper.resolveExtension("STREAM", StreamQuality.RAW, "audio/wav"))
    }

    @Test
    fun buildDownloadUrl_andMultiParamsForUpdatePlaylist() {
        val creds = SubsonicCredentials(
            serverId = 1L,
            baseUrl = "https://music.example.com",
            username = "bob",
            password = "secret",
        )
        val rawUrl = SubsonicAuth.buildDownloadUrl(creds, "sg-1", StreamQuality.RAW, saltOverride = "abcdef")
        assertEquals("/rest/download.view", rawUrl.encodedPath)
        assertEquals("sg-1", rawUrl.queryParameter("id"))
        assertNull(rawUrl.queryParameter("maxBitRate"))

        val transcodeUrl = SubsonicAuth.buildDownloadUrl(creds, "sg-1", StreamQuality.MP3_320, saltOverride = "abcdef")
        assertEquals("/rest/stream.view", transcodeUrl.encodedPath)
        assertEquals("320", transcodeUrl.queryParameter("maxBitRate"))
        assertEquals("mp3", transcodeUrl.queryParameter("format"))

        val updateUrl = SubsonicAuth.buildEndpointUrlMulti(
            creds = creds,
            endpoint = "updatePlaylist",
            params = listOf(
                "playlistId" to "pl-9",
                "songIdToAdd" to "s1",
                "songIdToAdd" to "s2",
                "songIndexToRemove" to "5",
                "songIndexToRemove" to "2",
            ),
            saltOverride = "abcdef",
        )
        assertEquals(listOf("s1", "s2"), updateUrl.queryParameterValues("songIdToAdd"))
        assertEquals(listOf("5", "2"), updateUrl.queryParameterValues("songIndexToRemove"))
    }
}
