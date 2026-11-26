package com.sonify.player.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 自定义 RenderersFactory：
 * 1. 注入 HybridAudioSink（USB 独占 / 本机回退）；
 * 2. 在 Android 10+ 上显式为 FLAC 解码器配置 KEY_PCM_ENCODING = ENCODING_PCM_FLOAT，
 *    避免 MediaCodec 默认将 24-bit FLAC 截断为 16-bit PCM（24-bit 整数在 float32 尾数内 100% 比特精确）。
 */
@OptIn(UnstableApi::class)
class HiFiRenderersFactory(
    context: Context,
    private val hybridSink: HybridAudioSink,
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = hybridSink

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val renderer = HiFiMediaCodecAudioRenderer(
            context = context,
            codecAdapterFactory = codecAdapterFactory,
            mediaCodecSelector = mediaCodecSelector,
            enableDecoderFallback = enableDecoderFallback,
            eventHandler = eventHandler,
            eventListener = eventListener,
            audioSink = audioSink,
        )
        out.add(renderer)
    }
}

@OptIn(UnstableApi::class)
private class HiFiMediaCodecAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink,
) {
    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float,
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxInputSize,
            codecOperatingRate,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            format.sampleMimeType == MimeTypes.AUDIO_FLAC
        ) {
            // 请求 float32 PCM 输出以完整保留 24-bit FLAC 动态范围（防止系统默认降成 16-bit）
            mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            Log.i(TAG, "FLAC 解码显式请求 KEY_PCM_ENCODING=ENCODING_PCM_FLOAT")
        }
        return mediaFormat
    }

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        val actualEnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            mediaFormat?.containsKey(MediaFormat.KEY_PCM_ENCODING) == true
        ) {
            mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else -1
        Log.i(
            TAG,
            "解码器实际输出: mime=${format.sampleMimeType}, sr=${format.sampleRate}, " +
                "ch=${format.channelCount}, mediaFormatPcmEnc=$actualEnc"
        )
        super.onOutputFormatChanged(format, mediaFormat)
    }

    companion object {
        private const val TAG = "HiFiCodecRenderer"
    }
}
