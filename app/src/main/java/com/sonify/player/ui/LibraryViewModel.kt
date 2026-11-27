package com.sonify.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonify.player.HiFiApp
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
                val suffix = if (initialTracks.isNotEmpty()) "并加入 ${initialTracks.size} 首歌曲" else ""
                showToast("已创建歌单「${pl.name}」$suffix")
                app.analytics.track("playlist_action", mapOf("action" to "create", "scope" to "local"))
                onCreated(pl)
            }.onFailure {
                showToast(it.message ?: "创建歌单失败")
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
                showToast("歌单信息已更新")
            }.onFailure {
                showToast(it.message ?: "更新歌单失败")
            }
        }
    }

    fun deleteLocalPlaylist(playlistId: Long, playlistName: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.deletePlaylist(playlistId)
            }.onSuccess {
                showToast("已删除歌单「$playlistName」")
                app.analytics.track("playlist_action", mapOf("action" to "delete", "scope" to "local"))
                onDeleted()
            }.onFailure {
                showToast(it.message ?: "删除歌单失败")
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
                        "已加入「$playlistName」${res.added} 首（跳过 ${res.skippedDuplicates} 首重复）"
                    res.added > 0 ->
                        "已加入「$playlistName」${res.added} 首歌曲"
                    else ->
                        "所选歌曲已在「$playlistName」中"
                }
                showToast(msg)
                if (res.added > 0) {
                    app.analytics.track("playlist_action", mapOf("action" to "add", "scope" to "local", "count" to res.added))
                }
            }.onFailure {
                showToast(it.message ?: "加入歌单失败")
            }
        }
    }

    fun removeTracksFromLocalPlaylist(playlistId: Long, trackPaths: List<String>) {
        viewModelScope.launch {
            runCatching {
                playlistRepo.removeTracksFromPlaylist(playlistId, trackPaths)
            }.onSuccess {
                showToast("已从歌单移除 ${trackPaths.size} 首歌曲")
            }.onFailure {
                showToast(it.message ?: "移除歌曲失败")
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
                showToast("已在服务器创建歌单「$name」")
                onDone()
            }.onFailure {
                showToast(it.message ?: "创建服务器歌单失败")
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
                showToast("已同步更新服务器歌单")
                onDone()
            }.onFailure {
                showToast(it.message ?: "更新服务器歌单失败")
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
                if (count > 0) showToast("已添加 $count 首歌曲到服务器歌单「$playlistName」")
                else showToast("仅支持添加来自同一 Subsonic 服务器的歌曲")
                onDone()
            }.onFailure {
                showToast(it.message ?: "添加到服务器歌单失败")
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
                showToast("已从服务器歌单移除 ${indexes.size} 首歌曲")
                onDone()
            }.onFailure {
                showToast(it.message ?: "移除服务器歌单曲目失败")
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
                showToast("已删除服务器歌单「$playlistName」")
                onDone()
            }.onFailure {
                showToast(it.message ?: "删除服务器歌单失败")
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
                        "已加入下载队列 ${res.queuedCount} 首（${res.skippedAlreadyDownloaded} 首已下载跳过）"
                    res.queuedCount > 0 ->
                        "已开始下载 ${res.queuedCount} 首歌曲"
                    res.skippedAlreadyDownloaded > 0 ->
                        "所选远程歌曲均已离线下载"
                    else ->
                        "所选均为本地歌曲，无需下载"
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
            showToast("已删除离线文件「$title」")
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadMgr.deleteAllDownloads()
            showToast("已清空全部离线下载文件")
        }
    }
}
