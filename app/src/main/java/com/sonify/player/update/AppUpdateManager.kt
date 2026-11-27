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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Sonify 应用内更新管理器：
 * - 默认对接 https://sonify.zhangbing.tech/api/v1/update/latest；
 * - 支持自动节流静默检查、手动检查、跳过版本、流式进度下载（含实时速度与取消）与强制 SHA-256 校验；
 * - 若目标版本 APK 已完整下载并通过 SHA-256 校验，秒进 ReadyToInstall 不重复耗流量。
 */
class AppUpdateManager(
    private val context: Context,
    private val prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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

    fun activeBaseUrl(): String {
        if (BuildConfig.DEBUG) {
            val custom = prefs.current.customUpdateBaseUrl.trim().trimEnd('/')
            if (custom.startsWith("http://") || custom.startsWith("https://")) return custom
        }
        return BuildConfig.UPDATE_BASE_URL.trimEnd('/')
    }

    fun dismissDialog() {
        val cur = _state.value
        if (cur is UpdateState.Available && cur.isForced) return
        _showDialog.value = false
    }

    fun openDialogIfAvailable() {
        val badge = _availableBadge.value
        if (badge != null) {
            val eval = UpdatePolicy.evaluate(
                currentVersionCode = BuildConfig.VERSION_CODE,
                release = badge,
                ignoredVersionCode = prefs.current.ignoredUpdateVersionCode,
                isManualCheck = true,
            )
            if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.ReadyToInstall) {
                _state.value = UpdateState.Available(badge, eval.isForced)
            }
            _showDialog.value = true
        } else {
            checkForUpdates(manual = true)
        }
    }

    fun ignoreVersion(versionCode: Int) {
        scope.launch {
            prefs.setIgnoredUpdateVersionCode(versionCode)
            _showDialog.value = false
        }
    }

    fun checkForUpdates(manual: Boolean) {
        val settings = prefs.current
        val now = System.currentTimeMillis()
        if (!manual && !UpdatePolicy.shouldRunAutoCheck(settings.autoCheckUpdate, settings.lastUpdateCheckAt, now)) {
            return
        }
        if (_state.value is UpdateState.Downloading) {
            if (manual) _showDialog.value = true
            return
        }

        scope.launch {
            if (manual) {
                _state.value = UpdateState.Checking
                _showDialog.value = true
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "arm64-v8a" }
            val url = "${activeBaseUrl()}/api/v1/update/latest?channel=stable&abi=$abi&versionCode=${BuildConfig.VERSION_CODE}"
            val result = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("更新服务返回 HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    ReleaseInfo.fromJson(body) ?: error("服务端返回的版本清单格式无效")
                }
            }

            prefs.setLastUpdateCheckAt(System.currentTimeMillis())

            result.onSuccess { release ->
                val eval = UpdatePolicy.evaluate(
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    release = release,
                    ignoredVersionCode = prefs.current.ignoredUpdateVersionCode,
                    isManualCheck = manual,
                )
                if (eval.hasUpdate) {
                    _availableBadge.value = release
                    val cachedApk = targetApkFile(release.versionCode)
                    if (cachedApk.isFile && release.sha256.length == 64 &&
                        UpdatePolicy.verifySha256(release.sha256, computeSha256(cachedApk))
                    ) {
                        _state.value = UpdateState.ReadyToInstall(release, cachedApk)
                    } else {
                        _state.value = UpdateState.Available(release, eval.isForced)
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
                    _state.value = UpdateState.Error("检查更新失败：${err.message ?: "网络连接异常"}")
                }
            }
        }
    }

    fun startDownload(release: ReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val finalFile = targetApkFile(release.versionCode)
            val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
            finalFile.parentFile?.mkdirs()

            // 已完整缓存且校验通过时免重复下载
            if (finalFile.isFile && release.sha256.length == 64 &&
                UpdatePolicy.verifySha256(release.sha256, computeSha256(finalFile))
            ) {
                _state.value = UpdateState.ReadyToInstall(release, finalFile)
                return@launch
            }

            _state.value = UpdateState.Downloading(release, 0L, release.fileSize, 0L)
            try {
                val req = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "Sonify/${BuildConfig.VERSION_NAME} (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("下载安装包失败（HTTP ${resp.code}）")
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
                                    _state.value = UpdateState.Downloading(release, downloaded, total, speed)
                                }
                            }
                            if (!isActive) {
                                runCatching { partFile.delete() }
                                _state.value = UpdateState.Available(release, release.forceUpdate)
                                return@launch
                            }
                            out.flush()
                        }
                    }

                    _state.value = UpdateState.Verifying(release)
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (release.sha256.isNotBlank() && !UpdatePolicy.verifySha256(release.sha256, actualSha)) {
                        runCatching { partFile.delete() }
                        _state.value = UpdateState.Error(
                            message = "安装包 SHA-256 完整性校验失败，已自动清理受损文件，请重试",
                            release = release,
                        )
                        return@launch
                    }

                    if (finalFile.exists()) finalFile.delete()
                    check(partFile.renameTo(finalFile)) { "保存更新包失败" }
                    pruneOldApks(finalFile)
                    _state.value = UpdateState.ReadyToInstall(release, finalFile)
                }
            } catch (t: Throwable) {
                runCatching { partFile.delete() }
                if (isActive) {
                    _state.value = UpdateState.Error(
                        message = "下载失败：${t.message ?: "网络中断"}",
                        release = release,
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val cur = _state.value
        if (cur is UpdateState.Downloading) {
            _state.value = UpdateState.Available(cur.release, cur.release.forceUpdate)
        }
    }

    private fun targetApkFile(versionCode: Int): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        dir.mkdirs()
        return File(dir, "sonify_v${versionCode}.apk")
    }

    private fun pruneOldApks(keepFile: File) {
        val dir = keepFile.parentFile ?: return
        dir.listFiles()?.forEach { f ->
            if (f.absolutePath != keepFile.absolutePath) runCatching { f.delete() }
        }
    }

    private suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
