package com.sonify.player

import android.app.Application
import android.content.Context
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.UserPreferences
import com.sonify.player.library.MediaScanner
import com.sonify.player.usb.UsbAudioManager
import com.sonify.player.usb.UsbVolumeController

/**
 * 轻量级应用级依赖容器，统一管理跨界面/跨 Service 共享的 USB、音量护耳、数据库、设置与扫描器实例。
 */
class HiFiApp : Application() {

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

    override fun onCreate() {
        super.onCreate()
        prefs = UserPreferences(this)
        db = LibraryDatabase.get(this)
        volumeController = UsbVolumeController(prefs)
        usbManager = UsbAudioManager(this, volumeController)
        scanner = MediaScanner(this, db, prefs)
    }

    companion object {
        fun from(context: Context): HiFiApp = context.applicationContext as HiFiApp
    }
}
