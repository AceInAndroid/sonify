package com.sonify.player.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.sonify.player.data.DownloadStorageMode
import com.sonify.player.data.DownloadedTrackEntity
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.StreamQuality
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.UserPreferences
import com.sonify.player.library.AudioMetadataParser
import com.sonify.player.subsonic.SubsonicAuth
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.theme.CoverArtSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

enum class DownloadTaskStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class DownloadTaskItem(
    val track: TrackEntity,
    val status: DownloadTaskStatus = DownloadTaskStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else if (status == DownloadTaskStatus.COMPLETED) 1f else 0f
}

data class DownloadQueueState(
    val tasks: List<DownloadTaskItem> = emptyList(),
) {
    val activeCount: Int
        get() = tasks.count { it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING }
    val failedCount: Int
        get() = tasks.count { it.status == DownloadTaskStatus.FAILED }
}

sealed interface EnqueueDownloadResult {
    data class Started(val queuedCount: Int, val skippedAlreadyDownloaded: Int, val skippedLocal: Int) : EnqueueDownloadResult
    data class BlockedByWifiOnly(val tracks: List<TrackEntity>) : EnqueueDownloadResult
}

/**
 * 离线原盘无损下载管理器：
 * - 最多 2 路并发下载，采用 .part 原子写入防半截损坏；
 * - 默认按 /sdcard/Music/SonifyDownloads/<艺人>/<专辑>/<曲号> - <歌名>.<后缀> 归档（无写权限或偏好私有目录时自动落盘 App 私有目录）；
 * - 下载完成后登记 downloaded_tracks，播放远程曲目时由 SubsonicDataSourceFactory 零流量优先命中实现离线 Bit-Perfect 直通。
 */
class SonifyDownloadManager(
    private val context: Context,
    private val db: LibraryDatabase,
    private val prefs: UserPreferences,
    private val subsonicRepo: SubsonicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(permits = 2)
    private val downloadedDao = db.downloadedTrackDao()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val localPathCache = ConcurrentHashMap<String, String>()

    val downloadedList: StateFlow<List<DownloadedTrackEntity>> = downloadedDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val downloadsByPath: StateFlow<Map<String, DownloadedTrackEntity>> = downloadedList
        .map { list ->
            localPathCache.clear()
            list.forEach { localPathCache[it.remotePath] = it.localFilePath }
            list.associateBy { it.remotePath }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _queueState = MutableStateFlow(DownloadQueueState())
    val queueState: StateFlow<DownloadQueueState> = _queueState.asStateFlow()

    init {
        scope.launch {
            downloadedDao.getAll().forEach { localPathCache[it.remotePath] = it.localFilePath }
        }
    }

    /** 同步查询远程曲目是否已完整下载到本地（供 SubsonicDataSourceFactory 在 open() 时零延迟调用） */
    fun findLocalFileSync(remotePath: String): File? {
        val local = localPathCache[remotePath] ?: return null
        val f = File(local)
        return if (f.isFile && f.length() > 0L) f else null
    }

    fun isDownloaded(remotePath: String): Boolean = findLocalFileSync(remotePath) != null

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun enqueueTracks(
        tracks: List<TrackEntity>,
        allowCellularOverride: Boolean = false,
    ): EnqueueDownloadResult {
        val remoteOnly = tracks.distinctBy { it.path }.filter { SubsonicUri.isTrackUri(it.path) }
        val skippedLocal = tracks.distinctBy { it.path }.size - remoteOnly.size
        val toDownload = remoteOnly.filter { !isDownloaded(it.path) }
        val skippedDone = remoteOnly.size - toDownload.size

        if (toDownload.isEmpty()) {
            return EnqueueDownloadResult.Started(0, skippedDone, skippedLocal)
        }

        if (prefs.current.downloadWifiOnly && !allowCellularOverride && !isWifiConnected()) {
            return EnqueueDownloadResult.BlockedByWifiOnly(toDownload)
        }

        var queued = 0
        for (track in toDownload) {
            if (activeJobs.containsKey(track.path)) continue
            queued++
            upsertTask(DownloadTaskItem(track = track, status = DownloadTaskStatus.QUEUED))
            val job = scope.launch {
                try {
                    semaphore.withPermit {
                        executeDownload(track)
                    }
                } catch (_: CancellationException) {
                    removeTask(track.path)
                } finally {
                    activeJobs.remove(track.path)
                }
            }
            activeJobs[track.path] = job
        }
        return EnqueueDownloadResult.Started(queued, skippedDone, skippedLocal)
    }

    fun retryTask(remotePath: String) {
        val existing = _queueState.value.tasks.firstOrNull { it.track.path == remotePath } ?: return
        enqueueTracks(listOf(existing.track), allowCellularOverride = true)
    }

    fun cancelTask(remotePath: String) {
        activeJobs.remove(remotePath)?.cancel()
        removeTask(remotePath)
    }

    fun cancelAll() {
        val keys = activeJobs.keys.toList()
        keys.forEach { activeJobs.remove(it)?.cancel() }
        _queueState.update { st ->
            st.copy(tasks = st.tasks.filter { it.status == DownloadTaskStatus.COMPLETED })
        }
    }

    fun clearFinishedTasks() {
        _queueState.update { st ->
            st.copy(tasks = st.tasks.filter { it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING })
        }
    }

    suspend fun deleteDownloadedTrack(remotePath: String) = withContext(Dispatchers.IO) {
        val entity = downloadedDao.findByRemotePath(remotePath)
        if (entity != null) {
            runCatching { File(entity.localFilePath).delete() }
            db.trackDao().deleteByPaths(listOf(entity.localFilePath))
            downloadedDao.deleteByRemotePath(remotePath)
            localPathCache.remove(remotePath)
        }
        removeTask(remotePath)
    }

    suspend fun deleteAllDownloads() = withContext(Dispatchers.IO) {
        cancelAll()
        val all = downloadedDao.getAll()
        val localPaths = all.map { it.localFilePath }
        all.forEach { runCatching { File(it.localFilePath).delete() } }
        if (localPaths.isNotEmpty()) db.trackDao().deleteByPaths(localPaths)
        downloadedDao.deleteAll()
        localPathCache.clear()
        _queueState.value = DownloadQueueState()
    }

    private suspend fun executeDownload(track: TrackEntity) {
        val ref = SubsonicUri.parseTrackUri(track.path)
            ?: throw IOException("无效的远程曲目路径")
        val creds = subsonicRepo.getCredentials(ref.serverId)
            ?: throw IOException("未找到对应服务器账号凭据")

        val quality = prefs.current.downloadQuality
        val client = subsonicRepo.client.httpClientFor(creds.allowSelfSignedTls)

        updateTask(track.path) { it.copy(status = DownloadTaskStatus.DOWNLOADING, error = null) }

        val primaryUrl = SubsonicAuth.buildDownloadUrl(creds, ref.songId, quality)
        val fallbackUrl = if (quality == StreamQuality.RAW) {
            SubsonicAuth.buildStreamUrl(creds, ref.songId, StreamQuality.RAW)
        } else null

        var targetFile: File? = null
        var partFile: File? = null

        try {
            var response = client.newCall(
                Request.Builder().url(primaryUrl).header("User-Agent", "Sonify-Android/0.2").get().build()
            ).execute()

            val ctFirst = response.header("Content-Type").orEmpty()
            if ((!response.isSuccessful || ctFirst.contains("json", true) || ctFirst.contains("xml", true)) && fallbackUrl != null) {
                response.close()
                response = client.newCall(
                    Request.Builder().url(fallbackUrl).header("User-Agent", "Sonify-Android/0.2").get().build()
                ).execute()
            }

            response.use { resp ->
                val ct = resp.header("Content-Type").orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: 下载失败")
                }
                if (ct.contains("json", true) || ct.contains("xml", true)) {
                    throw IOException("服务器拒绝下载该曲目（可能未授予下载权限）")
                }

                val body = resp.body ?: throw IOException("空响应体")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: track.fileSize
                val ext = DownloadPathHelper.resolveExtension(track.format, quality, ct)
                val relPath = DownloadPathHelper.buildRelativePath(
                    artist = track.artist,
                    album = track.album,
                    trackNumber = track.trackNumber,
                    title = track.title,
                    extension = ext,
                )

                val rootDir = resolveDownloadRootDir()
                val dest = File(rootDir, relPath)
                dest.parentFile?.mkdirs()
                val part = File(dest.parentFile, "${dest.name}.part")
                targetFile = dest
                partFile = part

                val buf = ByteArray(64 * 1024)
                var copied = 0L
                var lastReportMs = 0L
                body.byteStream().use { input ->
                    FileOutputStream(part).use { out ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 160L) {
                                lastReportMs = now
                                updateTask(track.path) {
                                    it.copy(bytesDownloaded = copied, totalBytes = totalBytes)
                                }
                            }
                        }
                        out.fd.sync()
                    }
                }

                if (copied <= 0L) throw IOException("下载文件为空")
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }

                // 同步缓存封面图到本地，确保断网也能展示封面与氛围色
                val localCover = track.coverPath?.let { cp ->
                    if (SubsonicUri.isCoverUri(cp)) {
                        CoverArtSupport.fetchCoverFileSync(context, subsonicRepo, cp, 600)?.absolutePath
                    } else cp
                }

                val entity = DownloadedTrackEntity(
                    remotePath = track.path,
                    serverId = ref.serverId,
                    remoteId = ref.songId,
                    localFilePath = dest.absolutePath,
                    localCoverPath = localCover,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumArtist = track.albumArtist,
                    trackNumber = track.trackNumber,
                    discNumber = track.discNumber,
                    durationMs = track.durationMs,
                    sampleRate = track.sampleRate,
                    bitDepth = track.bitDepth,
                    channels = track.channels,
                    bitrateKbps = track.bitrateKbps,
                    format = ext.uppercase(),
                    fileSize = dest.length(),
                    quality = quality.name,
                    downloadedAt = System.currentTimeMillis(),
                )
                downloadedDao.upsert(entity)
                localPathCache[track.path] = dest.absolutePath

                // 若下载至公开 Music 目录，同步解析并纳入本地音乐库，便于本地模式与系统管理器立即可见
                if (prefs.current.downloadStorageMode == DownloadStorageMode.PUBLIC_MUSIC) {
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val parsedLocal = runCatching { AudioMetadataParser.parseFile(dest, coversDir) }.getOrNull()
                    if (parsedLocal != null) {
                        db.trackDao().upsert(
                            parsedLocal.copy(
                                coverPath = parsedLocal.coverPath ?: localCover,
                            )
                        )
                    }
                    runCatching {
                        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                    }
                }

                updateTask(track.path) {
                    it.copy(
                        status = DownloadTaskStatus.COMPLETED,
                        bytesDownloaded = dest.length(),
                        totalBytes = dest.length(),
                        error = null,
                    )
                }
                Log.i(TAG, "下载完成: ${track.title} -> ${dest.absolutePath} (${dest.length()} bytes)")
            }
        } catch (ce: CancellationException) {
            runCatching { partFile?.delete() }
            throw ce
        } catch (e: Exception) {
            runCatching { partFile?.delete() }
            if (targetFile?.length() == 0L) runCatching { targetFile?.delete() }
            Log.w(TAG, "下载失败 ${track.title}: ${e.message}")
            updateTask(track.path) {
                it.copy(status = DownloadTaskStatus.FAILED, error = e.message ?: "下载失败")
            }
        }
    }

    private fun resolveDownloadRootDir(): File {
        if (prefs.current.downloadStorageMode == DownloadStorageMode.PUBLIC_MUSIC) {
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val candidate = File(publicMusic, "SonifyDownloads")
            val writable = runCatching {
                candidate.mkdirs()
                val probe = File(candidate, ".sonify_write_probe")
                probe.writeText("ok")
                probe.delete()
                true
            }.getOrDefault(false)
            if (writable) return candidate
        }
        val appDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(appDir, "SonifyDownloads").apply { mkdirs() }
    }

    private fun upsertTask(item: DownloadTaskItem) {
        _queueState.update { st ->
            val filtered = st.tasks.filterNot { it.track.path == item.track.path }
            st.copy(tasks = listOf(item) + filtered)
        }
    }

    private fun updateTask(remotePath: String, transform: (DownloadTaskItem) -> DownloadTaskItem) {
        _queueState.update { st ->
            st.copy(
                tasks = st.tasks.map { if (it.track.path == remotePath) transform(it) else it }
            )
        }
    }

    private fun removeTask(remotePath: String) {
        _queueState.update { st ->
            st.copy(tasks = st.tasks.filterNot { it.track.path == remotePath })
        }
    }

    companion object {
        private const val TAG = "SonifyDownload"
    }
}
