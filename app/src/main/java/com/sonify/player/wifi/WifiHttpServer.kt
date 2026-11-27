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
import java.util.Locale

enum class WifiHttpError {
    BAD_PIN,
    NEED_PIN,
    BAD_HISTORY_ID,
    SERVER_ERROR,
    CLIENT_ABORT;

    fun defaultMessage(): String {
        val zh = Locale.getDefault().language.equals("zh", ignoreCase = true)
        return when (this) {
            BAD_PIN -> if (zh) "PIN 码错误，请核对手机屏幕上的 4 位访问码" else "Incorrect PIN — check the 4-digit code on your phone"
            NEED_PIN -> if (zh) "请输入手机端显示的 4 位访问码" else "Enter the 4-digit PIN shown on your phone"
            BAD_HISTORY_ID -> if (zh) "无效的历史记录 ID" else "Invalid history record ID"
            SERVER_ERROR -> if (zh) "服务器内部错误" else "Internal server error"
            CLIENT_ABORT -> if (zh) "客户端连接提前断开" else "Client disconnected before upload completed"
        }
    }
}

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
    fun resolveErrorMessage(error: WifiHttpError): String = error.defaultMessage()
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

        val ss = opened ?: throw IllegalStateException("Failed to bind port $preferredPort: ${lastErr?.message}", lastErr)
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
                        writeJson(
                            output,
                            401,
                            JSONObject().put("ok", false).put("error", backend.resolveErrorMessage(WifiHttpError.BAD_PIN))
                        )
                    }
                    return
                }

                // 其余 /api/* 接口若启用 PIN 则必须鉴权
                if (path.startsWith("/api/") && backend.isPinRequired() && !backend.isTokenValid(token)) {
                    writeJson(
                        output,
                        401,
                        JSONObject().put("error", "PIN_REQUIRED").put("message", backend.resolveErrorMessage(WifiHttpError.NEED_PIN))
                    )
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
                        val limited = ExactLengthInputStream(
                            upstream = input,
                            remaining = contentLength,
                            eofMessage = backend.resolveErrorMessage(WifiHttpError.CLIENT_ABORT),
                        )
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
                            writeJson(
                                output,
                                400,
                                JSONObject().put("error", backend.resolveErrorMessage(WifiHttpError.BAD_HISTORY_ID))
                            )
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
                    writeJson(
                        output,
                        500,
                        JSONObject().put("error", t.message ?: backend.resolveErrorMessage(WifiHttpError.SERVER_ERROR))
                    )
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
        /** 为内嵌 WiFi 传歌 Web 页面注入中/英双语支持与顶部快速切换按钮 */
        fun injectBilingualSupport(rawHtml: String, defaultChinese: Boolean): ByteArray {
            val defaultLang = if (defaultChinese) "zh" else "en"
            val langBtnHtml =
                """<button id="langToggleBtn" type="button" class="pill" style="cursor:pointer;color:var(--text-primary);font-family:var(--sans);font-weight:600;" onclick="window.__sonifyToggleLang && window.__sonifyToggleLang()">中 / EN</button>"""

            var out = rawHtml
            val pillsMarker = """<div class="status-pills">"""
            if (out.contains(pillsMarker) && !out.contains("id=\"langToggleBtn\"")) {
                out = out.replaceFirst(pillsMarker, "$pillsMarker\n        $langBtnHtml")
            }

            val i18nScript = """
  <script>
    (function() {
      var DEFAULT_LANG = '$defaultLang';
      var curLang = localStorage.getItem('sonify_wifi_lang') || DEFAULT_LANG;
      var EXACT_EN = {
        'WiFi 传歌': 'WiFi Transfer',
        '正在连接手机服务…': 'Connecting to phone…',
        '可用空间 --': 'Free --',
        '传歌上传': 'Upload',
        '上传无损音乐': 'Upload Lossless Music',
        '支持 FLAC / WAV / ALAC / MP3 / M4A / OPUS 及配套 .lrc / 封面图': 'Supports FLAC / WAV / ALAC / MP3 / M4A / OPUS, .lrc lyrics & cover art',
        '拖拽音频文件或整个专辑文件夹到此处': 'Drag & drop audio files or full album folders here',
        '上传前自动计算文件指纹防重，已存在曲目零流量跳过；传完自动解析入库': 'Auto SHA-256 dedup before upload; completed files are indexed automatically',
        '选择音频文件': 'Select Audio Files',
        '选择专辑文件夹': 'Select Album Folder',
        '防重：自动跳过重复文件': 'Dedup: auto-skip duplicate files',
        '等待选择文件': 'Waiting for files',
        '上传历史': 'Upload History',
        '与手机端实时同步': 'Live synced with phone',
        '刷新': 'Refresh',
        '清空记录': 'Clear',
        '全部': 'All',
        '上传成功': 'Success',
        '重复跳过': 'Skipped',
        '失败': 'Failed',
        '暂无上传历史': 'No upload history yet',
        '暂无匹配的上传记录': 'No matching upload records',
        '请输入 4 位访问码': 'Enter 4-Digit PIN',
        '请查看手机 Sonify「WiFi 传歌」界面显示的 4 位临时 PIN 码': 'Check the 4-digit temporary PIN shown in Sonify WiFi Transfer on your phone',
        '验证并进入': 'Verify & Enter',
        '连接手机服务失败，请确认 App 保持开启': 'Connection failed — make sure Sonify is running',
        '请输入完整 4 位数字 PIN 码': 'Enter the full 4-digit PIN',
        '验证通过，已连接 Sonify': 'Verified — connected to Sonify',
        'PIN 码错误': 'Incorrect PIN',
        '网络异常，请重试': 'Network error, please retry',
        '正在计算防重指纹…': 'Computing fingerprint…',
        '等待上传…': 'Queued…',
        '不支持的文件格式': 'Unsupported file format',
        '重复文件已跳过': 'Duplicate skipped',
        '等待上传（覆盖模式）…': 'Queued (overwrite)…',
        '正在高速传输…': 'Uploading…',
        '上传并自动解析入库成功': 'Uploaded & indexed',
        '重复文件已自动跳过': 'Duplicate skipped automatically',
        '需要重新验证 4 位 PIN 码': 'PIN verification required',
        '网络连接中断，请检查手机 WiFi 状态': 'Connection lost — check phone WiFi',
        '已手动取消': 'Cancelled',
        '等待强制覆盖上传…': 'Queued for overwrite…',
        '已取消': 'Cancelled',
        '已入库': 'Indexed',
        '队列中': 'Queued',
        '不支持': 'Unsupported',
        '覆盖上传': 'Overwrite',
        '取消': 'Cancel',
        '重试': 'Retry',
        '成功': 'Success',
        '删除': 'Delete',
        '已清空上传历史记录': 'Upload history cleared'
      };
      var EXACT_ZH = {};
      Object.keys(EXACT_EN).forEach(function(k) { EXACT_ZH[EXACT_EN[k]] = k; });

      function translateText(s, toLang) {
        if (!s) return s;
        var t = s.trim();
        if (toLang === 'en') {
          if (EXACT_EN[t]) return EXACT_EN[t];
          var m;
          if ((m = t.match(/^剩余\s+(.+)$/))) return 'Free ' + m[1];
          if ((m = t.match(/^共\s+(\d+)\s+条记录\s*·\s*与手机端实时同步$/))) return m[1] + ' records · Live synced';
          if ((m = t.match(/^与\s+(.+)\s+一致$/))) return 'Matches ' + m[1];
          if ((m = t.match(/^传输中\s+(\d+%)\s*·\s*(.+)$/))) return 'Uploading ' + m[1] + ' · ' + m[2];
          if ((m = t.match(/^上传失败\s*\(HTTP\s*(\d+)\)$/))) return 'Upload failed (HTTP ' + m[1] + ')';
          if ((m = t.match(/^防重拦截\s*(\d+)\s*个重复文件，已自动跳过$/))) return 'Dedup skipped ' + m[1] + ' duplicate file(s)';
          if ((m = t.match(/^本次共\s*(\d+)\s*项\s*·\s*成功\s*(\d+)\s*·\s*重复跳过\s*(\d+)(?:\s*·\s*进行中\s*(\d+))?$/))) {
            return m[1] + ' total · ' + m[2] + ' ok · ' + m[3] + ' skipped' + (m[4] ? ' · ' + m[4] + ' active' : '');
          }
          if (t.indexOf('Android 设备') !== -1) return t.replace('Android 设备', 'Android Device');
          return s;
        } else {
          if (EXACT_ZH[t]) return EXACT_ZH[t];
          var m2;
          if ((m2 = t.match(/^Free\s+(.+)$/))) return '剩余 ' + m2[1];
          if ((m2 = t.match(/^(\d+)\s+records\s*·\s*Live synced$/))) return '共 ' + m2[1] + ' 条记录 · 与手机端实时同步';
          if ((m2 = t.match(/^Matches\s+(.+)$/))) return '与 ' + m2[1] + ' 一致';
          if ((m2 = t.match(/^Uploading\s+(\d+%)\s*·\s*(.+)$/))) return '传输中 ' + m2[1] + ' · ' + m2[2];
          if ((m2 = t.match(/^(\d+)\s+total\s*·\s*(\d+)\s+ok\s*·\s*(\d+)\s+skipped(?:\s*·\s*(\d+)\s+active)?$/))) {
            return '本次共 ' + m2[1] + ' 项 · 成功 ' + m2[2] + ' · 重复跳过 ' + m2[3] + (m2[4] ? ' · 进行中 ' + m2[4] : '');
          }
          return s;
        }
      }

      var applying = false;
      function applyLang(lang) {
        if (applying) return;
        applying = true;
        try {
          curLang = lang;
          document.documentElement.lang = (lang === 'en') ? 'en' : 'zh-CN';
          document.title = (lang === 'en') ? 'Sonify · WiFi LAN Transfer' : 'Sonify · WiFi 局域网传歌';
          var btn = document.getElementById('langToggleBtn');
          if (btn) btn.textContent = (lang === 'en') ? '中 / EN ✓' : '✓ 中 / EN';
          var search = document.getElementById('historySearch');
          if (search) {
            search.placeholder = (lang === 'en')
              ? 'Search filename, album path, or client…'
              : '搜索文件名、专辑路径或来源设备…';
          }
          var histTab = document.getElementById('tabHistoryBtn');
          var cntEl = document.getElementById('histTabCount');
          if (histTab && cntEl) {
            var cnt = cntEl.textContent || '0';
            histTab.innerHTML = (lang === 'en' ? 'History (' : '上传历史 (') + '<span id="histTabCount">' + cnt + '</span>)';
          }
          var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
          var node;
          while ((node = walker.nextNode())) {
            var parent = node.parentElement;
            if (!parent || parent.tagName === 'SCRIPT' || parent.tagName === 'STYLE' || parent.id === 'langToggleBtn') continue;
            var orig = node.nodeValue;
            if (!orig || !orig.trim()) continue;
            var next = translateText(orig, lang);
            if (next !== orig) node.nodeValue = next;
          }
        } finally {
          applying = false;
        }
      }

      var origConfirm = window.confirm;
      window.confirm = function(msg) {
        if (curLang === 'en' && typeof msg === 'string' && msg.indexOf('仅清空上传历史记录') !== -1) {
          return origConfirm('Clear all upload history records? Uploaded lossless files on your phone will be kept.');
        }
        return origConfirm(msg);
      };

      window.__sonifyToggleLang = function() {
        var next = (curLang === 'zh') ? 'en' : 'zh';
        localStorage.setItem('sonify_wifi_lang', next);
        applyLang(next);
      };

      var obs = new MutationObserver(function() {
        if (curLang === 'en') applyLang('en');
      });
      obs.observe(document.body, { childList: true, subtree: true, characterData: true });
      applyLang(curLang);
    })();
  </script>
"""
            val bodyClose = "</body>"
            if (out.contains(bodyClose)) {
                out = out.replace(bodyClose, "$i18nScript$bodyClose")
            } else {
                out += i18nScript
            }
            return out.toByteArray(Charsets.UTF_8)
        }
    }
}

private class ExactLengthInputStream(
    private val upstream: InputStream,
    private var remaining: Long,
    private val eofMessage: String = "Client disconnected before upload completed",
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val b = upstream.read()
        if (b < 0) throw EOFException(eofMessage)
        remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val n = upstream.read(b, off, toRead)
        if (n < 0) throw EOFException(eofMessage)
        remaining -= n
        return n
    }
}
