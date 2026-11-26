package com.sonify.player.wifi

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.UserPreferences
import com.sonify.player.data.WifiUploadHistoryEntity
import com.sonify.player.library.AudioMetadataParser
import com.sonify.player.ui.theme.hiFiColorsFor
import com.sonify.player.usb.UsbAudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class WifiTransferState(
    val isRunning: Boolean = false,
    val primaryUrl: String = "",
    val allUrls: List<String> = emptyList(),
    val port: Int = 8088,
    val pinEnabled: Boolean = false,
    val pinCode: String = "",
    val activeUploads: Int = 0,
    val sessionUploadedCount: Int = 0,
    val sessionSkippedCount: Int = 0,
    val storageDirPath: String = "",
    val freeSpaceBytes: Long = 0L,
    val error: String? = null,
)

/**
 * WiFi 局域网传歌中枢：
 * - 启停内置流式 HTTP 服务与前台保活通知；
 * - 三层防重（历史内容指纹 Hash → 本地曲库文件大小+文件名 → 磁盘文件校验）；
 * - 流式原子落盘至 /sdcard/Music/SonifyWifi（保留专辑子目录，无权限自动回落 App 私有目录）；
 * - 音频文件上传即自动无损解析入库，配套 .lrc / 封面自动同目录落盘并挂接已有曲目。
 */
class WifiTransferManager(
    private val context: Context,
    private val db: LibraryDatabase,
    private val prefs: UserPreferences,
    private val usbManager: UsbAudioManager,
) : WifiTransferBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyDao = db.wifiUploadHistoryDao()
    private val trackDao = db.trackDao()
    private val uploadMutex = Mutex()
    private val random = SecureRandom()
    private val validTokens = ConcurrentHashMap.newKeySet<String>()

    private val server = WifiHttpServer(this)

    private val _state = MutableStateFlow(
        WifiTransferState(
            storageDirPath = resolveStorageRootDir().absolutePath,
            freeSpaceBytes = computeFreeSpaceBytes(resolveStorageRootDir()),
        )
    )
    val state: StateFlow<WifiTransferState> = _state.asStateFlow()

    val history: StateFlow<List<WifiUploadHistoryEntity>> = historyDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    @Volatile
    private var cachedHtml: ByteArray? = null

    fun startServer(): Boolean {
        if (server.isRunning) return true
        return try {
            val preferredPort = prefs.current.wifiTransferPort
            val boundPort = server.start(preferredPort)
            val ips = discoverLanIpv4Addresses()
            val urls = ips.map { "http://$it:$boundPort" }
            val primary = urls.firstOrNull() ?: "http://127.0.0.1:$boundPort"
            val rootDir = resolveStorageRootDir()
            val pinEnabled = prefs.current.wifiTransferPinEnabled
            val pinCode = if (pinEnabled) generateFourDigitPin() else ""
            validTokens.clear()

            _state.value = WifiTransferState(
                isRunning = true,
                primaryUrl = primary,
                allUrls = urls,
                port = boundPort,
                pinEnabled = pinEnabled,
                pinCode = pinCode,
                activeUploads = 0,
                sessionUploadedCount = 0,
                sessionSkippedCount = 0,
                storageDirPath = rootDir.absolutePath,
                freeSpaceBytes = computeFreeSpaceBytes(rootDir),
                error = if (ips.isEmpty()) "未检测到局域网 WiFi 地址，请确认手机已连接 WiFi" else null,
            )
            WifiTransferService.start(context, primary)
            Log.i(TAG, "WiFi 传歌服务已启动: $primary (dir=${rootDir.absolutePath})")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "启动 WiFi 传歌失败: ${t.message}", t)
            _state.update { it.copy(isRunning = false, error = t.message ?: "启动服务失败") }
            false
        }
    }

    fun stopServer() {
        server.stop()
        validTokens.clear()
        WifiTransferService.stop(context)
        val rootDir = resolveStorageRootDir()
        _state.update {
            it.copy(
                isRunning = false,
                activeUploads = 0,
                freeSpaceBytes = computeFreeSpaceBytes(rootDir),
                error = null,
            )
        }
        Log.i(TAG, "WiFi 传歌服务已停止")
    }

    fun refreshPinEnabled(enabled: Boolean) {
        validTokens.clear()
        val nextPin = if (enabled) generateFourDigitPin() else ""
        _state.update { it.copy(pinEnabled = enabled, pinCode = nextPin) }
    }

    fun regeneratePin() {
        validTokens.clear()
        val nextPin = generateFourDigitPin()
        _state.update { it.copy(pinEnabled = true, pinCode = nextPin) }
    }

    fun refreshNetworkAndStorage() {
        val rootDir = resolveStorageRootDir()
        val free = computeFreeSpaceBytes(rootDir)
        if (!server.isRunning) {
            _state.update { it.copy(storageDirPath = rootDir.absolutePath, freeSpaceBytes = free) }
            return
        }
        val port = server.boundPort
        val ips = discoverLanIpv4Addresses()
        val urls = ips.map { "http://$it:$port" }
        val primary = urls.firstOrNull() ?: "http://127.0.0.1:$port"
        _state.update {
            it.copy(
                primaryUrl = primary,
                allUrls = urls,
                storageDirPath = rootDir.absolutePath,
                freeSpaceBytes = free,
                error = if (ips.isEmpty()) "未检测到局域网 WiFi 地址，请确认手机已连接 WiFi" else null,
            )
        }
    }

    // ==================== WifiTransferBackend 实现 ====================

    override fun loadWebPageHtml(): ByteArray {
        cachedHtml?.let { return it }
        val bytes = context.assets.open("wifi_transfer.html").use { it.readBytes() }
        cachedHtml = bytes
        return bytes
    }

    override fun buildStatusJson(): JSONObject {
        val settings = prefs.current
        val palette = hiFiColorsFor(settings.darkCanvasStyle, settings.accentStyle)
        val usbSt = usbManager.state.value
        val rootDir = resolveStorageRootDir()
        val freeBytes = computeFreeSpaceBytes(rootDir)
        val totalBytes = computeTotalSpaceBytes(rootDir)
        val cur = _state.value

        return JSONObject()
            .put("appName", "Sonify")
            .put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("storageDir", rootDir.absolutePath)
            .put("freeSpaceBytes", freeBytes)
            .put("totalSpaceBytes", totalBytes)
            .put("pinRequired", cur.pinEnabled)
            .put("autoSkipDuplicate", settings.wifiTransferAutoSkipDuplicate)
            .put("sessionUploaded", cur.sessionUploadedCount)
            .put("sessionSkipped", cur.sessionSkippedCount)
            .put("usbConnected", usbSt.isConnected)
            .put("usbDeviceName", if (usbSt.isConnected) usbSt.deviceDisplayName else "")
            .put(
                "theme",
                JSONObject()
                    .put("canvas", toHexRgb(palette.canvas))
                    .put("surface1", toHexRgb(palette.surface1))
                    .put("surface2", toHexRgb(palette.surface2))
                    .put("surface3", toHexRgb(palette.surface3))
                    .put("accent", toHexRgb(palette.accent))
                    .put("onAccent", toHexRgb(palette.onAccent))
                    .put("exclusiveGreen", toHexRgb(palette.exclusiveGreen))
                    .put("danger", toHexRgb(palette.danger))
            )
    }

    override fun isPinRequired(): Boolean = _state.value.pinEnabled

    override fun verifyPinAndIssueToken(pin: String): String? {
        val cur = _state.value
        if (!cur.pinEnabled) return "no-pin"
        if (pin.trim() == cur.pinCode && cur.pinCode.isNotEmpty()) {
            val token = UUID.randomUUID().toString().replace("-", "")
            validTokens.add(token)
            return token
        }
        return null
    }

    override fun isTokenValid(token: String?): Boolean {
        if (!_state.value.pinEnabled) return true
        return !token.isNullOrBlank() && validTokens.contains(token)
    }

    override suspend fun checkFiles(
        items: List<CheckFileRequestItem>,
        userAgent: String,
    ): List<CheckFileResultItem> = withContext(Dispatchers.IO) {
        val rootDir = resolveStorageRootDir()
        val libraryTracks = trackDao.getAllFingerprints()
        val libByNameAndSize = libraryTracks.associateBy {
            "${File(it.path).name.lowercase()}::${it.fileSize}"
        }

        val clientInfo = WifiPathSanitizer.parseClientInfo(userAgent)

        items.map { item ->
            val (safeRel, safeName) = WifiPathSanitizer.sanitizeRelativePath(item.relativePath, item.name)
            if (!WifiPathSanitizer.isAllowedFile(safeName)) {
                return@map CheckFileResultItem(
                    relativePath = item.relativePath,
                    status = "UNSUPPORTED",
                    reason = "不支持的文件格式（仅支持常见无损/音频、.lrc 与封面图片）",
                )
            }

            // 第 1 层：查历史内容指纹 Hash（即便改名也能识别重复）
            if (item.hash.isNotBlank()) {
                val hashHit = historyDao.findSuccessByHash(item.hash)
                if (hashHit != null && File(hashHit.savedPath).isFile) {
                    recordSkippedDuplicate(safeName, safeRel, item.size, item.hash, hashHit.fileName, clientInfo)
                    return@map CheckFileResultItem(
                        relativePath = item.relativePath,
                        status = "DUPLICATE_HASH",
                        reason = "内容与已上传文件完全一致",
                        existingName = hashHit.fileName,
                    )
                }
            }

            // 第 2 层：查 Room 既有本地曲库（同名 + 同字节大小）
            val libHit = libByNameAndSize["${safeName.lowercase()}::${item.size}"]
            if (libHit != null && File(libHit.path).isFile) {
                val hitName = File(libHit.path).name
                recordSkippedDuplicate(safeName, safeRel, item.size, item.hash, hitName, clientInfo)
                return@map CheckFileResultItem(
                    relativePath = item.relativePath,
                    status = "DUPLICATE_LIBRARY",
                    reason = "本地音乐库已存在相同曲目",
                    existingName = hitName,
                )
            }

            // 第 3 层：查目标磁盘路径是否已有同名同大小文件
            val dest = File(rootDir, safeRel)
            if (dest.isFile && dest.length() == item.size && item.size > 0L) {
                recordSkippedDuplicate(safeName, safeRel, item.size, item.hash, dest.name, clientInfo)
                return@map CheckFileResultItem(
                    relativePath = item.relativePath,
                    status = "DUPLICATE_DISK",
                    reason = "目标目录已存在同名同大小文件",
                    existingName = dest.name,
                )
            }

            CheckFileResultItem(relativePath = item.relativePath, status = "OK")
        }
    }

    override suspend fun receiveUpload(
        fileName: String,
        relativePath: String,
        expectedSize: Long,
        clientHash: String,
        overwrite: Boolean,
        userAgent: String,
        bodyStream: InputStream,
    ): UploadOutcome = withContext(Dispatchers.IO) {
        val (safeRel, safeName) = WifiPathSanitizer.sanitizeRelativePath(relativePath, fileName)
        val clientInfo = WifiPathSanitizer.parseClientInfo(userAgent)

        if (!WifiPathSanitizer.isAllowedFile(safeName)) {
            return@withContext UploadOutcome(
                httpStatus = 415,
                status = "ERROR",
                message = "不支持的文件类型：$safeName",
            )
        }

        val rootDir = resolveStorageRootDir()
        val destFile = File(rootDir, safeRel)
        destFile.parentFile?.mkdirs()
        val partFile = File(destFile.parentFile, ".${safeName}.${System.currentTimeMillis()}.part")

        _state.update { it.copy(activeUploads = it.activeUploads + 1) }

        try {
            val buf = ByteArray(65536)
            var written = 0L
            FileOutputStream(partFile).use { out ->
                while (true) {
                    val n = bodyStream.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                }
                out.fd.sync()
            }

            if (written <= 0L) {
                runCatching { partFile.delete() }
                return@withContext UploadOutcome(400, "ERROR", "收到空文件")
            }

            // 服务端计算真实内容指纹做最终原子防重
            val realHash = FileFingerprint.compute(partFile)
            val extUpper = WifiPathSanitizer.extensionOf(safeName).uppercase()

            val duplicateHit = uploadMutex.withLock {
                if (!overwrite) {
                    val byHash = historyDao.findSuccessByHash(realHash)
                    if (byHash != null && File(byHash.savedPath).isFile) {
                        byHash.fileName
                    } else if (destFile.isFile && destFile.length() == written) {
                        destFile.name
                    } else null
                } else null
            }

            if (duplicateHit != null) {
                runCatching { partFile.delete() }
                val rec = WifiUploadHistoryEntity(
                    fileName = safeName,
                    relativePath = safeRel,
                    savedPath = "",
                    fileSize = written,
                    contentHash = realHash,
                    format = extUpper,
                    status = "SKIPPED_DUPLICATE",
                    duplicateOfName = duplicateHit,
                    clientInfo = clientInfo,
                )
                val id = historyDao.insert(rec)
                _state.update { it.copy(sessionSkippedCount = it.sessionSkippedCount + 1) }
                return@withContext UploadOutcome(
                    httpStatus = 409,
                    status = "DUPLICATE",
                    message = "重复文件已自动跳过（与「$duplicateHit」一致）",
                    recordJson = historyToJson(rec.copy(id = id)),
                )
            }

            uploadMutex.withLock {
                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }
            }

            // 自动无损解析入库或挂接歌词/封面
            var sampleRate = 0
            var bitDepth = 0
            var fmt = extUpper
            if (WifiPathSanitizer.isAudioFile(safeName)) {
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                val parsed = runCatching { AudioMetadataParser.parseFile(destFile, coversDir) }.getOrNull()
                if (parsed != null) {
                    sampleRate = parsed.sampleRate
                    bitDepth = parsed.bitDepth
                    fmt = parsed.format
                    trackDao.upsert(parsed)
                }
                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                }
            } else if (WifiPathSanitizer.isLyricsFile(safeName)) {
                fmt = "LRC"
            } else if (WifiPathSanitizer.isCoverImage(safeName)) {
                fmt = "COVER"
            }

            val rec = WifiUploadHistoryEntity(
                fileName = safeName,
                relativePath = safeRel,
                savedPath = destFile.absolutePath,
                fileSize = destFile.length(),
                contentHash = realHash,
                format = fmt,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                status = "SUCCESS",
                clientInfo = clientInfo,
            )
            val id = historyDao.insert(rec)
            _state.update {
                it.copy(
                    sessionUploadedCount = it.sessionUploadedCount + 1,
                    freeSpaceBytes = computeFreeSpaceBytes(rootDir),
                )
            }
            Log.i(TAG, "WiFi 传歌成功: $safeRel ($written bytes, $fmt ${sampleRate}Hz/${bitDepth}b)")
            UploadOutcome(
                httpStatus = 200,
                status = "SUCCESS",
                message = "上传并入库成功",
                recordJson = historyToJson(rec.copy(id = id)),
            )
        } catch (t: Throwable) {
            runCatching { partFile.delete() }
            val rec = WifiUploadHistoryEntity(
                fileName = safeName,
                relativePath = safeRel,
                savedPath = "",
                fileSize = expectedSize,
                contentHash = clientHash,
                format = WifiPathSanitizer.extensionOf(safeName).uppercase(),
                status = "FAILED",
                duplicateOfName = t.message.orEmpty().take(120),
                clientInfo = clientInfo,
            )
            val id = runCatching { historyDao.insert(rec) }.getOrDefault(0L)
            Log.w(TAG, "WiFi 传歌失败 $safeName: ${t.message}")
            UploadOutcome(
                httpStatus = 500,
                status = "ERROR",
                message = t.message ?: "写入文件失败",
                recordJson = historyToJson(rec.copy(id = id)),
            )
        } finally {
            _state.update { it.copy(activeUploads = (it.activeUploads - 1).coerceAtLeast(0)) }
        }
    }

    override suspend fun getHistoryJson(query: String, statusFilter: String): JSONArray =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            val items = historyDao.getRecent(300).filter { row ->
                val statusMatch = statusFilter.isBlank() ||
                    statusFilter.equals("ALL", true) ||
                    row.status.equals(statusFilter, true)
                val queryMatch = q.isEmpty() ||
                    row.fileName.lowercase().contains(q) ||
                    row.relativePath.lowercase().contains(q) ||
                    row.clientInfo.lowercase().contains(q)
                statusMatch && queryMatch
            }
            JSONArray().apply {
                items.forEach { put(historyToJson(it)) }
            }
        }

    override suspend fun deleteHistoryItem(id: Long, deleteFile: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val row = historyDao.findById(id) ?: return@withContext false
            if (deleteFile && row.savedPath.isNotBlank()) {
                runCatching { File(row.savedPath).delete() }
                trackDao.deleteByPaths(listOf(row.savedPath))
            }
            historyDao.deleteById(id)
            _state.update { it.copy(freeSpaceBytes = computeFreeSpaceBytes(resolveStorageRootDir())) }
            true
        }

    override suspend fun clearHistory(deleteFiles: Boolean) = withContext(Dispatchers.IO) {
        if (deleteFiles) {
            val rows = historyDao.getRecent(1000)
            val paths = rows.mapNotNull { it.savedPath.takeIf { p -> p.isNotBlank() } }
            paths.forEach { runCatching { File(it).delete() } }
            if (paths.isNotEmpty()) trackDao.deleteByPaths(paths)
        }
        historyDao.deleteAll()
        _state.update { it.copy(freeSpaceBytes = computeFreeSpaceBytes(resolveStorageRootDir())) }
    }

    /** 记录一条在前端预检阶段自动跳过的重复记录（供前端需要时统一写入，当前直接在 check/upload 反馈） */
    suspend fun recordSkippedDuplicate(
        fileName: String,
        relativePath: String,
        fileSize: Long,
        hash: String,
        existingName: String,
        clientInfo: String,
    ) = withContext(Dispatchers.IO) {
        historyDao.insert(
            WifiUploadHistoryEntity(
                fileName = fileName,
                relativePath = relativePath,
                savedPath = "",
                fileSize = fileSize,
                contentHash = hash,
                format = WifiPathSanitizer.extensionOf(fileName).uppercase(),
                status = "SKIPPED_DUPLICATE",
                duplicateOfName = existingName,
                clientInfo = clientInfo,
            )
        )
        _state.update { it.copy(sessionSkippedCount = it.sessionSkippedCount + 1) }
    }

    private fun resolveStorageRootDir(): File {
        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val candidate = File(publicMusic, "SonifyWifi")
        val writable = runCatching {
            candidate.mkdirs()
            val probe = File(candidate, ".sonify_wifi_probe")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
        if (writable) return candidate

        val appMusic = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        return File(appMusic, "SonifyWifi").apply { mkdirs() }
    }

    private fun computeFreeSpaceBytes(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun computeTotalSpaceBytes(dir: File): Long = runCatching {
        StatFs(dir.absolutePath).totalBytes
    }.getOrDefault(0L)

    private fun generateFourDigitPin(): String = "%04d".format(random.nextInt(10000))

    private fun discoverLanIpv4Addresses(): List<String> {
        val prioritized = mutableListOf<Pair<Int, String>>()
        runCatching {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val name = ni.name.lowercase()
                val score = when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") -> 1
                    name.startsWith("ap") -> 2
                    name.startsWith("rmnet") || name.startsWith("ccmni") -> 10
                    else -> 5
                }
                for (addr in Collections.list(ni.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        prioritized += score to ip
                    }
                }
            }
        }
        return prioritized.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun historyToJson(e: WifiUploadHistoryEntity): JSONObject = JSONObject()
        .put("id", e.id)
        .put("fileName", e.fileName)
        .put("relativePath", e.relativePath)
        .put("savedPath", e.savedPath)
        .put("fileSize", e.fileSize)
        .put("format", e.format)
        .put("sampleRate", e.sampleRate)
        .put("bitDepth", e.bitDepth)
        .put("status", e.status)
        .put("duplicateOfName", e.duplicateOfName)
        .put("clientInfo", e.clientInfo)
        .put("uploadedAt", e.uploadedAt)

    private fun toHexRgb(color: androidx.compose.ui.graphics.Color): String {
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    companion object {
        private const val TAG = "WifiTransfer"
    }
}
