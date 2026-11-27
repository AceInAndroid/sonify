package com.sonify.player.crash

import org.json.JSONObject

/**
 * 脱敏后的结构化崩溃报告（本地留存 & 可选上报至 sonify.zhangbing.tech）。
 */
data class CrashReport(
    val id: String,
    val timestampMs: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val deviceModel: String,
    val androidSdk: Int,
    val abi: String,
    val threadName: String,
    val audioRoute: String,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stackTrace: String,
    val uploaded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestampMs", timestampMs)
        put("appVersionName", appVersionName)
        put("appVersionCode", appVersionCode)
        put("deviceModel", deviceModel)
        put("androidSdk", androidSdk)
        put("abi", abi)
        put("threadName", threadName)
        put("audioRoute", audioRoute)
        put("exceptionClass", exceptionClass)
        put("exceptionMessage", exceptionMessage)
        put("stackTrace", stackTrace)
        put("uploaded", uploaded)
    }

    fun toShareableSummary(): String = buildString {
        appendLine("Sonify Crash Report ($id)")
        appendLine("Version: $appVersionName ($appVersionCode) · SDK $androidSdk · $abi")
        appendLine("Device: $deviceModel · Route: $audioRoute · Thread: $threadName")
        appendLine("Exception: $exceptionClass: $exceptionMessage")
        appendLine("--- StackTrace ---")
        append(stackTrace)
    }

    companion object {
        fun fromJson(raw: String): CrashReport? = runCatching {
            val o = JSONObject(raw)
            CrashReport(
                id = o.optString("id", ""),
                timestampMs = o.optLong("timestampMs", 0L),
                appVersionName = o.optString("appVersionName", ""),
                appVersionCode = o.optInt("appVersionCode", 0),
                deviceModel = o.optString("deviceModel", ""),
                androidSdk = o.optInt("androidSdk", 0),
                abi = o.optString("abi", ""),
                threadName = o.optString("threadName", ""),
                audioRoute = o.optString("audioRoute", ""),
                exceptionClass = o.optString("exceptionClass", "Throwable"),
                exceptionMessage = o.optString("exceptionMessage", ""),
                stackTrace = o.optString("stackTrace", ""),
                uploaded = o.optBoolean("uploaded", false),
            )
        }.getOrNull()
    }
}

data class CrashLoopDecision(
    val isLoopBroken: Boolean,
    val crashesInWindow: Int,
    val keptTimestamps: List<Long>,
)

/**
 * 纯 Kotlin 连崩熔断判定与日志隐私脱敏工具（100% JVM 可单测）：
 * - 默认 30 秒窗口内连续崩溃 >= 3 次即触发熔断，停止自动拉起防止死循环；
 * - 自动抹除本地私人文件路径、Subsonic 服务器域名与鉴权凭据参数。
 */
object CrashLoopGuard {

    const val DEFAULT_WINDOW_MS = 30_000L
    const val DEFAULT_MAX_CRASHES = 3

    private val LOCAL_PATH_REGEX = Regex("""/(?:storage/emulated/\d+|sdcard|mnt/[^/\s]+)/[^\s:)\]"']+""")
    private val SENSITIVE_QUERY_REGEX = Regex("""(?i)\b(password|pass|token|salt|t|p|u|pin)=([^&\s)\]"']+)""")
    private val SUBSONIC_URL_REGEX = Regex("""https?://[^\s/]+/rest/""")

    fun evaluateLoop(
        previousTimestampsMs: List<Long>,
        nowMs: Long,
        windowMs: Long = DEFAULT_WINDOW_MS,
        maxCrashes: Int = DEFAULT_MAX_CRASHES,
    ): CrashLoopDecision {
        val recent = (previousTimestampsMs.filter { ts -> ts in (nowMs - windowMs)..nowMs } + nowMs)
            .sorted()
            .takeLast(maxCrashes + 2)
        val count = recent.size
        return CrashLoopDecision(
            isLoopBroken = count >= maxCrashes,
            crashesInWindow = count,
            keptTimestamps = recent,
        )
    }

    /** 脱敏堆栈与异常消息，确保零隐私/零凭据外泄 */
    fun sanitizeText(raw: String, maxChars: Int = 16_000): String {
        if (raw.isEmpty()) return ""
        val cleaned = raw
            .replace(LOCAL_PATH_REGEX, "<local_path>")
            .replace(SUBSONIC_URL_REGEX, "<subsonic_host>/rest/")
            .replace(SENSITIVE_QUERY_REGEX) { mr -> "${mr.groupValues[1]}=***" }
        return if (cleaned.length > maxChars) {
            cleaned.take(maxChars) + "\n... [truncated]"
        } else cleaned
    }
}
