package com.sonify.player.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.crash.CrashReport
import com.sonify.player.crash.CrashReporter
import com.sonify.player.ui.theme.LocalHiFiColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃诊断日志管理弹窗：查看脱敏堆栈、一键复制、手动补报至 sonify.zhangbing.tech 或清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsSheet(
    baseUrl: String,
    onShowToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var reports by remember { mutableStateOf(CrashReporter.listReports(context)) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, tint = colors.accent, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("本地崩溃诊断记录（${reports.size}）", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("已自动脱敏路径与凭据，可复制或上报分析", fontSize = 11.sp, color = colors.textSecondary)
                }
                if (reports.isNotEmpty()) {
                    TextButton(onClick = {
                        CrashReporter.clearAllReports(context)
                        reports = emptyList()
                        onShowToast("已清空本地崩溃记录")
                    }) {
                        Text("清空", color = colors.danger, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (reports.isEmpty()) {
                Text(
                    text = "暂无崩溃记录，当前运行非常稳定。",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 28.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(reports, key = { it.first.name }) { (file, report) ->
                        CrashLogItemCard(
                            report = report,
                            timeStr = fmt.format(Date(report.timestampMs)),
                            expanded = expandedId == report.id,
                            onToggleExpand = {
                                expandedId = if (expandedId == report.id) null else report.id
                            },
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Sonify Crash", report.toShareableSummary()))
                                onShowToast("已复制脱敏崩溃堆栈")
                            },
                            onUpload = {
                                scope.launch {
                                    val res = CrashReporter.uploadReport(report, file, baseUrl)
                                    onShowToast(res.message)
                                    reports = CrashReporter.listReports(context)
                                }
                            },
                            onDelete = {
                                CrashReporter.deleteReport(file)
                                reports = CrashReporter.listReports(context)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogItemCard(
    report: CrashReport,
    timeStr: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface2)
            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = report.exceptionClass.substringAfterLast('.'),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$timeStr · v${report.appVersionName} · ${report.threadName}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
            }
            if (report.uploaded) {
                Icon(Icons.Rounded.CloudDone, contentDescription = "已上报", tint = colors.exclusiveGreen, modifier = Modifier.size(16.dp))
            }
        }

        if (report.exceptionMessage.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = report.exceptionMessage,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = if (expanded) 6 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = report.stackTrace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionChip(Icons.Rounded.ContentCopy, "复制", onCopy)
                    SmallActionChip(Icons.Rounded.CloudUpload, if (report.uploaded) "重报" else "上报", onUpload)
                    SmallActionChip(Icons.Rounded.Delete, "删除", onDelete)
                }
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface3)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = colors.textPrimary)
    }
}
