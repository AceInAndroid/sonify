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
    val gaplessEnabled: Boolean = true,
    val fadeEnabled: Boolean = true,
    val pauseOnUnplug: Boolean = true,
    val rememberPosition: Boolean = true,
    // 资料库
    val scanDirs: List<String> = defaultScanDirs(),
    val importMode: ImportMode = ImportMode.REFERENCE,
    val minDurationFilter: MinDurationFilter = MinDurationFilter.SEC_15,
    // 外观
    val coverStyle: CoverStyle = CoverStyle.VINYL,
    val darkCanvasStyle: DarkCanvasStyle = DarkCanvasStyle.SPOTIFY_CHARCOAL,
    val accentStyle: AccentStyle = AccentStyle.VINYL_GOLD,
    val ambientBackground: Boolean = true,
    // 系统与断点
    val wakeLockEnabled: Boolean = true,
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
        val WAKELOCK = booleanPreferencesKey("wakelock")
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
                wakeLockEnabled = p[Keys.WAKELOCK] ?: true,
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

    suspend fun setWakeLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKELOCK] = enabled }
    }

    suspend fun saveLastPlayback(trackPath: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_PATH] = trackPath
            it[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }
}
