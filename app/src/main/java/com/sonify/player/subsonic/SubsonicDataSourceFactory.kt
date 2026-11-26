package com.sonify.player.subsonic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sonify.player.data.StreamQuality
import com.sonify.player.data.UserPreferences
import com.sonify.player.download.SonifyDownloadManager
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 统一音频数据源工厂：
 * - 对本地 file:// / content:// 零开销透传给 DefaultDataSource；
 * - 对远程 subsonic://<serverId>/<songId> 优先检测本地已下载离线原盘文件（零流量直通 Bit-Perfect）；
 * - 若未下载则在 open() 时动态签发带随机盐值的 stream.view URL，
 *   按当前网络（Wi-Fi / 蜂窝）选取原盘无损直通（format=raw）或转码档位，
 *   并路由至对应服务器的隔离 OkHttpClient（保障自签名 TLS 仅对单服务器生效）。
 */
@OptIn(UnstableApi::class)
class SubsonicDataSourceFactory(
    private val context: Context,
    private val repo: SubsonicRepository,
    private val prefs: UserPreferences,
    private val downloadManager: SonifyDownloadManager,
) : DataSource.Factory {

    private val localFactory = DefaultDataSource.Factory(context)
    private val standardHttpFactory = OkHttpDataSource.Factory(repo.client.standardHttpClient)
        .setUserAgent("Sonify-Android/0.2")
    private val selfSignedHttpFactory by lazy {
        OkHttpDataSource.Factory(repo.client.selfSignedHttpClient)
            .setUserAgent("Sonify-Android/0.2")
    }

    override fun createDataSource(): DataSource = SubsonicRoutingDataSource(
        context = context,
        repo = repo,
        prefs = prefs,
        downloadManager = downloadManager,
        localFactory = localFactory,
        standardHttpFactory = standardHttpFactory,
        selfSignedHttpFactoryProvider = { selfSignedHttpFactory },
    )
}

@OptIn(UnstableApi::class)
private class SubsonicRoutingDataSource(
    private val context: Context,
    private val repo: SubsonicRepository,
    private val prefs: UserPreferences,
    private val downloadManager: SonifyDownloadManager,
    private val localFactory: DataSource.Factory,
    private val standardHttpFactory: OkHttpDataSource.Factory,
    private val selfSignedHttpFactoryProvider: () -> OkHttpDataSource.Factory,
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null
    private var activeUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val rawUri = dataSpec.uri
        if (rawUri.scheme != SubsonicUri.TRACK_SCHEME) {
            val local = localFactory.createDataSource().also { ds ->
                listeners.forEach { ds.addTransferListener(it) }
            }
            active = local
            activeUri = rawUri
            return local.open(dataSpec)
        }

        val ref = SubsonicUri.parseTrackUri(rawUri.toString())
            ?: throw IOException("无效的 Subsonic 曲目 URI")

        // 优先命中本地已下载的离线文件：零流量、断网可播、100% Bit-Perfect 直通 USB DAC
        val offlineFile = downloadManager.findLocalFileSync(rawUri.toString())
        if (offlineFile != null) {
            val localUri = Uri.fromFile(offlineFile)
            val local = localFactory.createDataSource().also { ds ->
                listeners.forEach { ds.addTransferListener(it) }
            }
            Log.i(TAG, "命中本地离线文件播放: ${offlineFile.name}")
            active = local
            activeUri = localUri
            return local.open(dataSpec.buildUpon().setUri(localUri).build())
        }

        val creds = repo.getCredentialsSync(ref.serverId)
            ?: runBlocking { repo.getCredentials(ref.serverId) }
            ?: throw IOException("未找到对应的 Subsonic 服务器账号（可能已被移除）")

        val quality = selectQuality()
        val signedUrl = SubsonicAuth.buildStreamUrl(creds, ref.songId, quality)
        val resolvedUri = Uri.parse(signedUrl.toString())

        val httpFactory = if (creds.allowSelfSignedTls) {
            selfSignedHttpFactoryProvider()
        } else {
            standardHttpFactory
        }
        val upstream = httpFactory.createDataSource().also { ds ->
            listeners.forEach { ds.addTransferListener(it) }
        }

        val resolvedSpec = dataSpec.buildUpon()
            .setUri(resolvedUri)
            .build()

        Log.i(TAG, "打开 Subsonic 音频流 serverId=${ref.serverId} songId=${ref.songId} quality=${quality.name}")
        active = upstream
        activeUri = resolvedUri
        return upstream.open(resolvedSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val ds = active ?: return C.RESULT_END_OF_INPUT
        return ds.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri ?: activeUri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        val ds = active
        active = null
        activeUri = null
        ds?.close()
    }

    private fun selectQuality(): StreamQuality {
        val settings = prefs.current
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val isUnmeteredOrWifi = caps == null ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return if (isUnmeteredOrWifi) settings.subsonicWifiQuality else settings.subsonicCellularQuality
    }

    companion object {
        private const val TAG = "SubsonicStreamDS"
    }
}
