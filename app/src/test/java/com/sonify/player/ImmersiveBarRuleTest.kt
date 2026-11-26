package com.sonify.player

import com.sonify.player.data.AppSettings
import com.sonify.player.data.ImmersiveScope
import com.sonify.player.ui.theme.ImmersiveBarRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveBarRuleTest {

    @Test
    fun disabledImmersive_showsBothBarsAndNoKeepScreenOn() {
        val s = AppSettings(
            immersiveMusicMode = false,
            immersiveHideStatusBar = true,
            immersiveHideNavBar = true,
            immersiveKeepScreenOn = true,
        )
        val spec = ImmersiveBarRule.resolve(s, isPlayerSurface = true, isPlaying = true)
        assertFalse(spec.hideStatusBar)
        assertFalse(spec.hideNavBar)
        assertFalse(spec.keepScreenOn)
    }

    @Test
    fun playerOnlyScope_hidesInNowPlayingAndRestoresInLibrary() {
        val s = AppSettings(
            immersiveMusicMode = true,
            immersiveScope = ImmersiveScope.PLAYER_AND_LOCKSCREEN,
            immersiveHideStatusBar = true,
            immersiveHideNavBar = true,
            immersiveKeepScreenOn = true,
        )

        val inLibrary = ImmersiveBarRule.resolve(s, isPlayerSurface = false, isPlaying = true)
        assertFalse(inLibrary.hideStatusBar)
        assertFalse(inLibrary.hideNavBar)
        assertFalse(inLibrary.keepScreenOn)

        val inPlayerPlaying = ImmersiveBarRule.resolve(s, isPlayerSurface = true, isPlaying = true)
        assertTrue(inPlayerPlaying.hideStatusBar)
        assertTrue(inPlayerPlaying.hideNavBar)
        assertTrue(inPlayerPlaying.keepScreenOn)

        val inPlayerPaused = ImmersiveBarRule.resolve(s, isPlayerSurface = true, isPlaying = false)
        assertTrue(inPlayerPaused.hideStatusBar)
        assertTrue(inPlayerPaused.hideNavBar)
        assertFalse(inPlayerPaused.keepScreenOn)
    }

    @Test
    fun globalScope_respectsIndividualBarToggles() {
        val s = AppSettings(
            immersiveMusicMode = true,
            immersiveScope = ImmersiveScope.GLOBAL,
            immersiveHideStatusBar = true,
            immersiveHideNavBar = false,
            immersiveKeepScreenOn = true,
        )
        val inLibrary = ImmersiveBarRule.resolve(s, isPlayerSurface = false, isPlaying = true)
        assertTrue(inLibrary.hideStatusBar)
        assertFalse(inLibrary.hideNavBar)
        assertFalse(inLibrary.keepScreenOn) // 常亮仅在播放页生效，不在音乐库耗电
    }

    @Test
    fun lockScreen_neverForcesKeepScreenOn() {
        val s = AppSettings(
            immersiveMusicMode = true,
            immersiveKeepScreenOn = true,
        )
        val lockSpec = ImmersiveBarRule.resolve(
            settings = s,
            isPlayerSurface = true,
            isPlaying = true,
            allowKeepScreenOn = false,
        )
        assertTrue(lockSpec.hideStatusBar)
        assertTrue(lockSpec.hideNavBar)
        assertFalse(lockSpec.keepScreenOn)
    }
}
