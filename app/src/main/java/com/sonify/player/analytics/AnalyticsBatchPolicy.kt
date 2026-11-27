package com.sonify.player.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 纯 Kotlin 埋点隐私过滤、批量节流与跨日心跳判定工具（100% JVM 可单测）：
 * - 自动剥离可能泄露个人隐私的字段键（路径、URL、密码、搜索词、歌名、设备硬件号等）；
 * - 强制按北京时间（UTC+8）切分自然日，确保常驻后台次日切回前台时准确触发 DAU 心跳。
 */
object AnalyticsBatchPolicy {

    const val BATCH_FLUSH_COUNT = 10
    const val BATCH_FLUSH_INTERVAL_MS = 60_000L
    const val MAX_QUEUED_EVENTS = 200
    const val MAX_EVENTS_PER_REQUEST = 50

    private val SAFE_EVENT_REGEX = Regex("^[a-z][a-z0-9_]{1,47}$")

    private val FORBIDDEN_KEYS = setOf(
        "path", "filepath", "file_path", "url", "uri", "host", "server_url",
        "password", "pass", "token", "salt", "pin", "secret", "cookie",
        "query", "keyword", "search_text", "title", "track_title", "filename",
        "imei", "oaid", "android_id", "mac", "phone", "email", "username",
    )

    fun isValidEventName(name: String): Boolean = SAFE_EVENT_REGEX.matches(name.trim())

    /** 清洗事件属性：丢弃敏感键、含路径/URL 的值与超长文本 */
    fun sanitizeProps(raw: Map<String, Any?>): Map<String, Any> {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in raw) {
            if (out.size >= 20) break
            val key = k.trim().lowercase(Locale.US)
            if (key.isEmpty() || key.length > 40 || key in FORBIDDEN_KEYS) continue
            when (v) {
                is Boolean, is Int, is Long, is Float, is Double -> out[key] = v
                is String -> {
                    val s = v.trim().take(120)
                    if (s.isEmpty()) continue
                    if (s.contains("/storage/") || s.contains("/sdcard/") ||
                        s.contains("http://") || s.contains("https://")
                    ) continue
                    out[key] = s
                }
            }
        }
        return out
    }

    /** 将本地曲目数量分桶，避免暴露精确私人曲库元数据 */
    fun bucketTrackCount(count: Int): String = when {
        count <= 0 -> "0"
        count <= 20 -> "1_20"
        count <= 100 -> "21_100"
        count <= 500 -> "101_500"
        else -> "500_plus"
    }

    /** 按北京时间（UTC+8）计算自然日字符串 yyyy-MM-dd */
    fun beijingDay(nowMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT+08:00")
        return fmt.format(Date(nowMs))
    }

    fun isNewActiveDay(lastActiveDay: String, nowMs: Long): Boolean {
        val today = beijingDay(nowMs)
        return lastActiveDay.trim() != today
    }

    fun shouldFlush(
        queuedCount: Int,
        lastFlushMs: Long,
        nowMs: Long,
        forceImmediate: Boolean = false,
    ): Boolean {
        if (queuedCount <= 0) return false
        if (forceImmediate) return true
        if (queuedCount >= BATCH_FLUSH_COUNT) return true
        if (lastFlushMs <= 0L) return true
        return (nowMs - lastFlushMs) >= BATCH_FLUSH_INTERVAL_MS
    }
}
