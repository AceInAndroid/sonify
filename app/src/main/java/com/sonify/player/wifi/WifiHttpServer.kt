package com.sonify.player.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

data class CheckFileRequestItem(
    val name: String,
    val relativePath: String,
    val size: Long,
    val hash: String,
)

data class CheckFileResultItem(
    val relativePath: String,
    val status: String,         // OK / DUPLICATE_HASH / DUPLICATE_LIBRARY / DUPLICATE_DISK / UNSUPPORTED
    val reason: String = "",
    val existingName: String = "",
)

data class UploadOutcome(
    val httpStatus: Int,
    val status: String,         // SUCCESS / DUPLICATE / ERROR
    val message: String,
    val recordJson: JSONObject? = null,
)

/**
 * WifiHttpServer 依赖的业务后端契约（与 Android Context 解耦，支持纯 JVM 端到端单测）。
 */
interface WifiTransferBackend {
    fun loadWebPageHtml(): ByteArray
    fun buildStatusJson(): JSONObject
    fun isPinRequired(): Boolean
    fun verifyPinAndIssueToken(pin: String): String?
    fun isTokenValid(token: String?): Boolean
    suspend fun checkFiles(items: List<CheckFileRequestItem>, userAgent: String): List<CheckFileResultItem>
    suspend fun receiveUpload(
        fileName: String,
        relativePath: String,
        expectedSize: Long,
        clientHash: String,
        overwrite: Boolean,
        userAgent: String,
        bodyStream: InputStream,
    ): UploadOutcome

    suspend fun getHistoryJson(query: String, statusFilter: String): JSONArray
    suspend fun deleteHistoryItem(id: Long, deleteFile: Boolean): Boolean
    suspend fun clearHistory(deleteFiles: Boolean)
}

/**
 * 轻量纯 Kotlin 流式 HTTP/1.1 局域网传输服务器：
 * - 基于 ServerSocket + Dispatchers.IO 协程，无沉重框架依赖；
 * - 原始二进制流直接写磁盘，传输数百 MB 无损 WAV/FLAC 内存恒定 < 256 KB；
 * - 兼容浏览器 Expect: 100-continue、UTF-8 中文文件名及 CORS 预检。
 */
class WifiHttpServer(
    private val backend: WifiTransferBackend,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null

    @Volatile
    var boundPort: Int = -1
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    fun start(preferredPort: Int, maxFallbackOffset: Int = 10): Int {
        stop()
        var lastErr: Throwable? = null
        val candidatePorts = if (preferredPort == 0) {
            listOf(0)
        } else {
            (preferredPort..(preferredPort + maxFallbackOffset).coerceAtMost(65535)).toList()
        }

        var opened: ServerSocket? = null
        for (p in candidatePorts) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", p), 32)
                opened = ss
                break
            } catch (t: Throwable) {
                lastErr = t
            }
        }

        val ss = opened ?: throw IllegalStateException("无法绑定端口 $preferredPort：${lastErr?.message}", lastErr)
        serverSocket = ss
        boundPort = ss.localPort

        val srvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = srvScope
        acceptJob = srvScope.launch {
            while (isActive && !ss.isClosed) {
                val socket = try {
                    ss.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: Throwable) {
                    continue
                }
                launch {
                    handleConnection(socket)
                }
            }
        }
        return boundPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
        boundPort = -1
    }

    private suspend fun handleConnection(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val input = BufferedInputStream(s.getInputStream(), 65536)
            val output = s.getOutputStream()

            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeJson(output, 400, JSONObject().put("error", "Bad Request"))
                return
            }

            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val qIdx = rawTarget.indexOf('?')
            val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
            val queryStr = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""
            val queryParams = parseQueryParams(queryStr)

            val headers = HashMap<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase()
                    val v = line.substring(colon + 1).trim()
                    headers[k] = v
                }
            }

            if (method == "OPTIONS") {
                writeRaw(output, 204, "No Content", "text/plain", ByteArray(0))
                return
            }

            // 兼容浏览器/curl 的 Expect: 100-continue
            if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
                output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()
            }

            val contentLength = headers["content-length"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val userAgent = headers["user-agent"].orEmpty()
            val token = headers["x-sonify-token"] ?: queryParams["token"]

            try {
                // 静态 Web 页面与基础状态接口免 PIN
                if (method == "GET" && (path == "/" || path == "/index.html")) {
                    val html = backend.loadWebPageHtml()
                    writeRaw(output, 200, "OK", "text/html; charset=utf-8", html)
                    return
                }

                if (method == "GET" && path == "/api/status") {
                    val st = backend.buildStatusJson()
                    st.put("authenticated", !backend.isPinRequired() || backend.isTokenValid(token))
                    writeJson(output, 200, st)
                    return
                }

                if (method == "POST" && path == "/api/pin") {
                    val bodyStr = readLimitedString(input, contentLength, 4096)
                    val pin = runCatching { JSONObject(bodyStr).optString("pin", "") }.getOrDefault("")
                    val issued = backend.verifyPinAndIssueToken(pin)
                    if (issued != null) {
                        writeJson(output, 200, JSONObject().put("ok", true).put("token", issued))
                    } else {
                        writeJson(output, 401, JSONObject().put("ok", false).put("error", "PIN 码错误，请核对手机屏幕上的 4 位访问码"))
                    }
                    return
                }

                // 其余 /api/* 接口若启用 PIN 则必须鉴权
                if (path.startsWith("/api/") && backend.isPinRequired() && !backend.isTokenValid(token)) {
                    writeJson(output, 401, JSONObject().put("error", "PIN_REQUIRED").put("message", "请输入手机端显示的 4 位访问码"))
                    return
                }

                when {
                    method == "POST" && path == "/api/check" -> {
                        val bodyStr = readLimitedString(input, contentLength, 2 * 1024 * 1024)
                        val root = JSONObject(bodyStr)
                        val arr = root.optJSONArray("files") ?: JSONArray()
                        val reqItems = buildList(arr.length()) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                add(
                                    CheckFileRequestItem(
                                        name = obj.optString("name", ""),
                                        relativePath = obj.optString("relativePath", obj.optString("name", "")),
                                        size = obj.optLong("size", 0L),
                                        hash = obj.optString("hash", ""),
                                    )
                                )
                            }
                        }
                        val results = backend.checkFiles(reqItems, userAgent)
                        val resArr = JSONArray()
                        results.forEach { r ->
                            resArr.put(
                                JSONObject()
                                    .put("relativePath", r.relativePath)
                                    .put("status", r.status)
                                    .put("reason", r.reason)
                                    .put("existingName", r.existingName)
                            )
                        }
                        writeJson(output, 200, JSONObject().put("results", resArr))
                    }

                    method == "POST" && path == "/api/upload" -> {
                        val fileName = queryParams["name"].orEmpty()
                        val relPath = queryParams["relPath"].orEmpty().ifBlank { fileName }
                        val clientHash = queryParams["hash"].orEmpty()
                        val overwrite = queryParams["overwrite"].equals("true", ignoreCase = true)
                        // 流式读取超时放宽，保障大体积无损文件上传
                        s.soTimeout = 120_000
                        val limited = ExactLengthInputStream(input, contentLength)
                        val outcome = backend.receiveUpload(
                            fileName = fileName,
                            relativePath = relPath,
                            expectedSize = contentLength,
                            clientHash = clientHash,
                            overwrite = overwrite,
                            userAgent = userAgent,
                            bodyStream = limited,
                        )
                        val resp = JSONObject()
                            .put("status", outcome.status)
                            .put("message", outcome.message)
                        outcome.recordJson?.let { resp.put("record", it) }
                        writeJson(output, outcome.httpStatus, resp)
                    }

                    method == "GET" && path == "/api/history" -> {
                        val q = queryParams["q"].orEmpty()
                        val status = queryParams["status"].orEmpty()
                        val list = backend.getHistoryJson(q, status)
                        writeJson(output, 200, JSONObject().put("items", list))
                    }

                    method == "DELETE" && path == "/api/history" -> {
                        val deleteFiles = queryParams["deleteFiles"].equals("true", ignoreCase = true)
                        backend.clearHistory(deleteFiles)
                        writeJson(output, 200, JSONObject().put("ok", true))
                    }

                    method == "DELETE" && path.startsWith("/api/history/") -> {
                        val id = path.removePrefix("/api/history/").toLongOrNull()
                        if (id == null) {
                            writeJson(output, 400, JSONObject().put("error", "无效的历史记录 ID"))
                        } else {
                            val deleteFile = queryParams["deleteFile"].equals("true", ignoreCase = true)
                            val ok = backend.deleteHistoryItem(id, deleteFile)
                            writeJson(output, if (ok) 200 else 404, JSONObject().put("ok", ok))
                        }
                    }

                    else -> {
                        writeJson(output, 404, JSONObject().put("error", "Not Found"))
                    }
                }
            } catch (t: Throwable) {
                runCatching {
                    writeJson(output, 500, JSONObject().put("error", t.message ?: "服务器内部错误"))
                }
            }
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream(128)
        var gotAny = false
        while (true) {
            val b = input.read()
            if (b == -1) return if (gotAny) baos.toString("UTF-8") else null
            gotAny = true
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
                if (baos.size() > 16 * 1024) break
            }
        }
        return baos.toString("UTF-8")
    }

    private fun readLimitedString(input: InputStream, contentLength: Long, maxBytes: Int): String {
        val toRead = contentLength.coerceAtMost(maxBytes.toLong()).toInt()
        if (toRead <= 0) return ""
        val buf = ByteArray(toRead)
        var offset = 0
        while (offset < toRead) {
            val n = input.read(buf, offset, toRead - offset)
            if (n <= 0) break
            offset += n
        }
        return String(buf, 0, offset, Charsets.UTF_8)
    }

    private fun writeJson(output: OutputStream, status: Int, obj: JSONObject) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            415 -> "Unsupported Media Type"
            else -> "Internal Server Error"
        }
        writeRaw(output, status, reason, "application/json; charset=utf-8", obj.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeRaw(output: OutputStream, status: Int, reason: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Sonify-Token\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    companion object {
        fun parseQueryParams(query: String): Map<String, String> {
            if (query.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    out[percentDecode(pair)] = ""
                } else {
                    out[percentDecode(pair.substring(0, eq))] = percentDecode(pair.substring(eq + 1))
                }
            }
            return out
        }

        /** 严格按 RFC 3986 解码 %XX，不把文件名中的 '+' 错解为空格 */
        fun percentDecode(input: String): String {
            val out = ArrayList<Byte>(input.length)
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '%' && i + 2 < input.length) {
                    val v = input.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) {
                        out.add(v.toByte())
                        i += 3
                        continue
                    }
                }
                for (b in c.toString().toByteArray(Charsets.UTF_8)) out.add(b)
                i++
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}

private class ExactLengthInputStream(
    private val upstream: InputStream,
    private var remaining: Long,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val b = upstream.read()
        if (b < 0) throw EOFException("客户端连接提前断开")
        remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val n = upstream.read(b, off, toRead)
        if (n < 0) throw EOFException("客户端连接提前断开")
        remaining -= n
        return n
    }
}
