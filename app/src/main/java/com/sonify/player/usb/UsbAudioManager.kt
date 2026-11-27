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
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.sonify.player.HiFiApp
import com.sonify.player.R
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
            ?: if (java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)) "未连接 USB DAC" else "No USB DAC Connected"
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

    private fun s(@StringRes id: Int, vararg args: Any): String {
        val ctx = (appContext.applicationContext as? HiFiApp)?.localizedContext() ?: appContext
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

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
                    appendLog(s(R.string.usb_log_attached, list.size))
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    appendLog(s(R.string.usb_log_detached))
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
        appendLog(s(if (loaded) R.string.usb_log_native_ok else R.string.usb_log_native_fail))

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
        appendLog(s(R.string.usb_log_req_perm, device.productName ?: device.deviceName))
        val granted = requestPermission(device)
        if (!granted) {
            appendLog(s(R.string.usb_log_perm_denied))
            return Result.failure(IOException(s(R.string.usb_log_perm_denied)))
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
            ?: return Result.failure(IOException(s(R.string.usb_log_open_null)))

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
                val msg = s(R.string.usb_log_claim_fail, itf.id)
                appendLog(msg)
                return Result.failure(IOException(msg))
            }
        }
        if (claimed.isEmpty()) {
            conn.close()
            return Result.failure(IOException(s(R.string.usb_log_no_audio_if)))
        }

        val fd = conn.fileDescriptor
        if (fd < 0) {
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            return Result.failure(IOException("getFileDescriptor -> $fd"))
        }

        val json = UsbAudioNative.nativeOpen(fd)
        if (json.startsWith("ERROR")) {
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            val err = json.removePrefix("ERROR:").trim()
            appendLog(s(R.string.usb_log_open_fail, err))
            return Result.failure(IOException(err))
        }

        val info = parseInfo(json)
        if (info == null) {
            UsbAudioNative.nativeClose()
            claimed.forEach { runCatching { conn.releaseInterface(it) } }
            conn.close()
            return Result.failure(IOException(s(R.string.usb_log_desc_parse_fail)))
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
            if (hasHwVol) {
                s(R.string.usb_log_connected_hw, devLabel, info.uacVersion, rates.size, hwRange[0], hwRange[1])
            } else {
                s(R.string.usb_log_connected_sw, devLabel, info.uacVersion, rates.size)
            }
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
