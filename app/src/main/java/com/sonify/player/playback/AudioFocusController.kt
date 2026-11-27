package com.sonify.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import com.sonify.player.R
import com.sonify.player.data.AudioFocusPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioFocusState(@StringRes val bannerTextRes: Int?) {
    NONE(null),
    GRANTED(null),
    DELAYED(R.string.player_focus_call),
    DUCKED(R.string.player_focus_ducking),
    PAUSED_TRANSIENT(R.string.player_focus_transient),
    LOST_PERMANENT(R.string.player_focus_lost),
}

sealed interface FocusDecision {
    data object NoOp : FocusDecision
    data object ResumePlayback : FocusDecision
    data object PauseForTransient : FocusDecision
    data object PausePermanent : FocusDecision
    data class ApplyDuck(val ducked: Boolean) : FocusDecision
}

/**
 * 纯函数音频焦点状态机（无 Android 依赖，100% 单元测试覆盖）：
 * - 精准区分「用户主动暂停」与「系统临时打断暂停」，绝不在用户手动暂停后误自动起播；
 * - 完整支持 AUDIOFOCUS_REQUEST_DELAYED（通话中延迟获焦）、TRANSIENT 自动恢复、
 *   CAN_DUCK 压低/暂停/忽略三策略，以及永久 LOSS 后随时重新抢占。
 */
class AudioFocusStateMachine {
    var state: AudioFocusState = AudioFocusState.NONE
        private set

    var resumeOnFocusGain: Boolean = false
        private set

    var isDucked: Boolean = false
        private set

    /** 申请焦点结果处理：返回是否应立即开始播放 */
    fun onRequestResult(result: Int): Boolean = when (result) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
            state = AudioFocusState.GRANTED
            resumeOnFocusGain = false
            isDucked = false
            true
        }

        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
            state = AudioFocusState.DELAYED
            resumeOnFocusGain = true
            isDucked = false
            false
        }

        else -> {
            state = AudioFocusState.LOST_PERMANENT
            resumeOnFocusGain = false
            isDucked = false
            false
        }
    }

    /** 用户主动点击暂停：清除任何自动恢复标记，避免后续系统归还焦点时擅自出声 */
    fun onUserPause() {
        resumeOnFocusGain = false
        isDucked = false
        if (state == AudioFocusState.DUCKED ||
            state == AudioFocusState.PAUSED_TRANSIENT ||
            state == AudioFocusState.DELAYED
        ) {
            state = AudioFocusState.NONE
        }
    }

    /** 主动停止或释放焦点 */
    fun onAbandoned() {
        state = AudioFocusState.NONE
        resumeOnFocusGain = false
        isDucked = false
    }

    /** 系统回调焦点变化 */
    fun onFocusChange(
        focusChange: Int,
        currentlyPlaying: Boolean,
        policy: AudioFocusPolicy,
    ): FocusDecision = when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
            val wasDucked = isDucked
            val shouldResume = resumeOnFocusGain
            state = AudioFocusState.GRANTED
            isDucked = false
            resumeOnFocusGain = false
            when {
                shouldResume -> FocusDecision.ResumePlayback
                wasDucked -> FocusDecision.ApplyDuck(false)
                else -> FocusDecision.NoOp
            }
        }

        AudioManager.AUDIOFOCUS_LOSS -> {
            // 被其他音乐/视频应用永久抢占：暂停且不自动抢回（需用户再次点击播放抢占）
            state = AudioFocusState.LOST_PERMANENT
            resumeOnFocusGain = false
            isDucked = false
            if (currentlyPlaying) FocusDecision.PausePermanent else FocusDecision.NoOp
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // 来电、微信语音、闹钟等短时打断：暂停并记住待恢复
            isDucked = false
            if (currentlyPlaying || resumeOnFocusGain) {
                state = AudioFocusState.PAUSED_TRANSIENT
                resumeOnFocusGain = true
                if (currentlyPlaying) FocusDecision.PauseForTransient else FocusDecision.NoOp
            } else {
                FocusDecision.NoOp
            }
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            when (policy) {
                AudioFocusPolicy.STANDARD_DUCK -> {
                    if (currentlyPlaying) {
                        state = AudioFocusState.DUCKED
                        isDucked = true
                        FocusDecision.ApplyDuck(true)
                    } else FocusDecision.NoOp
                }

                AudioFocusPolicy.PAUSE_ON_DUCK -> {
                    if (currentlyPlaying || resumeOnFocusGain) {
                        state = AudioFocusState.PAUSED_TRANSIENT
                        resumeOnFocusGain = true
                        isDucked = false
                        if (currentlyPlaying) FocusDecision.PauseForTransient else FocusDecision.NoOp
                    } else FocusDecision.NoOp
                }

                AudioFocusPolicy.IGNORE_DUCK -> {
                    // 发烧友沉浸模式：忽略短提示音，不中断不压低
                    FocusDecision.NoOp
                }
            }
        }

        else -> FocusDecision.NoOp
    }
}

/**
 * 系统音频焦点控制器：
 * 统一接管本机输出与 USB 独占输出的焦点抢占、Ducking 避让与自动恢复。
 */
class AudioFocusController(
    context: Context,
    private val policyProvider: () -> AudioFocusPolicy,
    private val isPlayingProvider: () -> Boolean,
    private val onResumeRequested: () -> Unit,
    private val onPauseRequested: (permanent: Boolean) -> Unit,
    private val onDuckChanged: (ducked: Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val machine = AudioFocusStateMachine()

    private var activeFocusRequest: AudioFocusRequest? = null

    private val _focusState = MutableStateFlow(AudioFocusState.NONE)
    val focusState: StateFlow<AudioFocusState> = _focusState.asStateFlow()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        val policy = policyProvider()
        val playing = isPlayingProvider()
        val decision = machine.onFocusChange(change, playing, policy)
        _focusState.value = machine.state
        Log.i(TAG, "焦点变化 change=$change (playing=$playing, policy=$policy) → $decision, state=${machine.state}")

        when (decision) {
            FocusDecision.NoOp -> Unit
            FocusDecision.ResumePlayback -> {
                onDuckChanged(false)
                onResumeRequested()
            }

            FocusDecision.PauseForTransient -> {
                onDuckChanged(false)
                onPauseRequested(false)
            }

            FocusDecision.PausePermanent -> {
                onDuckChanged(false)
                onPauseRequested(true)
                abandonFocusInternal(resetState = false)
            }

            is FocusDecision.ApplyDuck -> {
                onDuckChanged(decision.ducked)
            }
        }
    }

    /** 播放前主动向系统抢占 AUDIOFOCUS_GAIN（逼停其他媒体播放器） */
    fun requestFocusForPlayback(): Boolean {
        val policy = policyProvider()
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(policy == AudioFocusPolicy.PAUSE_ON_DUCK)
                .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                .build()
            activeFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }

        val canPlayNow = machine.onRequestResult(res)
        _focusState.value = machine.state
        if (canPlayNow) {
            onDuckChanged(false)
        }
        Log.i(TAG, "请求系统音频焦点 result=$res → canPlayNow=$canPlayNow, state=${machine.state}")
        return canPlayNow
    }

    /** 用户主动暂停：取消后续自动恢复标记并还原 Duck 状态 */
    fun onUserPaused() {
        machine.onUserPause()
        _focusState.value = machine.state
        onDuckChanged(false)
    }

    /** 播放结束或服务销毁时归还系统焦点 */
    fun abandonFocus() {
        abandonFocusInternal(resetState = true)
    }

    private fun abandonFocusInternal(resetState: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { audioManager.abandonAudioFocusRequest(req) }
            }
            activeFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(focusChangeListener) }
        }
        onDuckChanged(false)
        if (resetState) {
            machine.onAbandoned()
            _focusState.value = machine.state
        }
    }

    companion object {
        private const val TAG = "HiFiAudioFocus"
        const val DUCK_ATTENUATION_DB = -12.0
    }
}
