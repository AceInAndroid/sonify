package com.sonify.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonify.player.data.AppSettings
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.ScanState
import com.sonify.player.subsonic.SubsonicAlbum
import com.sonify.player.subsonic.SubsonicArtist
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.SubsonicBrowseUiState
import com.sonify.player.ui.SubsonicDrillState
import com.sonify.player.ui.SubsonicTab
import com.sonify.player.ui.components.VinylDisc
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration
import java.io.File

private enum class LibraryFilter(val label: String) {
    SONGS("歌曲"),
    ALBUMS("专辑"),
    ARTISTS("艺术家"),
    FOLDERS("文件夹"),
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
    onResumeLast: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalHiFiColors.current
    var filter by remember { mutableStateOf(LibraryFilter.SONGS) }
    var drillGroup by remember { mutableStateOf<DrillDownGroup?>(null) }

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
            onBack = { drillGroup = null },
            onPlay = { list, idx -> onPlayTracks(list, idx, currentGroup.title) },
            bottomPadding = bottomPadding,
        )
        return
    }

    val lastTrack = remember(tracks, settings.lastTrackPath, settings.rememberPosition, playback.currentTrack) {
        if (!settings.rememberPosition || playback.currentTrack != null) null
        else settings.lastTrackPath?.let { p -> tracks.firstOrNull { it.path == p } }
    }

    val inRemoteDrill = selectedSubsonicServer != null && subsonicState.drill != SubsonicDrillState.None

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        if (!inRemoteDrill) {
            // 顶部 Spotify 风格标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
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
                if (selectedSubsonicServer != null) {
                    IconButton(onClick = { onSubsonicToggleSearch(!subsonicState.searchActive) }) {
                        Icon(
                            imageVector = if (subsonicState.searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "搜索服务器",
                            tint = if (subsonicState.searchActive) colors.accent else colors.textPrimary,
                        )
                    }
                    IconButton(onClick = onSubsonicRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新远程曲库",
                            tint = colors.textPrimary,
                        )
                    }
                    IconButton(onClick = { onOpenSubsonicAccounts(false) }) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = "管理 Subsonic 账号",
                            tint = colors.exclusiveGreen,
                        )
                    }
                } else {
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
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "导入音频文件",
                            tint = colors.textPrimary,
                        )
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

        // 若选中了 Subsonic 服务器，直接呈现远程按需浏览内容
        if (selectedSubsonicServer != null) {
            SubsonicLibraryContent(
                server = selectedSubsonicServer,
                state = subsonicState,
                currentTrackPath = playback.currentTrack?.path,
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
                onOpenAccounts = { onOpenSubsonicAccounts(false) },
                bottomPadding = bottomPadding,
            )
            return
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

        // 断点记忆「继续上次播放」卡片（不自动出声）
        if (lastTrack != null) {
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
                    Text(
                        text = "继续上次播放",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                    )
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
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "继续播放",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // Spotify 标志性横向胶囊筛选 Chips
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
                        .clickable { filter = item }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
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

        // 空状态引导
        if (tracks.isEmpty()) {
            EmptyLibraryView(
                hasPermission = hasStoragePermission,
                lastSummary = scanState.lastSummary,
                onRequestPermission = onRequestPermission,
                onScan = onScan,
                onImportFiles = onImportFiles,
            )
            return
        }

        when (filter) {
            LibraryFilter.SONGS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                ) {
                    item(key = "header_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "共 ${tracks.size} 首本地无损/音频",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surface2)
                                    .clickable {
                                        val shuffled = tracks.shuffled()
                                        onPlayTracks(shuffled, 0, "全部随机")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text("随机播放", fontSize = 12.sp, color = colors.textPrimary)
                            }
                        }
                    }
                    itemsIndexed(tracks, key = { _, t -> t.path }) { idx, track ->
                        TrackRowItem(
                            track = track,
                            isCurrent = playback.currentTrack?.path == track.path,
                            onClick = { onPlayTracks(tracks, idx, "全部歌曲") },
                        )
                    }
                }
            }

            LibraryFilter.ALBUMS -> {
                val albums = remember(tracks) {
                    tracks.groupBy { it.album }
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
                val artists = remember(tracks) {
                    tracks.groupBy { it.artist }
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
                                    Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
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
                                Text(
                                    group.subtitle,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            LibraryFilter.FOLDERS -> {
                val folders = remember(tracks) {
                    tracks.groupBy { it.folderPath }
                        .entries
                        .sortedBy { it.key.lowercase() }
                        .map { (folder, list) ->
                            DrillDownGroup(
                                title = File(folder).name.ifBlank { folder },
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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = colors.accent,
                                )
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDetailScreen(
    group: DrillDownGroup,
    currentTrackPath: String?,
    onBack: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalHiFiColors.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = group.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoverSquare(
                    coverPath = group.coverPath,
                    modifier = Modifier.size(180.dp),
                    cornerDp = 12,
                )
                Spacer(Modifier.height(14.dp))
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
                    text = "${group.subtitle} · ${group.tracks.size} 首",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent)
                            .clickable { onPlay(group.tracks, 0) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "全部播放",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onAccent,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surface2)
                            .clickable { onPlay(group.tracks.shuffled(), 0) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("随机", fontSize = 14.sp, color = colors.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        itemsIndexed(group.tracks, key = { _, t -> t.path }) { idx, track ->
            TrackRowItem(
                track = track,
                isCurrent = currentTrackPath == track.path,
                onClick = { onPlay(group.tracks, idx) },
            )
        }
    }
}

@Composable
fun TrackRowItem(
    track: TrackEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = "${track.artist} · ${track.album}",
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Text(
            text = "还没有本地无损音乐",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
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
