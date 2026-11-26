package com.sonify.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import java.util.LinkedHashMap
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

/**
 * 专辑封面提取的 4 色沉浸氛围调色板：
 * 供播放页动态背景与沉浸式歌词流体多色光晕共享，保证纯白歌词始终高对比可读。
 */
data class AmbientPalette(
    val primary: Color,
    val secondary: Color,
    val accentGlow: Color,
    val deepBase: Color,
)

/** 封面主色/多色调色板与柔化封面提取缓存（支持本地与 Subsonic 远程封面，全版本 Android 兼容） */
object AmbientColorExtractor {
    private val paletteCache = ConcurrentHashMap<String, AmbientPalette>()
    private val blurredCoverCache = object : LinkedHashMap<String, ImageBitmap>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 8
    }

    val DefaultAmbient = Color(0xFF2A241E)
    val DefaultPalette = AmbientPalette(
        primary = Color(0xFF3A2A1E),
        secondary = Color(0xFF222938),
        accentGlow = Color(0xFF563D24),
        deepBase = Color(0xFF0E0D0C),
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var subsonicRepo: SubsonicRepository? = null

    fun init(context: Context, repo: SubsonicRepository) {
        appContext = context.applicationContext
        subsonicRepo = repo
    }

    suspend fun extract(coverPath: String?): Color = extractPalette(coverPath).primary

    suspend fun extractPalette(coverPath: String?): AmbientPalette {
        if (coverPath.isNullOrBlank()) return DefaultPalette
        paletteCache[coverPath]?.let { return it }

        val extracted = withContext(Dispatchers.IO) {
            val file = resolveCoverFile(coverPath) ?: return@withContext DefaultPalette
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                ?: return@withContext DefaultPalette
            try {
                val palette = Palette.from(bmp).maximumColorCount(16).generate()
                val dominant = palette.getDominantColor(DefaultAmbient.toArgb())
                val rawPrimary = palette.getDarkVibrantColor(
                    palette.getVibrantColor(
                        palette.getDarkMutedColor(dominant)
                    )
                )
                val rawSecondary = palette.getMutedColor(
                    palette.getDarkMutedColor(
                        palette.getLightVibrantColor(shiftHue(rawPrimary, 42f))
                    )
                )
                val rawGlow = palette.getVibrantColor(
                    palette.getLightVibrantColor(
                        palette.getLightMutedColor(shiftHue(rawPrimary, -32f))
                    )
                )

                val primary = tuneHsl(rawPrimary, minS = 0.20f, maxS = 0.60f, minL = 0.17f, maxL = 0.29f)
                val secondaryCandidate = if (hueDistance(rawPrimary, rawSecondary) < 18f) {
                    shiftHue(rawPrimary, 46f)
                } else rawSecondary
                val secondary = tuneHsl(secondaryCandidate, minS = 0.22f, maxS = 0.58f, minL = 0.15f, maxL = 0.27f)
                val accentGlow = tuneHsl(rawGlow, minS = 0.30f, maxS = 0.68f, minL = 0.24f, maxL = 0.38f)
                val deepBase = tuneHsl(rawPrimary, minS = 0.14f, maxS = 0.34f, minL = 0.05f, maxL = 0.09f)

                AmbientPalette(
                    primary = primary,
                    secondary = secondary,
                    accentGlow = accentGlow,
                    deepBase = deepBase,
                )
            } finally {
                bmp.recycle()
            }
        }
        paletteCache[coverPath] = extracted
        return extracted
    }

    /**
     * 解码并生成 64×64 柔化封面底图（纯 Kotlin 2-pass 分离盒式模糊），
     * 即使在 Android 10 (API 29) 不支持 RenderEffect 模糊的设备上也能流畅呈现高级封面虚化背景。
     */
    suspend fun extractBlurredCover(coverPath: String?): ImageBitmap? {
        if (coverPath.isNullOrBlank()) return null
        synchronized(blurredCoverCache) {
            blurredCoverCache[coverPath]?.let { return it }
        }
        val imageBmp = withContext(Dispatchers.IO) {
            val file = resolveCoverFile(coverPath) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (maxDim / (sample * 2) >= 96) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                ?: return@withContext null
            val scaled = Bitmap.createScaledBitmap(decoded, 64, 64, true)
            if (scaled !== decoded) decoded.recycle()
            try {
                fastBoxBlur(scaled, radius = 6, passes = 2)
                scaled.asImageBitmap()
            } catch (_: Throwable) {
                scaled.recycle()
                null
            }
        } ?: return null

        synchronized(blurredCoverCache) {
            blurredCoverCache[coverPath] = imageBmp
        }
        return imageBmp
    }

    private fun resolveCoverFile(coverPath: String): File? {
        return if (SubsonicUri.isCoverUri(coverPath)) {
            val ctx = appContext
            val repo = subsonicRepo
            if (ctx != null && repo != null) {
                CoverArtSupport.fetchCoverFileSync(ctx, repo, coverPath, size = 300)
            } else null
        } else {
            File(coverPath).takeIf { it.isFile }
        }
    }

    private fun tuneHsl(argb: Int, minS: Float, maxS: Float, minL: Float, maxL: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceIn(minS, maxS)
        hsl[2] = hsl[2].coerceIn(minL, maxL)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    private fun shiftHue(argb: Int, deltaDeg: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[0] = ((hsl[0] + deltaDeg) % 360f + 360f) % 360f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun hueDistance(a: Int, b: Int): Float {
        val ha = FloatArray(3).also { ColorUtils.colorToHSL(a, it) }[0]
        val hb = FloatArray(3).also { ColorUtils.colorToHSL(b, it) }[0]
        val diff = kotlin.math.abs(ha - hb) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    /** 针对 64×64 小图的高速分离盒式模糊（耗时 < 1ms） */
    private fun fastBoxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        val tmp = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val div = radius * 2 + 1

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val row = y * w
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dx in -radius..radius) {
                    val c = pix[row + dx.coerceIn(0, w - 1)]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    tmp[row + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val oldC = pix[row + (x - radius).coerceIn(0, w - 1)]
                    val newC = pix[row + (x + radius + 1).coerceIn(0, w - 1)]
                    rSum += ((newC ushr 16) and 0xFF) - ((oldC ushr 16) and 0xFF)
                    gSum += ((newC ushr 8) and 0xFF) - ((oldC ushr 8) and 0xFF)
                    bSum += (newC and 0xFF) - (oldC and 0xFF)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dy in -radius..radius) {
                    val c = tmp[dy.coerceIn(0, h - 1) * w + x]
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    pix[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                    val oldC = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                    val newC = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                    rSum += ((newC ushr 16) and 0xFF) - ((oldC ushr 16) and 0xFF)
                    gSum += ((newC ushr 8) and 0xFF) - ((oldC ushr 8) and 0xFF)
                    bSum += (newC and 0xFF) - (oldC and 0xFF)
                }
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
