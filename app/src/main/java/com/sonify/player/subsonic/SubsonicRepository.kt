package com.sonify.player.subsonic

import android.content.Context
import android.util.Log
import com.sonify.player.data.LibraryDatabase
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.SubsonicTrackCacheEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.data.UserPreferences
import com.sonify.player.ui.theme.CoverArtSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Subsonic 多账号管理、凭据加解密缓存与远程媒体库统一仓库。
 */
class SubsonicRepository(
    private val context: Context,
    private val db: LibraryDatabase,
    private val prefs: UserPreferences,
    val client: SubsonicClient = SubsonicClient(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverDao = db.subsonicServerDao()
    private val trackCacheDao = db.subsonicTrackCacheDao()

    private val credCache = ConcurrentHashMap<Long, SubsonicCredentials>()

    val servers: StateFlow<List<SubsonicServerEntity>> = serverDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeServer: StateFlow<SubsonicServerEntity?> = servers
        .map { list -> list.firstOrNull { it.isActive } ?: list.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val cachedTracks: StateFlow<List<TrackEntity>> = trackCacheDao.observeAll()
        .map { list -> list.map { it.toTrackEntity() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
        useLegacyAuth: Boolean,
        allowSelfSignedTls: Boolean,
    ): SubsonicPingResult {
        val normalized = SubsonicAuth.normalizeBaseUrl(baseUrl)
        val creds = SubsonicCredentials(
            baseUrl = normalized,
            username = username.trim(),
            password = password,
            useLegacyAuth = useLegacyAuth,
            allowSelfSignedTls = allowSelfSignedTls,
        )
        return client.ping(creds)
    }

    /**
     * 登录/编辑服务器账号：
     * - 先通过 ping.view 校验地址与用户名密码；
     * - 校验成功后使用 Android Keystore AES-256-GCM 加密密码并落库；
     * - 编辑已有账号且密码留空时自动沿用原已保存的密文，无需重复输入。
     */
    suspend fun loginAndSaveServer(
        editingId: Long? = null,
        displayName: String,
        baseUrl: String,
        username: String,
        rawPassword: String,
        useLegacyAuth: Boolean,
        allowSelfSignedTls: Boolean,
        activate: Boolean = true,
    ): Pair<SubsonicServerEntity, SubsonicPingResult> = withContext(Dispatchers.IO) {
        val normalizedUrl = SubsonicAuth.normalizeBaseUrl(baseUrl)
        val cleanUser = username.trim()
        require(cleanUser.isNotEmpty()) { "用户名不能为空" }

        val existing = editingId?.let { serverDao.findById(it) }
        val plainPassword = if (rawPassword.isNotEmpty()) {
            rawPassword
        } else if (existing != null) {
            CredentialCipher.decrypt(existing.encryptedPassword)
        } else {
            throw IllegalArgumentException("密码不能为空")
        }

        val probeCreds = SubsonicCredentials(
            serverId = existing?.id ?: 0L,
            baseUrl = normalizedUrl,
            username = cleanUser,
            password = plainPassword,
            useLegacyAuth = useLegacyAuth,
            allowSelfSignedTls = allowSelfSignedTls,
        )
        val ping = client.ping(probeCreds)

        val encrypted = if (rawPassword.isNotEmpty() || existing == null) {
            CredentialCipher.encrypt(plainPassword)
        } else {
            existing.encryptedPassword
        }

        val fallbackName = runCatching {
            val hostName = normalizedUrl.toHttpUrlOrNull()?.host ?: normalizedUrl
            if (ping.serverType.isNotBlank()) {
                "${ping.serverType.replaceFirstChar { it.uppercase() }} ($hostName)"
            } else hostName
        }.getOrDefault(normalizedUrl)

        val finalName = displayName.trim().ifBlank { fallbackName }
        val now = System.currentTimeMillis()

        val entity = SubsonicServerEntity(
            id = existing?.id ?: 0L,
            name = finalName,
            baseUrl = normalizedUrl,
            username = cleanUser,
            encryptedPassword = encrypted,
            useLegacyAuth = useLegacyAuth,
            allowSelfSignedTls = allowSelfSignedTls,
            musicFolderId = existing?.musicFolderId,
            serverType = ping.serverType,
            serverVersion = ping.displayBadge(),
            isActive = activate || existing?.isActive == true,
            lastConnectedAt = now,
            createdAt = existing?.createdAt ?: now,
        )

        val savedId = serverDao.upsert(entity)
        val resolvedId = if (entity.id > 0L) entity.id else savedId
        if (activate) {
            serverDao.setActive(resolvedId)
            prefs.setLastLibrarySource(sourceKeyFor(resolvedId))
        }

        val finalEntity = entity.copy(id = resolvedId, isActive = activate || entity.isActive)
        credCache[resolvedId] = probeCreds.copy(serverId = resolvedId)
        Log.i(TAG, "已保存 Subsonic 服务器账号 id=$resolvedId name=$finalName type=${ping.serverType}")
        finalEntity to ping
    }

    suspend fun testExistingServer(serverId: Long): SubsonicPingResult = withContext(Dispatchers.IO) {
        val creds = getCredentials(serverId) ?: throw IllegalStateException("找不到已保存的服务器凭据")
        val ping = client.ping(creds)
        serverDao.updateConnectionInfo(
            serverId = serverId,
            ts = System.currentTimeMillis(),
            type = ping.serverType,
            version = ping.displayBadge(),
        )
        ping
    }

    suspend fun setActiveServer(serverId: Long) = withContext(Dispatchers.IO) {
        serverDao.setActive(serverId)
        prefs.setLastLibrarySource(sourceKeyFor(serverId))
    }

    suspend fun setMusicFolder(serverId: Long, folderId: String?) = withContext(Dispatchers.IO) {
        serverDao.setMusicFolder(serverId, folderId)
    }

    suspend fun deleteServer(serverId: Long) = withContext(Dispatchers.IO) {
        val wasActive = serverDao.findById(serverId)?.isActive == true
        credCache.remove(serverId)
        trackCacheDao.deleteByServer(serverId)
        serverDao.deleteById(serverId)
        CoverArtSupport.clearServerCoverCache(context, serverId)
        if (wasActive) {
            val next = serverDao.getAll().firstOrNull()
            if (next != null) {
                serverDao.setActive(next.id)
                prefs.setLastLibrarySource(sourceKeyFor(next.id))
            } else {
                prefs.setLastLibrarySource(LOCAL_SOURCE_KEY)
            }
        }
    }

    fun getCredentialsSync(serverId: Long): SubsonicCredentials? {
        credCache[serverId]?.let { return it }
        return null
    }

    suspend fun getCredentials(serverId: Long): SubsonicCredentials? = withContext(Dispatchers.IO) {
        credCache[serverId]?.let { return@withContext it }
        val entity = serverDao.findById(serverId) ?: return@withContext null
        val plain = runCatching { CredentialCipher.decrypt(entity.encryptedPassword) }
            .onFailure { Log.e(TAG, "解密服务器 id=$serverId 密码失败: ${it.message}") }
            .getOrNull() ?: return@withContext null
        SubsonicCredentials(
            serverId = entity.id,
            baseUrl = entity.baseUrl,
            username = entity.username,
            password = plain,
            useLegacyAuth = entity.useLegacyAuth,
            allowSelfSignedTls = entity.allowSelfSignedTls,
        ).also { credCache[serverId] = it }
    }

    /** 预热全部已保存账号的解密凭据，避免播放器切歌或加载封面时阻塞等待 */
    suspend fun preloadCredentials() = withContext(Dispatchers.IO) {
        for (srv in serverDao.getAll()) {
            getCredentials(srv.id)
        }
    }

    suspend fun cacheSongs(serverId: Long, songs: List<SubsonicSong>): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext emptyList()
            val entities = songs.map { it.toCacheEntity(serverId) }
            trackCacheDao.upsertAll(entities)
            entities.map { it.toTrackEntity() }
        }

    suspend fun findCachedTrack(path: String): TrackEntity? = withContext(Dispatchers.IO) {
        trackCacheDao.findByPath(path)?.toTrackEntity()
    }

    suspend fun findResumptionQueue(trackPath: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val hit = trackCacheDao.findByPath(trackPath) ?: return@withContext emptyList()
        val albumTracks = if (hit.albumId.isNotBlank()) {
            trackCacheDao.findByAlbum(hit.serverId, hit.albumId)
        } else emptyList()
        val list = if (albumTracks.isNotEmpty()) albumTracks else trackCacheDao.findRecentByServer(hit.serverId)
        list.map { it.toTrackEntity() }
    }

    suspend fun toggleRemoteFavorite(trackPath: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        trackCacheDao.setFavorite(trackPath, favorite)
        val ref = SubsonicUri.parseTrackUri(trackPath) ?: return@withContext false
        val creds = getCredentials(ref.serverId) ?: return@withContext false
        runCatching {
            client.setStarred(creds, ref.songId, favorite)
            true
        }.onFailure {
            Log.w(TAG, "同步远端收藏失败: ${it.message}")
        }.getOrDefault(false)
    }

    suspend fun scrobbleIfEnabled(trackPath: String, submission: Boolean) = withContext(Dispatchers.IO) {
        if (!prefs.current.subsonicScrobbleEnabled) return@withContext
        val ref = SubsonicUri.parseTrackUri(trackPath) ?: return@withContext
        val creds = getCredentials(ref.serverId) ?: return@withContext
        runCatching {
            client.scrobble(creds, ref.songId, submission)
        }.onFailure {
            Log.w(TAG, "Subsonic scrobble 失败 (submission=$submission): ${it.message}")
        }
    }

    companion object {
        private const val TAG = "SubsonicRepo"
        const val LOCAL_SOURCE_KEY = "local"

        fun sourceKeyFor(serverId: Long): String = "server:$serverId"
        fun parseServerIdFromSourceKey(key: String): Long? =
            key.removePrefix("server:").takeIf { key.startsWith("server:") }?.toLongOrNull()
    }
}
