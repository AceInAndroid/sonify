package com.sonify.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.R
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.EffectiveVolumeEngine
import com.sonify.player.usb.VolumeHudState
import kotlinx.coroutines.delay

/**
 * 极简深色护耳音量 HUD：
 * 物理音量 +/- 键触发后从顶部滑入，2.2 秒无操作自动隐去；
 * 进度条直观标出耳朵保护锁定红区（如 60%..100%）。
 */
@Composable
fun VolumeHudOverlay(
    volumeState: VolumeHudState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(volumeState.hudTick) {
        if (volumeState.hudTick > 0L) {
            visible = true
            delay(2200)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val isMuted = volumeState.volumePercent <= 0 &&
            volumeState.engine != EffectiveVolumeEngine.FIXED_DIRECT
        val dbText = when {
            volumeState.engine == EffectiveVolumeEngine.FIXED_DIRECT -> "0.0 dBFS"
            isMuted -> stringResource(R.string.player_muted)
            else -> "%.1f dB".format(volumeState.effectiveDb)
        }
        val engineLabel = stringResource(volumeState.engine.shortLabelRes)
        val barColor = when {
            volumeState.engine == EffectiveVolumeEngine.FIXED_DIRECT -> colors.danger
            volumeState.hitLimit -> colors.accent
            volumeState.engine == EffectiveVolumeEngine.HARDWARE_UAC -> colors.exclusiveGreen
            else -> colors.accent
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF21C1C20))
                .border(
                    1.dp,
                    if (volumeState.hitLimit) colors.accent.copy(alpha = 0.65f) else colors.hairline,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                    else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append(engineLabel)
                        if (volumeState.deviceName.isNotBlank()) {
                            append(" · ")
                            append(volumeState.deviceName)
                        }
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${volumeState.volumePercent}%  $dbText",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor,
                )
            }

            Spacer(Modifier.height(10.dp))

            // 护耳刻度音量条（含右侧红色保护锁定区）
            VolumeMeterBar(
                percent = volumeState.volumePercent,
                maxLimitPercent = if (volumeState.protectionEnabled) volumeState.maxLimitPercent else 100,
                fillColor = barColor,
                dangerColor = colors.danger,
            )

            if (volumeState.hitLimit && volumeState.protectionEnabled) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(R.string.player_vol_hud_limit, volumeState.maxLimitPercent),
                        fontSize = 11.sp,
                        color = colors.accent,
                    )
                }
            } else if (volumeState.engine == EffectiveVolumeEngine.FIXED_DIRECT) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_vol_hud_direct),
                    fontSize = 11.sp,
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
fun VolumeMeterBar(
    percent: Int,
    maxLimitPercent: Int,
    fillColor: Color,
    dangerColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        val w = size.width
        val pctFrac = (percent.coerceIn(0, 100)) / 100f
        val limitFrac = (maxLimitPercent.coerceIn(20, 100)) / 100f

        // 底层轨道
        drawRoundRect(
            color = Color.White.copy(alpha = 0.14f),
            size = Size(w, h),
            cornerRadius = r,
        )

        // 右侧护耳锁定红区
        if (limitFrac < 0.995f) {
            val lockStart = w * limitFrac
            drawRoundRect(
                color = dangerColor.copy(alpha = 0.30f),
                topLeft = Offset(lockStart, 0f),
                size = Size(w - lockStart, h),
                cornerRadius = r,
            )
            // 上限刻度线
            drawLine(
                color = dangerColor.copy(alpha = 0.85f),
                start = Offset(lockStart, -2f),
                end = Offset(lockStart, h + 2f),
                strokeWidth = 2.5f,
            )
        }

        // 当前音量填充
        if (pctFrac > 0f) {
            drawRoundRect(
                color = fillColor,
                size = Size(w * pctFrac, h),
                cornerRadius = r,
            )
        }
    }
}
