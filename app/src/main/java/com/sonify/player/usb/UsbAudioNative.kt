package com.sonify.player.usb

import org.json.JSONObject
import java.nio.ByteBuffer

data class UacStats(
    val running: Boolean = false,
    val mode: String = "idle",
    val underruns: Long = 0L,
    val ringUnderruns: Long = 0L,
    val bufferFillMs: Double = 0.0,
    val rate: Int = 0,
    val effectiveRate: Double = 0.0,
    val bits: Int = 0,
    val subslot: Int = 0,
    val channels: Int = 0,
    val ep: Int = -1,
    val feedbackEp: Int = -1,
    val syncType: Int = 0,
    val fbRaw: Long = 0L,
    val fbRate: Double = 0.0,
    val hasHwVol: Boolean = false,
    val hwVolDb: Double = 0.0,
    val swVolDb: Double = 0.0,
    val buildTime: String = "",
    val rawJson: String = "",
)

/**
 * JNI 桥：对应 cpp/jni_bridge.cpp。
 *
 * 生命周期约定：
 *   nativeOpen(fd) 之后必须保留 UsbDeviceConnection 引用，直到 nativeClose()。
 */
object UsbAudioNative {

    // 与 cpp/pcm_ring_buffer.h 中的 PcmEncoding 枚举严格对应
    const val PCM_ENC_16BIT = 1
    const val PCM_ENC_24BIT = 2
    const val PCM_ENC_32BIT = 3
    const val PCM_ENC_FLOAT = 4
    const val PCM_ENC_24BIT_BE = 5
    const val PCM_ENC_32BIT_BE = 6

    init {
        System.loadLibrary("hifiaudio")
    }

    @JvmStatic
    external fun nativeOpen(fd: Int): String

    @JvmStatic
    external fun nativeSupportedRates(): String

    @JvmStatic
    external fun nativeSetRate(rate: Int): Boolean

    @JvmStatic
    external fun nativePlayTone(
        freq: Double,
        seconds: Int,
        sampleRate: Int,
        amplitude: Double,
    ): Boolean

    // ---- M2 PCM 流接口 ----

    @JvmStatic
    external fun nativeStartPcmStream(
        sampleRate: Int,
        channels: Int,
        preferredSubslot: Int,
        bufferMs: Int,
        fadeEnabled: Boolean,
        feedbackEnabled: Boolean,
        resetAlt: Boolean,
    ): Boolean

    @JvmStatic
    external fun nativeWritePcm(
        directBuffer: ByteBuffer,
        offset: Int,
        length: Int,
        pcmEncoding: Int,
        srcChannels: Int,
    ): Int

    @JvmStatic
    external fun nativeFlushPcm()

    @JvmStatic
    external fun nativeSetPaused(paused: Boolean)

    @JvmStatic
    external fun nativeSetVolume(volume: Float)

    @JvmStatic
    external fun nativeHasHardwareVolume(): Boolean

    /** outDb 长度至少 3：[minDb, maxDb, curDb] */
    @JvmStatic
    external fun nativeGetHardwareVolumeRange(outDb: FloatArray): Boolean

    @JvmStatic
    external fun nativeSetHardwareVolumeDb(db: Float, mute: Boolean): Boolean

    @JvmStatic
    external fun nativeSetSoftwareVolumeDb(db: Float, mute: Boolean, maxDb: Float)

    @JvmStatic
    external fun nativeSetEos()

    /**
     * outArr 至少长度 6：
     *   [0] consumedFrames, [1] bufferedFrames, [2] ringUnderruns,
     *   [3] inFlightFrames, [4] isRunning(0/1), [5] isDrained(0/1)
     */
    @JvmStatic
    external fun nativeGetPcmState(outArr: LongArray): Boolean

    @JvmStatic
    external fun nativeStop()

    @JvmStatic
    external fun nativeClose()

    @JvmStatic
    external fun nativeStats(): String

    fun parseStats(json: String): UacStats = try {
        val o = JSONObject(json)
        UacStats(
            running = o.optBoolean("running", false),
            mode = o.optString("mode", "idle"),
            underruns = o.optLong("underruns", 0L),
            ringUnderruns = o.optLong("ringUnderruns", 0L),
            bufferFillMs = o.optDouble("bufferFillMs", 0.0),
            rate = o.optInt("rate", 0),
            effectiveRate = o.optDouble("effectiveRate", 0.0),
            bits = o.optInt("bits", 0),
            subslot = o.optInt("subslot", 0),
            channels = o.optInt("channels", 0),
            ep = o.optInt("ep", -1),
            feedbackEp = o.optInt("feedbackEp", -1),
            syncType = o.optInt("syncType", 0),
            fbRaw = o.optLong("fbRaw", 0L),
            fbRate = o.optDouble("fbRate", 0.0),
            hasHwVol = o.optBoolean("hasHwVol", false),
            hwVolDb = o.optDouble("hwVolDb", 0.0),
            swVolDb = o.optDouble("swVolDb", 0.0),
            buildTime = o.optString("buildTime", ""),
            rawJson = json,
        )
    } catch (_: Throwable) {
        UacStats(rawJson = json)
    }
}
