package com.sonify.player.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonify.player.R
import com.sonify.player.data.DownloadedTrackEntity
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.download.DownloadQueueState
import com.sonify.player.download.DownloadTaskStatus
import com.sonify.player.library.PlaylistSummary
import com.sonify.player.library.TrackSortField
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.screens.CoverSquare
import com.sonify.player.ui.theme.LocalHiFiColors

/**
 * 新建 / 编辑歌单弹窗：
 * - 支持歌单名称、简介与系统相册自定义封面（支持恢复自动 2×2 四宫格）；
 * - 新建模式且已登录 Subsonic 时，支持选择创建「本地歌单」或「Subsonic 服务器歌单」。
 */
@Composable
fun PlaylistEditorDialog(
    isEdit: Boolean,
    isRemotePlaylist: Boolean = false,
    initialName: String = "",
    initialDescription: String = "",
    initialCustomCoverPath: String? = null,
    autoCovers: List<String> = emptyList(),
    activeRemoteServer: SubsonicServerEntity? = null,
    defaultToRemote: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String,
        pickedCoverUri: Uri?,
        clearCustomCover: Boolean,
        createOnRemoteServer: Boolean,
    ) -> Unit,
) {
    val colors = LocalHiFiColors.current
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var clearCustomCover by remember { mutableStateOf(false) }
    var createRemote by remember { mutableStateOf(defaultToRemote && activeRemoteServer != null && !isEdit) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedCoverUri = uri
            clearCustomCover = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.hairline,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        title = {
            Text(
                text = stringResource(if (isEdit) R.string.playlist_edit_title else R.string.playlist_create_title),
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 非远端歌单支持自定义相册封面
                if (!isRemotePlaylist && !createRemote) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pickedCoverUri != null) {
                                AsyncImage(
                                    model = pickedCoverUri,
                                    contentDescription = stringResource(R.string.playlist_cover_selected),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(76.dp),
                                )
                            } else {
                                PlaylistCoverArt(
                                    customCoverPath = if (clearCustomCover) null else initialCustomCoverPath,
                                    autoCovers = autoCovers,
                                    modifier = Modifier.size(76.dp),
                                    cornerDp = 10,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface2)
                                    .clickable { imagePicker.launch("image/*") }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.playlist_cover_pick), fontSize = 12.sp, color = colors.textPrimary)
                            }
                            if (pickedCoverUri != null || (!clearCustomCover && !initialCustomCoverPath.isNullOrBlank())) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.playlist_cover_reset),
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier
                                        .clickable {
                                            pickedCoverUri = null
                                            clearCustomCover = true
                                        }
                                        .padding(vertical = 2.dp),
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.playlist_cover_hint),
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 新建时若已登录 Subsonic，允许切换目标位置
                if (!isEdit && activeRemoteServer != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TargetChip(
                            label = stringResource(R.string.playlist_type_local),
                            selected = !createRemote,
                            onClick = { createRemote = false },
                            modifier = Modifier.weight(1f),
                        )
                        TargetChip(
                            label = activeRemoteServer.name,
                            selected = createRemote,
                            onClick = { createRemote = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.playlist_name_hint), fontSize = 12.sp) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_desc_label)) },
                    maxLines = 3,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onConfirm(name.trim(), description.trim(), pickedCoverUri, clearCustomCover, createRemote)
                },
            ) {
                Text(
                    text = stringResource(if (isEdit) R.string.common_save else R.string.common_create),
                    color = if (name.trim().isNotEmpty()) colors.accent else colors.textMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun TargetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.2f) else colors.surface2)
            .border(
                1.dp,
                if (selected) colors.accent else colors.hairline,
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「加入歌单」底部弹窗（单曲与批量通用）：
 * - 顶部支持「+ 新建歌单并加入」；
 * - 展示全部本地歌单，以及（当所选曲目全属当前 Subsonic 服务器时）服务器歌单列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    tracksToAdd: List<TrackEntity>,
    localPlaylists: List<PlaylistSummary>,
    remoteServer: SubsonicServerEntity?,
    remotePlaylists: List<SubsonicPlaylist>,
    onDismiss: () -> Unit,
    onCreateNewAndAdd: () -> Unit,
    onPickLocalPlaylist: (PlaylistSummary) -> Unit,
    onPickRemotePlaylist: (SubsonicServerEntity, SubsonicPlaylist) -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allBelongToRemote = remember(tracksToAdd, remoteServer) {
        remoteServer != null &&
            tracksToAdd.isNotEmpty() &&
            tracksToAdd.all { SubsonicUri.parseTrackUri(it.path)?.serverId == remoteServer.id }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (tracksToAdd.size == 1) stringResource(R.string.playlist_add_title)
                        else stringResource(R.string.playlist_batch_add_title, tracksToAdd.size),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    if (tracksToAdd.size == 1) {
                        Text(
                            text = "${tracksToAdd.first().title} · ${tracksToAdd.first().artist}",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 快捷入口：新建歌单并加入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onCreateNewAndAdd()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.playlist_create_and_add),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                if (localPlaylists.isNotEmpty()) {
                    item(key = "local_pl_header") {
                        Text(
                            text = stringResource(R.string.playlist_local_section),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                    items(localPlaylists, key = { "lpl_${it.playlist.id}" }) { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPickLocalPlaylist(summary)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistCoverArt(
                                customCoverPath = summary.playlist.customCoverPath,
                                autoCovers = summary.autoCovers,
                                modifier = Modifier.size(46.dp),
                                cornerDp = 8,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = summary.playlist.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.common_tracks_count, summary.trackCount),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                if (allBelongToRemote && remoteServer != null && remotePlaylists.isNotEmpty()) {
                    item(key = "remote_pl_header") {
                        Text(
                            text = stringResource(R.string.playlist_remote_section, remoteServer.name),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.exclusiveGreen,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(remotePlaylists, key = { "rpl_${it.id}" }) { rpl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPickRemotePlaylist(remoteServer, rpl)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
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
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    tint = colors.exclusiveGreen,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = rpl.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.playlist_remote_meta, rpl.songCount),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 排序选择弹窗（覆盖 默认顺序 / 标题 / 专辑 / 艺人 / 最近添加 + 升/降序） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSortSheet(
    title: String = stringResource(R.string.playlist_sort_title),
    fields: List<TrackSortField>,
    selectedField: TrackSortField,
    ascending: Boolean,
    onSelect: (TrackSortField, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            fields.forEach { field ->
                val isSelected = field == selectedField
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextAsc = if (isSelected) !ascending else field.defaultAscending
                            onSelect(field, nextAsc)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(field.labelRes),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.accent else colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(if (ascending) R.string.playlist_sort_asc else R.string.playlist_sort_desc),
                                fontSize = 12.sp,
                                color = colors.accent,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单曲「⋮」快捷操作菜单 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: TrackEntity,
    isDownloaded: Boolean,
    showRemoveFromPlaylist: Boolean = false,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isRemote = SubsonicUri.isTrackUri(track.path)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverSquare(
                    coverPath = track.coverPath,
                    modifier = Modifier.size(48.dp),
                    cornerDp = 8,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${track.artist} · ${track.album}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ActionMenuRow(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(R.string.common_play_now),
                onClick = { onDismiss(); onPlayNow() },
            )
            ActionMenuRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.playlist_add_title),
                tint = colors.accent,
                onClick = { onDismiss(); onAddToPlaylist() },
            )
            ActionMenuRow(
                icon = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(if (track.isFavorite) R.string.player_unfavorite else R.string.player_favorite_song),
                tint = if (track.isFavorite) colors.accent else colors.textPrimary,
                onClick = { onDismiss(); onToggleFavorite() },
            )
            if (isRemote && !isDownloaded && onDownload != null) {
                ActionMenuRow(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.playlist_action_download_offline),
                    tint = colors.exclusiveGreen,
                    onClick = { onDismiss(); onDownload() },
                )
            }
            if (isRemote && isDownloaded && onDeleteDownload != null) {
                ActionMenuRow(
                    icon = Icons.Rounded.DownloadDone,
                    label = stringResource(R.string.playlist_action_delete_offline),
                    tint = colors.danger,
                    onClick = { onDismiss(); onDeleteDownload() },
                )
            }
            if (showRemoveFromPlaylist && onRemoveFromPlaylist != null) {
                ActionMenuRow(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.playlist_action_remove_here),
                    tint = colors.danger,
                    onClick = { onDismiss(); onRemoveFromPlaylist() },
                )
            }
        }
    }
}

@Composable
private fun ActionMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color = LocalHiFiColors.current.textPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = tint)
    }
}

/** 多选模式底部悬浮操作栏（批量加入歌单 / 批量下载 / 批量从歌单移除） */
@Composable
fun BatchSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    hasRemoteSelected: Boolean,
    showRemoveFromPlaylist: Boolean,
    onToggleSelectAll: () -> Unit,
    onBatchAddToPlaylist: () -> Unit,
    onBatchDownload: (() -> Unit)?,
    onBatchRemoveFromPlaylist: (() -> Unit)?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    val allSelected = totalCount > 0 && selectedCount >= totalCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface2)
            .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.playlist_selected_count, selectedCount, totalCount),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleSelectAll() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SelectAll,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(if (allSelected) R.string.common_deselect_all else R.string.common_select_all),
                    fontSize = 12.sp,
                    color = colors.accent,
                )
            }
            IconButton(onClick = onExit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.playlist_exit_multi), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatchActionButton(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.playlist_add_title),
                enabled = selectedCount > 0,
                accentBg = true,
                onClick = onBatchAddToPlaylist,
                modifier = Modifier.weight(1f),
            )
            if (hasRemoteSelected && onBatchDownload != null) {
                BatchActionButton(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.common_download),
                    enabled = selectedCount > 0,
                    tint = colors.exclusiveGreen,
                    onClick = onBatchDownload,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showRemoveFromPlaylist && onBatchRemoveFromPlaylist != null) {
                BatchActionButton(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.playlist_remove_from),
                    enabled = selectedCount > 0,
                    tint = colors.danger,
                    onClick = onBatchRemoveFromPlaylist,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accentBg: Boolean = false,
    tint: Color = LocalHiFiColors.current.textPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    val bg = when {
        !enabled -> colors.surface3.copy(alpha = 0.5f)
        accentBg -> colors.accent
        else -> colors.surface3
    }
    val fg = when {
        !enabled -> colors.textMuted
        accentBg -> colors.onAccent
        else -> tint
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
        )
    }
}

/** 歌单详情内「+ 添加歌曲」多选选歌器（支持搜索与勾选本地/远程歌曲） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTracksPickerSheet(
    playlistName: String,
    existingPaths: Set<String>,
    candidateTracks: List<TrackEntity>,
    onDismiss: () -> Unit,
    onConfirmAdd: (List<TrackEntity>) -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val selectedPaths = remember { mutableStateListOf<String>() }

    val filtered = remember(candidateTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) candidateTracks
        else candidateTracks.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.playlist_picker_title, playlistName),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.playlist_picker_search), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_clear), tint = colors.textSecondary)
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.playlist_picker_empty), fontSize = 13.sp, color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(filtered, key = { it.path }) { track ->
                        val alreadyIn = existingPaths.contains(track.path)
                        val checked = selectedPaths.contains(track.path)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyIn) {
                                    if (checked) selectedPaths.remove(track.path)
                                    else selectedPaths.add(track.path)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when {
                                    alreadyIn -> Icons.Rounded.CheckCircle
                                    checked -> Icons.Rounded.CheckBox
                                    else -> Icons.Rounded.CheckBoxOutlineBlank
                                },
                                contentDescription = null,
                                tint = when {
                                    alreadyIn -> colors.textMuted
                                    checked -> colors.accent
                                    else -> colors.textSecondary
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            CoverSquare(
                                coverPath = track.coverPath,
                                modifier = Modifier.size(40.dp),
                                cornerDp = 6,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 14.sp,
                                    color = if (alreadyIn) colors.textMuted else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (alreadyIn) stringResource(R.string.playlist_picker_already_in, track.artist)
                                    else "${track.artist} · ${track.album}",
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

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selectedPaths.isNotEmpty()) colors.accent else colors.surface3)
                    .clickable(enabled = selectedPaths.isNotEmpty()) {
                        val set = selectedPaths.toSet()
                        val chosen = candidateTracks.filter { it.path in set }
                        onConfirmAdd(chosen)
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selectedPaths.isEmpty()) stringResource(R.string.playlist_picker_prompt)
                    else stringResource(R.string.playlist_picker_confirm, selectedPaths.size),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedPaths.isNotEmpty()) colors.onAccent else colors.textMuted,
                )
            }
        }
    }
}

/** 离线下载队列与已下载空间管理面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueSheet(
    queueState: DownloadQueueState,
    downloadedList: List<DownloadedTrackEntity>,
    onRetry: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClearAll by remember { mutableStateOf(false) }

    val totalBytes = remember(downloadedList) { downloadedList.sumOf { it.fileSize.coerceAtLeast(0L) } }
    val sizeLabel = remember(totalBytes) {
        val mb = totalBytes / (1024.0 * 1024.0)
        when {
            mb >= 1024.0 -> "${"%.2f".format(mb / 1024.0)} GB"
            mb >= 1.0 -> "${"%.1f".format(mb)} MB"
            totalBytes > 0L -> "${(totalBytes / 1024L).coerceAtLeast(1L)} KB"
            else -> "0 KB"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.exclusiveGreen)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.playlist_dl_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(
                        text = stringResource(R.string.playlist_dl_summary, downloadedList.size, sizeLabel),
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
                if (queueState.activeCount > 0) {
                    TextButton(onClick = onCancelAll) {
                        Text(stringResource(R.string.playlist_dl_cancel_all), color = colors.danger, fontSize = 12.sp)
                    }
                } else if (queueState.tasks.isNotEmpty()) {
                    TextButton(onClick = onClearFinished) {
                        Text(stringResource(R.string.playlist_dl_clear_done), color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (queueState.tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.playlist_dl_no_active),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(queueState.tasks, key = { it.track.path }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.track.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val statusText = when (item.status) {
                                        DownloadTaskStatus.QUEUED -> stringResource(R.string.playlist_dl_queued)
                                        DownloadTaskStatus.DOWNLOADING -> {
                                            val pct = (item.progress * 100f).toInt()
                                            if (item.totalBytes > 0) stringResource(R.string.playlist_dl_progress, pct)
                                            else stringResource(R.string.playlist_dl_downloading)
                                        }
                                        DownloadTaskStatus.COMPLETED -> stringResource(R.string.playlist_dl_done)
                                        DownloadTaskStatus.FAILED -> item.error ?: stringResource(R.string.playlist_dl_failed)
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 11.sp,
                                        color = when (item.status) {
                                            DownloadTaskStatus.COMPLETED -> colors.exclusiveGreen
                                            DownloadTaskStatus.FAILED -> colors.danger
                                            else -> colors.textSecondary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                when (item.status) {
                                    DownloadTaskStatus.FAILED -> {
                                        IconButton(onClick = { onRetry(item.track.path) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_retry), tint = colors.accent, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    DownloadTaskStatus.QUEUED, DownloadTaskStatus.DOWNLOADING -> {
                                        IconButton(onClick = { onCancelTask(item.track.path) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    DownloadTaskStatus.COMPLETED -> {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.exclusiveGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            if (item.status == DownloadTaskStatus.DOWNLOADING) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    color = colors.exclusiveGreen,
                                    trackColor = colors.surface3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                    }
                }
            }

            if (downloadedList.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.danger.copy(alpha = 0.12f))
                        .clickable { confirmClearAll = true }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.playlist_dl_clear_all_btn, sizeLabel),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.danger,
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = colors.surface1,
            title = { Text(stringResource(R.string.playlist_dl_clear_dialog_title), color = colors.textPrimary) },
            text = {
                Text(
                    stringResource(R.string.playlist_dl_clear_dialog_msg, downloadedList.size, sizeLabel),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onDeleteAllDownloads()
                }) {
                    Text(stringResource(R.string.playlist_dl_clear_confirm), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
        )
    }
}
