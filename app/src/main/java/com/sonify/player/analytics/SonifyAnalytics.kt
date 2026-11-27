package com.sonify.player.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import com.sonify.player.BuildConfig
import com.sonify.player.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Sonify 隐私优先轻量埋点客户端：
 * - 随机 UUIDv4（存于 DataStore，卸载重置），零硬件标识/零歌名路径采集；
 * - 内存 + 本地 JSONL 双缓冲（断网不丢事件，最多缓存 200 条）；
 * - 仅在用户开启「匿名日活与功能统计」时工作，静默异步上报，绝不影响播放与 UI 帧率。
 */
class SonifyAnalytics(
    private val context: Context,
    private val prefs: UserPreferences,
    private val baseUrlProvider: () -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queueFile = File(context.filesDir, "analytics_queue.jsonl")
    private val lastFlushAtMs = AtomicLong(0L)
    private val latestUsbDac = AtomicReference("")
    private var coldLaunchTracked = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun updateCurrentUsbDac(dacName: String) {
        latestUsbDac.set(dacName.trim().take(80))
    }

    fun trackAppLaunch(
        hasUsbDac: Boolean,
        localTrackCount: Int,
        subsonicAccountCount: Int,
        immersiveMode: Boolean,
    ) {
        if (!prefs.current.analyticsEnabled) return
        val now = System.currentTimeMillis()
        val isCold = !coldLaunchTracked
        val isNewDay = AnalyticsBatchPolicy.isNewActiveDay(prefs.current.lastAnalyticsActiveDay, now)
        if (!isCold && !isNewDay) return
        coldLaunchTracked = true

        val today = AnalyticsBatchPolicy.beijingDay(now)
        scope.launch {
            prefs.setLastAnalyticsActiveDay(today)
            enqueueInternal(
                name = "app_launch",
                props = mapOf(
                    "launch_type" to if (isCold) "cold" else "new_day",
                    "has_usb_dac" to hasUsbDac,
                    "local_tracks_bucket" to AnalyticsBatchPolicy.bucketTrackCount(localTrackCount),
                    "subsonic_count" to subsonicAccountCount,
                    "immersive_mode" to immersiveMode,
                ),
            )
            // 冷启后短暂等待网络就绪再首报，确保 DAU 快速入库
            if (isCold) delay(1_500L)
            flushInternal(force = true)
        }
    }

    fun track(name: String, props: Map<String, Any?> = emptyMap(), flushNow: Boolean = false) {
        if (!prefs.current.analyticsEnabled) return
        val cleanName = name.trim().lowercase()
        if (!AnalyticsBatchPolicy.isValidEventName(cleanName)) return
        scope.launch {
            enqueueInternal(cleanName, props)
            if (flushNow || AnalyticsBatchPolicy.shouldFlush(
                    queuedCount = countQueuedLocked(),
                    lastFlushMs = lastFlushAtMs.get(),
                    nowMs = System.currentTimeMillis(),
                )
            ) {
                flushInternal(force = flushNow)
            }
        }
    }

    fun flushAsync() {
        if (!prefs.current.analyticsEnabled) return
        scope.launch { flushInternal(force = true) }
    }

    private suspend fun ensureInstallId(): String {
        val existing = prefs.current.analyticsInstallId.trim()
        if (existing.length >= 8) return existing
        val generated = UUID.randomUUID().toString()
        prefs.setAnalyticsInstallId(generated)
        return generated
    }

    private suspend fun enqueueInternal(name: String, props: Map<String, Any?>) {
        val cleanProps = AnalyticsBatchPolicy.sanitizeProps(props)
        val ev = AnalyticsEvent(name = name, tsMs = System.currentTimeMillis(), props = cleanProps)
        mutex.withLock {
            runCatching {
                val lines = if (queueFile.isFile) {
                    queueFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.toMutableList()
                } else mutableListOf()
                lines.add(ev.toJson().toString())
                val trimmed = if (lines.size > AnalyticsBatchPolicy.MAX_QUEUED_EVENTS) {
                    lines.takeLast(AnalyticsBatchPolicy.MAX_QUEUED_EVENTS)
                } else lines
                queueFile.writeText(trimmed.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        }
    }

    private suspend fun countQueuedLocked(): Int = mutex.withLock {
        runCatching {
            if (!queueFile.isFile) 0
            else queueFile.readLines(Charsets.UTF_8).count { it.isNotBlank() }
        }.getOrDefault(0)
    }

    private suspend fun flushInternal(force: Boolean) {
        if (!prefs.current.analyticsEnabled) return
        val batchLines: List<String>
        val remainingLines: List<String>

        mutex.withLock {
            if (!queueFile.isFile) return
            val all = runCatching {
                queueFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
            if (all.isEmpty()) return
            if (!AnalyticsBatchPolicy.shouldFlush(all.size, lastFlushAtMs.get(), System.currentTimeMillis(), force)) {
                return
            }
            batchLines = all.take(AnalyticsBatchPolicy.MAX_EVENTS_PER_REQUEST)
            remainingLines = all.drop(batchLines.size)
        }

        val eventsArr = JSONArray()
        for (ln in batchLines) {
            AnalyticsEvent.fromJson(ln)?.let { eventsArr.put(it.toJson()) }
        }
        if (eventsArr.length() == 0) {
            writeRemaining(remainingLines)
            return
        }

        val device = AnalyticsDeviceInfo(
            installId = ensureInstallId(),
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            deviceModel = Build.MODEL.orEmpty().trim(),
            manufacturer = Build.MANUFACTURER.orEmpty().trim(),
            androidSdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "arm64-v8a" },
            usbDac = latestUsbDac.get(),
            channel = "stable",
        )
        val bodyJson = JSONObject().apply {
            put("device", device.toJson())
            put("events", eventsArr)
        }.toString()

        val endpoint = "${baseUrlProvider().trimEnd('/')}/api/v1/telemetry/events"
        val ok = runCatching {
            val req = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.onFailure {
            Log.d(TAG, "Telemetry flush deferred: ${it.message}")
        }.getOrDefault(false)

        if (ok) {
            lastFlushAtMs.set(System.currentTimeMillis())
            writeRemaining(remainingLines)
        }
    }

    private suspend fun writeRemaining(remaining: List<String>) {
        mutex.withLock {
            runCatching {
                if (remaining.isEmpty()) {
                    if (queueFile.isFile) queueFile.delete()
                } else {
                    queueFile.writeText(remaining.joinToString("\n") + "\n", Charsets.UTF_8)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SonifyAnalytics"
    }
}
