package com.sonify.player.update

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import com.sonify.player.BuildConfig
import com.sonify.player.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Sonify 应用内更新管理器：
 * - 默认对接 https://sonify.zhangbing.tech/api/v1/update/latest；
 * - 支持常规自动更新（12h 节流 + 忽略版本）与全生命周期锁定「强制更新」（穿透自动开关与忽略版本）；
 * - 流式进度下载（含实时速度与取消）+ 强制 SHA-256 校验；已校验缓存秒进 ReadyToInstall。
 */
class AppUpdateManager(
    private val context: Context,
    private val prefs: UserPreferences,
    private val onTrackEvent: (name: String, props: Map<String, Any?>, flushNow: Boolean) -> Unit = { _, _, _ -> },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    /** 安装包大文件下载客户端：采用稳定 HTTP/1.1 避免部分 CDN/移动网络 HTTP/2 流重置 */
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _availableBadge = MutableStateFlow<ReleaseInfo?>(null)
    val availableBadge: StateFlow<ReleaseInfo?> = _availableBadge.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private var downloadJob: Job? = null
    private var lastSilentCheckMs: Long = 0L

    fun activeBaseUrl(): String {
        if (BuildConfig.DEBUG) {
            val custom = prefs.current.customUpdateBaseUrl.trim().trimEnd('/')
            if (custom.startsWith("http://") || custom.startsWith("https://")) return custom
        }
        return BuildConfig.UPDATE_BASE_URL.trimEnd('/')
    }

    fun dismissDialog() {
        if (_state.value.isForcedUpdate) return
        _showDialog.value = false
    }

    fun ignoreVersion(versionCode: Int) {
        if (_state.value.isForcedUpdate) return
        scope.launch {
            prefs.setIgnoredUpdateVersionCode(versionCode)
            _showDialog.value = false
        }
    }

    /** 回到前台时调用：若已在强更锁定态则确保弹窗常驻，否则按 30 分钟冷却静默复查是否开启紧急强更 */
    fun onForegrounded() {
        if (_state.value.isForcedUpdate) {
            _showDialog.value = true
            return
        }
        checkForUpdates(manual = false, isColdStart = false)
    }

    fun checkForUpdates(manual: Boolean, isColdStart: Boolean = false) {
        val settings = prefs.current
        val now = System.currentTimeMillis()
        if (!manual && !UpdatePolicy.shouldFetchManifestSilently(lastSilentCheckMs, now, isColdStart)) {
            return
        }
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Verifying) {
            if (manual || _state.value.isForcedUpdate) _showDialog.value = true
            return
        }

        val allowNormalAutoPrompt = UpdatePolicy.shouldRunAutoCheck(
            autoCheckEnabled = settings.autoCheckUpdate,
            lastCheckMs = settings.lastUpdateCheckAt,
            nowMs = now,
        )

        scope.launch {
            if (manual) {
                _state.value = UpdateState.Checking
                _showDialog.value = true
            }
            lastSilentCheckMs = System.currentTimeMillis()

            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "arm64-v8a" }
            val url = "${activeBaseUrl()}/api/v1/update/latest?channel=stable&abi=$abi&versionCode=${BuildConfig.VERSION_CODE}"
            val result = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                    .build()
                downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("更新服务返回 HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    ReleaseInfo.fromJson(body) ?: error("服务端返回的版本清单格式无效")
                }
            }

            if (manual || allowNormalAutoPrompt) {
                prefs.setLastUpdateCheckAt(System.currentTimeMillis())
            }

            result.onSuccess { release ->
                val eval = UpdatePolicy.evaluate(
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    release = release,
                    ignoredVersionCode = prefs.current.ignoredUpdateVersionCode,
                    isManualCheck = manual,
                    allowNormalAutoPrompt = allowNormalAutoPrompt,
                )
                onTrackEvent(
                    "update_check",
                    mapOf(
                        "manual" to manual,
                        "has_update" to eval.hasUpdate,
                        "is_forced" to eval.isForced,
                        "target_version" to release.versionName,
                    ),
                    false,
                )
                if (eval.hasUpdate) {
                    _availableBadge.value = release
                    val cachedApk = targetApkFile(release.versionCode)
                    if (cachedApk.isFile && release.sha256.length == 64 &&
                        UpdatePolicy.verifySha256(release.sha256, computeSha256(cachedApk))
                    ) {
                        _state.value = UpdateState.ReadyToInstall(release, cachedApk, isForced = eval.isForced)
                    } else {
                        _state.value = UpdateState.Available(release, isForced = eval.isForced)
                    }
                    if (eval.shouldPrompt) {
                        _showDialog.value = true
                    }
                } else {
                    _availableBadge.value = null
                    _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                }
            }.onFailure { err ->
                if (manual) {
                    _state.value = UpdateState.Error(
                        message = "检查更新失败：${err.message ?: "网络连接异常"}",
                        isForced = _state.value.isForcedUpdate,
                    )
                }
            }
        }
    }

    fun startDownload(release: ReleaseInfo, isForced: Boolean = _state.value.isForcedUpdate) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val finalFile = targetApkFile(release.versionCode)
            val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
            finalFile.parentFile?.mkdirs()

            if (finalFile.isFile && release.sha256.length == 64 &&
                UpdatePolicy.verifySha256(release.sha256, computeSha256(finalFile))
            ) {
                _state.value = UpdateState.ReadyToInstall(release, finalFile, isForced = isForced)
                return@launch
            }

            _state.value = UpdateState.Downloading(release, 0L, release.fileSize, 0L, isForced = isForced)
            try {
                val req = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android)")
                    .build()
                downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("下载失败（HTTP ${resp.code}）")
                    }
                    val body = resp.body ?: error("安装包响应体为空")
                    val total = body.contentLength().takeIf { it > 0L } ?: release.fileSize
                    val digest = MessageDigest.getInstance("SHA-256")

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { out ->
                            val buf = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var lastTickMs = SystemClock.elapsedRealtime()
                            var lastTickBytes = 0L

                            while (isActive) {
                                val read = input.read(buf)
                                if (read < 0) break
                                out.write(buf, 0, read)
                                digest.update(buf, 0, read)
                                downloaded += read

                                val nowMs = SystemClock.elapsedRealtime()
                                val dt = nowMs - lastTickMs
                                if (dt >= 250L) {
                                    val speed = ((downloaded - lastTickBytes) * 1000L) / dt.coerceAtLeast(1L)
                                    lastTickMs = nowMs
                                    lastTickBytes = downloaded
                                    _state.value = UpdateState.Downloading(
                                        release = release,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        bytesPerSecond = speed,
                                        isForced = isForced,
                                    )
                                }
                            }
                            out.flush()
                        }
                    }

                    if (!isActive) {
                        runCatching { partFile.delete() }
                        return@launch
                    }

                    _state.value = UpdateState.Verifying(release, isForced = isForced)
                    val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                    if (release.sha256.length == 64 && !UpdatePolicy.verifySha256(release.sha256, actualHex)) {
                        runCatching { partFile.delete() }
                        onTrackEvent(
                            "update_download_result",
                            mapOf("target_version" to release.versionName, "success" to false, "error_code" to "sha256_mismatch"),
                            true,
                        )
                        _state.value = UpdateState.Error(
                            message = "安装包 SHA-256 完整性校验未通过，已自动删除以策安全，请重试。",
                            release = release,
                            isForced = isForced,
                        )
                        return@launch
                    }

                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    onTrackEvent(
                        "update_download_result",
                        mapOf("target_version" to release.versionName, "success" to true, "is_forced" to isForced),
                        true,
                    )
                    _state.value = UpdateState.ReadyToInstall(release, finalFile, isForced = isForced)
                }
            } catch (e: Throwable) {
                runCatching { partFile.delete() }
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = UpdateState.Available(release, isForced = isForced)
                } else {
                    onTrackEvent(
                        "update_download_result",
                        mapOf("target_version" to release.versionName, "success" to false, "error_code" to "network_error"),
                        false,
                    )
                    _state.value = UpdateState.Error(
                        message = "更新下载中断：${e.message ?: "网络异常"}",
                        release = release,
                        isForced = isForced,
                    )
                }
            }
        }
    }

    fun recordInstallTriggered(release: ReleaseInfo, isForced: Boolean, hadPermission: Boolean) {
        onTrackEvent(
            "update_install_trigger",
            mapOf("target_version" to release.versionName, "is_forced" to isForced, "had_permission" to hadPermission),
            true,
        )
    }

    fun cancelDownload() {
        val cur = _state.value as? UpdateState.Downloading
        downloadJob?.cancel()
        if (cur != null) {
            _state.value = UpdateState.Available(cur.release, isForced = cur.isForced)
        }
    }

    private fun targetApkFile(versionCode: Int): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        return File(File(baseDir, "updates"), "sonify-update-$versionCode.apk")
    }

    private suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
