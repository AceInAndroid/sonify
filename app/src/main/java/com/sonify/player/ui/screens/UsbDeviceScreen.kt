package com.sonify.player.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.audio.ActiveOutputRoute
import com.sonify.player.data.AppSettings
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.usb.UsbAudioNative
import com.sonify.player.usb.UsbConnectionState
import kotlinx.coroutines.delay
import kotlin.math.log10

/**
 * Tab 2: USB 设备管理与实时链路监控页（致敬 Spotify Connect + 保留全部 M1 工程诊断能力）。
 */
@Composable
fun UsbDeviceScreen(
    usbState: UsbConnectionState,
    settings: AppSettings,
    activeRoute: ActiveOutputRoute,
    onRefreshDevices: () -> Unit,
    onConnectDevice: (UsbDevice) -> Unit,
    onDisconnectDevice: () -> Unit,
    onToggleExclusive: (Boolean) -> Unit,
    onRefreshStats: () -> Unit,
    onAppendLog: (String) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    var showEngineering by remember { mutableStateOf(false) }
    var tonePlaying by remember { mutableStateOf(false) }
    var toneRate by remember(usbState.supportedRates) {
        mutableIntStateOf(usbState.supportedRates.firstOrNull() ?: 44100)
    }
    var toneAmp by remember { mutableFloatStateOf(0.08f) }

    LaunchedEffect(usbState.isConnected, tonePlaying) {
        while (usbState.isConnected) {
            onRefreshStats()
            delay(700)
        }
    }

    val stats = usbState.stats
    val isExclusiveLocked = activeRoute == ActiveOutputRoute.USB_EXCLUSIVE && usbState.isConnected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = bottomPadding + 24.dp)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "USB 设备",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefreshDevices) {
                Icon(Icons.Rounded.Refresh, contentDescription = "扫描 USB", tint = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(10.dp))

        // 顶部状态仪表大卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface1)
                .border(
                    1.dp,
                    if (usbState.isConnected) colors.exclusiveGreen.copy(alpha = 0.4f) else colors.hairline,
                    RoundedCornerShape(14.dp),
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (usbState.isConnected) colors.exclusiveGreen.copy(alpha = 0.16f)
                            else colors.surface2
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Usb,
                        contentDescription = null,
                        tint = if (usbState.isConnected) colors.exclusiveGreen else colors.textMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (usbState.isConnected) colors.exclusiveGreen else colors.textMuted)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                isExclusiveLocked -> "USB 独占输出运行中"
                                usbState.isConnected -> "USB DAC 已连接就绪"
                                else -> "未连接 USB DAC"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (usbState.isConnected) colors.exclusiveGreen else colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = usbState.deviceDisplayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (usbState.isConnected) {
                    Text(
                        text = "断开",
                        fontSize = 12.sp,
                        color = colors.danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.danger.copy(alpha = 0.12f))
                            .clickable {
                                tonePlaying = false
                                onDisconnectDevice()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (usbState.isConnected) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile(
                        label = "协商采样率",
                        value = if (stats.rate > 0) "${stats.rate} Hz" else "待机",
                        highlight = stats.running,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "位深 / 容器",
                        value = if (stats.bits > 0) "${stats.bits}b / ${stats.subslot}B" else "—",
                        highlight = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile(
                        label = "Feedback 实测",
                        value = if (stats.fbRate > 0) "%.1f Hz".format(stats.fbRate) else "—",
                        highlight = false,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "缓冲 / 欠载",
                        value = "%.0fms · %d/%d".format(stats.bufferFillMs, stats.underruns, stats.ringUnderruns),
                        highlight = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "通过 OTG 接入 USB DAC 后，点击下方设备授权即可启用 Bit-Perfect 独占驱动。未连接时将自动回退至本机音频输出。",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // USB 独占开关快捷栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface1)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "USB 独占输出（Bit-Perfect）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "绕过 Android AudioFlinger 重采样，直接发 iso URB",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = settings.usbExclusiveEnabled,
                onCheckedChange = onToggleExclusive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = colors.exclusiveGreen,
                ),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "已检测到的 USB 音频设备（${usbState.devices.size}）",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))

        if (usbState.devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface1)
                    .padding(16.dp)
            ) {
                Text(
                    text = "未检测到 USB Audio Class 设备。请确认 DAC 已插紧且手机已开启 OTG。",
                    fontSize = 12.sp,
                    color = colors.textMuted,
                )
            }
        } else {
            usbState.devices.forEach { dev ->
                val isConn = usbState.connectedDevice?.deviceName == dev.deviceName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConn) colors.surface2 else colors.surface1)
                        .border(
                            1.dp,
                            if (isConn) colors.exclusiveGreen.copy(alpha = 0.6f) else colors.hairline,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onConnectDevice(dev) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = dev.productName ?: dev.deviceName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "VID 0x%04X · PID 0x%04X · ${dev.deviceName}".format(dev.vendorId, dev.productId),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (isConn) "已连接" else "连接",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConn) colors.exclusiveGreen else colors.accent,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 可折叠的 M1 工程诊断与正弦波测试抽屉
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface1)
                .clickable { showEngineering = !showEngineering }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "工程诊断与测试音工具",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "UAC 描述符 / Alt Setting / 440Hz & 1kHz 纯音测试 / 日志",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = if (showEngineering) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }

        AnimatedVisibility(visible = showEngineering) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface1)
                    .padding(14.dp)
            ) {
                usbState.uacInfo?.let { info ->
                    Text(
                        text = "UAC${info.uacVersion} · AC 接口 ${info.acInterface} · " +
                            if (info.hasClockSource) "Clock ID ${info.clockSourceId}" else "无独立 Clock",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    info.alts.forEach { a ->
                        Text(
                            text = "if=${a.iface} alt=${a.alt} ep=${a.ep} pkt=${a.maxPacket} " +
                                "${a.bits}b/${a.subslot}B ${a.channels}ch fb=${a.feedbackEp}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("测试音采样率", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    val rates = usbState.supportedRates.ifEmpty { listOf(44100, 48000, 96000) }
                    rates.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { r ->
                                val on = r == toneRate
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (on) colors.accent.copy(alpha = 0.2f) else colors.surface2)
                                        .border(
                                            1.dp,
                                            if (on) colors.accent else colors.hairline,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { toneRate = r }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (r % 1000 == 0) "${r / 1000}k" else "$r",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (on) colors.accent else colors.textSecondary,
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("测试音幅度", fontSize = 12.sp, color = colors.textSecondary)
                        Text(
                            "%.0f dB".format(20 * log10(toneAmp.toDouble().coerceAtLeast(1e-6))),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textPrimary,
                        )
                    }
                    Slider(
                        value = toneAmp,
                        onValueChange = { toneAmp = it },
                        valueRange = 0.005f..0.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.surface3,
                        ),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (tonePlaying) colors.danger else colors.accent)
                                .clickable {
                                    if (tonePlaying) {
                                        UsbAudioNative.nativeStop()
                                        tonePlaying = false
                                        onAppendLog("测试音已停止")
                                    } else {
                                        val ok = UsbAudioNative.nativePlayTone(440.0, 0, toneRate, toneAmp.toDouble())
                                        tonePlaying = ok
                                        onAppendLog(if (ok) "播放 440Hz @ $toneRate Hz" else "测试音启动失败")
                                    }
                                    onRefreshStats()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (tonePlaying) "停止测试音" else "播放 440Hz",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (tonePlaying) Color.White else colors.onAccent,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surface2)
                                .clickable {
                                    val ok = UsbAudioNative.nativePlayTone(1000.0, 0, toneRate, toneAmp.toDouble())
                                    tonePlaying = ok
                                    onAppendLog(if (ok) "播放 1kHz @ $toneRate Hz" else "测试音启动失败")
                                    onRefreshStats()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("播放 1kHz", fontSize = 13.sp, color = colors.textPrimary)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stats.rawJson.ifBlank { "无实时统计" },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textMuted,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("最近日志", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                Spacer(Modifier.height(4.dp))
                usbState.logs.takeLast(12).reversed().forEach { line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textMuted,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = colors.textMuted)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) colors.exclusiveGreen else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
