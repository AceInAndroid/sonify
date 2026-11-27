package com.sonify.player.subsonic

import com.sonify.player.data.StreamQuality
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Subsonic API v1.16.1 认证与 URL 构造纯函数工具（无 Android 依赖，100% JVM 可单测）。
 */
object SubsonicAuth {

    const val API_VERSION = "1.16.1"
    const val CLIENT_NAME = "Sonify"

    private val SALT_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    private val secureRandom = SecureRandom()

    /**
     * 规范化用户输入的服务器地址：
     * - 去除首尾空白与尾部多余斜杠；
     * - 去除误粘贴的 "/rest" 后缀；
     * - 若未写协议前缀，默认补 "https://"（若是纯内网 IP / localhost 则补 "http://"）。
     */
    fun normalizeBaseUrl(raw: String): String {
        var s = raw.trim()
            .replace('：', ':')
            .replace('／', '/')
            .replace('。', '.')
            .replace('．', '.')
            .replace(" ", "")
        require(s.isNotEmpty()) { "服务器地址不能为空" }
        if (s.startsWith("http://", ignoreCase = true)) {
            s = "http://" + s.substring(7)
        } else if (s.startsWith("https://", ignoreCase = true)) {
            s = "https://" + s.substring(8)
        }

        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            val hostPart = s.substringBefore('/').substringBefore(':')
            val isLikelyLocal = hostPart.equals("localhost", ignoreCase = true) ||
                hostPart.startsWith("127.") ||
                hostPart.startsWith("10.") ||
                hostPart.startsWith("192.168.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.").containsMatchIn(hostPart) ||
                hostPart.endsWith(".local", ignoreCase = true)
            s = (if (isLikelyLocal) "http://" else "https://") + s
        }

        while (s.endsWith("/")) s = s.dropLast(1)
        if (s.endsWith("/rest", ignoreCase = true)) {
            s = s.dropLast(5)
            while (s.endsWith("/")) s = s.dropLast(1)
        }

        val parsed = s.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("服务器地址格式不正确：$raw")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "仅支持 http:// 或 https:// 协议"
        }
        return s
    }

    fun generateSalt(length: Int = 12): String {
        val chars = CharArray(length.coerceAtLeast(6)) {
            SALT_CHARS[secureRandom.nextInt(SALT_CHARS.size)]
        }
        return String(chars)
    }

    fun computeToken(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest((password + salt).toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                append("%02x".format(b.toInt() and 0xFF))
            }
        }
    }

    fun encodeLegacyPassword(password: String): String {
        val bytes = password.toByteArray(Charsets.UTF_8)
        return buildString(4 + bytes.size * 2) {
            append("enc:")
            for (b in bytes) {
                append("%02x".format(b.toInt() and 0xFF))
            }
        }
    }

    fun buildEndpointUrl(
        creds: SubsonicCredentials,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        saltOverride: String? = null,
    ): HttpUrl = buildEndpointUrlMulti(
        creds = creds,
        endpoint = endpoint,
        params = params.entries.map { it.key to it.value },
        saltOverride = saltOverride,
    )

    fun buildEndpointUrlMulti(
        creds: SubsonicCredentials,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList(),
        saltOverride: String? = null,
    ): HttpUrl {
        val base = normalizeBaseUrl(creds.baseUrl)
        val cleanEndpoint = endpoint.trimStart('/').removeSuffix(".view")
        val builder = "$base/rest/$cleanEndpoint.view".toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("无法构造接口地址: $base / $endpoint")

        builder.addQueryParameter("u", creds.username)
        if (creds.useLegacyAuth) {
            builder.addQueryParameter("p", encodeLegacyPassword(creds.password))
        } else {
            val salt = saltOverride ?: generateSalt()
            builder.addQueryParameter("t", computeToken(creds.password, salt))
            builder.addQueryParameter("s", salt)
        }
        builder.addQueryParameter("v", API_VERSION)
        builder.addQueryParameter("c", CLIENT_NAME)
        builder.addQueryParameter("f", "json")

        for ((k, v) in params) {
            builder.addQueryParameter(k, v)
        }
        return builder.build()
    }

    fun buildStreamUrl(
        creds: SubsonicCredentials,
        songId: String,
        quality: StreamQuality = StreamQuality.RAW,
        saltOverride: String? = null,
    ): HttpUrl {
        val params = linkedMapOf("id" to songId)
        if (quality == StreamQuality.RAW) {
            params["format"] = "raw"
        } else {
            if (quality.format.isNotBlank()) params["format"] = quality.format
            if (quality.maxBitRate > 0) params["maxBitRate"] = quality.maxBitRate.toString()
        }
        return buildEndpointUrl(creds, "stream", params, saltOverride)
    }

    /**
     * 构造离线下载 URL：
     * - RAW（原盘无损）默认使用 rest/download.view 获取服务端未修改原文件；
     * - 转码档位走 rest/stream.view 携带 format + maxBitRate。
     */
    fun buildDownloadUrl(
        creds: SubsonicCredentials,
        songId: String,
        quality: StreamQuality = StreamQuality.RAW,
        saltOverride: String? = null,
    ): HttpUrl = if (quality == StreamQuality.RAW) {
        buildEndpointUrl(creds, "download", mapOf("id" to songId), saltOverride)
    } else {
        buildStreamUrl(creds, songId, quality, saltOverride)
    }

    fun buildCoverArtUrl(
        creds: SubsonicCredentials,
        coverArtId: String,
        size: Int = 600,
        saltOverride: String? = null,
    ): HttpUrl {
        val params = linkedMapOf("id" to coverArtId)
        if (size > 0) params["size"] = size.toString()
        return buildEndpointUrl(creds, "getCoverArt", params, saltOverride)
    }
}
