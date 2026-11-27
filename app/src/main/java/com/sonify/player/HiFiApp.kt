package com.sonify.player

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sonify.player.analytics.SonifyAnalytics
import com.sonify.player.crash.SonifyCrashHandler
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.UserPreferences
import com.sonify.player.download.SonifyDownloadManager
import com.sonify.player.i18n.AppLanguage
import com.sonify.player.i18n.SonifyLocaleManager
import com.sonify.player.library.MediaScanner
import com.sonify.player.library.PlaylistRepository
import com.sonify.player.lyrics.LyricsRepository
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.SubsonicCoverFetcher
import com.sonify.player.update.AppUpdateManager
import com.sonify.player.usb.UsbAudioManager
import com.sonify.player.usb.UsbVolumeController
import com.sonify.player.wifi.WifiTransferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 轻量级应用级依赖容器，统一管理跨界面/跨 Service 共享的 USB、音量护耳、数据库、设置、扫描器、Subsonic、更新与崩溃保护。
 */
class HiFiApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var prefs: UserPreferences
        private set
    lateinit var db: LibraryDatabase
        private set
    lateinit var volumeController: UsbVolumeController
        private set
    lateinit var usbManager: UsbAudioManager
        private set
    lateinit var scanner: MediaScanner
        private set
    lateinit var subsonicRepo: SubsonicRepository
        private set
    lateinit var playlistRepo: PlaylistRepository
        private set
    lateinit var downloadManager: SonifyDownloadManager
        private set
    lateinit var wifiTransferManager: WifiTransferManager
        private set
    lateinit var lyricsRepo: LyricsRepository
        private set
    lateinit var updateManager: AppUpdateManager
        private set
    lateinit var analytics: SonifyAnalytics
        private set

    @Volatile
    private var cachedLocalizedContext: Context? = null

    @Volatile
    private var cachedLanguage: AppLanguage? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(SonifyLocaleManager.wrapContext(base))
    }

    /** 供 Service / ViewModel / Repository 读取最新语言资源的 Context */
    fun localizedContext(): Context {
        val lang = SonifyLocaleManager.readPersistedSync(this)
        val cached = cachedLocalizedContext
        if (cached != null && cachedLanguage == lang) return cached
        return SonifyLocaleManager.wrapContext(this, lang).also {
            cachedLanguage = lang
            cachedLocalizedContext = it
        }
    }

    fun onLanguageChanged(language: AppLanguage) {
        SonifyLocaleManager.writePersistedSync(this, language)
        cachedLanguage = language
        cachedLocalizedContext = SonifyLocaleManager.wrapContext(this, language)
    }

    override fun onCreate() {
        super.onCreate()
        // 独立 `:crash` 崩溃恢复进程跳过重量级初始化，确保秒启且不受主进程受损状态牵连
        if (isCrashProcess()) return

        SonifyCrashHandler.install(this)

        prefs = UserPreferences(this)
        db = LibraryDatabase.get(this)
        volumeController = UsbVolumeController(prefs)
        usbManager = UsbAudioManager(this, volumeController)
        scanner = MediaScanner(this, db, prefs)
        subsonicRepo = SubsonicRepository(this, db, prefs)
        playlistRepo = PlaylistRepository(this, db, subsonicRepo)
        downloadManager = SonifyDownloadManager(this, db, prefs, subsonicRepo)
        wifiTransferManager = WifiTransferManager(this, db, prefs, usbManager)
        lyricsRepo = LyricsRepository(this, prefs, subsonicRepo)
        analytics = SonifyAnalytics(this, prefs) { updateManager.activeBaseUrl() }
        updateManager = AppUpdateManager(
            context = this,
            prefs = prefs,
            onTrackEvent = { name, props, flushNow -> analytics.track(name, props, flushNow) },
        )
        AmbientColorExtractor.init(this, subsonicRepo)

        appScope.launch {
            subsonicRepo.preloadCredentials()
        }
    }

    private fun isCrashProcess(): Boolean {
        val procName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n') }.getOrDefault("")
        }
        return procName.endsWith(":crash")
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SubsonicCoverFetcher.Factory(this@HiFiApp, subsonicRepo))
            }
            .crossfade(true)
            .build()

    companion object {
        fun from(context: Context): HiFiApp = context.applicationContext as HiFiApp
    }
}
