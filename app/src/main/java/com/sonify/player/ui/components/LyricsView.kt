package com.sonify.player.ui.components

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.AppSettings
import com.sonify.player.data.LyricsAlignment
import com.sonify.player.data.TrackEntity
import com.sonify.player.lyrics.LrcParser
import com.sonify.player.lyrics.LyricsProgressMath
import com.sonify.player.lyrics.OnlineLyricCandidate
import com.sonify.player.ui.LyricsUiState
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.screens.CoverSquare
import com.sonify.player.ui.theme.AmbientPalette
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 沉浸式全屏歌词视图（与外层 ImmersiveLyricsBackdrop 边到边无缝融合）：
 * - 60fps 局部时钟插值驱动逐句流光扫色（支持增强型 `<mm:ss.xx>` 逐字与标准行级 LRC）；
 * - 距离景深衰减 + Offscreen DstIn 上下真实 Alpha 羽化渐隐 + 间奏呼吸三点；
 * - 5 秒静置自动进入沉浸纯享态（淡出顶/底栏仅留流动光晕、歌词与极细进度线）；
 * - 底部悬浮半透明「实时样式面板」边看歌词滚动边调字体/字号/高亮/背景。
 */
@Composable
fun LyricsOverlayView(
    track: TrackEntity,
    playback: PlaybackUiState,
    lyricsState: LyricsUiState,
    settings: AppSettings,
    ambientPalette: AmbientPalette,
    onBackToVinyl: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    styleActions: LyricsStyleActions,
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
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var controlsHidden by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableIntStateOf(0) }

    val doc = lyricsState.document

    // 60fps 局部平滑时钟插值，让逐句流光扫色丝滑推进且暂停时零 CPU 占用
    var smoothPositionMs by remember(track.path) { mutableLongStateOf(playback.positionMs) }
    LaunchedEffect(playback.positionMs, playback.isPlaying, doc?.isSynced) {
        val anchorPos = playback.positionMs
        val anchorWall = SystemClock.elapsedRealtime()
        smoothPositionMs = anchorPos
        if (!playback.isPlaying || doc == null || !doc.isSynced) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                val elapsed = (SystemClock.elapsedRealtime() - anchorWall).coerceIn(0L, 650L)
                smoothPositionMs = (anchorPos + elapsed).coerceAtMost(playback.durationMs.coerceAtLeast(anchorPos + 650L))
            }
        }
    }

    val activeIndex = remember(doc, smoothPositionMs) {
        if (doc != null && doc.isSynced) {
            LrcParser.findActiveIndex(doc.lines, smoothPositionMs, doc.offsetMs)
        } else -1
    }

    val sweepProgress = remember(doc, activeIndex, smoothPositionMs, playback.durationMs) {
        if (doc != null && doc.isSynced && activeIndex >= 0) {
            LyricsProgressMath.lineProgress(
                lines = doc.lines,
                activeIndex = activeIndex,
                positionMs = smoothPositionMs,
                offsetMs = doc.offsetMs,
                trackDurationMs = playback.durationMs,
            )
        } else 0f
    }

    val interlude = remember(doc, activeIndex, smoothPositionMs) {
        if (doc != null && doc.isSynced) {
            LyricsProgressMath.interludeState(
                lines = doc.lines,
                activeIndex = activeIndex,
                positionMs = smoothPositionMs,
                offsetMs = doc.offsetMs,
            )
        } else com.sonify.player.lyrics.InterludeState(isActive = false)
    }

    // 仅检测真实手指拖拽（collectIsDraggedAsState 不会被程序自动滚屏误触发！）
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userDraggingPause = true
            controlsHidden = false
            interactionNonce++
        } else if (userDraggingPause) {
            delay(4000)
            userDraggingPause = false
        }
    }

    // 沉浸纯享歌词自动隐身计时（播放中无操作 5 秒柔和淡出顶/底控件）
    LaunchedEffect(
        settings.lyricsAutoHideControls,
        playback.isPlaying,
        doc?.isEmpty,
        showStylePanel,
        showOffsetPanel,
        showSearchSheet,
        userDraggingPause,
        interactionNonce,
    ) {
        val canAutoHide = settings.lyricsAutoHideControls &&
            playback.isPlaying &&
            doc != null && !doc.isEmpty &&
            !showStylePanel && !showOffsetPanel && !showSearchSheet && !userDraggingPause
        if (!canAutoHide) {
            controlsHidden = false
            return@LaunchedEffect
        }
        delay(5000)
        controlsHidden = true
    }

    // 自动平滑居中滚动至当前演唱句（打开底部实时样式面板时自动上移至可见上半屏）
    LaunchedEffect(activeIndex, userDraggingPause, doc?.lines?.size, showStylePanel) {
        if (!userDraggingPause && activeIndex >= 0 && doc != null && doc.isSynced) {
            val anchorRatio = if (showStylePanel) 0.22f else 0.36f
            val offsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * anchorRatio).toInt() else -240
            val targetItemIdx = if (interlude.isActive && activeIndex < 0) 1 else activeIndex
            runCatching {
                listState.animateScrollToItem(index = targetItemIdx, scrollOffset = offsetPx)
            }
        }
    }

    val durationMs = playback.durationMs.coerceAtLeast(1L)
    val progressFrac = (smoothPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val textAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) TextAlign.Center else TextAlign.Start
    val hAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
    val pivotX = if (settings.lyricsAlignment == LyricsAlignment.CENTER) 0.5f else 0.0f

    val composeFont = settings.lyricsFontFamily.toComposeFontFamily()
    val letterSpacing = settings.lyricsFontFamily.letterSpacingSp().sp
    val activeSp = settings.lyricsFontSizeSp.toFloat()
    val inactiveSp = (activeSp * 0.82f).coerceAtLeast(13.5f)
    val lineMult = settings.lyricsLineSpacing.lineHeightMultiplier

    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportLocalLrc(uri)
    }

    fun wakeControls() {
        controlsHidden = false
        interactionNonce++
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
        ) {
            // 顶部紧凑曲目头（纯享歌词态自动柔和收起）
            AnimatedVisibility(
                visible = !controlsHidden,
                enter = fadeIn(tween(260)) + expandVertically(tween(260)),
                exit = fadeOut(tween(340)) + shrinkVertically(tween(340)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverSquare(
                        coverPath = track.coverPath,
                        modifier = Modifier
                            .size(42.dp)
                            .clickable { onBackToVinyl() },
                        cornerDp = 8,
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
                                color = Color.White.copy(alpha = 0.72f),
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
                                        .background(colors.accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    // 手动一键进入纯享歌词态
                    if (doc != null && !doc.isEmpty) {
                        IconButton(
                            onClick = {
                                showStylePanel = false
                                showOffsetPanel = false
                                controlsHidden = true
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = "纯享歌词",
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onBackToVinyl() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("黑胶", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 中央歌词主体区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { viewportHeightPx = it.height }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (showStylePanel) {
                            showStylePanel = false
                        } else if (controlsHidden) {
                            wakeControls()
                        } else if (doc != null && !doc.isEmpty) {
                            controlsHidden = true
                        }
                    },
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
                                tint = colors.accent.copy(alpha = 0.78f),
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
                                color = Color.White.copy(alpha = 0.72f),
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
                                        .background(Color.White.copy(alpha = 0.12f))
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
                            modifier = Modifier
                                .fillMaxSize()
                                // Offscreen DstIn 真实 Alpha 上下羽化，让歌词毫无接缝地融进后方流体多色背景
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            0.08f to Color.White,
                                            0.88f to Color.White,
                                            1.0f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                            contentPadding = PaddingValues(
                                top = if (controlsHidden) 88.dp else 56.dp,
                                bottom = if (showStylePanel) 330.dp else 170.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(settings.lyricsLineSpacing.itemGapDp.dp),
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

                            // 前奏呼吸三点（开唱前长前奏）
                            if (doc.isSynced && activeIndex < 0 && interlude.isActive) {
                                item(key = "intro_interlude_dots") {
                                    InterludeDotsIndicator(
                                        state = interlude,
                                        tint = Color.White,
                                        alignment = hAlign,
                                    )
                                }
                            }

                            itemsIndexed(doc.lines, key = { idx, line -> "${idx}_${line.timeMs}" }) { idx, line ->
                                val isActive = doc.isSynced && idx == activeIndex
                                val depth = if (doc.isSynced && activeIndex >= 0) {
                                    LyricsProgressMath.depthStyle(idx - activeIndex, userDraggingPause)
                                } else {
                                    LyricsProgressMath.depthStyle(0, isDragging = true)
                                }

                                val animatedAlpha by animateFloatAsState(
                                    targetValue = if (!doc.isSynced) 0.88f else depth.alpha,
                                    animationSpec = tween(260),
                                    label = "lyric_depth_alpha",
                                )
                                val animatedScale by animateFloatAsState(
                                    targetValue = if (!doc.isSynced) 1.0f else depth.scale,
                                    animationSpec = tween(260),
                                    label = "lyric_depth_scale",
                                )
                                val blurModifier = if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    doc.isSynced &&
                                    depth.blurDp > 0.1f
                                ) {
                                    Modifier.blur(depth.blurDp.dp)
                                } else Modifier

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(blurModifier)
                                        .graphicsLayer {
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                            transformOrigin = TransformOrigin(pivotX, 0.5f)
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            enabled = doc.isSynced,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            val targetMs = (line.timeMs - doc.offsetMs).coerceAtLeast(0L)
                                            userDraggingPause = false
                                            wakeControls()
                                            onSeekTo(targetMs)
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalAlignment = hAlign,
                                ) {
                                    if (isActive) {
                                        ActiveLyricText(
                                            text = line.text,
                                            highlightStyle = settings.lyricsHighlightStyle,
                                            sweepProgress = sweepProgress,
                                            fontSizeSp = activeSp,
                                            lineHeightSp = activeSp * lineMult,
                                            fontFamily = settings.lyricsFontFamily,
                                            boldActive = settings.lyricsBoldActive,
                                            textAlign = textAlign,
                                            glowColor = ambientPalette.accentGlow,
                                            accentColor = colors.accent,
                                        )
                                    } else {
                                        Text(
                                            text = line.text,
                                            style = TextStyle(
                                                fontSize = inactiveSp.sp,
                                                lineHeight = (inactiveSp * lineMult).sp,
                                                fontFamily = composeFont,
                                                fontWeight = FontWeight.Medium,
                                                letterSpacing = letterSpacing,
                                                color = Color.White.copy(alpha = animatedAlpha),
                                                textAlign = textAlign,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    if (settings.lyricsShowTranslation && !line.translation.isNullOrBlank()) {
                                        Spacer(Modifier.height(5.dp))
                                        val transSp = (if (isActive) activeSp else inactiveSp) * 0.72f
                                        Text(
                                            text = line.translation,
                                            style = TextStyle(
                                                fontSize = transSp.sp,
                                                lineHeight = (transSp * 1.32f).sp,
                                                fontFamily = composeFont,
                                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                                color = Color.White.copy(
                                                    alpha = if (isActive) 0.80f else (animatedAlpha * 0.82f)
                                                ),
                                                textAlign = textAlign,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    // 句间长器乐间奏呼吸三点
                                    if (isActive && interlude.isActive) {
                                        Spacer(Modifier.height(6.dp))
                                        InterludeDotsIndicator(
                                            state = interlude,
                                            tint = Color.White,
                                            alignment = hAlign,
                                        )
                                    }
                                }
                            }
                        }

                        // 手动滑词暂离时右下浮现「回到当前句」胶囊
                        androidx.compose.animation.AnimatedVisibility(
                            visible = userDraggingPause && activeIndex >= 0 && doc.isSynced && !showStylePanel,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xD91A1A1D))
                                    .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                                    .clickable {
                                        userDraggingPause = false
                                        wakeControls()
                                        scope.launch {
                                            val offsetPx = if (viewportHeightPx > 0) -(viewportHeightPx * 0.36f).toInt() else -240
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
            AnimatedVisibility(visible = showOffsetPanel && !controlsHidden && doc != null && doc.isSynced) {
                val curOffset = doc?.offsetMs ?: 0L
                val offsetSecLabel = "${if (curOffset > 0) "+" else ""}${"%.1f".format(curOffset / 1000.0)}s"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OffsetStepChip("-0.5s") { wakeControls(); onAdjustOffset(-500L) }
                    OffsetStepChip("-0.1s") { wakeControls(); onAdjustOffset(-100L) }
                    Text(
                        text = offsetSecLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (curOffset == 0L) colors.textSecondary else colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { wakeControls(); onResetOffset() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    OffsetStepChip("+0.1s") { wakeControls(); onAdjustOffset(100L) }
                    OffsetStepChip("+0.5s") { wakeControls(); onAdjustOffset(500L) }
                }
            }

            // 底部工具栏与播控（纯享态平滑隐藏，仅留极细进度条）
            AnimatedVisibility(
                visible = !controlsHidden,
                enter = fadeIn(tween(260)) + expandVertically(tween(260)),
                exit = fadeOut(tween(340)) + shrinkVertically(tween(340)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    wakeControls()
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
                                wakeControls()
                                showOffsetPanel = false
                                showStylePanel = !showStylePanel
                            },
                        )
                        ToolPill(
                            icon = Icons.Rounded.Search,
                            label = "搜词",
                            active = false,
                            onClick = {
                                wakeControls()
                                showStylePanel = false
                                showSearchSheet = true
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${formatDuration(smoothPositionMs)} / ${formatDuration(playback.durationMs)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            color = Color.White.copy(alpha = 0.68f),
                        )
                    }
                }
            }

            // 常驻极细微光进度条（纯享态缩为 2dp 低调呼吸指示，控件展开时 2.5dp）
            LinearProgressIndicator(
                progress = { progressFrac },
                color = if (controlsHidden) Color.White.copy(alpha = 0.55f) else colors.textPrimary,
                trackColor = Color.White.copy(alpha = if (controlsHidden) 0.10f else 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (controlsHidden) 6.dp else 0.dp)
                    .height(if (controlsHidden) 2.dp else 2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )

            AnimatedVisibility(
                visible = !controlsHidden,
                enter = fadeIn(tween(260)) + expandVertically(tween(260)),
                exit = fadeOut(tween(340)) + shrinkVertically(tween(340)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { wakeControls(); onSkipPrev() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一曲", tint = colors.textPrimary, modifier = Modifier.size(30.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { wakeControls(); onTogglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playback.isPlaying) "暂停" else "播放",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    IconButton(onClick = { wakeControls(); onSkipNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一曲", tint = colors.textPrimary, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }

        // 底部半透明「所见即所得」实时样式调参面板（不遮蔽上方正在流动的高亮歌词）
        androidx.compose.animation.AnimatedVisibility(
            visible = showStylePanel,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LyricsLiveStylePanel(
                settings = settings,
                actions = styleActions,
                onClose = {
                    showStylePanel = false
                    wakeControls()
                },
            )
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
