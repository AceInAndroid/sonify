package com.sonify.player.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sonify.player.HiFiApp
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.data.AppSettings
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.ScanState
import com.sonify.player.playback.AudioFocusState
import com.sonify.player.playback.HiFiPlaybackService
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.usb.UsbConnectionState
import com.sonify.player.usb.VolumeHudState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentTrack: TrackEntity? = null,
    val queue: List<TrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val contextLabel: String = "音乐库",
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = HiFiApp.from(application)

    val settings: StateFlow<AppSettings> = app.prefs.settings
    val usbState: StateFlow<UsbConnectionState> = app.usbManager.state
    val volumeState: StateFlow<VolumeHudState> = app.volumeController.state
    val scanState: StateFlow<ScanState> = app.scanner.state
    val activeRoute: StateFlow<ActiveOutputRoute> = HiFiPlaybackService.activeRouteFlow
    val audioFocusState: StateFlow<AudioFocusState> = HiFiPlaybackService.audioFocusStateFlow

    val tracks: StateFlow<List<TrackEntity>> = app.db.trackDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remoteCachedTracks: StateFlow<List<TrackEntity>> = app.subsonicRepo.cachedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeQueueByPath: Map<String, TrackEntity> = emptyMap()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
        }
    }

    init {
        connectController()
    }

    private fun connectController() {
        val token = SessionToken(app, ComponentName(app, HiFiPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val ctrl = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = ctrl
                ctrl.addListener(playerListener)
                syncFromPlayer(ctrl)
                startProgressLoop()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    _playback.update {
                        it.copy(
                            positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                            durationMs = ctrl.duration.takeIf { d -> d > 0 } ?: it.currentTrack?.durationMs ?: 0L,
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun syncFromPlayer(player: Player) {
        val allByPath = buildMap<String, TrackEntity> {
            remoteCachedTracks.value.forEach { put(it.path, it) }
            tracks.value.forEach { put(it.path, it) }
            activeQueueByPath.forEach { (k, v) -> putIfAbsent(k, v) }
        }
        val count = player.mediaItemCount
        val queue = buildList(count) {
            for (i in 0 until count) {
                val item = player.getMediaItemAt(i)
                val entity = allByPath[item.mediaId]
                if (entity != null) {
                    add(entity)
                } else {
                    add(
                        TrackEntity(
                            path = item.mediaId,
                            title = item.mediaMetadata.title?.toString() ?: "未知歌曲",
                            artist = item.mediaMetadata.artist?.toString() ?: "未知艺术家",
                            album = item.mediaMetadata.albumTitle?.toString() ?: "未知专辑",
                            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                        )
                    )
                }
            }
        }
        val idx = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val current = queue.getOrNull(idx)

        _playback.update {
            it.copy(
                currentTrack = current,
                queue = queue,
                currentIndex = idx,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { d -> d > 0 } ?: current?.durationMs ?: 0L,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }
    }

    fun playTracks(
        queue: List<TrackEntity>,
        startIndex: Int,
        contextLabel: String = "音乐库",
        startPositionMs: Long = 0L,
    ) {
        if (queue.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, queue.lastIndex)
        val ctrl = controller ?: return

        // 如果已经有权限的 USB DAC 接在上面且开了独占但未打开，静默连上
        if (app.prefs.current.usbExclusiveEnabled && !app.usbManager.isConnected) {
            app.usbManager.tryAutoConnectPermitted()
        }

        activeQueueByPath = queue.associateBy { it.path }
        _playback.update { it.copy(contextLabel = contextLabel) }
        val items: List<MediaItem> = queue.map { HiFiPlaybackService.trackToMediaItem(it) }
        ctrl.setMediaItems(items, safeIdx, startPositionMs.coerceAtLeast(0L))
        ctrl.prepare()
        ctrl.play()
    }

    fun resumeLastPlayback(allTracks: List<TrackEntity>) {
        val lastPath = settings.value.lastTrackPath ?: return
        val idx = allTracks.indexOfFirst { it.path == lastPath }
        if (idx < 0) return
        val pos = settings.value.lastPositionMs
        playTracks(allTracks, idx, contextLabel = "继续聆听", startPositionMs = pos)
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.playbackState == Player.STATE_IDLE || ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.prepare()
            }
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val ctrl = controller ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        ctrl.seekTo(clamped)
        _playback.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val ctrl = controller ?: return
        if (ctrl.hasNextMediaItem()) ctrl.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val ctrl = controller ?: return
        if (ctrl.currentPosition > 3000L) {
            ctrl.seekTo(0L)
        } else if (ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
        } else {
            ctrl.seekTo(0L)
        }
    }

    fun selectQueueIndex(index: Int) {
        val ctrl = controller ?: return
        if (index in 0 until ctrl.mediaItemCount) {
            ctrl.seekToDefaultPosition(index)
            ctrl.play()
        }
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = !track.isFavorite
            if (SubsonicUri.isTrackUri(track.path)) {
                app.subsonicRepo.toggleRemoteFavorite(track.path, next)
            } else {
                app.db.trackDao().setFavorite(track.path, next)
            }
            HiFiPlaybackService.instance?.notifyFavoriteChanged(track.path, next)
            activeQueueByPath = activeQueueByPath.toMutableMap().apply {
                this[track.path] = track.copy(isFavorite = next)
            }
            _playback.update { st ->
                if (st.currentTrack?.path == track.path) {
                    st.copy(currentTrack = st.currentTrack.copy(isFavorite = next))
                } else st
            }
        }
    }

    /** 修改 USB 独占开关、采样率策略、位深偏好或缓冲大小后无感重建路由 */
    fun rebindAudioRoute() {
        val ctrl = controller
        if (ctrl != null) {
            ctrl.sendCustomCommand(SessionCommand(HiFiPlaybackService.CMD_REBIND_ROUTE, Bundle.EMPTY), Bundle.EMPTY)
        } else {
            HiFiPlaybackService.instance?.rebindOutputRoute()
        }
    }

    fun startScan() {
        viewModelScope.launch {
            app.scanner.scanAllConfiguredDirs()
        }
    }

    fun cancelScan() {
        app.scanner.cancelScan()
    }

    override fun onCleared() {
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
