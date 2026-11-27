package com.sonify.player.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.io.File
import java.util.Locale

/**
 * 轻量级多语言区域管理器（零 AppCompat 依赖，兼容 API 24–35 及独立 `:crash` 进程）：
 * - 通过 `filesDir/app_language.txt` 同步持久化语言键，使 `attachBaseContext()` 与 `:crash` 进程可无阻塞读取；
 * - 通过 `createConfigurationContext()` 动态构造区域化 Context，驱动 Compose 16ms 热切换而不重建 Activity、不断开 USB DAC；
 * - Android 13+（API 33+）自动同步系统级 `LocaleManager.applicationLocales`。
 */
object SonifyLocaleManager {

    private const val SYNC_FILE_NAME = "app_language.txt"

    /** 纯函数解析目标 Locale，便于 JVM 单元测试覆盖 */
    fun resolveLocale(language: AppLanguage, systemLocale: Locale = currentSystemLocale()): Locale =
        when (language) {
            AppLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.SYSTEM -> {
                if (systemLocale.language.equals("zh", ignoreCase = true)) {
                    Locale.SIMPLIFIED_CHINESE
                } else {
                    Locale.ENGLISH
                }
            }
        }

    fun isChinese(language: AppLanguage, systemLocale: Locale = currentSystemLocale()): Boolean =
        resolveLocale(language, systemLocale).language.equals("zh", ignoreCase = true)

    fun currentSystemLocale(): Locale {
        val sysConfig = Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sysConfig.locales.get(0) ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            sysConfig.locale ?: Locale.getDefault()
        }
    }

    /** 同步读取当前持久化语言（用于 attachBaseContext 与独立 :crash 进程） */
    fun readPersistedSync(context: Context): AppLanguage = runCatching {
        val f = File(context.filesDir, SYNC_FILE_NAME)
        if (!f.isFile) return AppLanguage.SYSTEM
        AppLanguage.fromStorageKey(f.readText(Charsets.UTF_8).trim())
    }.getOrDefault(AppLanguage.SYSTEM)

    /** 原子写入同步语言文件并同步 Android 13+ 系统应用语言 */
    fun writePersistedSync(context: Context, language: AppLanguage) {
        runCatching {
            val dir = context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val tmp = File(dir, "$SYNC_FILE_NAME.tmp")
            val target = File(dir, SYNC_FILE_NAME)
            tmp.writeText(language.storageKey, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(language.storageKey, Charsets.UTF_8)
                tmp.delete()
            }
        }
        syncSystemAppLocales(context, language)
    }

    /** 构造携带目标 Locale 的 ConfigurationContext（API 24–35 全兼容） */
    fun wrapContext(base: Context, language: AppLanguage = readPersistedSync(base)): Context {
        val targetLocale = resolveLocale(language)
        Locale.setDefault(targetLocale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        }
        config.setLayoutDirection(targetLocale)
        return base.createConfigurationContext(config)
    }

    /** Android 13+ 同步系统「应用语言」设置 */
    fun syncSystemAppLocales(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val lm = context.getSystemService(LocaleManager::class.java) ?: return
            val desired = if (language.bcp47Tag == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.bcp47Tag)
            }
            if (lm.applicationLocales != desired) {
                lm.applicationLocales = desired
            }
        }
    }
}
