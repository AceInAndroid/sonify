package com.sonify.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors

/**
 * 极简黑胶唱片组件（Spotify Dark × Vinyl Style）：
 * - Canvas 自绘深炭盘面、细腻同心音槽与静止 45° 高光（不随唱片旋转，模拟真实灯光）
 * - 中心圆形封面标 + 暗金细环 + 中央主轴孔
 * - 播放时 20s/圈匀速旋转，暂停平滑停在当前角度不回弹
 */
@Composable
fun VinylDisc(
    coverPath: String?,
    isPlaying: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    isMini: Boolean = false,
) {
    val colors = LocalHiFiColors.current
    var angle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNs = withFrameNanos { it }
        while (true) {
            val nowNs = withFrameNanos { it }
            val dtSec = ((nowNs - lastFrameNs).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
            lastFrameNs = nowNs
            // 18°/s = 20 秒一圈（接近 33⅓ RPM 的沉静舒缓节奏）
            angle = (angle + dtSec * 18f) % 360f
        }
    }

    val labelFraction = if (isMini) 0.54f else 0.56f
    val labelSize = size * labelFraction
    val spindleSize = if (isMini) 5.dp else 12.dp

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (isMini) 4.dp else 24.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // 旋转层：黑胶盘体 + 同心音槽 + 中心封面
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // 盘面深炭径向基底
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF17171A), Color(0xFF0A0A0C), Color(0xFF141417)),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )

                // 极细同心音槽
                val grooveCount = if (isMini) 4 else 18
                val innerR = r * (labelFraction + 0.04f)
                val outerR = r * 0.96f
                val step = (outerR - innerR) / grooveCount.coerceAtLeast(1)
                for (i in 0 until grooveCount) {
                    val gr = innerR + step * i
                    val alpha = if (i % 3 == 0) 0.065f else 0.03f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = gr,
                        center = center,
                        style = Stroke(width = if (isMini) 0.6f else 1.0f),
                    )
                }

                // 最外圈极细描边
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = r - 1f,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }

            // 中心专辑封面标
            Box(
                modifier = Modifier
                    .size(labelSize)
                    .clip(CircleShape)
                    .background(Color(0xFF222226))
                    .border(
                        width = if (isMini) 1.dp else 1.5.dp,
                        color = colors.accent.copy(alpha = 0.45f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val coverModel = CoverArtSupport.resolveCoilModel(coverPath)
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(labelSize * 0.42f),
                    )
                }
            }
        }

        // 静止层：45° 与 225° 柔和反光高光（不随唱片转动，增强立体黑胶质感）
        if (!isMini) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.08f to Color.White.copy(alpha = 0.09f),
                        0.16f to Color.Transparent,
                        0.50f to Color.Transparent,
                        0.58f to Color.White.copy(alpha = 0.07f),
                        0.66f to Color.Transparent,
                        1.00f to Color.Transparent,
                        center = center,
                    ),
                    radius = r,
                    center = center,
                )
            }
        }

        // 中央主轴孔（Spindle Hole）
        Box(
            modifier = Modifier
                .size(spindleSize)
                .clip(CircleShape)
                .background(Color(0xFF0E0E10))
                .border(
                    width = if (isMini) 0.8.dp else 1.5.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = CircleShape,
                )
        )
    }
}
