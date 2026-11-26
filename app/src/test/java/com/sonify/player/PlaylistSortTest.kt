package com.sonify.player

import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSorter
import com.sonify.player.library.SortableTrack
import com.sonify.player.library.TrackSortField
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSortTest {

    private val sample = listOf(
        SortableTrack(
            track = TrackEntity(
                path = "/a.flac",
                title = "Zebra",
                artist = "Miles Davis",
                album = "Kind of Blue",
                trackNumber = 2,
                addedAt = 100L,
            ),
            addedAt = 100L,
            position = 0,
        ),
        SortableTrack(
            track = TrackEntity(
                path = "/b.flac",
                title = "Alpha",
                artist = "Bill Evans",
                album = "Waltz for Debby",
                trackNumber = 1,
                addedAt = 300L,
            ),
            addedAt = 300L,
            position = 1,
        ),
        SortableTrack(
            track = TrackEntity(
                path = "/c.flac",
                title = "Middle",
                artist = "Miles Davis",
                album = "Kind of Blue",
                trackNumber = 1,
                addedAt = 200L,
            ),
            addedAt = 200L,
            position = 2,
        ),
    )

    @Test
    fun sortByTitle_ascAndDesc() {
        val asc = PlaylistSorter.sortTracks(sample, TrackSortField.TITLE, ascending = true)
        assertEquals(listOf("Alpha", "Middle", "Zebra"), asc.map { it.track.title })

        val desc = PlaylistSorter.sortTracks(sample, TrackSortField.TITLE, ascending = false)
        assertEquals(listOf("Zebra", "Middle", "Alpha"), desc.map { it.track.title })
    }

    @Test
    fun sortByAlbum_respectsTrackNumberInsideAlbum() {
        val asc = PlaylistSorter.sortTracks(sample, TrackSortField.ALBUM, ascending = true)
        assertEquals(listOf("/c.flac", "/a.flac", "/b.flac"), asc.map { it.track.path })
    }

    @Test
    fun sortByArtist_respectsAlbumAndTrackNumber() {
        val asc = PlaylistSorter.sortTracks(sample, TrackSortField.ARTIST, ascending = true)
        assertEquals(listOf("Bill Evans", "Miles Davis", "Miles Davis"), asc.map { it.track.artist })
        assertEquals(listOf("/b.flac", "/c.flac", "/a.flac"), asc.map { it.track.path })
    }

    @Test
    fun sortByRecentlyAdded_newestFirstWhenDescending() {
        val newestFirst = PlaylistSorter.sortTracks(sample, TrackSortField.RECENTLY_ADDED, ascending = false)
        assertEquals(listOf(300L, 200L, 100L), newestFirst.map { it.addedAt })
    }

    @Test
    fun sortByCustom_preservesExplicitPosition() {
        val custom = PlaylistSorter.sortTracks(sample, TrackSortField.CUSTOM, ascending = true)
        assertEquals(listOf(0, 1, 2), custom.map { it.position })
    }
}
