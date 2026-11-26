package com.sonify.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sonify.player.HiFiApp
import com.sonify.player.MainActivity
import com.sonify.player.R
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.audio.HiFiRenderersFactory
import com.sonify.player.audio.HybridAudioSink
import com.sonify.player.data.PlaybackPositionEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.VolumeMode
import com.sonify.player.ui.lockscreen.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 前台/后台播放核心服务（MediaSessionService）：
 * - 自定义 Spotify 风格 5 键通知栏（♡ ⏮ ⏯ ⏭ 🔁）+ 实时 HiFi 协商规格副标题 + 跨进程高清封面
 * - 点击通知栏直达全屏黑胶播放页
 * - 熄屏播放中静默就位 Sonify 专属黑胶锁屏界面（无需悬浮窗权限、息屏不耗电）
 * - 支持蓝牙/耳机线控唤醒断点续播（onPlaybackResumption）
 * - 监听耳机/USB 拔出自动暂停、每 5 秒/暂停/切歌保存断点、按需 PARTIAL_WAKE_LOCK
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

    @Volatile
    private var currentTrackIsFavorite: Boolean = false

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

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val app = HiFiApp.from(this@HiFiPlaybackService)
                if (app.prefs.current.lockScreenVinylEnabled &&
                    player.isPlaying &&
                    player.mediaItemCount > 0
                ) {
                    launchVinylLockScreen()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = HiFiApp.from(this)

        // 注册 Spotify 风格 5 键通知栏 Provider（含黑胶单色小图标）
        setMediaNotificationProvider(SonifyMediaNotificationProvider(this))

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

        // 用远程音量 + 实时 HiFi 元数据代理包装 ExoPlayer
        sessionPlayer = UsbVolumeForwardingPlayer(player, app)

        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOW_PLAYING, true)
        }
        val sessionActivityPi = PendingIntent.getActivity(
            this,
            1001,
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivityPi)
            .setBitmapLoader(SonifyBitmapLoader(this))
            .setCallback(sessionCallback)
            .build()

        updateNotificationCustomLayout()

        app.volumeController.onVolumeChangedCallback = { vol ->
            serviceScope.launch {
                sessionPlayer.notifyVolumeChanged(vol)
            }
        }

        // 暴露活跃路由流并刷新通知栏 HiFi 规格
        serviceScope.launch {
            hybridSink.activeRoute.collect { route ->
                _activeRouteFlow.value = route
                sessionPlayer.notifyDeviceInfoChanged()
                sessionPlayer.notifyMediaMetadataChanged()
            }
        }

        // USB 协商状态变化（采样率/位深切换）实时更新通知栏 HiFi 规格副标题
        serviceScope.launch {
            var lastRate = -1
            var lastBits = -1
            app.usbManager.state.collect { st ->
                if (st.stats.rate != lastRate || st.stats.bits != lastBits) {
                    lastRate = st.stats.rate
                    lastBits = st.stats.bits
                    sessionPlayer.notifyMediaMetadataChanged()
                }
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

        // 监听 USB DAC 新连接：若开启 USB 独占，自动切到 USB DAC
        serviceScope.launch {
            app.usbManager.connectedEvents.collect {
                if (app.prefs.current.usbExclusiveEnabled && player.mediaItemCount > 0) {
                    rebindOutputRoute(autoResume = player.playWhenReady)
                }
            }
        }

        // 监听音量/护耳/通知规格显示开关变化
        serviceScope.launch {
            app.prefs.settings.collect {
                app.volumeController.onProtectionPolicyChanged()
                sessionPlayer.notifyDeviceInfoChanged()
                sessionPlayer.notifyMediaMetadataChanged()
            }
        }

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_EXPORTED,
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
        // 播放中划掉多任务卡片继续在后台运行；仅在暂停或无曲目时退出服务
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
        runCatching { unregisterReceiver(screenOffReceiver) }
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

    /** 通知栏与 App 内收藏同步后调用，即时刷新通知栏心形图标 */
    fun notifyFavoriteChanged(trackPath: String, isFavorite: Boolean) {
        if (player.currentMediaItem?.mediaId == trackPath) {
            currentTrackIsFavorite = isFavorite
            updateNotificationCustomLayout()
        }
    }

    private fun refreshFavoriteStateForCurrentTrack() {
        val path = player.currentMediaItem?.mediaId ?: return
        val app = HiFiApp.from(this)
        serviceScope.launch(Dispatchers.IO) {
            val fav = app.db.trackDao().findByPath(path)?.isFavorite == true
            serviceScope.launch(Dispatchers.Main.immediate) {
                currentTrackIsFavorite = fav
                updateNotificationCustomLayout()
            }
        }
    }

    private fun updateNotificationCustomLayout() {
        val session = mediaSession ?: return
        val favButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setIconResId(
                if (currentTrackIsFavorite) R.drawable.ic_notif_favorite_filled
                else R.drawable.ic_notif_favorite_outline
            )
            .setDisplayName(if (currentTrackIsFavorite) "取消收藏" else "收藏")
            .build()

        val (repeatIcon, repeatLabel) = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all to "列表循环"
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one to "单曲循环"
            else -> R.drawable.ic_notif_repeat_off to "顺序播放"
        }
        val repeatButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setDisplayName(repeatLabel)
            .build()

        session.setCustomLayout(ImmutableList.of(favButton, repeatButton))
    }

    private fun launchVinylLockScreen() {
        runCatching {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            val pi = PendingIntent.getActivity(
                this,
                LOCKSCREEN_NOTIF_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Android 10+ 后台启动限制（BAL）会拦截熄屏后直接 startActivity，
            // 使用静默高优先级 FullScreenIntent 通道官方合规唤起黑胶锁屏界面，
            // LockScreenActivity 启动后会立即撤销该触发通知，不污染通知栏。
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                val ch = NotificationChannel(
                    LOCKSCREEN_CHANNEL_ID,
                    "Sonify 黑胶锁屏",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }

            val notif = NotificationCompat.Builder(this, LOCKSCREEN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sonify_notification)
                .setContentTitle(player.currentMediaItem?.mediaMetadata?.title ?: "Sonify")
                .setContentText("黑胶锁屏播放中")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true)
                .build()

            nm?.notify(LOCKSCREEN_NOTIF_ID, notif)
            Log.i(TAG, "已通过 FullScreenIntent 触发黑胶锁屏界面")
        }.onFailure {
            Log.w(TAG, "启动黑胶锁屏界面失败: ${it.message}")
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val cmds = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_REBIND_ROUTE, Bundle.EMPTY))
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(cmds, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_REBIND_ROUTE -> {
                    rebindOutputRoute()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                CMD_TOGGLE_FAVORITE -> {
                    val path = player.currentMediaItem?.mediaId
                    if (!path.isNullOrBlank()) {
                        val next = !currentTrackIsFavorite
                        currentTrackIsFavorite = next
                        updateNotificationCustomLayout()
                        val app = HiFiApp.from(this@HiFiPlaybackService)
                        serviceScope.launch(Dispatchers.IO) {
                            app.db.trackDao().setFavorite(path, next)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                CMD_CYCLE_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    updateNotificationCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /** 耳机线控 / 蓝牙按键 / 系统锁屏媒体卡片在进程重启后一键恢复上次断点续播 */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaItemsWithStartPosition>()
            val app = HiFiApp.from(this@HiFiPlaybackService)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val allTracks = app.db.trackDao().observeAll().first()
                    if (allTracks.isEmpty()) {
                        future.setException(IllegalStateException("媒体库为空"))
                        return@launch
                    }
                    val lastPath = app.prefs.current.lastTrackPath
                    val startIdx = allTracks.indexOfFirst { it.path == lastPath }.coerceAtLeast(0)
                    val startPos = if (lastPath != null && allTracks[startIdx].path == lastPath) {
                        app.prefs.current.lastPositionMs.coerceAtLeast(0L)
                    } else 0L
                    val items = allTracks.map { trackToMediaItem(it) }
                    future.set(MediaItemsWithStartPosition(items, startIdx, startPos))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
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
            refreshFavoriteStateForCurrentTrack()
            HiFiApp.from(this@HiFiPlaybackService).usbManager.refreshStats()
            sessionPlayer.notifyMediaMetadataChanged()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateNotificationCustomLayout()
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
            if (!wl.isHeld) wl.acquire(6 * 60 * 60 * 1000L)
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
        const val CMD_TOGGLE_FAVORITE = "com.sonify.player.CMD_TOGGLE_FAVORITE"
        const val CMD_CYCLE_REPEAT = "com.sonify.player.CMD_CYCLE_REPEAT"
        const val EXTRA_OPEN_NOW_PLAYING = "com.sonify.player.EXTRA_OPEN_NOW_PLAYING"
        const val LOCKSCREEN_CHANNEL_ID = "sonify_lockscreen_trigger"
        const val LOCKSCREEN_NOTIF_ID = 2026

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
 * 远程音量 + 实时 HiFi 规格元数据 ForwardingPlayer：
 * 1. USB 独占开启且已连 DAC（非固定直通）时，向系统 MediaSession 声明 PLAYBACK_TYPE_REMOTE (0..100)，
 *    令息屏/后台时的物理音量键自动走 UsbVolumeController（严格受护耳上限约束）；
 * 2. 动态把实时 HiFi 协商规格（如 "USB 独占 · 44.1k/32b"）注入通知栏与原生锁屏卡片的 artist/subtitle。
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

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val settings = app.prefs.current
        val usbSt = app.usbManager.state.value
        val route = HiFiPlaybackService.activeRouteFlow.value

        val specBadge = SonifyNotificationFormat.formatSpecBadge(
            activeRoute = route,
            usbConnected = usbSt.isConnected,
            stats = usbSt.stats,
            deviceName = usbSt.deviceDisplayName,
        )
        val rawArtist = base.artist?.toString().orEmpty()
        val rawAlbum = base.albumTitle?.toString().orEmpty()
        val enrichedArtist = SonifyNotificationFormat.formatArtistLine(
            artist = rawArtist,
            album = rawAlbum,
            showSpec = settings.notifShowAudioSpec,
            specBadge = specBadge,
        )

        return base.buildUpon()
            .setArtist(enrichedArtist)
            .setSubtitle(if (settings.notifShowAudioSpec) specBadge else rawAlbum)
            .build()
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

    fun notifyMediaMetadataChanged() {
        val meta = getMediaMetadata()
        externalListeners.toList().forEach { it.onMediaMetadataChanged(meta) }
    }

    fun notifyVolumeChanged(percent: Int) {
        if (!isRemoteUsbVolumeActive()) return
        externalListeners.toList().forEach { it.onDeviceVolumeChanged(percent, percent <= 0) }
    }
}
