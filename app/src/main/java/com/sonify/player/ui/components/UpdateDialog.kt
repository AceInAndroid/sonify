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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sonify.player.update.ProjectLinks
import com.sonify.player.update.ReleaseInfo
import com.sonify.player.update.ReleaseNotesCatalog
import com.sonify.player.update.UpdatePolicy
import com.sonify.player.update.UpdateState
import com.sonify.player.update.isForcedUpdate
import java.io.File

/**
 * Spotify-Dark 风格应用内更新弹窗：
 * - 清晰展示本次更新内容、发布日期、安装包体积、内置历史更新日志与 GitHub 项目/发布页直达链接；
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
    var showChangelogDialog by remember { mutableStateOf(false) }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.exclusiveGreen,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.exclusiveGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                            val pubDate = state.latestRelease?.publishedAt
                                ?.let { ReleaseNotesCatalog.formatPublishedDate(it) }
                                .orEmpty()
                            if (pubDate.isNotEmpty()) {
                                Text(
                                    text = pubDate,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "当前版本更新内容",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        ReleaseNotesCard(
                            notes = ReleaseNotesCatalog.formatCurrentNotes(
                                serverRelease = state.latestRelease,
                                currentVersionName = BuildConfig.VERSION_NAME,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        ReleaseQuickLinksRow(
                            changelogUrl = state.latestRelease?.changelogUrl.orEmpty(),
                            repoUrl = state.latestRelease?.repoUrl.orEmpty(),
                            onOpenLocalChangelog = { showChangelogDialog = true },
                        )
                    }

                    is UpdateState.Available -> {
                        ReleaseHeaderAndNotes(
                            release = state.release,
                            onOpenLocalChangelog = { showChangelogDialog = true },
                        )
                    }

                    is UpdateState.Downloading -> {
                        ReleaseHeaderAndNotes(
                            release = state.release,
                            showLinks = false,
                            onOpenLocalChangelog = { showChangelogDialog = true },
                        )
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
                        ReleaseHeaderAndNotes(
                            release = state.release,
                            showLinks = false,
                            onOpenLocalChangelog = { showChangelogDialog = true },
                        )
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
                        Spacer(Modifier.height(10.dp))
                        ReleaseQuickLinksRow(
                            changelogUrl = state.release?.changelogUrl.orEmpty(),
                            repoUrl = state.release?.repoUrl.orEmpty(),
                            onOpenLocalChangelog = { showChangelogDialog = true },
                        )
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

    if (showChangelogDialog) {
        ChangelogDialog(onDismiss = { showChangelogDialog = false })
    }
}

/**
 * 应用内完整版本更新日志弹窗（支持离线浏览 + 一键跳转 GitHub Releases / 仓库主页）。
 */
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = colors.accent, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text("版本更新日志", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReleaseNotesCatalog.entries.forEachIndexed { idx, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.canvas.copy(alpha = 0.65f))
                            .border(
                                width = 1.dp,
                                color = if (idx == 0) colors.accent.copy(alpha = 0.45f) else colors.hairline,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "v${entry.versionName}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) colors.accent else colors.textPrimary,
                                )
                                if (entry.versionCode == BuildConfig.VERSION_CODE) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "当前",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.exclusiveGreen)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Text(
                                text = entry.releaseDate,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textMuted,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.summary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        entry.highlights.forEach { bullet ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text("• ", fontSize = 12.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                                Text(
                                    text = bullet,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "开源协议：Sonify Non-Commercial v1.0（仅限个人非商用，禁止任何未经授权的商业用途）",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textMuted,
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent.copy(alpha = 0.16f))
                    .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .clickable { ProjectLinks.openExternalUrl(context, ProjectLinks.GITHUB_RELEASES_URL) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("GitHub 发布页", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun ReleaseHeaderAndNotes(
    release: ReleaseInfo,
    showLinks: Boolean = true,
    onOpenLocalChangelog: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val pubDate = ReleaseNotesCatalog.formatPublishedDate(release.publishedAt)

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
            maxLines = 1,
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
            maxLines = 1,
        )
    }

    val notes = release.releaseNotes.trim().ifBlank { "包含体验优化与稳定性修复。" }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "本次更新内容",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMuted,
        )
        if (pubDate.isNotEmpty()) {
            Text(
                text = "发布于 $pubDate",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textMuted,
            )
        }
    }
    Spacer(Modifier.height(5.dp))
    ReleaseNotesCard(notes = notes)

    if (showLinks) {
        Spacer(Modifier.height(10.dp))
        ReleaseQuickLinksRow(
            changelogUrl = release.changelogUrl,
            repoUrl = release.repoUrl,
            onOpenLocalChangelog = onOpenLocalChangelog,
        )
    }
}

@Composable
private fun ReleaseNotesCard(notes: String) {
    val colors = LocalHiFiColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 176.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.canvas.copy(alpha = 0.65f))
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text = notes,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun ReleaseQuickLinksRow(
    changelogUrl: String,
    repoUrl: String,
    onOpenLocalChangelog: () -> Unit,
) {
    val context = LocalContext.current
    val resolvedRepo = repoUrl.ifBlank { ProjectLinks.GITHUB_REPO_URL }
    val resolvedReleases = changelogUrl.ifBlank { ProjectLinks.GITHUB_RELEASES_URL }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactLinkChip(
            icon = Icons.Rounded.History,
            label = "更新日志",
            onClick = onOpenLocalChangelog,
        )
        CompactLinkChip(
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            label = "发布页",
            onClick = { ProjectLinks.openExternalUrl(context, resolvedReleases) },
        )
        CompactLinkChip(
            icon = Icons.Rounded.Code,
            label = "GitHub",
            onClick = { ProjectLinks.openExternalUrl(context, resolvedRepo) },
        )
    }
}

@Composable
private fun CompactLinkChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surface2)
            .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
    }
}
