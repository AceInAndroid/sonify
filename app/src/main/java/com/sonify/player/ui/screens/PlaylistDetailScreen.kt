package com.sonify.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.DownloadedTrackEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSorter
import com.sonify.player.library.SortableTrack
import com.sonify.player.library.TrackSortField
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.components.BatchSelectionBar
import com.sonify.player.ui.components.PlaylistCoverArt
import com.sonify.player.ui.components.TrackSortSheet
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration

/**
 * 统一歌单详情页（复用于本地自建歌单 & Subsonic 服务端歌单）：
 * - 封面主色氛围渐变头图 + 自定义封面/2×2 四宫格封面；
 * - 歌单顺序播放 / 歌单随机播放 / 歌单全部下载 / + 添加歌曲 / 排序（默认/标题/专辑/艺人/最近添加）；
 * - 单曲菜单 / 长按多选批量移出歌单、批量加入其他歌单、批量下载 / 自定义顺序上下移位；
 * - 编辑歌单与删除歌单。
 */
@Composable
fun PlaylistDetailScreen(
    title: String,
    description: String,
    isRemotePlaylist: Boolean,
    customCoverPath: String?,
    autoCovers: List<String>,
    items: List<SortableTrack>,
    sortField: TrackSortField,
    sortAscending: Boolean,
    isLoading: Boolean = false,
    error: String? = null,
    currentTrackPath: String?,
    downloadsByPath: Map<String, DownloadedTrackEntity>,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onShufflePlay: (List<TrackEntity>, String) -> Unit,
    onChangeSort: (TrackSortField, Boolean) -> Unit,
    onEditPlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onOpenAddTracks: () -> Unit,
    onDownloadPlaylist: (List<TrackEntity>) -> Unit,
    onMoveTrack: ((trackPath: String, moveUp: Boolean) -> Unit)? = null,
    onTrackMenu: (TrackEntity) -> Unit,
    onBatchAddToPlaylist: (List<TrackEntity>) -> Unit,
    onBatchDownload: (List<TrackEntity>) -> Unit,
    onBatchRemoveFromPlaylist: (List<SortableTrack>) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    var showSortSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    val selectedKeys = remember { mutableStateListOf<String>() }

    val sortedItems = remember(items, sortField, sortAscending) {
        PlaylistSorter.sortTracks(items, sortField, sortAscending)
    }
    val sortedTracks = remember(sortedItems) { sortedItems.map { it.track } }

    val remoteTracks = remember(sortedTracks) {
        sortedTracks.filter { SubsonicUri.isTrackUri(it.path) }
    }
    val downloadedRemoteCount = remember(remoteTracks, downloadsByPath) {
        remoteTracks.count { downloadsByPath.containsKey(it.path) }
    }
    val allRemoteDownloaded = remoteTracks.isNotEmpty() && downloadedRemoteCount >= remoteTracks.size

    val headerCoverForAmbient = customCoverPath ?: autoCovers.firstOrNull()
    var ambientColor by remember(headerCoverForAmbient) { mutableStateOf(AmbientColorExtractor.DefaultAmbient) }
    LaunchedEffect(headerCoverForAmbient) {
        ambientColor = AmbientColorExtractor.extract(headerCoverForAmbient)
    }

    BackHandler(enabled = selectionMode || reorderMode) {
        if (selectionMode) {
            selectionMode = false
            selectedKeys.clear()
        } else {
            reorderMode = false
        }
    }

    val totalDurationMs = remember(sortedTracks) { sortedTracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航与操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ambientColor.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (selectionMode) {
                        selectionMode = false
                        selectedKeys.clear()
                    } else onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isRemotePlaylist && onMoveTrack != null && sortField == TrackSortField.CUSTOM && sortedItems.size > 1) {
                    IconButton(onClick = {
                        selectionMode = false
                        selectedKeys.clear()
                        reorderMode = !reorderMode
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = "调整顺序",
                            tint = if (reorderMode) colors.accent else colors.textPrimary,
                        )
                    }
                }
                if (sortedItems.isNotEmpty()) {
                    IconButton(onClick = {
                        reorderMode = false
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedKeys.clear()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Checklist,
                            contentDescription = "批量管理",
                            tint = if (selectionMode) colors.accent else colors.textPrimary,
                        )
                    }
                }
                IconButton(onClick = onEditPlaylist) {
                    Icon(Icons.Rounded.Edit, contentDescription = "编辑歌单", tint = colors.textPrimary)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除歌单", tint = colors.textSecondary)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
                }
                return@Column
            }

            if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error, fontSize = 13.sp, color = colors.danger)
                    if (onRetry != null) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.accent)
                                .clickable { onRetry() }
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        ) {
                            Text("重试", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                        }
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = bottomPadding + if (selectionMode) 118.dp else 20.dp
                ),
            ) {
                // Spotify 风格氛围渐变头图
                item(key = "pl_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to ambientColor.copy(alpha = 0.65f),
                                    0.85f to colors.canvas,
                                    1.0f to colors.canvas,
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistCoverArt(
                                customCoverPath = customCoverPath,
                                autoCovers = autoCovers,
                                modifier = Modifier.size(112.dp),
                                cornerDp = 10,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = description,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                val mins = (totalDurationMs / 60_000L).coerceAtLeast(0L)
                                val metaText = buildString {
                                    append(if (isRemotePlaylist) "服务器歌单 · " else "本地歌单 · ")
                                    append("${sortedTracks.size} 首")
                                    if (mins > 0) append(" · 约 $mins 分钟")
                                }
                                Text(
                                    text = metaText,
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 主控操作栏：左侧胶囊区可横向轻滑，确保右侧随机键与 46dp 圆形播放键在任意屏宽下绝不被挤压变形
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // + 添加歌曲
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable { onOpenAddTracks() }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("加歌", fontSize = 12.sp, color = colors.textPrimary, maxLines = 1, softWrap = false)
                                }

                                // 歌单排序胶囊
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surface2)
                                        .clickable { showSortSheet = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    val arrow = if (sortAscending) "↑" else "↓"
                                    Text("${sortField.label} $arrow", fontSize = 12.sp, color = colors.textPrimary, maxLines = 1, softWrap = false)
                                }

                                // 歌单下载按钮（仅含 Subsonic 远程曲目时展示）
                                if (remoteTracks.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (allRemoteDownloaded) colors.exclusiveGreen.copy(alpha = 0.16f)
                                                else colors.surface2
                                            )
                                            .clickable { onDownloadPlaylist(remoteTracks) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (allRemoteDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                            contentDescription = "下载歌单",
                                            tint = if (allRemoteDownloaded) colors.exclusiveGreen else colors.textPrimary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (allRemoteDownloaded) "已离线"
                                            else if (downloadedRemoteCount > 0) "$downloadedRemoteCount/${remoteTracks.size}"
                                            else "下载",
                                            fontSize = 11.sp,
                                            color = if (allRemoteDownloaded) colors.exclusiveGreen else colors.textPrimary,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(6.dp))

                            // 歌单随机播放
                            IconButton(
                                enabled = sortedTracks.isNotEmpty(),
                                onClick = { onShufflePlay(sortedTracks, title) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "歌单随机播放",
                                    tint = if (sortedTracks.isNotEmpty()) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // 歌单大圆播放键
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (sortedTracks.isNotEmpty()) colors.accent else colors.surface3)
                                    .clickable(enabled = sortedTracks.isNotEmpty()) {
                                        onPlayTracks(sortedTracks, 0, title)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "播放歌单",
                                    tint = if (sortedTracks.isNotEmpty()) colors.onAccent else colors.textMuted,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }

                        if (reorderMode) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "排序微调中：点击右侧上下箭头调整顺序，再次点击顶栏排序图标完成",
                                fontSize = 11.sp,
                                color = colors.accent,
                            )
                        }
                    }
                }

                if (sortedItems.isEmpty()) {
                    item(key = "pl_empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("歌单里还没有歌曲", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text("点击下方按钮从音乐库挑选歌曲加入", fontSize = 12.sp, color = colors.textSecondary)
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.accent)
                                    .clickable { onOpenAddTracks() }
                                    .padding(horizontal = 22.dp, vertical = 10.dp)
                            ) {
                                Text("添加歌曲", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                            }
                        }
                    }
                } else {
                    itemsIndexed(sortedItems, key = { idx, item -> "${idx}_${item.track.path}" }) { idx, item ->
                        val track = item.track
                        val itemKey = "${item.position}_${track.path}"
                        val isSelected = selectedKeys.contains(itemKey)
                        val isCurrent = currentTrackPath == track.path
                        val isDownloaded = downloadsByPath.containsKey(track.path)

                        PlaylistTrackRow(
                            track = track,
                            isCurrent = isCurrent,
                            isDownloaded = isDownloaded,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            reorderMode = reorderMode,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < sortedItems.lastIndex,
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selectedKeys.remove(itemKey) else selectedKeys.add(itemKey)
                                } else if (!reorderMode) {
                                    onPlayTracks(sortedTracks, idx, title)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    reorderMode = false
                                    selectionMode = true
                                    selectedKeys.clear()
                                    selectedKeys.add(itemKey)
                                }
                            },
                            onMoreClick = { onTrackMenu(track) },
                            onMoveUp = { onMoveTrack?.invoke(track.path, true) },
                            onMoveDown = { onMoveTrack?.invoke(track.path, false) },
                        )
                    }
                }
            }
        }

        // 多选模式底部悬浮批量操作条
        if (selectionMode) {
            val selectedItems = sortedItems.filter { "${it.position}_${it.track.path}" in selectedKeys }
            val selectedTracks = selectedItems.map { it.track }
            BatchSelectionBar(
                selectedCount = selectedItems.size,
                totalCount = sortedItems.size,
                hasRemoteSelected = selectedTracks.any { SubsonicUri.isTrackUri(it.path) },
                showRemoveFromPlaylist = true,
                onToggleSelectAll = {
                    if (selectedKeys.size >= sortedItems.size) {
                        selectedKeys.clear()
                    } else {
                        selectedKeys.clear()
                        selectedKeys.addAll(sortedItems.map { "${it.position}_${it.track.path}" })
                    }
                },
                onBatchAddToPlaylist = {
                    onBatchAddToPlaylist(selectedTracks)
                    selectionMode = false
                    selectedKeys.clear()
                },
                onBatchDownload = {
                    onBatchDownload(selectedTracks)
                    selectionMode = false
                    selectedKeys.clear()
                },
                onBatchRemoveFromPlaylist = {
                    onBatchRemoveFromPlaylist(selectedItems)
                    selectionMode = false
                    selectedKeys.clear()
                },
                onExit = {
                    selectionMode = false
                    selectedKeys.clear()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding + 6.dp),
            )
        }
    }

    if (showSortSheet) {
        TrackSortSheet(
            title = "歌单曲目排序",
            fields = TrackSortField.entries,
            selectedField = sortField,
            ascending = sortAscending,
            onSelect = { f, asc ->
                if (f != TrackSortField.CUSTOM) reorderMode = false
                onChangeSort(f, asc)
            },
            onDismiss = { showSortSheet = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface1,
            title = { Text("删除歌单", color = colors.textPrimary) },
            text = {
                Text(
                    text = if (isRemotePlaylist) {
                        "确定删除服务器歌单「$title」吗？此操作将同步到 Subsonic 服务器，不会删除歌曲原文件。"
                    } else {
                        "确定删除歌单「$title」吗？此操作仅移除歌单，不会删除任何本地或远程音频文件。"
                    },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeletePlaylist()
                }) {
                    Text("确认删除", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
            modifier = Modifier.size(46.dp),
            cornerDp = 6,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
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
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = track.formatBadgeShort(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (track.isHiRes) colors.accent else colors.textSecondary,
            )
            if (track.durationMs > 0) {
                Text(
                    text = formatDuration(track.durationMs),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
            }
        }
        when {
            reorderMode -> {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "上移",
                        tint = if (canMoveUp) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "下移",
                        tint = if (canMoveDown) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            !selectionMode -> {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
