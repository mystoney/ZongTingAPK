package com.zongting.zongting.ui.player.layout

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.request.ImageRequest
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.screens.RingtoneCutterScreen
import com.zongting.zongting.ringtone.RingtoneCutterViewModel
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.player.component.AlbumCoverPage
import com.zongting.zongting.ui.player.component.LyricPage
import com.zongting.zongting.ui.player.component.PlayerBottomBar
import com.zongting.zongting.ui.player.component.SavePlaylistDialog
import com.zongting.zongting.ui.player.component.SleepTimerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phone portrait (竖屏手机) player layout. The default branch in
 * [com.zongting.zongting.ui.screens.PlayerScreen] — anything that is
 * NOT expanded (width >= 840dp) and NOT landscape-phone.
 *
 * Layout:
 *  - Top: TopAppBar (back arrow collapses the sheet OR navigates back) +
 *    TabRow (播放 / 歌词).
 *  - Middle: HorizontalPager that swaps between [AlbumCoverPage] (the
 *    spinning vinyl + control panel) and [LyricPage] (auto-scrolling
 *    lyrics).
 *  - Bottom: [PlayerBottomBar] (slider + core controls + function keys).
 *  - Overlays: sleep-timer dialog, save-playlist dialog, ringtone-cutter
 *    full-screen, all hidden while the ringtone cutter is showing.
 *
 * Background: the current cover is stretched edge-to-edge, blurred
 * (RenderEffect on API 31+, Modifier.blur on older), then covered with
 * a 70%-opacity black wash so the foreground text stays readable.
 *
 * Originally inline in `fun PlayerScreen` (the else branch in
 * PlayerScreen.kt). Hoisted here so [com.zongting.zongting.ui.screens.PlayerScreen]
 * becomes a thin dispatcher and this composable can be unit-tested
 * in isolation.
 *
 * The signature still takes [MainViewModel] directly (rather than a
 * fully-decoupled [com.zongting.zongting.ui.player.PlayerUiState] +
 * [com.zongting.zongting.ui.player.PlayerActions] pair) because the
 * portrait layout owns the pager state, ringtone VM lookup, and
 * sleep-timer wiring, which all need the view-model. Future refactor:
 * pass state/actions instead of the view-model, and hoist the pager
 * state into the caller.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhonePortrait(
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass,
    baseDensity: Density,
    onBackClick: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricState by viewModel.lyricState.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    // 定时关闭状态来自静态 SleepTimerManager（不是 ViewModel 字段）
    val isTimerActive by SleepTimerManager.isActive.collectAsState()
    val timerRemaining by SleepTimerManager.remainingSeconds.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showRingtoneCutter by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }

    val imageLoader: ImageLoader = viewModel.cachedImageLoader

    // 封面虚化平铺背景（无封面时用深色背景）
    Box(modifier = Modifier.fillMaxSize()) {
        // 深色基底
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1A)))

        // 平铺封面：单张图片拉伸到填满背景 + 虚化
        currentSong?.let { song ->
            val coverUrl = song.coverUrl ?: song.pic
            if (coverUrl.isNotBlank()) {
                val ctx = LocalContext.current

                val loadedBmp = produceState<android.graphics.Bitmap?>(null, coverUrl) {
                    val coilImageLoader = coil.ImageLoader(ctx)
                    val request = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = coilImageLoader.execute(request)
                    value = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Modifier.blur(16.dp)
                            else Modifier
                        )
                ) {
                    val bmp = loadedBmp.value
                    if (bmp != null && !bmp.isRecycled) {
                        val dst = android.graphics.Rect(0, 0, size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                        val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                        drawContext.canvas.nativeCanvas.drawBitmap(bmp, src, dst, null)
                    }
                }

                // Android 12+ 用 RenderEffect 虚化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                renderEffect = android.graphics.RenderEffect
                                    .createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                    ) { }
                }
            }
        }

        // 遮罩确保文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color(0xB3000000))
                }
        )

        // 主内容 — 放在遮罩层之上
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            if (!showRingtoneCutter) {
                TopAppBar(
                    title = { Text("正在播放", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (showPlaylistSheet) {
                                    showPlaylistSheet = false
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                if (currentSong != null) {
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
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            playbackState = playbackState,
                            playMode = viewModel.playMode.value,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onSeek = { pos ->
                                isSeeking = true
                                viewModel.seekTo(pos)
                                coroutineScope.launch {
                                    delay(500L)
                                    isSeeking = false
                                }
                            },
                            onDrag = { pos -> viewModel.updateProgress(pos, playbackState.duration) },
                            onPrevious = { viewModel.playPrevious() },
                            onNext = { viewModel.playNext() },
                            imageLoader = imageLoader
                        )
                        1 -> {
                            // 歌词：opt-out 2.5x 字体放大，保持 1.0x
                            CompositionLocalProvider(LocalDensity provides baseDensity) {
                                LyricPage(
                                    currentSong = currentSong,
                                    lyricState = lyricState,
                                    playbackState = playbackState,
                                    isPlaying = isPlaying,
                                    onTogglePlay = { viewModel.togglePlayPause() },
                                    onPrevious = { viewModel.playPrevious() },
                                    onNext = { viewModel.playNext() },
                                    onDrag = { pos -> viewModel.updateProgress(pos, playbackState.duration) },
                                    onSeek = { pos -> viewModel.seekTo(pos) },
                                    isPad = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
                                )
                            }
                        }
                    }
                }
            }

            if (!showRingtoneCutter) {
                PlayerBottomBar(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    playMode = viewModel.playMode.value,
                    isFavorite = currentSong?.let { favoriteSongs.contains(it.rid) } ?: false,
                    showPlaylist = showPlaylistSheet,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onTogglePlayMode = { viewModel.togglePlayMode() },
                    onShowPlaylist = { showPlaylistSheet = it },
                    onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                    onToggleSavePlaylist = { showSavePlaylistDialog = true },
                    onSleepTimerClick = { showSleepTimerDialog = true },
                    onRingtoneCutterClick = {
                        PlayerManager.pause()
                        showRingtoneCutter = true
                    },
                    isTimerActive = isTimerActive,
                    timerRemaining = timerRemaining,
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    currentPlaylist = currentPlaylist,
                    onPlaySong = { song -> viewModel.playSong(song, currentPlaylist) },
                    playlistListState = rememberLazyListState(),
                    windowSizeClass = windowSizeClass
                )
            }

            val context = LocalContext.current
            if (showSleepTimerDialog && !showRingtoneCutter) {
                SleepTimerDialog(
                    isActive = isTimerActive,
                    remainingSeconds = timerRemaining,
                    onStartTimer = { mins -> SleepTimerManager.start(context, mins) },
                    onCancelTimer = { SleepTimerManager.cancelWithNotification(context) },
                    onDismiss = { showSleepTimerDialog = false }
                )
            }

            if (showSavePlaylistDialog && !showRingtoneCutter) {
                SavePlaylistDialog(
                    songCount = 1,
                    playlists = viewModel.userPlaylists.value,
                    onSelectPlaylist = { playlistId ->
                        currentSong?.let { song -> viewModel.addSongToPlaylist(playlistId, song) {} }
                    },
                    onCreatePlaylist = { name ->
                        currentSong?.let { song ->
                            viewModel.createPlaylistAndAddSong(name, song)
                        } ?: viewModel.createPlaylist(name)
                    },
                    onDismiss = { showSavePlaylistDialog = false }
                )
            }

            if (showRingtoneCutter) {
                val lyrics = (lyricState as? LyricState.Success)?.lyrics ?: emptyList()
                val durationMs = playbackState.duration.coerceAtLeast(0L)

                val ringtoneViewModel: RingtoneCutterViewModel = hiltViewModel()
                LaunchedEffect(currentSong, durationMs, lyrics) {
                    if (durationMs > 0) {
                        ringtoneViewModel.initialize(currentSong, durationMs, lyrics)
                    }
                }

                RingtoneCutterScreen(
                    onBackClick = {
                        ringtoneViewModel.stopPreview()
                        showRingtoneCutter = false
                    },
                    viewModel = ringtoneViewModel,
                    lyrics = lyrics
                )
            }
        }
    }
}
