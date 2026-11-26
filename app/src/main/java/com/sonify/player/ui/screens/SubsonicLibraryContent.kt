package com.sonify.player.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.subsonic.SubsonicAlbum
import com.sonify.player.subsonic.SubsonicArtist
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.ui.SubsonicBrowseUiState
import com.sonify.player.ui.SubsonicDrillState
import com.sonify.player.ui.SubsonicTab
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors

/**
 * 音乐库内嵌的 Subsonic 远程按需浏览内容（Spotify Dark 风格）：
 * - 支持最新专辑 / 全部专辑（滚动分页）/ 艺术家 / 歌单 / 已收藏 / 随机发现；
 * - 支持多音乐目录（musicFolders）筛选、实时 search3 搜索与专辑/艺术家/歌单二级钻取播放。
 */
@Composable
fun SubsonicLibraryContent(
    server: SubsonicServerEntity,
    state: SubsonicBrowseUiState,
    currentTrackPath: String?,
    onSelectTab: (SubsonicTab) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onOpenAlbum: (SubsonicAlbum) -> Unit,
    onOpenArtist: (SubsonicArtist) -> Unit,
    onOpenPlaylist: (SubsonicPlaylist) -> Unit,
    onCloseDrill: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onOpenAccounts: () -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current

    // ==================== 二级钻取详情页 ====================
    when (val drill = state.drill) {
        is SubsonicDrillState.AlbumDetail -> {
            SubsonicTrackGroupDetail(
                title = drill.album.name,
                subtitle = listOfNotNull(
                    drill.album.artist.takeIf { it.isNotBlank() },
                    drill.album.year.takeIf { it > 0 }?.toString(),
                    "${drill.tracks.size.takeIf { it > 0 } ?: drill.album.songCount} 首",
                ).joinToString(" · "),
                coverPath = drill.album.coverUri(server.id),
                tracks = drill.tracks,
                isLoading = drill.isLoading,
                error = drill.error,
                currentTrackPath = currentTrackPath,
                onBack = onCloseDrill,
                onRetry = { onOpenAlbum(drill.album) },
                onPlay = { list, idx -> onPlayTracks(list, idx, "${server.name} · ${drill.album.name}") },
                bottomPadding = bottomPadding,
            )
            return
        }

        is SubsonicDrillState.PlaylistDetail -> {
            SubsonicTrackGroupDetail(
                title = drill.playlist.name,
                subtitle = listOfNotNull(
                    drill.playlist.owner.takeIf { it.isNotBlank() }?.let { "创建者 $it" },
                    "${drill.tracks.size.takeIf { it > 0 } ?: drill.playlist.songCount} 首",
                ).joinToString(" · "),
                coverPath = drill.playlist.coverUri(server.id),
                tracks = drill.tracks,
                isLoading = drill.isLoading,
                error = drill.error,
                currentTrackPath = currentTrackPath,
                onBack = onCloseDrill,
                onRetry = { onOpenPlaylist(drill.playlist) },
                onPlay = { list, idx -> onPlayTracks(list, idx, "${server.name} · ${drill.playlist.name}") },
                bottomPadding = bottomPadding,
            )
            return
        }

        is SubsonicDrillState.ArtistDetail -> {
            SubsonicArtistDetail(
                server = server,
                artist = drill.artist,
                albums = drill.albums,
                isLoading = drill.isLoading,
                error = drill.error,
                onBack = onCloseDrill,
                onRetry = { onOpenArtist(drill.artist) },
                onOpenAlbum = onOpenAlbum,
                bottomPadding = bottomPadding,
            )
            return
        }

        SubsonicDrillState.None -> Unit
    }

    // ==================== 搜索模式 ====================
    if (state.searchActive) {
        SubsonicSearchContent(
            server = server,
            state = state,
            currentTrackPath = currentTrackPath,
            onQueryChange = onUpdateSearchQuery,
            onCloseSearch = onCloseSearch,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onPlayTracks = { list, idx -> onPlayTracks(list, idx, "${server.name} · 搜索结果") },
            bottomPadding = bottomPadding,
        )
        return
    }

    // ==================== 常规栏目浏览 ====================
    Column(modifier = Modifier.fillMaxSize()) {
        // 栏目横向胶囊
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SubsonicTab.entries) { tab ->
                val selected = tab == state.selectedTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) colors.accent else colors.surface2)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) colors.onAccent else colors.textPrimary,
                    )
                }
            }
        }

        // 多音乐目录筛选条（仅服务器存在 >1 个 musicFolder 时展示）
        if (state.musicFolders.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "folder_all") {
                    FolderPill(
                        label = "全部目录",
                        selected = state.selectedFolderId == null,
                        onClick = { onSelectFolder(null) },
                    )
                }
                items(state.musicFolders, key = { it.id }) { folder ->
                    FolderPill(
                        label = folder.name,
                        selected = state.selectedFolderId == folder.id,
                        onClick = { onSelectFolder(folder.id) },
                    )
                }
            }
        }

        // 错误提示态
        if (state.error != null) {
            SubsonicErrorState(
                message = state.error,
                onRetry = onRefresh,
                onManageAccount = onOpenAccounts,
            )
            return
        }

        // 首次加载中
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("正在从 ${server.name} 拉取曲库…", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            return
        }

        when (state.selectedTab) {
            SubsonicTab.NEWEST, SubsonicTab.ALL_ALBUMS -> {
                SubsonicAlbumGrid(
                    serverId = server.id,
                    albums = state.albums,
                    isLoadingMore = state.isLoadingMoreAlbums,
                    hasMore = state.hasMoreAlbums,
                    onLoadMore = onLoadMoreAlbums,
                    onOpenAlbum = onOpenAlbum,
                    bottomPadding = bottomPadding,
                )
            }

            SubsonicTab.ARTISTS -> {
                if (state.artists.isEmpty()) {
                    EmptyRemoteHint("服务器中暂无艺术家")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        items(state.artists, key = { it.id }) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenArtist(artist) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val model = CoverArtSupport.resolveCoilModel(artist.coverUri(server.id))
                                    if (model != null) {
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${artist.albumCount} 张专辑",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SubsonicTab.PLAYLISTS -> {
                if (state.playlists.isEmpty()) {
                    EmptyRemoteHint("服务器中暂无歌单")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        items(state.playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlaylist(pl) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val cover = pl.coverUri(server.id)
                                if (cover != null) {
                                    CoverSquare(
                                        coverPath = cover,
                                        modifier = Modifier.size(50.dp),
                                        cornerDp = 8,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surface2),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = listOfNotNull(
                                        "${pl.songCount} 首",
                                        pl.owner.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ")
                                    Text(
                                        text = sub,
                                        fontSize = 12.sp,
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

            SubsonicTab.STARRED -> {
                if (state.starredTracks.isEmpty() && state.starredAlbums.isEmpty()) {
                    EmptyRemoteHint("还没有在服务器收藏歌曲或专辑")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        if (state.starredAlbums.isNotEmpty()) {
                            item(key = "starred_albums_title") {
                                Text(
                                    text = "收藏专辑（${state.starredAlbums.size}）",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            item(key = "starred_albums_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(state.starredAlbums, key = { it.id }) { album ->
                                        Column(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .clickable { onOpenAlbum(album) }
                                        ) {
                                            CoverSquare(
                                                coverPath = album.coverUri(server.id),
                                                modifier = Modifier.size(120.dp),
                                                cornerDp = 8,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = album.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = album.artist,
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
                        if (state.starredTracks.isNotEmpty()) {
                            item(key = "starred_songs_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "收藏歌曲（${state.starredTracks.size}）",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.surface2)
                                            .clickable {
                                                onPlayTracks(state.starredTracks, 0, "${server.name} · 已收藏")
                                            }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("全部播放", fontSize = 12.sp, color = colors.textPrimary)
                                    }
                                }
                            }
                            itemsIndexed(state.starredTracks, key = { _, t -> t.path }) { idx, track ->
                                TrackRowItem(
                                    track = track,
                                    isCurrent = currentTrackPath == track.path,
                                    onClick = { onPlayTracks(state.starredTracks, idx, "${server.name} · 已收藏") },
                                )
                            }
                        }
                    }
                }
            }

            SubsonicTab.RANDOM -> {
                if (state.randomTracks.isEmpty()) {
                    EmptyRemoteHint("未能从服务器获取随机曲目")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    ) {
                        item(key = "random_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "为您随机挑选 ${state.randomTracks.size} 首",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.surface2)
                                            .clickable { onRefresh() }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("换一批", fontSize = 12.sp, color = colors.textPrimary)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.accent)
                                            .clickable {
                                                onPlayTracks(state.randomTracks, 0, "${server.name} · 随机发现")
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            tint = colors.onAccent,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "播放",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onAccent,
                                        )
                                    }
                                }
                            }
                        }
                        itemsIndexed(state.randomTracks, key = { _, t -> t.path }) { idx, track ->
                            TrackRowItem(
                                track = track,
                                isCurrent = currentTrackPath == track.path,
                                onClick = { onPlayTracks(state.randomTracks, idx, "${server.name} · 随机发现") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.surface3 else colors.surface1)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.55f) else colors.hairline,
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textMuted,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Composable
private fun SubsonicAlbumGrid(
    serverId: Long,
    albums: List<SubsonicAlbum>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (SubsonicAlbum) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    if (albums.isEmpty()) {
        EmptyRemoteHint("服务器中暂无专辑")
        return
    }
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(albums.size, hasMore, isLoadingMore) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && albums.isNotEmpty() && lastVisible >= albums.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
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
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAlbum(album) }
            ) {
                CoverSquare(
                    coverPath = album.coverUri(serverId),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerDp = 8,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    album.artist.takeIf { it.isNotBlank() },
                    album.songCount.takeIf { it > 0 }?.let { "${it} 首" },
                ).joinToString(" · ")
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsonicSearchContent(
    server: SubsonicServerEntity,
    state: SubsonicBrowseUiState,
    currentTrackPath: String?,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenAlbum: (SubsonicAlbum) -> Unit,
    onOpenArtist: (SubsonicArtist) -> Unit,
    onPlayTracks: (List<TrackEntity>, Int) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("在 ${server.name} 中搜索歌曲、专辑、艺术家…", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary)
                },
                trailingIcon = {
                    IconButton(onClick = {
                        if (state.searchQuery.isNotEmpty()) onQueryChange("") else onCloseSearch()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭搜索", tint = colors.textSecondary)
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
            }
            return
        }

        state.searchError?.let { err ->
            EmptyRemoteHint(err)
            return
        }

        if (state.searchQuery.isBlank()) {
            EmptyRemoteHint("输入关键词实时检索服务器曲库")
            return
        }

        val res = state.searchResult
        if (state.searchTracks.isEmpty() && res.albums.isEmpty() && res.artists.isEmpty()) {
            EmptyRemoteHint("未找到与「${state.searchQuery}」匹配的结果")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        ) {
            if (res.artists.isNotEmpty()) {
                item(key = "s_artists_h") {
                    Text(
                        text = "艺术家（${res.artists.size}）",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "s_artists_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(res.artists, key = { it.id }) { artist ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surface2)
                                    .clickable { onOpenArtist(artist) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(artist.name, fontSize = 13.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }

            if (res.albums.isNotEmpty()) {
                item(key = "s_albums_h") {
                    Text(
                        text = "专辑（${res.albums.size}）",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "s_albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(res.albums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .width(116.dp)
                                    .clickable { onOpenAlbum(album) }
                            ) {
                                CoverSquare(
                                    coverPath = album.coverUri(server.id),
                                    modifier = Modifier.size(116.dp),
                                    cornerDp = 8,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = album.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.artist,
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

            if (state.searchTracks.isNotEmpty()) {
                item(key = "s_songs_h") {
                    Text(
                        text = "歌曲（${state.searchTracks.size}）",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(state.searchTracks, key = { _, t -> t.path }) { idx, track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = currentTrackPath == track.path,
                        onClick = { onPlayTracks(state.searchTracks, idx) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsonicTrackGroupDetail(
    title: String,
    subtitle: String,
    coverPath: String?,
    tracks: List<TrackEntity>,
    isLoading: Boolean,
    error: String?,
    currentTrackPath: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
            }
            return
        }

        if (error != null) {
            SubsonicErrorState(message = error, onRetry = onRetry, onManageAccount = null)
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        ) {
            item(key = "detail_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverSquare(
                        coverPath = coverPath,
                        modifier = Modifier.size(96.dp),
                        cornerDp = 10,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        if (tracks.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.accent)
                                        .clickable { onPlay(tracks, 0) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = colors.onAccent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "播放全部",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onAccent,
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable { onPlay(tracks.shuffled(), 0) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("随机", fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.path }) { idx, track ->
                TrackRowItem(
                    track = track,
                    isCurrent = currentTrackPath == track.path,
                    onClick = { onPlay(tracks, idx) },
                )
            }
        }
    }
}

@Composable
private fun SubsonicArtistDetail(
    server: SubsonicServerEntity,
    artist: SubsonicArtist,
    albums: List<SubsonicAlbum>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (SubsonicAlbum) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${albums.size.takeIf { it > 0 } ?: artist.albumCount} 张专辑",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
            }
            return
        }
        if (error != null) {
            SubsonicErrorState(message = error, onRetry = onRetry, onManageAccount = null)
            return
        }

        SubsonicAlbumGrid(
            serverId = server.id,
            albums = albums,
            isLoadingMore = false,
            hasMore = false,
            onLoadMore = {},
            onOpenAlbum = onOpenAlbum,
            bottomPadding = bottomPadding,
        )
    }
}

@Composable
private fun SubsonicErrorState(
    message: String,
    onRetry: () -> Unit,
    onManageAccount: (() -> Unit)?,
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
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = colors.danger.copy(alpha = 0.85f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "无法连接媒体服务器",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent)
                    .clickable { onRetry() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("重试连接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
            }
            if (onManageAccount != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface2)
                        .clickable { onManageAccount() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("检查账号", fontSize = 13.sp, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun EmptyRemoteHint(message: String) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(message, fontSize = 13.sp, color = colors.textSecondary)
        }
    }
}
