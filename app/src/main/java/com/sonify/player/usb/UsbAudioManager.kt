package com.sonify.player.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sonify.player.ui.UacInfo
import com.sonify.player.ui.parseInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

data class UsbConnectionState(
    val devices: List<UsbDevice> = emptyList(),
    val connectedDevice: UsbDevice? = null,
    val uacInfo: UacInfo? = null,
    val supportedRates: List<Int> = emptyList(),
    val stats: UacStats = UacStats(),
    val logs: List<String> = emptyList(),
    val nativeLoaded: Boolean = false,
) {
    val isConnected: Boolean get() = connectedDevice != null && uacInfo != null

    val deviceDisplayName: String
        get() = uacInfo?.product?.takeIf { it.isNotBlank() }
            ?: connectedDevice?.productName?.takeIf { it.isNotBlank() }
            ?: connectedDevice?.deviceName
            ?: "未连接 USB DAC"
}

/**
 * 应用级 USB 音频设备管理器：
 * 统一负责枚举、权限、claimInterface(force=true) 踢掉内核驱动、openDevice 与热插拔广播。
 */
class UsbAudioManager(
    private val appContext: Context,
    private val volumeController: UsbVolumeController,
) {

    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "${appContext.packageName}.USB_PERMISSION"

    @Volatile
    private var connection: UsbDeviceConnection? = null

    @Volatile
    private var openedDevice: UsbDevice? = null

    private val claimedInterfaces = mutableListOf<UsbInterface>()

    private val _state = MutableStateFlow(UsbConnectionState())
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    private val _detachedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val detachedEvents: SharedFlow<Unit> = _detachedEvents.asSharedFlow()

    private val _connectedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val connectedEvents: SharedFlow<Unit> = _connectedEvents.asSharedFlow()

    val isConnected: Boolean get() = _state.value.isConnected

    val openedDeviceName: String?
        get() = _state.value.takeIf { it.isConnected }?.deviceDisplayName

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val list = listAudioDevices()
            _state.update { it.copy(devices = list) }
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    appendLog("检测到 USB 音频设备接入（共 ${list.size} 台）")
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    appendLog("USB 设备已拔出")
                    val current = openedDevice
                    if (current != null && list.none { it.deviceName == current.deviceName }) {
                        close()
                        _detachedEvents.tryEmit(Unit)
                    }
                }
            }
        }
    }

    init {
        val loaded = runCatching {
            val s = UsbAudioNative.parseStats(UsbAudioNative.nativeStats())
            _state.update { it.copy(stats = s, nativeLoaded = true) }
            true
        }.getOrDefault(false)

        val list = listAudioDevices()
        _state.update { it.copy(devices = list, nativeLoaded = loaded) }
        appendLog(if (loaded) "native 音频库已加载" else "native 库加载失败，请检查 ABI (arm64-v8a)")

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            appContext, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun appendLog(msg: String) {
        Log.i(TAG, msg)
        _state.update { it.copy(logs = (it.logs + msg).takeLast(60)) }
    }

    fun refreshDevices(): List<UsbDevice> {
        val list = listAudioDevices()
        _state.update { it.copy(devices = list) }
        return list
    }

    fun refreshStats(): UacStats {
        if (!_state.value.nativeLoaded) return UacStats()
        val s = runCatching {
            UsbAudioNative.parseStats(UsbAudioNative.nativeStats())
        }.getOrDefault(UacStats())
        _state.update { it.copy(stats = s) }
        return s
    }

    fun listAudioDevices(): List<UsbDevice> =
        usbManager.deviceList.values
            .filter { isAudioDevice(it) }
            .sortedBy { it.deviceName }

    private fun isAudioDevice(d: UsbDevice): Boolean {
        if (d.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true
        for (i in 0 until d.interfaceCount) {
            if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) return true
        }
        return false
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true

        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != permissionAction) return
                    runCatching { appContext.unregisterReceiver(this) }
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(granted)
                }
            }

            ContextCompat.registerReceiver(
                appContext, receiver, IntentFilter(permissionAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val pi = PendingIntent.getBroadcast(
                appContext,
                device.deviceId,
                Intent(permissionAction).setPackage(appContext.packageName),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, pi)

            cont.invokeOnCancellation {
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        }
    }

    /** 一键授权并打开 USB DAC */
    suspend fun connectDevice(device: UsbDevice): Result<UacInfo> {
        appendLog("请求权限: ${device.productName ?: device.deviceName}")
        val granted = requestPermission(device)
        if (!granted) {
            appendLog("用户拒绝了 USB 权限")
            return Result.failure(IOException("USB 权限被拒绝"))
        }
        return open(device)
    }

    /** 如果已存在有权限的音频设备且尚未连接，自动静默打开 */
    fun tryAutoConnectPermitted(): Boolean {
        if (isConnected) return true
        val permitted = listAudioDevices().firstOrNull { usbManager.hasPermission(it) } ?: return false
        return open(permitted).isSuccess
    }

    @Synchronized
    fun open(device: UsbDevice): Result<UacInfo> {
        close()

        val conn = usbManager.openDevice(device)
            ?: return Result.failure(IOException("openDevice 返回 null（权限不足或被其他进程占用）"))

        val claimed = mutableListOf<UsbInterface>()
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            if (itf.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            val ok = conn.claimInterface(itf, true)
            if (ok) {
                claimed.add(itf)
            } else {
                claimed.forEach { runCatching { conn.releaseInterface(it) } }
                conn.close()
                val msg = "claimInterface(if=${itf.id}, force=true) 失败，内核驱动可能占着"
                appendLog(msg)
                return Result.failure(IOException(msg))
            }
        }
        if (claimed.isEmpty()) {
            conn.close()
            return Result.failure(IOException("设备没有音频类接口，无法以 UAC 模式使用"))
        }

        val fd = conn.fileDescriptor
        if (fd < 0) {
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            return Result.failure(IOException("getFileDescriptor 返回 $fd"))
        }

        val json = UsbAudioNative.nativeOpen(fd)
        if (json.startsWith("ERROR")) {
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            val err = json.removePrefix("ERROR:").trim()
            appendLog("打开失败: $err")
            return Result.failure(IOException(err))
        }

        val info = parseInfo(json)
        if (info == null) {
            UsbAudioNative.nativeClose()
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            return Result.failure(IOException("描述符 JSON 解析失败"))
        }

        val rates = UsbAudioNative.nativeSupportedRates()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }

        connection = conn
        openedDevice = device
        claimedInterfaces.clear()
        claimedInterfaces.addAll(claimed)

        // ★ 护耳第一防线：在通知连接/起播之前，立刻压低 DAC 硬件或软件音量至安全值
        val devKey = "%04x_%04x".format(device.vendorId, device.productId)
        val devLabel = info.product.ifBlank { device.productName ?: device.deviceName }
        val hwRange = FloatArray(3)
        val hasHwVol = UsbAudioNative.nativeHasHardwareVolume() &&
            UsbAudioNative.nativeGetHardwareVolumeRange(hwRange)
        volumeController.onUsbDeviceOpened(
            deviceKey = devKey,
            deviceName = devLabel,
            hwVolSupported = hasHwVol,
            hwMin = if (hasHwVol) hwRange[0].toDouble() else VolumeMath.DEFAULT_MIN_DB,
            hwMax = if (hasHwVol) hwRange[1].toDouble() else VolumeMath.DEFAULT_MAX_DB,
        )

        val stats = runCatching {
            UsbAudioNative.parseStats(UsbAudioNative.nativeStats())
        }.getOrDefault(UacStats())

        _state.update {
            it.copy(
                devices = listAudioDevices(),
                connectedDevice = device,
                uacInfo = info,
                supportedRates = rates,
                stats = stats,
            )
        }
        appendLog(
            "已连接 $devLabel (UAC${info.uacVersion}, ${rates.size} 档采样率, " +
                if (hasHwVol) "支持硬件音量 %.1f..%.1fdB)".format(hwRange[0], hwRange[1]) else "软件对数音量)"
        )
        _connectedEvents.tryEmit(Unit)
        return Result.success(info)
    }

    @Synchronized
    fun close() {
        volumeController.onUsbDeviceClosed()
        runCatching { UsbAudioNative.nativeClose() }
        val conn = connection
        if (conn != null) {
            claimedInterfaces.forEach { runCatching { conn.releaseInterface(it) } }
            runCatching { conn.close() }
        }
        claimedInterfaces.clear()
        connection = null
        openedDevice = null
        _state.update {
            it.copy(
                connectedDevice = null,
                uacInfo = null,
                supportedRates = emptyList(),
                stats = UacStats(buildTime = it.stats.buildTime),
            )
        }
    }

    companion object {
        private const val TAG = "HiFiUsb"
    }
}
