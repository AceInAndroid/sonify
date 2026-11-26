package com.sonify.player

import com.sonify.player.data.StreamQuality
import com.sonify.player.subsonic.SubsonicAuth
import com.sonify.player.subsonic.SubsonicCredentials
import com.sonify.player.subsonic.SubsonicUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {

    @Test
    fun computeToken_matchesOfficialSubsonicSpecVector() {
        // Subsonic API 官方文档标准向量：md5("sesame" + "c19b2d") = "26719a1196d2a940705a59634eb18eab"
        assertEquals(
            "26719a1196d2a940705a59634eb18eab",
            SubsonicAuth.computeToken("sesame", "c19b2d"),
        )
    }

    @Test
    fun encodeLegacyPassword_encodesUtf8Hex() {
        assertEquals("enc:736573616d65", SubsonicAuth.encodeLegacyPassword("sesame"))
        // 中文密码 UTF-8 编码正确
        assertEquals("enc:e99fb3e4b990", SubsonicAuth.encodeLegacyPassword("音乐"))
    }

    @Test
    fun normalizeBaseUrl_handlesSchemesTrailingSlashesAndSubpaths() {
        assertEquals(
            "http://192.168.1.10:4533",
            SubsonicAuth.normalizeBaseUrl("  192.168.1.10:4533/  "),
        )
        assertEquals(
            "https://music.example.com/navidrome",
            SubsonicAuth.normalizeBaseUrl("music.example.com/navidrome/rest/"),
        )
        assertEquals(
            "https://nas.home.io:8443",
            SubsonicAuth.normalizeBaseUrl("https://nas.home.io:8443///"),
        )
    }

    @Test
    fun buildStreamUrl_rawVsTranscode() {
        val creds = SubsonicCredentials(
            serverId = 7L,
            baseUrl = "https://music.example.com/sub",
            username = "alice",
            password = "sesame",
            useLegacyAuth = false,
        )
        val rawUrl = SubsonicAuth.buildStreamUrl(
            creds = creds,
            songId = "tr-100",
            quality = StreamQuality.RAW,
            saltOverride = "c19b2d",
        )
        assertEquals("/sub/rest/stream.view", rawUrl.encodedPath)
        assertEquals("alice", rawUrl.queryParameter("u"))
        assertEquals("26719a1196d2a940705a59634eb18eab", rawUrl.queryParameter("t"))
        assertEquals("c19b2d", rawUrl.queryParameter("s"))
        assertEquals("raw", rawUrl.queryParameter("format"))
        assertNull(rawUrl.queryParameter("maxBitRate"))

        val mp3Url = SubsonicAuth.buildStreamUrl(
            creds = creds.copy(useLegacyAuth = true),
            songId = "tr-100",
            quality = StreamQuality.MP3_320,
        )
        assertEquals("enc:736573616d65", mp3Url.queryParameter("p"))
        assertNull(mp3Url.queryParameter("t"))
        assertEquals("mp3", mp3Url.queryParameter("format"))
        assertEquals("320", mp3Url.queryParameter("maxBitRate"))
    }

    @Test
    fun subsonicUri_roundTripNeverLeaksSecrets() {
        val trackUri = SubsonicUri.trackUri(12L, "song/abc 123?x=1")
        assertTrue(SubsonicUri.isTrackUri(trackUri))
        assertFalse(trackUri.contains("sesame"))
        val ref = SubsonicUri.parseTrackUri(trackUri)
        assertNotNull(ref)
        assertEquals(12L, ref!!.serverId)
        assertEquals("song/abc 123?x=1", ref.songId)

        val coverUri = SubsonicUri.coverUri(12L, "al-999")
        assertTrue(SubsonicUri.isCoverUri(coverUri))
        val coverRef = SubsonicUri.parseCoverUri(coverUri)
        assertNotNull(coverRef)
        assertEquals(12L, coverRef!!.serverId)
        assertEquals("al-999", coverRef.coverArtId)
    }
}
