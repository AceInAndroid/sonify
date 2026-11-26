package com.sonify.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.theme.CoverArtSupport
import java.io.File
import java.util.concurrent.Executors

/**
 * 通知栏与锁屏高清封面加载器：
 * - 直接在本进程内将本地/Subsonic 远程封面缩放为 512x512 Bitmap 传给 MediaSession，
 *   彻底避免 Android SystemUI 跨进程无法读取 file:// 或需认证 URI 导致通知栏黑块的问题；
 * - 无封面时绘制极简黑胶默认封面图，保持 Spotify Dark × 黑胶风格一致。
 */
@OptIn(UnstableApi::class)
class SonifyBitmapLoader(
    private val context: Context,
    private val subsonicRepo: SubsonicRepository? = null,
) : BitmapLoader {

    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    private val cache = LruCache<String, Bitmap>(16)

    @Volatile
    private var defaultVinylBitmap: Bitmap? = null

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            decodeScaledBytes(data) ?: getDefaultVinylArtwork()
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            val key = uri.toString()
            cache.get(key)?.let { return@submit it }

            val bmp = when (uri.scheme) {
                "file", null -> {
                    val path = uri.path
                    if (path != null && File(path).isFile) decodeScaledFile(path) else null
                }

                "content" -> runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { decodeScaledBytes(it) }

                SubsonicUri.COVER_SCHEME -> {
                    val repo = subsonicRepo
                    if (repo != null) {
                        CoverArtSupport.fetchCoverFileSync(context, repo, key, MAX_ART_PX)
                            ?.let { decodeScaledFile(it.absolutePath) }
                    } else null
                }

                else -> null
            } ?: getDefaultVinylArtwork()

            cache.put(key, bmp)
            bmp
        }

    private fun decodeScaledFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_ART_PX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(path, opts) ?: return null
        return centerCropSquare(raw, MAX_ART_PX)
    }

    private fun decodeScaledBytes(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_ART_PX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
        return centerCropSquare(raw, MAX_ART_PX)
    }

    private fun centerCropSquare(src: Bitmap, maxEdge: Int): Bitmap {
        val minSide = minOf(src.width, src.height)
        val x = (src.width - minSide) / 2
        val y = (src.height - minSide) / 2
        val cropped = if (src.width == src.height) src else Bitmap.createBitmap(src, x, y, minSide, minSide)
        if (cropped !== src) src.recycle()
        return if (cropped.width > maxEdge) {
            val scaled = Bitmap.createScaledBitmap(cropped, maxEdge, maxEdge, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        } else cropped
    }

    private fun getDefaultVinylArtwork(): Bitmap {
        defaultVinylBitmap?.let { return it }
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        canvas.drawColor(Color.parseColor("#181818"))

        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, size * 0.44f,
                intArrayOf(Color.parseColor("#1F1F24"), Color.parseColor("#0B0B0D")),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, size * 0.42f, discPaint)

        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(18, 255, 255, 255)
            strokeWidth = 1.2f
        }
        for (i in 0 until 8) {
            val r = size * (0.20f + i * 0.028f)
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5A93C")
        }
        canvas.drawCircle(cx, cy, size * 0.16f, labelPaint)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121212")
        }
        canvas.drawCircle(cx, cy, size * 0.045f, holePaint)

        defaultVinylBitmap = bmp
        return bmp
    }

    companion object {
        const val MAX_ART_PX = 512

        fun computeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            val longest = maxOf(width, height)
            while (longest / (sample * 2) >= maxEdge) {
                sample *= 2
            }
            return sample
        }
    }
}
