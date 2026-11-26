package com.sonify.player.ui.components

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.data.AppSettings
import com.sonify.player.data.TrackEntity
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.UsbConnectionState

/**
 * Spotify Connect 风格的音频输出设备与实时 HiFi 信号链路弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputDeviceSheet(
    currentTrack: TrackEntity?,
    settings: AppSettings,
    usbState: UsbConnectionState,
    activeRoute: ActiveOutputRoute,
    onSelectUsbDevice: (UsbDevice) -> Unit,
    onSwitchToSystem: () -> Unit,
    onSwitchToUsbExclusive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isUsbActive = activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbState.isConnected
    val stats = usbState.stats

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "音频输出与信号链路",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isUsbActive) "Bit-Perfect USB 独占已锁定（绕过 Android 混音）"
                else "当前经由 Android 系统音频通路输出",
                fontSize = 12.sp,
                color = if (isUsbActive) colors.exclusiveGreen else colors.textSecondary,
            )

            Spacer(Modifier.height(16.dp))

            // 信号链路摘要卡
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SignalRow(
                    label = "音频源",
                    value = currentTrack?.formatBadgeFull() ?: "未在播放",
                    valueColor = colors.textPrimary,
                )
                SignalRow(
                    label = "输出协商",
                    value = if (isUsbActive && stats.rate > 0) {
                        "UAC${usbState.uacInfo?.uacVersion ?: 2} · ${stats.rate}Hz / ${stats.bits}bit (${stats.subslot}B)"
                    } else if (usbState.isConnected && settings.usbExclusiveEnabled) {
                        "已连接 ${usbState.deviceDisplayName}（待起播）"
                    } else {
                        "Android AudioTrack"
                    },
                    valueColor = if (isUsbActive) colors.exclusiveGreen else colors.textSecondary,
                )
                if (isUsbActive) {
                    val fbStr = if (stats.fbRate > 0) "%.1f Hz".format(stats.fbRate) else "无 feedback"
                    SignalRow(
                        label = "时钟反馈",
                        value = "设备 $fbStr · 缓冲 %.0f ms · 欠载 ${stats.underruns}/${stats.ringUnderruns}".format(stats.bufferFillMs),
                        valueColor = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "选择输出设备",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))

            // USB DAC 列表
            usbState.devices.forEach { dev ->
                val isThisConnected = usbState.connectedDevice?.deviceName == dev.deviceName
                val isSelected = isThisConnected && settings.usbExclusiveEnabled
                DeviceOptionRow(
                    icon = Icons.Rounded.Usb,
                    title = dev.productName ?: dev.deviceName,
                    subtitle = if (isThisConnected) {
                        "USB 独占 · UAC${usbState.uacInfo?.uacVersion ?: 2} · ${usbState.supportedRates.size} 档采样率"
                    } else {
                        "VID 0x%04X · PID 0x%04X（点击授权并独占连接）".format(dev.vendorId, dev.productId)
                    },
                    selected = isSelected,
                    highlightColor = colors.exclusiveGreen,
                    onClick = {
                        if (isThisConnected) onSwitchToUsbExclusive() else onSelectUsbDevice(dev)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // 本机扬声器 / 耳机回退选项
            DeviceOptionRow(
                icon = Icons.Rounded.Headphones,
                title = "本机扬声器 / 有线耳机 / 蓝牙",
                subtitle = "走系统 AudioTrack（退出 USB 独占模式）",
                selected = !isUsbActive && (!settings.usbExclusiveEnabled || !usbState.isConnected),
                highlightColor = colors.accent,
                onClick = onSwitchToSystem,
            )
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, valueColor: Color) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = colors.textMuted)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeviceOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    highlightColor: Color,
    onClick: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) highlightColor.copy(alpha = 0.12f) else colors.surface2)
            .border(
                1.dp,
                if (selected) highlightColor.copy(alpha = 0.55f) else colors.hairline,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) highlightColor else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) highlightColor else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(highlightColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已选",
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
