package com.sonify.player.ui.theme

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.sonify.player.subsonic.SubsonicAuth
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.security.MessageDigest

/**
 * 统一封面模型解析与 Subsonic 远程封面磁盘缓存：
 * - 本地文件返回 File 对象，保持原有零拷贝直读；
 * - 远程 subsonic-cover://<serverId>/<coverArtId> 走统一磁盘缓存（cacheDir/subsonic_covers），
 *   供 Coil UI、SonifyBitmapLoader（通知栏/锁屏）与 AmbientColorExtractor（氛围取色）共用。
 */
object CoverArtSupport {

    private const val CACHE_DIR_NAME = "subsonic_covers"

    fun resolveCoilModel(coverPath: String?): Any? {
        if (coverPath.isNullOrBlank()) return null
        if (SubsonicUri.isCoverUri(coverPath) ||
            coverPath.startsWith("http://", ignoreCase = true) ||
            coverPath.startsWith("https://", ignoreCase = true)
        ) {
            return coverPath
        }
        val f = File(coverPath)
        return if (f.isFile) f else null
    }

    fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    private fun cacheFileFor(context: Context, serverId: Long, coverArtId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(coverArtId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(cacheDir(context), "s${serverId}_$digest.img")
    }

    fun fetchCoverFileSync(
        context: Context,
        repo: SubsonicRepository,
        coverUri: String,
        size: Int = 600,
    ): File? {
        val ref = SubsonicUri.parseCoverUri(coverUri) ?: return null
        val target = cacheFileFor(context, ref.serverId, ref.coverArtId)
        if (target.isFile && target.length() > 0L) return target

        val creds = repo.getCredentialsSync(ref.serverId)
            ?: runBlocking { repo.getCredentials(ref.serverId) }
            ?: return null

        val url = SubsonicAuth.buildCoverArtUrl(creds, ref.coverArtId, size)
        val bytes = runCatching {
            repo.client.fetchBinarySync(url, creds.allowSelfSignedTls)
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null

        return runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }.getOrNull()
    }

    suspend fun resolveLocalOrCachedFile(
        context: Context,
        repo: SubsonicRepository,
        coverPath: String?,
        size: Int = 600,
    ): File? = withContext(Dispatchers.IO) {
        if (coverPath.isNullOrBlank()) return@withContext null
        if (SubsonicUri.isCoverUri(coverPath)) {
            fetchCoverFileSync(context, repo, coverPath, size)
        } else {
            File(coverPath).takeIf { it.isFile }
        }
    }

    fun getCoverCacheSizeBytes(context: Context): Long {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.isDirectory) return 0L
        return dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    fun clearAllCoverCache(context: Context) {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun clearServerCoverCache(context: Context, serverId: Long) {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        val prefix = "s${serverId}_"
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix)) runCatching { f.delete() }
        }
    }
}

/** Coil 自定义 Fetcher：透明拦截 subsonic-cover:// URI，按需下载并缓存至本地磁盘 */
class SubsonicCoverFetcher(
    private val context: Context,
    private val repo: SubsonicRepository,
    private val uriString: String,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val file = CoverArtSupport.fetchCoverFileSync(context, repo, uriString, size = 600)
            ?: return@withContext null
        SourceResult(
            source = ImageSource(file = file.toOkioPath()),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val context: Context,
        private val repo: SubsonicRepository,
    ) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!SubsonicUri.isCoverUri(data)) return null
            return SubsonicCoverFetcher(context, repo, data)
        }
    }
}
