package com.sonify.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sonify.player.BuildConfig
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.update.ApkInstaller
import com.sonify.player.update.ReleaseInfo
import com.sonify.player.update.UpdatePolicy
import com.sonify.player.update.UpdateState
import com.sonify.player.update.isForcedUpdate
import java.io.File

/**
 * Spotify-Dark 风格应用内更新弹窗：
 * - 普通更新支持更新日志、进度与速度、SHA-256 校验、忽略版本与未知来源权限引导；
 * - 强制更新（Force Update）在 Available / Downloading / Verifying / ReadyToInstall / Error 全生命周期锁死，
 *   禁止返回键与点击外部关闭，隐藏跳过/稍后/关闭按钮，仅允许升级或退出应用。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onStartDownload: (ReleaseInfo, Boolean) -> Unit,
    onCancelDownload: () -> Unit,
    onIgnoreVersion: (Int) -> Unit,
    onRetryCheck: () -> Unit,
    onInstallTriggered: (ReleaseInfo, Boolean, Boolean) -> Unit,
    onExitApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    var needUnknownSourcePermission by remember { mutableStateOf(false) }

    val isForced = state.isForcedUpdate

    // 从系统「允许安装未知应用」设置返回后自动检测权限并恢复「立即安装」按钮
    LifecycleResumeEffect(Unit) {
        if (needUnknownSourcePermission && ApkInstaller.canRequestPackageInstalls(context)) {
            needUnknownSourcePermission = false
        }
        onPauseOrDispose { }
    }

    fun triggerInstall(release: ReleaseInfo, apkFile: File) {
        val launched = ApkInstaller.launchInstall(context, apkFile)
        onInstallTriggered(release, isForced, launched)
        if (!launched) {
            needUnknownSourcePermission = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isForced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isForced,
            dismissOnClickOutside = !isForced,
        ),
        containerColor = colors.surface1,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint, heading) = when {
                    isForced && state is UpdateState.ReadyToInstall ->
                        Triple(Icons.Rounded.InstallMobile, colors.exclusiveGreen, "重要更新校验通过")
                    isForced ->
                        Triple(Icons.Rounded.Warning, colors.danger, "重要版本升级（必须更新）")
                    state is UpdateState.UpToDate ->
                        Triple(Icons.Rounded.CheckCircle, colors.exclusiveGreen, "已是最新版本")
                    state is UpdateState.Error ->
                        Triple(Icons.Rounded.ErrorOutline, colors.danger, "更新检查异常")
                    state is UpdateState.ReadyToInstall ->
                        Triple(Icons.Rounded.InstallMobile, colors.exclusiveGreen, "安装包校验通过")
                    else ->
                        Triple(Icons.Rounded.NewReleases, colors.accent, "应用更新")
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(heading, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isForced) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.danger.copy(alpha = 0.14f))
                            .border(1.dp, colors.danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "当前版本已停止兼容或包含关键安全修复，请完成升级后继续使用 Sonify。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = colors.danger,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when (state) {
                    UpdateState.Idle, UpdateState.Checking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在连接更新服务器查询最新版本…", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Text(
                            text = "当前已安装 Sonify v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}），已是最新发行版本。",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    }

                    is UpdateState.Available -> {
                        ReleaseHeaderAndNotes(state.release)
                    }

                    is UpdateState.Downloading -> {
                        ReleaseHeaderAndNotes(state.release)
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            color = colors.accent,
                            trackColor = colors.surface3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${(state.progress * 100).toInt()}% · ${UpdatePolicy.formatProgressBytes(state.downloadedBytes)} / ${UpdatePolicy.formatSize(state.totalBytes)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = UpdatePolicy.formatSpeed(state.bytesPerSecond),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.accent,
                            )
                        }
                    }

                    is UpdateState.Verifying -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = colors.exclusiveGreen, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在校验安装包 SHA-256 完整性…", fontSize = 13.sp, color = colors.textPrimary)
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        ReleaseHeaderAndNotes(state.release)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.exclusiveGreen.copy(alpha = 0.14f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = colors.exclusiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (needUnknownSourcePermission) {
                                    "请先在系统设置中开启「允许 Sonify 安装未知应用」，返回后点击立即安装"
                                } else {
                                    "SHA-256 校验通过，点击下方立即调起系统安装器"
                                },
                                fontSize = 12.sp,
                                color = colors.exclusiveGreen,
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Text(state.message, fontSize = 13.sp, color = colors.danger)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isForced) colors.danger else colors.accent)
                            .clickable { onStartDownload(state.release, state.isForced) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = if (isForced) Color.White else colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "立即下载更新",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isForced) Color.White else colors.onAccent,
                        )
                    }
                }

                is UpdateState.ReadyToInstall -> {
                    if (needUnknownSourcePermission) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(ApkInstaller.buildUnknownSourcesIntent(context)) }
                        }) {
                            Text("去开启安装权限", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.exclusiveGreen)
                                .clickable { triggerInstall(state.release, state.apkFile) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("立即安装", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                is UpdateState.Error -> {
                    TextButton(onClick = {
                        if (state.release != null) onStartDownload(state.release, state.isForced)
                        else onRetryCheck()
                    }) {
                        Text("重试", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }

                else -> {
                    if (state !is UpdateState.Downloading && state !is UpdateState.Verifying && !isForced) {
                        TextButton(onClick = onDismiss) {
                            Text("知道了", color = colors.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available -> {
                    if (state.isForced) {
                        TextButton(onClick = onExitApp) {
                            Text("退出应用", color = colors.textMuted, fontSize = 12.sp)
                        }
                    } else {
                        Row {
                            TextButton(onClick = { onIgnoreVersion(state.release.versionCode) }) {
                                Text("跳过此版", color = colors.textMuted, fontSize = 12.sp)
                            }
                            TextButton(onClick = onDismiss) {
                                Text("稍后", color = colors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                is UpdateState.Downloading -> {
                    TextButton(onClick = onCancelDownload) {
                        Text("取消下载", color = colors.danger)
                    }
                }

                is UpdateState.ReadyToInstall, is UpdateState.Error -> {
                    if (isForced) {
                        TextButton(onClick = onExitApp) {
                            Text("退出应用", color = colors.textMuted, fontSize = 12.sp)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = colors.textSecondary)
                        }
                    }
                }

                else -> Unit
            }
        },
    )
}

@Composable
private fun ReleaseHeaderAndNotes(release: ReleaseInfo) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "v${BuildConfig.VERSION_NAME} → v${release.versionName}",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accent.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            text = UpdatePolicy.formatSize(release.fileSize),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.textMuted,
        )
    }

    if (release.releaseNotes.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.canvas.copy(alpha = 0.65f))
                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                text = release.releaseNotes,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textPrimary,
            )
        }
    }
}
