package com.sonify.player.library

import androidx.annotation.StringRes
import com.sonify.player.R
import com.sonify.player.data.TrackEntity

/** 歌单曲目与本地歌曲列表统一多维排序维度 */
enum class TrackSortField(@StringRes val labelRes: Int, val defaultAscending: Boolean) {
    CUSTOM(R.string.pref_sort_custom, true),
    TITLE(R.string.pref_sort_title, true),
    ALBUM(R.string.pref_sort_album, true),
    ARTIST(R.string.pref_sort_artist, true),
    RECENTLY_ADDED(R.string.pref_sort_recent_added, false);

    companion object {
        fun fromNameOrDefault(name: String?, fallback: TrackSortField = CUSTOM): TrackSortField =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** 歌单列表排序维度 */
enum class PlaylistListSort(@StringRes val labelRes: Int) {
    UPDATED(R.string.pref_sort_recent_updated),
    NAME(R.string.pref_sort_playlist_name),
    COUNT(R.string.pref_sort_track_count),
}

data class SortableTrack(
    val track: TrackEntity,
    val addedAt: Long,
    val position: Int,
)

/**
 * 纯函数多维排序引擎（100% JVM 可单测）：
 * 覆盖默认顺序、标题、专辑、艺人、最近添加五种维度及升/降序稳定排序。
 */
object PlaylistSorter {

    fun sortTracks(
        items: List<SortableTrack>,
        field: TrackSortField,
        ascending: Boolean,
    ): List<SortableTrack> {
        if (items.size <= 1) return items
        val comparator: Comparator<SortableTrack> = when (field) {
            TrackSortField.CUSTOM -> compareBy<SortableTrack> { it.position }
                .thenBy { it.addedAt }

            TrackSortField.TITLE -> compareBy<SortableTrack> { it.track.title.lowercase() }
                .thenBy { it.track.artist.lowercase() }
                .thenBy { it.track.album.lowercase() }

            TrackSortField.ALBUM -> compareBy<SortableTrack> { it.track.album.lowercase() }
                .thenBy { it.track.discNumber }
                .thenBy { it.track.trackNumber }
                .thenBy { it.track.title.lowercase() }

            TrackSortField.ARTIST -> compareBy<SortableTrack> { it.track.artist.lowercase() }
                .thenBy { it.track.album.lowercase() }
                .thenBy { it.track.discNumber }
                .thenBy { it.track.trackNumber }
                .thenBy { it.track.title.lowercase() }

            TrackSortField.RECENTLY_ADDED -> compareBy<SortableTrack> { it.addedAt }
                .thenBy { it.position }
        }
        val finalComp = if (ascending) comparator else comparator.reversed()
        return items.sortedWith(finalComp)
    }

    fun sortPlainTracks(
        tracks: List<TrackEntity>,
        field: TrackSortField,
        ascending: Boolean,
    ): List<TrackEntity> {
        val wrapped = tracks.mapIndexed { idx, t -> SortableTrack(t, t.addedAt, idx) }
        return sortTracks(wrapped, field, ascending).map { it.track }
    }
}
