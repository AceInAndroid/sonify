package com.sonify.player.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.sonify.player.data.UserPreferences
import com.sonify.player.usb.UsbAudioManager
import com.sonify.player.usb.UsbVolumeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

enum class ActiveOutputRoute {
    NONE,
    USB_EXCLUSIVE,
    SYSTEM_AUDIOTRACK,
}

/**
 * 混合路由 AudioSink：
 * - 当 USB DAC 已连接且「USB 独占」开启时，路由至 UacAudioSink（bit-perfect 直通）；
 * - 否则自动回退至 Media3 DefaultAudioSink（本机扬声器 / 3.5mm / 蓝牙），确保日常无 DAC 也能正常听歌。
 */
@OptIn(UnstableApi::class)
class HybridAudioSink(
    context: Context,
    private val usbManager: UsbAudioManager,
    private val prefs: UserPreferences,
    volumeController: UsbVolumeController,
) : AudioSink {

    val uacSink = UacAudioSink(usbManager, prefs, volumeController)
    private val systemSink: DefaultAudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(true)
        .setEnableAudioTrackPlaybackParams(false)
        .build()

    @Volatile
    private var activeSink: AudioSink = systemSink

    private val _activeRoute = MutableStateFlow(ActiveOutputRoute.NONE)
    val activeRoute: StateFlow<ActiveOutputRoute> = _activeRoute.asStateFlow()

    private fun shouldUseUsb(): Boolean =
        prefs.current.usbExclusiveEnabled && usbManager.isConnected

    private fun targetSink(): AudioSink = if (shouldUseUsb()) uacSink else systemSink

    override fun setListener(listener: AudioSink.Listener) {
        uacSink.setListener(listener)
        systemSink.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        uacSink.setPlayerId(playerId)
        systemSink.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        uacSink.setClock(clock)
        systemSink.setClock(clock)
    }

    override fun supportsFormat(format: Format): Boolean =
        targetSink().supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        targetSink().getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        AudioOffloadSupport.DEFAULT_UNSUPPORTED

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        val desired = targetSink()
        if (desired !== activeSink) {
            runCatching { activeSink.reset() }
            activeSink = desired
        }
        val route = if (activeSink === uacSink) {
            ActiveOutputRoute.USB_EXCLUSIVE
        } else {
            ActiveOutputRoute.SYSTEM_AUDIOTRACK
        }
        _activeRoute.value = route
        Log.i(TAG, "路由选定: $route")
        activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        activeSink.getCurrentPositionUs(sourceEnded)

    override fun play() = activeSink.play()

    override fun pause() = activeSink.pause()

    override fun handleDiscontinuity() = activeSink.handleDiscontinuity()

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean = activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

    override fun playToEndOfStream() = activeSink.playToEndOfStream()

    override fun isEnded(): Boolean = activeSink.isEnded()

    override fun hasPendingData(): Boolean = activeSink.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        uacSink.setPlaybackParameters(playbackParameters)
        systemSink.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = activeSink.getPlaybackParameters()

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        uacSink.setSkipSilenceEnabled(skipSilenceEnabled)
        systemSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = activeSink.getSkipSilenceEnabled()

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        uacSink.setAudioAttributes(audioAttributes)
        systemSink.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = activeSink.getAudioAttributes()

    override fun setAudioSessionId(audioSessionId: Int) {
        uacSink.setAudioSessionId(audioSessionId)
        systemSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        uacSink.setAuxEffectInfo(auxEffectInfo)
        systemSink.setAuxEffectInfo(auxEffectInfo)
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        systemSink.setPreferredDevice(audioDeviceInfo)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        uacSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
        systemSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun enableTunnelingV21() = activeSink.enableTunnelingV21()

    override fun disableTunneling() = activeSink.disableTunneling()

    override fun setVolume(volume: Float) {
        uacSink.setVolume(volume)
        systemSink.setVolume(volume)
    }

    override fun flush() = activeSink.flush()

    override fun reset() {
        uacSink.reset()
        systemSink.reset()
        _activeRoute.value = ActiveOutputRoute.NONE
    }

    override fun release() {
        uacSink.release()
        systemSink.release()
        _activeRoute.value = ActiveOutputRoute.NONE
    }

    companion object {
        private const val TAG = "HiFiHybridSink"
    }
}
