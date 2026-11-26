package com.sonify.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sonify.player.data.PlaylistEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSorter
import com.sonify.player.library.TrackSortField
import com.sonify.player.playback.HiFiPlaybackService
import com.sonify.player.subsonic.SubsonicPlaylist
import com.sonify.player.subsonic.SubsonicRepository
import com.sonify.player.subsonic.SubsonicUri
import com.sonify.player.ui.LibraryViewModel
import com.sonify.player.ui.PlayerViewModel
import com.sonify.player.ui.SubsonicDrillState
import com.sonify.player.ui.SubsonicViewModel
import com.sonify.player.ui.components.AddToPlaylistSheet
import com.sonify.player.ui.components.AddTracksPickerSheet
import com.sonify.player.ui.components.DownloadQueueSheet
import com.sonify.player.ui.components.MiniPlayerBar
import com.sonify.player.ui.components.OutputDeviceSheet
import com.sonify.player.ui.components.PlaylistEditorDialog
import com.sonify.player.ui.components.SubsonicAccountsSheet
import com.sonify.player.ui.components.TrackActionSheet
import com.sonify.player.ui.components.VolumeHudOverlay
import com.sonify.player.ui.components.WifiTransferSheet
import com.sonify.player.ui.screens.LibraryScreen
import com.sonify.player.ui.screens.NowPlayingScreen
import com.sonify.player.ui.screens.PlaylistDetailScreen
import com.sonify.player.ui.screens.SettingsScreen
import com.sonify.player.ui.screens.UsbDeviceScreen
import com.sonify.player.ui.theme.HiFiPlayerTheme
import com.sonify.player.ui.theme.ImmersiveBarRule
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.SystemBarsController
import com.sonify.player.ui.theme.SystemBarsSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    LIBRARY("音乐库", Icons.Rounded.LibraryMusic),
    USB("USB 设备", Icons.Rounded.Usb),
    SETTINGS("设置", Icons.Rounded.Settings),
}

private sealed interface PlaylistEditorMode {
    data class Create(
        val defaultRemote: Boolean,
        val initialTracks: List<TrackEntity> = emptyList(),
    ) : PlaylistEditorMode

    data class EditLocal(
        val playlist: PlaylistEntity,
        val autoCovers: List<String>,
    ) : PlaylistEditorMode

    data class EditRemote(
        val serverId: Long,
        val playlist: SubsonicPlaylist,
    ) : PlaylistEditorMode
}

private sealed interface AddPickerTarget {
    data class Local(val playlist: PlaylistEntity, val existingPaths: Set<String>) : AddPickerTarget
    data class Remote(val serverId: Long, val playlist: SubsonicPlaylist, val existingPaths: Set<String>) : AddPickerTarget
}

private data class TrackMenuContext(
    val track: TrackEntity,
    val localPlaylistId: Long? = null,
    val remotePlaylistRemove: (() -> Unit)? = null,
)

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val subsonicVm: SubsonicViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()
    private val openPlayerTrigger = mutableStateOf(0L)
    private var latestBarsSpec = SystemBarsSpec()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBarsController.enableEdgeToEdge(window)
        if (intent?.getBooleanExtra(HiFiPlaybackService.EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openPlayerTrigger.value = System.currentTimeMillis()
        }
        setContent {
            val settings by vm.settings.collectAsState()
            HiFiPlayerTheme(
                canvasStyle = settings.darkCanvasStyle,
                accentStyle = settings.accentStyle,
            ) {
                Surface(
                    color = LocalHiFiColors.current.canvas,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HiFiRootScreen(
                        vm = vm,
                        subsonicVm = subsonicVm,
                        libraryVm = libraryVm,
                        openPlayerTrigger = openPlayerTrigger.value,
                        onApplySystemBars = { spec ->
                            latestBarsSpec = spec
                            SystemBarsController.apply(window, spec)
                        },
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            SystemBarsController.apply(window, latestBarsSpec)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(HiFiPlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
            openPlayerTrigger.value = System.currentTimeMillis()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val app = HiFiApp.from(this)
            if (app.usbManager.isConnected && app.prefs.current.usbExclusiveEnabled) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                        app.volumeController.stepUp(showHud = true)
                    } else {
                        app.volumeController.stepDown(showHud = true)
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun HiFiRootScreen(
    vm: PlayerViewModel,
    subsonicVm: SubsonicViewModel,
    libraryVm: LibraryViewModel,
    openPlayerTrigger: Long,
    onApplySystemBars: (SystemBarsSpec) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { HiFiApp.from(context) }
    val scope = rememberCoroutineScope()
    val colors = LocalHiFiColors.current

    val settings by vm.settings.collectAsState()
    val usbState by vm.usbState.collectAsState()
    val volumeState by vm.volumeState.collectAsState()
    val scanState by vm.scanState.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val remoteCachedTracks by vm.remoteCachedTracks.collectAsState()
    val playback by vm.playback.collectAsState()
    val activeRoute by vm.activeRoute.collectAsState()
    val audioFocusState by vm.audioFocusState.collectAsState()

    val subsonicServers by subsonicVm.servers.collectAsState()
    val activeSubsonicServer by subsonicVm.activeServer.collectAsState()
    val subsonicBrowse by subsonicVm.browse.collectAsState()
    val subsonicAction by subsonicVm.accountAction.collectAsState()

    val localPlaylists by libraryVm.playlists.collectAsState()
    val downloadedList by libraryVm.downloadedList.collectAsState()
    val downloadsByPath by libraryVm.downloadsByPath.collectAsState()
    val downloadQueue by libraryVm.downloadQueue.collectAsState()
    val toastMessage by libraryVm.toastMessage.collectAsState()
    val wifiBlockedTracks by libraryVm.wifiOnlyBlockedTracks.collectAsState()

    val wifiTransferState by app.wifiTransferManager.state.collectAsState()
    val wifiUploadHistory by app.wifiTransferManager.history.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.LIBRARY) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var showSubsonicSheet by remember { mutableStateOf(false) }
    var subsonicSheetInitialAdd by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showWifiTransferSheet by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }

    // 歌单 & 歌曲操作状态
    var openLocalPlaylistId by remember { mutableStateOf<Long?>(null) }
    var playlistEditorMode by remember { mutableStateOf<PlaylistEditorMode?>(null) }
    var pendingAddTracks by remember { mutableStateOf<List<TrackEntity>?>(null) }
    var trackMenuCtx by remember { mutableStateOf<TrackMenuContext?>(null) }
    var addPickerTarget by remember { mutableStateOf<AddPickerTarget?>(null) }

    // 音乐库当前数据源
    val selectedServerId = SubsonicRepository.parseServerIdFromSourceKey(settings.lastLibrarySource)
    val selectedSubsonicServer = remember(selectedServerId, subsonicServers) {
        selectedServerId?.let { id -> subsonicServers.firstOrNull { it.id == id } }
    }
    LaunchedEffect(selectedSubsonicServer?.id) {
        selectedSubsonicServer?.let { subsonicVm.ensureLoadedFor(it) }
    }

    val isPlayerSurface = showNowPlaying && playback.currentTrack != null
    val barsSpec = remember(settings, isPlayerSurface, playback.isPlaying) {
        ImmersiveBarRule.resolve(
            settings = settings,
            isPlayerSurface = isPlayerSurface,
            isPlaying = playback.isPlaying,
        )
    }
    LaunchedEffect(barsSpec) {
        onApplySystemBars(barsSpec)
    }

    LaunchedEffect(openPlayerTrigger) {
        if (openPlayerTrigger > 0L) {
            showNowPlaying = true
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePermission = checkStoragePermission(context)
        if (hasStoragePermission) vm.startScan()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch { app.scanner.importUris(uris) }
        }
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }
                .onFailure { permLauncher.launch(requiredRuntimePermissions()) }
        } else {
            permLauncher.launch(requiredRuntimePermissions())
        }
    }

    LifecycleResumeEffect(Unit) {
        val grantedNow = checkStoragePermission(context)
        hasStoragePermission = grantedNow
        app.usbManager.refreshDevices()
        if (grantedNow && tracks.isEmpty() && !scanState.isScanning) {
            vm.startScan()
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && tracks.isEmpty() && !scanState.isScanning) {
            vm.startScan()
        }
    }

    BackHandler(enabled = showNowPlaying) {
        showNowPlaying = false
    }
    BackHandler(enabled = !showNowPlaying && currentTab == MainTab.LIBRARY && openLocalPlaylistId != null) {
        openLocalPlaylistId = null
    }

    val hasMiniPlayer = playback.currentTrack != null
    val bottomClearance = if (hasMiniPlayer) 132.dp else 68.dp

    val topSafeModifier = if (barsSpec.hideStatusBar) Modifier.displayCutoutPadding() else Modifier.statusBarsPadding()
    val bottomSafeModifier = if (barsSpec.hideNavBar) Modifier.padding(bottom = 4.dp) else Modifier.navigationBarsPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(topSafeModifier)
        ) {
            when (currentTab) {
                MainTab.LIBRARY -> {
                    val activeLocalPlId = openLocalPlaylistId
                    if (activeLocalPlId != null && selectedSubsonicServer == null) {
                        val plEntity by remember(activeLocalPlId) {
                            libraryVm.observeLocalPlaylist(activeLocalPlId)
                        }.collectAsState(initial = null)
                        val plItems by remember(activeLocalPlId) {
                            libraryVm.observeLocalPlaylistTracks(activeLocalPlId)
                        }.collectAsState(initial = emptyList())

                        val currentPl = plEntity
                        if (currentPl != null) {
                            val autoCovers = remember(plItems) {
                                plItems.mapNotNull { it.track.coverPath?.takeIf { c -> c.isNotBlank() } }
                                    .distinct()
                                    .take(4)
                            }
                            val sortField = TrackSortField.fromNameOrDefault(currentPl.sortField, TrackSortField.CUSTOM)
                            PlaylistDetailScreen(
                                title = currentPl.name,
                                description = currentPl.description,
                                isRemotePlaylist = false,
                                customCoverPath = currentPl.customCoverPath,
                                autoCovers = autoCovers,
                                items = plItems,
                                sortField = sortField,
                                sortAscending = currentPl.sortAscending,
                                currentTrackPath = playback.currentTrack?.path,
                                downloadsByPath = downloadsByPath,
                                onBack = { openLocalPlaylistId = null },
                                onPlayTracks = { list, idx, label -> vm.playTracks(list, idx, label) },
                                onShufflePlay = { list, label ->
                                    if (list.isNotEmpty()) vm.playTracks(list.shuffled(), 0, "$label · 随机")
                                },
                                onChangeSort = { field, asc ->
                                    libraryVm.setLocalPlaylistSort(currentPl.id, field, asc)
                                },
                                onEditPlaylist = {
                                    playlistEditorMode = PlaylistEditorMode.EditLocal(currentPl, autoCovers)
                                },
                                onDeletePlaylist = {
                                    libraryVm.deleteLocalPlaylist(currentPl.id, currentPl.name) {
                                        openLocalPlaylistId = null
                                    }
                                },
                                onOpenAddTracks = {
                                    addPickerTarget = AddPickerTarget.Local(
                                        playlist = currentPl,
                                        existingPaths = plItems.map { it.track.path }.toSet(),
                                    )
                                },
                                onDownloadPlaylist = { remoteList -> libraryVm.enqueueDownload(remoteList) },
                                onMoveTrack = { trackPath, up ->
                                    libraryVm.moveTrackInLocalPlaylist(currentPl.id, trackPath, up)
                                },
                                onTrackMenu = { track ->
                                    trackMenuCtx = TrackMenuContext(track = track, localPlaylistId = currentPl.id)
                                },
                                onBatchAddToPlaylist = { list -> pendingAddTracks = list },
                                onBatchDownload = { list -> libraryVm.enqueueDownload(list) },
                                onBatchRemoveFromPlaylist = { items ->
                                    libraryVm.removeTracksFromLocalPlaylist(currentPl.id, items.map { it.track.path })
                                },
                                bottomPadding = bottomClearance,
                            )
                        } else {
                            LaunchedEffect(Unit) { openLocalPlaylistId = null }
                        }
                    } else {
                        LibraryScreen(
                            tracks = tracks,
                            localPlaylists = localPlaylists,
                            downloadedList = downloadedList,
                            downloadsByPath = downloadsByPath,
                            downloadQueue = downloadQueue,
                            playback = playback,
                            settings = settings,
                            scanState = scanState,
                            hasStoragePermission = hasStoragePermission,
                            subsonicServers = subsonicServers,
                            selectedSubsonicServer = selectedSubsonicServer,
                            subsonicState = subsonicBrowse,
                            onSelectLocalSource = {
                                scope.launch { app.prefs.setLastLibrarySource(SubsonicRepository.LOCAL_SOURCE_KEY) }
                            },
                            onSelectSubsonicServer = { srv ->
                                openLocalPlaylistId = null
                                subsonicVm.switchActiveServer(srv)
                            },
                            onOpenSubsonicAccounts = { openAdd ->
                                subsonicVm.clearAccountMessage()
                                subsonicSheetInitialAdd = openAdd
                                showSubsonicSheet = true
                            },
                            onSubsonicSelectTab = subsonicVm::selectTab,
                            onSubsonicSelectFolder = subsonicVm::selectMusicFolder,
                            onSubsonicRefresh = subsonicVm::refresh,
                            onSubsonicLoadMoreAlbums = subsonicVm::loadMoreAlbums,
                            onSubsonicOpenAlbum = subsonicVm::openAlbum,
                            onSubsonicOpenArtist = subsonicVm::openArtist,
                            onSubsonicOpenPlaylist = subsonicVm::openPlaylist,
                            onSubsonicCloseDrill = subsonicVm::closeDrill,
                            onSubsonicToggleSearch = subsonicVm::setSearchActive,
                            onSubsonicSearchQuery = subsonicVm::updateSearchQuery,
                            onRequestPermission = ::requestStorageAccess,
                            onScan = vm::startScan,
                            onCancelScan = vm::cancelScan,
                            onImportFiles = { importLauncher.launch(arrayOf("audio/*")) },
                            onPlayTracks = { list, idx, ctxLabel -> vm.playTracks(list, idx, ctxLabel) },
                            onShufflePlay = { list, ctxLabel ->
                                if (list.isNotEmpty()) vm.playTracks(list.shuffled(), 0, ctxLabel)
                            },
                            onResumeLast = { vm.resumeLastPlayback(tracks) },
                            onChangeLocalSongSort = libraryVm::setLibrarySongSort,
                            onOpenCreatePlaylist = { defaultRemote ->
                                playlistEditorMode = PlaylistEditorMode.Create(defaultRemote = defaultRemote)
                            },
                            onOpenLocalPlaylist = { summary -> openLocalPlaylistId = summary.playlist.id },
                            onQuickPlayLocalPlaylist = { summary ->
                                scope.launch {
                                    val items = libraryVm.observeLocalPlaylistTracks(summary.playlist.id).first()
                                    val field = TrackSortField.fromNameOrDefault(summary.playlist.sortField, TrackSortField.CUSTOM)
                                    val sorted = PlaylistSorter.sortTracks(items, field, summary.playlist.sortAscending).map { it.track }
                                    if (sorted.isNotEmpty()) vm.playTracks(sorted, 0, summary.playlist.name)
                                }
                            },
                            onTrackMenu = { track ->
                                val remoteDrill = subsonicBrowse.drill as? SubsonicDrillState.PlaylistDetail
                                val srv = selectedSubsonicServer
                                val remoteRemover: (() -> Unit)? = if (remoteDrill != null && srv != null) {
                                    val idx = remoteDrill.tracks.indexOfFirst { it.path == track.path }
                                    if (idx >= 0) {
                                        {
                                            libraryVm.removeIndexesFromRemotePlaylist(
                                                serverId = srv.id,
                                                playlistId = remoteDrill.playlist.id,
                                                indexes = listOf(idx),
                                            ) { subsonicVm.openPlaylist(remoteDrill.playlist) }
                                        }
                                    } else null
                                } else null
                                trackMenuCtx = TrackMenuContext(
                                    track = track,
                                    localPlaylistId = null,
                                    remotePlaylistRemove = remoteRemover,
                                )
                            },
                            onBatchAddToPlaylist = { list -> pendingAddTracks = list },
                            onDownloadTracks = { list -> libraryVm.enqueueDownload(list) },
                            onOpenDownloadManager = { showDownloadSheet = true },
                            onEditRemotePlaylist = { pl ->
                                val srv = selectedSubsonicServer ?: return@LibraryScreen
                                playlistEditorMode = PlaylistEditorMode.EditRemote(srv.id, pl)
                            },
                            onDeleteRemotePlaylist = { pl ->
                                val srv = selectedSubsonicServer ?: return@LibraryScreen
                                libraryVm.deleteRemotePlaylist(srv.id, pl.id, pl.name) {
                                    subsonicVm.closeDrill()
                                    subsonicVm.refresh()
                                }
                            },
                            onAddTracksToRemotePlaylist = { pl, existing ->
                                val srv = selectedSubsonicServer ?: return@LibraryScreen
                                addPickerTarget = AddPickerTarget.Remote(srv.id, pl, existing)
                            },
                            onRemoveRemotePlaylistIndexes = { pl, indexes ->
                                val srv = selectedSubsonicServer ?: return@LibraryScreen
                                libraryVm.removeIndexesFromRemotePlaylist(srv.id, pl.id, indexes) {
                                    subsonicVm.openPlaylist(pl)
                                }
                            },
                            bottomPadding = bottomClearance,
                        )
                    }
                }

                MainTab.USB -> UsbDeviceScreen(
                    usbState = usbState,
                    settings = settings,
                    activeRoute = activeRoute,
                    onRefreshDevices = { app.usbManager.refreshDevices() },
                    onConnectDevice = { dev ->
                        scope.launch { app.usbManager.connectDevice(dev) }
                    },
                    onDisconnectDevice = { app.usbManager.close() },
                    onToggleExclusive = { enabled ->
                        scope.launch {
                            app.prefs.setUsbExclusive(enabled)
                            vm.rebindAudioRoute()
                        }
                    },
                    onRefreshStats = { app.usbManager.refreshStats() },
                    onAppendLog = { app.usbManager.appendLog(it) },
                    bottomPadding = bottomClearance,
                )

                MainTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    prefs = app.prefs,
                    volumeController = app.volumeController,
                    subsonicServers = subsonicServers,
                    activeSubsonicServer = activeSubsonicServer,
                    downloadedCount = downloadedList.size,
                    downloadedTotalBytes = downloadedList.sumOf { it.fileSize.coerceAtLeast(0L) },
                    wifiTransferState = wifiTransferState,
                    nativeBuildTime = usbState.stats.buildTime,
                    onRebindRoute = vm::rebindAudioRoute,
                    onScanNow = {
                        if (hasStoragePermission) vm.startScan() else requestStorageAccess()
                    },
                    onOpenSubsonicAccounts = {
                        subsonicVm.clearAccountMessage()
                        subsonicSheetInitialAdd = subsonicServers.isEmpty()
                        showSubsonicSheet = true
                    },
                    onOpenDownloadManager = { showDownloadSheet = true },
                    onOpenWifiTransfer = { showWifiTransferSheet = true },
                    bottomPadding = bottomClearance,
                )
            }
        }

        // 底部悬浮 MiniPlayer + Spotify 风格半透明渐变 3-Tab 导航栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.32f to colors.canvas.copy(alpha = 0.94f),
                        1.0f to colors.canvas,
                    )
                )
                .then(bottomSafeModifier)
        ) {
            if (hasMiniPlayer) {
                MiniPlayerBar(
                    playback = playback,
                    usbState = usbState,
                    activeRoute = activeRoute,
                    ambientEnabled = settings.ambientBackground,
                    onOpenNowPlaying = { showNowPlaying = true },
                    onOpenOutputSheet = { showOutputSheet = true },
                    onTogglePlayPause = vm::togglePlayPause,
                    onSkipNext = vm::skipNext,
                    onSkipPrev = vm::skipPrevious,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val tint = if (selected) colors.textPrimary else colors.textMuted
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentTab = tab }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                            if (tab == MainTab.USB && usbState.isConnected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.exclusiveGreen)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
                        )
                    }
                }
            }
        }

        // 全屏黑胶播放页
        AnimatedVisibility(
            visible = showNowPlaying && playback.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            val curPath = playback.currentTrack?.path
            NowPlayingScreen(
                playback = playback,
                settings = settings,
                usbState = usbState,
                volumeState = volumeState,
                activeRoute = activeRoute,
                audioFocusState = audioFocusState,
                barsSpec = barsSpec,
                onCollapse = { showNowPlaying = false },
                onTogglePlayPause = vm::togglePlayPause,
                onSkipNext = vm::skipNext,
                onSkipPrev = vm::skipPrevious,
                onSeekTo = vm::seekTo,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeat = vm::cycleRepeatMode,
                onToggleFavorite = vm::toggleFavorite,
                onSelectQueueIndex = vm::selectQueueIndex,
                onOpenOutputSheet = { showOutputSheet = true },
                onUsbVolumeChange = { pct ->
                    app.volumeController.setVolumePercent(pct, showHud = false)
                },
                onToggleMute = { muted ->
                    app.volumeController.setMuted(muted, showHud = false)
                },
                onToggleImmersiveMode = { enabled ->
                    scope.launch { app.prefs.setImmersiveMusicMode(enabled) }
                },
                isCurrentTrackDownloaded = curPath != null && downloadsByPath.containsKey(curPath),
                onAddCurrentToPlaylist = { track -> pendingAddTracks = listOf(track) },
                onDownloadCurrentTrack = { track -> libraryVm.enqueueDownload(listOf(track)) },
            )
        }

        // 顶层全局护耳音量悬浮 HUD
        VolumeHudOverlay(
            volumeState = volumeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topSafeModifier),
        )

        // 轻量操作反馈 Toast 胶囊（歌单加歌 / 排序 / 下载反馈）
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topSafeModifier)
                .padding(top = 56.dp, start = 20.dp, end = 20.dp),
        ) {
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface2.copy(alpha = 0.96f))
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }

    // ==================== 各类全局 Sheet & Dialog ====================

    if (showOutputSheet) {
        OutputDeviceSheet(
            currentTrack = playback.currentTrack,
            settings = settings,
            usbState = usbState,
            activeRoute = activeRoute,
            onSelectUsbDevice = { dev ->
                scope.launch {
                    app.prefs.setUsbExclusive(true)
                    app.usbManager.connectDevice(dev)
                    vm.rebindAudioRoute()
                }
            },
            onSwitchToSystem = {
                scope.launch {
                    app.prefs.setUsbExclusive(false)
                    vm.rebindAudioRoute()
                    showOutputSheet = false
                }
            },
            onSwitchToUsbExclusive = {
                scope.launch {
                    app.prefs.setUsbExclusive(true)
                    vm.rebindAudioRoute()
                    showOutputSheet = false
                }
            },
            onDismiss = { showOutputSheet = false },
        )
    }

    if (showSubsonicSheet) {
        SubsonicAccountsSheet(
            servers = subsonicServers,
            actionState = subsonicAction,
            initialShowAddForm = subsonicSheetInitialAdd,
            onDismiss = { showSubsonicSheet = false },
            onClearMessage = subsonicVm::clearAccountMessage,
            onSelectServer = { srv ->
                openLocalPlaylistId = null
                subsonicVm.switchActiveServer(srv)
                currentTab = MainTab.LIBRARY
            },
            onTestServer = subsonicVm::testExistingServer,
            onDeleteServer = subsonicVm::deleteServer,
            onLoginAndSave = { editingId, name, url, user, pass, legacy, selfSigned, onDone ->
                subsonicVm.loginAndSaveServer(
                    editingId = editingId,
                    displayName = name,
                    baseUrl = url,
                    username = user,
                    password = pass,
                    useLegacyAuth = legacy,
                    allowSelfSignedTls = selfSigned,
                ) { _, _ ->
                    openLocalPlaylistId = null
                    currentTab = MainTab.LIBRARY
                    onDone()
                }
            },
        )
    }

    // 新建 / 编辑歌单弹窗
    playlistEditorMode?.let { mode ->
        when (mode) {
            is PlaylistEditorMode.Create -> PlaylistEditorDialog(
                isEdit = false,
                activeRemoteServer = activeSubsonicServer,
                defaultToRemote = mode.defaultRemote,
                onDismiss = { playlistEditorMode = null },
                onConfirm = { name, desc, coverUri, _, createRemote ->
                    playlistEditorMode = null
                    if (createRemote && activeSubsonicServer != null) {
                        libraryVm.createRemotePlaylist(
                            serverId = activeSubsonicServer!!.id,
                            name = name,
                            initialTracks = mode.initialTracks,
                        ) {
                            subsonicVm.refresh()
                        }
                    } else {
                        libraryVm.createLocalPlaylist(
                            name = name,
                            description = desc,
                            coverUri = coverUri,
                            initialTracks = mode.initialTracks,
                        )
                    }
                },
            )

            is PlaylistEditorMode.EditLocal -> PlaylistEditorDialog(
                isEdit = true,
                isRemotePlaylist = false,
                initialName = mode.playlist.name,
                initialDescription = mode.playlist.description,
                initialCustomCoverPath = mode.playlist.customCoverPath,
                autoCovers = mode.autoCovers,
                onDismiss = { playlistEditorMode = null },
                onConfirm = { name, desc, coverUri, clearCover, _ ->
                    playlistEditorMode = null
                    libraryVm.updateLocalPlaylist(mode.playlist.id, name, desc, coverUri, clearCover)
                },
            )

            is PlaylistEditorMode.EditRemote -> PlaylistEditorDialog(
                isEdit = true,
                isRemotePlaylist = true,
                initialName = mode.playlist.name,
                initialDescription = mode.playlist.comment,
                onDismiss = { playlistEditorMode = null },
                onConfirm = { name, desc, _, _, _ ->
                    playlistEditorMode = null
                    libraryVm.updateRemotePlaylistMeta(mode.serverId, mode.playlist.id, name, desc) {
                        subsonicVm.openPlaylist(mode.playlist.copy(name = name, comment = desc))
                    }
                },
            )
        }
    }

    // 加入歌单弹窗（单曲与批量共用）
    pendingAddTracks?.let { toAdd ->
        AddToPlaylistSheet(
            tracksToAdd = toAdd,
            localPlaylists = localPlaylists,
            remoteServer = activeSubsonicServer,
            remotePlaylists = subsonicBrowse.playlists,
            onDismiss = { pendingAddTracks = null },
            onCreateNewAndAdd = {
                val targetRemote = activeSubsonicServer != null &&
                    toAdd.all { SubsonicUri.parseTrackUri(it.path)?.serverId == activeSubsonicServer!!.id }
                playlistEditorMode = PlaylistEditorMode.Create(
                    defaultRemote = targetRemote,
                    initialTracks = toAdd,
                )
            },
            onPickLocalPlaylist = { summary ->
                libraryVm.addTracksToLocalPlaylist(summary.playlist.id, summary.playlist.name, toAdd)
            },
            onPickRemotePlaylist = { srv, rpl ->
                libraryVm.addTracksToRemotePlaylist(srv.id, rpl.id, rpl.name, toAdd) {
                    subsonicVm.refresh()
                }
            },
        )
    }

    // 单曲「⋮」快捷操作菜单
    trackMenuCtx?.let { ctx ->
        val t = ctx.track
        TrackActionSheet(
            track = t,
            isDownloaded = downloadsByPath.containsKey(t.path),
            showRemoveFromPlaylist = ctx.localPlaylistId != null || ctx.remotePlaylistRemove != null,
            onDismiss = { trackMenuCtx = null },
            onPlayNow = { vm.playTracks(listOf(t), 0, t.album.ifBlank { "立即播放" }) },
            onAddToPlaylist = { pendingAddTracks = listOf(t) },
            onToggleFavorite = { vm.toggleFavorite(t) },
            onDownload = { libraryVm.enqueueDownload(listOf(t)) },
            onDeleteDownload = { libraryVm.deleteDownloadedTrack(t.path, t.title) },
            onRemoveFromPlaylist = {
                ctx.localPlaylistId?.let { plId ->
                    libraryVm.removeTracksFromLocalPlaylist(plId, listOf(t.path))
                }
                ctx.remotePlaylistRemove?.invoke()
            },
        )
    }

    // 歌单内「+ 添加歌曲」选歌器
    addPickerTarget?.let { target ->
        val candidates = when (target) {
            is AddPickerTarget.Local -> remember(tracks, remoteCachedTracks) {
                (tracks + remoteCachedTracks).distinctBy { it.path }
            }

            is AddPickerTarget.Remote -> remember(remoteCachedTracks, target.serverId) {
                remoteCachedTracks.filter { SubsonicUri.parseTrackUri(it.path)?.serverId == target.serverId }
            }
        }
        val plName = when (target) {
            is AddPickerTarget.Local -> target.playlist.name
            is AddPickerTarget.Remote -> target.playlist.name
        }
        val existing = when (target) {
            is AddPickerTarget.Local -> target.existingPaths
            is AddPickerTarget.Remote -> target.existingPaths
        }
        AddTracksPickerSheet(
            playlistName = plName,
            existingPaths = existing,
            candidateTracks = candidates,
            onDismiss = { addPickerTarget = null },
            onConfirmAdd = { chosen ->
                when (target) {
                    is AddPickerTarget.Local ->
                        libraryVm.addTracksToLocalPlaylist(target.playlist.id, target.playlist.name, chosen)

                    is AddPickerTarget.Remote ->
                        libraryVm.addTracksToRemotePlaylist(target.serverId, target.playlist.id, target.playlist.name, chosen) {
                            subsonicVm.openPlaylist(target.playlist)
                        }
                }
            },
        )
    }

    // 离线下载队列与空间管理 Sheet
    if (showDownloadSheet) {
        DownloadQueueSheet(
            queueState = downloadQueue,
            downloadedList = downloadedList,
            onRetry = libraryVm::retryDownload,
            onCancelTask = libraryVm::cancelDownload,
            onCancelAll = libraryVm::cancelAllDownloads,
            onClearFinished = libraryVm::clearFinishedDownloads,
            onDeleteAllDownloads = libraryVm::deleteAllDownloads,
            onDismiss = { showDownloadSheet = false },
        )
    }

    // WiFi 局域网传歌控制台 Sheet（含扫码二维码与上传历史）
    if (showWifiTransferSheet) {
        WifiTransferSheet(
            state = wifiTransferState,
            history = wifiUploadHistory,
            autoSkipDuplicate = settings.wifiTransferAutoSkipDuplicate,
            onToggleServer = { enable ->
                if (enable) app.wifiTransferManager.startServer()
                else app.wifiTransferManager.stopServer()
            },
            onRefreshNetwork = { app.wifiTransferManager.refreshNetworkAndStorage() },
            onTogglePinEnabled = { enabled ->
                scope.launch {
                    app.prefs.setWifiTransferPinEnabled(enabled)
                    app.wifiTransferManager.refreshPinEnabled(enabled)
                }
            },
            onRegeneratePin = { app.wifiTransferManager.regeneratePin() },
            onToggleAutoSkipDuplicate = { enabled ->
                scope.launch { app.prefs.setWifiTransferAutoSkipDuplicate(enabled) }
            },
            onPlayUploadedFile = { savedPath ->
                scope.launch {
                    val found = tracks.firstOrNull { it.path == savedPath }
                        ?: app.db.trackDao().findByPath(savedPath)
                    if (found != null) {
                        vm.playTracks(listOf(found), 0, "WiFi 传歌")
                        showWifiTransferSheet = false
                        showNowPlaying = true
                    } else {
                        libraryVm.showToast("文件尚未索引，请稍后再试")
                    }
                }
            },
            onDeleteHistoryItem = { id, deleteFile ->
                scope.launch {
                    app.wifiTransferManager.deleteHistoryItem(id, deleteFile)
                    libraryVm.showToast(if (deleteFile) "已删除记录与本地文件" else "已删除上传记录")
                }
            },
            onClearHistory = { deleteFiles ->
                scope.launch {
                    app.wifiTransferManager.clearHistory(deleteFiles)
                    libraryVm.showToast("已清空上传历史")
                }
            },
            onShowToast = libraryVm::showToast,
            onDismiss = { showWifiTransferSheet = false },
        )
    }

    // 仅 Wi-Fi 下载提示弹窗（允许本次一键放行流量下载）
    wifiBlockedTracks?.let { pending ->
        AlertDialog(
            onDismissRequest = libraryVm::dismissCellularDownload,
            containerColor = colors.surface1,
            title = { Text("当前为移动网络", color = colors.textPrimary) },
            text = {
                Text(
                    "您已开启「仅 Wi-Fi 下载」，本次待下载 ${pending.size} 首无损/音频曲目。是否允许本次使用移动流量继续下载？",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = libraryVm::confirmCellularDownload) {
                    Text("继续下载", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = libraryVm::dismissCellularDownload) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requiredRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
