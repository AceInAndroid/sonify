package com.sonify.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import com.sonify.player.R
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.usb.UacStats

/**
 * 纯函数通知栏 HiFi 规格与副标题格式化工具（便于 JVM 单元测试覆盖）。
 */
object SonifyNotificationFormat {

    fun formatSpecBadge(
        activeRoute: ActiveOutputRoute,
        usbConnected: Boolean,
        stats: UacStats,
        deviceName: String,
    ): String {
        if (activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbConnected) {
            return if (stats.rate > 0 && stats.bits > 0) {
                val sr = if (stats.rate % 1000 == 0) "${stats.rate / 1000}k"
                else "%.1fk".format(stats.rate / 1000.0)
                "USB 独占 · $sr/${stats.bits}b"
            } else if (deviceName.isNotBlank()) {
                "USB 独占 · $deviceName"
            } else {
                "USB 独占"
            }
        }
        return "本机输出"
    }

    fun formatArtistLine(
        artist: String,
        album: String,
        showSpec: Boolean,
        specBadge: String,
    ): String {
        val cleanArtist = artist.ifBlank { "未知艺术家" }
        return if (showSpec && specBadge.isNotBlank()) {
            "$cleanArtist · $specBadge"
        } else if (album.isNotBlank()) {
            "$cleanArtist · $album"
        } else {
            cleanArtist
        }
    }
}

/**
 * Spotify 风格 5 键通知栏 Provider：
 *   [♡ 收藏]   [⏮ 上一曲]   [⏯ 播放/暂停]   [⏭ 下一曲]   [🔁 循环模式]
 * 折叠态保留核心 3 键（⏮ ⏯ ⏭），小图标使用 Sonify 极简黑胶矢量图标。
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION")
class SonifyMediaNotificationProvider(
    context: Context,
) : DefaultMediaNotificationProvider(
    context,
    NotificationIdProvider { DEFAULT_NOTIFICATION_ID },
    CHANNEL_ID,
    R.string.notif_channel_name,
) {
    init {
        setSmallIcon(R.drawable.ic_sonify_notification)
    }

    override fun getMediaButtons(
        session: androidx.media3.session.MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val defaultButtons = super.getMediaButtons(
            session,
            playerCommands,
            ImmutableList.of(),
            showPauseButton,
        )

        val ordered = ImmutableList.builder<CommandButton>()
        // 左侧第 1 键：收藏 / 取消收藏
        if (customLayout.isNotEmpty()) {
            ordered.add(customLayout[0])
        }
        // 中间核心 3 键：上一曲 · 播放/暂停 · 下一曲
        ordered.addAll(defaultButtons)
        // 右侧第 5 键：循环模式切换（顺序 / 列表循环 / 单曲循环）
        if (customLayout.size >= 2) {
            ordered.add(customLayout[1])
        }
        return ordered.build()
    }

    companion object {
        const val CHANNEL_ID = "sonify_playback"
    }
}
