package com.sonify.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.AppSettings
import com.sonify.player.data.LyricsAlignment
import com.sonify.player.data.LyricsFontFamily
import com.sonify.player.data.LyricsHighlightStyle
import com.sonify.player.lyrics.InterludeState
import com.sonify.player.ui.theme.AmbientPalette
import com.sonify.player.ui.theme.LocalHiFiColors

fun LyricsFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    LyricsFontFamily.SANS_MODERN -> FontFamily.SansSerif
    LyricsFontFamily.SERIF_EDITORIAL -> FontFamily.Serif
    LyricsFontFamily.ROUNDED_SOFT -> FontFamily.SansSerif
    LyricsFontFamily.MONO_STUDIO -> FontFamily.Monospace
}

fun LyricsFontFamily.letterSpacingSp(): Float = when (this) {
    LyricsFontFamily.SANS_MODERN -> 0.1f
    LyricsFontFamily.SERIF_EDITORIAL -> 0.45f
    LyricsFontFamily.ROUNDED_SOFT -> 0.85f
    LyricsFontFamily.MONO_STUDIO -> -0.25f
}

/**
 * 当前演唱句的多风格高亮文本渲染器：
 * - PROGRESSIVE_SWEEP：逐句（或逐字）从左向右羽化流光扫色，支持多行自动折行接力点亮，兼容全版本 Android；
 * - GLOW_PURE：柔光纯白 + 专辑主色外发光阴影；
 * - ACCENT_TINT：主题强调色（黑胶暖金 / Spotify 翠绿）点亮；
 * - CAPSULE_FOCUS：暗调半透明磨砂聚焦胶囊框。
 */
@Composable
fun ActiveLyricText(
    text: String,
    highlightStyle: LyricsHighlightStyle,
    sweepProgress: Float,
    fontSizeSp: Float,
    lineHeightSp: Float,
    fontFamily: LyricsFontFamily,
    boldActive: Boolean,
    textAlign: TextAlign,
    glowColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val composeFont = fontFamily.toComposeFontFamily()
    val letterSpacing = fontFamily.letterSpacingSp().sp
    val activeWeight = when {
        boldActive && fontFamily == LyricsFontFamily.ROUNDED_SOFT -> FontWeight.SemiBold
        boldActive -> FontWeight.Bold
        else -> FontWeight.Medium
    }

    when (highlightStyle) {
        LyricsHighlightStyle.PROGRESSIVE_SWEEP -> {
            var layoutResult by remember(text, fontSizeSp, fontFamily, textAlign, boldActive) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            val baseStyle = TextStyle(
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeightSp.sp,
                fontFamily = composeFont,
                fontWeight = activeWeight,
                letterSpacing = letterSpacing,
                textAlign = textAlign,
            )

            Box(modifier = modifier) {
                // 底层待演唱半透底字
                Text(
                    text = text,
                    style = baseStyle.copy(color = Color.White.copy(alpha = 0.36f)),
                    onTextLayout = { layoutResult = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                // 顶层纯白微光扫色字（按多行实际布局宽度逐行接力 DstIn 羽化裁切）
                Text(
                    text = text,
                    style = baseStyle.copy(
                        color = Color.White,
                        shadow = Shadow(
                            color = glowColor.copy(alpha = 0.55f),
                            offset = Offset.Zero,
                            blurRadius = 14f,
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val lr = layoutResult ?: return@drawWithContent
                            val lineCount = lr.lineCount
                            if (lineCount <= 0) return@drawWithContent

                            var totalW = 0f
                            val lineWidths = FloatArray(lineCount) { i ->
                                val w = (lr.getLineRight(i) - lr.getLineLeft(i)).coerceAtLeast(1f)
                                totalW += w
                                w
                            }
                            val targetPx = totalW * sweepProgress.coerceIn(0f, 1f)
                            var accW = 0f

                            for (i in 0 until lineCount) {
                                val lineLeft = lr.getLineLeft(i)
                                val lineW = lineWidths[i]
                                val yTop = if (i == 0) 0f else lr.getLineTop(i)
                                val yBottom = if (i == lineCount - 1) size.height else lr.getLineBottom(i)
                                val stripH = (yBottom - yTop).coerceAtLeast(1f)
                                val litW = (targetPx - accW).coerceIn(0f, lineW)

                                when {
                                    litW >= lineW - 0.5f -> {
                                        // 整行已完全点亮，保持 100% alpha
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(0f, yTop),
                                            size = Size(size.width, stripH),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                    litW <= 0.5f -> {
                                        // 整行尚未扫到，清为透明露出底层半透底字
                                        drawRect(
                                            color = Color.Transparent,
                                            topLeft = Offset(0f, yTop),
                                            size = Size(size.width, stripH),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                    else -> {
                                        val feather = (lineW * 0.14f).coerceIn(14f, 44f)
                                        val sweepX = lineLeft + litW
                                        val startFrac = ((sweepX - feather * 0.45f) / size.width.coerceAtLeast(1f))
                                            .coerceIn(0f, 0.99f)
                                        val endFrac = ((sweepX + feather * 0.55f) / size.width.coerceAtLeast(1f))
                                            .coerceIn(startFrac + 0.005f, 1f)
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.White,
                                                startFrac to Color.White,
                                                endFrac to Color.Transparent,
                                                1f to Color.Transparent,
                                            ),
                                            topLeft = Offset(0f, yTop),
                                            size = Size(size.width, stripH),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }
                                accW += lineW
                            }
                        },
                )
            }
        }

        LyricsHighlightStyle.GLOW_PURE -> {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp.sp,
                    fontFamily = composeFont,
                    fontWeight = activeWeight,
                    letterSpacing = letterSpacing,
                    textAlign = textAlign,
                    color = Color.White,
                    shadow = Shadow(
                        color = glowColor.copy(alpha = 0.72f),
                        offset = Offset.Zero,
                        blurRadius = 20f,
                    ),
                ),
                modifier = modifier.fillMaxWidth(),
            )
        }

        LyricsHighlightStyle.ACCENT_TINT -> {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = fontSizeSp.sp,
                    lineHeight = lineHeightSp.sp,
                    fontFamily = composeFont,
                    fontWeight = activeWeight,
                    letterSpacing = letterSpacing,
                    textAlign = textAlign,
                    color = accentColor,
                    shadow = Shadow(
                        color = accentColor.copy(alpha = 0.45f),
                        offset = Offset.Zero,
                        blurRadius = 16f,
                    ),
                ),
                modifier = modifier.fillMaxWidth(),
            )
        }

        LyricsHighlightStyle.CAPSULE_FOCUS -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.11f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSizeSp.sp,
                        lineHeight = lineHeightSp.sp,
                        fontFamily = composeFont,
                        fontWeight = activeWeight,
                        letterSpacing = letterSpacing,
                        textAlign = textAlign,
                        color = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 前奏 / 长器乐间奏呼吸三点（• • •），随间奏倒计时依次柔光点亮。
 */
@Composable
fun InterludeDotsIndicator(
    state: InterludeState,
    tint: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val arrangement = if (alignment == Alignment.CenterHorizontally) {
        Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally)
    } else {
        Arrangement.spacedBy(9.dp, Alignment.Start)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.dotAlphas.forEach { alpha ->
            val scale = 0.80f + 0.32f * alpha
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

/**
 * 设置页顶部内置「歌词沉浸样式实时预览卡片」：
 * 动态演示流体光晕 / 逐句流光扫色 / 字体 / 字号 / 行距 / 对齐 / 加粗 / 双语译文效果。
 */
@Composable
fun LyricsSettingsPreviewCard(
    settings: AppSettings,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    val previewPalette = remember {
        AmbientPalette(
            primary = Color(0xFF513322),
            secondary = Color(0xFF1E3247),
            accentGlow = Color(0xFF7D5029),
            deepBase = Color(0xFF0E0D0C),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "preview_sweep")
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "preview_sweep_frac",
    )

    val textAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) TextAlign.Center else TextAlign.Start
    val hAlign = if (settings.lyricsAlignment == LyricsAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start
    // 卡片空间紧凑，按 0.80 比例微缩呈现真实视觉层次
    val activeSp = (settings.lyricsFontSizeSp * 0.82f).coerceIn(14f, 25f)
    val inactiveSp = (activeSp * 0.78f).coerceAtLeast(11.5f)
    val composeFont = settings.lyricsFontFamily.toComposeFontFamily()
    val letterSpacing = settings.lyricsFontFamily.letterSpacingSp().sp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
    ) {
        ImmersiveLyricsBackdrop(
            style = settings.lyricsBackdropStyle,
            palette = previewPalette,
            blurredCover = null,
            isPlaying = true,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "实时预览 · ${settings.lyricsFontSizeSp}sp",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable { onResetDefaults() }
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "恢复默认",
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("恢复默认", fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.Medium)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = hAlign,
                verticalArrangement = Arrangement.spacedBy((settings.lyricsLineSpacing.itemGapDp * 0.45f).dp),
            ) {
                Text(
                    text = "针尖轻落入黑胶微光",
                    style = TextStyle(
                        fontSize = inactiveSp.sp,
                        fontFamily = composeFont,
                        letterSpacing = letterSpacing,
                        color = Color.White.copy(alpha = 0.34f),
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(horizontalAlignment = hAlign, modifier = Modifier.fillMaxWidth()) {
                    ActiveLyricText(
                        text = "旋律与光影在夜色里缓缓相融",
                        highlightStyle = settings.lyricsHighlightStyle,
                        sweepProgress = sweep,
                        fontSizeSp = activeSp,
                        lineHeightSp = activeSp * settings.lyricsLineSpacing.lineHeightMultiplier * 0.92f,
                        fontFamily = settings.lyricsFontFamily,
                        boldActive = settings.lyricsBoldActive,
                        textAlign = textAlign,
                        glowColor = previewPalette.accentGlow,
                        accentColor = colors.accent,
                    )
                    if (settings.lyricsShowTranslation) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Melody and light gently dissolve into the night",
                            style = TextStyle(
                                fontSize = (activeSp * 0.66f).sp,
                                fontFamily = composeFont,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = textAlign,
                            ),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    text = "每一帧采样都还原现场呼吸",
                    style = TextStyle(
                        fontSize = inactiveSp.sp,
                        fontFamily = composeFont,
                        letterSpacing = letterSpacing,
                        color = Color.White.copy(alpha = 0.40f),
                        textAlign = textAlign,
                    ),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
