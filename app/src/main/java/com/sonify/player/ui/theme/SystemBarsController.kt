package com.sonify.player.ui.theme

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sonify.player.data.AppSettings
import com.sonify.player.data.ImmersiveScope

data class SystemBarsSpec(
    val hideStatusBar: Boolean = false,
    val hideNavBar: Boolean = false,
    val keepScreenOn: Boolean = false,
)

/**
 * 纯函数沉浸音乐模式规则引擎（无 Android 依赖，100% JVM 单元测试覆盖）。
 */
object ImmersiveBarRule {

    fun resolve(
        settings: AppSettings,
        isPlayerSurface: Boolean,
        isPlaying: Boolean,
        allowKeepScreenOn: Boolean = true,
    ): SystemBarsSpec {
        if (!settings.immersiveMusicMode) {
            return SystemBarsSpec(hideStatusBar = false, hideNavBar = false, keepScreenOn = false)
        }
        val activeHere = when (settings.immersiveScope) {
            ImmersiveScope.PLAYER_AND_LOCKSCREEN -> isPlayerSurface
            ImmersiveScope.GLOBAL -> true
        }
        if (!activeHere) {
            return SystemBarsSpec(hideStatusBar = false, hideNavBar = false, keepScreenOn = false)
        }
        return SystemBarsSpec(
            hideStatusBar = settings.immersiveHideStatusBar,
            hideNavBar = settings.immersiveHideNavBar,
            keepScreenOn = allowKeepScreenOn &&
                settings.immersiveKeepScreenOn &&
                isPlayerSurface &&
                isPlaying,
        )
    }
}

/**
 * 统一系统状态栏与导航栏控制器（兼容 Android 7–15）：
 * - 开启真正的 Edge-to-Edge 透明穿透与刘海屏短边延伸（SHORT_EDGES），消除上下黑框；
 * - 使用 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 保证隐藏/边缘临时呼出零布局抖动；
 * - 独立控制状态栏、底部导航栏与桌面黑胶摆台屏幕常亮。
 */
object SystemBarsController {

    fun enableEdgeToEdge(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun apply(window: Window, spec: SystemBarsSpec) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        if (spec.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }

        if (spec.hideNavBar) {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }

        if (spec.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
