package com.sonify.player.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.AppSettings
import com.sonify.player.data.LyricsAlignment
import com.sonify.player.data.LyricsFontSize
import com.sonify.player.data.TrackEntity
import com.sonify.player.lyrics.LrcParser
import com.sonify.player.lyrics.OnlineLyricCandidate
import com.sonify.player.ui.LyricsUiState
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.screens.CoverSquare
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spotify Dark × 黑胶美学全屏歌词视图（内嵌于 NowPlayingScreen）：
 * - 共享播放页封面动态氛围渐变背景，顶部紧凑曲目头 + 来源微标；
 * - 逐句丝滑居中滚动高亮（支持双语原文+译文）、上下柔化边缘遮罩；
 * - 点击任意一句歌词立即 Seek 跳播；手指自由滑动暂停自动滚屏 4 秒并浮现「回到当前句」胶囊；
 * - 底部提供时间偏移微调（±0.5s / ±0.1s / 归零）、字号/对齐即时调节、搜词/换词弹窗与紧凑播控。
 */
@Composable
fun LyricsOverlayView(
    track: TrackEntity,
    playback: PlaybackUiState,
    lyricsState: LyricsUiState,
    settings: AppSettings,
    topAmbientColor: Color,
    onBackToVinyl: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onChangeFontSize: (LyricsFontSize) -> Unit,
    onChangeAlignment: (LyricsAlignment) -> Unit,
    onSearchOnline: (String) -> Unit,
    onApplyCandidate: (OnlineLyricCandidate) -> Unit,
    onImportLocalLrc: (Uri) -> Unit,
    onClearOverride: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showOffsetPanel by remember { mutableStateOf(false) }
    var showStylePanel by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var userDraggingPause by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableStateOf(0) }

    val doc = lyricsState.document
    val activeIndex = remember(doc, playback.positionMs) {
        if (doc != null && doc.isSynced) {
            LrcParser.findActiveIndex(doc.lines, playback.positionMs, doc.offsetMs)
        } else -1
    }

    // 手指滑动歌词列表时暂停自动居中 4 秒
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userDraggingPause = true
        } else if (userDraggingPause) {
            delay(4000)
            userDraggingPause = false
        }
    }

    // 自动平滑居中滚动至当前演唱句（约视野 34% 黄金阅读位置）
    LaunchedEffect(activeIndex, userDraggingPause, doc?.lines?.size) {
        if (!userDraggingPause && activeIndex >= 0 && doc != null && doc.isSynced) {
            val offsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * 0.34f).toInt() else -240
            runCatching {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = offsetPx)
            }
        }
    }

    val durationMs = playback.durationMs.coerceAtLeast(1L)
    val progressFrac = (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val textAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) TextAlign.Center else TextAlign.Start
    val hAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start

    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportLocalLrc(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // 顶部紧凑曲目头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverSquare(
                coverPath = track.coverPath,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBackToVinyl() },
                cornerDp = 6,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (doc != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = doc.source.label,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent.copy(alpha = 0.16f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onBackToVinyl() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("黑胶", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
            }
        }

        // 中央歌词主体区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            when {
                lyricsState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("正在查找匹配歌词…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }

                doc == null || doc.isEmpty -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = colors.accent.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "暂未找到歌词",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "可在线检索免费开源歌词库（LRCLIB），或直接导入本地 .lrc 歌词文件",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.accent)
                                    .clickable { showSearchSheet = true }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("在线搜词", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { lrcPicker.launch(arrayOf("*/*")) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("导入 .lrc", fontSize = 13.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 48.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!doc.isSynced) {
                            item(key = "unsynced_banner") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("纯文本歌词（无时间轴）", fontSize = 11.sp, color = colors.textSecondary)
                                    Text(
                                        text = "搜同步词",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.accent,
                                        modifier = Modifier.clickable { showSearchSheet = true },
                                    )
                                }
                            }
                        }

                        itemsIndexed(doc.lines, key = { idx, line -> "${idx}_${line.timeMs}" }) { idx, line ->
                            val isActive = doc.isSynced && idx == activeIndex
                            val isPast = doc.isSynced && activeIndex >= 0 && idx < activeIndex

                            val targetColor = when {
                                !doc.isSynced -> Color.White.copy(alpha = 0.88f)
                                isActive -> Color.White
                                isPast -> Color.White.copy(alpha = 0.30f)
                                else -> Color.White.copy(alpha = 0.42f)
                            }
                            val animatedColor by animateColorAsState(
                                targetValue = targetColor,
                                animationSpec = tween(220),
                                label = "lyric_color",
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isActive) 1.03f else 1.0f,
                                animationSpec = tween(220),
                                label = "lyric_scale",
                            )
                            val fontSizeSp = if (isActive) settings.lyricsFontSize.activeSp else settings.lyricsFontSize.inactiveSp

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        enabled = doc.isSynced,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        val targetMs = (line.timeMs - doc.offsetMs).coerceAtLeast(0L)
                                        userDraggingPause = false
                                        onSeekTo(targetMs)
                                    }
                                    .padding(vertical = 3.dp),
                                horizontalAlignment = hAlign,
                            ) {
                                Text(
                                    text = line.text,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.38f).sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = animatedColor,
                                    textAlign = textAlign,
                                )
                                if (!line.translation.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = line.translation,
                                        fontSize = (fontSizeSp * 0.78f).sp,
                                        lineHeight = (fontSizeSp * 1.12f).sp,
                                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                        color = animatedColor.copy(alpha = if (isActive) 0.85f else 0.75f),
                                        textAlign = textAlign,
                                    )
                                }
                            }
                        }
                    }

                    // 顶部 & 底部柔和渐变遮罩
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(topAmbientColor.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.canvas.copy(alpha = 0.92f))
                                )
                            )
                    )

                    // 手动滑词暂离时右下浮现「回到当前句」胶囊
                    androidx.compose.animation.AnimatedVisibility(
                        visible = userDraggingPause && activeIndex >= 0 && doc.isSynced,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surface2.copy(alpha = 0.94f))
                                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .clickable {
                                    userDraggingPause = false
                                    scope.launch {
                                        val offsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * 0.34f).toInt() else -240
                                        listState.animateScrollToItem(activeIndex, offsetPx)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("回到当前句", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 内联展开：时间偏移微调条
        AnimatedVisibility(visible = showOffsetPanel && doc != null && doc.isSynced) {
            val curOffset = doc?.offsetMs ?: 0L
            val offsetSecLabel = "${if (curOffset > 0) "+" else ""}${"%.1f".format(curOffset / 1000.0)}s"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OffsetStepChip("-0.5s") { onAdjustOffset(-500L) }
                OffsetStepChip("-0.1s") { onAdjustOffset(-100L) }
                Text(
                    text = offsetSecLabel,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (curOffset == 0L) colors.textSecondary else colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onResetOffset() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                OffsetStepChip("+0.1s") { onAdjustOffset(100L) }
                OffsetStepChip("+0.5s") { onAdjustOffset(500L) }
            }
        }

        // 内联展开：字号 & 对齐样式条
        AnimatedVisibility(visible = showStylePanel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LyricsFontSize.entries.forEach { sz ->
                        val sel = sz == settings.lyricsFontSize
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) colors.accent else Color.White.copy(alpha = 0.08f))
                                .clickable { onChangeFontSize(sz) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = when (sz) {
                                    LyricsFontSize.SMALL -> "小"
                                    LyricsFontSize.MEDIUM -> "中"
                                    LyricsFontSize.LARGE -> "大"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) colors.onAccent else colors.textPrimary,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { onChangeAlignment(LyricsAlignment.LEFT) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.FormatAlignLeft,
                            contentDescription = "左对齐",
                            tint = if (settings.lyricsAlignment == LyricsAlignment.LEFT) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { onChangeAlignment(LyricsAlignment.CENTER) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.FormatAlignCenter,
                            contentDescription = "居中",
                            tint = if (settings.lyricsAlignment == LyricsAlignment.CENTER) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // 底部工具胶囊 + 极简进度条与播控
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (doc != null && doc.isSynced) {
                val offsetActive = showOffsetPanel || doc.offsetMs != 0L
                val offsetLabel = if (doc.offsetMs == 0L) "偏移"
                else "${if (doc.offsetMs > 0) "+" else ""}${"%.1f".format(doc.offsetMs / 1000.0)}s"
                ToolPill(
                    icon = Icons.Rounded.Timer,
                    label = offsetLabel,
                    active = offsetActive,
                    onClick = {
                        showStylePanel = false
                        showOffsetPanel = !showOffsetPanel
                    },
                )
            }
            ToolPill(
                icon = Icons.Rounded.FormatSize,
                label = "样式",
                active = showStylePanel,
                onClick = {
                    showOffsetPanel = false
                    showStylePanel = !showStylePanel
                },
            )
            ToolPill(
                icon = Icons.Rounded.Search,
                label = "搜词/换词",
                active = false,
                onClick = { showSearchSheet = true },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatDuration(playback.positionMs)} / ${formatDuration(playback.durationMs)}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textMuted,
            )
        }

        LinearProgressIndicator(
            progress = { progressFrac },
            color = colors.textPrimary,
            trackColor = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSkipPrev) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一曲", tint = colors.textPrimary, modifier = Modifier.size(30.dp))
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onTogglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一曲", tint = colors.textPrimary, modifier = Modifier.size(30.dp))
            }
        }
    }

    if (showSearchSheet) {
        LyricsSearchSheet(
            track = track,
            lyricsState = lyricsState,
            onSearch = onSearchOnline,
            onSelectCandidate = { cand ->
                onApplyCandidate(cand)
                showSearchSheet = false
            },
            onImportLocalLrc = {
                showSearchSheet = false
                lrcPicker.launch(arrayOf("*/*"))
            },
            onRestoreAuto = {
                onClearOverride()
                showSearchSheet = false
            },
            onDismiss = { showSearchSheet = false },
        )
    }
}

@Composable
private fun ToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) colors.accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (active) colors.accent.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) colors.accent else colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) colors.accent else colors.textPrimary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OffsetStepChip(label: String, onClick: () -> Unit) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.textPrimary,
        )
    }
}

/** 在线搜词（LRCLIB）与本地 .lrc 导入弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchSheet(
    track: TrackEntity,
    lyricsState: LyricsUiState,
    onSearch: (String) -> Unit,
    onSelectCandidate: (OnlineLyricCandidate) -> Unit,
    onImportLocalLrc: () -> Unit,
    onRestoreAuto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultQuery = remember(track.path) {
        listOf(track.title, track.artist.takeIf { it != "未知艺术家" }.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    var query by remember(track.path) { mutableStateOf(defaultQuery) }

    LaunchedEffect(track.path) {
        if (defaultQuery.isNotBlank()) onSearch(defaultQuery)
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
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = colors.accent, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("搜索与更换歌词", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("数据源：LRCLIB 开源同步歌词库 / 本地 .lrc", fontSize = 11.sp, color = colors.textSecondary)
                }
                TextButton(onClick = onImportLocalLrc) {
                    Text("导入 .lrc", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("输入歌名 / 艺人关键词…", fontSize = 13.sp) },
                    singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空", tint = colors.textSecondary)
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.hairline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent)
                        .clickable { onSearch(query) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text("搜索", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                lyricsState.isSearchingOnline -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp)
                    }
                }

                lyricsState.searchError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(lyricsState.searchError, fontSize = 13.sp, color = colors.danger)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { onSearch(query) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重试", color = colors.accent)
                        }
                    }
                }

                lyricsState.searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("未搜到匹配候选，可尝试精简关键词或导入本地 .lrc", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(lyricsState.searchResults, key = { "${it.id}_${it.trackName}_${it.durationSec}" }) { cand ->
                            val preview = remember(cand.id, cand.syncedLyrics, cand.plainLyrics) {
                                cand.bestText
                                    .lineSequence()
                                    .map { it.replace(Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]"""), "").trim() }
                                    .filter { it.isNotBlank() && !it.startsWith("[") }
                                    .take(2)
                                    .joinToString(" / ")
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface2)
                                    .clickable { onSelectCandidate(cand) }
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cand.trackName.ifBlank { track.title },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (cand.hasSynced) "同步" else "纯文本",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cand.hasSynced) colors.exclusiveGreen else colors.textSecondary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (cand.hasSynced) colors.exclusiveGreen.copy(alpha = 0.16f)
                                                else colors.surface3
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                val meta = listOfNotNull(
                                    cand.artistName.takeIf { it.isNotBlank() },
                                    cand.albumName.takeIf { it.isNotBlank() },
                                    cand.durationSec.takeIf { it > 0 }?.let { formatDuration(it * 1000L) },
                                ).joinToString(" · ")
                                Text(meta, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (preview.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(preview, fontSize = 11.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onRestoreAuto) {
                    Text("恢复自动匹配", fontSize = 12.sp, color = colors.textSecondary)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", fontSize = 12.sp, color = colors.textPrimary)
                }
            }
        }
    }
}
