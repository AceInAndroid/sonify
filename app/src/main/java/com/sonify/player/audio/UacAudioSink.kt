package com.sonify.player.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.sonify.player.data.UserPreferences
import com.sonify.player.usb.UsbAudioManager
import com.sonify.player.usb.UsbAudioNative
import com.sonify.player.usb.UsbVolumeController
import java.nio.ByteBuffer

/**
 * 自研 UAC bit-perfect AudioSink：
 * 把 Media3 解码输出的原始 PCM（16/24/32-bit/Float）零拷贝写入 C++ PcmRingBufferSource，
 * 再由 UacStream 通过 libusb isochronous OUT 直送 USB DAC。
 */
@OptIn(UnstableApi::class)
class UacAudioSink(
    private val usbManager: UsbAudioManager,
    private val prefs: UserPreferences,
    private val volumeController: UsbVolumeController,
) : AudioSink {

    private var listener: AudioSink.Listener? = null
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var audioAttributes = AudioAttributes.DEFAULT
    private var skipSilence = false
    private var playerVolume = 1.0f

    // 输入（解码器输出）格式
    private var inputSampleRate = 0
    private var inputChannels = 0
    private var inputPcmEncoding = C.ENCODING_INVALID
    private var nativeEncoding = 0

    // 当前正在 USB 侧运行的协商参数（用于判定 Gapless 快速路径）
    private var activeRate = 0
    private var activeChannels = 0
    private var activeSubslotPref = -1
    private var streamStarted = false
    private var playing = false
    private var eosSignaled = false

    // 播放时钟锚点
    private var startMediaTimeUs = C.TIME_UNSET
    private var lastReportedUnderruns = 0L

    // 复用 DirectByteBuffer（仅当遇到非 Direct ByteBuffer 时中转，避免频繁分配）
    private var scratchDirectBuf: ByteBuffer = ByteBuffer.allocateDirect(65536)
    private val pcmStateBuf = LongArray(6)

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        if (!usbManager.isConnected) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return AudioSink.SINK_FORMAT_UNSUPPORTED
        val enc = mapPcmEncoding(format.pcmEncoding)
        if (enc == 0) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.sampleRate !in 8000..384000) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.channelCount !in 1..8) return AudioSink.SINK_FORMAT_UNSUPPORTED
        return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        val enc = mapPcmEncoding(inputFormat.pcmEncoding)
        if (enc == 0) {
            throw AudioSink.ConfigurationException(
                "不支持的 PCM 编码: ${inputFormat.pcmEncoding}",
                inputFormat
            )
        }

        val settings = prefs.current
        val targetRate = if (settings.sampleRatePolicy.fixedHz > 0) {
            settings.sampleRatePolicy.fixedHz
        } else {
            inputFormat.sampleRate
        }
        val targetChannels = if (inputFormat.channelCount >= 2) 2 else 1
        val subslotPref = settings.bitDepthPref.subslotBytes

        inputSampleRate = inputFormat.sampleRate
        inputChannels = inputFormat.channelCount
        inputPcmEncoding = inputFormat.pcmEncoding
        nativeEncoding = enc

        Log.i(
            TAG,
            "configure: 输入=${inputFormat.sampleRate}Hz/${inputFormat.channelCount}ch/" +
                "enc=${encodingName(inputFormat.pcmEncoding)} → 目标=${targetRate}Hz, subslotPref=$subslotPref"
        )

        // ★ Gapless 快速路径：同采样率、同声道数、同位深偏好，且流正在跑 → 直接续流，不重切时钟！
        val canGaplessReuse = settings.gaplessEnabled &&
            streamStarted &&
            activeRate == targetRate &&
            activeChannels == targetChannels &&
            activeSubslotPref == subslotPref &&
            isNativeRunning()

        if (canGaplessReuse) {
            Log.i(TAG, "Gapless 快速路径命中：保持 ${activeRate}Hz USB 流不断开直接续流")
            eosSignaled = false
            return
        }

        // 跨采样率或首次启动：若上一首即将结束，短等缓冲 drain（最多 250ms）
        if (streamStarted && eosSignaled) {
            waitForDrain(250)
        }

        val ok = UsbAudioNative.nativeStartPcmStream(
            sampleRate = targetRate,
            channels = targetChannels,
            preferredSubslot = subslotPref,
            bufferMs = settings.bufferSizePref.ms,
            fadeEnabled = settings.fadeEnabled,
            feedbackEnabled = settings.feedbackControlEnabled,
            resetAlt = settings.resetAltOnRateChange,
        )
        if (!ok) {
            streamStarted = false
            throw AudioSink.ConfigurationException(
                "启动 USB UAC 流失败（rate=$targetRate）",
                inputFormat
            )
        }

        val stats = usbManager.refreshStats()
        activeRate = if (stats.rate > 0) stats.rate else targetRate
        activeChannels = targetChannels
        activeSubslotPref = subslotPref
        streamStarted = true
        eosSignaled = false
        startMediaTimeUs = C.TIME_UNSET
        lastReportedUnderruns = 0L

        applyEffectiveVolume()
        UsbAudioNative.nativeSetPaused(!playing)

        Log.i(
            TAG,
            "UAC 流已就绪: 实际协商=${stats.rate}Hz/${stats.bits}bit(subslot=${stats.subslot}B)/${stats.channels}ch"
        )
    }

    override fun play() {
        playing = true
        if (streamStarted) {
            UsbAudioNative.nativeSetPaused(false)
        }
    }

    override fun pause() {
        playing = false
        if (streamStarted) {
            UsbAudioNative.nativeSetPaused(true)
        }
    }

    override fun handleDiscontinuity() {
        // 标记非连续点；在 ExoPlayer 内部 seek 后会紧跟 flush()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!streamStarted || !usbManager.isConnected) {
            throw AudioSink.WriteException(
                -1,
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setSampleRate(inputSampleRate)
                    .setChannelCount(inputChannels)
                    .setPcmEncoding(inputPcmEncoding)
                    .build(),
                /* isRecoverable= */ false
            )
        }

        val remaining = buffer.remaining()
        if (remaining == 0) return true

        if (startMediaTimeUs == C.TIME_UNSET) {
            startMediaTimeUs = presentationTimeUs
        }

        // 零拷贝直接透传 DirectByteBuffer；若为 heap buffer 则复用到 scratchDirectBuf
        val written = if (buffer.isDirect) {
            UsbAudioNative.nativeWritePcm(
                directBuffer = buffer,
                offset = buffer.position(),
                length = remaining,
                pcmEncoding = nativeEncoding,
                srcChannels = inputChannels,
            )
        } else {
            if (scratchDirectBuf.capacity() < remaining) {
                scratchDirectBuf = ByteBuffer.allocateDirect(remaining.coerceAtLeast(65536))
            }
            scratchDirectBuf.clear()
            val savedPos = buffer.position()
            scratchDirectBuf.put(buffer)
            buffer.position(savedPos)
            UsbAudioNative.nativeWritePcm(
                directBuffer = scratchDirectBuf,
                offset = 0,
                length = remaining,
                pcmEncoding = nativeEncoding,
                srcChannels = inputChannels,
            )
        }

        if (written > 0) {
            buffer.position(buffer.position() + written)
        }

        // 检查是否发生环形缓冲欠载并通知监听器
        if (UsbAudioNative.nativeGetPcmState(pcmStateBuf)) {
            val underruns = pcmStateBuf[2]
            if (underruns > lastReportedUnderruns) {
                lastReportedUnderruns = underruns
                listener?.onUnderrun(0, 0L, 0L)
            }
            if (pcmStateBuf[4] == 0L) {
                // USB 底层流异常退出（例如设备掉线）
                streamStarted = false
                throw AudioSink.WriteException(
                    -2,
                    Format.Builder()
                        .setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setSampleRate(inputSampleRate)
                        .setChannelCount(inputChannels)
                        .setPcmEncoding(inputPcmEncoding)
                        .build(),
                    false
                )
            }
        }

        // 完全消耗返回 true，否则返回 false 通知 ExoPlayer 触发背压稍后重试
        return !buffer.hasRemaining()
    }

    override fun playToEndOfStream() {
        eosSignaled = true
        if (streamStarted) {
            UsbAudioNative.nativeSetEos()
        }
    }

    override fun isEnded(): Boolean {
        if (!streamStarted) return true
        if (!eosSignaled) return false
        return if (UsbAudioNative.nativeGetPcmState(pcmStateBuf)) {
            pcmStateBuf[5] == 1L || pcmStateBuf[1] == 0L
        } else true
    }

    override fun hasPendingData(): Boolean {
        if (!streamStarted) return false
        return UsbAudioNative.nativeGetPcmState(pcmStateBuf) && pcmStateBuf[1] > 0L
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!streamStarted || startMediaTimeUs == C.TIME_UNSET || activeRate <= 0) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        if (!UsbAudioNative.nativeGetPcmState(pcmStateBuf)) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val consumedFrames = pcmStateBuf[0]
        val inFlightFrames = pcmStateBuf[3]
        // 扣除 USB URB 管线延迟（约 16ms），让进度条与实际出声时间对齐
        val audibleFrames = (consumedFrames - inFlightFrames).coerceAtLeast(0L)
        val elapsedUs = (audibleFrames * 1_000_000L) / activeRate.toLong()
        return startMediaTimeUs + elapsedUs
    }

    override fun flush() {
        if (streamStarted) {
            UsbAudioNative.nativeFlushPcm()
        }
        startMediaTimeUs = C.TIME_UNSET
        eosSignaled = false
    }

    override fun reset() {
        playing = false
        streamStarted = false
        activeRate = 0
        activeChannels = 0
        activeSubslotPref = -1
        startMediaTimeUs = C.TIME_UNSET
        eosSignaled = false
        UsbAudioNative.nativeStop()
        usbManager.refreshStats()
    }

    override fun release() {
        reset()
    }

    override fun setVolume(volume: Float) {
        playerVolume = volume.coerceIn(0f, 1f)
        // ExoPlayer 音频焦点压低(duck) 时可同步，但日常电平由 UsbVolumeController 统一护耳管辖
        applyEffectiveVolume()
    }

    fun applyEffectiveVolume() {
        volumeController.applyCurrentVolume(showHud = false)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        // HiFi bit-perfect 模式固定 1.0x 速，防止重采样
        this.playbackParameters = PlaybackParameters.DEFAULT
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        this.skipSilence = skipSilenceEnabled
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilence

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {}

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}

    override fun enableTunnelingV21() {}

    override fun disableTunneling() {}

    private fun isNativeRunning(): Boolean =
        UsbAudioNative.nativeGetPcmState(pcmStateBuf) && pcmStateBuf[4] == 1L

    private fun waitForDrain(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!UsbAudioNative.nativeGetPcmState(pcmStateBuf)) break
            if (pcmStateBuf[1] == 0L || pcmStateBuf[4] == 0L) break
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    companion object {
        private const val TAG = "HiFiAudioSink"

        fun mapPcmEncoding(exoEncoding: Int): Int = when (exoEncoding) {
            C.ENCODING_PCM_16BIT -> UsbAudioNative.PCM_ENC_16BIT
            C.ENCODING_PCM_24BIT -> UsbAudioNative.PCM_ENC_24BIT
            C.ENCODING_PCM_32BIT -> UsbAudioNative.PCM_ENC_32BIT
            C.ENCODING_PCM_FLOAT -> UsbAudioNative.PCM_ENC_FLOAT
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> UsbAudioNative.PCM_ENC_24BIT_BE
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> UsbAudioNative.PCM_ENC_32BIT_BE
            else -> 0
        }

        fun encodingName(exoEncoding: Int): String = when (exoEncoding) {
            C.ENCODING_PCM_16BIT -> "PCM_16BIT"
            C.ENCODING_PCM_24BIT -> "PCM_24BIT"
            C.ENCODING_PCM_32BIT -> "PCM_32BIT"
            C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "PCM_24BIT_BE"
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "PCM_32BIT_BE"
            else -> "ENC($exoEncoding)"
        }
    }
}
