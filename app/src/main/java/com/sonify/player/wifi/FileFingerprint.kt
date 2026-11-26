package com.sonify.player.wifi

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 与浏览器端纯 JS 实现逐字节一致的快速内容指纹算法（用于 WiFi 传歌三层防重）：
 * - 文件 <= 4 MB：对全文字节计算 SHA-256；
 * - 文件 >  4 MB：对 [0..64KB) + [mid..mid+64KB) + [size-64KB..size) 三段拼接计算 SHA-256
 *   （其中 mid = (size - 65536) / 2，整数截断）；
 * - 输出格式："v1:<size>:<lowercase_sha256_hex>"。
 */
object FileFingerprint {

    const val FULL_HASH_LIMIT = 4L * 1024L * 1024L
    const val SLICE_SIZE = 65536

    fun compute(file: File): String {
        val size = file.length()
        val md = MessageDigest.getInstance("SHA-256")
        if (size <= FULL_HASH_LIMIT) {
            val buf = ByteArray(65536)
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
        } else {
            val mid = (size - SLICE_SIZE) / 2L
            val tail = size - SLICE_SIZE
            val slice = ByteArray(SLICE_SIZE)
            RandomAccessFile(file, "r").use { raf ->
                for (offset in longArrayOf(0L, mid, tail)) {
                    raf.seek(offset)
                    raf.readFully(slice)
                    md.update(slice)
                }
            }
        }
        return format(size, md.digest())
    }

    fun compute(bytes: ByteArray): String {
        val size = bytes.size.toLong()
        val md = MessageDigest.getInstance("SHA-256")
        if (size <= FULL_HASH_LIMIT) {
            md.update(bytes)
        } else {
            val mid = ((size - SLICE_SIZE) / 2L).toInt()
            val tail = (size - SLICE_SIZE).toInt()
            md.update(bytes, 0, SLICE_SIZE)
            md.update(bytes, mid, SLICE_SIZE)
            md.update(bytes, tail, SLICE_SIZE)
        }
        return format(size, md.digest())
    }

    private fun format(size: Long, digest: ByteArray): String {
        val hex = buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b.toInt() and 0xFF))
        }
        return "v1:$size:$hex"
    }
}

/**
 * WiFi 传歌路径安全清洗、支持格式判定与来源客户端识别（纯 Kotlin，100% JVM 可单测）。
 */
object WifiPathSanitizer {

    val AUDIO_EXTENSIONS = setOf(
        "flac", "wav", "mp3", "m4a", "aac", "ogg", "opus", "alac", "aiff", "aif", "wv", "ape",
    )
    val COMPANION_EXTENSIONS = setOf("lrc", "jpg", "jpeg", "png", "webp")
    val ALLOWED_EXTENSIONS = AUDIO_EXTENSIONS + COMPANION_EXTENSIONS

    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val MULTI_SPACE = Regex("""\s+""")

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").trim().lowercase()

    fun isAllowedFile(fileName: String): Boolean =
        extensionOf(fileName) in ALLOWED_EXTENSIONS

    fun isAudioFile(fileName: String): Boolean =
        extensionOf(fileName) in AUDIO_EXTENSIONS

    fun isLyricsFile(fileName: String): Boolean =
        extensionOf(fileName) == "lrc"

    fun isCoverImage(fileName: String): Boolean =
        extensionOf(fileName) in setOf("jpg", "jpeg", "png", "webp")

    /**
     * 清洗浏览器上传的相对路径（支持保留最多 3 层专辑子目录，彻底拦截 ../ 路径穿越与非法字符）。
     * 返回 Pair(清洗后的安全相对路径, 最终安全文件名)。
     */
    fun sanitizeRelativePath(rawRelativePath: String, fallbackName: String): Pair<String, String> {
        val unified = rawRelativePath.ifBlank { fallbackName }.replace('\\', '/')
        val rawSegments = unified.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .filterNot { it.endsWith(":") } // 去除 Windows 盘符如 C:

        val cleanedSegments = rawSegments.mapNotNull { seg ->
            val clean = seg
                .replace("..", "_")
                .replace(ILLEGAL, "_")
                .replace(MULTI_SPACE, " ")
                .trim()
                .trim('.')
                .take(120)
                .trim()
            clean.takeIf { it.trim('_', '.', ' ').isNotEmpty() }
        }

        if (cleanedSegments.isEmpty()) {
            val safeFallback = sanitizeSingleName(fallbackName)
            return safeFallback to safeFallback
        }

        val fileName = cleanedSegments.last()
        // 最多保留 3 层目录（例如 Artist/Album/CD1），防止恶意超深嵌套
        val dirs = cleanedSegments.dropLast(1).takeLast(3)
        val rel = (dirs + fileName).joinToString("/")
        return rel to fileName
    }

    private fun sanitizeSingleName(raw: String): String {
        val clean = raw
            .replace("..", "_")
            .replace(ILLEGAL, "_")
            .replace(MULTI_SPACE, " ")
            .trim()
            .trim('.')
            .take(120)
            .trim()
        return if (clean.trim('_', '.', ' ').isEmpty()) "track.flac" else clean
    }

    /** 解析 HTTP User-Agent，在上传历史中清晰标明来源端（如 "PC · Chrome" / "手机 · Safari"） */
    fun parseClientInfo(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        if (ua.isBlank()) return "局域网客户端"

        val isMobile = ua.contains("Mobile", ignoreCase = true) ||
            ua.contains("Android", ignoreCase = true) ||
            ua.contains("iPhone", ignoreCase = true) ||
            ua.contains("iPad", ignoreCase = true)

        val device = when {
            ua.contains("iPhone", ignoreCase = true) -> "iPhone"
            ua.contains("iPad", ignoreCase = true) -> "iPad"
            ua.contains("Android", ignoreCase = true) -> "Android 手机"
            isMobile -> "手机"
            ua.contains("Macintosh", ignoreCase = true) || ua.contains("Mac OS", ignoreCase = true) -> "Mac"
            ua.contains("Windows", ignoreCase = true) -> "Windows PC"
            ua.contains("Linux", ignoreCase = true) -> "Linux PC"
            else -> "PC"
        }

        val browser = when {
            ua.contains("Edg/", ignoreCase = true) -> "Edge"
            ua.contains("Firefox/", ignoreCase = true) -> "Firefox"
            ua.contains("Chrome/", ignoreCase = true) || ua.contains("CriOS/", ignoreCase = true) -> "Chrome"
            ua.contains("Safari/", ignoreCase = true) -> "Safari"
            ua.contains("curl/", ignoreCase = true) -> "curl"
            else -> ""
        }

        return if (browser.isNotEmpty()) "$device · $browser" else device
    }
}
