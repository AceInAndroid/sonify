package com.sonify.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.AppSettings
import com.sonify.player.data.LyricsAlignment
import com.sonify.player.data.LyricsBackdropStyle
import com.sonify.player.data.LyricsFontFamily
import com.sonify.player.data.LyricsHighlightStyle
import com.sonify.player.data.LyricsLineSpacing
import com.sonify.player.ui.theme.LocalHiFiColors
import kotlin.math.roundToInt

/**
 * 歌词样式实时回调聚合接口，减少跨层参数传递冗余。
 */
data class LyricsStyleActions(
    val onChangeFontSizeSp: (Int) -> Unit = {},
    val onChangeFontFamily: (LyricsFontFamily) -> Unit = {},
    val onChangeHighlightStyle: (LyricsHighlightStyle) -> Unit = {},
    val onChangeBackdropStyle: (LyricsBackdropStyle) -> Unit = {},
    val onChangeLineSpacing: (LyricsLineSpacing) -> Unit = {},
    val onChangeAlignment: (LyricsAlignment) -> Unit = {},
    val onToggleBoldActive: (Boolean) -> Unit = {},
    val onToggleShowTranslation: (Boolean) -> Unit = {},
    val onToggleAutoHideControls: (Boolean) -> Unit = {},
    val onResetDefaults: () -> Unit = {},
)

/**
 * 歌词页底部悬浮「半屏实时样式调参面板」：
 * 不使用遮蔽全屏的黑底 ModalBottomSheet，让用户在调节字体、字号、高亮、背景氛围时，
 * 上方正在流动的高亮歌词所见即所得实时变换！
 */
@Composable
fun LyricsLiveStylePanel(
    settings: AppSettings,
    actions: LyricsStyleActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Color(0xF7141417))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        // 顶部把手与标题栏
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "沉浸歌词样式",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "调节参数，上方歌词实时生效",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { actions.onResetDefaults() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("默认", fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 沉浸背景氛围
            StyleSection("氛围背景") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsBackdropStyle.entries.forEach { bg ->
                        val shortLabel = when (bg) {
                            LyricsBackdropStyle.FLUID_AURORA -> "流体多色光晕"
                            LyricsBackdropStyle.COVER_BLUR -> "封面暗调虚化"
                            LyricsBackdropStyle.MINIMAL_DARK -> "极简深黑"
                        }
                        StyleOptionChip(
                            label = shortLabel,
                            selected = settings.lyricsBackdropStyle == bg,
                            onClick = { actions.onChangeBackdropStyle(bg) },
                        )
                    }
                }
            }

            // 2. 高亮动效
            StyleSection("高亮动效") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsHighlightStyle.entries.forEach { hl ->
                        val shortLabel = when (hl) {
                            LyricsHighlightStyle.PROGRESSIVE_SWEEP -> "逐句流光扫色"
                            LyricsHighlightStyle.GLOW_PURE -> "柔光纯白"
                            LyricsHighlightStyle.ACCENT_TINT -> "主题强调色"
                            LyricsHighlightStyle.CAPSULE_FOCUS -> "玻璃聚焦条"
                        }
                        StyleOptionChip(
                            label = shortLabel,
                            selected = settings.lyricsHighlightStyle == hl,
                            onClick = { actions.onChangeHighlightStyle(hl) },
                        )
                    }
                }
            }

            // 3. 字体风格（芯片本身即采用该字体族渲染）
            StyleSection("歌词字体") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LyricsFontFamily.entries.forEach { ff ->
                        val shortLabel = when (ff) {
                            LyricsFontFamily.SANS_MODERN -> "现代无衬线"
                            LyricsFontFamily.SERIF_EDITORIAL -> "文艺黑胶衬线"
                            LyricsFontFamily.ROUNDED_SOFT -> "温润舒展"
                            LyricsFontFamily.MONO_STUDIO -> "录音室等宽"
                        }
                        StyleOptionChip(
                            label = shortLabel,
                            selected = settings.lyricsFontFamily == ff,
                            fontFamily = ff.toComposeFontFamily(),
                            onClick = { actions.onChangeFontFamily(ff) },
                        )
                    }
                }
            }

            // 4. 连续字号滑块（16..32sp）
            StyleSection("字号大小", trailing = "${settings.lyricsFontSizeSp} sp") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("A", fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    Slider(
                        value = settings.lyricsFontSizeSp.toFloat(),
                        onValueChange = { actions.onChangeFontSizeSp(it.roundToInt()) },
                        valueRange = 16f..32f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text("A", fontSize = 19.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }

            // 5. 行距与对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("行距留白", fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LyricsLineSpacing.entries.forEach { sp ->
                            val lbl = when (sp) {
                                LyricsLineSpacing.COMPACT -> "紧凑"
                                LyricsLineSpacing.COMFORTABLE -> "舒展"
                                LyricsLineSpacing.RELAXED -> "宽松"
                            }
                            StyleOptionChip(
                                label = lbl,
                                selected = settings.lyricsLineSpacing == sp,
                                onClick = { actions.onChangeLineSpacing(sp) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("排版对齐", fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AlignIconChip(
                            icon = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                            selected = settings.lyricsAlignment == LyricsAlignment.LEFT,
                            onClick = { actions.onChangeAlignment(LyricsAlignment.LEFT) },
                        )
                        AlignIconChip(
                            icon = Icons.Rounded.FormatAlignCenter,
                            selected = settings.lyricsAlignment == LyricsAlignment.CENTER,
                            onClick = { actions.onChangeAlignment(LyricsAlignment.CENTER) },
                        )
                    }
                }
            }

            // 6. 精细开关组
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactToggleRow(
                    label = "当前演唱句加粗强调",
                    checked = settings.lyricsBoldActive,
                    onCheckedChange = actions.onToggleBoldActive,
                )
                CompactToggleRow(
                    label = "显示双语歌词译文",
                    checked = settings.lyricsShowTranslation,
                    onCheckedChange = actions.onToggleShowTranslation,
                )
                CompactToggleRow(
                    label = "沉浸纯享态（5 秒无操作隐藏控件）",
                    checked = settings.lyricsAutoHideControls,
                    onCheckedChange = actions.onToggleAutoHideControls,
                )
            }
        }
    }
}

@Composable
private fun StyleSection(
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
            if (trailing != null) {
                Text(
                    text = trailing,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun StyleOptionChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent else Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onAccent else colors.textPrimary,
        )
    }
}

@Composable
private fun AlignIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent else Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.onAccent else colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CompactToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surface3,
            ),
        )
    }
}
