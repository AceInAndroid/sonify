package com.sonify.player.crash

import android.content.Context
import com.sonify.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class CrashUploadResult(
    val ok: Boolean,
    val message: String,
)

/**
 * 本地崩溃日志仓库与脱敏上报器（对接 https://sonify.zhangbing.tech/api/v1/crash/report）。
 */
object CrashReporter {

    private const val CRASH_DIR = "crash_logs"
    private const val MAX_STORED_REPORTS = 15

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun crashDir(context: Context): File =
        File(context.filesDir, CRASH_DIR).apply { mkdirs() }

    fun saveReportSync(context: Context, report: CrashReport): File {
        val dir = crashDir(context)
        val file = File(dir, "crash_${report.timestampMs}.json")
        file.writeText(report.toJson().toString(2), Charsets.UTF_8)
        pruneOldReports(dir)
        return file
    }

    fun readReportFile(path: String): CrashReport? {
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { CrashReport.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun listReports(context: Context): List<Pair<File, CrashReport>> {
        val dir = crashDir(context)
        return dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                val report = runCatching { CrashReport.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
                if (report != null) file to report else null
            } ?: emptyList()
    }

    fun clearAllReports(context: Context) {
        val dir = crashDir(context)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun deleteReport(file: File) {
        runCatching { file.delete() }
    }

    suspend fun uploadReport(
        report: CrashReport,
        reportFile: File? = null,
        baseUrl: String = BuildConfig.UPDATE_BASE_URL,
    ): CrashUploadResult = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl.trimEnd('/')}/api/v1/crash/report"
        runCatching {
            val payload = report.copy(
                exceptionMessage = CrashLoopGuard.sanitizeText(report.exceptionMessage, 1024),
                stackTrace = CrashLoopGuard.sanitizeText(report.stackTrace, 16_000),
            ).toJson().toString()
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    if (reportFile != null && reportFile.isFile) {
                        runCatching {
                            reportFile.writeText(report.copy(uploaded = true).toJson().toString(2), Charsets.UTF_8)
                        }
                    }
                    CrashUploadResult(ok = true, message = "脱敏日志已上报")
                } else {
                    CrashUploadResult(ok = false, message = "上报失败（HTTP ${resp.code}）")
                }
            }
        }.getOrElse { err ->
            CrashUploadResult(ok = false, message = "暂未连上服务器：${err.message ?: "网络不可达"}")
        }
    }

    private fun pruneOldReports(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size > MAX_STORED_REPORTS) {
            files.drop(MAX_STORED_REPORTS).forEach { runCatching { it.delete() } }
        }
    }
}
