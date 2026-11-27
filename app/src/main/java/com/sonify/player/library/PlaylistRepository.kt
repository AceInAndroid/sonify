package com.sonify.player.library

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.sonify.player.HiFiApp
import com.sonify.player.R
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.PlaylistEntity
import com.sonify.player.data.PlaylistItemEntity
import com.sonify.player.data.SubsonicTrackCacheEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class PlaylistSummary(
    val playlist: PlaylistEntity,
    val trackCount: Int,
    val totalDurationMs: Long,
    val autoCovers: List<String>,
    val remoteTrackCount: Int,
)

data class AddToPlaylistResult(
    val added: Int,
    val skippedDuplicates: Int,
)

/**
 * 歌单统一仓库：
 * - 管理本地自建歌单（支持混收本地无损文件 + Subsonic 远程曲目、自定义封面与排序偏好持久化）；
 * - 代理 Subsonic 服务端歌单的创建、编辑、批量加歌、批量删歌与删除操作。
 */
class PlaylistRepository(
    private val context: Context,
    private val db: LibraryDatabase,
    private val subsonicRepo: SubsonicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun s(@StringRes id: Int, vararg args: Any): String {
        val ctx = (context.applicationContext as? HiFiApp)?.localizedContext() ?: context
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val remoteCacheDao = db.subsonicTrackCacheDao()
    private val downloadedDao = db.downloadedTrackDao()

    private val trackLookupFlow: Flow<Map<String, TrackEntity>> = combine(
        trackDao.observeAll(),
        remoteCacheDao.observeAll(),
        downloadedDao.observeAll(),
    ) { local, remote, downloaded ->
        buildMap {
            downloaded.forEach { d -> put(d.remotePath, d.toTrackEntity()) }
            remote.forEach { r -> put(r.path, r.toTrackEntity()) }
            local.forEach { l -> put(l.path, l) }
        }
    }

    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        playlistDao.observeAllPlaylists(),
        playlistDao.observeAllItems(),
        trackLookupFlow,
    ) { allPlaylists, allItems, lookup ->
        val itemsByPlaylist = allItems.groupBy { it.playlistId }
        allPlaylists.map { pl ->
            val items = itemsByPlaylist[pl.id].orEmpty()
            val resolved = items.mapNotNull { lookup[it.trackPath] }
            val covers = resolved
                .mapNotNull { it.coverPath?.takeIf { c -> c.isNotBlank() } }
                .distinct()
                .take(4)
            PlaylistSummary(
                playlist = pl,
                trackCount = resolved.size,
                totalDurationMs = resolved.sumOf { it.durationMs.coerceAtLeast(0L) },
                autoCovers = covers,
                remoteTrackCount = resolved.count { SubsonicUri.isTrackUri(it.path) },
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?> =
        playlistDao.observePlaylist(playlistId)

    fun observePlaylistTracks(playlistId: Long): Flow<List<SortableTrack>> = combine(
        playlistDao.observeItems(playlistId),
        trackLookupFlow,
    ) { items, lookup ->
        items.mapNotNull { item ->
            val track = lookup[item.trackPath] ?: return@mapNotNull null
            SortableTrack(
                track = track,
                addedAt = item.addedAt,
                position = item.position,
            )
        }
    }

    suspend fun createPlaylist(
        name: String,
        description: String = "",
        coverUri: Uri? = null,
    ): PlaylistEntity = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { s(R.string.err_playlist_name_empty) }
        val now = System.currentTimeMillis()
        val id = playlistDao.upsertPlaylist(
            PlaylistEntity(
                name = cleanName,
                description = description.trim(),
                createdAt = now,
                updatedAt = now,
            )
        )
        val savedCover = if (coverUri != null) {
            PlaylistCoverHelper.saveCustomCover(context, id, coverUri)
        } else null
        if (savedCover != null) {
            playlistDao.updatePlaylistMeta(id, cleanName, description.trim(), savedCover, now)
        }
        playlistDao.findPlaylistById(id) ?: PlaylistEntity(
            id = id,
            name = cleanName,
            description = description.trim(),
            customCoverPath = savedCover,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun updatePlaylist(
        playlistId: Long,
        name: String,
        description: String,
        newCoverUri: Uri? = null,
        clearCustomCover: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { s(R.string.err_playlist_name_empty) }
        val existing = playlistDao.findPlaylistById(playlistId) ?: return@withContext
        val oldCover = existing.customCoverPath
        val nextCover = when {
            newCoverUri != null -> {
                val created = PlaylistCoverHelper.saveCustomCover(context, playlistId, newCoverUri)
                if (created != null && created != oldCover) PlaylistCoverHelper.deleteCoverFile(oldCover)
                created ?: oldCover
            }
            clearCustomCover -> {
                PlaylistCoverHelper.deleteCoverFile(oldCover)
                null
            }
            else -> oldCover
        }
        playlistDao.updatePlaylistMeta(
            playlistId = playlistId,
            name = cleanName,
            description = description.trim(),
            coverPath = nextCover,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val existing = playlistDao.findPlaylistById(playlistId)
        PlaylistCoverHelper.deleteCoverFile(existing?.customCoverPath)
        playlistDao.deleteAllItemsForPlaylist(playlistId)
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<TrackEntity>,
    ): AddToPlaylistResult = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext AddToPlaylistResult(0, 0)

        // 若包含 Subsonic 远程曲目，确保其元数据落入 subsonic_tracks 缓存表，保证歌单随时可解析
        val remoteEntities = tracks.mapNotNull { t ->
            val ref = SubsonicUri.parseTrackUri(t.path) ?: return@mapNotNull null
            SubsonicTrackCacheEntity(
                path = t.path,
                serverId = ref.serverId,
                remoteId = ref.songId,
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumArtist = t.albumArtist,
                trackNumber = t.trackNumber,
                discNumber = t.discNumber,
                durationMs = t.durationMs,
                sampleRate = t.sampleRate,
                bitDepth = t.bitDepth,
                channels = t.channels,
                bitrateKbps = t.bitrateKbps,
                format = t.format,
                fileSize = t.fileSize,
                coverPath = t.coverPath,
                isFavorite = t.isFavorite,
            )
        }
        if (remoteEntities.isNotEmpty()) {
            remoteCacheDao.upsertAll(remoteEntities)
        }

        val startPos = playlistDao.getMaxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        val distinct = tracks.distinctBy { it.path }
        val items = distinct.mapIndexed { idx, t ->
            PlaylistItemEntity(
                playlistId = playlistId,
                trackPath = t.path,
                position = startPos + idx,
                addedAt = now + idx,
            )
        }
        val rowIds = playlistDao.insertItemsIgnore(items)
        val added = rowIds.count { it != -1L }
        val skipped = items.size - added
        if (added > 0) {
            playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
        }
        AddToPlaylistResult(added = added, skippedDuplicates = skipped)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackPaths: List<String>) =
        withContext(Dispatchers.IO) {
            if (trackPaths.isEmpty()) return@withContext
            playlistDao.deleteItemsByPaths(playlistId, trackPaths)
            playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
        }

    suspend fun moveTrackInPlaylist(playlistId: Long, trackPath: String, moveUp: Boolean) =
        withContext(Dispatchers.IO) {
            val items = playlistDao.getItems(playlistId).toMutableList()
            val idx = items.indexOfFirst { it.trackPath == trackPath }
            if (idx < 0) return@withContext
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in items.indices) return@withContext
            val moved = items.removeAt(idx)
            items.add(targetIdx, moved)
            items.forEachIndexed { pos, entity ->
                if (entity.position != pos) {
                    playlistDao.updateItemPosition(entity.id, pos)
                }
            }
            playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
        }

    suspend fun setPlaylistSort(playlistId: Long, field: TrackSortField, ascending: Boolean) =
        withContext(Dispatchers.IO) {
            playlistDao.updatePlaylistSort(
                playlistId = playlistId,
                sortField = field.name,
                ascending = ascending,
                updatedAt = System.currentTimeMillis(),
            )
        }

    // ==================== Subsonic 服务端歌单读写同步 ====================

    suspend fun createRemotePlaylist(
        serverId: Long,
        name: String,
        tracks: List<TrackEntity> = emptyList(),
    ): SubsonicPlaylist? = withContext(Dispatchers.IO) {
        val creds = subsonicRepo.getCredentials(serverId)
            ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
        val songIds = tracks.mapNotNull { t ->
            SubsonicUri.parseTrackUri(t.path)?.takeIf { it.serverId == serverId }?.songId
        }
        subsonicRepo.client.createPlaylist(creds, name, songIds)
    }

    suspend fun updateRemotePlaylistMeta(
        serverId: Long,
        playlistId: String,
        name: String,
        comment: String,
    ) = withContext(Dispatchers.IO) {
        val creds = subsonicRepo.getCredentials(serverId)
            ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
        subsonicRepo.client.updatePlaylist(
            creds = creds,
            playlistId = playlistId,
            name = name,
            comment = comment,
        )
    }

    suspend fun addTracksToRemotePlaylist(
        serverId: Long,
        playlistId: String,
        tracks: List<TrackEntity>,
    ): Int = withContext(Dispatchers.IO) {
        val creds = subsonicRepo.getCredentials(serverId)
            ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
        val songIds = tracks.mapNotNull { t ->
            SubsonicUri.parseTrackUri(t.path)?.takeIf { it.serverId == serverId }?.songId
        }
        if (songIds.isEmpty()) return@withContext 0
        subsonicRepo.client.updatePlaylist(
            creds = creds,
            playlistId = playlistId,
            songIdsToAdd = songIds,
        )
        songIds.size
    }

    suspend fun removeIndexesFromRemotePlaylist(
        serverId: Long,
        playlistId: String,
        indexes: List<Int>,
    ) = withContext(Dispatchers.IO) {
        if (indexes.isEmpty()) return@withContext
        val creds = subsonicRepo.getCredentials(serverId)
            ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
        subsonicRepo.client.updatePlaylist(
            creds = creds,
            playlistId = playlistId,
            songIndexesToRemove = indexes,
        )
    }

    suspend fun deleteRemotePlaylist(serverId: Long, playlistId: String) =
        withContext(Dispatchers.IO) {
            val creds = subsonicRepo.getCredentials(serverId)
                ?: throw IllegalStateException("找不到对应的 Subsonic 服务器凭据")
            subsonicRepo.client.deletePlaylist(creds, playlistId)
        }
}
