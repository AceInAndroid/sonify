package com.sonify.player.library

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import com.sonify.player.HiFiApp
import com.sonify.player.R
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ScanState(
    val isScanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val addedOrUpdated: Int = 0,
    val currentFile: String = "",
    val lastSummary: String = "",
)

class MediaScanner(
    private val context: Context,
    private val db: LibraryDatabase,
    private val prefs: UserPreferences,
) {
    private val coversDir = File(context.filesDir, "covers")
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private fun s(@StringRes id: Int, vararg args: Any): String {
        val ctx = (context.applicationContext as? HiFiApp)?.localizedContext() ?: context
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    @Volatile
    private var scanJob: Job? = null

    fun cancelScan() {
        scanJob?.cancel()
    }

    /** 增量递归扫描全部已配置目录 */
    suspend fun scanAllConfiguredDirs() = withContext(Dispatchers.IO) {
        if (_state.value.isScanning) return@withContext
        val settings = prefs.current
        val minDurationMs = settings.minDurationFilter.minMs

        _state.value = ScanState(isScanning = true, currentFile = s(R.string.lib_scanning_traversing))

        try {
            coroutineScope {
                scanJob = coroutineContext[Job]

                val candidates = mutableListOf<File>()
                val visitedCanonical = HashSet<String>()

                for (dirPath in settings.scanDirs) {
                    val root = File(dirPath)
                    if (root.isDirectory && root.canRead()) {
                        collectAudioFiles(root, candidates, visitedCanonical)
                    }
                }
                // 同时扫描 App 私有导入目录，确保 SAF 导入的曲目不会丢
                val privateMusic = File(context.filesDir, "ImportedMusic")
                if (privateMusic.isDirectory) {
                    collectAudioFiles(privateMusic, candidates, visitedCanonical)
                }

                val existingMap = db.trackDao().getAllFingerprints().associateBy { it.path }
                val seenPaths = HashSet<String>(candidates.size)
                val toUpsert = ArrayList<TrackEntity>(64)

                var done = 0
                var updated = 0
                val total = candidates.size
                _state.value = _state.value.copy(total = total)

                for (file in candidates) {
                    if (!isActive) throw CancellationException()
                    val path = file.absolutePath
                    seenPaths.add(path)
                    done++

                    val prev = existingMap[path]
                    if (prev != null &&
                        prev.fileSize == file.length() &&
                        prev.lastModified == file.lastModified()
                    ) {
                        if (done % 25 == 0 || done == total) {
                            _state.value = _state.value.copy(
                                scanned = done,
                                currentFile = file.name,
                            )
                        }
                        continue
                    }

                    _state.value = _state.value.copy(
                        scanned = done,
                        currentFile = file.name,
                    )

                    val entity = AudioMetadataParser.parseFile(file, coversDir) ?: continue
                    if (minDurationMs > 0 && entity.durationMs in 1 until minDurationMs) {
                        continue
                    }

                    val merged = if (prev != null) {
                        entity.copy(
                            id = prev.id,
                            isFavorite = prev.isFavorite,
                            addedAt = prev.addedAt,
                        )
                    } else entity

                    toUpsert.add(merged)
                    updated++

                    if (toUpsert.size >= 32) {
                        db.trackDao().upsertAll(toUpsert)
                        toUpsert.clear()
                    }
                }

                if (toUpsert.isNotEmpty()) {
                    db.trackDao().upsertAll(toUpsert)
                }

                // 清理磁盘上已不存在的旧记录
                val stalePaths = existingMap.keys.filter { path ->
                    path !in seenPaths && !File(path).exists()
                }
                if (stalePaths.isNotEmpty()) {
                    stalePaths.chunked(100).forEach { db.trackDao().deleteByPaths(it) }
                }

                val finalCount = db.trackDao().count()
                val summary = s(R.string.lib_scan_done, finalCount, updated)
                Log.i(TAG, summary)
                _state.value = ScanState(
                    isScanning = false,
                    scanned = total,
                    total = total,
                    addedOrUpdated = updated,
                    lastSummary = summary,
                )
            }
        } catch (_: CancellationException) {
            _state.value = _state.value.copy(isScanning = false, lastSummary = s(R.string.lib_scan_cancelled))
        } catch (t: Throwable) {
            Log.e(TAG, "扫描失败", t)
            _state.value = _state.value.copy(
                isScanning = false,
                lastSummary = s(R.string.lib_scan_failed, t.message ?: ""),
            )
        } finally {
            scanJob = null
        }
    }

    /** 通过 SAF Uri 导入音频文件到资料库并解析入库 */
    suspend fun importUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        val targetDir = resolveImportDirectory()
        if (!targetDir.exists()) targetDir.mkdirs()

        var imported = 0
        for (uri in uris) {
            val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.flac"
            val dst = uniqueFile(targetDir, displayName)
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dst).use { out -> input.copyTo(out, 32768) }
                    true
                } ?: false
            }.getOrDefault(false)

            if (!copied) {
                dst.delete()
                continue
            }

            val entity = AudioMetadataParser.parseFile(dst, coversDir)
            if (entity != null) {
                db.trackDao().upsert(entity)
                imported++
            } else {
                dst.delete()
            }
        }
        if (imported > 0) {
            _state.value = _state.value.copy(lastSummary = s(R.string.lib_imported_count, imported))
        }
        imported
    }

    private fun resolveImportDirectory(): File {
        val publicDir = File(
            Environment.getExternalStorageDirectory(),
            "HiFiMusic/Music"
        )
        return if (publicDir.exists() && publicDir.canWrite() || publicDir.mkdirs()) {
            publicDir
        } else {
            File(context.filesDir, "ImportedMusic")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = f.nameWithoutExtension
        val ext = f.extension
        var idx = 1
        while (f.exists()) {
            f = File(dir, if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx")
            idx++
        }
        return f
    }

    private fun collectAudioFiles(
        dir: File,
        out: MutableList<File>,
        visited: MutableSet<String>,
    ) {
        val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!visited.add(canonical)) return
        if (File(dir, ".nomedia").exists()) return

        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                collectAudioFiles(child, out, visited)
            } else if (child.isFile) {
                if (child.extension.lowercase() in AudioMetadataParser.SUPPORTED_EXTENSIONS) {
                    out.add(child)
                }
            }
        }
    }

    companion object {
        private const val TAG = "HiFiScanner"
    }
}
