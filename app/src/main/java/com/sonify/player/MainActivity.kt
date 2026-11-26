package com.sonify.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sonify.player.playback.HiFiPlaybackService
import com.sonify.player.ui.PlayerViewModel
import com.sonify.player.ui.components.MiniPlayerBar
import com.sonify.player.ui.components.OutputDeviceSheet
import com.sonify.player.ui.components.VolumeHudOverlay
import com.sonify.player.ui.screens.LibraryScreen
import com.sonify.player.ui.screens.NowPlayingScreen
import com.sonify.player.ui.screens.SettingsScreen
import com.sonify.player.ui.screens.UsbDeviceScreen
import com.sonify.player.ui.theme.HiFiPlayerTheme
import com.sonify.player.ui.theme.LocalHiFiColors
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    LIBRARY("音乐库", Icons.Rounded.LibraryMusic),
    USB("USB 设备", Icons.Rounded.Usb),
    SETTINGS("设置", Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val openPlayerTrigger = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(HiFiPlaybackService.EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openPlayerTrigger.value = System.currentTimeMillis()
        }
        setContent {
            val settings by vm.settings.collectAsState()
            HiFiPlayerTheme(
                canvasStyle = settings.darkCanvasStyle,
                accentStyle = settings.accentStyle,
            ) {
                Surface(
                    color = LocalHiFiColors.current.canvas,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HiFiRootScreen(vm, openPlayerTrigger.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(HiFiPlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
            openPlayerTrigger.value = System.currentTimeMillis()
        }
    }

    /**
     * 前台物理音量 +/- 键抢占：
     * USB 独占已连接且开启时，拦截系统音量键并交由 UsbVolumeController 精细护耳控音；
     * 未连接 USB DAC 或退出独占时完全放行，恢复 Android 原生系统音量。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val app = HiFiApp.from(this)
            if (app.usbManager.isConnected && app.prefs.current.usbExclusiveEnabled) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                        app.volumeController.stepUp(showHud = true)
                    } else {
                        app.volumeController.stepDown(showHud = true)
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun HiFiRootScreen(vm: PlayerViewModel, openPlayerTrigger: Long) {
    val context = LocalContext.current
    val app = remember { HiFiApp.from(context) }
    val scope = rememberCoroutineScope()
    val colors = LocalHiFiColors.current

    val settings by vm.settings.collectAsState()
    val usbState by vm.usbState.collectAsState()
    val volumeState by vm.volumeState.collectAsState()
    val scanState by vm.scanState.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val playback by vm.playback.collectAsState()
    val activeRoute by vm.activeRoute.collectAsState()
    val audioFocusState by vm.audioFocusState.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.LIBRARY) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }

    // 点击通知栏或从黑胶锁屏页解锁进入时自动展开全屏黑胶播放页
    LaunchedEffect(openPlayerTrigger) {
        if (openPlayerTrigger > 0L) {
            showNowPlaying = true
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStoragePermission(context)
        if (hasStoragePermission) vm.startScan()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch { app.scanner.importUris(uris) }
        }
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }
                .onFailure {
                    permLauncher.launch(requiredRuntimePermissions())
                }
        } else {
            permLauncher.launch(requiredRuntimePermissions())
        }
    }

    // 回到前台时刷新存储权限与 USB 设备列表
    LifecycleResumeEffect(Unit) {
        val grantedNow = checkStoragePermission(context)
        hasStoragePermission = grantedNow
        app.usbManager.refreshDevices()
        if (grantedNow && tracks.isEmpty() && !scanState.isScanning) {
            vm.startScan()
        }
        onPauseOrDispose { }
    }

    // 首次有权限且库为空时自动触发一次扫描
    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && tracks.isEmpty() && !scanState.isScanning) {
            vm.startScan()
        }
    }

    BackHandler(enabled = showNowPlaying) {
        showNowPlaying = false
    }

    val hasMiniPlayer = playback.currentTrack != null
    val bottomClearance = if (hasMiniPlayer) 132.dp else 68.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
    ) {
        when (currentTab) {
            MainTab.LIBRARY -> LibraryScreen(
                tracks = tracks,
                playback = playback,
                settings = settings,
                scanState = scanState,
                hasStoragePermission = hasStoragePermission,
                onRequestPermission = ::requestStorageAccess,
                onScan = vm::startScan,
                onCancelScan = vm::cancelScan,
                onImportFiles = { importLauncher.launch(arrayOf("audio/*")) },
                onPlayTracks = { list, idx, ctxLabel ->
                    vm.playTracks(list, idx, ctxLabel)
                },
                onResumeLast = { vm.resumeLastPlayback(tracks) },
                bottomPadding = bottomClearance,
            )

            MainTab.USB -> UsbDeviceScreen(
                usbState = usbState,
                settings = settings,
                activeRoute = activeRoute,
                onRefreshDevices = { app.usbManager.refreshDevices() },
                onConnectDevice = { dev ->
                    scope.launch { app.usbManager.connectDevice(dev) }
                },
                onDisconnectDevice = { app.usbManager.close() },
                onToggleExclusive = { enabled ->
                    scope.launch {
                        app.prefs.setUsbExclusive(enabled)
                        vm.rebindAudioRoute()
                    }
                },
                onRefreshStats = { app.usbManager.refreshStats() },
                onAppendLog = { app.usbManager.appendLog(it) },
                bottomPadding = bottomClearance,
            )

            MainTab.SETTINGS -> SettingsScreen(
                settings = settings,
                prefs = app.prefs,
                volumeController = app.volumeController,
                nativeBuildTime = usbState.stats.buildTime,
                onRebindRoute = vm::rebindAudioRoute,
                onScanNow = {
                    if (hasStoragePermission) vm.startScan() else requestStorageAccess()
                },
                bottomPadding = bottomClearance,
            )
        }

        // 底部悬浮 MiniPlayer + Spotify 风格半透明渐变 3-Tab 导航栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.32f to colors.canvas.copy(alpha = 0.94f),
                        1.0f to colors.canvas,
                    )
                )
                .navigationBarsPadding()
        ) {
            if (hasMiniPlayer) {
                MiniPlayerBar(
                    playback = playback,
                    usbState = usbState,
                    activeRoute = activeRoute,
                    ambientEnabled = settings.ambientBackground,
                    onOpenNowPlaying = { showNowPlaying = true },
                    onOpenOutputSheet = { showOutputSheet = true },
                    onTogglePlayPause = vm::togglePlayPause,
                    onSkipNext = vm::skipNext,
                    onSkipPrev = vm::skipPrevious,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val tint = if (selected) colors.textPrimary else colors.textMuted
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentTab = tab }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                            if (tab == MainTab.USB && usbState.isConnected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.exclusiveGreen)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
                        )
                    }
                }
            }
        }

        // 全屏黑胶播放页（从底部滑入）
        AnimatedVisibility(
            visible = showNowPlaying && playback.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            NowPlayingScreen(
                playback = playback,
                settings = settings,
                usbState = usbState,
                volumeState = volumeState,
                activeRoute = activeRoute,
                audioFocusState = audioFocusState,
                onCollapse = { showNowPlaying = false },
                onTogglePlayPause = vm::togglePlayPause,
                onSkipNext = vm::skipNext,
                onSkipPrev = vm::skipPrevious,
                onSeekTo = vm::seekTo,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeat = vm::cycleRepeatMode,
                onToggleFavorite = vm::toggleFavorite,
                onSelectQueueIndex = vm::selectQueueIndex,
                onOpenOutputSheet = { showOutputSheet = true },
                onUsbVolumeChange = { pct ->
                    app.volumeController.setVolumePercent(pct, showHud = false)
                },
                onToggleMute = { muted ->
                    app.volumeController.setMuted(muted, showHud = false)
                },
            )
        }

        // 顶层全局护耳音量悬浮 HUD（物理音量键触发）
        VolumeHudOverlay(
            volumeState = volumeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }

    // Spotify Connect 风格音频输出与信号链路 Sheet
    if (showOutputSheet) {
        OutputDeviceSheet(
            currentTrack = playback.currentTrack,
            settings = settings,
            usbState = usbState,
            activeRoute = activeRoute,
            onSelectUsbDevice = { dev ->
                scope.launch {
                    app.prefs.setUsbExclusive(true)
                    app.usbManager.connectDevice(dev)
                    vm.rebindAudioRoute()
                }
            },
            onSwitchToSystem = {
                scope.launch {
                    app.prefs.setUsbExclusive(false)
                    vm.rebindAudioRoute()
                    showOutputSheet = false
                }
            },
            onSwitchToUsbExclusive = {
                scope.launch {
                    app.prefs.setUsbExclusive(true)
                    vm.rebindAudioRoute()
                    showOutputSheet = false
                }
            },
            onDismiss = { showOutputSheet = false },
        )
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requiredRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
