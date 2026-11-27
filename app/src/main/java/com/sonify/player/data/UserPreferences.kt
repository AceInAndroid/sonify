package com.sonify.player.data

import android.content.Context
import android.os.Environment
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonify.player.HiFiApp
import com.sonify.player.R
import com.sonify.player.i18n.AppLanguage
import com.sonify.player.i18n.SonifyLocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "sonify_settings")

enum class SampleRatePolicy(@StringRes val labelRes: Int, val fixedHz: Int) {
    FOLLOW_SOURCE(R.string.pref_sr_follow, 0),
    FIXED_44100(R.string.pref_sr_44100, 44100),
    FIXED_48000(R.string.pref_sr_48000, 48000),
    FIXED_96000(R.string.pref_sr_96000, 96000),
    FIXED_192000(R.string.pref_sr_192000, 192000),
}

enum class BitDepthPref(@StringRes val labelRes: Int, val subslotBytes: Int) {
    AUTO(R.string.pref_bits_auto, 0),
    BIT_24(R.string.pref_bits_24, 3),
    BIT_32(R.string.pref_bits_32, 4),
}

enum class BufferSizePref(@StringRes val labelRes: Int, val ms: Int) {
    LOW(R.string.pref_buf_low, 100),
    BALANCED(R.string.pref_buf_balanced, 180),
    SAFE(R.string.pref_buf_safe, 300),
}

enum class VolumeMode(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    AUTO_HARDWARE(R.string.pref_vol_auto_label, R.string.pref_vol_auto_sub),
    SOFTWARE_ONLY(R.string.pref_vol_sw_label, R.string.pref_vol_sw_sub),
    FIXED_DIRECT(R.string.pref_vol_direct_label, R.string.pref_vol_direct_sub),
}

enum class ImportMode(@StringRes val labelRes: Int) {
    REFERENCE(R.string.pref_import_ref),
    COPY_TO_LIBRARY(R.string.pref_import_copy),
}

enum class MinDurationFilter(@StringRes val labelRes: Int, val minMs: Long) {
    NONE(R.string.pref_dur_none, 0L),
    SEC_15(R.string.pref_dur_15, 15_000L),
    SEC_30(R.string.pref_dur_30, 30_000L),
}

enum class CoverStyle(@StringRes val labelRes: Int) {
    VINYL(R.string.pref_cover_vinyl),
    SQUARE(R.string.pref_cover_square),
}

enum class DarkCanvasStyle(@StringRes val labelRes: Int) {
    SPOTIFY_CHARCOAL(R.string.pref_canvas_charcoal),
    OLED_BLACK(R.string.pref_canvas_oled),
}

enum class AccentStyle(@StringRes val labelRes: Int) {
    VINYL_GOLD(R.string.pref_accent_gold),
    SPOTIFY_GREEN(R.string.pref_accent_green),
}

enum class ImmersiveScope(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    PLAYER_AND_LOCKSCREEN(R.string.pref_imm_player_label, R.string.pref_imm_player_sub),
    GLOBAL(R.string.pref_imm_global_label, R.string.pref_imm_global_sub),
}

enum class LyricsFontSize(@StringRes val labelRes: Int, val activeSp: Float, val inactiveSp: Float) {
    SMALL(R.string.pref_lrc_size_small, 19f, 16f),
    MEDIUM(R.string.pref_lrc_size_medium, 23f, 18f),
    LARGE(R.string.pref_lrc_size_large, 27f, 21f),
}

enum class LyricsAlignment(@StringRes val labelRes: Int) {
    LEFT(R.string.pref_lrc_align_left),
    CENTER(R.string.pref_lrc_align_center),
}

enum class LyricsBackdropStyle(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    FLUID_AURORA(R.string.pref_lrc_bg_aurora_label, R.string.pref_lrc_bg_aurora_sub),
    COVER_BLUR(R.string.pref_lrc_bg_blur_label, R.string.pref_lrc_bg_blur_sub),
    MINIMAL_DARK(R.string.pref_lrc_bg_dark_label, R.string.pref_lrc_bg_dark_sub),
}

enum class LyricsHighlightStyle(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    PROGRESSIVE_SWEEP(R.string.pref_lrc_hl_sweep_label, R.string.pref_lrc_hl_sweep_sub),
    GLOW_PURE(R.string.pref_lrc_hl_pure_label, R.string.pref_lrc_hl_pure_sub),
    ACCENT_TINT(R.string.pref_lrc_hl_accent_label, R.string.pref_lrc_hl_accent_sub),
    CAPSULE_FOCUS(R.string.pref_lrc_hl_capsule_label, R.string.pref_lrc_hl_capsule_sub),
}

enum class LyricsFontFamily(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val sampleRes: Int,
) {
    SANS_MODERN(R.string.pref_lrc_font_sans_label, R.string.pref_lrc_font_sans_sub, R.string.pref_lrc_font_sans_sample),
    SERIF_EDITORIAL(R.string.pref_lrc_font_serif_label, R.string.pref_lrc_font_serif_sub, R.string.pref_lrc_font_serif_sample),
    ROUNDED_SOFT(R.string.pref_lrc_font_rounded_label, R.string.pref_lrc_font_rounded_sub, R.string.pref_lrc_font_rounded_sample),
    MONO_STUDIO(R.string.pref_lrc_font_mono_label, R.string.pref_lrc_font_mono_sub, R.string.pref_lrc_font_mono_sample),
}

enum class LyricsLineSpacing(
    @StringRes val labelRes: Int,
    val itemGapDp: Int,
    val lineHeightMultiplier: Float,
) {
    COMPACT(R.string.pref_lrc_space_compact, 14, 1.30f),
    COMFORTABLE(R.string.pref_lrc_space_comfort, 22, 1.42f),
    RELAXED(R.string.pref_lrc_space_relaxed, 30, 1.56f),
}

enum class StreamQuality(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    val format: String,
    val maxBitRate: Int,
) {
    RAW(R.string.pref_stream_raw_label, R.string.pref_stream_raw_sub, "raw", 0),
    MP3_320(R.string.pref_stream_mp3_320_label, R.string.pref_stream_mp3_320_sub, "mp3", 320),
    OPUS_192(R.string.pref_stream_opus_192_label, R.string.pref_stream_opus_192_sub, "opus", 192),
    MP3_192(R.string.pref_stream_mp3_192_label, R.string.pref_stream_mp3_192_sub, "mp3", 192),
}

enum class DownloadStorageMode(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    PUBLIC_MUSIC(R.string.pref_dl_public_label, R.string.pref_dl_public_sub),
    APP_PRIVATE(R.string.pref_dl_private_label, R.string.pref_dl_private_sub),
}

enum class AudioFocusPolicy(@StringRes val labelRes: Int, @StringRes val subtitleRes: Int) {
    STANDARD_DUCK(R.string.pref_focus_duck_label, R.string.pref_focus_duck_sub),
    PAUSE_ON_DUCK(R.string.pref_focus_pause_label, R.string.pref_focus_pause_sub),
    IGNORE_DUCK(R.string.pref_focus_ignore_label, R.string.pref_focus_ignore_sub),
}

data class AppSettings(
    val usbExclusiveEnabled: Boolean = true,
    val sampleRatePolicy: SampleRatePolicy = SampleRatePolicy.FOLLOW_SOURCE,
    val bitDepthPref: BitDepthPref = BitDepthPref.AUTO,
    val bufferSizePref: BufferSizePref = BufferSizePref.BALANCED,
    // 音量与耳朵安全保护
    val volumeMode: VolumeMode = VolumeMode.AUTO_HARDWARE,
    val hearingProtectionEnabled: Boolean = true,
    val maxVolumeLimitPercent: Int = 60,
    val safeResetOnPlugEnabled: Boolean = true,
    val safeStartupPercent: Int = 25,
    val volumeStepPercent: Int = 2,
    val usbVolumePercent: Int = 25,
    // UAC 兼容项
    val feedbackControlEnabled: Boolean = true,
    val resetAltOnRateChange: Boolean = false,
    // 播放控制
    val audioFocusPolicy: AudioFocusPolicy = AudioFocusPolicy.STANDARD_DUCK,
    val gaplessEnabled: Boolean = true,
    val fadeEnabled: Boolean = true,
    val pauseOnUnplug: Boolean = true,
    val rememberPosition: Boolean = true,
    // 资料库
    val scanDirs: List<String> = defaultScanDirs(),
    val importMode: ImportMode = ImportMode.REFERENCE,
    val minDurationFilter: MinDurationFilter = MinDurationFilter.SEC_15,
    // 外观、语言与沉浸音乐模式
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val coverStyle: CoverStyle = CoverStyle.VINYL,
    val darkCanvasStyle: DarkCanvasStyle = DarkCanvasStyle.SPOTIFY_CHARCOAL,
    val accentStyle: AccentStyle = AccentStyle.VINYL_GOLD,
    val ambientBackground: Boolean = true,
    val immersiveMusicMode: Boolean = true,
    val immersiveScope: ImmersiveScope = ImmersiveScope.PLAYER_AND_LOCKSCREEN,
    val immersiveHideStatusBar: Boolean = true,
    val immersiveHideNavBar: Boolean = true,
    val immersiveKeepScreenOn: Boolean = false,
    // 歌词显示、沉浸样式与在线匹配
    val lyricsShowMiniPreview: Boolean = true,
    val lyricsAutoOnlineSearch: Boolean = true,
    val lyricsFontSize: LyricsFontSize = LyricsFontSize.MEDIUM,
    val lyricsFontSizeSp: Int = 24,
    val lyricsFontFamily: LyricsFontFamily = LyricsFontFamily.SANS_MODERN,
    val lyricsHighlightStyle: LyricsHighlightStyle = LyricsHighlightStyle.PROGRESSIVE_SWEEP,
    val lyricsBackdropStyle: LyricsBackdropStyle = LyricsBackdropStyle.FLUID_AURORA,
    val lyricsLineSpacing: LyricsLineSpacing = LyricsLineSpacing.COMFORTABLE,
    val lyricsAlignment: LyricsAlignment = LyricsAlignment.LEFT,
    val lyricsBoldActive: Boolean = true,
    val lyricsShowTranslation: Boolean = true,
    val lyricsAutoHideControls: Boolean = true,
    // 通知、锁屏与后台
    val lockScreenVinylEnabled: Boolean = true,
    val notifShowAudioSpec: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    // Subsonic 远程媒体服务器与离线下载
    val subsonicWifiQuality: StreamQuality = StreamQuality.RAW,
    val subsonicCellularQuality: StreamQuality = StreamQuality.MP3_320,
    val subsonicScrobbleEnabled: Boolean = true,
    val downloadStorageMode: DownloadStorageMode = DownloadStorageMode.PUBLIC_MUSIC,
    val downloadQuality: StreamQuality = StreamQuality.RAW,
    val downloadWifiOnly: Boolean = true,
    val librarySongSortField: String = "RECENTLY_ADDED",
    val librarySongSortAscending: Boolean = false,
    // WiFi 局域网传歌
    val wifiTransferPort: Int = 8088,
    val wifiTransferPinEnabled: Boolean = false,
    val wifiTransferAutoSkipDuplicate: Boolean = true,
    // 应用更新、崩溃诊断、匿名日活统计与全局搜索
    val autoCheckUpdate: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val ignoredUpdateVersionCode: Int = 0,
    val customUpdateBaseUrl: String = "",
    val autoUploadCrashLogs: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val analyticsInstallId: String = "",
    val lastAnalyticsActiveDay: String = "",
    val searchHistory: List<String> = emptyList(),
    val lastLibrarySource: String = "local",
    val lastTrackPath: String? = null,
    val lastPositionMs: Long = 0L,
) {
    companion object {
        fun defaultScanDirs(): List<String> {
            val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }
                .getOrDefault("/storage/emulated/0")
            return listOf("$root/Music", "$root/HiFiMusic")
        }
    }
}

class UserPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val USB_EXCLUSIVE = booleanPreferencesKey("usb_exclusive")
        val SAMPLE_RATE_POLICY = stringPreferencesKey("sample_rate_policy")
        val BIT_DEPTH_PREF = stringPreferencesKey("bit_depth_pref")
        val BUFFER_SIZE_PREF = stringPreferencesKey("buffer_size_pref")

        val VOLUME_MODE = stringPreferencesKey("volume_mode_v2")
        val HEARING_PROTECTION = booleanPreferencesKey("hearing_protection")
        val MAX_VOLUME_LIMIT = intPreferencesKey("max_volume_limit_pct")
        val SAFE_RESET_ON_PLUG = booleanPreferencesKey("safe_reset_on_plug")
        val SAFE_STARTUP_PCT = intPreferencesKey("safe_startup_pct")
        val VOLUME_STEP_PCT = intPreferencesKey("volume_step_pct")
        val USB_VOLUME_PCT = intPreferencesKey("usb_volume_pct")

        val FEEDBACK_CONTROL = booleanPreferencesKey("feedback_control")
        val RESET_ALT = booleanPreferencesKey("reset_alt")
        val AUDIO_FOCUS_POLICY = stringPreferencesKey("audio_focus_policy")
        val GAPLESS = booleanPreferencesKey("gapless")
        val FADE = booleanPreferencesKey("fade")
        val PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val SCAN_DIRS = stringSetPreferencesKey("scan_dirs")
        val IMPORT_MODE = stringPreferencesKey("import_mode")
        val MIN_DURATION = stringPreferencesKey("min_duration")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val COVER_STYLE = stringPreferencesKey("cover_style")
        val DARK_CANVAS = stringPreferencesKey("dark_canvas")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val AMBIENT_BG = booleanPreferencesKey("ambient_bg")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_music_mode")
        val IMMERSIVE_SCOPE = stringPreferencesKey("immersive_scope")
        val IMMERSIVE_HIDE_STATUS = booleanPreferencesKey("immersive_hide_status_bar")
        val IMMERSIVE_HIDE_NAV = booleanPreferencesKey("immersive_hide_nav_bar")
        val IMMERSIVE_KEEP_SCREEN_ON = booleanPreferencesKey("immersive_keep_screen_on")
        val LYRICS_MINI_PREVIEW = booleanPreferencesKey("lyrics_mini_preview")
        val LYRICS_AUTO_ONLINE = booleanPreferencesKey("lyrics_auto_online")
        val LYRICS_FONT_SIZE = stringPreferencesKey("lyrics_font_size")
        val LYRICS_FONT_SIZE_SP = intPreferencesKey("lyrics_font_size_sp")
        val LYRICS_FONT_FAMILY = stringPreferencesKey("lyrics_font_family")
        val LYRICS_HIGHLIGHT_STYLE = stringPreferencesKey("lyrics_highlight_style")
        val LYRICS_BACKDROP_STYLE = stringPreferencesKey("lyrics_backdrop_style")
        val LYRICS_LINE_SPACING = stringPreferencesKey("lyrics_line_spacing")
        val LYRICS_ALIGNMENT = stringPreferencesKey("lyrics_alignment")
        val LYRICS_BOLD_ACTIVE = booleanPreferencesKey("lyrics_bold_active")
        val LYRICS_SHOW_TRANSLATION = booleanPreferencesKey("lyrics_show_translation")
        val LYRICS_AUTO_HIDE_CONTROLS = booleanPreferencesKey("lyrics_auto_hide_controls")
        val LOCKSCREEN_VINYL = booleanPreferencesKey("lockscreen_vinyl")
        val NOTIF_SHOW_SPEC = booleanPreferencesKey("notif_show_spec")
        val WAKELOCK = booleanPreferencesKey("wakelock")
        val SUBSONIC_WIFI_QUALITY = stringPreferencesKey("subsonic_wifi_quality")
        val SUBSONIC_CELLULAR_QUALITY = stringPreferencesKey("subsonic_cellular_quality")
        val SUBSONIC_SCROBBLE = booleanPreferencesKey("subsonic_scrobble")
        val DOWNLOAD_STORAGE_MODE = stringPreferencesKey("download_storage_mode")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val LIBRARY_SONG_SORT_FIELD = stringPreferencesKey("library_song_sort_field")
        val LIBRARY_SONG_SORT_ASC = booleanPreferencesKey("library_song_sort_asc")
        val WIFI_TRANSFER_PORT = intPreferencesKey("wifi_transfer_port")
        val WIFI_TRANSFER_PIN_ENABLED = booleanPreferencesKey("wifi_transfer_pin_enabled")
        val WIFI_TRANSFER_AUTO_SKIP_DUP = booleanPreferencesKey("wifi_transfer_auto_skip_dup")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val IGNORED_UPDATE_VERSION_CODE = intPreferencesKey("ignored_update_version_code")
        val CUSTOM_UPDATE_BASE_URL = stringPreferencesKey("custom_update_base_url")
        val AUTO_UPLOAD_CRASH_LOGS = booleanPreferencesKey("auto_upload_crash_logs")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val ANALYTICS_INSTALL_ID = stringPreferencesKey("analytics_install_id")
        val LAST_ANALYTICS_ACTIVE_DAY = stringPreferencesKey("last_analytics_active_day")
        val SEARCH_HISTORY_JSON = stringPreferencesKey("search_history_json")
        val LAST_LIBRARY_SOURCE = stringPreferencesKey("last_library_source")
        val LAST_TRACK_PATH = stringPreferencesKey("last_track_path")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")

        fun deviceVolKey(deviceKey: String) = intPreferencesKey("dev_vol_$deviceKey")
    }

    val settings: StateFlow<AppSettings> = context.dataStore.data
        .map { p ->
            val defaultDirs = AppSettings.defaultScanDirs()
            val dirs = p[Keys.SCAN_DIRS]?.toList()?.sorted()?.ifEmpty { defaultDirs } ?: defaultDirs
            AppSettings(
                usbExclusiveEnabled = p[Keys.USB_EXCLUSIVE] ?: true,
                sampleRatePolicy = p[Keys.SAMPLE_RATE_POLICY]?.let {
                    runCatching { SampleRatePolicy.valueOf(it) }.getOrNull()
                } ?: SampleRatePolicy.FOLLOW_SOURCE,
                bitDepthPref = p[Keys.BIT_DEPTH_PREF]?.let {
                    runCatching { BitDepthPref.valueOf(it) }.getOrNull()
                } ?: BitDepthPref.AUTO,
                bufferSizePref = p[Keys.BUFFER_SIZE_PREF]?.let {
                    runCatching { BufferSizePref.valueOf(it) }.getOrNull()
                } ?: BufferSizePref.BALANCED,
                volumeMode = p[Keys.VOLUME_MODE]?.let {
                    runCatching { VolumeMode.valueOf(it) }.getOrNull()
                } ?: VolumeMode.AUTO_HARDWARE,
                hearingProtectionEnabled = p[Keys.HEARING_PROTECTION] ?: true,
                maxVolumeLimitPercent = (p[Keys.MAX_VOLUME_LIMIT] ?: 60).coerceIn(20, 100),
                safeResetOnPlugEnabled = p[Keys.SAFE_RESET_ON_PLUG] ?: true,
                safeStartupPercent = (p[Keys.SAFE_STARTUP_PCT] ?: 25).coerceIn(10, 40),
                volumeStepPercent = (p[Keys.VOLUME_STEP_PCT] ?: 2).coerceIn(1, 10),
                usbVolumePercent = (p[Keys.USB_VOLUME_PCT] ?: 25).coerceIn(0, 100),
                feedbackControlEnabled = p[Keys.FEEDBACK_CONTROL] ?: true,
                resetAltOnRateChange = p[Keys.RESET_ALT] ?: false,
                audioFocusPolicy = p[Keys.AUDIO_FOCUS_POLICY]?.let {
                    runCatching { AudioFocusPolicy.valueOf(it) }.getOrNull()
                } ?: AudioFocusPolicy.STANDARD_DUCK,
                gaplessEnabled = p[Keys.GAPLESS] ?: true,
                fadeEnabled = p[Keys.FADE] ?: true,
                pauseOnUnplug = p[Keys.PAUSE_ON_UNPLUG] ?: true,
                rememberPosition = p[Keys.REMEMBER_POSITION] ?: true,
                scanDirs = dirs,
                importMode = p[Keys.IMPORT_MODE]?.let {
                    runCatching { ImportMode.valueOf(it) }.getOrNull()
                } ?: ImportMode.REFERENCE,
                minDurationFilter = p[Keys.MIN_DURATION]?.let {
                    runCatching { MinDurationFilter.valueOf(it) }.getOrNull()
                } ?: MinDurationFilter.SEC_15,
                appLanguage = AppLanguage.fromStorageKey(p[Keys.APP_LANGUAGE]),
                coverStyle = p[Keys.COVER_STYLE]?.let {
                    runCatching { CoverStyle.valueOf(it) }.getOrNull()
                } ?: CoverStyle.VINYL,
                darkCanvasStyle = p[Keys.DARK_CANVAS]?.let {
                    runCatching { DarkCanvasStyle.valueOf(it) }.getOrNull()
                } ?: DarkCanvasStyle.SPOTIFY_CHARCOAL,
                accentStyle = p[Keys.ACCENT_STYLE]?.let {
                    runCatching { AccentStyle.valueOf(it) }.getOrNull()
                } ?: AccentStyle.VINYL_GOLD,
                ambientBackground = p[Keys.AMBIENT_BG] ?: true,
                immersiveMusicMode = p[Keys.IMMERSIVE_MODE] ?: true,
                immersiveScope = p[Keys.IMMERSIVE_SCOPE]?.let {
                    runCatching { ImmersiveScope.valueOf(it) }.getOrNull()
                } ?: ImmersiveScope.PLAYER_AND_LOCKSCREEN,
                immersiveHideStatusBar = p[Keys.IMMERSIVE_HIDE_STATUS] ?: true,
                immersiveHideNavBar = p[Keys.IMMERSIVE_HIDE_NAV] ?: true,
                immersiveKeepScreenOn = p[Keys.IMMERSIVE_KEEP_SCREEN_ON] ?: false,
                lyricsShowMiniPreview = p[Keys.LYRICS_MINI_PREVIEW] ?: true,
                lyricsAutoOnlineSearch = p[Keys.LYRICS_AUTO_ONLINE] ?: true,
                lyricsFontSize = p[Keys.LYRICS_FONT_SIZE]?.let {
                    runCatching { LyricsFontSize.valueOf(it) }.getOrNull()
                } ?: LyricsFontSize.MEDIUM,
                lyricsFontSizeSp = (p[Keys.LYRICS_FONT_SIZE_SP] ?: run {
                    when (p[Keys.LYRICS_FONT_SIZE]?.let { runCatching { LyricsFontSize.valueOf(it) }.getOrNull() }) {
                        LyricsFontSize.SMALL -> 19
                        LyricsFontSize.LARGE -> 28
                        else -> 24
                    }
                }).coerceIn(16, 32),
                lyricsFontFamily = p[Keys.LYRICS_FONT_FAMILY]?.let {
                    runCatching { LyricsFontFamily.valueOf(it) }.getOrNull()
                } ?: LyricsFontFamily.SANS_MODERN,
                lyricsHighlightStyle = p[Keys.LYRICS_HIGHLIGHT_STYLE]?.let {
                    runCatching { LyricsHighlightStyle.valueOf(it) }.getOrNull()
                } ?: LyricsHighlightStyle.PROGRESSIVE_SWEEP,
                lyricsBackdropStyle = p[Keys.LYRICS_BACKDROP_STYLE]?.let {
                    runCatching { LyricsBackdropStyle.valueOf(it) }.getOrNull()
                } ?: LyricsBackdropStyle.FLUID_AURORA,
                lyricsLineSpacing = p[Keys.LYRICS_LINE_SPACING]?.let {
                    runCatching { LyricsLineSpacing.valueOf(it) }.getOrNull()
                } ?: LyricsLineSpacing.COMFORTABLE,
                lyricsAlignment = p[Keys.LYRICS_ALIGNMENT]?.let {
                    runCatching { LyricsAlignment.valueOf(it) }.getOrNull()
                } ?: LyricsAlignment.LEFT,
                lyricsBoldActive = p[Keys.LYRICS_BOLD_ACTIVE] ?: true,
                lyricsShowTranslation = p[Keys.LYRICS_SHOW_TRANSLATION] ?: true,
                lyricsAutoHideControls = p[Keys.LYRICS_AUTO_HIDE_CONTROLS] ?: true,
                lockScreenVinylEnabled = p[Keys.LOCKSCREEN_VINYL] ?: true,
                notifShowAudioSpec = p[Keys.NOTIF_SHOW_SPEC] ?: true,
                wakeLockEnabled = p[Keys.WAKELOCK] ?: true,
                subsonicWifiQuality = p[Keys.SUBSONIC_WIFI_QUALITY]?.let {
                    runCatching { StreamQuality.valueOf(it) }.getOrNull()
                } ?: StreamQuality.RAW,
                subsonicCellularQuality = p[Keys.SUBSONIC_CELLULAR_QUALITY]?.let {
                    runCatching { StreamQuality.valueOf(it) }.getOrNull()
                } ?: StreamQuality.MP3_320,
                subsonicScrobbleEnabled = p[Keys.SUBSONIC_SCROBBLE] ?: true,
                downloadStorageMode = p[Keys.DOWNLOAD_STORAGE_MODE]?.let {
                    runCatching { DownloadStorageMode.valueOf(it) }.getOrNull()
                } ?: DownloadStorageMode.PUBLIC_MUSIC,
                downloadQuality = p[Keys.DOWNLOAD_QUALITY]?.let {
                    runCatching { StreamQuality.valueOf(it) }.getOrNull()
                } ?: StreamQuality.RAW,
                downloadWifiOnly = p[Keys.DOWNLOAD_WIFI_ONLY] ?: true,
                librarySongSortField = p[Keys.LIBRARY_SONG_SORT_FIELD] ?: "RECENTLY_ADDED",
                librarySongSortAscending = p[Keys.LIBRARY_SONG_SORT_ASC] ?: false,
                wifiTransferPort = (p[Keys.WIFI_TRANSFER_PORT] ?: 8088).coerceIn(1024, 65500),
                wifiTransferPinEnabled = p[Keys.WIFI_TRANSFER_PIN_ENABLED] ?: false,
                wifiTransferAutoSkipDuplicate = p[Keys.WIFI_TRANSFER_AUTO_SKIP_DUP] ?: true,
                autoCheckUpdate = p[Keys.AUTO_CHECK_UPDATE] ?: true,
                lastUpdateCheckAt = p[Keys.LAST_UPDATE_CHECK_AT] ?: 0L,
                ignoredUpdateVersionCode = p[Keys.IGNORED_UPDATE_VERSION_CODE] ?: 0,
                customUpdateBaseUrl = p[Keys.CUSTOM_UPDATE_BASE_URL] ?: "",
                autoUploadCrashLogs = p[Keys.AUTO_UPLOAD_CRASH_LOGS] ?: true,
                analyticsEnabled = p[Keys.ANALYTICS_ENABLED] ?: true,
                analyticsInstallId = p[Keys.ANALYTICS_INSTALL_ID] ?: "",
                lastAnalyticsActiveDay = p[Keys.LAST_ANALYTICS_ACTIVE_DAY] ?: "",
                searchHistory = parseHistoryJson(p[Keys.SEARCH_HISTORY_JSON]),
                lastLibrarySource = p[Keys.LAST_LIBRARY_SOURCE] ?: "local",
                lastTrackPath = p[Keys.LAST_TRACK_PATH],
                lastPositionMs = p[Keys.LAST_POSITION_MS] ?: 0L,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val current: AppSettings get() = settings.value

    suspend fun getSavedDeviceVolume(deviceKey: String): Int? =
        context.dataStore.data.first()[Keys.deviceVolKey(deviceKey)]

    suspend fun saveDeviceVolume(deviceKey: String, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        context.dataStore.edit {
            it[Keys.USB_VOLUME_PCT] = clamped
            if (deviceKey.isNotBlank()) it[Keys.deviceVolKey(deviceKey)] = clamped
        }
    }

    suspend fun setUsbExclusive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USB_EXCLUSIVE] = enabled }
    }

    suspend fun setSampleRatePolicy(policy: SampleRatePolicy) {
        context.dataStore.edit { it[Keys.SAMPLE_RATE_POLICY] = policy.name }
    }

    suspend fun setBitDepthPref(pref: BitDepthPref) {
        context.dataStore.edit { it[Keys.BIT_DEPTH_PREF] = pref.name }
    }

    suspend fun setBufferSizePref(pref: BufferSizePref) {
        context.dataStore.edit { it[Keys.BUFFER_SIZE_PREF] = pref.name }
    }

    suspend fun setVolumeMode(mode: VolumeMode) {
        context.dataStore.edit { it[Keys.VOLUME_MODE] = mode.name }
    }

    suspend fun setHearingProtection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HEARING_PROTECTION] = enabled }
    }

    suspend fun setMaxVolumeLimit(percent: Int) {
        context.dataStore.edit { it[Keys.MAX_VOLUME_LIMIT] = percent.coerceIn(20, 100) }
    }

    suspend fun setSafeResetOnPlug(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAFE_RESET_ON_PLUG] = enabled }
    }

    suspend fun setSafeStartupPercent(percent: Int) {
        context.dataStore.edit { it[Keys.SAFE_STARTUP_PCT] = percent.coerceIn(10, 40) }
    }

    suspend fun setVolumeStepPercent(step: Int) {
        context.dataStore.edit { it[Keys.VOLUME_STEP_PCT] = step.coerceIn(1, 10) }
    }

    suspend fun setFeedbackControl(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FEEDBACK_CONTROL] = enabled }
    }

    suspend fun setResetAlt(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESET_ALT] = enabled }
    }

    suspend fun setAudioFocusPolicy(policy: AudioFocusPolicy) {
        context.dataStore.edit { it[Keys.AUDIO_FOCUS_POLICY] = policy.name }
    }

    suspend fun setGapless(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GAPLESS] = enabled }
    }

    suspend fun setFade(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FADE] = enabled }
    }

    suspend fun setPauseOnUnplug(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PAUSE_ON_UNPLUG] = enabled }
    }

    suspend fun setRememberPosition(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_POSITION] = enabled }
    }

    suspend fun addScanDir(path: String) {
        val cleaned = path.trim().trimEnd('/')
        if (cleaned.isEmpty()) return
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.SCAN_DIRS]?.toMutableSet() ?: AppSettings.defaultScanDirs().toMutableSet()
            cur.add(cleaned)
            prefs[Keys.SCAN_DIRS] = cur
        }
    }

    suspend fun removeScanDir(path: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.SCAN_DIRS]?.toMutableSet() ?: AppSettings.defaultScanDirs().toMutableSet()
            cur.remove(path)
            prefs[Keys.SCAN_DIRS] = cur
        }
    }

    suspend fun setImportMode(mode: ImportMode) {
        context.dataStore.edit { it[Keys.IMPORT_MODE] = mode.name }
    }

    suspend fun setMinDurationFilter(filter: MinDurationFilter) {
        context.dataStore.edit { it[Keys.MIN_DURATION] = filter.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        SonifyLocaleManager.writePersistedSync(context, language)
        (context.applicationContext as? HiFiApp)?.onLanguageChanged(language)
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = language.storageKey }
    }

    suspend fun setCoverStyle(style: CoverStyle) {
        context.dataStore.edit { it[Keys.COVER_STYLE] = style.name }
    }

    suspend fun setDarkCanvasStyle(style: DarkCanvasStyle) {
        context.dataStore.edit { it[Keys.DARK_CANVAS] = style.name }
    }

    suspend fun setAccentStyle(style: AccentStyle) {
        context.dataStore.edit { it[Keys.ACCENT_STYLE] = style.name }
    }

    suspend fun setAmbientBackground(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMBIENT_BG] = enabled }
    }

    suspend fun setImmersiveMusicMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_MODE] = enabled }
    }

    suspend fun setImmersiveScope(scope: ImmersiveScope) {
        context.dataStore.edit { it[Keys.IMMERSIVE_SCOPE] = scope.name }
    }

    suspend fun setImmersiveHideStatusBar(hide: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_HIDE_STATUS] = hide }
    }

    suspend fun setImmersiveHideNavBar(hide: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_HIDE_NAV] = hide }
    }

    suspend fun setImmersiveKeepScreenOn(keep: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_KEEP_SCREEN_ON] = keep }
    }

    suspend fun setLyricsShowMiniPreview(show: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_MINI_PREVIEW] = show }
    }

    suspend fun setLyricsAutoOnlineSearch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_AUTO_ONLINE] = enabled }
    }

    suspend fun setLyricsFontSize(size: LyricsFontSize) {
        context.dataStore.edit {
            it[Keys.LYRICS_FONT_SIZE] = size.name
            it[Keys.LYRICS_FONT_SIZE_SP] = size.activeSp.toInt().coerceIn(16, 32)
        }
    }

    suspend fun setLyricsFontSizeSp(sizeSp: Int) {
        val clamped = sizeSp.coerceIn(16, 32)
        val bucket = when {
            clamped <= 20 -> LyricsFontSize.SMALL
            clamped >= 27 -> LyricsFontSize.LARGE
            else -> LyricsFontSize.MEDIUM
        }
        context.dataStore.edit {
            it[Keys.LYRICS_FONT_SIZE_SP] = clamped
            it[Keys.LYRICS_FONT_SIZE] = bucket.name
        }
    }

    suspend fun setLyricsFontFamily(family: LyricsFontFamily) {
        context.dataStore.edit { it[Keys.LYRICS_FONT_FAMILY] = family.name }
    }

    suspend fun setLyricsHighlightStyle(style: LyricsHighlightStyle) {
        context.dataStore.edit { it[Keys.LYRICS_HIGHLIGHT_STYLE] = style.name }
    }

    suspend fun setLyricsBackdropStyle(style: LyricsBackdropStyle) {
        context.dataStore.edit { it[Keys.LYRICS_BACKDROP_STYLE] = style.name }
    }

    suspend fun setLyricsLineSpacing(spacing: LyricsLineSpacing) {
        context.dataStore.edit { it[Keys.LYRICS_LINE_SPACING] = spacing.name }
    }

    suspend fun setLyricsAlignment(alignment: LyricsAlignment) {
        context.dataStore.edit { it[Keys.LYRICS_ALIGNMENT] = alignment.name }
    }

    suspend fun setLyricsBoldActive(bold: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_BOLD_ACTIVE] = bold }
    }

    suspend fun setLyricsShowTranslation(show: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_SHOW_TRANSLATION] = show }
    }

    suspend fun setLyricsAutoHideControls(autoHide: Boolean) {
        context.dataStore.edit { it[Keys.LYRICS_AUTO_HIDE_CONTROLS] = autoHide }
    }

    suspend fun resetLyricsStyleDefaults() {
        context.dataStore.edit {
            it[Keys.LYRICS_FONT_SIZE] = LyricsFontSize.MEDIUM.name
            it[Keys.LYRICS_FONT_SIZE_SP] = 24
            it[Keys.LYRICS_FONT_FAMILY] = LyricsFontFamily.SANS_MODERN.name
            it[Keys.LYRICS_HIGHLIGHT_STYLE] = LyricsHighlightStyle.PROGRESSIVE_SWEEP.name
            it[Keys.LYRICS_BACKDROP_STYLE] = LyricsBackdropStyle.FLUID_AURORA.name
            it[Keys.LYRICS_LINE_SPACING] = LyricsLineSpacing.COMFORTABLE.name
            it[Keys.LYRICS_ALIGNMENT] = LyricsAlignment.LEFT.name
            it[Keys.LYRICS_BOLD_ACTIVE] = true
            it[Keys.LYRICS_SHOW_TRANSLATION] = true
            it[Keys.LYRICS_AUTO_HIDE_CONTROLS] = true
        }
    }

    suspend fun setLockScreenVinyl(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCKSCREEN_VINYL] = enabled }
    }

    suspend fun setNotifShowAudioSpec(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_SHOW_SPEC] = enabled }
    }

    suspend fun setWakeLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKELOCK] = enabled }
    }

    suspend fun setSubsonicWifiQuality(quality: StreamQuality) {
        context.dataStore.edit { it[Keys.SUBSONIC_WIFI_QUALITY] = quality.name }
    }

    suspend fun setSubsonicCellularQuality(quality: StreamQuality) {
        context.dataStore.edit { it[Keys.SUBSONIC_CELLULAR_QUALITY] = quality.name }
    }

    suspend fun setSubsonicScrobble(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUBSONIC_SCROBBLE] = enabled }
    }

    suspend fun setDownloadStorageMode(mode: DownloadStorageMode) {
        context.dataStore.edit { it[Keys.DOWNLOAD_STORAGE_MODE] = mode.name }
    }

    suspend fun setDownloadQuality(quality: StreamQuality) {
        context.dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setDownloadWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.DOWNLOAD_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setLibrarySongSort(field: String, ascending: Boolean) {
        context.dataStore.edit {
            it[Keys.LIBRARY_SONG_SORT_FIELD] = field
            it[Keys.LIBRARY_SONG_SORT_ASC] = ascending
        }
    }

    suspend fun setWifiTransferPort(port: Int) {
        context.dataStore.edit { it[Keys.WIFI_TRANSFER_PORT] = port.coerceIn(1024, 65500) }
    }

    suspend fun setWifiTransferPinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_TRANSFER_PIN_ENABLED] = enabled }
    }

    suspend fun setWifiTransferAutoSkipDuplicate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_TRANSFER_AUTO_SKIP_DUP] = enabled }
    }

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATE] = enabled }
    }

    suspend fun setLastUpdateCheckAt(timestampMs: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT] = timestampMs }
    }

    suspend fun setIgnoredUpdateVersionCode(versionCode: Int) {
        context.dataStore.edit { it[Keys.IGNORED_UPDATE_VERSION_CODE] = versionCode.coerceAtLeast(0) }
    }

    suspend fun setCustomUpdateBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.CUSTOM_UPDATE_BASE_URL] = url.trim().trimEnd('/') }
    }

    suspend fun setAutoUploadCrashLogs(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPLOAD_CRASH_LOGS] = enabled }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANALYTICS_ENABLED] = enabled }
    }

    suspend fun setAnalyticsInstallId(installId: String) {
        context.dataStore.edit { it[Keys.ANALYTICS_INSTALL_ID] = installId.trim() }
    }

    suspend fun setLastAnalyticsActiveDay(day: String) {
        context.dataStore.edit { it[Keys.LAST_ANALYTICS_ACTIVE_DAY] = day.trim() }
    }

    suspend fun addSearchHistory(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[Keys.SEARCH_HISTORY_JSON]).toMutableList()
            current.removeAll { it.equals(cleaned, ignoreCase = true) }
            current.add(0, cleaned)
            val trimmed = current.take(MAX_SEARCH_HISTORY)
            prefs[Keys.SEARCH_HISTORY_JSON] = encodeHistoryJson(trimmed)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        context.dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[Keys.SEARCH_HISTORY_JSON]).toMutableList()
            current.removeAll { it.equals(query, ignoreCase = true) }
            prefs[Keys.SEARCH_HISTORY_JSON] = encodeHistoryJson(current)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it[Keys.SEARCH_HISTORY_JSON] = "[]" }
    }

    suspend fun setLastLibrarySource(source: String) {
        context.dataStore.edit { it[Keys.LAST_LIBRARY_SOURCE] = source }
    }

    suspend fun saveLastPlayback(trackPath: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_PATH] = trackPath
            it[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    companion object {
        private const val MAX_SEARCH_HISTORY = 12

        private fun parseHistoryJson(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val out = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) out.add(v)
                }
                out.take(MAX_SEARCH_HISTORY)
            }.getOrDefault(emptyList())
        }

        private fun encodeHistoryJson(items: List<String>): String {
            val arr = JSONArray()
            items.take(MAX_SEARCH_HISTORY).forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
