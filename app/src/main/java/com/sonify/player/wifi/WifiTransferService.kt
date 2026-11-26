package com.sonify.player.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sonify.player.HiFiApp
import com.sonify.player.MainActivity
import com.sonify.player.R

/**
 * WiFi 局域网传歌前台保活服务（foregroundServiceType="dataSync"）：
 * 防止传输大体积 24-bit/192kHz 无损专辑时息屏或切后台中断，并提供通知栏一键停止。
 */
class WifiTransferService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            HiFiApp.from(this).wifiTransferManager.stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
            .ifBlank { HiFiApp.from(this).wifiTransferManager.state.value.primaryUrl }
        val notif = buildNotification(url)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(url: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "WiFi 局域网传歌",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            3001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, WifiTransferService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this,
            3002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sonify_notification)
            .setContentTitle("Sonify WiFi 传歌运行中")
            .setContentText(if (url.isNotBlank()) "浏览器访问 $url" else "等待局域网设备上传无损音乐")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .addAction(0, "停止服务", stopPi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sonify_wifi_transfer"
        private const val NOTIF_ID = 2088
        private const val EXTRA_URL = "extra_url"
        private const val ACTION_STOP = "com.sonify.player.wifi.ACTION_STOP"

        fun start(context: Context, url: String) {
            val intent = Intent(context, WifiTransferService::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WifiTransferService::class.java)) }
        }
    }
}
