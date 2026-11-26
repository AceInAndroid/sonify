package com.sonify.player.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 歌单自定义封面裁剪与持久化工具：
 * 将系统相册选取的图片居中裁剪为 600×600 正方形 JPEG，落盘于 filesDir/playlist_covers/。
 */
object PlaylistCoverHelper {

    private const val DIR_NAME = "playlist_covers"
    private const val MAX_EDGE = 600

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    suspend fun saveCustomCover(context: Context, playlistId: Long, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / (sample * 2) >= MAX_EDGE) sample *= 2

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    ?: return@withContext null

                val minSide = minOf(raw.width, raw.height)
                val x = (raw.width - minSide) / 2
                val y = (raw.height - minSide) / 2
                val cropped = if (raw.width == raw.height) raw else Bitmap.createBitmap(raw, x, y, minSide, minSide)
                if (cropped !== raw) raw.recycle()

                val finalBmp = if (cropped.width > MAX_EDGE) {
                    Bitmap.createScaledBitmap(cropped, MAX_EDGE, MAX_EDGE, true).also {
                        if (it !== cropped) cropped.recycle()
                    }
                } else cropped

                val outFile = File(dir(context), "pl_${playlistId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                }
                finalBmp.recycle()
                outFile.absolutePath
            }.getOrNull()
        }

    fun deleteCoverFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val f = File(path)
            if (f.isFile && f.parentFile?.name == DIR_NAME) f.delete()
        }
    }
}
