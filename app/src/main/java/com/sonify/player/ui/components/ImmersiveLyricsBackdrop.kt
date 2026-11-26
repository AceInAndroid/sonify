package com.sonify.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sonify.player.data.LyricsBackdropStyle
import com.sonify.player.ui.theme.AmbientPalette
import com.sonify.player.ui.theme.LocalHiFiColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 沉浸式歌词全屏动态背景层（100% 兼容 Android 7~15 / API 24~35，无需依赖 API 31 RenderEffect）：
 * - FLUID_AURORA：3 枚由专辑封面提取的柔和径向光晕球，播放时按 Lissajous 慢轨道呼吸流动，暂停零耗电静驻；
 * - COVER_BLUR：64×64 预模糊封面经 GPU 双线性平滑放大 + 缓慢呼吸缩放 + 电影暗角渐晕；
 * - MINIMAL_DARK：纯净深色画布叠加极克制顶部主色晕染。
 */
@Composable
fun ImmersiveLyricsBackdrop(
    style: LyricsBackdropStyle,
    palette: AmbientPalette,
    blurredCover: ImageBitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current

    val primary by animateColorAsState(palette.primary, tween(700), label = "lyr_bg_primary")
    val secondary by animateColorAsState(palette.secondary, tween(700), label = "lyr_bg_secondary")
    val accentGlow by animateColorAsState(palette.accentGlow, tween(700), label = "lyr_bg_glow")
    val deepBase by animateColorAsState(palette.deepBase, tween(700), label = "lyr_bg_deep")

    // 播放中按帧平滑推进相位；暂停或极简深黑模式下协程自动退出，零后台耗电
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, style) {
        if (!isPlaying || style == LyricsBackdropStyle.MINIMAL_DARK) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastNs != 0L) {
                    val dt = ((frameNs - lastNs) / 1_000_000_000f).coerceIn(0f, 0.08f)
                    phase = (phase + dt * 0.24f) % 628.3185f
                }
                lastNs = frameNs
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxDim = max(w, h).coerceAtLeast(1f)

        when (style) {
            LyricsBackdropStyle.FLUID_AURORA -> {
                // 基底深色渐变
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to deepBase,
                        0.55f to Color(0xFF0E0E10),
                        1.0f to Color(0xFF070708),
                    )
                )

                // 光晕球 1：专辑主色（左上 ↔ 中上慢游）
                val c1 = Offset(
                    x = w * (0.26f + 0.24f * sin(phase * 0.72f)),
                    y = h * (0.24f + 0.16f * cos(phase * 0.53f)),
                )
                val r1 = maxDim * (0.70f + 0.06f * sin(phase * 0.4f))
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to primary.copy(alpha = 0.88f),
                        0.52f to primary.copy(alpha = 0.36f),
                        1.0f to Color.Transparent,
                        center = c1,
                        radius = r1,
                    ),
                    radius = r1,
                    center = c1,
                )

                // 光晕球 2：专辑和谐次色（右下 ↔ 中部慢游）
                val c2 = Offset(
                    x = w * (0.76f + 0.22f * cos(phase * 0.61f + 1.7f)),
                    y = h * (0.68f + 0.18f * sin(phase * 0.47f + 0.9f)),
                )
                val r2 = maxDim * (0.66f + 0.07f * cos(phase * 0.37f))
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to secondary.copy(alpha = 0.82f),
                        0.50f to secondary.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                        center = c2,
                        radius = r2,
                    ),
                    radius = r2,
                    center = c2,
                )

                // 光晕球 3：高光呼吸晕（中上 ↔ 中右流转）
                val c3 = Offset(
                    x = w * (0.52f + 0.30f * sin(phase * 0.88f + 3.1f)),
                    y = h * (0.44f + 0.22f * cos(phase * 0.64f + 2.2f)),
                )
                val r3 = maxDim * (0.48f + 0.05f * sin(phase * 0.58f))
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to accentGlow.copy(alpha = 0.44f),
                        0.55f to accentGlow.copy(alpha = 0.14f),
                        1.0f to Color.Transparent,
                        center = c3,
                        radius = r3,
                    ),
                    radius = r3,
                    center = c3,
                )

                // 轻薄暗调阅读保护膜，保证任意亮色封面下纯白歌词依然高对比
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.28f),
                        0.45f to Color.Black.copy(alpha = 0.14f),
                        1.0f to Color.Black.copy(alpha = 0.48f),
                    )
                )
            }

            LyricsBackdropStyle.COVER_BLUR -> {
                drawRect(color = deepBase)
                if (blurredCover != null && blurredCover.width > 0 && blurredCover.height > 0) {
                    val breathe = 1.12f + 0.03f * sin(phase * 0.55f)
                    val coverAspect = blurredCover.width.toFloat() / blurredCover.height.toFloat()
                    val viewAspect = w / h.coerceAtLeast(1f)
                    val dstW: Float
                    val dstH: Float
                    if (viewAspect > coverAspect) {
                        dstW = w * breathe
                        dstH = dstW / coverAspect
                    } else {
                        dstH = h * breathe
                        dstW = dstH * coverAspect
                    }
                    val dx = ((w - dstW) * 0.5f + w * 0.02f * sin(phase * 0.42f)).roundToInt()
                    val dy = ((h - dstH) * 0.5f + h * 0.015f * cos(phase * 0.36f)).roundToInt()
                    drawImage(
                        image = blurredCover,
                        dstOffset = IntOffset(dx, dy),
                        dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1)),
                        alpha = 0.72f,
                        filterQuality = FilterQuality.High,
                    )
                } else {
                    // 无封面曲目时平滑回退到柔化双色径向渐变
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to primary.copy(alpha = 0.72f),
                            1.0f to Color.Transparent,
                            center = Offset(w * 0.5f, h * 0.36f),
                            radius = maxDim * 0.75f,
                        ),
                        radius = maxDim * 0.75f,
                        center = Offset(w * 0.5f, h * 0.36f),
                    )
                }
                // 电影暗角与可读性渐晕
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.50f),
                        0.42f to Color.Black.copy(alpha = 0.36f),
                        1.0f to Color.Black.copy(alpha = 0.76f),
                    )
                )
            }

            LyricsBackdropStyle.MINIMAL_DARK -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to primary.copy(alpha = 0.24f),
                        0.38f to colors.canvas,
                        1.0f to colors.canvas,
                    )
                )
            }
        }
    }
}
