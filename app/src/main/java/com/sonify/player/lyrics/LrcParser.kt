package com.sonify.player.lyrics

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 纯 Kotlin LRC 歌词解析器（100% JVM 可单测）：
 * - 多编码自动解码（UTF-8 BOM / UTF-16LE / UTF-16BE / 严格 UTF-8 / GB18030 回退防中文乱码）；
 * - 支持一行多时间戳、两位百分秒/三位毫秒/冒号分隔/无小数、[offset:+/-ms]、剥离增强型 <mm:ss.xx>；
 * - 同一时间戳（或间隔 <= 20ms）连续两句自动合并为双语原文+译文；
 * - 无时间戳文本平滑兜底为纯文本歌词；
 * - O(log N) 二分查找当前演唱句索引。
 */
object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val META_TAG_REGEX = Regex("""^\[[a-zA-Z]{2,12}:.*]$""")
    private val ENHANCED_INLINE_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    /** 自动识别字节编码，避免老旧 GBK/UTF-16 编码 .lrc 出现中文乱码 */
    fun decodeBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // UTF-8 BOM
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        // UTF-16LE BOM
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xFE
        ) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        // UTF-16BE BOM
        if (bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFE &&
            (bytes[1].toInt() and 0xFF) == 0xFF
        ) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        // 尝试严格 UTF-8 解码，遇到非法序列回退 GB18030
        val utf8Strict = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (utf8Strict != null) return utf8Strict

        val gb = runCatching { Charset.forName("GB18030") }.getOrElse { StandardCharsets.UTF_8 }
        return String(bytes, gb)
    }

    fun parse(
        rawText: String,
        source: LyricsSource,
        userOffsetMs: Long = 0L,
    ): LyricsDocument? {
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return null

        var tagOffsetMs = 0L
        OFFSET_TAG_REGEX.find(normalized)?.let { m ->
            tagOffsetMs = m.groupValues[1].toLongOrNull() ?: 0L
        }

        val timedEntries = mutableListOf<Pair<Long, String>>()
        val plainLines = mutableListOf<String>()

        for (rawLine in normalized.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val matches = TIME_TAG_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val stripped = line
                    .replace(TIME_TAG_REGEX, "")
                    .replace(ENHANCED_INLINE_REGEX, "")
                    .trim()
                if (stripped.isEmpty()) continue
                for (m in matches) {
                    val min = m.groupValues[1].toLongOrNull() ?: continue
                    val sec = m.groupValues[2].toLongOrNull() ?: continue
                    val fracStr = m.groupValues[3]
                    val fracMs = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    // LRC [offset:+500] 表示歌词提前 500ms，因此显示时间点应减去 offset
                    val ts = (min * 60_000L + sec * 1000L + fracMs - tagOffsetMs).coerceAtLeast(0L)
                    timedEntries += ts to stripped
                }
            } else if (!META_TAG_REGEX.matches(line)) {
                plainLines += line.replace(ENHANCED_INLINE_REGEX, "").trim()
            }
        }

        if (timedEntries.isNotEmpty()) {
            val sorted = timedEntries.sortedBy { it.first }
            val merged = mergeBilingualLines(sorted)
            return LyricsDocument(
                lines = merged,
                isSynced = true,
                source = source,
                rawLrc = normalized,
                offsetMs = userOffsetMs,
            )
        }

        val cleanPlain = plainLines.filter { it.isNotEmpty() }
        if (cleanPlain.isEmpty()) return null
        return LyricsDocument(
            lines = cleanPlain.mapIndexed { idx, text -> LyricLine(timeMs = idx.toLong(), text = text) },
            isSynced = false,
            source = source,
            rawLrc = normalized,
            offsetMs = 0L,
        )
    }

    /** 合并同一时间戳（或间隔 <= 20ms）的双语原文与译文 */
    private fun mergeBilingualLines(sorted: List<Pair<Long, String>>): List<LyricLine> {
        val out = ArrayList<LyricLine>(sorted.size)
        for ((ts, text) in sorted) {
            val prev = out.lastOrNull()
            if (prev != null &&
                prev.translation == null &&
                kotlin.math.abs(ts - prev.timeMs) <= 20L &&
                !prev.text.equals(text, ignoreCase = true)
            ) {
                out[out.lastIndex] = prev.copy(translation = text)
            } else {
                out.add(LyricLine(timeMs = ts, text = text))
            }
        }
        return out
    }

    /**
     * 二分查找当前播放位置对应的歌词行索引：
     * - `offsetMs > 0` 表示将歌词整体提前（相当于有效播放时间 + offsetMs）；
     * - 未到第一句前返回 -1。
     */
    fun findActiveIndex(lines: List<LyricLine>, positionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val effectivePos = positionMs + offsetMs
        if (effectivePos < lines.first().timeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var ans = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= effectivePos) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }

    /** 将结构化同步歌词序列化为标准 LRC 文本（供 OpenSubsonic 结构化歌词落盘缓存） */
    fun formatToLrc(lines: List<LyricLine>): String = buildString {
        for (line in lines) {
            val totalSec = line.timeMs.coerceAtLeast(0L) / 1000L
            val min = totalSec / 60L
            val sec = totalSec % 60L
            val cs = (line.timeMs.coerceAtLeast(0L) % 1000L) / 10L
            val tag = "[%02d:%02d.%02d]".format(min, sec, cs)
            append(tag).append(line.text).append('\n')
            if (!line.translation.isNullOrBlank()) {
                append(tag).append(line.translation).append('\n')
            }
        }
    }
}
