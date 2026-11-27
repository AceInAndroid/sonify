package com.sonify.player.usb

import android.util.Log
import androidx.annotation.StringRes
import com.sonify.player.R
import com.sonify.player.data.UserPreferences
import com.sonify.player.data.VolumeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

enum class EffectiveVolumeEngine(@StringRes val shortLabelRes: Int) {
    HARDWARE_UAC(R.string.pref_vol_engine_hw),
    SOFTWARE_LOG(R.string.pref_vol_engine_sw),
    FIXED_DIRECT(R.string.pref_vol_engine_direct),
}

data class VolumeHudState(
    val volumePercent: Int = 25,
    val effectiveDb: Double = -45.0,
    val engine: EffectiveVolumeEngine = EffectiveVolumeEngine.SOFTWARE_LOG,
    val maxLimitPercent: Int = 60,
    val protectionEnabled: Boolean = true,
    val hitLimit: Boolean = false,
    val deviceName: String = "",
    val hasHardwareVolume: Boolean = false,
    val hudTick: Long = 0L,
)

/**
 * 纯数学对数音量与耳朵保护钳位函数（无 Android 依赖，100% 单元测试覆盖）。
 */
object VolumeMath {
    const val DEFAULT_MIN_DB = -60.0
    const val DEFAULT_MAX_DB = 0.0

    /**
     * 对数 dB 映射：人耳响度呈对数感知，0..100% 线性映射到 [minDb, maxDb]：
     *   25% -> -45 dB（轻柔安全起步）
     *   60% -> -24 dB（默认护耳上限，对应实测舒适听音区）
     *   100% -> 0 dB
     */
    fun percentToDb(
        percent: Int,
        minDb: Double = DEFAULT_MIN_DB,
        maxDb: Double = DEFAULT_MAX_DB,
    ): Double {
        val lo = min(minDb, maxDb)
        val hi = max(minDb, maxDb)
        val p = percent.coerceIn(0, 100)
        return lo + (hi - lo) * (p / 100.0)
    }

    /** 护耳上限硬钳位，返回 (实际允许百分比, 是否触顶被拦截) */
    fun clampToProtection(
        requestedPercent: Int,
        protectionEnabled: Boolean,
        maxLimitPercent: Int,
    ): Pair<Int, Boolean> {
        val req = requestedPercent.coerceIn(0, 100)
        if (!protectionEnabled) return req to false
        val limit = maxLimitPercent.coerceIn(20, 100)
        return if (req > limit) limit to true else req to false
    }

    /** 计算 USB DAC 新接入 / 重插时的安全初始音量 */
    fun computeSafeStartupPercent(
        savedPercent: Int?,
        safeResetOnPlug: Boolean,
        safeStartupPercent: Int,
        protectionEnabled: Boolean,
        maxLimitPercent: Int,
    ): Int {
        val startup = safeStartupPercent.coerceIn(10, 40)
        val resetThreshold = max(startup + 10, 35)
        val base = when {
            savedPercent == null -> startup
            safeResetOnPlug && savedPercent > resetThreshold -> startup
            else -> savedPercent
        }
        return clampToProtection(base, protectionEnabled, maxLimitPercent).first
    }
}

/**
 * USB 独占音量与耳朵安全保护中枢：
 * - 统一处理前台物理键、后台 MediaSession 远程音量、播放页音量条与设备重插安全压低
 * - 硬件优先（UAC Feature Unit，保持 PCM 100% Bit-Perfect），无硬件能力时自动切对数软件音量
 * - 无论何种路径全部受 maxVolumeLimitPercent 硬钳位 + C++ 底层二次钳位
 */
class UsbVolumeController(
    private val prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connected = false

    @Volatile
    private var currentDeviceKey = ""

    @Volatile
    private var currentDeviceName = ""

    @Volatile
    private var hasHwVol = false

    @Volatile
    private var hwMinDb = VolumeMath.DEFAULT_MIN_DB

    @Volatile
    private var hwMaxDb = VolumeMath.DEFAULT_MAX_DB

    @Volatile
    private var lastNonZeroPercent = 25

    /** 音频焦点临时避让（Ducking）衰减 dB（正常为 0.0，Duck 时为 -12.0，不污染持久化记忆音量） */
    @Volatile
    private var duckAttenuationDb = 0.0

    private val _state = MutableStateFlow(VolumeHudState())
    val state: StateFlow<VolumeHudState> = _state.asStateFlow()

    @Volatile
    var onVolumeChangedCallback: ((Int) -> Unit)? = null

    /**
     * ★ 在 UsbAudioManager.open() 成功后、音频流启动之前第一时间调用，
     * 立即把 DAC 硬件音量 / 软件增益压到安全起步值，彻底消除起播炸耳！
     */
    fun onUsbDeviceOpened(
        deviceKey: String,
        deviceName: String,
        hwVolSupported: Boolean,
        hwMin: Double,
        hwMax: Double,
    ) {
        connected = true
        currentDeviceKey = deviceKey
        currentDeviceName = deviceName
        hasHwVol = hwVolSupported
        // 安全收窄硬件区间：下限至少到 -60dB，上限严禁超过 0dB（防止正增益爆音）
        hwMinDb = if (hwVolSupported) max(hwMin, -80.0).coerceAtMost(-24.0) else VolumeMath.DEFAULT_MIN_DB
        hwMaxDb = if (hwVolSupported) min(hwMax, 0.0) else VolumeMath.DEFAULT_MAX_DB

        val s = prefs.current
        val saved = runCatching {
            runBlocking(Dispatchers.IO) { prefs.getSavedDeviceVolume(deviceKey) }
        }.getOrNull()

        val initial = VolumeMath.computeSafeStartupPercent(
            savedPercent = saved,
            safeResetOnPlug = s.safeResetOnPlugEnabled,
            safeStartupPercent = s.safeStartupPercent,
            protectionEnabled = s.hearingProtectionEnabled,
            maxLimitPercent = s.maxVolumeLimitPercent,
        )
        if (initial > 0) lastNonZeroPercent = initial

        Log.i(
            TAG,
            "DAC 接入安全音量初始化: dev=$deviceName ($deviceKey), hasHwVol=$hasHwVol " +
                "[${"%.1f".format(hwMinDb)}..${"%.1f".format(hwMaxDb)} dB], " +
                "saved=$saved → init=$initial% (limit=${s.maxVolumeLimitPercent}%)"
        )
        applyPercentInternal(initial, showHud = false, persist = true)
    }

    fun onUsbDeviceClosed() {
        connected = false
        currentDeviceKey = ""
        currentDeviceName = ""
        hasHwVol = false
        _state.update { it.copy(hasHardwareVolume = false, deviceName = "") }
    }

    /** 每次流 configure 或设置变化时同步强制应用当前安全音量 */
    fun applyCurrentVolume(showHud: Boolean = false) {
        applyPercentInternal(_state.value.volumePercent, showHud = showHud, persist = false)
    }

    fun setVolumePercent(requestedPercent: Int, showHud: Boolean = false) {
        applyPercentInternal(requestedPercent, showHud = showHud, persist = true)
    }

    fun stepUp(showHud: Boolean = true) {
        val step = prefs.current.volumeStepPercent.coerceIn(1, 10)
        applyPercentInternal(_state.value.volumePercent + step, showHud = showHud, persist = true)
    }

    fun stepDown(showHud: Boolean = true) {
        val step = prefs.current.volumeStepPercent.coerceIn(1, 10)
        applyPercentInternal(_state.value.volumePercent - step, showHud = showHud, persist = true)
    }

    fun setMuted(muted: Boolean, showHud: Boolean = true) {
        val target = if (muted) 0 else lastNonZeroPercent.coerceAtLeast(10)
        applyPercentInternal(target, showHud = showHud, persist = true)
    }

    /** 用户下调耳朵保护上限或切换开关时，立即实时钳位当前音量 */
    fun onProtectionPolicyChanged() {
        applyPercentInternal(_state.value.volumePercent, showHud = false, persist = true)
    }

    /** 音频焦点 Ducking 临时衰减（不改写用户记忆百分比，恢复焦点后瞬间归零还原本色） */
    fun setDuckAttenuationDb(duckDb: Double) {
        val clampedDuck = duckDb.coerceIn(-24.0, 0.0)
        if (duckAttenuationDb == clampedDuck) return
        duckAttenuationDb = clampedDuck
        applyPercentInternal(_state.value.volumePercent, showHud = false, persist = false)
    }

    @Synchronized
    private fun applyPercentInternal(requested: Int, showHud: Boolean, persist: Boolean) {
        val s = prefs.current
        val (clamped, hitLimit) = VolumeMath.clampToProtection(
            requestedPercent = requested,
            protectionEnabled = s.hearingProtectionEnabled,
            maxLimitPercent = s.maxVolumeLimitPercent,
        )
        if (clamped > 0) lastNonZeroPercent = clamped

        val effectiveLimit = if (s.hearingProtectionEnabled) s.maxVolumeLimitPercent.coerceIn(20, 100) else 100
        val isMute = clamped <= 0

        val duckDb = duckAttenuationDb
        val engine: EffectiveVolumeEngine
        val baseSwDb = VolumeMath.percentToDb(clamped, VolumeMath.DEFAULT_MIN_DB, VolumeMath.DEFAULT_MAX_DB)
        var appliedDb = (baseSwDb + duckDb).coerceAtLeast(-96.0)

        if (s.volumeMode == VolumeMode.FIXED_DIRECT) {
            engine = EffectiveVolumeEngine.FIXED_DIRECT
            appliedDb = duckDb
            if (connected) {
                if (hasHwVol) UsbAudioNative.nativeSetHardwareVolumeDb(0.0f, false)
                // 固定直通模式下，正常态 0dB 直通；Duck 态临时温和衰减，结束立刻还原 0dB Bit-Perfect
                UsbAudioNative.nativeSetSoftwareVolumeDb(duckDb.toFloat(), false, 0.0f)
            }
        } else if (s.volumeMode == VolumeMode.AUTO_HARDWARE && hasHwVol && connected) {
            val hwDb = (VolumeMath.percentToDb(clamped, hwMinDb, hwMaxDb) + duckDb).coerceAtLeast(hwMinDb)
            val hwOk = UsbAudioNative.nativeSetHardwareVolumeDb(hwDb.toFloat(), isMute)
            if (hwOk) {
                engine = EffectiveVolumeEngine.HARDWARE_UAC
                appliedDb = hwDb
                // 硬件音量生效时：非静音下软件保持 0dB 直通（Bit-Perfect），0% 时双保险软件静音
                UsbAudioNative.nativeSetSoftwareVolumeDb(0.0f, isMute, 0.0f)
            } else {
                // 硬件 SET_CUR 失败时自动回退软件对数音量，确保护耳绝不失效
                val maxDb = VolumeMath.percentToDb(effectiveLimit, VolumeMath.DEFAULT_MIN_DB, VolumeMath.DEFAULT_MAX_DB)
                engine = EffectiveVolumeEngine.SOFTWARE_LOG
                UsbAudioNative.nativeSetSoftwareVolumeDb(appliedDb.toFloat(), isMute, maxDb.toFloat())
            }
        } else {
            // 软件对数模式（或无硬件音量 DAC）
            val maxDb = VolumeMath.percentToDb(effectiveLimit, VolumeMath.DEFAULT_MIN_DB, VolumeMath.DEFAULT_MAX_DB)
            engine = EffectiveVolumeEngine.SOFTWARE_LOG
            if (connected) {
                if (hasHwVol) UsbAudioNative.nativeSetHardwareVolumeDb(0.0f, false)
                UsbAudioNative.nativeSetSoftwareVolumeDb(appliedDb.toFloat(), isMute, maxDb.toFloat())
            }
        }

        val nowTick = if (showHud) System.currentTimeMillis() else _state.value.hudTick
        _state.value = VolumeHudState(
            volumePercent = if (engine == EffectiveVolumeEngine.FIXED_DIRECT) 100 else clamped,
            effectiveDb = if (isMute && engine != EffectiveVolumeEngine.FIXED_DIRECT) -96.0 else appliedDb,
            engine = engine,
            maxLimitPercent = effectiveLimit,
            protectionEnabled = s.hearingProtectionEnabled,
            hitLimit = hitLimit,
            deviceName = currentDeviceName,
            hasHardwareVolume = hasHwVol,
            hudTick = nowTick,
        )

        onVolumeChangedCallback?.invoke(_state.value.volumePercent)

        if (persist && engine != EffectiveVolumeEngine.FIXED_DIRECT) {
            val devKey = currentDeviceKey
            scope.launch { prefs.saveDeviceVolume(devKey, clamped) }
        }
    }

    companion object {
        private const val TAG = "HiFiVolume"
    }
}
