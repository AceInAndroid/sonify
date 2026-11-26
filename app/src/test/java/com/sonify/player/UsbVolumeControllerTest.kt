package com.sonify.player

import com.sonify.player.usb.VolumeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbVolumeControllerTest {

    @Test
    fun percentToDb_mapsLogScaleCorrectly() {
        assertEquals(-60.0, VolumeMath.percentToDb(0), 0.01)
        assertEquals(-45.0, VolumeMath.percentToDb(25), 0.01)
        assertEquals(-24.0, VolumeMath.percentToDb(60), 0.01)
        assertEquals(-12.0, VolumeMath.percentToDb(80), 0.01)
        assertEquals(0.0, VolumeMath.percentToDb(100), 0.01)
    }

    @Test
    fun clampToProtection_enforcesHardUpperLimit() {
        val (v1, hit1) = VolumeMath.clampToProtection(
            requestedPercent = 95,
            protectionEnabled = true,
            maxLimitPercent = 60,
        )
        assertEquals(60, v1)
        assertTrue(hit1)

        val (v2, hit2) = VolumeMath.clampToProtection(
            requestedPercent = 42,
            protectionEnabled = true,
            maxLimitPercent = 60,
        )
        assertEquals(42, v2)
        assertFalse(hit2)

        val (v3, hit3) = VolumeMath.clampToProtection(
            requestedPercent = 95,
            protectionEnabled = false,
            maxLimitPercent = 60,
        )
        assertEquals(95, v3)
        assertFalse(hit3)
    }

    @Test
    fun computeSafeStartupPercent_protectsOnNewDeviceAndReplug() {
        // 新设备强制安全起步 25%
        assertEquals(
            25,
            VolumeMath.computeSafeStartupPercent(
                savedPercent = null,
                safeResetOnPlug = true,
                safeStartupPercent = 25,
                protectionEnabled = true,
                maxLimitPercent = 60,
            )
        )

        // 重插时上次音量过高（55% > 35%）自动回落到安全起步 25%
        assertEquals(
            25,
            VolumeMath.computeSafeStartupPercent(
                savedPercent = 55,
                safeResetOnPlug = true,
                safeStartupPercent = 25,
                protectionEnabled = true,
                maxLimitPercent = 60,
            )
        )

        // 重插时关闭自动回落，仍严格受护耳上限 60% 钳位
        assertEquals(
            60,
            VolumeMath.computeSafeStartupPercent(
                savedPercent = 85,
                safeResetOnPlug = false,
                safeStartupPercent = 25,
                protectionEnabled = true,
                maxLimitPercent = 60,
            )
        )

        // 上次音量本来就轻（18%），保持记忆音量
        assertEquals(
            18,
            VolumeMath.computeSafeStartupPercent(
                savedPercent = 18,
                safeResetOnPlug = true,
                safeStartupPercent = 25,
                protectionEnabled = true,
                maxLimitPercent = 60,
            )
        )
    }
}
