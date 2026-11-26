package com.sonify.player.lyrics

import com.sonify.player.data.TrackEntity

enum class LyricsSource(val label: String) {
    USER_CUSTOM("自定义 LRC"),
    LOCAL_LRC("本地 LRC"),
    EMBEDDED_TAG("音频内嵌"),
    SUBSONIC("Subsonic"),
    LRCLIB("LRCLIB 在线"),
    NONE("无歌词"),
}

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
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
