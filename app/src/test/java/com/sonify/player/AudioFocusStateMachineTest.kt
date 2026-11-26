package com.sonify.player

import android.media.AudioManager
import com.sonify.player.data.AudioFocusPolicy
import com.sonify.player.playback.AudioFocusState
import com.sonify.player.playback.AudioFocusStateMachine
import com.sonify.player.playback.FocusDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusStateMachineTest {

    @Test
    fun transientLoss_pausesAndAutoResumesOnGain() {
        val m = AudioFocusStateMachine()
        assertTrue(m.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertEquals(AudioFocusState.GRANTED, m.state)

        // 来电或微信语音短时打断
        val d1 = m.onFocusChange(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            currentlyPlaying = true,
            policy = AudioFocusPolicy.STANDARD_DUCK,
        )
        assertEquals(FocusDecision.PauseForTransient, d1)
        assertEquals(AudioFocusState.PAUSED_TRANSIENT, m.state)
        assertTrue(m.resumeOnFocusGain)

        // 通话结束系统归还焦点 -> 自动恢复播放
        val d2 = m.onFocusChange(
            AudioManager.AUDIOFOCUS_GAIN,
            currentlyPlaying = false,
            policy = AudioFocusPolicy.STANDARD_DUCK,
        )
        assertEquals(FocusDecision.ResumePlayback, d2)
        assertEquals(AudioFocusState.GRANTED, m.state)
        assertFalse(m.resumeOnFocusGain)
    }

    @Test
    fun userPauseDuringTransient_doesNotAutoResumeLater() {
        val m = AudioFocusStateMachine()
        m.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        m.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, true, AudioFocusPolicy.STANDARD_DUCK)
        assertTrue(m.resumeOnFocusGain)

        // 用户主动点了暂停，明确不想恢复
        m.onUserPause()
        assertFalse(m.resumeOnFocusGain)

        val d = m.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, false, AudioFocusPolicy.STANDARD_DUCK)
        assertEquals(FocusDecision.NoOp, d)
    }

    @Test
    fun canDuck_behavesAccordingToPolicy() {
        // 标准模式：压低 -> 恢复原音量
        val m1 = AudioFocusStateMachine()
        m1.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertEquals(
            FocusDecision.ApplyDuck(true),
            m1.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, true, AudioFocusPolicy.STANDARD_DUCK),
        )
        assertEquals(AudioFocusState.DUCKED, m1.state)
        assertEquals(
            FocusDecision.ApplyDuck(false),
            m1.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, true, AudioFocusPolicy.STANDARD_DUCK),
        )
        assertEquals(AudioFocusState.GRANTED, m1.state)

        // 纯净模式：暂停而非压低 -> 结束后自动恢复
        val m2 = AudioFocusStateMachine()
        m2.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertEquals(
            FocusDecision.PauseForTransient,
            m2.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, true, AudioFocusPolicy.PAUSE_ON_DUCK),
        )
        assertEquals(
            FocusDecision.ResumePlayback,
            m2.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, false, AudioFocusPolicy.PAUSE_ON_DUCK),
        )

        // 沉浸模式：忽略短提示音
        val m3 = AudioFocusStateMachine()
        m3.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertEquals(
            FocusDecision.NoOp,
            m3.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, true, AudioFocusPolicy.IGNORE_DUCK),
        )
    }

    @Test
    fun permanentLoss_pausesWithoutAutoResumeUntilUserReclaims() {
        val m = AudioFocusStateMachine()
        m.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        val d1 = m.onFocusChange(AudioManager.AUDIOFOCUS_LOSS, true, AudioFocusPolicy.STANDARD_DUCK)
        assertEquals(FocusDecision.PausePermanent, d1)
        assertEquals(AudioFocusState.LOST_PERMANENT, m.state)
        assertFalse(m.resumeOnFocusGain)

        // 用户再次点播放重新抢占焦点
        assertTrue(m.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertEquals(AudioFocusState.GRANTED, m.state)
    }

    @Test
    fun delayedFocusGain_waitsAndAutoStartsWhenGranted() {
        val m = AudioFocusStateMachine()
        assertFalse(m.onRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
        assertEquals(AudioFocusState.DELAYED, m.state)
        assertTrue(m.resumeOnFocusGain)

        val d = m.onFocusChange(AudioManager.AUDIOFOCUS_GAIN, false, AudioFocusPolicy.STANDARD_DUCK)
        assertEquals(FocusDecision.ResumePlayback, d)
        assertEquals(AudioFocusState.GRANTED, m.state)
    }
}
