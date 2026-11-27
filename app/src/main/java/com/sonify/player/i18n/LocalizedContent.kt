package com.sonify.player.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 向上解包 ContextWrapper 查找宿主 Activity（兼容区域化 Context 包装）。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
private fun syncResourcesInPlace(resources: Resources, targetLocale: Locale) {
    val cfg = Configuration(resources.configuration)
    cfg.setLocale(targetLocale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        cfg.setLocales(LocaleList(targetLocale))
    }
    cfg.setLayoutDirection(targetLocale)
    resources.updateConfiguration(cfg, resources.displayMetrics)
}

/**
 * Compose 根级语言上下文包装器：
 * - 同步刷新宿主 Activity / Application 的 `Resources`（保证 Material3 `AlertDialog`、`ModalBottomSheet`、`Popup` 等独立子窗口同样即时生效）；
 * - 通过 `ContextWrapper(baseContext)` 覆写 `resources` / `assets` 并保留宿主 Activity 链（`LocalActivityResultRegistryOwner`、`LocalOnBackPressedDispatcherOwner`）；
 * - 切换 [AppLanguage] 时 1 帧（<16ms）内实时刷新整个界面树，不重建 Activity、不断开 USB DAC、不中断播放。
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val activityResultOwner = LocalActivityResultRegistryOwner.current
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    val localizedContext: Context = remember(language, baseContext, baseConfig) {
        val targetLocale = SonifyLocaleManager.resolveLocale(language)
        Locale.setDefault(targetLocale)
        // 同步更新宿主 Activity 与 Application 资源，确保 Dialog / ModalBottomSheet 子窗口立即跟随切换
        baseContext.findActivity()?.resources?.let { syncResourcesInPlace(it, targetLocale) }
        baseContext.applicationContext?.resources?.let { syncResourcesInPlace(it, targetLocale) }

        val configCtx = SonifyLocaleManager.wrapContext(baseContext, language)
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = configCtx.resources
            override fun getAssets(): AssetManager = configCtx.assets
        }
    }

    val provided = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedContext.resources.configuration)
        if (activityResultOwner != null) {
            add(LocalActivityResultRegistryOwner provides activityResultOwner)
        }
        if (backDispatcherOwner != null) {
            add(LocalOnBackPressedDispatcherOwner provides backDispatcherOwner)
        }
    }.toTypedArray()

    CompositionLocalProvider(
        values = provided,
        content = content,
    )
}
