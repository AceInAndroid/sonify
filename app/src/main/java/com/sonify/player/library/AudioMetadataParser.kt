package com.sonify.player.library

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.sonify.player.data.TrackEntity
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 纯头部解析结果（无 Android 依赖，便于 JVM 单元测试覆盖 FLAC/WAV 二进制解析）。
 */
data class ParsedAudioMeta(
    val format: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val bitrateKbps: Int = 0,
    val embeddedCoverBytes: ByteArray? = null,
)

/**
 * 极速无损音频元数据与封面提取器：
 * - FLAC：直接读 STREAMINFO / VORBIS_COMMENT / PICTURE（无需全文件解码）
 * - WAV：直接读 RIFF/WAVE fmt + data + LIST/INFO 块
 * - 其它格式（MP3 / M4A / AAC / OGG / OPUS / AIFF）：MediaMetadataRetriever + MediaExtractor 兜底
 * - 封面兜底：同目录查找 cover / folder / front / 封面 图片
 */
object AudioMetadataParser {

    val SUPPORTED_EXTENSIONS = setOf(
        "flac", "wav", "aif", "aiff", "mp3", "m4a", "aac", "ogg", "opus"
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun parseFile(file: File, coversDir: File): TrackEntity? {
        if (!file.isFile || !file.canRead() || file.length() < 16) return null
        val ext = file.extension.lowercase()
        if (ext !in SUPPORTED_EXTENSIONS) return null

        val header = ByteArray(12)
        val readHead = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(0)
        if (readHead < 4) return null

        val magic = detectMagicFormat(header, readHead, ext) ?: return null

        val parsed = when (magic) {
            "FLAC" -> runCatching {
                BufferedInputStream(FileInputStream(file), 16384).use {
                    parseFlacStream(it, file.length())
                }
            }.getOrNull()

            "WAV" -> runCatching {
                BufferedInputStream(FileInputStream(file), 16384).use {
                    parseWavStream(it, file.length())
                }
            }.getOrNull()

            else -> null
        } ?: parseWithAndroidRetriever(file, magic)

        val fallback = parseFilenameFallback(file)
        val title = parsed.title?.takeIf { it.isNotBlank() } ?: fallback.first
        val artist = parsed.artist?.takeIf { it.isNotBlank() }
            ?: fallback.second
            ?: "未知艺术家"
        val album = parsed.album?.takeIf { it.isNotBlank() }
            ?: file.parentFile?.name?.takeIf { it.isNotBlank() && it != "Music" }
            ?: "未知专辑"
        val trackNo = if (parsed.trackNumber > 0) parsed.trackNumber else fallback.third

        val coverPath = resolveCover(file, coversDir, parsed.embeddedCoverBytes, album, artist)
        val lrcFile = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
        val lrcPath = if (lrcFile.isFile) lrcFile.absolutePath else null

        val bitrate = if (parsed.bitrateKbps > 0) {
            parsed.bitrateKbps
        } else if (parsed.durationMs > 0) {
            ((file.length() * 8L) / parsed.durationMs).toInt()
        } else 0

        return TrackEntity(
            path = file.absolutePath,
            title = title,
            artist = artist,
            album = album,
            albumArtist = parsed.albumArtist?.takeIf { it.isNotBlank() } ?: artist,
            trackNumber = trackNo,
            discNumber = parsed.discNumber,
            durationMs = parsed.durationMs,
            sampleRate = parsed.sampleRate,
            bitDepth = parsed.bitDepth,
            channels = if (parsed.channels > 0) parsed.channels else 2,
            bitrateKbps = bitrate,
            format = parsed.format,
            fileSize = file.length(),
            lastModified = file.lastModified(),
            coverPath = coverPath,
            lrcPath = lrcPath,
            folderPath = file.parentFile?.absolutePath ?: "",
        )
    }

    /** 校验文件头魔数，过滤伪装后缀的非音频文件 */
    fun detectMagicFormat(head: ByteArray, len: Int, ext: String): String? {
        if (len >= 4) {
            val s4 = String(head, 0, 4, StandardCharsets.US_ASCII)
            if (s4 == "fLaC") return "FLAC"
            if (s4 == "OggS") return if (ext == "opus") "OPUS" else "OGG"
            if (s4 == "RIFF" && len >= 12 &&
                String(head, 8, 4, StandardCharsets.US_ASCII) == "WAVE"
            ) return "WAV"
            if (s4 == "FORM" && len >= 12) {
                val sub = String(head, 8, 4, StandardCharsets.US_ASCII)
                if (sub == "AIFF" || sub == "AIFC") return "AIFF"
            }
            if (s4.startsWith("ID3")) {
                return if (ext == "flac") "FLAC" else "MP3"
            }
            if (len >= 8 && String(head, 4, 4, StandardCharsets.US_ASCII) == "ftyp") {
                return if (ext == "aac") "AAC" else "M4A"
            }
            // MPEG ADTS / MP3 帧同步字 0xFFE0
            val b0 = head[0].toInt() and 0xFF
            val b1 = head[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                return if (ext == "aac") "AAC" else "MP3"
            }
        }
        return null
    }

    /** 纯流式解析 FLAC STREAMINFO + VORBIS_COMMENT + PICTURE */
    fun parseFlacStream(input: InputStream, fileSize: Long): ParsedAudioMeta? {
        val sig = readExact(input, 4) ?: return null
        if (String(sig, StandardCharsets.US_ASCII) != "fLaC") return null

        var sampleRate = 0
        var channels = 2
        var bitDepth = 0
        var totalSamples = 0L

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNumber = 0
        var discNumber = 0
        var coverBytes: ByteArray? = null
        var coverPriority = -1

        var isLast = false
        var blockGuard = 0
        while (!isLast && blockGuard < 64) {
            blockGuard++
            val hdr = readExact(input, 4) ?: break
            isLast = (hdr[0].toInt() and 0x80) != 0
            val type = hdr[0].toInt() and 0x7F
            val length = ((hdr[1].toInt() and 0xFF) shl 16) or
                ((hdr[2].toInt() and 0xFF) shl 8) or
                (hdr[3].toInt() and 0xFF)

            if (length < 0 || length > 32 * 1024 * 1024) break

            when (type) {
                0 -> { // STREAMINFO
                    if (length < 34) {
                        skipExact(input, length.toLong())
                    } else {
                        val b = readExact(input, length) ?: break
                        // bytes 10..17: 20bit sr | 3bit ch-1 | 5bit bps-1 | 36bit totalSamples
                        val b10 = b[10].toInt() and 0xFF
                        val b11 = b[11].toInt() and 0xFF
                        val b12 = b[12].toInt() and 0xFF
                        val b13 = b[13].toInt() and 0xFF
                        sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                        channels = ((b12 ushr 1) and 0x07) + 1
                        bitDepth = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
                        totalSamples = ((b13.toLong() and 0x0FL) shl 32) or
                            ((b[14].toLong() and 0xFFL) shl 24) or
                            ((b[15].toLong() and 0xFFL) shl 16) or
                            ((b[16].toLong() and 0xFFL) shl 8) or
                            (b[17].toLong() and 0xFFL)
                    }
                }

                4 -> { // VORBIS_COMMENT
                    if (length > 2 * 1024 * 1024) {
                        skipExact(input, length.toLong())
                    } else {
                        val b = readExact(input, length) ?: break
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
                                    "TITLE" -> if (title == null) title = value
                                    "ARTIST" -> if (artist == null) artist = value
                                    "ALBUM" -> if (album == null) album = value
                                    "ALBUMARTIST", "ALBUM ARTIST" -> if (albumArtist == null) albumArtist = value
                                    "TRACKNUMBER" -> trackNumber = parseLeadingInt(value)
                                    "DISCNUMBER" -> discNumber = parseLeadingInt(value)
                                }
                            }
                        }
                    }
                }

                6 -> { // PICTURE
                    if (length > 10 * 1024 * 1024) {
                        skipExact(input, length.toLong())
                    } else {
                        val b = readExact(input, length) ?: break
                        var off = 0
                        if (off + 8 <= b.size) {
                            val picType = be32(b, off); off += 4
                            val mimeLen = be32(b, off); off += 4 + mimeLen.coerceAtLeast(0)
                            if (off + 4 <= b.size) {
                                val descLen = be32(b, off); off += 4 + descLen.coerceAtLeast(0)
                                off += 16 // width, height, depth, colors
                                if (off + 4 <= b.size) {
                                    val dataLen = be32(b, off); off += 4
                                    val priority = if (picType == 3) 2 else 1 // 3 = Cover (front)
                                    if (dataLen > 0 && off + dataLen <= b.size && priority > coverPriority) {
                                        coverBytes = b.copyOfRange(off, off + dataLen)
                                        coverPriority = priority
                                    }
                                }
                            }
                        }
                    }
                }

                else -> skipExact(input, length.toLong())
            }
        }

        if (sampleRate <= 0) return null
        val durationMs = if (totalSamples > 0) (totalSamples * 1000L) / sampleRate else 0L
        val bitrateKbps = if (durationMs > 0) ((fileSize * 8L) / durationMs).toInt() else 0

        return ParsedAudioMeta(
            format = "FLAC",
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitrateKbps = bitrateKbps,
            embeddedCoverBytes = coverBytes,
        )
    }

    /** 纯流式解析 WAV RIFF/WAVE fmt + data + LIST/INFO */
    fun parseWavStream(input: InputStream, fileSize: Long): ParsedAudioMeta? {
        val riff = readExact(input, 12) ?: return null
        if (String(riff, 0, 4, StandardCharsets.US_ASCII) != "RIFF" ||
            String(riff, 8, 4, StandardCharsets.US_ASCII) != "WAVE"
        ) return null

        var channels = 2
        var sampleRate = 0
        var byteRate = 0
        var bitDepth = 0
        var dataSize = 0L

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber = 0

        var chunkGuard = 0
        while (chunkGuard < 64) {
            chunkGuard++
            val hdr = readExact(input, 8) ?: break
            val id = String(hdr, 0, 4, StandardCharsets.US_ASCII)
            val size = le32(hdr, 4).toLong() and 0xFFFFFFFFL
            val paddedSize = size + (size and 1L)

            when (id) {
                "fmt " -> {
                    val toRead = size.coerceAtMost(64).toInt()
                    val b = readExact(input, toRead) ?: break
                    if (b.size >= 16) {
                        channels = le16(b, 2)
                        sampleRate = le32(b, 4)
                        byteRate = le32(b, 8)
                        bitDepth = le16(b, 14)
                    }
                    if (paddedSize > toRead) skipExact(input, paddedSize - toRead)
                }

                "data" -> {
                    dataSize = size
                    // data 块之后如果还有尾部 INFO，但一般 WAV 很大，跳过剩余以保证毫秒级扫描
                    break
                }

                "LIST" -> {
                    if (size in 4..65536) {
                        val b = readExact(input, size.toInt()) ?: break
                        if ((size and 1L) != 0L) skipExact(input, 1)
                        if (b.size >= 4 && String(b, 0, 4, StandardCharsets.US_ASCII) == "INFO") {
                            var off = 4
                            while (off + 8 <= b.size) {
                                val subId = String(b, off, 4, StandardCharsets.US_ASCII)
                                val subLen = le32(b, off + 4).coerceAtLeast(0)
                                off += 8
                                if (off + subLen > b.size) break
                                val text = String(b, off, subLen, StandardCharsets.UTF_8)
                                    .trimEnd('\u0000').trim()
                                off += subLen + (subLen and 1)
                                if (text.isEmpty()) continue
                                when (subId) {
                                    "INAM" -> title = text
                                    "IART" -> artist = text
                                    "IPRD" -> album = text
                                    "ITRK" -> trackNumber = parseLeadingInt(text)
                                }
                            }
                        }
                    } else {
                        skipExact(input, paddedSize)
                    }
                }

                else -> {
                    if (paddedSize > fileSize) break
                    if (!skipExact(input, paddedSize)) break
                }
            }
        }

        if (sampleRate <= 0) return null
        val calcByteRate = if (byteRate > 0) byteRate else sampleRate * channels * (bitDepth / 8).coerceAtLeast(1)
        val durationMs = if (calcByteRate > 0 && dataSize > 0) (dataSize * 1000L) / calcByteRate else 0L
        val bitrateKbps = (calcByteRate * 8) / 1000

        return ParsedAudioMeta(
            format = "WAV",
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitrateKbps = bitrateKbps,
        )
    }

    private fun parseWithAndroidRetriever(file: File, format: String): ParsedAudioMeta {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNumber = 0
        var discNumber = 0
        var durationMs = 0L
        var bitrateKbps = 0
        var sampleRate = 0
        var channels = 2
        var bitDepth = 0
        var cover: ByteArray? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            albumArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            trackNumber = parseLeadingInt(
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            )
            discNumber = parseLeadingInt(
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            )
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            bitrateKbps = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0) / 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull() ?: 0
                bitDepth = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull() ?: 0
            }
            cover = mmr.embeddedPicture
        } catch (_: Throwable) {
        } finally {
            runCatching { mmr.release() }
        }

        if (sampleRate == 0) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            bitDepth = when (fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                                android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                                else -> bitDepth
                            }
                        }
                        break
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { extractor.release() }
            }
        }

        return ParsedAudioMeta(
            format = format,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitrateKbps = bitrateKbps,
            embeddedCoverBytes = cover,
        )
    }

    /** 针对 "(06) [周杰倫] 黑色幽默.flac" / "01 - 晴天.flac" 提取标题、艺人、曲号 */
    fun parseFilenameFallback(file: File): Triple<String, String?, Int> {
        var name = file.nameWithoutExtension.trim()
        var trackNo = 0
        var artist: String? = null

        val leadingNum = Regex("^\\(?(\\d{1,3})\\)?[.\\s_-]+(.+)$").find(name)
        if (leadingNum != null) {
            trackNo = leadingNum.groupValues[1].toIntOrNull() ?: 0
            name = leadingNum.groupValues[2].trim()
        }

        val bracketArtist = Regex("^\\[([^\\]]+)]\\s*(.+)$").find(name)
        if (bracketArtist != null) {
            artist = bracketArtist.groupValues[1].trim()
            name = bracketArtist.groupValues[2].trim()
        } else {
            val dashSplit = name.split(" - ", limit = 2)
            if (dashSplit.size == 2 && dashSplit[0].isNotBlank() && dashSplit[1].isNotBlank()) {
                artist = dashSplit[0].trim()
                name = dashSplit[1].trim()
            }
        }
        return Triple(name.ifBlank { file.nameWithoutExtension }, artist, trackNo)
    }

    private fun resolveCover(
        audioFile: File,
        coversDir: File,
        embedded: ByteArray?,
        album: String,
        artist: String,
    ): String? {
        if (embedded != null && embedded.size > 64) {
            if (!coversDir.exists()) coversDir.mkdirs()
            val key = if (album != "未知专辑") "$artist::$album" else audioFile.absolutePath
            val hash = md5Hex(key)
            val ext = if (embedded[0] == 0x89.toByte() && embedded[1] == 0x50.toByte()) "png" else "jpg"
            val outFile = File(coversDir, "$hash.$ext")
            if (!outFile.exists()) {
                runCatching { outFile.writeBytes(embedded) }
            }
            if (outFile.isFile) return outFile.absolutePath
        }

        // 同目录封面图兜底（支持 cover/folder/front/*封面*，避开 封底/back）
        val parent = audioFile.parentFile ?: return null
        val imgs = parent.listFiles { f ->
            f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
        } ?: return null
        if (imgs.isEmpty()) return null

        val ranked = imgs.sortedBy { f ->
            val n = f.nameWithoutExtension.lowercase()
            when {
                n == "cover" || n == "folder" || n == "front" -> 0
                n.contains("封面") && !n.contains("封底") -> 1
                n.contains("cover") || n.contains("front") -> 2
                n.contains("封底") || n.contains("back") || n.contains("cd") -> 5
                else -> 3
            }
        }
        return ranked.firstOrNull()?.absolutePath
    }

    private fun parseLeadingInt(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        return Regex("^(\\d+)").find(s.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun readExact(input: InputStream, n: Int): ByteArray? {
        if (n <= 0) return ByteArray(0)
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) return null
            off += r
        }
        return buf
    }

    private fun skipExact(input: InputStream, n: Long): Boolean {
        var remaining = n
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (input.read() < 0) return false
                remaining--
            }
        }
        return true
    }

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

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
}
