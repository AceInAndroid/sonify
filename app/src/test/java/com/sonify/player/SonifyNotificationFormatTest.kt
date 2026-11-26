package com.sonify.player

import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.playback.SonifyBitmapLoader
import com.sonify.player.playback.SonifyNotificationFormat
import com.sonify.player.usb.UacStats
import org.junit.Assert.assertEquals
import org.junit.Test

class SonifyNotificationFormatTest {

    @Test
    fun formatSpecBadge_showsNegotiatedUsbSpecWhenExclusive() {
        val badge = SonifyNotificationFormat.formatSpecBadge(
            activeRoute = ActiveOutputRoute.USB_EXCLUSIVE,
            usbConnected = true,
            stats = UacStats(running = true, rate = 44100, bits = 32),
            deviceName = "MOONRIVER 3",
        )
        assertEquals("USB 独占 · 44.1k/32b", badge)

        val hiRes = SonifyNotificationFormat.formatSpecBadge(
            activeRoute = ActiveOutputRoute.USB_EXCLUSIVE,
            usbConnected = true,
            stats = UacStats(running = true, rate = 96000, bits = 24),
            deviceName = "MOONRIVER 3",
        )
        assertEquals("USB 独占 · 96k/24b", hiRes)

        val local = SonifyNotificationFormat.formatSpecBadge(
            activeRoute = ActiveOutputRoute.SYSTEM_AUDIOTRACK,
            usbConnected = false,
            stats = UacStats(),
            deviceName = "",
        )
        assertEquals("本机输出", local)
    }

    @Test
    fun formatArtistLine_respectsShowSpecToggle() {
        val withSpec = SonifyNotificationFormat.formatArtistLine(
            artist = "周杰伦",
            album = "叶惠美",
            showSpec = true,
            specBadge = "USB 独占 · 44.1k/32b",
        )
        assertEquals("周杰伦 · USB 独占 · 44.1k/32b", withSpec)

        val withoutSpec = SonifyNotificationFormat.formatArtistLine(
            artist = "周杰伦",
            album = "叶惠美",
            showSpec = false,
            specBadge = "USB 独占 · 44.1k/32b",
        )
        assertEquals("周杰伦 · 叶惠美", withoutSpec)
    }

    @Test
    fun bitmapSampleSize_scalesLargeArtworkSafely() {
        assertEquals(1, SonifyBitmapLoader.computeSampleSize(512, 512, 512))
        assertEquals(2, SonifyBitmapLoader.computeSampleSize(1200, 1200, 512))
        assertEquals(4, SonifyBitmapLoader.computeSampleSize(2400, 2400, 512))
    }
}
