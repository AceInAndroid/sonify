package com.sonify.player.lyrics

import androidx.annotation.StringRes
import com.sonify.player.R
import com.sonify.player.data.TrackEntity

enum class LyricsSource(@StringRes val labelRes: Int) {
    USER_CUSTOM(R.string.lyrics_source_custom),
    LOCAL_LRC(R.string.lyrics_source_sidecar),
    EMBEDDED_TAG(R.string.lyrics_source_embedded),
    SUBSONIC(R.string.lyrics_source_subsonic),
    LRCLIB(R.string.lyrics_source_lrclib),
    NONE(R.string.lyrics_source_none),
}

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList(),
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val source: LyricsSource,
    val rawLrc: String = "",
    val offsetMs: Long = 0L,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

data class OnlineLyricCandidate(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?,
) {
    val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val bestText: String get() = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics.orEmpty()
}

/**
 * 统一歌词提供者契约（支持瀑布降级，并为 V2 AI 音频打轴 Provider 预留扩展点）。
 */
interface LyricsProvider {
    val source: LyricsSource
    suspend fun fetch(track: TrackEntity): LyricsDocument?
}
