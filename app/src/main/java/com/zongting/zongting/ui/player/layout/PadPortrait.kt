package com.zongting.zongting.ui.player.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.screens.RingtoneCutterScreen
import com.zongting.zongting.ringtone.RingtoneCutterViewModel
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.player.PlayerActions
import com.zongting.zongting.ui.player.PlayerUiState
import com.zongting.zongting.ui.player.component.AlbumCoverPage
import com.zongting.zongting.ui.player.component.LyricPage
import com.zongting.zongting.ui.player.component.PlayerBottomBar
import com.zongting.zongting.ui.player.component.SavePlaylistDialog
import com.zongting.zongting.ui.player.component.SleepTimerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Player layout for **portrait**-orientation tablets (Medium width
 * size class, height greater than width).
 *
 * Layout — same as [PhonePortrait]: a 2-tab HorizontalPager (播放 / 歌词)
 * wrapped in a Column with [PadPortraitTopBar] on top and
 * [PlayerBottomBar] at the bottom. The difference from [PhonePortrait]
 * is only the overall scale (PAD uses larger controls / text — to be
 * tuned later) and the top-bar implementation ([PadPortraitTopBar] vs.
 * [androidx.compose.material3.TopAppBar]).
 *
 * @param state snapshot of all reactive state the player needs.
 * @param actions callback bundle for every user-driven event.
 * @param imageLoader Coil loader used by [AlbumCoverPage].
 * @param windowSizeClass propagated to [PlayerBottomBar] so its scale
 *   factor (icon / button sizes) stays consistent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PadPortrait(
    state: PlayerUiState,
    actions: PlayerActions,
    imageLoader: ImageLoader,
    windowSizeClass: WindowSizeClass,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val baseDensity = LocalDensity.current

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showRingtoneCutter by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        Column(modifier = Modifier.fillMaxSize()) {
            if (!showRingtoneCutter) {
                PadPortraitTopBar(state, actions)

                if (state.currentSong != null) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("播放", style = MaterialTheme.typography.labelMedium) }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("歌词", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            if (!showRingtoneCutter) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> AlbumCoverPage(
                            currentSong = state.currentSong,
                            isPlaying = state.isPlaying,
                            playbackState = state.playbackState,
                            playMode = state.playMode,
                            onTogglePlay = actions.onTogglePlay,
                            onSeek = { pos ->
                                isSeeking = true
                                actions.onSeek(pos)
                                coroutineScope.launch {
                                    delay(500L)
                                    isSeeking = false
                                }
                            },
                            onDrag = actions.onDrag,
                            onPrevious = actions.onPrevious,
                            onNext = actions.onNext,
                            imageLoader = imageLoader
                        )
                        1 -> {
                            // 歌词：opt-out 字体放大，保持当前 density
                            CompositionLocalProvider(LocalDensity provides baseDensity) {
                                LyricPage(
                                    currentSong = state.currentSong,
                                    lyricState = state.lyricState,
                                    playbackState = state.playbackState,
                                    isPlaying = state.isPlaying,
                                    onTogglePlay = actions.onTogglePlay,
                                    onPrevious = actions.onPrevious,
                                    onNext = actions.onNext,
                                    onDrag = actions.onDrag,
                                    onSeek = actions.onSeek,
                                    isPad = true
                                )
                            }
                        }
                    }
                }
            }

            if (!showRingtoneCutter) {
                PlayerBottomBar(
                    currentSong = state.currentSong,
                    isPlaying = state.isPlaying,
                    playbackState = state.playbackState,
                    playMode = state.playMode,
                    isFavorite = state.isFavorite,
                    showPlaylist = false,
                    onTogglePlay = actions.onTogglePlay,
                    onTogglePlayMode = actions.onTogglePlayMode,
                    onShowPlaylist = { },
                    onToggleSavePlaylist = { showSavePlaylistDialog = true },
                    onToggleFavorite = actions.onToggleFavorite,
                    onSleepTimerClick = { showSleepTimerDialog = true },
                    onRingtoneCutterClick = {
                        PlayerManager.pause()
                        showRingtoneCutter = true
                    },
                    isTimerActive = state.isTimerActive,
                    timerRemaining = state.timerRemaining,
                    onPrevious = actions.onPrevious,
                    onNext = actions.onNext,
                    currentPlaylist = state.currentPlaylist,
                    onPlaySong = actions.onPlaySong,
                    playlistListState = rememberLazyListState(),
                    windowSizeClass = windowSizeClass
                )
            }

            // ───── Dialogs / full-screen ringtone cutter ─────
            if (showSleepTimerDialog && !showRingtoneCutter) {
                SleepTimerDialog(
                    isActive = state.isTimerActive,
                    remainingSeconds = state.timerRemaining,
                    onStartTimer = { mins -> SleepTimerManager.start(context, mins) },
                    onCancelTimer = { SleepTimerManager.cancelWithNotification(context) },
                    onDismiss = { showSleepTimerDialog = false }
                )
            }

            if (showSavePlaylistDialog && !showRingtoneCutter) {
                SavePlaylistDialog(
                    songCount = 1,
                    playlists = state.userPlaylists,
                    onSelectPlaylist = { playlistId ->
                        state.currentSong?.let { song ->
                            actions.onAddSongToPlaylist(playlistId, song) {}
                        }
                    },
                    onCreatePlaylist = { name ->
                        state.currentSong?.let { song ->
                            actions.onCreatePlaylistAndAddSong(name, song)
                        }
                    },
                    onDismiss = { showSavePlaylistDialog = false }
                )
            }

            if (showRingtoneCutter) {
                val lyrics = (state.lyricState as? LyricState.Success)?.lyrics ?: emptyList()
                val durationMs = state.playbackState.duration.coerceAtLeast(0L)
                val ringtoneViewModel: RingtoneCutterViewModel = hiltViewModel()
                LaunchedEffect(state.currentSong, durationMs, lyrics) {
                    if (durationMs > 0 && state.currentSong != null) {
                        ringtoneViewModel.initialize(state.currentSong, durationMs, lyrics)
                    }
                }
                RingtoneCutterScreen(
                    onBackClick = { showRingtoneCutter = false },
                    viewModel = ringtoneViewModel,
                    lyrics = lyrics
                )
            }
        }
    }
}

@Composable
private fun PadPortraitTopBar(
    state: PlayerUiState,
    actions: PlayerActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = actions.onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "正在播放",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        // 定时关闭状态
        if (state.isTimerActive && state.timerRemaining > 0) {
            val mins = state.timerRemaining / 60
            val secs = state.timerRemaining % 60
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${mins}:${"%02d".format(secs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7C4DFF)
            )
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = actions.onToggleFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = if (state.isFavorite) "取消喜欢" else "我喜欢",
                tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onToggleSavePlaylist) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加到歌单",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onSleepTimerClick) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "定时关闭",
                tint = if (state.isTimerActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onRingtoneCutterClick) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "铃声剪辑",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
