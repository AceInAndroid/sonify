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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.R
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
                text = stringResource(R.string.player_output_sheet_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (isUsbActive) R.string.player_output_usb_locked else R.string.player_output_android_route
                ),
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
                    label = stringResource(R.string.player_output_source),
                    value = currentTrack?.formatBadgeFull() ?: stringResource(R.string.player_output_idle),
                    valueColor = colors.textPrimary,
                )
                SignalRow(
                    label = stringResource(R.string.player_output_negotiated),
                    value = if (isUsbActive && stats.rate > 0) {
                        "UAC${usbState.uacInfo?.uacVersion ?: 2} · ${stats.rate}Hz / ${stats.bits}bit (${stats.subslot}B)"
                    } else if (usbState.isConnected && settings.usbExclusiveEnabled) {
                        stringResource(R.string.player_output_usb_ready, usbState.deviceDisplayName)
                    } else {
                        "Android AudioTrack"
                    },
                    valueColor = if (isUsbActive) colors.exclusiveGreen else colors.textSecondary,
                )
                if (isUsbActive) {
                    val fbStr = if (stats.fbRate > 0) "%.1f Hz".format(stats.fbRate) else stringResource(R.string.player_output_no_feedback)
                    SignalRow(
                        label = stringResource(R.string.player_output_clock_feedback),
                        value = stringResource(
                            R.string.player_output_clock_detail,
                            fbStr,
                            stats.bufferFillMs,
                            stats.underruns,
                            stats.ringUnderruns,
                        ),
                        valueColor = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.player_output_select),
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
                        stringResource(
                            R.string.player_output_usb_sub,
                            usbState.uacInfo?.uacVersion ?: 2,
                            usbState.supportedRates.size,
                        )
                    } else {
                        stringResource(R.string.player_output_usb_vid_pid, dev.vendorId, dev.productId)
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
                title = stringResource(R.string.player_output_local_title),
                subtitle = stringResource(R.string.player_output_local_sub),
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
                    contentDescription = stringResource(R.string.common_selected),
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
