package com.sonify.player.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonify.player.HiFiApp
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.audio.HiFiRenderersFactory
import com.sonify.player.audio.HybridAudioSink
import com.sonify.player.data.PlaybackPositionEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.VolumeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 前台后台播放核心服务（MediaSessionService）：
 * - 持有自定义 RenderersFactory + HybridAudioSink 的 ExoPlayer
 * - 监听耳机拔出（ACTION_AUDIO_BECOMING_NOISY）与 USB DAC 拔出，自动暂停
 * - 播放时每 5 秒 + 暂停/切歌时保存断点记忆
 * - 仅在播放时按设置持 PARTIAL_WAKE_LOCK，暂停立即释放
 * - 支持运行时无缝重绑输出路由（USB 独占 <-> 本机输出 / 修改采样率与位深立即生效）
 */
@OptIn(UnstableApi::class)
class HiFiPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var hybridSink: HybridAudioSink
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: UsbVolumeForwardingPlayer
    private var mediaSession: MediaSession? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var positionSaveJob: Job? = null
    private var statsPollJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val app = HiFiApp.from(this@HiFiPlaybackService)
                if (app.prefs.current.pauseOnUnplug && player.isPlaying) {
                    Log.i(TAG, "收到 AUDIO_BECOMING_NOISY（耳机/蓝牙断开），自动暂停")
                    player.pause()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = HiFiApp.from(this)

        hybridSink = HybridAudioSink(this, app.usbManager, app.prefs, app.volumeController)
        val renderersFactory = HiFiRenderersFactory(this, hybridSink)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttrs, /* handleAudioFocus= */ true)
            .build()

        player.addListener(playerListener)

        // ★ 用远程音量代理包装 ExoPlayer：后台/息屏按手机物理音量键时自动接管为 USB 独占护耳音量
        sessionPlayer = UsbVolumeForwardingPlayer(player, app)

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setCallback(sessionCallback)
            .build()

        app.volumeController.onVolumeChangedCallback = { vol ->
            serviceScope.launch {
                sessionPlayer.notifyVolumeChanged(vol)
            }
        }

        // 暴露活跃路由流给 UI
        serviceScope.launch {
            hybridSink.activeRoute.collect { route ->
                _activeRouteFlow.value = route
                sessionPlayer.notifyDeviceInfoChanged()
            }
        }

        // 监听 USB DAC 拔出：立即暂停并切换状态
        serviceScope.launch {
            app.usbManager.detachedEvents.collect {
                if (app.prefs.current.pauseOnUnplug && player.isPlaying) {
                    Log.i(TAG, "USB DAC 拔出，自动暂停")
                    player.pause()
                }
                if (_activeRouteFlow.value == ActiveOutputRoute.USB_EXCLUSIVE) {
                    rebindOutputRoute(autoResume = false)
                }
            }
        }

        // 监听 USB DAC 新连接：若开启 USB 独占，当前未播放或正在播则自动切到 USB DAC
        serviceScope.launch {
            app.usbManager.connectedEvents.collect {
                if (app.prefs.current.usbExclusiveEnabled && player.mediaItemCount > 0) {
                    rebindOutputRoute(autoResume = player.playWhenReady)
                }
            }
        }

        // 监听音量模式 / 护耳保护策略变化实时同步到 USB 音量中枢
        serviceScope.launch {
            app.prefs.settings.collect {
                app.volumeController.onProtectionPolicyChanged()
                sessionPlayer.notifyDeviceInfoChanged()
            }
        }

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sonify::PlaybackWakeLock")
            .apply { setReferenceCounted(false) }

        instance = this
        Log.i(TAG, "HiFiPlaybackService 已启动")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPositionImmediate()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        HiFiApp.from(this).volumeController.onVolumeChangedCallback = null
        saveCurrentPositionImmediate()
        releaseWakeLock()
        positionSaveJob?.cancel()
        statsPollJob?.cancel()
        runCatching { unregisterReceiver(noisyReceiver) }
        mediaSession?.release()
        mediaSession = null
        player.removeListener(playerListener)
        player.release()
        serviceScope.cancel()
        Log.i(TAG, "HiFiPlaybackService 已销毁")
        super.onDestroy()
    }

    /** 运行时无感重建音频路由（切换 USB 独占 / 本机输出 / 改采样率与位深策略时立即生效） */
    fun rebindOutputRoute(autoResume: Boolean = player.playWhenReady) {
        if (player.mediaItemCount == 0) return
        val index = player.currentMediaItemIndex
        val posMs = player.currentPosition.coerceAtLeast(0L)
        Log.i(TAG, "重建音频输出路由: index=$index, pos=${posMs}ms, autoResume=$autoResume")
        player.stop()
        hybridSink.reset()
        player.seekTo(index, posMs)
        player.prepare()
        player.playWhenReady = autoResume
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val cmds = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_REBIND_ROUTE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(cmds, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_REBIND_ROUTE) {
                rebindOutputRoute()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val app = HiFiApp.from(this@HiFiPlaybackService)
            if (isPlaying) {
                if (app.prefs.current.wakeLockEnabled) acquireWakeLock()
                startPositionSaver()
                startStatsPoller()
            } else {
                releaseWakeLock()
                positionSaveJob?.cancel()
                statsPollJob?.cancel()
                saveCurrentPositionImmediate()
                app.usbManager.refreshStats()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveCurrentPositionImmediate()
            HiFiApp.from(this@HiFiPlaybackService).usbManager.refreshStats()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                releaseWakeLock()
                HiFiApp.from(this@HiFiPlaybackService).usbManager.refreshStats()
            }
        }
    }

    private fun startPositionSaver() {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            while (isActive && player.isPlaying) {
                delay(5000)
                saveCurrentPositionImmediate()
            }
        }
    }

    private fun startStatsPoller() {
        statsPollJob?.cancel()
        statsPollJob = serviceScope.launch {
            val usb = HiFiApp.from(this@HiFiPlaybackService).usbManager
            while (isActive && player.isPlaying) {
                if (usb.isConnected) usb.refreshStats()
                delay(800)
            }
        }
    }

    private fun saveCurrentPositionImmediate() {
        val app = HiFiApp.from(this)
        if (!app.prefs.current.rememberPosition) return
        val path = player.currentMediaItem?.mediaId ?: return
        if (path.isBlank()) return
        val posMs = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            app.db.playbackPositionDao().upsert(PlaybackPositionEntity(path, posMs))
            app.prefs.saveLastPlayback(path, posMs)
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val wl = wakeLock ?: return
            if (!wl.isHeld) wl.acquire(6 * 60 * 60 * 1000L) // 最长 6 小时保底
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            val wl = wakeLock ?: return
            if (wl.isHeld) wl.release()
        }
    }

    companion object {
        private const val TAG = "HiFiService"
        const val CMD_REBIND_ROUTE = "com.sonify.player.CMD_REBIND_ROUTE"

        @Volatile
        var instance: HiFiPlaybackService? = null
            private set

        private val _activeRouteFlow = MutableStateFlow(ActiveOutputRoute.NONE)
        val activeRouteFlow: StateFlow<ActiveOutputRoute> = _activeRouteFlow.asStateFlow()

        /** 将 TrackEntity 转换为 Media3 MediaItem */
        fun trackToMediaItem(track: TrackEntity): MediaItem {
            val metaBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setTrackNumber(track.trackNumber.takeIf { it > 0 })
            track.coverPath?.let { cp ->
                val f = File(cp)
                if (f.isFile) metaBuilder.setArtworkUri(Uri.fromFile(f))
            }
            return MediaItem.Builder()
                .setMediaId(track.path)
                .setUri(Uri.fromFile(File(track.path)))
                .setMediaMetadata(metaBuilder.build())
                .build()
        }
    }
}

/**
 * 远程音量 ForwardingPlayer：
 * - USB 独占开启且已连 DAC（非固定直通）时，向系统 MediaSession 声明 PLAYBACK_TYPE_REMOTE (0..100)，
 *   令息屏/后台时的物理音量键自动走 UsbVolumeController（并严格受护耳上限约束）；
 * - 切回本机输出时返回 PLAYBACK_TYPE_LOCAL，恢复原生 Android 系统音量。
 */
@OptIn(UnstableApi::class)
private class UsbVolumeForwardingPlayer(
    player: Player,
    private val app: HiFiApp,
) : ForwardingPlayer(player) {

    private val externalListeners = mutableListOf<Player.Listener>()

    private fun isRemoteUsbVolumeActive(): Boolean =
        app.usbManager.isConnected &&
            app.prefs.current.usbExclusiveEnabled &&
            app.prefs.current.volumeMode != VolumeMode.FIXED_DIRECT

    override fun addListener(listener: Player.Listener) {
        externalListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        externalListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return if (isRemoteUsbVolumeActive()) {
            base.buildUpon()
                .add(Player.COMMAND_GET_DEVICE_VOLUME)
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                .build()
        } else base
    }

    override fun getDeviceInfo(): DeviceInfo =
        if (isRemoteUsbVolumeActive()) {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .build()
        } else {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
        }

    override fun getDeviceVolume(): Int =
        if (isRemoteUsbVolumeActive()) app.volumeController.state.value.volumePercent
        else super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean =
        if (isRemoteUsbVolumeActive()) app.volumeController.state.value.volumePercent <= 0
        else super.isDeviceMuted()

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        if (isRemoteUsbVolumeActive()) app.volumeController.setVolumePercent(volume, showHud = true)
        else super.setDeviceVolume(volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (isRemoteUsbVolumeActive()) app.volumeController.setVolumePercent(volume, showHud = true)
        else super.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        if (isRemoteUsbVolumeActive()) app.volumeController.stepUp(showHud = true)
        else super.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (isRemoteUsbVolumeActive()) app.volumeController.stepUp(showHud = true)
        else super.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        if (isRemoteUsbVolumeActive()) app.volumeController.stepDown(showHud = true)
        else super.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (isRemoteUsbVolumeActive()) app.volumeController.stepDown(showHud = true)
        else super.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        if (isRemoteUsbVolumeActive()) app.volumeController.setMuted(muted, showHud = true)
        else super.setDeviceMuted(muted)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        if (isRemoteUsbVolumeActive()) app.volumeController.setMuted(muted, showHud = true)
        else super.setDeviceMuted(muted, flags)
    }

    fun notifyDeviceInfoChanged() {
        val info = getDeviceInfo()
        externalListeners.toList().forEach { it.onDeviceInfoChanged(info) }
    }

    fun notifyVolumeChanged(percent: Int) {
        if (!isRemoteUsbVolumeActive()) return
        externalListeners.toList().forEach { it.onDeviceVolumeChanged(percent, percent <= 0) }
    }
}
