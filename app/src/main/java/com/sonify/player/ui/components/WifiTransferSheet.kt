package com.sonify.player.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.sonify.player.data.WifiUploadHistoryEntity
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.wifi.WifiTransferState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryStatusFilter(val label: String, val raw: String) {
    ALL("全部", "ALL"),
    SUCCESS("成功", "SUCCESS"),
    DUPLICATE("重复跳过", "SKIPPED_DUPLICATE"),
    FAILED("失败", "FAILED"),
}

/**
 * 设置页「WiFi 局域网传歌」全功能控制台（Spotify Dark × 黑胶风格）：
 * - 一键启停局域网 HTTP 传歌服务（带前台保活通知）
 * - 展示局域网访问链接、一键复制与 ZXing 高清扫码二维码（手机/PC 浏览器均可直连）
 * - 可选 4 位临时 PIN 访问码（防公共 WiFi 误传）
 * - 实时持久化上传历史（筛选、点击直接播歌、单条/批量删除）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferSheet(
    state: WifiTransferState,
    history: List<WifiUploadHistoryEntity>,
    autoSkipDuplicate: Boolean,
    onToggleServer: (Boolean) -> Unit,
    onRefreshNetwork: () -> Unit,
    onTogglePinEnabled: (Boolean) -> Unit,
    onRegeneratePin: () -> Unit,
    onToggleAutoSkipDuplicate: (Boolean) -> Unit,
    onPlayUploadedFile: (String) -> Unit,
    onDeleteHistoryItem: (Long, Boolean) -> Unit,
    onClearHistory: (Boolean) -> Unit,
    onShowToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var filter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var deleteTarget by remember { mutableStateOf<WifiUploadHistoryEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshNetwork()
    }

    val filteredHistory = remember(history, filter) {
        if (filter == HistoryStatusFilter.ALL) history
        else history.filter { it.status.equals(filter.raw, ignoreCase = true) }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            // 顶部标题与服务开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isRunning) colors.exclusiveGreen.copy(alpha = 0.18f)
                            else colors.surface2
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (state.isRunning) colors.exclusiveGreen else colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "WiFi 局域网传歌",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = if (state.isRunning) {
                            "服务运行中 · 本次成功 ${state.sessionUploadedCount} · 防重跳过 ${state.sessionSkippedCount}"
                        } else {
                            "开启后可在手机或 PC 浏览器直接拖拽上传无损音频"
                        },
                        fontSize = 11.sp,
                        color = if (state.isRunning) colors.exclusiveGreen else colors.textSecondary,
                    )
                }
                Switch(
                    checked = state.isRunning,
                    onCheckedChange = onToggleServer,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.exclusiveGreen,
                    ),
                )
            }

            state.error?.let { err ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = colors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.danger.copy(alpha = 0.14f))
                        .padding(10.dp),
                )
            }

            // 运行中：连接地址 + 扫码二维码 + PIN 码卡片
            if (state.isRunning && state.primaryUrl.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.exclusiveGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "同一 WiFi 下手机扫码或电脑浏览器打开以下地址",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 高清 QR 二维码（白底圆角框便于任意手机瞬间扫码）
                    QrCodeCard(
                        content = state.primaryUrl,
                        modifier = Modifier.size(164.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.canvas.copy(alpha = 0.7f))
                            .clickable {
                                copyToClipboard(context, state.primaryUrl)
                                onShowToast("已复制链接：${state.primaryUrl}")
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.primaryUrl,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制链接",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新 IP",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (state.pinEnabled && state.pinCode.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("临时访问 PIN：", fontSize = 12.sp, color = colors.textSecondary)
                            Text(
                                text = state.pinCode.chunked(1).joinToString(" "),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.accent,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "换一个",
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surface3)
                                    .clickable { onRegeneratePin() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "接收目录：${state.storageDirPath} · 剩余 ${formatFreeBytes(state.freeSpaceBytes)}",
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 传输策略选项：防重 & PIN 码开关
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface2.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("防重复上传自动跳过", fontSize = 13.sp, color = colors.textPrimary)
                        Text("上传前先比对内容指纹与本地曲库，重复文件零流量跳过", fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Switch(
                        checked = autoSkipDuplicate,
                        onCheckedChange = onToggleAutoSkipDuplicate,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("启用 4 位临时 PIN 访问码", fontSize = 13.sp, color = colors.textPrimary)
                        Text("在公共 WiFi 环境下防止他人误打开上传页面", fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Switch(
                        checked = state.pinEnabled,
                        onCheckedChange = onTogglePinEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==================== 上传历史区块 ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上传历史（${history.size}）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("清空历史", fontSize = 12.sp, color = colors.danger)
                    }
                }
            }

            // 状态筛选胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryStatusFilter.entries.forEach { item ->
                    val selected = item == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.accent else colors.surface2)
                            .clickable { filter = item }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.onAccent else colors.textSecondary,
                        )
                    }
                }
            }

            if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无上传历史记录", fontSize = 13.sp, color = colors.textMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(filteredHistory, key = { it.id }) { row ->
                        WifiHistoryRow(
                            item = row,
                            onPlay = {
                                if (row.status == "SUCCESS" && row.savedPath.isNotBlank() && File(row.savedPath).isFile) {
                                    onPlayUploadedFile(row.savedPath)
                                }
                            },
                            onDelete = { deleteTarget = row },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { row ->
        val hasFile = row.status == "SUCCESS" && row.savedPath.isNotBlank() && File(row.savedPath).isFile
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = colors.surface1,
            title = { Text("删除上传记录", color = colors.textPrimary) },
            text = {
                Text(
                    text = if (hasFile) "请选择仅删除「${row.fileName}」的历史记录，或同时删除本地音频文件。"
                    else "确定删除「${row.fileName}」的上传记录吗？",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                Row {
                    if (hasFile) {
                        TextButton(onClick = {
                            onDeleteHistoryItem(row.id, true)
                            deleteTarget = null
                        }) {
                            Text("删记录并删文件", color = colors.danger)
                        }
                    }
                    TextButton(onClick = {
                        onDeleteHistoryItem(row.id, false)
                        deleteTarget = null
                    }) {
                        Text("仅删记录", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surface1,
            title = { Text("清空上传历史", color = colors.textPrimary) },
            text = {
                Text(
                    "仅清空全部历史记录（保留手机上已传好的无损音频文件），确认继续吗？",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearHistory(false)
                }) {
                    Text("确认清空", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun WifiHistoryRow(
    item: WifiUploadHistoryEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val canPlay = item.status == "SUCCESS" &&
        item.savedPath.isNotBlank() &&
        item.format != "LRC" &&
        item.format != "COVER" &&
        File(item.savedPath).isFile

    val (statusLabel, statusColor) = when (item.status) {
        "SUCCESS" -> "成功" to colors.exclusiveGreen
        "SKIPPED_DUPLICATE" -> "重复跳过" to colors.accent
        else -> "失败" to colors.danger
    }
    val timeStr = remember(item.uploadedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.uploadedAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .clickable(enabled = canPlay) { onPlay() }
            .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val spec = if (item.sampleRate > 0) {
                val sr = if (item.sampleRate % 1000 == 0) "${item.sampleRate / 1000}k"
                else "%.1fk".format(item.sampleRate / 1000.0)
                val bits = if (item.bitDepth > 0) "/${item.bitDepth}b" else ""
                "${item.format} $sr$bits"
            } else item.format

            val meta = buildList {
                if (spec.isNotBlank()) add(spec)
                add(formatFreeBytes(item.fileSize))
                if (item.clientInfo.isNotBlank()) add(item.clientInfo)
                add(timeStr)
            }.joinToString(" · ")

            Text(
                text = meta,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status == "SKIPPED_DUPLICATE" && item.duplicateOfName.isNotBlank()) {
                Text(
                    text = "与「${item.duplicateOfName}」内容重复",
                    fontSize = 10.sp,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        Text(
            text = statusLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (canPlay) {
            IconButton(onClick = onPlay, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = colors.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QrCodeCard(
    content: String,
    modifier: Modifier = Modifier,
) {
    val matrix: BitMatrix? = remember(content) {
        runCatching {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 1),
            )
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val mw = matrix.width
                val mh = matrix.height
                val cellW = size.width / mw.toFloat()
                val cellH = size.height / mh.toFloat()
                for (y in 0 until mh) {
                    for (x in 0 until mw) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = Color(0xFF111111),
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW + 0.5f, cellH + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Sonify WiFi URL", text))
}

private fun formatFreeBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "${"%.1f".format(gb)} GB"
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return "${"%.1f".format(mb)} MB"
    return "${(bytes / 1024L).coerceAtLeast(1L)} KB"
}
