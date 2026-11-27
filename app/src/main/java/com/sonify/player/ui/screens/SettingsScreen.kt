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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.BuildConfig
import com.sonify.player.R
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
import com.sonify.player.data.LyricsBackdropStyle
import com.sonify.player.data.LyricsFontFamily
import com.sonify.player.data.LyricsHighlightStyle
import com.sonify.player.data.LyricsLineSpacing
import com.sonify.player.data.MinDurationFilter
import com.sonify.player.data.SampleRatePolicy
import com.sonify.player.data.StreamQuality
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.UserPreferences
import com.sonify.player.data.VolumeMode
import com.sonify.player.i18n.AppLanguage
import com.sonify.player.i18n.SonifyLocaleManager
import com.sonify.player.ui.components.ChangelogDialog
import com.sonify.player.ui.components.LyricsSettingsPreviewCard
import com.sonify.player.ui.components.SheetOption
import com.sonify.player.ui.components.SpotifyOptionSheet
import com.sonify.player.update.ProjectLinks
import com.sonify.player.ui.theme.CoverArtSupport
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.UsbVolumeController
import com.sonify.player.usb.VolumeMath
import com.sonify.player.update.ReleaseInfo
import com.sonify.player.wifi.WifiTransferState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    APP_LANGUAGE,
    COVER_STYLE,
    DARK_CANVAS,
    ACCENT_STYLE,
    IMMERSIVE_SCOPE,
    SUBSONIC_WIFI_QUALITY,
    SUBSONIC_CELLULAR_QUALITY,
    DOWNLOAD_STORAGE_MODE,
    DOWNLOAD_QUALITY,
    LYRICS_BACKDROP_STYLE,
    LYRICS_HIGHLIGHT_STYLE,
    LYRICS_FONT_FAMILY,
    LYRICS_LINE_SPACING,
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
    updateAvailableBadge: ReleaseInfo?,
    activeUpdateBaseUrl: String,
    crashReportCount: Int,
    onRebindRoute: () -> Unit,
    onScanNow: () -> Unit,
    onOpenSubsonicAccounts: () -> Unit,
    onOpenDownloadManager: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenCrashLogs: () -> Unit,
    onSimulateCrash: () -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var showDirsDialog by remember { mutableStateOf(false) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showUpdateServerDialog by remember { mutableStateOf(false) }
    var showSimulateCrashConfirm by remember { mutableStateOf(false) }
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
            text = stringResource(R.string.settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // ==================== 分组 1：音频输出与 USB ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_audio))

        SettingsSwitchItem(
            icon = Icons.Rounded.Usb,
            title = stringResource(R.string.settings_usb_exclusive_title),
            subtitle = stringResource(R.string.settings_usb_exclusive_sub),
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
            title = stringResource(R.string.settings_sample_rate),
            subtitle = stringResource(settings.sampleRatePolicy.labelRes),
            onClick = { activeSheet = ActiveSheet.SAMPLE_RATE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Memory,
            title = stringResource(R.string.settings_bit_depth),
            subtitle = stringResource(settings.bitDepthPref.labelRes),
            onClick = { activeSheet = ActiveSheet.BIT_DEPTH },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.settings_buffer_size),
            subtitle = stringResource(settings.bufferSizePref.labelRes),
            onClick = { activeSheet = ActiveSheet.BUFFER_SIZE },
        )
        SettingsSelectItem(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            title = stringResource(R.string.settings_volume_mode),
            subtitle = stringResource(settings.volumeMode.labelRes),
            onClick = { activeSheet = ActiveSheet.VOLUME_MODE },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.settings_ear_guard_title),
            subtitle = if (settings.hearingProtectionEnabled) {
                val limitDb = "%.0f".format(VolumeMath.percentToDb(settings.maxVolumeLimitPercent))
                stringResource(R.string.settings_ear_guard_on_sub, settings.maxVolumeLimitPercent, limitDb)
            } else {
                stringResource(R.string.settings_ear_guard_off_sub)
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
                title = stringResource(R.string.settings_max_vol_title),
                subtitle = stringResource(R.string.common_pct_with_db, settings.maxVolumeLimitPercent, limitDb),
                onClick = { activeSheet = ActiveSheet.MAX_VOLUME_LIMIT },
            )
        }
        SettingsSwitchItem(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.settings_safe_replug_title),
            subtitle = stringResource(R.string.settings_safe_replug_sub, settings.safeStartupPercent),
            checked = settings.safeResetOnPlugEnabled,
            onCheckedChange = { scope.launch { prefs.setSafeResetOnPlug(it) } },
        )
        val startupDb = "%.0f".format(VolumeMath.percentToDb(settings.safeStartupPercent))
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.settings_safe_startup_title),
            subtitle = stringResource(R.string.common_pct_with_db, settings.safeStartupPercent, startupDb),
            onClick = { activeSheet = ActiveSheet.SAFE_STARTUP_VOLUME },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.settings_vol_step_title),
            subtitle = stringResource(R.string.settings_vol_step_sub, settings.volumeStepPercent),
            onClick = { activeSheet = ActiveSheet.VOLUME_STEP },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.settings_feedback_title),
            subtitle = stringResource(R.string.settings_feedback_sub),
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
            title = stringResource(R.string.settings_reset_alt_title),
            subtitle = stringResource(R.string.settings_reset_alt_sub),
            checked = settings.resetAltOnRateChange,
            onCheckedChange = { scope.launch { prefs.setResetAlt(it) } },
        )

        // ==================== 分组 2：播放控制 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_playback))

        SettingsSelectItem(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.settings_audio_focus),
            subtitle = stringResource(settings.audioFocusPolicy.labelRes),
            onClick = { activeSheet = ActiveSheet.AUDIO_FOCUS_POLICY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Album,
            title = stringResource(R.string.settings_gapless_title),
            subtitle = stringResource(R.string.settings_gapless_sub),
            checked = settings.gaplessEnabled,
            onCheckedChange = { scope.launch { prefs.setGapless(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.settings_fade_title),
            subtitle = stringResource(R.string.settings_fade_sub),
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
            title = stringResource(R.string.settings_unplug_pause_title),
            subtitle = stringResource(R.string.settings_unplug_pause_sub),
            checked = settings.pauseOnUnplug,
            onCheckedChange = { scope.launch { prefs.setPauseOnUnplug(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_remember_pos_title),
            subtitle = stringResource(R.string.settings_remember_pos_sub),
            checked = settings.rememberPosition,
            onCheckedChange = { scope.launch { prefs.setRememberPosition(it) } },
        )

        // ==================== 分组 3：资料库与导入 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_library))

        SettingsSelectItem(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.settings_wifi_title),
            subtitle = if (wifiTransferState.isRunning) {
                stringResource(R.string.settings_wifi_running_sub, wifiTransferState.primaryUrl)
            } else {
                stringResource(R.string.settings_wifi_idle_sub)
            },
            onClick = onOpenWifiTransfer,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = stringResource(R.string.settings_scan_dirs),
            subtitle = settings.scanDirs.joinToString(" · "),
            onClick = { showDirsDialog = true },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = stringResource(R.string.settings_import_mode),
            subtitle = stringResource(settings.importMode.labelRes),
            onClick = { activeSheet = ActiveSheet.IMPORT_MODE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Timer,
            title = stringResource(R.string.settings_min_duration),
            subtitle = stringResource(settings.minDurationFilter.labelRes),
            onClick = { activeSheet = ActiveSheet.MIN_DURATION },
        )

        // ==================== 分组 3.5：媒体服务器（Subsonic / OpenSubsonic） ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_subsonic))

        SettingsSelectItem(
            icon = Icons.Rounded.Cloud,
            title = stringResource(R.string.settings_subsonic_accounts),
            subtitle = if (subsonicServers.isEmpty()) {
                stringResource(R.string.settings_subsonic_not_signed_in)
            } else {
                val cur = activeSubsonicServer ?: subsonicServers.first()
                stringResource(R.string.settings_subsonic_signed_in, subsonicServers.size, cur.name, cur.username)
            },
            onClick = onOpenSubsonicAccounts,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.settings_subsonic_wifi_q),
            subtitle = stringResource(settings.subsonicWifiQuality.labelRes),
            onClick = { activeSheet = ActiveSheet.SUBSONIC_WIFI_QUALITY },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.SignalCellularAlt,
            title = stringResource(R.string.settings_subsonic_cell_q),
            subtitle = stringResource(settings.subsonicCellularQuality.labelRes),
            onClick = { activeSheet = ActiveSheet.SUBSONIC_CELLULAR_QUALITY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_subsonic_scrobble_title),
            subtitle = stringResource(R.string.settings_subsonic_scrobble_sub),
            checked = settings.subsonicScrobbleEnabled,
            onCheckedChange = { scope.launch { prefs.setSubsonicScrobble(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Folder,
            title = stringResource(R.string.settings_dl_storage),
            subtitle = stringResource(settings.downloadStorageMode.labelRes),
            onClick = { activeSheet = ActiveSheet.DOWNLOAD_STORAGE_MODE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_dl_quality),
            subtitle = stringResource(settings.downloadQuality.labelRes),
            onClick = { activeSheet = ActiveSheet.DOWNLOAD_QUALITY },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Wifi,
            title = stringResource(R.string.settings_dl_wifi_only_title),
            subtitle = stringResource(R.string.settings_dl_wifi_only_sub),
            checked = settings.downloadWifiOnly,
            onCheckedChange = { scope.launch { prefs.setDownloadWifiOnly(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_dl_manager_title),
            subtitle = if (downloadedCount <= 0) stringResource(R.string.settings_dl_manager_empty) else {
                val mb = downloadedTotalBytes / (1024.0 * 1024.0)
                val sizeStr = if (mb >= 1.0) "${"%.1f".format(mb)} MB" else "${(downloadedTotalBytes / 1024L).coerceAtLeast(1L)} KB"
                stringResource(R.string.settings_dl_manager_summary, downloadedCount, sizeStr)
            },
            onClick = onOpenDownloadManager,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.settings_cover_cache_title),
            subtitle = if (coverCacheBytes <= 0L) stringResource(R.string.settings_cache_empty) else {
                val mb = coverCacheBytes / (1024.0 * 1024.0)
                if (mb >= 1.0) stringResource(R.string.settings_cover_cache_mb, mb)
                else stringResource(R.string.settings_cover_cache_kb, (coverCacheBytes / 1024L).coerceAtLeast(1L))
            },
            onClick = {
                CoverArtSupport.clearAllCoverCache(context)
                coverCacheBytes = CoverArtSupport.getCoverCacheSizeBytes(context)
            },
        )

        // ==================== 分组 4：外观、语言与黑胶风格 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_appearance))

        val resolvedSysLangLabel = if (SonifyLocaleManager.isChinese(AppLanguage.SYSTEM)) {
            AppLanguage.ZH_CN.nativeName
        } else {
            AppLanguage.EN.nativeName
        }
        SettingsSelectItem(
            icon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_language_title),
            subtitle = when (settings.appLanguage) {
                AppLanguage.SYSTEM -> "${stringResource(R.string.settings_language_follow_system)} ($resolvedSysLangLabel)"
                else -> settings.appLanguage.nativeName
            },
            onClick = { activeSheet = ActiveSheet.APP_LANGUAGE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Album,
            title = stringResource(R.string.settings_cover_style),
            subtitle = stringResource(settings.coverStyle.labelRes),
            onClick = { activeSheet = ActiveSheet.COVER_STYLE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_dark_canvas),
            subtitle = stringResource(settings.darkCanvasStyle.labelRes),
            onClick = { activeSheet = ActiveSheet.DARK_CANVAS },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.ColorLens,
            title = stringResource(R.string.settings_accent_style),
            subtitle = stringResource(settings.accentStyle.labelRes),
            onClick = { activeSheet = ActiveSheet.ACCENT_STYLE },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_ambient_bg_title),
            subtitle = stringResource(R.string.settings_ambient_bg_sub),
            checked = settings.ambientBackground,
            onCheckedChange = { scope.launch { prefs.setAmbientBackground(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Fullscreen,
            title = stringResource(R.string.settings_immersive_title),
            subtitle = stringResource(R.string.settings_immersive_sub),
            checked = settings.immersiveMusicMode,
            accentChecked = colors.accent,
            onCheckedChange = { scope.launch { prefs.setImmersiveMusicMode(it) } },
        )
        if (settings.immersiveMusicMode) {
            SettingsSelectItem(
                icon = Icons.Rounded.Fullscreen,
                title = stringResource(R.string.settings_immersive_scope),
                subtitle = stringResource(settings.immersiveScope.labelRes),
                onClick = { activeSheet = ActiveSheet.IMMERSIVE_SCOPE },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Fullscreen,
                title = stringResource(R.string.settings_hide_status_title),
                subtitle = stringResource(R.string.settings_hide_status_sub),
                checked = settings.immersiveHideStatusBar,
                onCheckedChange = { scope.launch { prefs.setImmersiveHideStatusBar(it) } },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Fullscreen,
                title = stringResource(R.string.settings_hide_nav_title),
                subtitle = stringResource(R.string.settings_hide_nav_sub),
                checked = settings.immersiveHideNavBar,
                onCheckedChange = { scope.launch { prefs.setImmersiveHideNavBar(it) } },
            )
            SettingsSwitchItem(
                icon = Icons.Rounded.Album,
                title = stringResource(R.string.settings_keep_screen_on_title),
                subtitle = stringResource(R.string.settings_keep_screen_on_sub),
                checked = settings.immersiveKeepScreenOn,
                onCheckedChange = { scope.launch { prefs.setImmersiveKeepScreenOn(it) } },
            )
        }
        // ==================== 分组 4.5：歌词与沉浸样式 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_lyrics))

        LyricsSettingsPreviewCard(
            settings = settings,
            onResetDefaults = { scope.launch { prefs.resetLyricsStyleDefaults() } },
        )

        SettingsSelectItem(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_lyrics_backdrop),
            subtitle = stringResource(settings.lyricsBackdropStyle.labelRes),
            onClick = { activeSheet = ActiveSheet.LYRICS_BACKDROP_STYLE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.settings_lyrics_highlight),
            subtitle = stringResource(settings.lyricsHighlightStyle.labelRes),
            onClick = { activeSheet = ActiveSheet.LYRICS_HIGHLIGHT_STYLE },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.FormatSize,
            title = stringResource(R.string.settings_lyrics_font),
            subtitle = "${stringResource(settings.lyricsFontFamily.labelRes)} · ${stringResource(settings.lyricsFontFamily.sampleRes)}",
            onClick = { activeSheet = ActiveSheet.LYRICS_FONT_FAMILY },
        )
        // 内联连续字号微调滑块
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatSize,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.settings_lyrics_size),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${settings.lyricsFontSizeSp} sp",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 38.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_lyrics_size_small), fontSize = 11.sp, color = colors.textMuted)
                Slider(
                    value = settings.lyricsFontSizeSp.toFloat(),
                    onValueChange = { v -> scope.launch { prefs.setLyricsFontSizeSp(v.roundToInt()) } },
                    valueRange = 16f..32f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.surface3,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(stringResource(R.string.settings_lyrics_size_large), fontSize = 13.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        }
        SettingsSelectItem(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.settings_lyrics_spacing),
            subtitle = stringResource(settings.lyricsLineSpacing.labelRes),
            onClick = { activeSheet = ActiveSheet.LYRICS_LINE_SPACING },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Lyrics,
            title = stringResource(R.string.settings_lyrics_align),
            subtitle = stringResource(settings.lyricsAlignment.labelRes),
            onClick = { activeSheet = ActiveSheet.LYRICS_ALIGNMENT },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.FormatSize,
            title = stringResource(R.string.settings_lyrics_bold_title),
            subtitle = stringResource(R.string.settings_lyrics_bold_sub),
            checked = settings.lyricsBoldActive,
            onCheckedChange = { scope.launch { prefs.setLyricsBoldActive(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Subtitles,
            title = stringResource(R.string.settings_lyrics_trans_title),
            subtitle = stringResource(R.string.settings_lyrics_trans_sub),
            checked = settings.lyricsShowTranslation,
            onCheckedChange = { scope.launch { prefs.setLyricsShowTranslation(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.VisibilityOff,
            title = stringResource(R.string.settings_lyrics_autohide_title),
            subtitle = stringResource(R.string.settings_lyrics_autohide_sub),
            checked = settings.lyricsAutoHideControls,
            accentChecked = colors.accent,
            onCheckedChange = { scope.launch { prefs.setLyricsAutoHideControls(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Lyrics,
            title = stringResource(R.string.settings_lyrics_mini_title),
            subtitle = stringResource(R.string.settings_lyrics_mini_sub),
            checked = settings.lyricsShowMiniPreview,
            onCheckedChange = { scope.launch { prefs.setLyricsShowMiniPreview(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Lyrics,
            title = stringResource(R.string.settings_lyrics_auto_online_title),
            subtitle = stringResource(R.string.settings_lyrics_auto_online_sub),
            checked = settings.lyricsAutoOnlineSearch,
            onCheckedChange = { scope.launch { prefs.setLyricsAutoOnlineSearch(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.settings_lyrics_cache_title),
            subtitle = if (lyricsCacheBytes <= 0L) stringResource(R.string.settings_cache_empty) else {
                val kb = (lyricsCacheBytes / 1024L).coerceAtLeast(1L)
                stringResource(R.string.settings_lyrics_cache_kb, kb)
            },
            onClick = {
                appRef.lyricsRepo.clearAllCache()
                lyricsCacheBytes = appRef.lyricsRepo.getCacheSizeBytes()
            },
        )

        // ==================== 分组 5：通知、锁屏与后台保活 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_background))

        SettingsSwitchItem(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.settings_lockscreen_title),
            subtitle = stringResource(R.string.settings_lockscreen_sub),
            checked = settings.lockScreenVinylEnabled,
            onCheckedChange = { scope.launch { prefs.setLockScreenVinyl(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_notif_spec_title),
            subtitle = stringResource(R.string.settings_notif_spec_sub),
            checked = settings.notifShowAudioSpec,
            onCheckedChange = { scope.launch { prefs.setNotifShowAudioSpec(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.settings_notif_perm_title),
            subtitle = stringResource(R.string.settings_notif_perm_sub),
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = stringResource(R.string.settings_battery_title),
            subtitle = stringResource(if (ignoringBattery) R.string.settings_battery_on else R.string.settings_battery_off),
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
            title = stringResource(R.string.settings_keepalive_title),
            subtitle = stringResource(R.string.settings_keepalive_sub),
            onClick = { showKeepAliveDialog = true },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = stringResource(R.string.settings_wakelock_title),
            subtitle = stringResource(R.string.settings_wakelock_sub),
            checked = settings.wakeLockEnabled,
            onCheckedChange = { scope.launch { prefs.setWakeLock(it) } },
        )

        // ==================== 分组 6：应用更新与崩溃诊断 ====================
        SettingsGroupHeader(stringResource(R.string.settings_group_update))

        SettingsSelectItem(
            icon = Icons.Rounded.NewReleases,
            title = stringResource(R.string.settings_check_update_title),
            subtitle = if (updateAvailableBadge != null) {
                stringResource(R.string.settings_check_update_new, updateAvailableBadge.versionName)
            } else {
                stringResource(R.string.settings_check_update_cur, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            },
            badgeText = updateAvailableBadge?.let { "v${it.versionName}" },
            onClick = onCheckUpdate,
        )
        SettingsSelectItem(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.settings_changelog_title),
            subtitle = stringResource(R.string.settings_changelog_sub, BuildConfig.VERSION_NAME),
            onClick = { showChangelogDialog = true },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.Code,
            title = stringResource(R.string.settings_github_title),
            subtitle = stringResource(R.string.settings_github_sub),
            onClick = { ProjectLinks.openExternalUrl(context, ProjectLinks.GITHUB_REPO_URL) },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_auto_update_title),
            subtitle = stringResource(R.string.settings_auto_update_sub),
            checked = settings.autoCheckUpdate,
            onCheckedChange = { scope.launch { prefs.setAutoCheckUpdate(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.CloudUpload,
            title = stringResource(R.string.settings_auto_crash_title),
            subtitle = stringResource(R.string.settings_auto_crash_sub),
            checked = settings.autoUploadCrashLogs,
            onCheckedChange = { scope.launch { prefs.setAutoUploadCrashLogs(it) } },
        )
        SettingsSwitchItem(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_analytics_title),
            subtitle = stringResource(R.string.settings_analytics_sub),
            checked = settings.analyticsEnabled,
            onCheckedChange = { scope.launch { prefs.setAnalyticsEnabled(it) } },
        )
        SettingsSelectItem(
            icon = Icons.Rounded.BugReport,
            title = stringResource(R.string.settings_crash_logs_title),
            subtitle = if (crashReportCount > 0) {
                stringResource(R.string.settings_crash_logs_count, crashReportCount)
            } else {
                stringResource(R.string.settings_crash_logs_empty)
            },
            onClick = onOpenCrashLogs,
        )
        if (BuildConfig.DEBUG) {
            SettingsSelectItem(
                icon = Icons.Rounded.Cloud,
                title = stringResource(R.string.settings_debug_server_title),
                subtitle = activeUpdateBaseUrl,
                onClick = { showUpdateServerDialog = true },
            )
            SettingsSelectItem(
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.settings_debug_crash_title),
                subtitle = stringResource(R.string.settings_debug_crash_sub),
                onClick = { showSimulateCrashConfirm = true },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { ProjectLinks.openExternalUrl(context, ProjectLinks.GITHUB_LICENSE_URL) }
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
                Text(
                    text = "Sonify v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                )
                Text(
                    text = "libusb 1.0.27 · UAC1/2 Native (${nativeBuildTime.ifBlank { "ready" }})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
                Text(
                    text = stringResource(R.string.settings_license_footer),
                    fontSize = 11.sp,
                    color = colors.textMuted,
                )
            }
        }
    }

    if (showChangelogDialog) {
        ChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    // ---- 单选 BottomSheet ----
    when (activeSheet) {
        ActiveSheet.SAMPLE_RATE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_sample_rate),
            options = SampleRatePolicy.entries.map { SheetOption(it, stringResource(it.labelRes)) },
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
            title = stringResource(R.string.settings_bit_depth_sheet),
            options = BitDepthPref.entries.map { SheetOption(it, stringResource(it.labelRes)) },
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
            title = stringResource(R.string.settings_buffer_size),
            options = BufferSizePref.entries.map { SheetOption(it, stringResource(it.labelRes)) },
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
            title = stringResource(R.string.settings_volume_mode),
            options = VolumeMode.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
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
            title = stringResource(R.string.settings_max_vol_sheet),
            options = listOf(20, 30, 40, 50, 60, 70, 80, 90, 100).map { pct ->
                val db = VolumeMath.percentToDb(pct)
                val note = when (pct) {
                    60 -> stringResource(R.string.settings_max_vol_opt_default, db)
                    in 90..100 -> stringResource(R.string.settings_max_vol_opt_high, db)
                    else -> stringResource(R.string.common_approx_db, db)
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
            title = stringResource(R.string.settings_safe_startup_sheet),
            options = listOf(15, 20, 25, 30, 35).map { pct ->
                val db = VolumeMath.percentToDb(pct)
                SheetOption(
                    pct,
                    if (pct == 25) stringResource(R.string.common_pct_recommended, pct) else "$pct%",
                    stringResource(R.string.common_approx_db, db),
                )
            },
            selected = settings.safeStartupPercent,
            onSelect = { pct -> scope.launch { prefs.setSafeStartupPercent(pct) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.VOLUME_STEP -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_vol_step_sheet),
            options = listOf(
                SheetOption(1, stringResource(R.string.settings_vol_step_1_label), stringResource(R.string.settings_vol_step_1_sub)),
                SheetOption(2, stringResource(R.string.settings_vol_step_2_label), stringResource(R.string.settings_vol_step_2_sub)),
                SheetOption(5, stringResource(R.string.settings_vol_step_5_label), stringResource(R.string.settings_vol_step_5_sub)),
            ),
            selected = settings.volumeStepPercent,
            onSelect = { step -> scope.launch { prefs.setVolumeStepPercent(step) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.AUDIO_FOCUS_POLICY -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_audio_focus_sheet),
            options = AudioFocusPolicy.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.audioFocusPolicy,
            onSelect = { policy -> scope.launch { prefs.setAudioFocusPolicy(policy) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.IMPORT_MODE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_import_mode),
            options = ImportMode.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.importMode,
            onSelect = { scope.launch { prefs.setImportMode(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.MIN_DURATION -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_min_duration),
            options = MinDurationFilter.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.minDurationFilter,
            onSelect = { scope.launch { prefs.setMinDurationFilter(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.APP_LANGUAGE -> {
            val sysResolved = if (SonifyLocaleManager.isChinese(AppLanguage.SYSTEM)) {
                AppLanguage.ZH_CN.nativeName
            } else {
                AppLanguage.EN.nativeName
            }
            SpotifyOptionSheet(
                title = stringResource(R.string.settings_language_sheet_title),
                options = listOf(
                    SheetOption(
                        AppLanguage.SYSTEM,
                        stringResource(R.string.settings_language_follow_system),
                        stringResource(R.string.settings_language_follow_system_sub, sysResolved),
                    ),
                    SheetOption(
                        AppLanguage.ZH_CN,
                        AppLanguage.ZH_CN.nativeName,
                        stringResource(R.string.settings_language_zh_sub),
                    ),
                    SheetOption(
                        AppLanguage.EN,
                        AppLanguage.EN.nativeName,
                        stringResource(R.string.settings_language_en_sub),
                    ),
                ),
                selected = settings.appLanguage,
                onSelect = { lang -> scope.launch { prefs.setAppLanguage(lang) } },
                onDismiss = { activeSheet = ActiveSheet.NONE },
            )
        }

        ActiveSheet.COVER_STYLE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_cover_style),
            options = CoverStyle.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.coverStyle,
            onSelect = { scope.launch { prefs.setCoverStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DARK_CANVAS -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_dark_canvas),
            options = DarkCanvasStyle.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.darkCanvasStyle,
            onSelect = { scope.launch { prefs.setDarkCanvasStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.ACCENT_STYLE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_accent_style),
            options = AccentStyle.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.accentStyle,
            onSelect = { scope.launch { prefs.setAccentStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.IMMERSIVE_SCOPE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_immersive_scope_sheet),
            options = ImmersiveScope.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.immersiveScope,
            onSelect = { scope.launch { prefs.setImmersiveScope(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.SUBSONIC_WIFI_QUALITY -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_subsonic_wifi_q),
            options = StreamQuality.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.subsonicWifiQuality,
            onSelect = { scope.launch { prefs.setSubsonicWifiQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.SUBSONIC_CELLULAR_QUALITY -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_subsonic_cell_q),
            options = StreamQuality.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.subsonicCellularQuality,
            onSelect = { scope.launch { prefs.setSubsonicCellularQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DOWNLOAD_STORAGE_MODE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_dl_storage_sheet),
            options = DownloadStorageMode.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.downloadStorageMode,
            onSelect = { scope.launch { prefs.setDownloadStorageMode(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.DOWNLOAD_QUALITY -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_dl_quality),
            options = StreamQuality.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.downloadQuality,
            onSelect = { scope.launch { prefs.setDownloadQuality(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_BACKDROP_STYLE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_lyrics_backdrop_sheet),
            options = LyricsBackdropStyle.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.lyricsBackdropStyle,
            onSelect = { scope.launch { prefs.setLyricsBackdropStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_HIGHLIGHT_STYLE -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_lyrics_highlight_sheet),
            options = LyricsHighlightStyle.entries.map { SheetOption(it, stringResource(it.labelRes), stringResource(it.subtitleRes)) },
            selected = settings.lyricsHighlightStyle,
            onSelect = { scope.launch { prefs.setLyricsHighlightStyle(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_FONT_FAMILY -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_lyrics_font),
            options = LyricsFontFamily.entries.map {
                SheetOption(it, stringResource(it.labelRes), "${stringResource(it.subtitleRes)} · ${stringResource(it.sampleRes)}")
            },
            selected = settings.lyricsFontFamily,
            onSelect = { scope.launch { prefs.setLyricsFontFamily(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_LINE_SPACING -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_lyrics_spacing_sheet),
            options = LyricsLineSpacing.entries.map {
                SheetOption(
                    it,
                    stringResource(it.labelRes),
                    stringResource(R.string.settings_lyrics_spacing_detail, it.itemGapDp, it.lineHeightMultiplier),
                )
            },
            selected = settings.lyricsLineSpacing,
            onSelect = { scope.launch { prefs.setLyricsLineSpacing(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.LYRICS_ALIGNMENT -> SpotifyOptionSheet(
            title = stringResource(R.string.settings_lyrics_align),
            options = LyricsAlignment.entries.map { SheetOption(it, stringResource(it.labelRes)) },
            selected = settings.lyricsAlignment,
            onSelect = { scope.launch { prefs.setLyricsAlignment(it) } },
            onDismiss = { activeSheet = ActiveSheet.NONE },
        )

        ActiveSheet.NONE -> Unit
    }

    // 高音量 / 关闭护耳二次警示确认弹窗
    highVolConfirm?.let { pending ->
        val (title, message) = when (pending) {
            HighVolumeConfirm.DirectPassThrough -> stringResource(R.string.settings_warn_direct_title) to
                stringResource(R.string.settings_warn_direct_msg)

            is HighVolumeConfirm.RaiseLimit -> {
                val targetDb = "%.0f".format(VolumeMath.percentToDb(pending.targetPercent))
                stringResource(R.string.settings_warn_raise_title, pending.targetPercent) to
                    stringResource(R.string.settings_warn_raise_msg, targetDb)
            }

            HighVolumeConfirm.DisableProtection -> stringResource(R.string.settings_warn_disable_title) to
                stringResource(R.string.settings_warn_disable_msg)
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
                    Text(stringResource(R.string.settings_warn_confirm), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { highVolConfirm = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textPrimary)
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
            title = { Text(stringResource(R.string.settings_keepalive_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.settings_keepalive_dialog_body),
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeepAliveDialog = false }) {
                    Text(stringResource(R.string.common_got_it), color = colors.accent)
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
            title = { Text(stringResource(R.string.settings_scan_dirs), fontWeight = FontWeight.Bold) },
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
                                        contentDescription = stringResource(R.string.settings_scan_dirs_remove),
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
                    Text(
                        stringResource(if (newPath.isBlank()) R.string.settings_scan_now else R.string.settings_add_and_scan),
                        color = colors.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirsDialog = false }) {
                    Text(stringResource(R.string.common_close), color = colors.textSecondary)
                }
            },
        )
    }

    if (BuildConfig.DEBUG && showUpdateServerDialog) {
        var urlInput by remember { mutableStateOf(settings.customUpdateBaseUrl.ifBlank { BuildConfig.UPDATE_BASE_URL }) }
        AlertDialog(
            onDismissRequest = { showUpdateServerDialog = false },
            containerColor = colors.surface1,
            title = { Text(stringResource(R.string.settings_debug_server_dialog_title), color = colors.textPrimary) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_debug_server_dialog_desc, BuildConfig.UPDATE_BASE_URL),
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = urlInput.trim().trimEnd('/')
                        scope.launch {
                            if (cleaned.isEmpty() || cleaned == BuildConfig.UPDATE_BASE_URL.trimEnd('/')) {
                                prefs.setCustomUpdateBaseUrl("")
                            } else if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                                prefs.setCustomUpdateBaseUrl(cleaned)
                            }
                        }
                        showUpdateServerDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_save), color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (settings.customUpdateBaseUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                scope.launch { prefs.setCustomUpdateBaseUrl("") }
                                showUpdateServerDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.common_reset_default), color = colors.textSecondary)
                        }
                    }
                    TextButton(onClick = { showUpdateServerDialog = false }) {
                        Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                    }
                }
            },
        )
    }

    if (BuildConfig.DEBUG && showSimulateCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showSimulateCrashConfirm = false },
            containerColor = colors.surface1,
            title = { Text(stringResource(R.string.settings_debug_crash_dialog_title), color = colors.textPrimary) },
            text = {
                Text(
                    stringResource(R.string.settings_debug_crash_dialog_msg),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSimulateCrashConfirm = false
                        onSimulateCrash()
                    }
                ) {
                    Text(stringResource(R.string.settings_debug_crash_confirm), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulateCrashConfirm = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
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
    badgeText: String? = null,
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
            tint = if (badgeText != null) colors.accent else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                )
                if (!badgeText.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
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
