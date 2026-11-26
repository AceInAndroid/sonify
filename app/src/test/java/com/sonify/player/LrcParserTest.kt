package com.sonify.player

import com.sonify.player.lyrics.LrcParser
import com.sonify.player.lyrics.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LrcParserTest {

    @Test
    fun parse_multiTimestampOffsetEnhancedAndBilingualMerge() {
        val raw = """
            [ti:Test Song]
            [ar:Sonify]
            [offset:500]
            [00:10.50][01:02.345]Hello <00:10.80>world
            [00:10.50]你好世界
            [00:20:25]Second line
        """.trimIndent()

        val doc = LrcParser.parse(raw, LyricsSource.LOCAL_LRC)
        assertNotNull(doc)
        assertTrue(doc!!.isSynced)
        assertEquals(3, doc.lines.size)

        // offset:+500ms => 10500 - 500 = 10000ms，增强 <...> 标签已剥离，双语同时间戳自动合并
        assertEquals(10_000L, doc.lines[0].timeMs)
        assertEquals("Hello world", doc.lines[0].text)
        assertEquals("你好世界", doc.lines[0].translation)

        // [00:20:25] - 500ms = 19750ms
        assertEquals(19_750L, doc.lines[1].timeMs)
        assertEquals("Second line", doc.lines[1].text)
        assertNull(doc.lines[1].translation)

        // [01:02.345] - 500ms = 61845ms
        assertEquals(61_845L, doc.lines[2].timeMs)
        assertEquals("Hello world", doc.lines[2].text)
    }

    @Test
    fun parse_plainUnsyncedLyricsFallback() {
        val raw = """
            First plain line
            Second plain line
        """.trimIndent()
        val doc = LrcParser.parse(raw, LyricsSource.LRCLIB)
        assertNotNull(doc)
        assertFalse(doc!!.isSynced)
        assertEquals(2, doc.lines.size)
        assertEquals("First plain line", doc.lines[0].text)
    }

    @Test
    fun decodeBytes_handlesUtf8BomAndGbkChineseWithoutMojibake() {
        val chinese = "[00:01.00]海阔天空 - 粤语无损"
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + chinese.toByteArray(Charsets.UTF_8)
        assertEquals(chinese, LrcParser.decodeBytes(utf8Bom))

        val gbkBytes = chinese.toByteArray(Charset.forName("GB18030"))
        assertEquals(chinese, LrcParser.decodeBytes(gbkBytes))
    }

    @Test
    fun findActiveIndex_binarySearchAndOffsetBehavior() {
        val doc = LrcParser.parse(
            """
                [00:05.00]Line 0
                [00:10.00]Line 1
                [00:15.00]Line 2
            """.trimIndent(),
            LyricsSource.LOCAL_LRC,
        )!!
        assertEquals(-1, LrcParser.findActiveIndex(doc.lines, 4999L))
        assertEquals(0, LrcParser.findActiveIndex(doc.lines, 5000L))
        assertEquals(1, LrcParser.findActiveIndex(doc.lines, 12_000L))
        assertEquals(2, LrcParser.findActiveIndex(doc.lines, 99_000L))
        // 用户提前偏移 +600ms，在 9500ms 时就已切到 10000ms 的 Line 1
        assertEquals(1, LrcParser.findActiveIndex(doc.lines, 9500L, offsetMs = 600L))
    }
}
