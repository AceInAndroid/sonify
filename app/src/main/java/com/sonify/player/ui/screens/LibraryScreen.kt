package com.sonify.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonify.player.data.AppSettings
import com.sonify.player.data.DownloadedTrackEntity
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.download.DownloadQueueState
import com.sonify.player.library.PlaylistListSort
import com.sonify.player.library.PlaylistSorter
import com.sonify.player.library.PlaylistSummary
import com.sonify.player.library.ScanState
import com.sonify.player.library.TrackSortField
import com.sonify.player.subsonic.SubsonicAlbum
import com.sonify.player.subsonic.SubsonicArtist
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.SubsonicBrowseUiState
import com.sonify.player.ui.SubsonicDrillState
import com.sonify.player.ui.SubsonicTab
import com.sonify.player.ui.components.BatchSelectionBar
import com.sonify.player.ui.components.PlaylistCoverArt
import com.sonify.player.ui.components.TrackSortSheet
import com.sonify.player.ui.components.VinylDisc
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration
import java.io.File

enum class LibraryFilter(val label: String) {
    SONGS("歌曲"),
    PLAYLISTS("歌单"),
    ALBUMS("专辑"),
    ARTISTS("艺术家"),
    FOLDERS("文件夹"),
    FAVORITES("已收藏"),
    DOWNLOADS("已下载"),
}

private data class DrillDownGroup(
    val title: String,
    val subtitle: String,
    val coverPath: String?,
    val tracks: List<TrackEntity>,
)

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    localPlaylists: List<PlaylistSummary>,
    downloadedList: List<DownloadedTrackEntity>,
    downloadsByPath: Map<String, DownloadedTrackEntity>,
    downloadQueue: DownloadQueueState,
    playback: PlaybackUiState,
    settings: AppSettings,
    scanState: ScanState,
    hasStoragePermission: Boolean,
    subsonicServers: List<SubsonicServerEntity>,
    selectedSubsonicServer: SubsonicServerEntity?,
    subsonicState: SubsonicBrowseUiState,
    onSelectLocalSource: () -> Unit,
    onSelectSubsonicServer: (SubsonicServerEntity) -> Unit,
    onOpenSubsonicAccounts: (Boolean) -> Unit,
    onSubsonicSelectTab: (SubsonicTab) -> Unit,
    onSubsonicSelectFolder: (String?) -> Unit,
    onSubsonicRefresh: () -> Unit,
    onSubsonicLoadMoreAlbums: () -> Unit,
    onSubsonicOpenAlbum: (SubsonicAlbum) -> Unit,
    onSubsonicOpenArtist: (SubsonicArtist) -> Unit,
    onSubsonicOpenPlaylist: (SubsonicPlaylist) -> Unit,
    onSubsonicCloseDrill: () -> Unit,
    onSubsonicToggleSearch: (Boolean) -> Unit,
    onSubsonicSearchQuery: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onImportFiles: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onShufflePlay: (List<TrackEntity>, String) -> Unit,
    onResumeLast: () -> Unit,
    onChangeLocalSongSort: (TrackSortField, Boolean) -> Unit,
    onOpenCreatePlaylist: (Boolean) -> Unit,
    onOpenLocalPlaylist: (PlaylistSummary) -> Unit,
    onQuickPlayLocalPlaylist: (PlaylistSummary) -> Unit,
    onTrackMenu: (TrackEntity) -> Unit,
    onBatchAddToPlaylist: (List<TrackEntity>) -> Unit,
    onDownloadTracks: (List<TrackEntity>) -> Unit,
    onOpenDownloadManager: () -> Unit,
    onEditRemotePlaylist: (SubsonicPlaylist) -> Unit,
    onDeleteRemotePlaylist: (SubsonicPlaylist) -> Unit,
    onAddTracksToRemotePlaylist: (SubsonicPlaylist, Set<String>) -> Unit,
    onRemoveRemotePlaylistIndexes: (SubsonicPlaylist, List<Int>) -> Unit,
    onOpenGlobalSearch: () -> Unit,
    selectedFilter: LibraryFilter = LibraryFilter.SONGS,
    onSelectFilter: (LibraryFilter) -> Unit = {},
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    val filter = selectedFilter
    var drillGroup by remember { mutableStateOf<DrillDownGroup?>(null) }
    var localSearchActive by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    var showLocalSortSheet by remember { mutableStateOf(false) }
    var playlistListSort by remember { mutableStateOf(PlaylistListSort.UPDATED) }
    var multiSelect by remember(filter) { mutableStateOf(false) }
    val selectedPaths = remember(filter) { mutableStateListOf<String>() }

    val localSortField = remember(settings.librarySongSortField) {
        TrackSortField.fromNameOrDefault(settings.librarySongSortField, TrackSortField.RECENTLY_ADDED)
    }
    val localSortAsc = settings.librarySongSortAscending

    BackHandler(enabled = multiSelect) {
        multiSelect = false
        selectedPaths.clear()
    }
    BackHandler(enabled = localSearchActive && drillGroup == null && selectedSubsonicServer == null) {
        localSearchActive = false
        localSearchQuery = ""
    }
    BackHandler(enabled = drillGroup != null) {
        drillGroup = null
    }
    BackHandler(enabled = selectedSubsonicServer != null && subsonicState.drill != SubsonicDrillState.None) {
        onSubsonicCloseDrill()
    }
    BackHandler(enabled = selectedSubsonicServer != null && subsonicState.searchActive && subsonicState.drill == SubsonicDrillState.None) {
        onSubsonicToggleSearch(false)
    }

    val currentGroup = drillGroup
    if (selectedSubsonicServer == null && currentGroup != null) {
        GroupDetailScreen(
            group = currentGroup,
            currentTrackPath = playback.currentTrack?.path,
            downloadsByPath = downloadsByPath,
            onBack = { drillGroup = null },
            onPlay = { list, idx -> onPlayTracks(list, idx, currentGroup.title) },
            onTrackMenu = onTrackMenu,
            onBatchAddToPlaylist = onBatchAddToPlaylist,
            bottomPadding = bottomPadding,
        )
        return
    }

    val filteredLocalTracks = remember(tracks, localSearchActive, localSearchQuery) {
        val q = if (localSearchActive) localSearchQuery.trim().lowercase() else ""
        if (q.isEmpty()) tracks
        else tracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }

    val sortedLocalSongs = remember(filteredLocalTracks, localSortField, localSortAsc) {
        PlaylistSorter.sortPlainTracks(filteredLocalTracks, localSortField, localSortAsc)
    }

    val favoriteTracks = remember(filteredLocalTracks, localSortField, localSortAsc) {
        PlaylistSorter.sortPlainTracks(filteredLocalTracks.filter { it.isFavorite }, localSortField, localSortAsc)
    }

    val downloadedTracks = remember(downloadedList, localSearchActive, localSearchQuery) {
        val all = downloadedList.map { it.toTrackEntity() }
        val q = if (localSearchActive) localSearchQuery.trim().lowercase() else ""
        if (q.isEmpty()) all
        else all.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }

    val sortedPlaylists = remember(localPlaylists, playlistListSort, localSearchActive, localSearchQuery) {
        val q = if (localSearchActive) localSearchQuery.trim().lowercase() else ""
        val base = if (q.isEmpty()) localPlaylists else localPlaylists.filter {
            it.playlist.name.lowercase().contains(q) || it.playlist.description.lowercase().contains(q)
        }
        when (playlistListSort) {
            PlaylistListSort.UPDATED -> base.sortedByDescending { it.playlist.updatedAt }
            PlaylistListSort.NAME -> base.sortedBy { it.playlist.name.lowercase() }
            PlaylistListSort.COUNT -> base.sortedByDescending { it.trackCount }
        }
    }

    val lastTrack = remember(tracks, settings.lastTrackPath, settings.rememberPosition, playback.currentTrack) {
        if (!settings.rememberPosition || playback.currentTrack != null) null
        else settings.lastTrackPath?.let { p -> tracks.firstOrNull { it.path == p } }
    }

    val inRemoteDrill = selectedSubsonicServer != null && subsonicState.drill != SubsonicDrillState.None

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!inRemoteDrill) {
                // 顶部 Spotify 风格标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedSubsonicServer?.name ?: "音乐库",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 离线下载队列入口（有活跃任务时亮起绿点）
                    if (downloadQueue.activeCount > 0 || downloadedList.isNotEmpty() || selectedSubsonicServer != null) {
                        IconButton(onClick = onOpenDownloadManager) {
                            Box {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "下载管理",
                                    tint = if (downloadQueue.activeCount > 0) colors.exclusiveGreen else colors.textPrimary,
                                )
                                if (downloadQueue.activeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.exclusiveGreen)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSubsonicServer != null) {
                        IconButton(onClick = onOpenGlobalSearch) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "全局搜索",
                                tint = colors.textPrimary,
                            )
                        }
                        IconButton(onClick = onSubsonicRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新远程曲库", tint = colors.textPrimary)
                        }
                        IconButton(onClick = { onOpenSubsonicAccounts(false) }) {
                            Icon(Icons.Rounded.Cloud, contentDescription = "管理 Subsonic 账号", tint = colors.exclusiveGreen)
                        }
                    } else {
                        IconButton(onClick = onOpenGlobalSearch) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "全局搜索",
                                tint = colors.textPrimary,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (scanState.isScanning) onCancelScan()
                                else if (!hasStoragePermission) onRequestPermission()
                                else onScan()
                            }
                        ) {
                            Icon(
                                imageVector = if (scanState.isScanning) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                contentDescription = if (scanState.isScanning) "取消扫描" else "扫描本地音乐",
                                tint = if (scanState.isScanning) colors.accent else colors.textPrimary,
                            )
                        }
                        IconButton(onClick = onImportFiles) {
                            Icon(Icons.Rounded.Add, contentDescription = "导入音频文件", tint = colors.textPrimary)
                        }
                    }
                }

                // 顶部数据源切换胶囊条：本地音乐 / 已保存的 Subsonic 服务器账号 / 登录新服务器
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "source_local") {
                        val localSelected = selectedSubsonicServer == null
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (localSelected) colors.surface3 else colors.surface1)
                                .border(
                                    1.dp,
                                    if (localSelected) colors.accent.copy(alpha = 0.7f) else colors.hairline,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { onSelectLocalSource() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Smartphone,
                                contentDescription = null,
                                tint = if (localSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "本地音乐",
                                fontSize = 12.sp,
                                fontWeight = if (localSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (localSelected) colors.textPrimary else colors.textSecondary,
                            )
                        }
                    }

                    items(subsonicServers, key = { "srv_${it.id}" }) { srv ->
                        val srvSelected = selectedSubsonicServer?.id == srv.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (srvSelected) colors.surface3 else colors.surface1)
                                .border(
                                    1.dp,
                                    if (srvSelected) colors.exclusiveGreen.copy(alpha = 0.75f) else colors.hairline,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { onSelectSubsonicServer(srv) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (srvSelected) colors.exclusiveGreen else colors.textMuted)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = srv.name,
                                fontSize = 12.sp,
                                fontWeight = if (srvSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (srvSelected) colors.textPrimary else colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    item(key = "source_add_subsonic") {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surface1)
                                .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                                .clickable { onOpenSubsonicAccounts(subsonicServers.isEmpty()) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (subsonicServers.isEmpty()) Icons.Rounded.Add else Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (subsonicServers.isEmpty()) "登录 Subsonic" else "管理账号",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Subsonic 远程模式
            if (selectedSubsonicServer != null) {
                SubsonicLibraryContent(
                    server = selectedSubsonicServer,
                    state = subsonicState,
                    currentTrackPath = playback.currentTrack?.path,
                    downloadsByPath = downloadsByPath,
                    onSelectTab = onSubsonicSelectTab,
                    onSelectFolder = onSubsonicSelectFolder,
                    onRefresh = onSubsonicRefresh,
                    onLoadMoreAlbums = onSubsonicLoadMoreAlbums,
                    onOpenAlbum = onSubsonicOpenAlbum,
                    onOpenArtist = onSubsonicOpenArtist,
                    onOpenPlaylist = onSubsonicOpenPlaylist,
                    onCloseDrill = onSubsonicCloseDrill,
                    onUpdateSearchQuery = onSubsonicSearchQuery,
                    onCloseSearch = { onSubsonicToggleSearch(false) },
                    onPlayTracks = onPlayTracks,
                    onShufflePlay = onShufflePlay,
                    onOpenAccounts = { onOpenSubsonicAccounts(false) },
                    onTrackMenu = onTrackMenu,
                    onBatchAddToPlaylist = onBatchAddToPlaylist,
                    onDownloadTracks = onDownloadTracks,
                    onCreateRemotePlaylist = { onOpenCreatePlaylist(true) },
                    onEditRemotePlaylist = onEditRemotePlaylist,
                    onDeleteRemotePlaylist = onDeleteRemotePlaylist,
                    onAddTracksToRemotePlaylist = onAddTracksToRemotePlaylist,
                    onRemoveRemotePlaylistIndexes = onRemoveRemotePlaylistIndexes,
                    bottomPadding = bottomPadding,
                )
                return@Column
            }

            // 本地即时搜索输入框
            if (localSearchActive) {
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    placeholder = { Text("搜索本地歌曲、歌单、专辑、艺人…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary) },
                    trailingIcon = if (localSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { localSearchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空", tint = colors.textSecondary)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.hairline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 扫描进度条
            if (scanState.isScanning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface1)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "正在扫描 ${scanState.scanned}/${scanState.total}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                        )
                        Text(
                            text = scanState.currentFile,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val frac = if (scanState.total > 0) scanState.scanned.toFloat() / scanState.total else 0f
                    LinearProgressIndicator(
                        progress = { frac },
                        color = colors.accent,
                        trackColor = colors.surface3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }

            // 断点记忆「继续上次播放」卡片
            if (lastTrack != null && !localSearchActive && filter == LibraryFilter.SONGS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface1)
                        .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                        .clickable { onResumeLast() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VinylDisc(
                        coverPath = lastTrack.coverPath,
                        isPlaying = false,
                        size = 44.dp,
                        isMini = true,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("继续上次播放", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                        Text(
                            text = lastTrack.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${lastTrack.artist} · ${formatDuration(settings.lastPositionMs)} / ${formatDuration(lastTrack.durationMs)}",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.textPrimary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "继续播放", tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Spotify 标志性横向胶囊筛选 Chips（歌曲 / 歌单 / 专辑 / 艺术家 / 文件夹 / 已收藏 / 已下载）
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibraryFilter.entries) { item ->
                    val selected = item == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.accent else colors.surface2)
                            .clickable { onSelectFilter(item) }
                            .padding(horizontal = 15.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.onAccent else colors.textPrimary,
                        )
                    }
                }
            }

            // 歌单 / 已下载 即使本地曲库为空也允许直接浏览和管理
            if (tracks.isEmpty() && filter != LibraryFilter.PLAYLISTS && filter != LibraryFilter.DOWNLOADS) {
                EmptyLibraryView(
                    hasPermission = hasStoragePermission,
                    lastSummary = scanState.lastSummary,
                    onRequestPermission = onRequestPermission,
                    onScan = onScan,
                    onImportFiles = onImportFiles,
                )
                return@Column
            }

            when (filter) {
                LibraryFilter.SONGS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + if (multiSelect) 118.dp else 16.dp),
                    ) {
                        item(key = "header_actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable { showLocalSortSheet = true }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val arrow = if (localSortAsc) "↑" else "↓"
                                    Text("${localSortField.label} $arrow · ${sortedLocalSongs.size}", fontSize = 12.sp, color = colors.textPrimary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.surface2)
                                            .clickable {
                                                multiSelect = !multiSelect
                                                if (!multiSelect) selectedPaths.clear()
                                            }
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.Checklist, contentDescription = null, tint = if (multiSelect) colors.accent else colors.textSecondary, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("多选", fontSize = 12.sp, color = colors.textPrimary)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.surface2)
                                            .clickable { onShufflePlay(sortedLocalSongs, "全部随机") }
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                        Text("随机", fontSize = 12.sp, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                        itemsIndexed(sortedLocalSongs, key = { _, t -> t.path }) { idx, track ->
                            val isSel = selectedPaths.contains(track.path)
                            TrackRowItem(
                                track = track,
                                isCurrent = playback.currentTrack?.path == track.path,
                                selectionMode = multiSelect,
                                isSelected = isSel,
                                onClick = {
                                    if (multiSelect) {
                                        if (isSel) selectedPaths.remove(track.path) else selectedPaths.add(track.path)
                                    } else {
                                        onPlayTracks(sortedLocalSongs, idx, "全部歌曲")
                                    }
                                },
                                onLongClick = {
                                    if (!multiSelect) {
                                        multiSelect = true
                                        selectedPaths.clear()
                                        selectedPaths.add(track.path)
                                    }
                                },
                                onMoreClick = { onTrackMenu(track) },
                            )
                        }
                    }
                }

                LibraryFilter.PLAYLISTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        item(key = "playlists_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable {
                                            val entries = PlaylistListSort.entries
                                            playlistListSort = entries[(entries.indexOf(playlistListSort) + 1) % entries.size]
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${playlistListSort.label} · ${sortedPlaylists.size}", fontSize = 12.sp, color = colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.accent)
                                        .clickable { onOpenCreatePlaylist(false) }
                                        .padding(horizontal = 13.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("新建歌单", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                }
                            }
                        }

                        if (sortedPlaylists.isEmpty()) {
                            item(key = "pl_empty_list") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 52.dp, horizontal = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.accent.copy(alpha = 0.7f), modifier = Modifier.size(46.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("还没有自建歌单", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "支持混收本地无损与 Subsonic 曲目，自动拼出 2×2 专辑四宫格封面或自选相册封面",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                    )
                                    Spacer(Modifier.height(18.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.accent)
                                            .clickable { onOpenCreatePlaylist(false) }
                                            .padding(horizontal = 24.dp, vertical = 10.dp)
                                    ) {
                                        Text("立即新建歌单", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                    }
                                }
                            }
                        } else {
                            items(sortedPlaylists, key = { it.playlist.id }) { summary ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenLocalPlaylist(summary) }
                                        .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlaylistCoverArt(
                                        customCoverPath = summary.playlist.customCoverPath,
                                        autoCovers = summary.autoCovers,
                                        modifier = Modifier.size(58.dp),
                                        cornerDp = 8,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = summary.playlist.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        val mins = (summary.totalDurationMs / 60_000L).coerceAtLeast(0L)
                                        val sub = buildString {
                                            append("${summary.trackCount} 首歌曲")
                                            if (mins > 0) append(" · 约 $mins 分钟")
                                            if (summary.playlist.description.isNotBlank()) append(" · ${summary.playlist.description}")
                                        }
                                        Text(
                                            text = sub,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (summary.trackCount > 0) {
                                        IconButton(onClick = { onQuickPlayLocalPlaylist(summary) }) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.surface2),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放歌单", tint = colors.accent, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LibraryFilter.ALBUMS -> {
                    val albums = remember(filteredLocalTracks) {
                        filteredLocalTracks.groupBy { it.album }
                            .entries
                            .sortedBy { it.key.lowercase() }
                            .map { (album, list) ->
                                val sorted = list.sortedWith(
                                    compareBy<TrackEntity> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title }
                                )
                                DrillDownGroup(
                                    title = album,
                                    subtitle = sorted.firstOrNull()?.albumArtist?.ifBlank { sorted.first().artist } ?: "",
                                    coverPath = sorted.firstNotNullOfOrNull { it.coverPath },
                                    tracks = sorted,
                                )
                            }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomPadding + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(albums, key = { it.title }) { group ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { drillGroup = group }
                            ) {
                                CoverSquare(
                                    coverPath = group.coverPath,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    cornerDp = 8,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = group.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${group.subtitle} · ${group.tracks.size} 首",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                LibraryFilter.ARTISTS -> {
                    val artists = remember(filteredLocalTracks) {
                        filteredLocalTracks.groupBy { it.artist }
                            .entries
                            .sortedBy { it.key.lowercase() }
                            .map { (artist, list) ->
                                DrillDownGroup(
                                    title = artist,
                                    subtitle = "${list.map { it.album }.distinct().size} 张专辑 · ${list.size} 首",
                                    coverPath = list.firstNotNullOfOrNull { it.coverPath },
                                    tracks = list,
                                )
                            }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        items(artists, key = { it.title }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { drillGroup = group }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val coverModel = CoverArtSupport.resolveCoilModel(group.coverPath)
                                    if (coverModel != null) {
                                        AsyncImage(
                                            model = coverModel,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textSecondary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(group.subtitle, fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }

                LibraryFilter.FOLDERS -> {
                    val folders = remember(filteredLocalTracks) {
                        filteredLocalTracks.groupBy { it.folderPath }
                            .entries
                            .sortedBy { it.key.lowercase() }
                            .map { (folder, list) ->
                                val shortName = File(folder).name.ifBlank { folder }
                                DrillDownGroup(
                                    title = shortName,
                                    subtitle = "$folder · ${list.size} 首",
                                    coverPath = list.firstNotNullOfOrNull { it.coverPath },
                                    tracks = list,
                                )
                            }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        items(folders, key = { it.subtitle }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { drillGroup = group }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        group.subtitle,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryFilter.FAVORITES -> {
                    if (favoriteTracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("还没有收藏的本地歌曲", fontSize = 14.sp, color = colors.textSecondary)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding + if (multiSelect) 118.dp else 16.dp),
                        ) {
                            item(key = "fav_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("共收藏 ${favoriteTracks.size} 首", fontSize = 12.sp, color = colors.textSecondary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(colors.surface2)
                                                .clickable {
                                                    multiSelect = !multiSelect
                                                    if (!multiSelect) selectedPaths.clear()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Checklist, contentDescription = null, tint = if (multiSelect) colors.accent else colors.textSecondary, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("多选", fontSize = 12.sp, color = colors.textPrimary)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(colors.accent)
                                                .clickable { onPlayTracks(favoriteTracks, 0, "已收藏") }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("播放全部", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                        }
                                    }
                                }
                            }
                            itemsIndexed(favoriteTracks, key = { _, t -> t.path }) { idx, track ->
                                val isSel = selectedPaths.contains(track.path)
                                TrackRowItem(
                                    track = track,
                                    isCurrent = playback.currentTrack?.path == track.path,
                                    selectionMode = multiSelect,
                                    isSelected = isSel,
                                    onClick = {
                                        if (multiSelect) {
                                            if (isSel) selectedPaths.remove(track.path) else selectedPaths.add(track.path)
                                        } else {
                                            onPlayTracks(favoriteTracks, idx, "已收藏")
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelect) {
                                            multiSelect = true
                                            selectedPaths.clear()
                                            selectedPaths.add(track.path)
                                        }
                                    },
                                    onMoreClick = { onTrackMenu(track) },
                                )
                            }
                        }
                    }
                }

                LibraryFilter.DOWNLOADS -> {
                    val totalBytes = remember(downloadedList) { downloadedList.sumOf { it.fileSize.coerceAtLeast(0L) } }
                    val sizeLabel = remember(totalBytes) {
                        val mb = totalBytes / (1024.0 * 1024.0)
                        if (mb >= 1.0) "${"%.1f".format(mb)} MB" else "${(totalBytes / 1024L).coerceAtLeast(1L)} KB"
                    }

                    if (downloadedTracks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.exclusiveGreen.copy(alpha = 0.7f), modifier = Modifier.size(46.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("还没有离线下载的歌曲", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "在 Subsonic 曲库点击单曲下载、歌单下载或专辑下载后，离线原盘无损文件将显示在这里，断网亦可 Bit-Perfect 直通播放",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                            if (downloadQueue.activeCount > 0) {
                                Spacer(Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.exclusiveGreen)
                                        .clickable { onOpenDownloadManager() }
                                        .padding(horizontal = 18.dp, vertical = 9.dp)
                                ) {
                                    Text("查看下载中（${downloadQueue.activeCount}）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding + if (multiSelect) 118.dp else 16.dp),
                        ) {
                            item(key = "dl_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("已离线 ${downloadedTracks.size} 首 · $sizeLabel", fontSize = 12.sp, color = colors.exclusiveGreen)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(colors.surface2)
                                                .clickable { onOpenDownloadManager() }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("管理", fontSize = 12.sp, color = colors.textPrimary)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(colors.accent)
                                                .clickable { onPlayTracks(downloadedTracks, 0, "离线下载") }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("播放", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                        }
                                    }
                                }
                            }
                            itemsIndexed(downloadedTracks, key = { _, t -> t.path }) { idx, track ->
                                val isSel = selectedPaths.contains(track.path)
                                TrackRowItem(
                                    track = track,
                                    isCurrent = playback.currentTrack?.path == track.path,
                                    isDownloaded = true,
                                    selectionMode = multiSelect,
                                    isSelected = isSel,
                                    onClick = {
                                        if (multiSelect) {
                                            if (isSel) selectedPaths.remove(track.path) else selectedPaths.add(track.path)
                                        } else {
                                            onPlayTracks(downloadedTracks, idx, "离线下载")
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelect) {
                                            multiSelect = true
                                            selectedPaths.clear()
                                            selectedPaths.add(track.path)
                                        }
                                    },
                                    onMoreClick = { onTrackMenu(track) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 多选模式底部批量加入歌单操作栏
        if (multiSelect && selectedSubsonicServer == null) {
            val activeList = when (filter) {
                LibraryFilter.SONGS -> sortedLocalSongs
                LibraryFilter.FAVORITES -> favoriteTracks
                LibraryFilter.DOWNLOADS -> downloadedTracks
                else -> emptyList()
            }
            val chosen = activeList.filter { it.path in selectedPaths }
            BatchSelectionBar(
                selectedCount = chosen.size,
                totalCount = activeList.size,
                hasRemoteSelected = false,
                showRemoveFromPlaylist = false,
                onToggleSelectAll = {
                    if (selectedPaths.size >= activeList.size) selectedPaths.clear()
                    else {
                        selectedPaths.clear()
                        selectedPaths.addAll(activeList.map { it.path })
                    }
                },
                onBatchAddToPlaylist = {
                    onBatchAddToPlaylist(chosen)
                    multiSelect = false
                    selectedPaths.clear()
                },
                onBatchDownload = null,
                onBatchRemoveFromPlaylist = null,
                onExit = {
                    multiSelect = false
                    selectedPaths.clear()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + 6.dp),
            )
        }
    }

    if (showLocalSortSheet) {
        TrackSortSheet(
            title = "歌曲列表排序",
            fields = listOf(
                TrackSortField.TITLE,
                TrackSortField.ALBUM,
                TrackSortField.ARTIST,
                TrackSortField.RECENTLY_ADDED,
            ),
            selectedField = localSortField,
            ascending = localSortAsc,
            onSelect = onChangeLocalSongSort,
            onDismiss = { showLocalSortSheet = false },
        )
    }
}

@Composable
private fun GroupDetailScreen(
    group: DrillDownGroup,
    currentTrackPath: String?,
    downloadsByPath: Map<String, DownloadedTrackEntity>,
    onBack: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    onTrackMenu: (TrackEntity) -> Unit,
    onBatchAddToPlaylist: (List<TrackEntity>) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    var multiSelect by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    BackHandler(enabled = multiSelect) {
        multiSelect = false
        selectedPaths.clear()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding + if (multiSelect) 118.dp else 16.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (multiSelect) {
                                    multiSelect = false
                                    selectedPaths.clear()
                                } else onBack()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = colors.textPrimary)
                        }
                        IconButton(
                            onClick = {
                                multiSelect = !multiSelect
                                if (!multiSelect) selectedPaths.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = "批量加入歌单",
                                tint = if (multiSelect) colors.accent else colors.textPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverSquare(
                            coverPath = group.coverPath,
                            modifier = Modifier.size(104.dp),
                            cornerDp = 10,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = group.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = group.subtitle,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.accent)
                                        .clickable { onPlay(group.tracks, 0) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                                    Text("播放", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable { onPlay(group.tracks.shuffled(), 0) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(17.dp))
                                    Text("随机", fontSize = 13.sp, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(group.tracks, key = { _, t -> t.path }) { idx, track ->
                val isSel = selectedPaths.contains(track.path)
                TrackRowItem(
                    track = track,
                    isCurrent = currentTrackPath == track.path,
                    isDownloaded = downloadsByPath.containsKey(track.path),
                    selectionMode = multiSelect,
                    isSelected = isSel,
                    onClick = {
                        if (multiSelect) {
                            if (isSel) selectedPaths.remove(track.path) else selectedPaths.add(track.path)
                        } else {
                            onPlay(group.tracks, idx)
                        }
                    },
                    onLongClick = {
                        if (!multiSelect) {
                            multiSelect = true
                            selectedPaths.clear()
                            selectedPaths.add(track.path)
                        }
                    },
                    onMoreClick = { onTrackMenu(track) },
                )
            }
        }

        if (multiSelect) {
            val chosen = group.tracks.filter { it.path in selectedPaths }
            BatchSelectionBar(
                selectedCount = chosen.size,
                totalCount = group.tracks.size,
                hasRemoteSelected = false,
                showRemoveFromPlaylist = false,
                onToggleSelectAll = {
                    if (selectedPaths.size >= group.tracks.size) selectedPaths.clear()
                    else {
                        selectedPaths.clear()
                        selectedPaths.addAll(group.tracks.map { it.path })
                    }
                },
                onBatchAddToPlaylist = {
                    onBatchAddToPlaylist(chosen)
                    multiSelect = false
                    selectedPaths.clear()
                },
                onBatchDownload = null,
                onBatchRemoveFromPlaylist = null,
                onExit = {
                    multiSelect = false
                    selectedPaths.clear()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: TrackEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    isDownloaded: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(
                start = 16.dp,
                end = if (onMoreClick != null && !selectionMode) 6.dp else 16.dp,
                top = 9.dp,
                bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.textSecondary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(22.dp),
            )
        }
        CoverSquare(
            coverPath = track.coverPath,
            modifier = Modifier.size(48.dp),
            cornerDp = 6,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = "已离线下载",
                        tint = colors.exclusiveGreen,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(13.dp),
                    )
                }
                Text(
                    text = "${track.artist} · ${track.album}",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (track.isHiRes) colors.accent.copy(alpha = 0.16f) else colors.surface2
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = track.formatBadgeShort(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (track.isHiRes) colors.accent else colors.textSecondary,
                )
            }
            if (track.durationMs > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDuration(track.durationMs),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
            }
        }
        if (onMoreClick != null && !selectionMode) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "更多操作",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun CoverSquare(
    coverPath: String?,
    modifier: Modifier = Modifier,
    cornerDp: Int = 8,
) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        val model = CoverArtSupport.resolveCoilModel(coverPath)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(
    hasPermission: Boolean,
    lastSummary: String,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onImportFiles: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = colors.accent.copy(alpha = 0.8f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("还没有本地无损音乐", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (!hasPermission) "授予存储读取权限后，即可一键扫描 /sdcard/Music 中的 FLAC / WAV 无损专辑。"
            else "点击下方按钮扫描本地目录，或直接从系统文件管理器导入歌曲。",
            fontSize = 13.sp,
            color = colors.textSecondary,
        )
        if (lastSummary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(lastSummary, fontSize = 12.sp, color = colors.textMuted)
        }
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent)
                .clickable { if (!hasPermission) onRequestPermission() else onScan() }
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (!hasPermission) "授予权限并扫描" else "扫描本地音乐",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onAccent,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "或通过文件选择器导入",
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier
                .clickable { onImportFiles() }
                .padding(8.dp),
        )
    }
}
