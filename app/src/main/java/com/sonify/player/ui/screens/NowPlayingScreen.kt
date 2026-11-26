package com.sonify.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.data.AppSettings
import com.sonify.player.data.CoverStyle
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.VolumeMode
import com.sonify.player.playback.AudioFocusState
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.components.VinylDisc
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.SystemBarsSpec
import com.sonify.player.ui.theme.formatDuration
import com.sonify.player.usb.EffectiveVolumeEngine
import com.sonify.player.usb.UsbConnectionState
import com.sonify.player.usb.VolumeHudState
import kotlinx.coroutines.delay

/**
 * 全屏播放页（Spotify Dark × 简约黑胶风格）：
 * - 封面主色顶部暗调渐变
 * - 居中极简旋转黑胶唱片（支持左右滑切歌、设置可切极简方形封面）
 * - 极简 HiFi 双徽章信号链路胶囊（实时展示真实协商采样率与位深）
 * - Spotify 左对齐曲目信息、细进度条、64dp 纯白圆形主播放键
 * - 左下 Connect 绿色输出设备入口 + 右下播放队列弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playback: PlaybackUiState,
    settings: AppSettings,
    usbState: UsbConnectionState,
    volumeState: VolumeHudState,
    activeRoute: ActiveOutputRoute,
    audioFocusState: AudioFocusState = AudioFocusState.NONE,
    barsSpec: SystemBarsSpec = SystemBarsSpec(),
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onOpenOutputSheet: () -> Unit,
    onUsbVolumeChange: (Int) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleImmersiveMode: (Boolean) -> Unit = {},
    isCurrentTrackDownloaded: Boolean = false,
    onAddCurrentToPlaylist: (TrackEntity) -> Unit = {},
    onDownloadCurrentTrack: (TrackEntity) -> Unit = {},
) {
    val track = playback.currentTrack ?: return
    val colors = LocalHiFiColors.current

    var rawAmbient by remember(track.coverPath) { mutableStateOf(AmbientColorExtractor.DefaultAmbient) }
    LaunchedEffect(track.coverPath, settings.ambientBackground) {
        rawAmbient = if (settings.ambientBackground) {
            AmbientColorExtractor.extract(track.coverPath)
        } else {
            colors.canvas
        }
    }
    val topColor by animateColorAsState(
        targetValue = rawAmbient,
        animationSpec = tween(550),
        label = "ambient",
    )

    var showQueue by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableFloatStateOf(0f) }
    var zenVinylMode by remember { mutableStateOf(false) }
    var immersiveHintText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.immersiveMusicMode) {
        if (!settings.immersiveMusicMode) zenVinylMode = false
    }

    LaunchedEffect(immersiveHintText) {
        if (immersiveHintText != null) {
            delay(1800)
            immersiveHintText = null
        }
    }

    val durationMs = playback.durationMs.coerceAtLeast(1L)
    val displayPosMs = if (isDraggingSlider) dragPosMs.toLong() else playback.positionMs
    val progressFrac = (displayPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val isUsbExclusive = activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbState.isConnected
    val stats = usbState.stats

    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val targetArtworkSize = if (zenVinylMode) {
        (screenWidth * 0.78f).coerceIn(250f, 330f).dp
    } else {
        (screenWidth * 0.66f).coerceIn(210f, 280f).dp
    }
    val artworkSize by animateDpAsState(
        targetValue = targetArtworkSize,
        animationSpec = tween(320),
        label = "vinyl_size",
    )

    val topSafeMod = if (barsSpec.hideStatusBar) Modifier.displayCutoutPadding() else Modifier.statusBarsPadding()
    val bottomSafeMod = if (barsSpec.hideNavBar) Modifier.padding(bottom = 8.dp) else Modifier.navigationBarsPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to topColor,
                    0.55f to colors.canvas,
                    1.0f to colors.canvas,
                )
            )
            .clickable(
                enabled = zenVinylMode,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { zenVinylMode = false }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { verticalDrag = 0f },
                    onVerticalDrag = { _, drag -> verticalDrag += drag },
                    onDragEnd = {
                        if (verticalDrag > 140f) {
                            if (zenVinylMode) zenVinylMode = false else onCollapse()
                        }
                        verticalDrag = 0f
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(topSafeMod)
                .then(bottomSafeMod)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部栏（纯享黑胶态淡出）
            AnimatedVisibility(
                visible = !zenVinylMode,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(200)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "收起播放页",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "正在播放自",
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = playback.contextLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 一键沉浸音乐模式切换按钮
                    IconButton(
                        onClick = {
                            val next = !settings.immersiveMusicMode
                            onToggleImmersiveMode(next)
                            immersiveHintText = if (next) {
                                "沉浸音乐模式已开启 · 轻点唱片可进入纯享摆台"
                            } else {
                                "已退出沉浸音乐模式"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (settings.immersiveMusicMode) Icons.Rounded.FullscreenExit
                            else Icons.Rounded.Fullscreen,
                            contentDescription = "沉浸音乐模式",
                            tint = if (settings.immersiveMusicMode) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(if (zenVinylMode) 1.0f else 0.5f))

            // 中央黑胶唱片 / 极简方形封面（左右滑动切歌；沉浸模式下轻点切换纯享黑胶摆台态）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (settings.immersiveMusicMode) {
                            zenVinylMode = !zenVinylMode
                            if (zenVinylMode) {
                                immersiveHintText = "纯享黑胶模式 · 轻点任意处恢复操控"
                            }
                        }
                    }
                    .pointerInput(track.path) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { _, drag -> horizontalDrag += drag },
                            onDragEnd = {
                                if (horizontalDrag < -110f) onSkipNext()
                                else if (horizontalDrag > 110f) onSkipPrev()
                                horizontalDrag = 0f
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (settings.coverStyle == CoverStyle.VINYL) {
                    VinylDisc(
                        coverPath = track.coverPath,
                        isPlaying = playback.isPlaying,
                        size = artworkSize,
                        isMini = false,
                    )
                } else {
                    CoverSquare(
                        coverPath = track.coverPath,
                        modifier = Modifier.size(artworkSize),
                        cornerDp = 12,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ★ HiFi 核心：实时信号链路双徽章胶囊（显示真实协商值，点击弹诊断 Sheet）
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isUsbExclusive) colors.exclusiveGreen.copy(alpha = 0.35f) else colors.hairline,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onOpenOutputSheet() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = track.formatBadgeShort(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent,
                )
                Text("→", fontSize = 11.sp, color = colors.textMuted)
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isUsbExclusive) colors.exclusiveGreen else colors.textMuted)
                )
                val outLabel = if (isUsbExclusive && stats.rate > 0) {
                    val sr = if (stats.rate % 1000 == 0) "${stats.rate / 1000}k"
                    else "%.1fk".format(stats.rate / 1000.0)
                    "USB 独占 $sr/${stats.bits}b"
                } else if (isUsbExclusive) {
                    "USB 独占 · ${usbState.deviceDisplayName}"
                } else {
                    "本机输出"
                }
                Text(
                    text = outLabel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (isUsbExclusive) colors.exclusiveGreen else colors.textSecondary,
                )
            }

            // 系统音频焦点交互状态提示（临时避让 / 通话等待 / 被其他应用抢占）
            audioFocusState.bannerText?.let { focusTip ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = focusTip,
                    fontSize = 11.sp,
                    color = if (audioFocusState == AudioFocusState.LOST_PERMANENT) colors.accent else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            if (!playback.isPlaying) onTogglePlayPause()
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.weight(if (zenVinylMode) 0.4f else 0.6f))

            if (zenVinylMode) {
                // 纯享黑胶（Zen Vinyl）极简居中曲目展示
                Text(
                    text = track.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${track.artist} · ${track.album}",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1.0f))
            } else {
                // Spotify 标志性左对齐曲目信息 + 右侧收藏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${track.artist} · ${track.album}",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(track) }) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (track.isFavorite) colors.exclusiveGreen else colors.textSecondary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Spotify 极细进度条
                Slider(
                    value = displayPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        isDraggingSlider = true
                        dragPosMs = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        onSeekTo(dragPosMs.toLong())
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.textPrimary,
                        activeTrackColor = colors.textPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(displayPosMs),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = formatDuration(durationMs),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Spotify 经典 5 键播控栏（64dp 纯白主播放圆键）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (playback.shuffleEnabled) colors.exclusiveGreen else colors.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = onSkipPrev, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "上一曲",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onTogglePlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playback.isPlaying) "暂停" else "播放",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "下一曲",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = onCycleRepeat) {
                        val icon = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        }
                        val active = playback.repeatMode != Player.REPEAT_MODE_OFF
                        Icon(
                            imageVector = icon,
                            contentDescription = "循环模式",
                            tint = if (active) colors.exclusiveGreen else colors.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // USB 独占模式下常驻精致护耳音量条（支持滑块 + 静音 + 实时 dB 与上限标示）
                if (usbState.isConnected && settings.usbExclusiveEnabled) {
                    Spacer(Modifier.height(6.dp))
                    if (settings.volumeMode == VolumeMode.FIXED_DIRECT) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.danger.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "固定直通 0 dBFS（已绕过护耳上限，请用外接耳放调音量）",
                                fontSize = 11.sp,
                                color = colors.danger,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        val isMuted = volumeState.volumePercent <= 0
                        val maxLimit = if (volumeState.protectionEnabled) volumeState.maxLimitPercent else 100
                        val volAccent = if (volumeState.engine == EffectiveVolumeEngine.HARDWARE_UAC) {
                            colors.exclusiveGreen
                        } else colors.accent

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { onToggleMute(!isMuted) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                                        else Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "静音切换",
                                        tint = if (isMuted) colors.textMuted else volAccent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Slider(
                                    value = volumeState.volumePercent.toFloat().coerceIn(0f, maxLimit.toFloat()),
                                    onValueChange = { onUsbVolumeChange(it.toInt()) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = volAccent,
                                        activeTrackColor = volAccent,
                                        inactiveTrackColor = colors.surface3,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                )
                                Text(
                                    text = if (isMuted) "静音"
                                    else "${volumeState.volumePercent}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = volAccent,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (isMuted) "${volumeState.engine.shortLabel} · 静音"
                                    else "${volumeState.engine.shortLabel} · %.1f dB".format(volumeState.effectiveDb),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textMuted,
                                )
                                Text(
                                    text = if (volumeState.protectionEnabled) "护耳上限 $maxLimit%" else "护耳未开启",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (volumeState.protectionEnabled) colors.textSecondary else colors.danger,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 底部辅助栏：左下 Spotify Connect 设备标 + 右下队列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenOutputSheet() }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isUsbExclusive) Icons.Rounded.Usb else Icons.Rounded.Headphones,
                            contentDescription = "输出设备",
                            tint = if (isUsbExclusive) colors.exclusiveGreen else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (isUsbExclusive) usbState.deviceDisplayName else "本机输出",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUsbExclusive) colors.exclusiveGreen else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (SubsonicUri.isTrackUri(track.path)) {
                            IconButton(
                                onClick = { if (!isCurrentTrackDownloaded) onDownloadCurrentTrack(track) }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentTrackDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                    contentDescription = if (isCurrentTrackDownloaded) "已离线下载" else "下载歌曲",
                                    tint = if (isCurrentTrackDownloaded) colors.exclusiveGreen else colors.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onAddCurrentToPlaylist(track) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "加入歌单",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                        IconButton(onClick = { showQueue = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "播放队列",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // 纯享黑胶态底缘 2dp 极细进度线
        if (zenVinylMode) {
            LinearProgressIndicator(
                progress = { progressFrac },
                color = colors.accent,
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        // 沉浸模式切换 1.8 秒极简顶部提示胶囊
        AnimatedVisibility(
            visible = immersiveHintText != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topSafeMod)
                .padding(top = 56.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xDE1E1E22))
                    .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = immersiveHintText.orEmpty(),
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                )
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface1,
            contentColor = colors.textPrimary,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                Text(
                    text = "当前队列（${playback.queue.size} 首）",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(playback.queue, key = { idx, t -> "${idx}_${t.path}" }) { idx, item ->
                        TrackRowItem(
                            track = item,
                            isCurrent = idx == playback.currentIndex,
                            onClick = {
                                onSelectQueueIndex(idx)
                                showQueue = false
                            },
                        )
                    }
                }
            }
        }
    }
}
