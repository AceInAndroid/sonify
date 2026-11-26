package com.sonify.player.lyrics

import kotlin.math.abs

/**
 * 间奏呼吸三点视觉状态（前奏或长器乐间奏期间使用）。
 */
data class InterludeState(
    val isActive: Boolean,
    val progress: Float = 0f,
    val dotAlphas: List<Float> = listOf(0.2f, 0.2f, 0.2f),
)

/**
 * 距离当前演唱句的景深视觉参数（透明度、缩放、API 31+ 可选模糊半径）。
 */
data class LyricDepthStyle(
    val alpha: Float,
    val scale: Float,
    val blurDp: Float,
)

/**
 * 纯 Kotlin 沉浸式歌词动效数学核心（100% JVM 可单测）：
 * - 计算逐句/增强型逐字流光扫色进度（0f..1f）；
 * - 判定前奏与句间长器乐间奏并生成 3 颗呼吸光点节奏；
 * - 计算远近歌词行的景深透明与缩放衰减曲线。
 */
object LyricsProgressMath {

    private const val DEFAULT_LAST_LINE_DURATION_MS = 6_000L
    private const val MAX_SWEEP_DURATION_MS = 9_500L
    private const val MIN_SWEEP_DURATION_MS = 350L
    private const val INTERLUDE_LEAD_IN_MS = 4_000L

    /**
     * 计算当前演唱句的流光扫色推进比例 [0f, 1f]。
     * 若当前句包含增强型 `<mm:ss.xx>` 逐字时间戳，按逐字真实时间与字符权重精确映射；
     * 否则按标准行级时间戳与本句有效持续时长平滑推演。
     */
    fun lineProgress(
        lines: List<LyricLine>,
        activeIndex: Int,
        positionMs: Long,
        offsetMs: Long = 0L,
        trackDurationMs: Long = 0L,
    ): Float {
        if (activeIndex !in lines.indices) return 0f
        val line = lines[activeIndex]
        val effectivePos = positionMs + offsetMs
        if (effectivePos <= line.timeMs) return 0f

        val nextStartMs = when {
            activeIndex + 1 < lines.size -> lines[activeIndex + 1].timeMs
            trackDurationMs > line.timeMs -> trackDurationMs
            else -> line.timeMs + DEFAULT_LAST_LINE_DURATION_MS
        }

        if (line.words.isNotEmpty()) {
            return wordLevelProgress(line.words, effectivePos, nextStartMs)
        }

        val rawDuration = (nextStartMs - line.timeMs).coerceAtLeast(MIN_SWEEP_DURATION_MS)
        val singingDuration = (rawDuration.coerceAtMost(MAX_SWEEP_DURATION_MS) * 0.92f)
            .toLong()
            .coerceAtLeast(MIN_SWEEP_DURATION_MS)

        return ((effectivePos - line.timeMs).toFloat() / singingDuration.toFloat()).coerceIn(0f, 1f)
    }

    private fun wordLevelProgress(
        words: List<LyricWord>,
        effectivePos: Long,
        fallbackEndMs: Long,
    ): Float {
        val totalChars = words.sumOf { it.text.length }.coerceAtLeast(1)
        var completedChars = 0f

        for (i in words.indices) {
            val w = words[i]
            val wLen = w.text.length.toFloat()
            val wStart = w.startMs
            val wEnd = when {
                w.endMs > wStart -> w.endMs
                i + 1 < words.size && words[i + 1].startMs > wStart -> words[i + 1].startMs
                fallbackEndMs > wStart -> fallbackEndMs
                else -> wStart + 500L
            }

            when {
                effectivePos >= wEnd -> completedChars += wLen
                effectivePos > wStart -> {
                    val frac = ((effectivePos - wStart).toFloat() / (wEnd - wStart).coerceAtLeast(1L).toFloat())
                        .coerceIn(0f, 1f)
                    completedChars += wLen * frac
                    break
                }
                else -> break
            }
        }
        return (completedChars / totalChars.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 判定当前是否处于前奏或长器乐间奏，返回 3 颗呼吸光点的逐颗点亮状态。
     */
    fun interludeState(
        lines: List<LyricLine>,
        activeIndex: Int,
        positionMs: Long,
        offsetMs: Long = 0L,
        minGapMs: Long = 5_000L,
    ): InterludeState {
        if (lines.isEmpty()) return InterludeState(isActive = false)
        val effectivePos = positionMs + offsetMs

        // 1) 前奏阶段：未到第一句且前奏 >= minGapMs
        if (activeIndex < 0) {
            val firstMs = lines.first().timeMs
            if (firstMs >= minGapMs && effectivePos in 0L until firstMs) {
                val p = (effectivePos.toFloat() / firstMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                return InterludeState(isActive = true, progress = p, dotAlphas = computeDotAlphas(p))
            }
            return InterludeState(isActive = false)
        }

        // 2) 句间长器乐间奏：本句唱完后距下一句仍有 >= minGapMs
        if (activeIndex + 1 < lines.size) {
            val cur = lines[activeIndex]
            val next = lines[activeIndex + 1]
            val wordEnd = cur.words.lastOrNull()?.let { w ->
                if (w.endMs > w.startMs) w.endMs else w.startMs + 600L
            }
            val interludeStart = wordEnd ?: (cur.timeMs + INTERLUDE_LEAD_IN_MS)
            val interludeSpan = next.timeMs - interludeStart
            if (interludeSpan >= minGapMs && effectivePos in interludeStart until next.timeMs) {
                val p = ((effectivePos - interludeStart).toFloat() / interludeSpan.coerceAtLeast(1L).toFloat())
                    .coerceIn(0f, 1f)
                return InterludeState(isActive = true, progress = p, dotAlphas = computeDotAlphas(p))
            }
        }

        return InterludeState(isActive = false)
    }

    private fun computeDotAlphas(progress: Float): List<Float> {
        val p = progress.coerceIn(0f, 1f)
        // 三颗微光点依次充能亮起，临近开唱时三颗齐亮并轻柔淡出
        val fadeTail = if (p > 0.92f) ((1f - p) / 0.08f).coerceIn(0f, 1f) else 1f
        return List(3) { idx ->
            val segStart = idx / 3f
            val segLocal = ((p - segStart) / (1f / 3f)).coerceIn(0f, 1f)
            val raw = 0.22f + 0.78f * segLocal
            (raw * fadeTail).coerceIn(0f, 1f)
        }
    }

    /**
     * 基于距当前演唱句的距离返回景深透明度、缩放与 API 31+ 模糊半径。
     * 手指自由拖动浏览歌词时自动解除景深压暗，方便选句跳转。
     */
    fun depthStyle(distance: Int, isDragging: Boolean): LyricDepthStyle {
        val d = abs(distance)
        if (isDragging) {
            return LyricDepthStyle(
                alpha = if (d == 0) 1.0f else 0.74f,
                scale = if (d == 0) 1.02f else 1.0f,
                blurDp = 0f,
            )
        }
        return when (d) {
            0 -> LyricDepthStyle(alpha = 1.0f, scale = 1.035f, blurDp = 0f)
            1 -> LyricDepthStyle(alpha = 0.48f, scale = 0.97f, blurDp = 0f)
            2 -> LyricDepthStyle(alpha = 0.28f, scale = 0.94f, blurDp = 1.2f)
            else -> LyricDepthStyle(alpha = 0.16f, scale = 0.92f, blurDp = 2.2f)
        }
    }
}
