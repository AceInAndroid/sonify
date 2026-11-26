package com.sonify.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.AccentStyle
import com.sonify.player.data.AppSettings
import com.sonify.player.data.AudioFocusPolicy
import com.sonify.player.data.BitDepthPref
import com.sonify.player.data.BufferSizePref
import com.sonify.player.data.CoverStyle
import com.sonify.player.data.DarkCanvasStyle
import com.sonify.player.data.DownloadStorageMode
import com.sonify.player.data.ImmersiveScope
import com.sonify.player.data.ImportMode
import com.sonify.player.data.LyricsAlignment
import com.sonify.player.data.LyricsFontSize
import com.sonify.player.data.MinDurationFilter
import com.sonify.player.data.SampleRatePolicy
import com.sonify.player.data.StreamQuality
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.UserPreferences
import com.sonify.player.data.VolumeMode
import com.sonify.player.ui.components.SheetOption
import com.sonify.player.ui.components.SpotifyOptionSheet
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.UsbVolumeController
import com.sonify.player.usb.VolumeMath
import com.sonify.player.wifi.WifiTransferState
import kotlinx.coroutines.launch

private enum class ActiveSheet {
    NONE,
    SAMPLE_RATE,
    BIT_DEPTH,
    BUFFER_SIZE,
    VOLUME_MODE,
    MAX_VOLUME_LIMIT,
    SAFE_STARTUP_VOLUME,
    VOLUME_STEP,
    AUDIO_FOCUS_POLICY,
    IMPORT_MODE,
    MIN_DURATION,
    COVER_STYLE,
    DARK_CANVAS,
    ACCENT_STYLE,
    IMMERSIVE_SCOPE,
    SUBSONIC_WIFI_QUALITY,
    SUBSONIC_CELLULAR_QUALITY,
    DOWNLOAD_STORAGE_MODE,
    DOWNLOAD_QUALITY,
    LYRICS_FONT_SIZE,
    LYRICS_ALIGNMENT,
}

private sealed interface HighVolumeConfirm {
    data object DirectPassThrough : HighVolumeConfirm
    data class RaiseLimit(val targetPercent: Int) : HighVolumeConfirm
    data object DisableProtection : HighVolumeConfirm
}

/**
 * Tab 3: 参考 Spotify Settings 的极简无框流式分组设置页。
 * 五大分组：音频输出与 USB（含耳朵音量保护） / 播放控制 / 资料库与导入 / 外观风格 / 系统与后台。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    prefs: UserPreferences,
    volumeController: UsbVolumeController,
    subsonicServers: List<SubsonicServerEntity>,
    activeSubsonicServer: SubsonicServerEntity?,
    downloadedCount: Int,
    downloadedTotalBytes: Long,
    wifiTransferState: WifiTransferState,
    nativeBuildTime: String,
    onRebindRoute: () -> Unit,
    onScanNow: () -> Unit,
    onOpenSubsonicAccounts: () -> Unit,
    onOpenDownloadManager: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var showDirsDialog by remember { mutableStateOf(false) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var highVolConfirm by remember { mutableStateOf<HighVolumeConfirm?>(null) }
    var coverCacheBytes by remember { mutableStateOf(CoverArtSupport.getCoverCacheSizeBytes(context)) }
    val appRef = remember { com.sonify.player.HiFiApp.from(context) }
    var lyricsCacheBytes by remember { mutableStateOf(appRef.lyricsRepo.getCacheSizeBytes()) }

    val pm = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringBattery = remember(settings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = bottomPadding + 28.dp)
    ) {
        Text(
            text = "设置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // ==================== 分组 1：音频输出与 USB ====================
        SettingsGroupHeader("音频输出与 USB 独占")

        SettingsSwitchItem(
            icon = Icons.Rounded.Usb,
            title = "USB 独占输出（Bit-Perfect）",
            subtitle = "绕过系统重采样，经 libusb 直送 USB DAC",
            checked = settings.usbExclusiveEnabled,
            accentChecked = colors.exclusiveGreen,
            onCheckedChange = {
                scope.launch {
                    prefs.setUsbExclusive(it)
                    onRebindRoute()
                }
            },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Speed,
            title = "采样率策略",
            subtitle = settings.sampleRatePolicy.label,
            onClick = { activeSheet = ActiveSheet.SAMPLE_RATE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Memory,
            title = "输出位深",
            subtitle = settings.bitDepthPref.label,
            onClick = { activeSheet = ActiveSheet.BIT_DEPTH },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = "环形缓冲大小",
            subtitle = settings.bufferSizePref.label,
            onClick = { activeSheet = ActiveSheet.BUFFER_SIZE },
        )
        SettingsSelectItem(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            title = "USB 音量控制模式",
            subtitle = settings.volumeMode.label,
            onClick = { activeSheet = ActiveSheet.VOLUME_MODE },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Shield,
            title = "耳朵音量保护",
            subtitle = if (settings.hearingProtectionEnabled) {
                val limitDb = "%.0f".format(VolumeMath.percentToDb(settings.maxVolumeLimitPercent))
                "已锁定最大上限 ${settings.maxVolumeLimitPercent}% ($limitDb dB)，防止误触炸耳"
            } else {
                "已关闭（可调节至 100% / 0 dBFS，请注意保护听力）"
            },
            checked = settings.hearingProtectionEnabled,
            accentChecked = colors.exclusiveGreen,
            onCheckedChange = { enable ->
                if (!enable) {
                    highVolConfirm = HighVolumeConfirm.DisableProtection
                } else {
                    scope.launch {
                        prefs.setHearingProtection(true)
                        volumeController.onProtectionPolicyChanged()
                    }
                }
            },
        )
        if (settings.hearingProtectionEnabled) {
            val limitDb = "%.0f".format(VolumeMath.percentToDb(settings.maxVolumeLimitPercent))
            SettingsSelectItem(
                icon = Icons.Rounded.Shield,
                title = "最大音量保护上限",
                subtitle = "${settings.maxVolumeLimitPercent}% (约 $limitDb dB)",
                onClick = { activeSheet = ActiveSheet.MAX_VOLUME_LIMIT },
            )
        }
        SettingsSwitchItem(
            icon = Icons.Rounded.Shield,
            title = "重新接入 DAC 回落至安全音量",
            subtitle = "重插 DAC 时若上次高于 35%，自动回落至 ${settings.safeStartupPercent}% 起步",
            checked = settings.safeResetOnPlugEnabled,
            onCheckedChange = { scope.launch { prefs.setSafeResetOnPlug(it) } },
        )
        val startupDb = "%.0f".format(VolumeMath.percentToDb(settings.safeStartupPercent))
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = "安全起步音量",
            subtitle = "${settings.safeStartupPercent}% (约 $startupDb dB)",
            onClick = { activeSheet = ActiveSheet.SAFE_STARTUP_VOLUME },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = "物理音量键步进",
            subtitle = "每按一次增减 ${settings.volumeStepPercent}%（USB 独占模式接管物理键）",
            onClick = { activeSheet = ActiveSheet.VOLUME_STEP },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.GraphicEq,
            title = "Feedback 异步时钟闭环微调",
            subtitle = "跟随 DAC 反馈端点自动对齐晶振速率，防止长播漂移",
            checked = settings.feedbackControlEnabled,
            onCheckedChange = {
                scope.launch {
                    prefs.setFeedbackControl(it)
                    onRebindRoute()
                }
            },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Tune,
            title = "切采样率前复位 Alt Setting",
            subtitle = "兼容部分 XMOS 方案 DAC（切率时先切 alt=0）",
            checked = settings.resetAltOnRateChange,
            onCheckedChange = { scope.launch { prefs.setResetAlt(it) } },
        )

        // ==================== 分组 2：播放控制 ====================
        SettingsGroupHeader("播放控制")

        SettingsSelectItem(
            icon = Icons.Rounded.GraphicEq,
            title = "系统音频焦点策略",
            subtitle = settings.audioFocusPolicy.label,
            onClick = { activeSheet = ActiveSheet.AUDIO_FOCUS_POLICY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Album,
            title = "无缝播放（Gapless）",
            subtitle = "同采样率连续曲目保持 USB 流不断开直接续流",
            checked = settings.gaplessEnabled,
            onCheckedChange = { scope.launch { prefs.setGapless(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.GraphicEq,
            title = "防爆音淡入淡出",
            subtitle = "暂停、Seek、切率时施加 5ms 平滑淡变",
            checked = settings.fadeEnabled,
            onCheckedChange = {
                scope.launch {
                    prefs.setFade(it)
                    onRebindRoute()
                }
            },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Headphones,
            title = "耳机与 USB 拔出自动暂停",
            subtitle = "检测到输出设备断开时立即暂停播放",
            checked = settings.pauseOnUnplug,
            onCheckedChange = { scope.launch { prefs.setPauseOnUnplug(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.History,
            title = "记住播放断点",
            subtitle = "重启后在首页显示「继续上次播放」卡片，不自动出声",
            checked = settings.rememberPosition,
            onCheckedChange = { scope.launch { prefs.setRememberPosition(it) } },
        )

        // ==================== 分组 3：资料库与导入 ====================
        SettingsGroupHeader("资料库与导入")

        SettingsSelectItem(
            icon = Icons.Rounded.Wifi,
            title = "WiFi 局域网传歌",
            subtitle = if (wifiTransferState.isRunning) {
                "运行中 · ${wifiTransferState.primaryUrl}（手机/PC 浏览器均可访问）"
            } else {
                "手机或电脑浏览器扫码/局域网传无损，支持防重与上传历史"
            },
            onClick = onOpenWifiTransfer,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = "扫描目录管理",
            subtitle = settings.scanDirs.joinToString(" · "),
            onClick = { showDirsDialog = true },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = "歌曲导入方式",
            subtitle = settings.importMode.label,
            onClick = { activeSheet = ActiveSheet.IMPORT_MODE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Timer,
            title = "过滤短音频",
            subtitle = settings.minDurationFilter.label,
            onClick = { activeSheet = ActiveSheet.MIN_DURATION },
        )

        // ==================== 分组 3.5：媒体服务器（Subsonic / OpenSubsonic） ====================
        SettingsGroupHeader("媒体服务器（Subsonic）")

        SettingsSelectItem(
            icon = Icons.Rounded.Cloud,
            title = "服务器与账号管理",
            subtitle = if (subsonicServers.isEmpty()) {
                "尚未登录 · 支持 Navidrome / Subsonic / Airsonic / Gonic 等"
            } else {
                val cur = activeSubsonicServer ?: subsonicServers.first()
                "已保存 ${subsonicServers.size} 个账号 · 当前：${cur.name}（${cur.username}）"
            },
            onClick = onOpenSubsonicAccounts,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Wifi,
            title = "Wi-Fi 流媒体音质",
            subtitle = settings.subsonicWifiQuality.label,
            onClick = { activeSheet = ActiveSheet.SUBSONIC_WIFI_QUALITY },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.SignalCellularAlt,
            title = "移动网络流媒体音质",
            subtitle = settings.subsonicCellularQuality.label,
            onClick = { activeSheet = ActiveSheet.SUBSONIC_CELLULAR_QUALITY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.History,
            title = "同步播放记录（Scrobble）",
            subtitle = "向 Subsonic 服务器上报正在播放与听完计数",
            checked = settings.subsonicScrobbleEnabled,
            onCheckedChange = { scope.launch { prefs.setSubsonicScrobble(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = "歌曲/歌单下载存储位置",
            subtitle = settings.downloadStorageMode.label,
            onClick = { activeSheet = ActiveSheet.DOWNLOAD_STORAGE_MODE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Download,
            title = "离线下载音质",
            subtitle = settings.downloadQuality.label,
            onClick = { activeSheet = ActiveSheet.DOWNLOAD_QUALITY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Wifi,
            title = "仅 Wi-Fi 下载歌曲与歌单",
            subtitle = "移动网络点击下载时先弹窗确认，防止意外消耗流量",
            checked = settings.downloadWifiOnly,
            onCheckedChange = { scope.launch { prefs.setDownloadWifiOnly(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Download,
            title = "离线下载文件管理",
            subtitle = if (downloadedCount <= 0) "尚未下载任何远程歌曲" else {
                val mb = downloadedTotalBytes / (1024.0 * 1024.0)
                val sizeStr = if (mb >= 1.0) "${"%.1f".format(mb)} MB" else "${(downloadedTotalBytes / 1024L).coerceAtLeast(1L)} KB"
                "已离线 $downloadedCount 首（共 $sizeStr）· 查看队列与清理"
            },
            onClick = onOpenDownloadManager,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Delete,
            title = "清除远程封面缓存",
            subtitle = if (coverCacheBytes <= 0L) "当前无缓存" else {
                val mb = coverCacheBytes / (1024.0 * 1024.0)
                if (mb >= 1.0) "已占用 ${"%.1f".format(mb)} MB · 点击清理"
                else "已占用 ${(coverCacheBytes / 1024L).coerceAtLeast(1L)} KB · 点击清理"
            },
            onClick = {
                CoverArtSupport.clearAllCoverCache(context)
                coverCacheBytes = CoverArtSupport.getCoverCacheSizeBytes(context)
            },
        )

        // ==================== 分组 4：外观风格 ====================
        SettingsGroupHeader("外观与黑胶风格")

        SettingsSelectItem(
            icon = Icons.Rounded.Album,
            title = "播放页视觉样式",
            subtitle = settings.coverStyle.label,
            onClick = { activeSheet = ActiveSheet.COVER_STYLE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Palette,
            title = "深色背景底色",
            subtitle = settings.darkCanvasStyle.label,
            onClick = { activeSheet = ActiveSheet.DARK_CANVAS },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.ColorLens,
            title = "主题强调色",
            subtitle = settings.accentStyle.label,
            onClick = { activeSheet = ActiveSheet.ACCENT_STYLE },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Palette,
            title = "封面动态氛围背景",
            subtitle = "提取专辑主色渲染播放页与 MiniPlayer 暗调渐变",
            checked = settings.ambientBackground,
            onCheckedChange = { scope.launch { prefs.setAmbientBackground(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Fullscreen,
            title = "沉浸音乐模式",
            subtitle = "隐藏状态栏与导航栏，边到边无干扰黑胶听音（边缘内滑呼出系统栏）",
            checked = settings.immersiveMusicMode,
            accentChecked = colors.accent,
            onCheckedChange = { scope.launch { prefs.setImmersiveMusicMode(it) } },
        )
        if (settings.immersiveMusicMode) {
            SettingsSelectItem(
                icon = Icons.Rounded.Fullscreen,
                title = "沉浸生效范围",
                subtitle = settings.immersiveScope.label,
                onClick = { activeSheet = ActiveSheet.IMMERSIVE_SCOPE },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Fullscreen,
                title = "隐藏顶部状态栏",
                subtitle = "隐藏时间、电量与顶部通知图标",
                checked = settings.immersiveHideStatusBar,
                onCheckedChange = { scope.launch { prefs.setImmersiveHideStatusBar(it) } },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Fullscreen,
                title = "隐藏底部导航栏",
                subtitle = "隐藏返回/主页/多任务三键或手势条",
                checked = settings.immersiveHideNavBar,
                onCheckedChange = { scope.launch { prefs.setImmersiveHideNavBar(it) } },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Album,
                title = "沉浸时保持屏幕常亮",
                subtitle = "仅播放页播放中生效，适合接 USB DAC 作桌面黑胶摆台",
                checked = settings.immersiveKeepScreenOn,
                onCheckedChange = { scope.launch { prefs.setImmersiveKeepScreenOn(it) } },
            )
        }
        SettingsSwitchItem(
            icon = Icons.Rounded.Lyrics,
            title = "播放页单行歌词预览",
            subtitle = "在黑胶唱片下方轻盈显示当前演唱句，点击可展开全屏歌词",
            checked = settings.lyricsShowMiniPreview,
            onCheckedChange = { scope.launch { prefs.setLyricsShowMiniPreview(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Lyrics,
            title = "自动在线匹配歌词（LRCLIB）",
            subtitle = "本地与 Subsonic 无歌词时自动向开源免费库匹配并缓存离线",
            checked = settings.lyricsAutoOnlineSearch,
            onCheckedChange = { scope.launch { prefs.setLyricsAutoOnlineSearch(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Lyrics,
            title = "歌词字号",
            subtitle = settings.lyricsFontSize.label,
            onClick = { activeSheet = ActiveSheet.LYRICS_FONT_SIZE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Lyrics,
            title = "歌词排版对齐",
            subtitle = settings.lyricsAlignment.label,
            onClick = { activeSheet = ActiveSheet.LYRICS_ALIGNMENT },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Delete,
            title = "清除歌词缓存",
            subtitle = if (lyricsCacheBytes <= 0L) "当前无缓存" else {
                val kb = (lyricsCacheBytes / 1024L).coerceAtLeast(1L)
                "已占用 $kb KB · 点击清空已下载与缓存的歌词"
            },
            onClick = {
                appRef.lyricsRepo.clearAllCache()
                lyricsCacheBytes = appRef.lyricsRepo.getCacheSizeBytes()
            },
        )

        // ==================== 分组 5：通知、锁屏与后台保活 ====================
        SettingsGroupHeader("通知、锁屏与后台保活")

        SettingsSwitchItem(
            icon = Icons.Rounded.Lock,
            title = "启用黑胶锁屏界面",
            subtitle = "播放时熄屏再亮屏呈现 Sonify 黑胶锁屏页，上滑即可退出",
            checked = settings.lockScreenVinylEnabled,
            onCheckedChange = { scope.launch { prefs.setLockScreenVinyl(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Notifications,
            title = "通知栏显示 HiFi 音频规格",
            subtitle = "在通知栏与原生锁屏实时显示 USB 独占采样率/位深",
            checked = settings.notifShowAudioSpec,
            onCheckedChange = { scope.launch { prefs.setNotifShowAudioSpec(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Notifications,
            title = "系统通知栏权限设置",
            subtitle = "确保锁屏与通知栏媒体控制常驻不被系统隐藏",
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "后台电池优化白名单",
            subtitle = if (ignoringBattery) "已加入白名单（息屏播放更稳定）" else "未加入，点击前往系统设置授权",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                }
            },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Info,
            title = "息屏防杀后台保活指引",
            subtitle = "查看各品牌手机后台不中断设置建议",
            onClick = { showKeepAliveDialog = true },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "播放时保持 CPU 唤醒（WakeLock）",
            subtitle = "配合音频高优先级调度，息屏持续发包零欠载，暂停自动释放",
            checked = settings.wakeLockEnabled,
            onCheckedChange = { scope.launch { prefs.setWakeLock(it) } },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Sonify v0.2.0", fontSize = 14.sp, color = colors.textPrimary)
                Text(
                    text = "libusb 1.0.27 · UAC1/2 Native (${nativeBuildTime.ifBlank { "ready" }})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
            }
        }
    }

    // ---- 单选 BottomSheet ----
    when (activeSheet) {
        ActiveSheet.SAMPLE_RATE -> SpotifyOptionSheet(
            title = "采样率策略",
            options = SampleRatePolicy.entries.map { SheetOption(it, it.label) },
            selected = settings.sampleRatePolicy,
            onSelect = {
                scope.launch {
                    prefs.setSampleRatePolicy(it)
                    onRebindRoute()
                }
            },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.BIT_DEPTH -> SpotifyOptionSheet(
            title = "输出位深偏好",
            options = BitDepthPref.entries.map { SheetOption(it, it.label) },
            selected = settings.bitDepthPref,
            onSelect = {
                scope.launch {
                    prefs.setBitDepthPref(it)
                    onRebindRoute()
                }
            },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.BUFFER_SIZE -> SpotifyOptionSheet(
            title = "环形缓冲大小",
            options = BufferSizePref.entries.map { SheetOption(it, it.label) },
            selected = settings.bufferSizePref,
            onSelect = {
                scope.launch {
                    prefs.setBufferSizePref(it)
                    onRebindRoute()
                }
            },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.VOLUME_MODE -> SpotifyOptionSheet(
            title = "USB 音量控制模式",
            options = VolumeMode.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.volumeMode,
            onSelect = { mode ->
                if (mode == VolumeMode.FIXED_DIRECT) {
                    highVolConfirm = HighVolumeConfirm.DirectPassThrough
                } else {
                    scope.launch {
                        prefs.setVolumeMode(mode)
                        volumeController.onProtectionPolicyChanged()
                    }
                }
            },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.MAX_VOLUME_LIMIT -> SpotifyOptionSheet(
            title = "设置耳朵音量保护上限",
            options = listOf(20, 30, 40, 50, 60, 70, 80, 90, 100).map { pct ->
                val db = VolumeMath.percentToDb(pct)
                val note = when (pct) {
                    60 -> "推荐默认（约 %.0f dB，耳塞舒适上限）".format(db)
                    in 90..100 -> "极高电平（约 %.0f dB，仅建议高阻大耳）".format(db)
                    else -> "约 %.0f dB".format(db)
                }
                SheetOption(pct, "$pct%", note)
            },
            selected = settings.maxVolumeLimitPercent,
            onSelect = { pct ->
                if (pct > 80) {
                    highVolConfirm = HighVolumeConfirm.RaiseLimit(pct)
                } else {
                    scope.launch {
                        prefs.setMaxVolumeLimit(pct)
                        volumeController.onProtectionPolicyChanged()
                    }
                }
            },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.SAFE_STARTUP_VOLUME -> SpotifyOptionSheet(
            title = "接入 DAC 安全起步音量",
            options = listOf(15, 20, 25, 30, 35).map { pct ->
                val db = VolumeMath.percentToDb(pct)
                SheetOption(
                    pct,
                    if (pct == 25) "$pct%（推荐）" else "$pct%",
                    "约 %.0f dB".format(db),
                )
            },
            selected = settings.safeStartupPercent,
            onSelect = { pct -> scope.launch { prefs.setSafeStartupPercent(pct) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.VOLUME_STEP -> SpotifyOptionSheet(
            title = "物理音量键单步增量",
            options = listOf(
                SheetOption(1, "1%（极精细 · 100 级）", "每格约 0.6 dB"),
                SheetOption(2, "2%（推荐 · 50 级）", "每格约 1.2 dB，细腻且顺手"),
                SheetOption(5, "5%（快速 · 20 级）", "每格约 3.0 dB"),
            ),
            selected = settings.volumeStepPercent,
            onSelect = { step -> scope.launch { prefs.setVolumeStepPercent(step) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.AUDIO_FOCUS_POLICY -> SpotifyOptionSheet(
            title = "系统音频焦点交互策略",
            options = AudioFocusPolicy.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.audioFocusPolicy,
            onSelect = { policy -> scope.launch { prefs.setAudioFocusPolicy(policy) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.IMPORT_MODE -> SpotifyOptionSheet(
            title = "歌曲导入方式",
            options = ImportMode.entries.map { SheetOption(it, it.label) },
            selected = settings.importMode,
            onSelect = { scope.launch { prefs.setImportMode(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.MIN_DURATION -> SpotifyOptionSheet(
            title = "过滤短音频",
            options = MinDurationFilter.entries.map { SheetOption(it, it.label) },
            selected = settings.minDurationFilter,
            onSelect = { scope.launch { prefs.setMinDurationFilter(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.COVER_STYLE -> SpotifyOptionSheet(
            title = "播放页视觉样式",
            options = CoverStyle.entries.map { SheetOption(it, it.label) },
            selected = settings.coverStyle,
            onSelect = { scope.launch { prefs.setCoverStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DARK_CANVAS -> SpotifyOptionSheet(
            title = "深色背景底色",
            options = DarkCanvasStyle.entries.map { SheetOption(it, it.label) },
            selected = settings.darkCanvasStyle,
            onSelect = { scope.launch { prefs.setDarkCanvasStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.ACCENT_STYLE -> SpotifyOptionSheet(
            title = "主题强调色",
            options = AccentStyle.entries.map { SheetOption(it, it.label) },
            selected = settings.accentStyle,
            onSelect = { scope.launch { prefs.setAccentStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.IMMERSIVE_SCOPE -> SpotifyOptionSheet(
            title = "沉浸音乐模式生效范围",
            options = ImmersiveScope.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.immersiveScope,
            onSelect = { scope.launch { prefs.setImmersiveScope(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.SUBSONIC_WIFI_QUALITY -> SpotifyOptionSheet(
            title = "Wi-Fi 流媒体音质",
            options = StreamQuality.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.subsonicWifiQuality,
            onSelect = { scope.launch { prefs.setSubsonicWifiQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.SUBSONIC_CELLULAR_QUALITY -> SpotifyOptionSheet(
            title = "移动网络流媒体音质",
            options = StreamQuality.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.subsonicCellularQuality,
            onSelect = { scope.launch { prefs.setSubsonicCellularQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DOWNLOAD_STORAGE_MODE -> SpotifyOptionSheet(
            title = "离线下载存储位置",
            options = DownloadStorageMode.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.downloadStorageMode,
            onSelect = { scope.launch { prefs.setDownloadStorageMode(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DOWNLOAD_QUALITY -> SpotifyOptionSheet(
            title = "离线下载音质",
            options = StreamQuality.entries.map { SheetOption(it, it.label, it.subtitle) },
            selected = settings.downloadQuality,
            onSelect = { scope.launch { prefs.setDownloadQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_FONT_SIZE -> SpotifyOptionSheet(
            title = "歌词字号",
            options = LyricsFontSize.entries.map { SheetOption(it, it.label) },
            selected = settings.lyricsFontSize,
            onSelect = { scope.launch { prefs.setLyricsFontSize(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_ALIGNMENT -> SpotifyOptionSheet(
            title = "歌词排版对齐",
            options = LyricsAlignment.entries.map { SheetOption(it, it.label) },
            selected = settings.lyricsAlignment,
            onSelect = { scope.launch { prefs.setLyricsAlignment(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.NONE -> Unit
    }

    // 高音量 / 关闭护耳二次警示确认弹窗
    highVolConfirm?.let { pending ->
        val (title, message) = when (pending) {
            HighVolumeConfirm.DirectPassThrough -> "高音量炸耳警告" to
                "开启「固定直通 100%」将以满幅 0 dBFS 直接驱动 USB DAC，音量保护将被绕过！仅限外接独立耳放/功放时使用，直推耳机/耳塞极可能损伤听力。确认继续吗？"

            is HighVolumeConfirm.RaiseLimit -> {
                val targetDb = "%.0f".format(VolumeMath.percentToDb(pending.targetPercent))
                "确认提高音量上限至 ${pending.targetPercent}%？" to
                    "超过 80%（约 $targetDb dB）在高灵敏度耳塞上可能非常响亮，建议先摘下耳机再确认。"
            }

            HighVolumeConfirm.DisableProtection -> "确认关闭耳朵音量保护？" to
                "关闭后音量可调至 100%（0 dBFS），误触物理按键或拖动滑块可能造成突然大音量。建议保持开启。"
        }
        AlertDialog(
            onDismissRequest = { highVolConfirm = null },
            containerColor = colors.surface1,
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.danger) },
            title = { Text(title, fontWeight = FontWeight.Bold, color = colors.danger) },
            text = { Text(message, fontSize = 13.sp, color = colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (pending) {
                                HighVolumeConfirm.DirectPassThrough -> {
                                    prefs.setVolumeMode(VolumeMode.FIXED_DIRECT)
                                    volumeController.onProtectionPolicyChanged()
                                }

                                is HighVolumeConfirm.RaiseLimit -> {
                                    prefs.setMaxVolumeLimit(pending.targetPercent)
                                    volumeController.onProtectionPolicyChanged()
                                }

                                HighVolumeConfirm.DisableProtection -> {
                                    prefs.setHearingProtection(false)
                                    volumeController.onProtectionPolicyChanged()
                                }
                            }
                            highVolConfirm = null
                        }
                    }
                ) {
                    Text("我已确认风险", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { highVolConfirm = null }) {
                    Text("取消", color = colors.textPrimary)
                }
            },
        )
    }

    if (showKeepAliveDialog) {
        AlertDialog(
            onDismissRequest = { showKeepAliveDialog = false },
            containerColor = colors.surface1,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("后台息屏保活建议", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "为了保证息屏连续播放 USB 独占无损音频不断流，建议完成以下 3 步：\n\n" +
                        "1. 开启本页「后台电池优化白名单」（设为无限制）。\n" +
                        "2. 在系统应用管理中允许 Sonify「后台高耗电 / 自启动」。\n" +
                        "3. 在最近任务列表中下拉或长按锁定 Sonify 卡片，防止一键清理误杀。\n\n" +
                        "（注：Sonify 已自动为 USB 音频线程启用高优先级调度与播放期 WakeLock。）",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeepAliveDialog = false }) {
                    Text("知道了", color = colors.accent)
                }
            },
        )
    }

    // 扫描目录管理弹窗
    if (showDirsDialog) {
        var newPath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDirsDialog = false },
            containerColor = colors.surface1,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("扫描目录管理", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    settings.scanDirs.forEach { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = dir,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (settings.scanDirs.size > 1) {
                                IconButton(
                                    onClick = { scope.launch { prefs.removeScanDir(dir) } },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "删除目录",
                                        tint = colors.danger,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        placeholder = { Text("/storage/emulated/0/Download", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = newPath.trim()
                        scope.launch {
                            if (p.isNotEmpty()) prefs.addScanDir(p)
                            showDirsDialog = false
                            onScanNow()
                        }
                    }
                ) {
                    Text(if (newPath.isBlank()) "立即扫描" else "添加并扫描", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirsDialog = false }) {
                    Text("关闭", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    val colors = LocalHiFiColors.current
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsSelectItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentChecked: Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHiFiColors.current
    val activeTrack = accentChecked ?: colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = activeTrack,
            ),
        )
    }
}
