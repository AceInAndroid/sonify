package com.sonify.player.lyrics

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 轻量按需内嵌歌词提取器（零外部依赖，仅读文件元数据头不解码音频）：
 * - FLAC：解析 VORBIS_COMMENT 中的 SYNCEDLYRICS / LYRICS / UNSYNCEDLYRICS
 * - MP3：解析 ID3v2 (v2.3 / v2.4) USLT 帧
 * - M4A / MP4：轻量扫描 ilst -> ©lyr 原子
 */
object EmbeddedLyricsExtractor {

    fun extractFromFile(file: File): String? {
        if (!file.isFile || !file.canRead() || file.length() < 16) return null
        return runCatching {
            when (file.extension.lowercase()) {
                "flac" -> BufferedInputStream(FileInputStream(file), 16384).use { extractFlacLyrics(it) }
                "mp3" -> BufferedInputStream(FileInputStream(file), 16384).use { extractId3v2Uslt(it) }
                "m4a", "mp4", "aac" -> extractMp4Lyrics(file)
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun extractFlacLyrics(input: InputStream): String? {
        val sig = readExact(input, 4) ?: return null
        if (String(sig, StandardCharsets.US_ASCII) != "fLaC") return null

        var synced: String? = null
        var generic: String? = null
        var unsynced: String? = null

        var isLast = false
        var guard = 0
        while (!isLast && guard < 64) {
            guard++
            val hdr = readExact(input, 4) ?: break
            isLast = (hdr[0].toInt() and 0x80) != 0
            val type = hdr[0].toInt() and 0x7F
            val len = ((hdr[1].toInt() and 0xFF) shl 16) or
                ((hdr[2].toInt() and 0xFF) shl 8) or
                (hdr[3].toInt() and 0xFF)
            if (len < 0 || len > 4 * 1024 * 1024) break

            if (type == 4) { // VORBIS_COMMENT
                val b = readExact(input, len) ?: break
                var off = 0
                if (off + 4 <= b.size) {
                    val vendorLen = le32(b, off)
                    off += 4 + vendorLen.coerceAtLeast(0)
                }
                if (off + 4 <= b.size) {
                    val count = le32(b, off).coerceIn(0, 2048)
                    off += 4
                    for (i in 0 until count) {
                        if (off + 4 > b.size) break
                        val cLen = le32(b, off)
                        off += 4
                        if (cLen < 0 || off + cLen > b.size) break
                        val entry = String(b, off, cLen, StandardCharsets.UTF_8)
                        off += cLen
                        val eq = entry.indexOf('=')
                        if (eq <= 0) continue
                        val key = entry.substring(0, eq).uppercase()
                        val value = entry.substring(eq + 1).trim()
                        if (value.isEmpty()) continue
                        when (key) {
                            "SYNCEDLYRICS" -> if (synced == null) synced = value
                            "LYRICS" -> if (generic == null) generic = value
                            "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (unsynced == null) unsynced = value
                        }
                    }
                }
                break
            } else {
                skipExact(input, len.toLong())
            }
        }
        return synced ?: generic ?: unsynced
    }

    fun extractId3v2Uslt(input: InputStream): String? {
        val hdr = readExact(input, 10) ?: return null
        if (hdr[0] != 'I'.code.toByte() || hdr[1] != 'D'.code.toByte() || hdr[2] != '3'.code.toByte()) return null
        val major = hdr[3].toInt() and 0xFF
        if (major !in 3..4) return null
        val tagSize = syncsafeInt(hdr, 6)
        if (tagSize <= 0 || tagSize > 8 * 1024 * 1024) return null

        val body = readExact(input, tagSize) ?: return null
        var off = 0
        // 跳过可选扩展头
        if ((hdr[5].toInt() and 0x40) != 0 && off + 4 <= body.size) {
            val extSize = if (major == 4) syncsafeInt(body, off) else be32(body, off)
            off += extSize.coerceAtLeast(4)
        }

        while (off + 10 <= body.size) {
            val frameId = String(body, off, 4, StandardCharsets.ISO_8859_1)
            if (frameId[0] == '\u0000') break
            val frameSize = if (major == 4) syncsafeInt(body, off + 4) else be32(body, off + 4)
            off += 10
            if (frameSize <= 0 || off + frameSize > body.size) break

            if (frameId == "USLT" && frameSize > 5) {
                val encoding = body[off].toInt() and 0xFF
                // 跳过 1 字节编码 + 3 字节语言码
                var p = off + 4
                val end = off + frameSize
                // 跳过 content descriptor（以 0x00 或 0x00 0x00 结尾）
                if (encoding == 1 || encoding == 2) {
                    while (p + 1 < end && !(body[p] == 0.toByte() && body[p + 1] == 0.toByte())) p += 2
                    p = (p + 2).coerceAtMost(end)
                } else {
                    while (p < end && body[p] != 0.toByte()) p++
                    p = (p + 1).coerceAtMost(end)
                }
                if (p < end) {
                    val slice = body.copyOfRange(p, end)
                    val decoded = when (encoding) {
                        0 -> LrcParser.decodeBytes(slice)
                        1 -> LrcParser.decodeBytes(slice)
                        2 -> String(slice, StandardCharsets.UTF_16BE)
                        3 -> String(slice, StandardCharsets.UTF_8)
                        else -> LrcParser.decodeBytes(slice)
                    }.trim('\u0000', ' ', '\n', '\r')
                    if (decoded.isNotEmpty()) return decoded
                }
            }
            off += frameSize
        }
        return null
    }

    /** 轻量扫描 MP4/M4A 头部或尾部 ©lyr 原子 */
    private fun extractMp4Lyrics(file: File): String? {
        // 仅扫描文件前 2MB（绝大多数带标签的 M4A moov 在头部）
        val maxRead = minOf(file.length(), 2L * 1024L * 1024L).toInt()
        val buf = ByteArray(maxRead)
        FileInputStream(file).use { fis ->
            var got = 0
            while (got < maxRead) {
                val n = fis.read(buf, got, maxRead - got)
                if (n <= 0) break
                got += n
            }
        }
        // 查找 0xA9 'l' 'y' 'r' 后紧跟 data atom
        val target = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        for (i in 4 until buf.size - 24) {
            if (buf[i] == target[0] && buf[i + 1] == target[1] && buf[i + 2] == target[2] && buf[i + 3] == target[3]) {
                val dataOff = i + 4
                if (dataOff + 16 <= buf.size &&
                    buf[dataOff + 4] == 'd'.code.toByte() &&
                    buf[dataOff + 5] == 'a'.code.toByte() &&
                    buf[dataOff + 6] == 't'.code.toByte() &&
                    buf[dataOff + 7] == 'a'.code.toByte()
                ) {
                    val atomSize = be32(buf, dataOff)
                    val textLen = atomSize - 16
                    if (textLen > 0 && dataOff + 16 + textLen <= buf.size) {
                        val raw = buf.copyOfRange(dataOff + 16, dataOff + 16 + textLen)
                        val text = String(raw, Charset.forName("UTF-8")).trim()
                        if (text.isNotEmpty()) return text
                    }
                }
            }
        }
        return null
    }

    private fun readExact(input: InputStream, count: Int): ByteArray? {
        val buf = ByteArray(count)
        var off = 0
        while (off < count) {
            val n = input.read(buf, off, count - off)
            if (n <= 0) return null
            off += n
        }
        return buf
    }

    private fun skipExact(input: InputStream, count: Long) {
        var rem = count
        while (rem > 0L) {
            val s = input.skip(rem)
            if (s <= 0L) {
                if (input.read() < 0) break
                rem--
            } else {
                rem -= s
            }
        }
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun syncsafeInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)
}
