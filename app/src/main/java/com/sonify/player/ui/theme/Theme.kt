package com.sonify.player.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.sonify.player.data.AccentStyle
import com.sonify.player.data.DarkCanvasStyle
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class HiFiColors(
    val canvas: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val exclusiveGreen: Color,
    val danger: Color,
)

val LocalHiFiColors = staticCompositionLocalOf {
    hiFiColorsFor(DarkCanvasStyle.SPOTIFY_CHARCOAL, AccentStyle.VINYL_GOLD)
}

fun hiFiColorsFor(canvasStyle: DarkCanvasStyle, accentStyle: AccentStyle): HiFiColors {
    val canvas = when (canvasStyle) {
        DarkCanvasStyle.SPOTIFY_CHARCOAL -> Color(0xFF121212)
        DarkCanvasStyle.OLED_BLACK -> Color(0xFF000000)
    }
    val surface1 = when (canvasStyle) {
        DarkCanvasStyle.SPOTIFY_CHARCOAL -> Color(0xFF181818)
        DarkCanvasStyle.OLED_BLACK -> Color(0xFF0D0D0F)
    }
    val surface2 = Color(0xFF242424)
    val surface3 = Color(0xFF2E2E2E)
    val (accent, onAccent) = when (accentStyle) {
        AccentStyle.VINYL_GOLD -> Color(0xFFE5A93C) to Color(0xFF140E02)
        AccentStyle.SPOTIFY_GREEN -> Color(0xFF1DB954) to Color(0xFF041A0B)
    }
    return HiFiColors(
        canvas = canvas,
        surface1 = surface1,
        surface2 = surface2,
        surface3 = surface3,
        hairline = Color(0x14FFFFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textMuted = Color(0xFF6A6A6A),
        accent = accent,
        onAccent = onAccent,
        exclusiveGreen = Color(0xFF1ED760),
        danger = Color(0xFFE85D5D),
    )
}

@Composable
fun HiFiPlayerTheme(
    canvasStyle: DarkCanvasStyle,
    accentStyle: AccentStyle,
    content: @Composable () -> Unit,
) {
    val colors = hiFiColorsFor(canvasStyle, accentStyle)
    val materialScheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.exclusiveGreen,
        background = colors.canvas,
        onBackground = colors.textPrimary,
        surface = colors.surface1,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.textSecondary,
        error = colors.danger,
    )

    CompositionLocalProvider(LocalHiFiColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography(),
            content = content,
        )
    }
}

/** 封面主色提取缓存，生成类似 Spotify 播放页和 MiniPlayer 的低饱和深色氛围色（支持本地与 Subsonic 远程封面） */
object AmbientColorExtractor {
    private val cache = ConcurrentHashMap<String, Color>()
    val DefaultAmbient = Color(0xFF2A241E)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var subsonicRepo: SubsonicRepository? = null

    fun init(context: Context, repo: SubsonicRepository) {
        appContext = context.applicationContext
        subsonicRepo = repo
    }

    suspend fun extract(coverPath: String?): Color {
        if (coverPath.isNullOrBlank()) return DefaultAmbient
        cache[coverPath]?.let { return it }

        val extracted = withContext(Dispatchers.IO) {
            val file = if (SubsonicUri.isCoverUri(coverPath)) {
                val ctx = appContext
                val repo = subsonicRepo
                if (ctx != null && repo != null) {
                    CoverArtSupport.fetchCoverFileSync(ctx, repo, coverPath, size = 300)
                } else null
            } else {
                File(coverPath).takeIf { it.isFile }
            } ?: return@withContext DefaultAmbient

            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                ?: return@withContext DefaultAmbient
            try {
                val palette = Palette.from(bmp).maximumColorCount(12).generate()
                val raw = palette.getDarkVibrantColor(
                    palette.getVibrantColor(
                        palette.getDarkMutedColor(
                            palette.getDominantColor(DefaultAmbient.toArgb())
                        )
                    )
                )
                // 压低亮度并适度收敛饱和度，保证纯白文字对比度始终清晰
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(raw, hsl)
                hsl[1] = hsl[1].coerceIn(0.18f, 0.58f)
                hsl[2] = hsl[2].coerceIn(0.16f, 0.28f)
                Color(ColorUtils.HSLToColor(hsl))
            } finally {
                bmp.recycle()
            }
        }
        cache[coverPath] = extracted
        return extracted
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
