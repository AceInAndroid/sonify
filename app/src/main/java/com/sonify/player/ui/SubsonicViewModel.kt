package com.sonify.player.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonify.player.HiFiApp
import com.sonify.player.R
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.subsonic.SubsonicAlbum
import com.sonify.player.subsonic.SubsonicArtist
import com.sonify.player.subsonic.SubsonicMusicFolder
import com.sonify.player.subsonic.SubsonicPingResult
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.subsonic.SubsonicSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SubsonicTab(@StringRes val labelRes: Int) {
    NEWEST(R.string.subsonic_sec_newest),
    ALL_ALBUMS(R.string.subsonic_sec_albums),
    ARTISTS(R.string.subsonic_sec_artists),
    PLAYLISTS(R.string.subsonic_sec_playlists),
    STARRED(R.string.subsonic_sec_starred),
    RANDOM(R.string.subsonic_sec_random),
}

sealed interface SubsonicDrillState {
    data object None : SubsonicDrillState
    data class AlbumDetail(
        val album: SubsonicAlbum,
        val tracks: List<TrackEntity> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    ) : SubsonicDrillState

    data class ArtistDetail(
        val artist: SubsonicArtist,
        val albums: List<SubsonicAlbum> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    ) : SubsonicDrillState

    data class PlaylistDetail(
        val playlist: SubsonicPlaylist,
        val tracks: List<TrackEntity> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    ) : SubsonicDrillState
}

data class SubsonicBrowseUiState(
    val serverId: Long? = null,
    val selectedTab: SubsonicTab = SubsonicTab.NEWEST,
    val musicFolders: List<SubsonicMusicFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val albums: List<SubsonicAlbum> = emptyList(),
    val hasMoreAlbums: Boolean = true,
    val isLoadingMoreAlbums: Boolean = false,
    val artists: List<SubsonicArtist> = emptyList(),
    val playlists: List<SubsonicPlaylist> = emptyList(),
    val starredAlbums: List<SubsonicAlbum> = emptyList(),
    val starredTracks: List<TrackEntity> = emptyList(),
    val randomTracks: List<TrackEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // 远程搜索
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResult: SubsonicSearchResult = SubsonicSearchResult(),
    val searchTracks: List<TrackEntity> = emptyList(),
    val searchError: String? = null,
    // 二级钻取详情
    val drill: SubsonicDrillState = SubsonicDrillState.None,
)

data class AccountActionState(
    val isSubmitting: Boolean = false,
    val testingServerId: Long? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

class SubsonicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = HiFiApp.from(application)
    private val repo = app.subsonicRepo

    private fun s(@StringRes id: Int, vararg args: Any): String {
        val ctx = app.localizedContext()
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    val servers: StateFlow<List<SubsonicServerEntity>> = repo.servers
    val activeServer: StateFlow<SubsonicServerEntity?> = repo.activeServer

    private val _browse = MutableStateFlow(SubsonicBrowseUiState())
    val browse: StateFlow<SubsonicBrowseUiState> = _browse.asStateFlow()

    private val _accountAction = MutableStateFlow(AccountActionState())
    val accountAction: StateFlow<AccountActionState> = _accountAction.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var drillJob: Job? = null

    fun ensureLoadedFor(server: SubsonicServerEntity) {
        if (_browse.value.serverId == server.id &&
            (_browse.value.albums.isNotEmpty() || _browse.value.artists.isNotEmpty() || _browse.value.isLoading)
        ) {
            return
        }
        _browse.value = SubsonicBrowseUiState(
            serverId = server.id,
            selectedFolderId = server.musicFolderId,
        )
        loadMusicFolders(server.id)
        loadCurrentTab(server.id, reset = true)
    }

    fun selectTab(tab: SubsonicTab) {
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        if (_browse.value.selectedTab == tab && _browse.value.drill == SubsonicDrillState.None) {
            // 随机发现再次点击刷新一批
            if (tab == SubsonicTab.RANDOM) loadCurrentTab(srvId, reset = true)
            return
        }
        _browse.update { it.copy(selectedTab = tab, drill = SubsonicDrillState.None, error = null) }
        loadCurrentTab(srvId, reset = true)
    }

    fun selectMusicFolder(folderId: String?) {
        val srvId = _browse.value.serverId ?: return
        _browse.update { it.copy(selectedFolderId = folderId, drill = SubsonicDrillState.None) }
        viewModelScope.launch {
            repo.setMusicFolder(srvId, folderId)
        }
        loadCurrentTab(srvId, reset = true)
    }

    fun refresh() {
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        when (val d = _browse.value.drill) {
            is SubsonicDrillState.AlbumDetail -> openAlbum(d.album)
            is SubsonicDrillState.ArtistDetail -> openArtist(d.artist)
            is SubsonicDrillState.PlaylistDetail -> openPlaylist(d.playlist)
            SubsonicDrillState.None -> {
                loadMusicFolders(srvId)
                loadCurrentTab(srvId, reset = true)
            }
        }
    }

    fun loadMoreAlbums() {
        val st = _browse.value
        val srvId = st.serverId ?: return
        if (st.isLoading || st.isLoadingMoreAlbums || !st.hasMoreAlbums) return
        if (st.selectedTab != SubsonicTab.NEWEST && st.selectedTab != SubsonicTab.ALL_ALBUMS) return

        val listType = if (st.selectedTab == SubsonicTab.NEWEST) "newest" else "alphabeticalByName"
        val offset = st.albums.size

        viewModelScope.launch {
            _browse.update { it.copy(isLoadingMoreAlbums = true) }
            runCatching {
                val creds = repo.getCredentials(srvId) ?: error(s(R.string.err_subsonic_no_creds))
                repo.client.getAlbumList2(
                    creds = creds,
                    type = listType,
                    size = PAGE_SIZE,
                    offset = offset,
                    musicFolderId = st.selectedFolderId,
                )
            }.onSuccess { more ->
                _browse.update { cur ->
                    val merged = (cur.albums + more).distinctBy { it.id }
                    cur.copy(
                        albums = merged,
                        hasMoreAlbums = more.size >= PAGE_SIZE,
                        isLoadingMoreAlbums = false,
                    )
                }
            }.onFailure {
                _browse.update { it.copy(isLoadingMoreAlbums = false) }
            }
        }
    }

    private fun loadMusicFolders(serverId: Long) {
        viewModelScope.launch {
            runCatching {
                val creds = repo.getCredentials(serverId) ?: return@launch
                repo.client.getMusicFolders(creds)
            }.onSuccess { folders ->
                _browse.update { cur ->
                    if (cur.serverId == serverId) cur.copy(musicFolders = folders) else cur
                }
            }
        }
    }

    private fun loadCurrentTab(serverId: Long, reset: Boolean) {
        loadJob?.cancel()
        val tab = _browse.value.selectedTab
        val folderId = _browse.value.selectedFolderId

        loadJob = viewModelScope.launch {
            _browse.update {
                it.copy(
                    serverId = serverId,
                    isLoading = true,
                    error = null,
                    albums = if (reset && (tab == SubsonicTab.NEWEST || tab == SubsonicTab.ALL_ALBUMS)) emptyList() else it.albums,
                    hasMoreAlbums = true,
                )
            }
            runCatching {
                val creds = repo.getCredentials(serverId) ?: throw IllegalStateException(s(R.string.err_subsonic_creds_invalid))
                when (tab) {
                    SubsonicTab.NEWEST -> {
                        val albums = repo.client.getAlbumList2(creds, "newest", PAGE_SIZE, 0, folderId)
                        _browse.update {
                            it.copy(albums = albums, hasMoreAlbums = albums.size >= PAGE_SIZE, isLoading = false)
                        }
                    }

                    SubsonicTab.ALL_ALBUMS -> {
                        val albums = repo.client.getAlbumList2(creds, "alphabeticalByName", PAGE_SIZE, 0, folderId)
                        _browse.update {
                            it.copy(albums = albums, hasMoreAlbums = albums.size >= PAGE_SIZE, isLoading = false)
                        }
                    }

                    SubsonicTab.ARTISTS -> {
                        val artists = repo.client.getArtists(creds, folderId)
                        _browse.update { it.copy(artists = artists, isLoading = false) }
                    }

                    SubsonicTab.PLAYLISTS -> {
                        val playlists = repo.client.getPlaylists(creds)
                        _browse.update { it.copy(playlists = playlists, isLoading = false) }
                    }

                    SubsonicTab.STARRED -> {
                        val (albums, songs) = repo.client.getStarred2(creds, folderId)
                        val tracks = repo.cacheSongs(serverId, songs)
                        _browse.update {
                            it.copy(starredAlbums = albums, starredTracks = tracks, isLoading = false)
                        }
                    }

                    SubsonicTab.RANDOM -> {
                        val songs = repo.client.getRandomSongs(creds, size = 40, musicFolderId = folderId)
                        val tracks = repo.cacheSongs(serverId, songs)
                        _browse.update { it.copy(randomTracks = tracks, isLoading = false) }
                    }
                }
            }.onFailure { err ->
                _browse.update {
                    it.copy(isLoading = false, error = err.message ?: s(R.string.err_subsonic_load_lib))
                }
            }
        }
    }

    fun openAlbum(album: SubsonicAlbum) {
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        drillJob?.cancel()
        _browse.update { it.copy(drill = SubsonicDrillState.AlbumDetail(album, isLoading = true)) }
        drillJob = viewModelScope.launch {
            runCatching {
                val creds = repo.getCredentials(srvId) ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
                val (detail, songs) = repo.client.getAlbum(creds, album.id)
                val tracks = repo.cacheSongs(srvId, songs)
                detail to tracks
            }.onSuccess { (detail, tracks) ->
                _browse.update {
                    it.copy(drill = SubsonicDrillState.AlbumDetail(detail, tracks, isLoading = false))
                }
            }.onFailure { err ->
                _browse.update {
                    it.copy(
                        drill = SubsonicDrillState.AlbumDetail(
                            album = album,
                            isLoading = false,
                            error = err.message ?: s(R.string.err_subsonic_load_album),
                        )
                    )
                }
            }
        }
    }

    fun openArtist(artist: SubsonicArtist) {
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        drillJob?.cancel()
        _browse.update { it.copy(drill = SubsonicDrillState.ArtistDetail(artist, isLoading = true)) }
        drillJob = viewModelScope.launch {
            runCatching {
                val creds = repo.getCredentials(srvId) ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
                repo.client.getArtist(creds, artist.id)
            }.onSuccess { (detail, albums) ->
                _browse.update {
                    it.copy(drill = SubsonicDrillState.ArtistDetail(detail, albums, isLoading = false))
                }
            }.onFailure { err ->
                _browse.update {
                    it.copy(
                        drill = SubsonicDrillState.ArtistDetail(
                            artist = artist,
                            isLoading = false,
                            error = err.message ?: s(R.string.err_subsonic_load_artist),
                        )
                    )
                }
            }
        }
    }

    fun openPlaylist(playlist: SubsonicPlaylist) {
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        drillJob?.cancel()
        _browse.update { it.copy(drill = SubsonicDrillState.PlaylistDetail(playlist, isLoading = true)) }
        drillJob = viewModelScope.launch {
            runCatching {
                val creds = repo.getCredentials(srvId) ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
                val (detail, songs) = repo.client.getPlaylist(creds, playlist.id)
                val tracks = repo.cacheSongs(srvId, songs)
                detail to tracks
            }.onSuccess { (detail, tracks) ->
                _browse.update {
                    it.copy(drill = SubsonicDrillState.PlaylistDetail(detail, tracks, isLoading = false))
                }
            }.onFailure { err ->
                _browse.update {
                    it.copy(
                        drill = SubsonicDrillState.PlaylistDetail(
                            playlist = playlist,
                            isLoading = false,
                            error = err.message ?: s(R.string.err_subsonic_load_playlist),
                        )
                    )
                }
            }
        }
    }

    fun closeDrill() {
        drillJob?.cancel()
        _browse.update { it.copy(drill = SubsonicDrillState.None) }
    }

    fun setSearchActive(active: Boolean) {
        if (!active) {
            searchJob?.cancel()
            _browse.update {
                it.copy(
                    searchActive = false,
                    searchQuery = "",
                    isSearching = false,
                    searchResult = SubsonicSearchResult(),
                    searchTracks = emptyList(),
                    searchError = null,
                )
            }
        } else {
            _browse.update { it.copy(searchActive = true) }
        }
    }

    fun updateSearchQuery(query: String) {
        _browse.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _browse.update {
                it.copy(isSearching = false, searchResult = SubsonicSearchResult(), searchTracks = emptyList())
            }
            return
        }
        val srvId = _browse.value.serverId ?: activeServer.value?.id ?: return
        searchJob = viewModelScope.launch {
            delay(350)
            _browse.update { it.copy(isSearching = true, searchError = null) }
            runCatching {
                val creds = repo.getCredentials(srvId) ?: throw IllegalStateException(s(R.string.err_subsonic_no_creds))
                val res = repo.client.search3(creds, trimmed, musicFolderId = _browse.value.selectedFolderId)
                val tracks = repo.cacheSongs(srvId, res.songs)
                res to tracks
            }.onSuccess { (res, tracks) ->
                _browse.update {
                    it.copy(isSearching = false, searchResult = res, searchTracks = tracks)
                }
            }.onFailure { err ->
                _browse.update {
                    it.copy(isSearching = false, searchError = err.message ?: s(R.string.err_subsonic_search))
                }
            }
        }
    }

    // ==================== 账号管理操作 ====================

    fun clearAccountMessage() {
        _accountAction.update { it.copy(statusMessage = null, isError = false) }
    }

    fun loginAndSaveServer(
        editingId: Long?,
        displayName: String,
        baseUrl: String,
        username: String,
        password: String,
        useLegacyAuth: Boolean,
        allowSelfSignedTls: Boolean,
        onSuccess: (SubsonicServerEntity, SubsonicPingResult) -> Unit,
    ) {
        viewModelScope.launch {
            _accountAction.value = AccountActionState(isSubmitting = true)
            runCatching {
                repo.loginAndSaveServer(
                    editingId = editingId,
                    displayName = displayName,
                    baseUrl = baseUrl,
                    username = username,
                    rawPassword = password,
                    useLegacyAuth = useLegacyAuth,
                    allowSelfSignedTls = allowSelfSignedTls,
                    activate = true,
                )
            }.onSuccess { (entity, ping) ->
                _accountAction.value = AccountActionState(
                    isSubmitting = false,
                    statusMessage = s(R.string.toast_subsonic_saved, ping.displayBadge()),
                    isError = false,
                )
                ensureLoadedFor(entity)
                onSuccess(entity, ping)
            }.onFailure { err ->
                _accountAction.value = AccountActionState(
                    isSubmitting = false,
                    statusMessage = err.message ?: s(R.string.err_subsonic_connect),
                    isError = true,
                )
            }
        }
    }

    fun testExistingServer(serverId: Long) {
        viewModelScope.launch {
            _accountAction.value = AccountActionState(testingServerId = serverId)
            runCatching {
                repo.testExistingServer(serverId)
            }.onSuccess { ping ->
                _accountAction.value = AccountActionState(
                    statusMessage = s(R.string.toast_subsonic_ok, ping.displayBadge()),
                    isError = false,
                )
            }.onFailure { err ->
                _accountAction.value = AccountActionState(
                    statusMessage = err.message ?: s(R.string.err_subsonic_test),
                    isError = true,
                )
            }
        }
    }

    fun switchActiveServer(server: SubsonicServerEntity) {
        viewModelScope.launch {
            repo.setActiveServer(server.id)
            ensureLoadedFor(server)
        }
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            repo.deleteServer(serverId)
            if (_browse.value.serverId == serverId) {
                _browse.value = SubsonicBrowseUiState()
            }
            _accountAction.value = AccountActionState(statusMessage = s(R.string.toast_subsonic_removed), isError = false)
        }
    }

    companion object {
        private const val PAGE_SIZE = 40
    }
}
