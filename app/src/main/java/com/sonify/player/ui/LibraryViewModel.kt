package com.sonify.player.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonify.player.HiFiApp
import com.sonify.player.R
import com.sonify.player.data.DownloadedTrackEntity
import com.sonify.player.data.PlaylistEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.download.DownloadQueueState
import com.sonify.player.download.EnqueueDownloadResult
import com.sonify.player.library.PlaylistSummary
import com.sonify.player.library.SortableTrack
import com.sonify.player.library.TrackSortField
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音乐库增强 ViewModel：
 * 统一管理自建歌单、Subsonic 远端歌单读写、多维排序偏好、批量操作反馈与离线下载队列。
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = HiFiApp.from(application)
    private val playlistRepo = app.playlistRepo
    private val downloadMgr = app.downloadManager

    private fun s(@StringRes id: Int, vararg args: Any): String {
        val ctx = app.localizedContext()
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    val playlists: StateFlow<List<PlaylistSummary>> = playlistRepo.playlists
    val downloadedList: StateFlow<List<DownloadedTrackEntity>> = downloadMgr.downloadedList
    val downloadsByPath: StateFlow<Map<String, DownloadedTrackEntity>> = downloadMgr.downloadsByPath
    val downloadQueue: StateFlow<DownloadQueueState> = downloadMgr.queueState

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _wifiOnlyBlockedTracks = MutableStateFlow<List<TrackEntity>?>(null)
    val wifiOnlyBlockedTracks: StateFlow<List<TrackEntity>?> = _wifiOnlyBlockedTracks.asStateFlow()

    private var toastJob: Job? = null

    fun showToast(msg: String) {
        toastJob?.cancel()
        _toastMessage.value = msg
        toastJob = viewModelScope.launch {
            delay(2400)
            _toastMessage.value = null
        }
    }

    fun observeLocalPlaylist(playlistId: Long): Flow<PlaylistEntity?> =
        playlistRepo.observePlaylist(playlistId)

    fun observeLocalPlaylistTracks(playlistId: Long): Flow<List<SortableTrack>> =
        playlistRepo.observePlaylistTracks(playlistId)

    fun setLibrarySongSort(field: TrackSortField, ascending: Boolean) {
        viewModelScope.launch {
            app.prefs.setLibrarySongSort(field.name, ascending)
        }
    }

    // ==================== 本地自建歌单 CRUD ====================

    fun createLocalPlaylist(
        name: String,
        description: String,
        coverUri: Uri?,
        initialTracks: List<TrackEntity> = emptyList(),
        onCreated: (PlaylistEntity) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val created = playlistRepo.createPlaylist(name, description, coverUri)
                if (initialTracks.isNotEmpty()) {
                    playlistRepo.addTracksToPlaylist(created.id, initialTracks)
                }
                created
            }.onSuccess { pl ->
                val msg = if (initialTracks.isNotEmpty()) {
                    s(R.string.toast_playlist_created_with_tracks, pl.name, initialTracks.size)
                } else {
                    s(R.string.toast_playlist_created, pl.name)
                }
                showToast(msg)
                app.analytics.track("playlist_action", mapOf("action" to "create", "scope" to "local"))
                onCreated(pl)
            }.onFailure {
                showToast(it.message ?: s(R.string.err_playlist_create_fail))
            }
        }
    }

    fun updateLocalPlaylist(
        playlistId: Long,
        name: String,
        description: String,
        newCoverUri: Uri?,
        clearCustomCover: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.updatePlaylist(playlistId, name, description, newCoverUri, clearCustomCover)
            }.onSuccess {
                showToast(s(R.string.toast_playlist_updated))
            }.onFailure {
                showToast(it.message ?: s(R.string.err_playlist_update_fail))
            }
        }
    }

    fun deleteLocalPlaylist(playlistId: Long, playlistName: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.deletePlaylist(playlistId)
            }.onSuccess {
                showToast(s(R.string.toast_playlist_deleted, playlistName))
                app.analytics.track("playlist_action", mapOf("action" to "delete", "scope" to "local"))
                onDeleted()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_playlist_delete_fail))
            }
        }
    }

    fun addTracksToLocalPlaylist(playlistId: Long, playlistName: String, tracks: List<TrackEntity>) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.addTracksToPlaylist(playlistId, tracks)
            }.onSuccess { res ->
                val msg = when {
                    res.added > 0 && res.skippedDuplicates > 0 ->
                        s(R.string.toast_added_with_dups, res.added, playlistName, res.skippedDuplicates)
                    res.added > 0 ->
                        s(R.string.toast_added_tracks, res.added, playlistName)
                    else ->
                        s(R.string.toast_already_in_playlist, playlistName)
                }
                showToast(msg)
                if (res.added > 0) {
                    app.analytics.track("playlist_action", mapOf("action" to "add", "scope" to "local", "count" to res.added))
                }
            }.onFailure {
                showToast(it.message ?: s(R.string.err_playlist_add_fail))
            }
        }
    }

    fun removeTracksFromLocalPlaylist(playlistId: Long, trackPaths: List<String>) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.removeTracksFromPlaylist(playlistId, trackPaths)
            }.onSuccess {
                showToast(s(R.string.toast_removed_tracks, trackPaths.size))
            }.onFailure {
                showToast(it.message ?: s(R.string.err_playlist_remove_fail))
            }
        }
    }

    fun moveTrackInLocalPlaylist(playlistId: Long, trackPath: String, moveUp: Boolean) {
        viewModelScope.launch {
            playlistRepo.moveTrackInPlaylist(playlistId, trackPath, moveUp)
        }
    }

    fun setLocalPlaylistSort(playlistId: Long, field: TrackSortField, ascending: Boolean) {
        viewModelScope.launch {
            playlistRepo.setPlaylistSort(playlistId, field, ascending)
        }
    }

    // ==================== Subsonic 服务端歌单读写同步 ====================

    fun createRemotePlaylist(
        serverId: Long,
        name: String,
        initialTracks: List<TrackEntity> = emptyList(),
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.createRemotePlaylist(serverId, name, initialTracks)
            }.onSuccess {
                showToast(s(R.string.toast_remote_pl_created, name))
                onDone()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_remote_pl_create_fail))
            }
        }
    }

    fun updateRemotePlaylistMeta(
        serverId: Long,
        playlistId: String,
        name: String,
        comment: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.updateRemotePlaylistMeta(serverId, playlistId, name, comment)
            }.onSuccess {
                showToast(s(R.string.toast_remote_pl_updated))
                onDone()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_remote_pl_update_fail))
            }
        }
    }

    fun addTracksToRemotePlaylist(
        serverId: Long,
        playlistId: String,
        playlistName: String,
        tracks: List<TrackEntity>,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.addTracksToRemotePlaylist(serverId, playlistId, tracks)
            }.onSuccess { count ->
                if (count > 0) showToast(s(R.string.toast_remote_pl_added, count, playlistName))
                else showToast(s(R.string.toast_remote_pl_same_server))
                onDone()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_remote_pl_add_fail))
            }
        }
    }

    fun removeIndexesFromRemotePlaylist(
        serverId: Long,
        playlistId: String,
        indexes: List<Int>,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.removeIndexesFromRemotePlaylist(serverId, playlistId, indexes)
            }.onSuccess {
                showToast(s(R.string.toast_remote_pl_removed, indexes.size))
                onDone()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_remote_pl_remove_fail))
            }
        }
    }

    fun deleteRemotePlaylist(
        serverId: Long,
        playlistId: String,
        playlistName: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.deleteRemotePlaylist(serverId, playlistId)
            }.onSuccess {
                showToast(s(R.string.toast_remote_pl_deleted, playlistName))
                onDone()
            }.onFailure {
                showToast(it.message ?: s(R.string.err_remote_pl_delete_fail))
            }
        }
    }

    // ==================== 离线下载操作 ====================

    fun enqueueDownload(tracks: List<TrackEntity>, allowCellularOverride: Boolean = false) {
        when (val res = downloadMgr.enqueueTracks(tracks, allowCellularOverride)) {
            is EnqueueDownloadResult.BlockedByWifiOnly -> {
                _wifiOnlyBlockedTracks.value = res.tracks
            }

            is EnqueueDownloadResult.Started -> {
                val msg = when {
                    res.queuedCount > 0 && res.skippedAlreadyDownloaded > 0 ->
                        s(R.string.toast_dl_queued_with_skip, res.queuedCount, res.skippedAlreadyDownloaded)
                    res.queuedCount > 0 ->
                        s(R.string.toast_dl_started, res.queuedCount)
                    res.skippedAlreadyDownloaded > 0 ->
                        s(R.string.toast_dl_all_offline)
                    else ->
                        s(R.string.toast_dl_all_local)
                }
                showToast(msg)
            }
        }
    }

    fun confirmCellularDownload() {
        val pending = _wifiOnlyBlockedTracks.value ?: return
        _wifiOnlyBlockedTracks.value = null
        enqueueDownload(pending, allowCellularOverride = true)
    }

    fun dismissCellularDownload() {
        _wifiOnlyBlockedTracks.value = null
    }

    fun cancelDownload(remotePath: String) = downloadMgr.cancelTask(remotePath)
    fun cancelAllDownloads() = downloadMgr.cancelAll()
    fun retryDownload(remotePath: String) = downloadMgr.retryTask(remotePath)
    fun clearFinishedDownloads() = downloadMgr.clearFinishedTasks()

    fun deleteDownloadedTrack(remotePath: String, title: String) {
        viewModelScope.launch {
            downloadMgr.deleteDownloadedTrack(remotePath)
            showToast(s(R.string.toast_dl_deleted_file, title))
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadMgr.deleteAllDownloads()
            showToast(s(R.string.toast_dl_cleared_all))
        }
    }
}
