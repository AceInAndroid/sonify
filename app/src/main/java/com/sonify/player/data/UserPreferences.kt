package com.sonify.player.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "sonify_settings")

enum class SampleRatePolicy(val label: String, val fixedHz: Int) {
    FOLLOW_SOURCE("跟随源文件（推荐）", 0),
    FIXED_44100("固定 44.1 kHz", 44100),
    FIXED_48000("固定 48 kHz", 48000),
    FIXED_96000("固定 96 kHz", 96000),
    FIXED_192000("固定 192 kHz", 192000),
}

enum class BitDepthPref(val label: String, val subslotBytes: Int) {
    AUTO("自动匹配（推荐）", 0),
    BIT_24("24-bit（兼容便携 DAC）", 3),
    BIT_32("32-bit 容器", 4),
}

enum class BufferSizePref(val label: String, val ms: Int) {
    LOW("低延迟（100 ms）", 100),
    BALANCED("均衡（180 ms · 推荐）", 180),
    SAFE("抗抖动（300 ms）", 300),
}

enum class VolumeMode(val label: String, val subtitle: String) {
    AUTO_HARDWARE(
        "智能硬件优先（推荐）",
        "优先调 DAC 硬件音量保持 Bit-Perfect，无硬件音量时自动切对数软件音量"
    ),
    SOFTWARE_ONLY(
        "强制对数软件音量",
        "始终在 C++ 端用对数 dB 曲线做 32-bit 高精度衰减"
    ),
    FIXED_DIRECT(
        "固定直通 100%（仅外接耳放）",
        "0 dBFS 满幅输出，直推耳塞有炸耳风险，仅限外接独立耳放/前级"
    ),
}

enum class ImportMode(val label: String) {
    REFERENCE("仅引用原路径"),
    COPY_TO_LIBRARY("拷贝到 HiFiMusic 资料库"),
}

enum class MinDurationFilter(val label: String, val minMs: Long) {
    NONE("不过滤", 0L),
    SEC_15("忽略短于 15 秒", 15_000L),
    SEC_30("忽略短于 30 秒", 30_000L),
}

enum class CoverStyle(val label: String) {
    VINYL("黑胶唱片（旋转）"),
    SQUARE("极简方形封面"),
}

enum class DarkCanvasStyle(val label: String) {
    SPOTIFY_CHARCOAL("Spotify 炭黑 (#121212)"),
    OLED_BLACK("OLED 纯黑 (#000000)"),
}

enum class AccentStyle(val label: String) {
    VINYL_GOLD("黑胶暖金 (#E5A93C)"),
    SPOTIFY_GREEN("Spotify 翠绿 (#1DB954)"),
}

enum class ImmersiveScope(val label: String, val subtitle: String) {
    PLAYER_AND_LOCKSCREEN(
        "仅播放页与锁屏（推荐）",
        "进入黑胶播放页与锁屏时自动隐藏系统栏，回到音乐库与设置自动恢复"
    ),
    GLOBAL(
        "全局应用",
        "在 Sonify 所有页面均隐藏所选系统栏，边缘滑动可临时呼出"
    ),
}

enum class StreamQuality(
    val label: String,
    val subtitle: String,
    val format: String,
    val maxBitRate: Int,
) {
    RAW(
        "原盘无损直通（推荐）",
        "不经服务端转码（format=raw），完美匹配 USB DAC Bit-Perfect 独占",
        "raw",
        0,
    ),
    MP3_320(
        "高音质 MP3 320 kbps",
        "兼顾音质与带宽，适合移动网络聆听",
        "mp3",
        320,
    ),
    OPUS_192(
        "流畅 Opus 192 kbps",
        "高压缩效率，适合弱网或省流量场景",
        "opus",
        192,
    ),
    MP3_192(
        "省流量 MP3 192 kbps",
        "通用兼容转码档位，显著减少蜂窝流量消耗",
        "mp3",
        192,
    ),
}

enum class AudioFocusPolicy(val label: String, val subtitle: String) {
    STANDARD_DUCK(
        "标准模式 · 压低避让（推荐）",
        "播歌抢占其他应用；来电/语音暂停并自动恢复；导航/提示音临时压低 -12 dB"
    ),
    PAUSE_ON_DUCK(
        "纯净模式 · 暂停避让",
        "遇到提示音或导航播报时暂停播放而非降音量，结束后自动续播"
    ),
    IGNORE_DUCK(
        "沉浸模式 · 忽略短提示音",
        "忽略消息提示音不断流不降音量，仅来电或视频等强打断时礼让暂停"
    ),
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
    // 外观与沉浸音乐模式
    val coverStyle: CoverStyle = CoverStyle.VINYL,
    val darkCanvasStyle: DarkCanvasStyle = DarkCanvasStyle.SPOTIFY_CHARCOAL,
    val accentStyle: AccentStyle = AccentStyle.VINYL_GOLD,
    val ambientBackground: Boolean = true,
    val immersiveMusicMode: Boolean = true,
    val immersiveScope: ImmersiveScope = ImmersiveScope.PLAYER_AND_LOCKSCREEN,
    val immersiveHideStatusBar: Boolean = true,
    val immersiveHideNavBar: Boolean = true,
    val immersiveKeepScreenOn: Boolean = false,
    // 通知、锁屏与后台
    val lockScreenVinylEnabled: Boolean = true,
    val notifShowAudioSpec: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    // Subsonic 远程媒体服务器
    val subsonicWifiQuality: StreamQuality = StreamQuality.RAW,
    val subsonicCellularQuality: StreamQuality = StreamQuality.MP3_320,
    val subsonicScrobbleEnabled: Boolean = true,
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
        val COVER_STYLE = stringPreferencesKey("cover_style")
        val DARK_CANVAS = stringPreferencesKey("dark_canvas")
        val ACCENT_STYLE = stringPreferencesKey("accent_style")
        val AMBIENT_BG = booleanPreferencesKey("ambient_bg")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_music_mode")
        val IMMERSIVE_SCOPE = stringPreferencesKey("immersive_scope")
        val IMMERSIVE_HIDE_STATUS = booleanPreferencesKey("immersive_hide_status_bar")
        val IMMERSIVE_HIDE_NAV = booleanPreferencesKey("immersive_hide_nav_bar")
        val IMMERSIVE_KEEP_SCREEN_ON = booleanPreferencesKey("immersive_keep_screen_on")
        val LOCKSCREEN_VINYL = booleanPreferencesKey("lockscreen_vinyl")
        val NOTIF_SHOW_SPEC = booleanPreferencesKey("notif_show_spec")
        val WAKELOCK = booleanPreferencesKey("wakelock")
        val SUBSONIC_WIFI_QUALITY = stringPreferencesKey("subsonic_wifi_quality")
        val SUBSONIC_CELLULAR_QUALITY = stringPreferencesKey("subsonic_cellular_quality")
        val SUBSONIC_SCROBBLE = booleanPreferencesKey("subsonic_scrobble")
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

    suspend fun setLastLibrarySource(source: String) {
        context.dataStore.edit { it[Keys.LAST_LIBRARY_SOURCE] = source }
    }

    suspend fun saveLastPlayback(trackPath: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_PATH] = trackPath
            it[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }
}
