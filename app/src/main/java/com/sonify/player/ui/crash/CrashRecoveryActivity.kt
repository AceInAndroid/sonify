package com.sonify.player.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.BuildConfig
import com.sonify.player.MainActivity
import com.sonify.player.R
import com.sonify.player.crash.CrashReport
import com.sonify.player.crash.CrashReporter
import com.sonify.player.data.AccentStyle
import com.sonify.player.data.DarkCanvasStyle
import com.sonify.player.i18n.SonifyLocaleManager
import com.sonify.player.ui.theme.HiFiPlayerTheme
import com.sonify.player.ui.theme.LocalHiFiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

/**
 * 独立进程 `:crash` 崩溃恢复页：
 * - 常规崩溃默认开启 3 秒自动倒计时拉起主进程，可随时暂停查看/上报堆栈；
 * - 30 秒内连崩 >= 3 次触发连崩熔断，停止自动拉起并提供「清理缓存并重启」安全选项。
 */
class CrashRecoveryActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SonifyLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reportPath = intent.getStringExtra(EXTRA_REPORT_PATH).orEmpty()
        val isLoopBroken = intent.getBooleanExtra(EXTRA_LOOP_BROKEN, false)
        val crashCount = intent.getIntExtra(EXTRA_CRASH_COUNT, 1)
        val autoUpload = intent.getBooleanExtra(EXTRA_AUTO_UPLOAD, true)
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: BuildConfig.UPDATE_BASE_URL
        val reportFile = reportPath.takeIf { it.isNotBlank() }?.let { File(it) }
        val report = reportPath.takeIf { it.isNotBlank() }?.let { CrashReporter.readReportFile(it) }

        setContent {
            HiFiPlayerTheme(
                canvasStyle = DarkCanvasStyle.SPOTIFY_CHARCOAL,
                accentStyle = AccentStyle.VINYL_GOLD,
            ) {
                CrashRecoveryContent(
                    report = report,
                    reportFile = reportFile,
                    isLoopBroken = isLoopBroken,
                    crashCount = crashCount,
                    autoUpload = autoUpload,
                    baseUrl = baseUrl,
                    onCopyLog = { copyToClipboard(report?.toShareableSummary().orEmpty()) },
                    onClearCacheAndRestart = {
                        runCatching { cacheDir.deleteRecursively() }
                        restartMainApp(crashCount, isLoopBroken)
                    },
                    onRestartNow = { restartMainApp(crashCount, isLoopBroken) },
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Sonify Crash Report", text))
    }

    private fun restartMainApp(crashCount: Int, isLoopBroken: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_FROM_CRASH_RECOVERY, true)
            putExtra(EXTRA_CRASH_COUNT, crashCount)
            putExtra(EXTRA_LOOP_BROKEN, isLoopBroken)
        }
        startActivity(intent)
        finish()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    companion object {
        const val EXTRA_REPORT_PATH = "extra_report_path"
        const val EXTRA_LOOP_BROKEN = "extra_loop_broken"
        const val EXTRA_CRASH_COUNT = "extra_crash_count"
        const val EXTRA_AUTO_UPLOAD = "extra_auto_upload"
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_FROM_CRASH_RECOVERY = "extra_from_crash_recovery"
    }
}

@Composable
private fun CrashRecoveryContent(
    report: CrashReport?,
    reportFile: File?,
    isLoopBroken: Boolean,
    crashCount: Int,
    autoUpload: Boolean,
    baseUrl: String,
    onCopyLog: () -> Unit,
    onClearCacheAndRestart: () -> Unit,
    onRestartNow: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val scope = rememberCoroutineScope()

    var countdownSec by remember { mutableIntStateOf(if (isLoopBroken) 0 else 3) }
    var autoRestartPaused by remember { mutableStateOf(isLoopBroken) }
    var showTrace by remember { mutableStateOf(false) }
    var copiedHint by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // 自动静默上报脱敏日志（不阻塞重启）
    LaunchedEffect(report?.id, autoUpload, baseUrl) {
        if (autoUpload && report != null && !report.uploaded) {
            uploading = true
            val res = CrashReporter.uploadReport(report, reportFile, baseUrl)
            uploading = false
            uploadStatus = res.message
        }
    }

    // 3 秒自动倒计时拉起
    LaunchedEffect(autoRestartPaused) {
        if (autoRestartPaused) return@LaunchedEffect
        for (sec in countdownSec downTo 1) {
            countdownSec = sec
            delay(1000)
            if (autoRestartPaused) return@LaunchedEffect
        }
        countdownSec = 0
        onRestartNow()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (isLoopBroken) colors.danger.copy(alpha = 0.16f) else colors.accent.copy(alpha = 0.16f))
                .border(
                    width = 1.dp,
                    color = if (isLoopBroken) colors.danger.copy(alpha = 0.45f) else colors.accent.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isLoopBroken) Icons.Rounded.Warning else Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (isLoopBroken) colors.danger else colors.accent,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(if (isLoopBroken) R.string.crash_loop_title else R.string.crash_normal_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isLoopBroken) {
                stringResource(R.string.crash_loop_desc, crashCount)
            } else {
                stringResource(R.string.crash_normal_desc)
            },
            fontSize = 13.sp,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(22.dp))

        // 自动重启倒计时卡片（仅非连崩熔断态）
        if (!isLoopBroken) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (autoRestartPaused) {
                            stringResource(R.string.crash_countdown_paused)
                        } else {
                            stringResource(R.string.crash_countdown_running, countdownSec)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (autoRestartPaused) colors.textSecondary else colors.accent,
                    )
                    if (!autoRestartPaused) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surface2)
                                .clickable { autoRestartPaused = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.crash_btn_pause), fontSize = 11.sp, color = colors.textPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (autoRestartPaused) 0f else (countdownSec / 3f).coerceIn(0f, 1f) },
                    color = colors.accent,
                    trackColor = colors.surface3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 主按钮：立即重启
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent)
                .clickable { onRestartNow() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.crash_btn_restart), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onAccent)
        }

        if (isLoopBroken) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface2)
                    .clickable { onClearCacheAndRestart() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.crash_btn_clear_restart), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 次操作行：上报日志 + 复制堆栈
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                    .clickable(enabled = !uploading && report != null) {
                        autoRestartPaused = true
                        scope.launch {
                            uploading = true
                            val res = CrashReporter.uploadReport(report!!, reportFile, baseUrl)
                            uploading = false
                            uploadStatus = res.message
                        }
                    }
                    .padding(vertical = 11.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = colors.exclusiveGreen, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (uploading) R.string.crash_btn_uploading else R.string.crash_btn_upload),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                    .clickable(enabled = report != null) {
                        autoRestartPaused = true
                        onCopyLog()
                        copiedHint = true
                    }
                    .padding(vertical = 11.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (copiedHint) R.string.crash_btn_copied else R.string.crash_btn_copy),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
            }
        }

        uploadStatus?.let { st ->
            Spacer(Modifier.height(10.dp))
            val uploadedBadge = stringResource(R.string.crash_badge_uploaded)
            Text(
                text = st,
                fontSize = 12.sp,
                color = if (st.contains(uploadedBadge, ignoreCase = true)) colors.exclusiveGreen else colors.textSecondary,
            )
        }

        // 可折叠异常详情卡片
        if (report != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            autoRestartPaused = true
                            showTrace = !showTrace
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = report.exceptionClass.substringAfterLast('.'),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (report.exceptionMessage.isNotBlank()) {
                            Text(
                                text = report.exceptionMessage,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showTrace) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                }

                AnimatedVisibility(visible = showTrace) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = report.stackTrace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
