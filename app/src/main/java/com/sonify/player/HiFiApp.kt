package com.sonify.player

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.UserPreferences
import com.sonify.player.download.SonifyDownloadManager
import com.sonify.player.library.MediaScanner
import com.sonify.player.library.PlaylistRepository
import com.sonify.player.lyrics.LyricsRepository
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.ui.theme.AmbientColorExtractor
import com.sonify.player.ui.theme.SubsonicCoverFetcher
import com.sonify.player.usb.UsbAudioManager
import com.sonify.player.usb.UsbVolumeController
import com.sonify.player.wifi.WifiTransferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 轻量级应用级依赖容器，统一管理跨界面/跨 Service 共享的 USB、音量护耳、数据库、设置、扫描器与 Subsonic 仓库。
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

    override fun onCreate() {
        super.onCreate()
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
        AmbientColorExtractor.init(this, subsonicRepo)

        appScope.launch {
            subsonicRepo.preloadCredentials()
        }
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
