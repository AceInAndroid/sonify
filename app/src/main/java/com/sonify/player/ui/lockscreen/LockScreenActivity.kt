package com.sonify.player.ui.lockscreen

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sonify.player.HiFiApp
import com.sonify.player.MainActivity
import com.sonify.player.R
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.data.CoverStyle
import com.sonify.player.i18n.ProvideAppLanguage
import com.sonify.player.i18n.SonifyLocaleManager
import com.sonify.player.playback.HiFiPlaybackService
import com.sonify.player.ui.PlayerViewModel
import com.sonify.player.ui.components.VinylDisc
import com.sonify.player.ui.components.VolumeHudOverlay
import com.sonify.player.ui.screens.CoverSquare
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.HiFiPlayerTheme
import com.sonify.player.ui.theme.ImmersiveBarRule
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.SystemBarsController
import com.sonify.player.ui.theme.SystemBarsSpec
import com.sonify.player.ui.theme.formatDuration
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sonify 专属黑胶锁屏界面（Spotify Dark × 黑胶风格）：
 * - 通过官方 setShowWhenLocked(true) 呈现，无需悬浮窗权限
 * - 绝不强行点亮熄灭的屏幕；息屏时黑胶自动停止动画零耗电
 * - 锁屏态按物理音量键同样接管为 USB 护耳音量并展示顶部 HUD
 * - 上滑或右滑立即关闭露出系统解锁；解锁后自动退出不挡路
 */
class LockScreenActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val isActiveOnScreen = mutableStateOf(false)
    private var latestBarsSpec = SystemBarsSpec()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                finish()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SonifyLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBarsController.enableEdgeToEdge(window)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED,
        )

        setContent {
            val settings by vm.settings.collectAsState()
            val barsSpec = remember(settings) {
                ImmersiveBarRule.resolve(
                    settings = settings,
                    isPlayerSurface = true,
                    isPlaying = false,
                    allowKeepScreenOn = false, // 锁屏不强行常亮避免口袋耗电
                )
            }
            LaunchedEffect(barsSpec) {
                latestBarsSpec = barsSpec
                SystemBarsController.apply(window, barsSpec)
            }

            ProvideAppLanguage(settings.appLanguage) {
                HiFiPlayerTheme(
                    canvasStyle = settings.darkCanvasStyle,
                    accentStyle = settings.accentStyle,
                ) {
                    Surface(
                        color = LocalHiFiColors.current.canvas,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LockScreenContent(
                            vm = vm,
                            barsSpec = barsSpec,
                            isScreenActive = isActiveOnScreen.value,
                            onDismissLockScreen = { finish() },
                            onUnlockAndOpenApp = ::unlockAndOpenSonify,
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            SystemBarsController.apply(window, latestBarsSpec)
        }
    }

    override fun onResume() {
        super.onResume()
        isActiveOnScreen.value = true
        // 锁屏界面展示后立即撤回 FullScreenIntent 辅助触发通知，保持通知栏整洁
        getSystemService(NotificationManager::class.java)?.cancel(HiFiPlaybackService.LOCKSCREEN_NOTIF_ID)
    }

    override fun onPause() {
        isActiveOnScreen.value = false
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

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

    private fun unlockAndOpenSonify() {
        val openApp = {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(HiFiPlaybackService.EXTRA_OPEN_NOW_PLAYING, true)
                }
            )
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        openApp()
                    }
                }
            )
        } else {
            openApp()
        }
    }
}

@Composable
private fun LockScreenContent(
    vm: PlayerViewModel,
    barsSpec: SystemBarsSpec,
    isScreenActive: Boolean,
    onDismissLockScreen: () -> Unit,
    onUnlockAndOpenApp: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val settings by vm.settings.collectAsState()
    val playback by vm.playback.collectAsState()
    val usbState by vm.usbState.collectAsState()
    val volumeState by vm.volumeState.collectAsState()
    val activeRoute by vm.activeRoute.collectAsState()

    val track = playback.currentTrack

    // 实时时间显示（仅在屏幕点亮时刷新）
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isScreenActive) {
        while (isScreenActive) {
            nowMs = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val activeLocale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    val datePattern = stringResource(R.string.lock_date_pattern)
    val timeStr = remember(nowMs, activeLocale) { SimpleDateFormat("HH:mm", activeLocale).format(Date(nowMs)) }
    val dateStr = remember(nowMs, activeLocale, datePattern) { SimpleDateFormat(datePattern, activeLocale).format(Date(nowMs)) }

    var rawAmbient by remember(track?.coverPath) { mutableStateOf(AmbientColorExtractor.DefaultAmbient) }
    LaunchedEffect(track?.coverPath, settings.ambientBackground) {
        rawAmbient = if (settings.ambientBackground && track != null) {
            AmbientColorExtractor.extract(track.coverPath)
        } else {
            colors.canvas
        }
    }
    val topColor by animateColorAsState(
        targetValue = rawAmbient,
        animationSpec = tween(500),
        label = "lock_ambient",
    )

    val isUsbExclusive = activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbState.isConnected
    val stats = usbState.stats
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var vDrag by remember { mutableFloatStateOf(0f) }
    var hDrag by remember { mutableFloatStateOf(0f) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val artworkSize = (screenWidth * 0.62f).coerceIn(200f, 260f).dp

    val topSafeMod = if (barsSpec.hideStatusBar) Modifier.displayCutoutPadding() else Modifier.statusBarsPadding()
    val bottomSafeMod = if (barsSpec.hideNavBar) Modifier.padding(bottom = 10.dp) else Modifier.navigationBarsPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to topColor,
                    0.55f to colors.canvas,
                    1.0f to colors.canvas,
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { vDrag = 0f },
                    onVerticalDrag = { _, drag -> vDrag += drag },
                    onDragEnd = {
                        if (vDrag < -140f) onDismissLockScreen()
                        vDrag = 0f
                    },
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(topSafeMod)
                .then(bottomSafeMod)
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))

            // 锁屏大号极简时间与日期
            Text(
                text = timeStr,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                color = colors.textPrimary,
                letterSpacing = (-1).sp,
            )
            Text(
                text = dateStr,
                fontSize = 13.sp,
                color = colors.textSecondary,
            )

            Spacer(Modifier.weight(0.55f))

            // 居中旋转黑胶（息屏自动停转，左右滑动切歌）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(track?.path) {
                        detectHorizontalDragGestures(
                            onDragStart = { hDrag = 0f },
                            onHorizontalDrag = { _, drag -> hDrag += drag },
                            onDragEnd = {
                                if (hDrag < -110f) vm.skipNext()
                                else if (hDrag > 110f) vm.skipPrevious()
                                hDrag = 0f
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (settings.coverStyle == CoverStyle.VINYL) {
                    VinylDisc(
                        coverPath = track?.coverPath,
                        isPlaying = playback.isPlaying && isScreenActive,
                        size = artworkSize,
                        isMini = false,
                    )
                } else {
                    CoverSquare(
                        coverPath = track?.coverPath,
                        modifier = Modifier.size(artworkSize),
                        cornerDp = 12,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // 实时 HiFi 信号链路胶囊
            if (track != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (isUsbExclusive) colors.exclusiveGreen.copy(alpha = 0.35f) else colors.hairline,
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = track.formatBadgeShort(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accent,
                    )
                    Text("→", fontSize = 11.sp, color = colors.textMuted)
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isUsbExclusive) colors.exclusiveGreen else colors.textMuted)
                    )
                    val outTxt = if (isUsbExclusive && stats.rate > 0) {
                        val sr = if (stats.rate % 1000 == 0) "${stats.rate / 1000}k"
                        else "%.1fk".format(stats.rate / 1000.0)
                        stringResource(R.string.player_usb_exclusive_spec, sr, stats.bits)
                    } else if (isUsbExclusive) {
                        stringResource(R.string.player_usb_exclusive_short)
                    } else {
                        stringResource(R.string.player_system_output)
                    }
                    Text(
                        text = outTxt,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isUsbExclusive) colors.exclusiveGreen else colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.weight(0.65f))

            // Spotify 左对齐歌名 + 收藏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "Sonify",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (track != null) "${track.artist} · ${track.album}" else stringResource(R.string.lock_waiting),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track != null) {
                    IconButton(onClick = { vm.toggleFavorite(track) }) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.player_favorite),
                            tint = if (track.isFavorite) colors.exclusiveGreen else colors.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 极简进度条
            LinearProgressIndicator(
                progress = { progress },
                color = colors.textPrimary,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(playback.positionMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                )
                Text(
                    text = formatDuration(playback.durationMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 锁屏播控 5 键
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧显示当前 USB 护耳音量或静音图标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = if (isUsbExclusive) colors.exclusiveGreen else colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isUsbExclusive) "${volumeState.volumePercent}%" else stringResource(R.string.lock_system_vol),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isUsbExclusive) colors.exclusiveGreen else colors.textMuted,
                    )
                }

                IconButton(onClick = vm::skipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.player_prev),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playback.isPlaying) R.string.common_pause else R.string.common_play),
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }

                IconButton(onClick = vm::skipNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(34.dp),
                    )
                }

                IconButton(onClick = onUnlockAndOpenApp) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = stringResource(R.string.lock_unlock),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 底部柔和上滑退出提示
            Row(
                modifier = Modifier
                    .clickable { onDismissLockScreen() }
                    .padding(bottom = 14.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.lock_swipe_up),
                    fontSize = 11.sp,
                    color = colors.textMuted,
                )
            }
        }

        // 锁屏顶层同样支持物理按键护耳音量 HUD
        VolumeHudOverlay(
            volumeState = volumeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topSafeMod),
        )
    }
}
