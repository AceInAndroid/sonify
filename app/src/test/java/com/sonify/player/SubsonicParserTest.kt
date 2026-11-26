package com.sonify.player

import com.sonify.player.subsonic.SubsonicException
import com.sonify.player.subsonic.SubsonicParser
import com.sonify.player.subsonic.SubsonicUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubsonicParserTest {

    @Test
    fun parsePing_readsOpenSubsonicServerInfo() {
        val json = """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "type": "navidrome",
                "serverVersion": "0.53.3 (123abc)",
                "openSubsonic": true
              }
            }
        """.trimIndent()
        val res = SubsonicParser.parsePing(json)
        assertEquals("1.16.1", res.apiVersion)
        assertEquals("navidrome", res.serverType)
        assertEquals("0.53.3 (123abc)", res.serverVersion)
        assertTrue(res.openSubsonic)
        assertTrue(res.displayBadge().contains("Navidrome"))
    }

    @Test
    fun parsePing_throwsFriendlySubsonicExceptionOnAuthError() {
        val json = """
            {
              "subsonic-response": {
                "status": "failed",
                "version": "1.16.1",
                "error": {
                  "code": 40,
                  "message": "Wrong username or password"
                }
              }
            }
        """.trimIndent()
        try {
            SubsonicParser.parsePing(json)
            fail("Expected SubsonicException")
        } catch (e: SubsonicException) {
            assertEquals(40, e.code)
            assertTrue(e.message!!.contains("用户名或密码错误"))
        }
    }

    @Test
    fun parseAlbumDetail_mapsOpenSubsonicHiResFieldsToTrackEntity() {
        val json = """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "album": {
                  "id": "al-1",
                  "name": "Kind of Blue",
                  "artist": "Miles Davis",
                  "coverArt": "al-1",
                  "songCount": 1,
                  "year": 1959,
                  "song": [
                    {
                      "id": "sg-42",
                      "title": "So What",
                      "artist": "Miles Davis",
                      "album": "Kind of Blue",
                      "albumId": "al-1",
                      "track": 1,
                      "discNumber": 1,
                      "duration": 562,
                      "samplingRate": 96000,
                      "bitDepth": 24,
                      "channelCount": 2,
                      "bitRate": 2860,
                      "suffix": "flac",
                      "size": 201234567,
                      "coverArt": "al-1",
                      "starred": "2026-11-26T10:00:00Z"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val (album, songs) = SubsonicParser.parseAlbumDetail(json)
        assertEquals("Kind of Blue", album.name)
        assertEquals(1, songs.size)
        val track = songs.first().toTrackEntity(serverId = 3L)
        assertEquals(SubsonicUri.trackUri(3L, "sg-42"), track.path)
        assertEquals(SubsonicUri.coverUri(3L, "al-1"), track.coverPath)
        assertEquals(96000, track.sampleRate)
        assertEquals(24, track.bitDepth)
        assertTrue(track.isHiRes)
        assertTrue(track.isFavorite)
        assertEquals("FLAC 96k/24b", track.formatBadgeShort())
    }

    @Test
    fun parseSearch3_andArtistsAndPlaylists() {
        val searchJson = """
            {
              "subsonic-response": {
                "status": "ok",
                "searchResult3": {
                  "artist": { "id": "ar-1", "name": "坂本龙一", "albumCount": 12 },
                  "album": [
                    { "id": "al-2", "name": "1996", "artist": "坂本龙一", "songCount": 16 }
                  ],
                  "song": [
                    { "id": "sg-9", "title": "Merry Christmas Mr. Lawrence", "artist": "坂本龙一", "album": "1996", "duration": 288, "suffix": "flac" }
                  ]
                }
              }
            }
        """.trimIndent()
        val res = SubsonicParser.parseSearch3(searchJson)
        assertEquals(1, res.artists.size)
        assertEquals("坂本龙一", res.artists.first().name)
        assertEquals(1, res.albums.size)
        assertEquals(1, res.songs.size)
        assertEquals("FLAC", res.songs.first().formatLabel())
    }
}
