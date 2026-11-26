package com.sonify.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProgressMathTest {

    @Test
    fun lineProgress_standardLrc_advancesSmoothlyAndCapsLongGap() {
        val lines = listOf(
            LyricLine(timeMs = 10_000L, text = "First line"),
            LyricLine(timeMs = 14_000L, text = "Second line"),
            LyricLine(timeMs = 45_000L, text = "After long solo"),
        )

        // 起唱前返回 0
        assertEquals(0f, LyricsProgressMath.lineProgress(lines, 0, 9_500L), 0.001f)
        // 4s 句长按 0.92 系数 = 3680ms，过 1840ms 应约 0.5
        assertEquals(0.5f, LyricsProgressMath.lineProgress(lines, 0, 11_840L), 0.02f)
        // 超过本句有效时长钳制到 1.0
        assertEquals(1.0f, LyricsProgressMath.lineProgress(lines, 0, 13_900L), 0.001f)

        // 31s 超长句间空档不应拖慢扫色（上限 9500ms * 0.92 = 8740ms）
        val midLongGap = LyricsProgressMath.lineProgress(lines, 1, 22_800L)
        assertEquals(1.0f, midLongGap, 0.001f)
    }

    @Test
    fun lineProgress_respectsOffsetMs() {
        val lines = listOf(
            LyricLine(timeMs = 10_000L, text = "Line A"),
            LyricLine(timeMs = 15_000L, text = "Line B"),
        )
        // offset = +1000ms 时，播放到 11_300ms 相当于有效时间 12_300ms（已进 2300 / 4600 = 0.5）
        val p = LyricsProgressMath.lineProgress(lines, 0, positionMs = 11_300L, offsetMs = 1_000L)
        assertEquals(0.5f, p, 0.02f)
    }

    @Test
    fun enhancedLrc_parsesWordTimestampsAndDrivesExactWordSweep() {
        val raw = """
            [00:10.00]<00:10.00>Hello <00:11.00>world <00:12.00>now
            [00:10.00]你好世界
            [00:15.00]Next verse
        """.trimIndent()

        val doc = LrcParser.parse(raw, LyricsSource.LOCAL_LRC)
        assertNotNull(doc)
        doc!!
        assertEquals(2, doc.lines.size)

        val first = doc.lines[0]
        assertEquals("Hello world now", first.text)
        assertEquals("你好世界", first.translation)
        assertEquals(3, first.words.size)
        assertEquals("Hello ", first.words[0].text)
        assertEquals(10_000L, first.words[0].startMs)
        assertEquals(11_000L, first.words[0].endMs)
        assertEquals("world ", first.words[1].text)
        assertEquals("now", first.words[2].text)

        // 总字符数 6 + 6 + 3 = 15；到 11_000ms 正好完成第一个词 "Hello " -> 6/15 = 0.4
        val pAtWord1End = LyricsProgressMath.lineProgress(doc.lines, 0, positionMs = 11_000L)
        assertEquals(6f / 15f, pAtWord1End, 0.01f)

        // 到 11_500ms 完成第一个词 + 第二个词一半 -> (6 + 3)/15 = 0.6
        val pAtWord2Mid = LyricsProgressMath.lineProgress(doc.lines, 0, positionMs = 11_500L)
        assertEquals(9f / 15f, pAtWord2Mid, 0.01f)
    }

    @Test
    fun interludeState_detectsLongIntroAndMidBreak_ignoresShortGaps() {
        val lines = listOf(
            LyricLine(timeMs = 12_000L, text = "Opening"),
            LyricLine(timeMs = 16_000L, text = "Quick follow"),
            LyricLine(timeMs = 30_000L, text = "After guitar solo"),
        )

        // 12s 长前奏中段：间奏三点应激活，且第一颗点已充满、第二颗半亮
        val intro = LyricsProgressMath.interludeState(lines, activeIndex = -1, positionMs = 6_000L)
        assertTrue(intro.isActive)
        assertEquals(0.5f, intro.progress, 0.01f)
        assertEquals(3, intro.dotAlphas.size)
        assertTrue(intro.dotAlphas[0] > intro.dotAlphas[1])
        assertTrue(intro.dotAlphas[1] > intro.dotAlphas[2])

        // 短间距（12s -> 16s 仅 4s）不触发间奏三点
        val shortGap = LyricsProgressMath.interludeState(lines, activeIndex = 0, positionMs = 15_000L)
        assertFalse(shortGap.isActive)

        // 句间长器乐间奏（16s + 4s = 20s 到 30s，跨度 10s >= 5s）在 25s 时激活且进度 0.5
        val solo = LyricsProgressMath.interludeState(lines, activeIndex = 1, positionMs = 25_000L)
        assertTrue(solo.isActive)
        assertEquals(0.5f, solo.progress, 0.01f)
    }

    @Test
    fun depthStyle_decaysWithDistanceAndClearsWhenDragging() {
        val d0 = LyricsProgressMath.depthStyle(0, isDragging = false)
        val d1 = LyricsProgressMath.depthStyle(1, isDragging = false)
        val d2 = LyricsProgressMath.depthStyle(2, isDragging = false)
        val d4 = LyricsProgressMath.depthStyle(-4, isDragging = false)

        assertTrue(d0.alpha > d1.alpha && d1.alpha > d2.alpha && d2.alpha > d4.alpha)
        assertTrue(d0.scale > d1.scale && d1.scale > d2.scale)
        assertEquals(0f, d0.blurDp, 0.001f)
        assertTrue(d2.blurDp > 0f && d4.blurDp > d2.blurDp)

        val dragging = LyricsProgressMath.depthStyle(3, isDragging = true)
        assertEquals(0f, dragging.blurDp, 0.001f)
        assertTrue(dragging.alpha > 0.7f)
    }
}
