package com.sonify.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.R
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.ui.PlaybackUiState
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.UsbConnectionState

/**
 * Spotify 同款悬浮圆角 MiniPlayer：
 * 浮于底部导航上方，带封面氛围底色、微型旋转黑胶、USB 独占指示灯、2dp 底部进度线与左右滑切歌。
 */
@Composable
fun MiniPlayerBar(
    playback: PlaybackUiState,
    usbState: UsbConnectionState,
    activeRoute: ActiveOutputRoute,
    ambientEnabled: Boolean,
    onOpenNowPlaying: () -> Unit,
    onOpenOutputSheet: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.currentTrack ?: return
    val colors = LocalHiFiColors.current

    var ambient by remember(track.coverPath) { mutableStateOf(colors.surface2) }
    LaunchedEffect(track.coverPath, ambientEnabled) {
        ambient = if (ambientEnabled) {
            AmbientColorExtractor.extract(track.coverPath)
        } else {
            colors.surface2
        }
    }

    val isUsbExclusive = activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbState.isConnected
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ambient)
            .clickable { onOpenNowPlaying() }
            .pointerInput(track.path) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                    onDragEnd = {
                        if (dragAccum < -90f) onSkipNext()
                        else if (dragAccum > 90f) onSkipPrev()
                        dragAccum = 0f
                    },
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VinylDisc(
                coverPath = track.coverPath,
                isPlaying = playback.isPlaying,
                size = 42.dp,
                isMini = true,
            )

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = track.title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (isUsbExclusive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.exclusiveGreen)
                        )
                        val sr = usbState.stats.rate.takeIf { it > 0 } ?: track.sampleRate
                        val srTxt = if (sr > 0) {
                            if (sr % 1000 == 0) "${sr / 1000}k" else "%.1fk".format(sr / 1000.0)
                        } else ""
                        Text(
                            text = "${usbState.deviceDisplayName} · $srTxt",
                            color = colors.exclusiveGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = track.artist,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(onClick = onOpenOutputSheet) {
                Icon(
                    imageVector = if (isUsbExclusive) Icons.Rounded.Usb else Icons.Rounded.Headphones,
                    contentDescription = stringResource(R.string.player_output_device),
                    tint = if (isUsbExclusive) colors.exclusiveGreen else colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }

            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playback.isPlaying) R.string.common_pause else R.string.common_play),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // 紧贴底部的 2dp Spotify 风格进度线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}
