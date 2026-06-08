package com.zongting.zongting.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.player.PlayerActions
import com.zongting.zongting.ui.player.PlayerUiState
import com.zongting.zongting.ui.player.layout.PadLandscape
import com.zongting.zongting.ui.player.layout.PadPortrait
import com.zongting.zongting.ui.player.layout.PhoneLandscape
import com.zongting.zongting.ui.player.layout.PhonePortrait
import kotlinx.coroutines.delay
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    isLandscapePhone: Boolean = false,
    baseDensity: Density,
    onBackClick: () -> Unit,
    viewModel: MainViewModel
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricState by viewModel.lyricState.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    // 定时关闭状态
    val isTimerActive by SleepTimerManager.isActive.collectAsState()
    val timerRemaining by SleepTimerManager.remainingSeconds.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }

    // 当前歌曲变化时自动获取歌词
    LaunchedEffect(currentSong?.rid) {
        currentSong?.let { song ->
            viewModel.fetchLyric(song.rid, song.source, song.name, song.artist)
        }
    }

    // 系统返回键 — 退出播放界面
    // 历史上这里有 "if (showPlaylistSheet) { showPlaylistSheet = false } else onBackClick()"
    // 但 showPlaylistSheet 顶层永远 false（layout 内部自己管 sheet），所以 if 永远不命中。
    // 简化为直接透传 onBackClick。
    BackHandler {
        onBackClick()
    }

    // 定期同步播放进度（每秒更新一次 position 和 duration）
    LaunchedEffect(Unit) {
        while (true) {
            if (!isSeeking) {
                val pos = PlayerManager.currentPosition
                // PlayerManager.duration 在媒体刚开始播放时可能为0，
                // 用 currentSong.duration（秒转毫秒）作为兜底
                val pmDur = PlayerManager.duration
                val songDur = ((currentSong?.duration ?: 0) * 1000L)
                val dur = if (pmDur > 0) pmDur else songDur
                if (dur > 0) {
                    viewModel.updateProgress(pos, dur)
                }
            }
            kotlinx.coroutines.delay(500L)
        }
    }

    // PAD 横屏：新布局
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val padPlaylistListState = rememberLazyListState()
    if (isExpanded) {
        PadLandscape(
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackState = playbackState,
            lyricState = lyricState,
            playMode = viewModel.playMode.value,
            isFavorite = currentSong?.let { favoriteSongs.contains(it.rid) } ?: false,
            onBackClick = onBackClick,
            onTogglePlay = { viewModel.togglePlayPause() },
            onTogglePlayMode = { viewModel.togglePlayMode() },
            onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            onSeek = { pos -> viewModel.seekTo(pos) },
            onDrag = { pos -> viewModel.updateProgress(pos, playbackState.duration) },
            currentPlaylist = currentPlaylist,
            onPlaySong = { song -> viewModel.playSong(song, currentPlaylist) },
            playlistListState = padPlaylistListState,
            userPlaylists = viewModel.userPlaylists.value,
            isTimerActive = isTimerActive,
            timerRemaining = timerRemaining,
            onAddSongToPlaylist = { playlistId, song, onDone -> viewModel.addSongToPlaylist(playlistId, song, onDone) },
            onCreatePlaylistAndAddSong = { name, song -> viewModel.createPlaylistAndAddSong(name, song) },
            imageLoader = viewModel.cachedImageLoader,
            // 4 dialog triggers below default to {} inside PadLandscape —
            // it owns its own Local dialog state (showSleepTimerDialogLocal etc).
            baseDensity = baseDensity
        )
        return
    }

    // 手机横屏：简化布局
    if (isLandscapePhone) {
        PhoneLandscape(
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackState = playbackState,
            playMode = viewModel.playMode.value,
            isFavorite = currentSong?.let { favoriteSongs.contains(it.rid) } ?: false,
            lyricState = lyricState,
            onBackClick = onBackClick,
            onTogglePlay = { viewModel.togglePlayPause() },
            onTogglePlayMode = { viewModel.togglePlayMode() },
            onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
            onPrevious = { viewModel.playPrevious() },
            onNext = { viewModel.playNext() },
            onSeek = { pos -> viewModel.seekTo(pos) },
            imageLoader = viewModel.cachedImageLoader
        )
        return
    }

    // 竖屏：手机(Compact)走 PhonePortrait,平板竖屏(Medium)走 PadPortrait 三栏
    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium) {
        PadPortrait(
            state = PlayerUiState(
                currentSong = currentSong,
                isPlaying = isPlaying,
                playbackState = playbackState,
                lyricState = lyricState,
                currentPlaylist = currentPlaylist,
                favoriteSet = favoriteSongs,
                playMode = viewModel.playMode.value,
                userPlaylists = viewModel.userPlaylists.value,
                isTimerActive = isTimerActive,
                timerRemaining = timerRemaining,
            ),
            actions = PlayerActions(
                onBack = onBackClick,
                onTogglePlay = { viewModel.togglePlayPause() },
                onTogglePlayMode = { viewModel.togglePlayMode() },
                onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                onSeek = { pos -> viewModel.seekTo(pos) },
                onDrag = { pos -> viewModel.updateProgress(pos, playbackState.duration) },
                onPlaySong = { song -> viewModel.playSong(song, currentPlaylist) },
                onAddSongToPlaylist = { id, song, onDone ->
                    viewModel.addSongToPlaylist(id, song, onDone)
                },
                onCreatePlaylistAndAddSong = { name, song ->
                    viewModel.createPlaylistAndAddSong(name, song)
                },
            ),
            imageLoader = viewModel.cachedImageLoader,
            windowSizeClass = windowSizeClass,
            baseDensity = baseDensity,
        )
    } else {
        PhonePortrait(
            viewModel = viewModel,
            windowSizeClass = windowSizeClass,
            baseDensity = baseDensity,
            onBackClick = onBackClick
        )
    }
}

