package com.sonify.player.crash

import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.sonify.player.BuildConfig
import com.sonify.player.HiFiApp
import com.sonify.player.playback.HiFiPlaybackService
import com.sonify.player.ui.crash.CrashRecoveryActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * 全局未捕获异常拦截器（仅主进程安装）：
 * 1. 非阻塞尝试安全停流 USB DAC，避免崩溃瞬间 DAC 残留爆音；
 * 2. 将脱敏崩溃报告与时间戳写入 filesDir/crash_logs/，评估 30 秒连崩熔断；
 * 3. 启动独立进程 `:crash` 的 CrashRecoveryActivity（3 秒倒计时自动拉起或熔断安全恢复）；
 * 4. 立即终止受损主进程，确保重启后状态完全干净。
 */
class SonifyCrashHandler private constructor(
    private val app: HiFiApp,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!handling.compareAndSet(false, true)) {
            return
        }

        try {
            Log.e(TAG, "捕获未处理异常 [${thread.name}]", throwable)

            // 1) 限时尝试安全释放 USB 音频流（绝不阻塞主崩溃收尾）
            val usbStopThread = Thread({
                runCatching { app.usbManager.close() }
            }, "crash-usb-guard")
            usbStopThread.start()
            runCatching { usbStopThread.join(160L) }

            // 2) 读取并更新连崩时间戳窗口
            val nowMs = System.currentTimeMillis()
            val tsFile = File(CrashReporter.crashDir(app), "timestamps.txt")
            val prevTs = runCatching {
                if (tsFile.isFile) {
                    tsFile.readLines().mapNotNull { it.trim().toLongOrNull() }
                } else emptyList()
            }.getOrDefault(emptyList())
            val decision = CrashLoopGuard.evaluateLoop(prevTs, nowMs)
            runCatching {
                tsFile.writeText(decision.keptTimestamps.joinToString("\n"), Charsets.UTF_8)
            }

            // 3) 脱敏堆栈并落盘崩溃报告
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val sanitizedTrace = CrashLoopGuard.sanitizeText(sw.toString())
            val sanitizedMsg = CrashLoopGuard.sanitizeText(throwable.message.orEmpty(), 1024)
            val routeName = runCatching { HiFiPlaybackService.activeRouteFlow.value.name }.getOrDefault("UNKNOWN")
            val autoUpload = runCatching { app.prefs.current.autoUploadCrashLogs }.getOrDefault(true)
            val activeBaseUrl = runCatching { app.updateManager.activeBaseUrl() }
                .getOrDefault(BuildConfig.UPDATE_BASE_URL)

            val report = CrashReport(
                id = UUID.randomUUID().toString().take(8),
                timestampMs = nowMs,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                androidSdk = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                threadName = thread.name,
                audioRoute = routeName,
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = sanitizedMsg,
                stackTrace = sanitizedTrace,
                uploaded = false,
            )
            val reportFile = CrashReporter.saveReportSync(app, report)

            // 4) 拉起独立 `:crash` 进程恢复页
            val intent = Intent(app, CrashRecoveryActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(CrashRecoveryActivity.EXTRA_REPORT_PATH, reportFile.absolutePath)
                putExtra(CrashRecoveryActivity.EXTRA_LOOP_BROKEN, decision.isLoopBroken)
                putExtra(CrashRecoveryActivity.EXTRA_CRASH_COUNT, decision.crashesInWindow)
                putExtra(CrashRecoveryActivity.EXTRA_AUTO_UPLOAD, autoUpload)
                putExtra(CrashRecoveryActivity.EXTRA_BASE_URL, activeBaseUrl)
            }
            app.startActivity(intent)
            runCatching { Thread.sleep(150L) }
        } catch (inner: Throwable) {
            Log.e(TAG, "Crash 拦截器收尾异常，回退系统处理器", inner)
            previousHandler?.uncaughtException(thread, throwable)
            return
        }

        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    companion object {
        private const val TAG = "SonifyCrash"

        fun install(app: HiFiApp) {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            if (prev is SonifyCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(SonifyCrashHandler(app, prev))
        }
    }
}
