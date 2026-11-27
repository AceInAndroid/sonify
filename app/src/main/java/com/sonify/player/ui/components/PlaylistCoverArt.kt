package com.sonify.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonify.player.R
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors

/**
 * Spotify 风格歌单封面组件：
 * 1. 若设置了相册自定义封面 → 显示自定义高清封面；
 * 2. 否则若有 >= 4 张不同专辑封面 → 自动拼出 2×2 四宫格封面；
 * 3. 否则若有 1~3 张封面 → 显示首张高清封面；
 * 4. 无任何封面 → 呈现暗调黑胶暖金渐变默认占位。
 */
@Composable
fun PlaylistCoverArt(
    customCoverPath: String?,
    autoCovers: List<String>,
    modifier: Modifier = Modifier,
    cornerDp: Int = 8,
) {
    val colors = LocalHiFiColors.current
    val customModel = CoverArtSupport.resolveCoilModel(customCoverPath)
    val resolvedAutos = autoCovers.mapNotNull { CoverArtSupport.resolveCoilModel(it) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surface2,
                        colors.accent.copy(alpha = 0.22f),
                        colors.surface1,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            customModel != null -> {
                AsyncImage(
                    model = customModel,
                    contentDescription = stringResource(R.string.playlist_cover_custom_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            resolvedAutos.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CollageTile(resolvedAutos[0], Modifier.weight(1f).fillMaxHeight())
                        CollageTile(resolvedAutos[1], Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CollageTile(resolvedAutos[2], Modifier.weight(1f).fillMaxHeight())
                        CollageTile(resolvedAutos[3], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }

            resolvedAutos.isNotEmpty() -> {
                AsyncImage(
                    model = resolvedAutos.first(),
                    contentDescription = stringResource(R.string.playlist_cover_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = colors.accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun CollageTile(model: Any, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF1C1C1F))) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
