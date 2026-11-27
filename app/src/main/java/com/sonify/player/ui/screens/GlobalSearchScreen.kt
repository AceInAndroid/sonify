package com.sonify.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.R
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.PlaylistSummary
import com.sonify.player.search.GlobalSearchEngine
import com.sonify.player.search.SearchAlbumHit
import com.sonify.player.search.SearchArtistHit
import com.sonify.player.search.SearchLyricHit
import com.sonify.player.search.SearchNormalizer
import com.sonify.player.subsonic.SubsonicAlbum
import com.sonify.player.subsonic.SubsonicSearchResult
import com.sonify.player.ui.components.PlaylistCoverArt
import com.sonify.player.ui.theme.LocalHiFiColors
import com.sonify.player.ui.theme.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private enum class SearchCategory(@StringRes val labelRes: Int) {
    ALL(R.string.search_tab_all),
    SONGS(R.string.search_tab_songs),
    ALBUMS(R.string.search_tab_albums),
    ARTISTS(R.string.search_tab_artists),
    PLAYLISTS(R.string.search_tab_playlists),
    REMOTE(R.string.search_tab_remote),
    LYRICS(R.string.search_tab_lyrics),
}

/**
 * 全屏跨源聚合搜索页（保持 3-Tab，由音乐库顶部放大镜直达）：
 * - 简繁互通（搜简体「周杰伦 叶惠美」秒中繁体「周杰倫 葉惠美」）+ 多词加权相关性排序 + 命中词暖金高亮；
 * - 一站式聚合本地歌曲、专辑、艺人、自建歌单、Subsonic 远程曲库（若已登录）与本地 `.lrc` 歌词搜歌；
 * - 持久化最近 12 条搜索历史 + 快捷探索胶囊（Hi-Res 母带 / FLAC 无损 / 已收藏）。
 */
@Composable
fun GlobalSearchScreen(
    tracks: List<TrackEntity>,
    playlists: List<PlaylistSummary>,
    activeSubsonicServer: SubsonicServerEntity?,
    searchHistory: List<String>,
    currentTrackPath: String?,
    onBack: () -> Unit,
    onAddSearchHistory: (String) -> Unit,
    onRemoveSearchHistory: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onSearchRemote: suspend (Long, String) -> SubsonicSearchResult?,
    onPlayTracks: (List<TrackEntity>, Int, String) -> Unit,
    onPlayLyricHit: (TrackEntity, Long) -> Unit,
    onOpenLocalPlaylist: (PlaylistSummary) -> Unit,
    onOpenRemoteAlbum: (SubsonicAlbum) -> Unit,
    onTrackMenu: (TrackEntity) -> Unit,
    bottomPadding: Dp,
) {
    val colors = LocalHiFiColors.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.ALL) }
    var lyricHits by remember { mutableStateOf<List<SearchLyricHit>>(emptyList()) }
    var remoteResult by remember { mutableStateOf<SubsonicSearchResult?>(null) }
    var isSearchingRemote by remember { mutableStateOf(false) }

    // 专辑/艺人钻取二级视图
    var openLocalAlbum by remember { mutableStateOf<SearchAlbumHit?>(null) }
    var openLocalArtist by remember { mutableStateOf<SearchArtistHit?>(null) }

    BackHandler {
        when {
            openLocalAlbum != null -> openLocalAlbum = null
            openLocalArtist != null -> openLocalArtist = null
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    val localResult = remember(query, tracks, playlists) {
        GlobalSearchEngine.searchLocal(query, tracks, playlists)
    }

    // 异步歌词搜歌（防抖 220ms，纯 IO 线程扫描不卡 UI）
    LaunchedEffect(query, tracks) {
        val q = query.trim()
        if (q.length < 2) {
            lyricHits = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        val cacheDir = File(context.filesDir, "lyrics_cache")
        lyricHits = withContext(Dispatchers.IO) {
            GlobalSearchEngine.searchLyricsFiles(q, tracks, cacheDir)
        }
    }

    // 异步 Subsonic 远程搜索（防抖 350ms）
    LaunchedEffect(query, activeSubsonicServer?.id) {
        val srv = activeSubsonicServer
        val q = query.trim()
        if (srv == null || q.isEmpty()) {
            remoteResult = null
            isSearchingRemote = false
            return@LaunchedEffect
        }
        delay(350)
        isSearchingRemote = true
        remoteResult = runCatching { onSearchRemote(srv.id, q) }.getOrNull()
        isSearchingRemote = false
    }

    fun commitQuery(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.isNotEmpty()) onAddSearchHistory(cleaned)
    }

    // 二级钻取：本地专辑或艺人详情
    val drillAlbum = openLocalAlbum
    val drillArtist = openLocalArtist
    if (drillAlbum != null || drillArtist != null) {
        val headerTitle = drillAlbum?.title ?: drillArtist!!.name
        val headerSub = drillAlbum?.artist ?: stringResource(R.string.common_local_tracks_count, drillArtist!!.tracks.size)
        val drillTracks = drillAlbum?.tracks ?: drillArtist!!.tracks
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    openLocalAlbum = null
                    openLocalArtist = null
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.search_back_to_search), tint = colors.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(headerTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(headerSub, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent)
                        .clickable {
                            if (drillTracks.isNotEmpty()) onPlayTracks(drillTracks, 0, headerTitle)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.common_play), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onAccent)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding + 20.dp),
            ) {
                itemsIndexed(drillTracks, key = { _, t -> t.path }) { idx, track ->
                    SearchSongRow(
                        track = track,
                        tokens = emptyList(),
                        isCurrent = currentTrackPath == track.path,
                        isRemote = false,
                        onClick = { onPlayTracks(drillTracks, idx, headerTitle) },
                        onMore = { onTrackMenu(track) },
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        // 顶部全屏搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(
                            if (activeSubsonicServer != null) R.string.search_hint_with_remote
                            else R.string.search_hint_local
                        ),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_clear), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitQuery(query) }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                    focusedContainerColor = colors.surface1,
                    unfocusedContainerColor = colors.surface1,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        if (query.trim().isEmpty()) {
            // 空态：最近搜索历史 + 快捷探索胶囊
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (searchHistory.isNotEmpty()) {
                    item(key = "history_section") {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.search_recent_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onClearSearchHistory() }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(stringResource(R.string.common_clear), fontSize = 11.sp, color = colors.textMuted)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                searchHistory.forEach { term ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(colors.surface2)
                                            .border(1.dp, colors.hairline, RoundedCornerShape(999.dp))
                                            .clickable {
                                                query = term
                                                commitQuery(term)
                                            }
                                            .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(term, fontSize = 12.sp, color = colors.textPrimary, maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.search_delete_history),
                                            tint = colors.textMuted,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onRemoveSearchHistory(term) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 快捷探索卡片
                item(key = "quick_explore") {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.search_quick_discover), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        val hiResTracks = remember(tracks) { tracks.filter { it.isHiRes } }
                        val favTracks = remember(tracks) { tracks.filter { it.isFavorite } }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickExploreCard(
                                icon = Icons.Rounded.GraphicEq,
                                title = stringResource(R.string.search_quick_flac_title),
                                subtitle = stringResource(R.string.search_quick_flac_sub),
                                tint = colors.accent,
                                onClick = { query = "FLAC"; commitQuery("FLAC") },
                                modifier = Modifier.weight(1f),
                            )
                            QuickExploreCard(
                                icon = Icons.Rounded.Favorite,
                                title = stringResource(R.string.search_quick_fav_title),
                                subtitle = stringResource(R.string.search_quick_fav_sub, favTracks.size),
                                tint = colors.exclusiveGreen,
                                onClick = {
                                    if (favTracks.isNotEmpty()) {
                                        onPlayTracks(favTracks, 0, context.getString(R.string.search_quick_fav_title))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (hiResTracks.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            QuickExploreCard(
                                icon = Icons.Rounded.Album,
                                title = stringResource(R.string.search_quick_hires_title),
                                subtitle = stringResource(R.string.search_quick_hires_sub, hiResTracks.size),
                                tint = colors.accent,
                                onClick = { onPlayTracks(hiResTracks, 0, context.getString(R.string.search_quick_hires_queue)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        } else {
            val remoteSongs = remember(remoteResult, activeSubsonicServer?.id) {
                val srvId = activeSubsonicServer?.id ?: return@remember emptyList()
                remoteResult?.songs.orEmpty().map { it.toTrackEntity(srvId) }
            }
            val remoteAlbums = remoteResult?.albums.orEmpty()
            val availableCats = buildList {
                add(SearchCategory.ALL)
                add(SearchCategory.SONGS)
                add(SearchCategory.ALBUMS)
                add(SearchCategory.ARTISTS)
                add(SearchCategory.PLAYLISTS)
                if (activeSubsonicServer != null) add(SearchCategory.REMOTE)
                add(SearchCategory.LYRICS)
            }

            // 分类筛选胶囊栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableCats.forEach { cat ->
                    val count = when (cat) {
                        SearchCategory.ALL -> localResult.totalCount + lyricHits.size + remoteSongs.size + remoteAlbums.size
                        SearchCategory.SONGS -> localResult.songs.size
                        SearchCategory.ALBUMS -> localResult.albums.size
                        SearchCategory.ARTISTS -> localResult.artists.size
                        SearchCategory.PLAYLISTS -> localResult.playlists.size
                        SearchCategory.REMOTE -> remoteSongs.size + remoteAlbums.size
                        SearchCategory.LYRICS -> lyricHits.size
                    }
                    val sel = category == cat
                    val catLabel = stringResource(cat.labelRes)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (sel) colors.accent else colors.surface2)
                            .clickable { category = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (count > 0 && cat != SearchCategory.ALL) "$catLabel $count" else catLabel,
                            fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) colors.onAccent else colors.textPrimary,
                        )
                    }
                }
            }

            val totalHits = localResult.totalCount + lyricHits.size + remoteSongs.size + remoteAlbums.size
            if (totalHits == 0 && !isSearchingRemote) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.search_empty_title, query.trim()), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.search_empty_sub), fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                ) {
                    val tokens = localResult.tokens

                    // 1. 本地歌曲
                    if ((category == SearchCategory.ALL || category == SearchCategory.SONGS) && localResult.songs.isNotEmpty()) {
                        val slice = if (category == SearchCategory.ALL) localResult.songs.take(5) else localResult.songs
                        item(key = "sec_songs") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_sec_local_songs, localResult.songs.size),
                                showMore = category == SearchCategory.ALL && localResult.songs.size > 5,
                                onShowMore = { category = SearchCategory.SONGS },
                            )
                        }
                        itemsIndexed(slice, key = { _, t -> "song_${t.path}" }) { idx, track ->
                            SearchSongRow(
                                track = track,
                                tokens = tokens,
                                isCurrent = currentTrackPath == track.path,
                                isRemote = false,
                                onClick = {
                                    commitQuery(query)
                                    onPlayTracks(localResult.songs, idx, context.getString(R.string.search_queue_label, query.trim()))
                                },
                                onMore = { onTrackMenu(track) },
                            )
                        }
                    }

                    // 2. 专辑
                    if ((category == SearchCategory.ALL || category == SearchCategory.ALBUMS) && localResult.albums.isNotEmpty()) {
                        val slice = if (category == SearchCategory.ALL) localResult.albums.take(4) else localResult.albums
                        item(key = "sec_albums") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_sec_albums, localResult.albums.size),
                                showMore = category == SearchCategory.ALL && localResult.albums.size > 4,
                                onShowMore = { category = SearchCategory.ALBUMS },
                            )
                        }
                        items(slice, key = { "alb_${it.title}" }) { alb ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        commitQuery(query)
                                        openLocalAlbum = alb
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverSquare(coverPath = alb.coverPath, modifier = Modifier.size(48.dp), cornerDp = 8)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    HighlightedText(alb.title, tokens, 14.sp, FontWeight.SemiBold, colors.textPrimary, colors.accent)
                                    HighlightedText(stringResource(R.string.lib_album_meta, alb.artist, alb.tracks.size), tokens, 12.sp, FontWeight.Normal, colors.textSecondary, colors.accent)
                                }
                            }
                        }
                    }

                    // 3. 艺术家
                    if ((category == SearchCategory.ALL || category == SearchCategory.ARTISTS) && localResult.artists.isNotEmpty()) {
                        val slice = if (category == SearchCategory.ALL) localResult.artists.take(4) else localResult.artists
                        item(key = "sec_artists") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_sec_artists, localResult.artists.size),
                                showMore = category == SearchCategory.ALL && localResult.artists.size > 4,
                                onShowMore = { category = SearchCategory.ARTISTS },
                            )
                        }
                        items(slice, key = { "art_${it.name}" }) { art ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        commitQuery(query)
                                        openLocalArtist = art
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    HighlightedText(art.name, tokens, 14.sp, FontWeight.SemiBold, colors.textPrimary, colors.accent)
                                    Text(stringResource(R.string.common_local_tracks_count, art.tracks.size), fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    // 4. 自建歌单
                    if ((category == SearchCategory.ALL || category == SearchCategory.PLAYLISTS) && localResult.playlists.isNotEmpty()) {
                        val slice = if (category == SearchCategory.ALL) localResult.playlists.take(4) else localResult.playlists
                        item(key = "sec_playlists") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_sec_playlists, localResult.playlists.size),
                                showMore = category == SearchCategory.ALL && localResult.playlists.size > 4,
                                onShowMore = { category = SearchCategory.PLAYLISTS },
                            )
                        }
                        items(slice, key = { "pl_${it.playlist.id}" }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        commitQuery(query)
                                        onOpenLocalPlaylist(pl)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlaylistCoverArt(
                                    customCoverPath = pl.playlist.customCoverPath,
                                    autoCovers = pl.autoCovers,
                                    modifier = Modifier.size(48.dp),
                                    cornerDp = 8,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    HighlightedText(pl.playlist.name, tokens, 14.sp, FontWeight.SemiBold, colors.textPrimary, colors.accent)
                                    Text(stringResource(R.string.common_tracks_count, pl.trackCount), fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }

                    // 5. Subsonic 远程曲库
                    if (activeSubsonicServer != null && (category == SearchCategory.ALL || category == SearchCategory.REMOTE)) {
                        if (isSearchingRemote) {
                            item(key = "sec_remote_loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(color = colors.exclusiveGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.search_remote_loading, activeSubsonicServer.name), fontSize = 12.sp, color = colors.textSecondary)
                                }
                            }
                        } else if (remoteSongs.isNotEmpty() || remoteAlbums.isNotEmpty()) {
                            item(key = "sec_remote") {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_sec_remote, activeSubsonicServer.name, remoteSongs.size + remoteAlbums.size),
                                    showMore = category == SearchCategory.ALL && (remoteSongs.size + remoteAlbums.size) > 4,
                                    onShowMore = { category = SearchCategory.REMOTE },
                                )
                            }
                            val rSongs = if (category == SearchCategory.ALL) remoteSongs.take(4) else remoteSongs
                            itemsIndexed(rSongs, key = { _, t -> "rem_${t.path}" }) { idx, track ->
                                SearchSongRow(
                                    track = track,
                                    tokens = tokens,
                                    isCurrent = currentTrackPath == track.path,
                                    isRemote = true,
                                    onClick = {
                                        commitQuery(query)
                                        onPlayTracks(remoteSongs, idx, context.getString(R.string.search_remote_queue_label, query.trim()))
                                    },
                                    onMore = { onTrackMenu(track) },
                                )
                            }
                            val rAlbums = if (category == SearchCategory.ALL) remoteAlbums.take(2) else remoteAlbums
                            items(rAlbums, key = { "ralb_${it.id}" }) { alb ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            commitQuery(query)
                                            onOpenRemoteAlbum(alb)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CoverSquare(coverPath = alb.coverUri(activeSubsonicServer.id), modifier = Modifier.size(46.dp), cornerDp = 8)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        HighlightedText(alb.name, tokens, 14.sp, FontWeight.SemiBold, colors.textPrimary, colors.exclusiveGreen)
                                        Text(stringResource(R.string.search_remote_album_sub, alb.artist), fontSize = 12.sp, color = colors.exclusiveGreen)
                                    }
                                }
                            }
                        }
                    }

                    // 6. 歌词搜歌
                    if ((category == SearchCategory.ALL || category == SearchCategory.LYRICS) && lyricHits.isNotEmpty()) {
                        val slice = if (category == SearchCategory.ALL) lyricHits.take(4) else lyricHits
                        item(key = "sec_lyrics") {
                            SearchSectionHeader(
                                title = stringResource(R.string.search_sec_lyrics, lyricHits.size),
                                showMore = category == SearchCategory.ALL && lyricHits.size > 4,
                                onShowMore = { category = SearchCategory.LYRICS },
                            )
                        }
                        items(slice, key = { "lyr_${it.track.path}" }) { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        commitQuery(query)
                                        onPlayLyricHit(hit.track, hit.timeMs)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    HighlightedText("\"${hit.matchedLine}\"", tokens, 14.sp, FontWeight.SemiBold, colors.textPrimary, colors.accent)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${hit.track.title} · ${hit.track.artist} · ${formatDuration(hit.timeMs)}",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    showMore: Boolean,
    onShowMore: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
        if (showMore) {
            Text(
                text = stringResource(R.string.common_view_all),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier.clickable { onShowMore() },
            )
        }
    }
}

@Composable
private fun SearchSongRow(
    track: TrackEntity,
    tokens: List<String>,
    isCurrent: Boolean,
    isRemote: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverSquare(coverPath = track.coverPath, modifier = Modifier.size(46.dp), cornerDp = 6)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRemote) {
                    Icon(Icons.Rounded.Cloud, contentDescription = null, tint = colors.exclusiveGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                HighlightedText(
                    text = track.title,
                    tokens = tokens,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    baseColor = if (isCurrent) colors.accent else colors.textPrimary,
                    highlightColor = colors.accent,
                )
            }
            Spacer(Modifier.height(2.dp))
            HighlightedText(
                text = "${track.artist} · ${track.album}",
                tokens = tokens,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                baseColor = colors.textSecondary,
                highlightColor = colors.accent,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = track.formatBadgeShort(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surface2)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(formatDuration(track.durationMs), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
        }
        IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more_actions), tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    tokens: List<String>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    baseColor: Color,
    highlightColor: Color,
) {
    val ranges = remember(text, tokens) { SearchNormalizer.findHighlightRanges(text, tokens) }
    if (ranges.isEmpty()) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = baseColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val annotated = remember(text, ranges, baseColor, highlightColor) {
            buildAnnotatedString {
                var cursor = 0
                for (r in ranges) {
                    val s = r.first.coerceIn(cursor, text.length)
                    val e = (r.last + 1).coerceIn(s, text.length)
                    if (s > cursor) append(text.substring(cursor, s))
                    if (e > s) {
                        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                            append(text.substring(s, e))
                        }
                    }
                    cursor = e
                }
                if (cursor < text.length) append(text.substring(cursor))
            }
        }
        Text(
            text = annotated,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = baseColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickExploreCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHiFiColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface1)
            .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
