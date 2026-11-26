package com.sonify.player

import com.sonify.player.wifi.CheckFileRequestItem
import com.sonify.player.wifi.CheckFileResultItem
import com.sonify.player.wifi.FileFingerprint
import com.sonify.player.wifi.UploadOutcome
import com.sonify.player.wifi.WifiHttpServer
import com.sonify.player.wifi.WifiPathSanitizer
import com.sonify.player.wifi.WifiTransferBackend
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class WifiTransferCoreTest {

    @Test
    fun fingerprint_matchesBetweenFileAndByteArray_forSmallAndLargeData() {
        val small = "hello-sonify-lossless-flac".toByteArray(Charsets.UTF_8)
        val tmpSmall = File.createTempFile("sonify_fp_small_", ".flac")
        try {
            tmpSmall.writeBytes(small)
            val fromBytes = FileFingerprint.compute(small)
            val fromFile = FileFingerprint.compute(tmpSmall)
            assertEquals(fromBytes, fromFile)
            assertTrue(fromBytes.startsWith("v1:${small.size}:"))
        } finally {
            tmpSmall.delete()
        }

        // > 4MB 大文件走三段 64KB 切片哈希，确保 File 与 ByteArray 完全一致
        val largeSize = (4 * 1024 * 1024) + 12345
        val large = ByteArray(largeSize) { i -> ((i * 31) and 0xFF).toByte() }
        val tmpLarge = File.createTempFile("sonify_fp_large_", ".wav")
        try {
            tmpLarge.writeBytes(large)
            assertEquals(FileFingerprint.compute(large), FileFingerprint.compute(tmpLarge))
        } finally {
            tmpLarge.delete()
        }
    }

    @Test
    fun pathSanitizer_blocksTraversalKeepsAlbumSubfoldersAndParsesClientInfo() {
        val (rel, name) = WifiPathSanitizer.sanitizeRelativePath("../../Miles Davis/Kind of Blue/01 - So What?.flac", "fallback.flac")
        assertFalse(rel.contains(".."))
        assertEquals("Miles Davis/Kind of Blue/01 - So What_.flac", rel)
        assertEquals("01 - So What_.flac", name)

        assertTrue(WifiPathSanitizer.isAllowedFile("track.FLAC"))
        assertTrue(WifiPathSanitizer.isAllowedFile("song.lrc"))
        assertTrue(WifiPathSanitizer.isAllowedFile("cover.jpg"))
        assertFalse(WifiPathSanitizer.isAllowedFile("malware.apk"))

        assertEquals(
            "Mac · Chrome",
            WifiPathSanitizer.parseClientInfo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36"),
        )
        assertEquals(
            "iPhone · Safari",
            WifiPathSanitizer.parseClientInfo("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"),
        )
    }

    @Test
    fun httpServer_loopbackEndToEnd_pinAuthCheckUploadAndDedup() {
        val uploadedByHash = ConcurrentHashMap<String, String>()
        val history = mutableListOf<JSONObject>()
        var issuedToken = ""

        val backend = object : WifiTransferBackend {
            override fun loadWebPageHtml(): ByteArray = "<html><body>Sonify WiFi</body></html>".toByteArray()
            override fun buildStatusJson(): JSONObject = JSONObject().put("appName", "Sonify").put("pinRequired", true)
            override fun isPinRequired(): Boolean = true
            override fun verifyPinAndIssueToken(pin: String): String? =
                if (pin == "4321") "tok_test_99".also { issuedToken = it } else null

            override fun isTokenValid(token: String?): Boolean = token != null && token == issuedToken

            override suspend fun checkFiles(items: List<CheckFileRequestItem>, userAgent: String): List<CheckFileResultItem> =
                items.map { item ->
                    val existing = uploadedByHash[item.hash]
                    if (existing != null) {
                        CheckFileResultItem(item.relativePath, "DUPLICATE_HASH", "重复", existing)
                    } else {
                        CheckFileResultItem(item.relativePath, "OK")
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
            ): UploadOutcome {
                val bytes = bodyStream.readBytes()
                val realHash = FileFingerprint.compute(bytes)
                val dup = uploadedByHash[realHash]
                if (!overwrite && dup != null) {
                    return UploadOutcome(409, "DUPLICATE", "重复跳过：$dup")
                }
                uploadedByHash[realHash] = fileName
                val rec = JSONObject().put("id", history.size + 1).put("fileName", fileName).put("status", "SUCCESS")
                history += rec
                return UploadOutcome(200, "SUCCESS", "OK", rec)
            }

            override suspend fun getHistoryJson(query: String, statusFilter: String): JSONArray =
                JSONArray().apply { history.forEach { put(it) } }

            override suspend fun deleteHistoryItem(id: Long, deleteFile: Boolean): Boolean = true
            override suspend fun clearHistory(deleteFiles: Boolean) { history.clear() }
        }

        val server = WifiHttpServer(backend)
        val port = server.start(preferredPort = 0)
        val client = OkHttpClient()
        val base = "http://127.0.0.1:$port"

        try {
            // 1. 首页与状态可读
            client.newCall(Request.Builder().url("$base/").get().build()).execute().use { r ->
                assertEquals(200, r.code)
                assertTrue(r.body!!.string().contains("Sonify WiFi"))
            }

            // 2. 未携带 PIN Token 时 /api/history 被 401 拦截
            client.newCall(Request.Builder().url("$base/api/history").get().build()).execute().use { r ->
                assertEquals(401, r.code)
            }

            // 3. 正确 PIN 解锁获取 Token
            val pinBody = """{"pin":"4321"}""".toRequestBody("application/json".toMediaType())
            val token = client.newCall(Request.Builder().url("$base/api/pin").post(pinBody).build()).execute().use { r ->
                assertEquals(200, r.code)
                JSONObject(r.body!!.string()).getString("token")
            }
            assertEquals("tok_test_99", token)

            // 4. 首次上传 C+C 测试音频（含 '+' 号与中文路径）
            val payload = "fLaC-mock-audio-bytes-0123456789".toByteArray()
            val hash = FileFingerprint.compute(payload)
            val uploadUrl = "$base/api/upload?name=C%2BC%20%E6%B5%8B%E8%AF%95.flac&relPath=Album/C%2BC%20%E6%B5%8B%E8%AF%95.flac&hash=$hash"
            client.newCall(
                Request.Builder()
                    .url(uploadUrl)
                    .header("X-Sonify-Token", token)
                    .post(payload.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
            ).execute().use { r ->
                assertEquals(200, r.code)
                val obj = JSONObject(r.body!!.string())
                assertEquals("SUCCESS", obj.getString("status"))
                assertEquals("C+C 测试.flac", obj.getJSONObject("record").getString("fileName"))
            }

            // 5. 预检同一份内容（即使改名）被防重精准拦截
            val checkJson = """{"files":[{"name":"renamed.flac","relativePath":"renamed.flac","size":${payload.size},"hash":"$hash"}]}"""
            client.newCall(
                Request.Builder()
                    .url("$base/api/check")
                    .header("X-Sonify-Token", token)
                    .post(checkJson.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().use { r ->
                assertEquals(200, r.code)
                val first = JSONObject(r.body!!.string()).getJSONArray("results").getJSONObject(0)
                assertEquals("DUPLICATE_HASH", first.getString("status"))
                assertEquals("C+C 测试.flac", first.getString("existingName"))
            }

            // 6. 绕过预检直接二次上传也被服务端 409 拦截
            client.newCall(
                Request.Builder()
                    .url(uploadUrl)
                    .header("X-Sonify-Token", token)
                    .post(payload.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
            ).execute().use { r ->
                assertEquals(409, r.code)
            }

            // 7. 查询上传历史确认记录已入库
            client.newCall(
                Request.Builder().url("$base/api/history").header("X-Sonify-Token", token).get().build()
            ).execute().use { r ->
                assertEquals(200, r.code)
                assertEquals(1, JSONObject(r.body!!.string()).getJSONArray("items").length())
            }
        } finally {
            server.stop()
        }
    }
}
