package com.zongting.zongting.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.LyricLine
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.theme.AppColors
import com.zongting.zongting.ui.PlaybackState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    isLandscapePhone: Boolean = false,
    onBackClick: () -> Unit,
    viewModel: MainViewModel
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricState by viewModel.lyricState.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showRingtoneCutter by remember { mutableStateOf(false) }

    // 定时关闭状态
    val isTimerActive by SleepTimerManager.isActive.collectAsState()
    val timerRemaining by SleepTimerManager.remainingSeconds.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }

    // 当前歌曲变化时自动获取歌词
    LaunchedEffect(currentSong?.rid) {
        currentSong?.let { song ->
            viewModel.fetchLyric(song.rid, song.source, song.name, song.artist)
        }
    }

    // 系统返回键优先关闭播放列表面板，再退出播放界面
    BackHandler {
        if (showPlaylistSheet) {
            showPlaylistSheet = false
        } else {
            onBackClick()
        }
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

    // 手机横屏：简化布局
    if (isLandscapePhone) {
        PlayerScreenLandscape(
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackState = playbackState,
            playMode = viewModel.playMode.value,
            isFavorite = currentSong?.let { viewModel.isFavorite(it.rid) } ?: false,
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

    // 封面虚化平铺背景（无封面时用深色背景）
    Box(modifier = Modifier.fillMaxSize()) {
        // 深色基底
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1A)))

        // 平铺封面：单张图片拉伸到填满背景 + 虚化
        currentSong?.let { song ->
            val coverUrl = song.coverUrl ?: song.pic
            if (coverUrl.isNotBlank()) {
                val ctx = LocalContext.current

                val loadedBmp: State<android.graphics.Bitmap?> = produceState<android.graphics.Bitmap?>(null, coverUrl) {
                    val coilImageLoader = coil.ImageLoader(ctx)
                    val request = coil.request.ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = coilImageLoader.execute(request)
                    value = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }

                // 单张图片拉伸填满屏幕
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
                        // 将图片拉伸到填满整个 Canvas（类似壁纸拉伸效果）
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
                    ) { /* 透明子层，RenderEffect 模糊拉伸的图片背景 */ }
                }
            }
        }

        // 遮罩确保文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color(0xB3000000)) // 70% 透明黑色
                }
        )

        // 主内容 — 放在遮罩层之上
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent) // 内容区透明，背景是 Box 的虚化封面
        ) {
        // 顶部导航（铃声截取界面时隐藏）
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

            // Tab 切换指示器
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

        // 页面内容区域（铃声截取界面时隐藏）
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
                        imageLoader = viewModel.cachedImageLoader
                    )
                    1 -> LyricPage(
                        currentSong = currentSong,
                        lyricState = lyricState,
                        playbackState = playbackState,
                        isPlaying = isPlaying,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onPrevious = { viewModel.playPrevious() },
                        onNext = { viewModel.playNext() },
                        onDrag = { pos -> viewModel.updateProgress(pos, playbackState.duration) },
                        onSeek = { viewModel.seekTo(it) },
                        isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                    )
                }
            }
        }

        // ===== 统一的底部播放控制栏（铃声截取界面时隐藏） =====
        if (!showRingtoneCutter) {
            PlayerBottomBar(
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackState = playbackState,
            playMode = viewModel.playMode.value,
            isFavorite = currentSong?.let { viewModel.isFavorite(it.rid) } ?: false,
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

        // 定时关闭弹窗（铃声截取界面时隐藏）
        val context = LocalContext.current
        if (showSleepTimerDialog && !showRingtoneCutter) {
            SleepTimerDialog(
                isActive = isTimerActive,
                remainingSeconds = timerRemaining,
                onStartTimer = { mins ->
                    SleepTimerManager.start(context, mins)
                },
                onCancelTimer = {
                    SleepTimerManager.cancelWithNotification(context)
                },
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        // 添加到歌单弹窗（铃声截取界面时隐藏）
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

        // 铃声截取界面（全屏覆盖）
        if (showRingtoneCutter) {
            val lyrics = (lyricState as? LyricState.Success)?.lyrics ?: emptyList()
            val durationMs = playbackState.duration.coerceAtLeast(0L)

            val ringtoneViewModel: com.zongting.zongting.ringtone.RingtoneCutterViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()
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

// ===== 统一的底部播放控制栏 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomBar(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    isFavorite: Boolean,
    showPlaylist: Boolean,
    onTogglePlay: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onShowPlaylist: (Boolean) -> Unit,
    onToggleSavePlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onRingtoneCutterClick: () -> Unit,
    isTimerActive: Boolean,
    timerRemaining: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    currentPlaylist: List<Song>,
    onPlaySong: (Song) -> Unit,
    playlistListState: androidx.compose.foundation.lazy.LazyListState,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass
) {
    // 乐观更新：本地记住当前图标状态，点击立即切换，异步同步真实 playMode
    var localPlayMode by remember { mutableIntStateOf(playMode) }
    LaunchedEffect(playMode) { localPlayMode = playMode }

    val playModeIcon = when (localPlayMode) {
        1 -> Icons.Default.RepeatOne
        2 -> Icons.Default.Shuffle
        else -> Icons.Default.Repeat
    }
    val playModeDesc = when (localPlayMode) {
        0 -> "顺序播放"
        1 -> "单曲循环"
        else -> "随机播放"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 4.dp
    ) {
        val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
        val scale = if (isExpanded) 1.3f else 1f
        val basePadding = 16.dp
        val baseBtnSize = 48.dp
        val baseFilledBtnSize = 56.dp
        val baseIconSize = 28.dp
        val baseLargeIconSize = 32.dp
        val baseSmallIconSize = 24.dp
        val scaledPadding = (basePadding.value * scale).dp.coerceAtLeast(basePadding)
        val scaledBtnSize = (baseBtnSize.value * scale).dp.coerceAtLeast(baseBtnSize)
        val scaledFilledBtnSize = (baseFilledBtnSize.value * scale).dp.coerceAtLeast(baseFilledBtnSize)
        val scaledIconSize = (baseIconSize.value * scale).dp.coerceAtLeast(baseIconSize)
        val scaledLargeIconSize = (baseLargeIconSize.value * scale).dp.coerceAtLeast(baseLargeIconSize)
        val scaledSmallIconSize = (baseSmallIconSize.value * scale).dp.coerceAtLeast(baseSmallIconSize)

        Column(modifier = Modifier.padding(horizontal = scaledPadding, vertical = 4.dp)) {
            // 上一首 / 播放暂停 / 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(scaledBtnSize)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(scaledIconSize))
                }

                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(scaledFilledBtnSize), shape = CircleShape) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(scaledLargeIconSize)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(scaledBtnSize)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(scaledIconSize))
                }
            }

            // 附加功能按钮（播放模式 / 播放列表 / 添加到歌单 / 收藏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {
                    localPlayMode = (localPlayMode + 1) % 3
                    onTogglePlayMode()
                }) {
                    Icon(imageVector = playModeIcon, contentDescription = playModeDesc, modifier = Modifier.size(scaledSmallIconSize))
                }

                IconButton(onClick = { onShowPlaylist(true) }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "播放列表", modifier = Modifier.size(scaledSmallIconSize))
                }

                IconButton(onClick = onToggleSavePlaylist) {
                    Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = "保存到歌单", modifier = Modifier.size(scaledSmallIconSize))
                }

                IconButton(onClick = onSleepTimerClick) {
                    Icon(
                        imageVector = if (isTimerActive) Icons.Default.BedtimeOff else Icons.Default.Bedtime,
                        contentDescription = "定时关闭",
                        tint = if (isTimerActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(scaledSmallIconSize)
                    )
                }

                IconButton(onClick = { onRingtoneCutterClick() }) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "设为铃声",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(scaledSmallIconSize)
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    FavoriteIcon(
                        isFavorite = isFavorite,
                        tint = if (isFavorite) AppColors.FavoriteActive else MaterialTheme.colorScheme.onSurface,
                        contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                        modifier = Modifier.size(scaledSmallIconSize)
                    )
                }
            }

            // 定时关闭剩余时间（仅开启时显示）
            if (isTimerActive && timerRemaining > 0) {
                val mins = timerRemaining / 60
                val secs = timerRemaining % 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "🌙 ${mins}分${secs}秒后停止播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7C4DFF)
                    )
                }
            }
        }
    }

    // 播放列表底部弹出面板
    if (showPlaylist) {
        ModalBottomSheet(
            onDismissRequest = { onShowPlaylist(false) },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xF20B1E10)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF20B1E10))
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "当前播放列表 (${currentPlaylist.size}首)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (currentPlaylist.isEmpty()) {
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xF20B1E10))
                            .heightIn(max = 400.dp),
                        state = playlistListState,
                    ) {
                        itemsIndexed(currentPlaylist) { index, song ->
                            val isCurrentSong = song.rid == currentSong?.rid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .combinedClickable(
                                        onClick = { onPlaySong(song) },
                                        onDoubleClick = { onPlaySong(song) }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrentSong) {
                                    val isExpandedRow = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                                    val rowScale = if (isExpandedRow) 1.3f else 1f
                                    val rowIconSize = (24.dp.value * rowScale).dp.coerceAtLeast(24.dp)
                                    IconButton(onClick = onTogglePlay) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "暂停" else "播放",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(rowIconSize)
                                        )
                                    }
                                }
                            }
                            if (index < currentPlaylist.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 唱片封面组件 =====

@Composable
private fun VinylRecord(
    albumArtUrl: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    imageLoader: coil.ImageLoader
) {
    val context = LocalContext.current
    val cachedLoader = androidx.compose.runtime.remember { imageLoader }

    var rotation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // 暂停时冻结在当前角度
    val pausedRotation = androidx.compose.runtime.remember(rotation, isPlaying) { rotation }
    val displayRotation = if (isPlaying) rotation else pausedRotation

    // 匀速旋转：每秒转 360/10 = 36 度
    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastTime = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val deltaMs = (now - lastTime).coerceAtLeast(0)
            rotation = (rotation + 360f * deltaMs / 10000f) % 360f
            lastTime = now
            delay(16)
        }
    }

    // 异步加载封面：图片加载前先显示纯色唱片，旋转不等待
    val loadedBmp: androidx.compose.runtime.State<android.graphics.Bitmap?> =
        androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, albumArtUrl, cachedLoader) {
            if (albumArtUrl == null) {
                value = null
                return@produceState
            }
            try {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(albumArtUrl)
                    .allowHardware(false)
                    .crossfade(true)
                    .build()
                val result = cachedLoader.execute(request)
                value = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                value = null
            }
        }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val outerR = size.minDimension / 2
            val artR = outerR - outerR * 0.02f   // 封面半径，边缘缩窄为原来的1/3（原6%→现2%）
            val labelR = outerR * 0.36f
            val holeR = outerR * 0.08f  // 中心孔直径翻倍（原0.04→现0.08）

            val nc = drawContext.canvas.nativeCanvas

            // 用 saveLayer 把唱片内容画到离屏缓冲，再用 CLEAR 挖空中心孔
            nc.saveLayer(0f, 0f, size.width, size.height, null)

            // 限制所有绘制只在唱片圆形区域内，唱片外保持透明（露出页面背景）
            val vinylClip = android.graphics.Path().apply {
                addCircle(cx, cy, outerR, android.graphics.Path.Direction.CW)
            }
            nc.clipPath(vinylClip)

            // --- 第1层：金属质感边缘：白色高光 -> 银灰 -> 黑色（径向渐变，左上光源） ---
            val lightX = cx - outerR * 0.3f   // 光源偏左上
            val lightY = cy - outerR * 0.3f
            val edgePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                shader = android.graphics.RadialGradient(
                    lightX, lightY, outerR,
                    intArrayOf(
                        android.graphics.Color.parseColor("#F5F5F5"),  // 高光白
                        android.graphics.Color.parseColor("#BDBDBD"),  // 银灰
                        android.graphics.Color.parseColor("#1A1A1A"),  // 暗面
                        android.graphics.Color.parseColor("#0D0D0D")   // 边缘黑
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            nc.drawCircle(cx, cy, outerR, edgePaint)

            // --- 旋转的唱片主体 ---
            nc.save()
            nc.rotate(displayRotation, cx, cy)

            // 封面图片：裁剪为圆形铺满整张唱片
            val bmp = loadedBmp.value
            if (bmp != null && !bmp.isRecycled) {
                val path = android.graphics.Path().apply {
                    addCircle(cx, cy, artR, android.graphics.Path.Direction.CW)
                }
                nc.clipPath(path)
                val src = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                val dstRect = android.graphics.Rect(
                    (cx - artR).toInt(), (cy - artR).toInt(),
                    (cx + artR).toInt(), (cy + artR).toInt()
                )
                nc.drawBitmap(bmp, src, dstRect, android.graphics.Paint().apply { isAntiAlias = true })
            } else {
                nc.drawCircle(cx, cy, artR, android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#1A1A1A")
                })
            }

            // 唱片纹理：同心圆细线叠加在封面上（模拟凹槽）
            nc.save()
            nc.clipRect((cx - artR).toFloat(), (cy - artR).toFloat(),
                        (cx + artR).toFloat(), (cy + artR).toFloat())
            val groovePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.2f
                color = android.graphics.Color.parseColor("#40000000")
            }
            val grooveStep = (artR - labelR - artR * 0.04f) / 16
            var g = labelR + artR * 0.04f
            while (g <= artR - artR * 0.02f) {
                nc.drawCircle(cx, cy, g, groovePaint)
                g += grooveStep
            }
            nc.restore()

            // 封面之上的光泽高光（半透明渐变）
            val hlX = cx - artR * 0.25f
            val hlY = cy - artR * 0.25f
            val highlightPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                shader = android.graphics.RadialGradient(
                    hlX, hlY, artR * 0.9f,
                    android.graphics.Color.parseColor("#25FFFFFF"),
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            nc.drawCircle(cx, cy, artR, highlightPaint)

            // 封面中心孔（用 CLEAR 挖空，透明穿透显示页面背景）
            val holePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            nc.drawCircle(cx, cy, holeR, holePaint)

            // 唱片外圈描边（银灰色描边，贴合金属边缘内侧）
            nc.drawCircle(cx, cy, artR, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
                color = android.graphics.Color.parseColor("#AAAAAA")
            })

            // 中心标签区域（无封面时为深色纯圆，有封面时画一小圈深色衬托中心孔）
            nc.drawCircle(cx, cy, labelR, android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f
                color = android.graphics.Color.parseColor("#60FFFFFF")
            })

            nc.restore() // 恢复clip + 提交saveLayer（中心孔CLEAR穿透显示页面背景）
        }
    }
}

// ===== 专辑封面页面（内容区） =====
@Composable
private fun AlbumCoverPage(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    imageLoader: coil.ImageLoader
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // ===== 横屏布局：左边文字信息，右边唱片 =====
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：歌曲信息（竖向排列，字号加大，底部对齐）
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentSong != null) {
                    val song = currentSong
                    // 上部：歌曲信息（字号加大）
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 歌名阴影
                        Text(
                            text = song.name,
                            color = Color.Black.copy(alpha = 0.6f),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                        )
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = 34.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // 歌手阴影
                        Text(
                            text = song.artist,
                            color = Color.Black.copy(alpha = 0.6f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 专辑名阴影
                        Text(
                            text = song.album,
                            color = Color.Black.copy(alpha = 0.5f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                        )
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // 下部：进度条和时间（底部对齐）
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // 进度条
                        var isDragging by remember { mutableStateOf(false) }
                        var dragProgress by remember { mutableFloatStateOf(0f) }

                        Slider(
                            value = if (isDragging) dragProgress else {
                                if (playbackState.duration > 0) {
                                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                                } else 0f
                            },
                            onValueChange = { newProgress ->
                                if (!isDragging) isDragging = true
                                dragProgress = newProgress
                                onDrag((newProgress * playbackState.duration).toLong())
                            },
                            onValueChangeFinished = {
                                onSeek((dragProgress * playbackState.duration).toLong())
                                isDragging = false
                            },
                            valueRange = 0f..1f,
                        )

                        // 时间显示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(playbackState.duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 右侧：唱片
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                currentSong?.let { song ->
                    VinylRecord(
                        albumArtUrl = song.coverUrl ?: song.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f),
                        imageLoader = imageLoader
                    )
                }
            }
        }
    } else {
        // ===== 竖屏布局：上下排列，唱片居中 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (currentSong != null) {
                val song = currentSong

                // 专辑封面 - 唱片样式
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    VinylRecord(
                        albumArtUrl = song.coverUrl ?: song.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(1f),
                        imageLoader = imageLoader
                    )
                }

                // 歌曲信息
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 进度条
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }

                Slider(
                    value = if (isDragging) dragProgress else {
                        if (playbackState.duration > 0) {
                            playbackState.position.toFloat() / playbackState.duration.toFloat()
                        } else 0f
                    },
                    onValueChange = { newProgress ->
                        if (!isDragging) isDragging = true
                        dragProgress = newProgress
                        onDrag((newProgress * playbackState.duration).toLong())
                    },
                    onValueChangeFinished = {
                        onSeek((dragProgress * playbackState.duration).toLong())
                        isDragging = false
                    },
                    valueRange = 0f..1f,
                )

                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(playbackState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ===== 歌词页面（内容区） =====
@Composable
private fun LyricPage(
    currentSong: Song?,
    lyricState: LyricState,
    playbackState: PlaybackState,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDrag: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    isExpanded: Boolean = false  // true = pad (WindowWidthSizeClass.Expanded)
) {
    val lazyListState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (lyricState) {
                is LyricState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在加载歌词...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is LyricState.Success -> {
                    val lines = lyricState.lyrics
                    val position = playbackState.position

                    // 实时计算当前行，保证 position 变化时立即更新高亮
                    val currentLineIndex = lines.indices.lastOrNull { lines[it].timestamp <= position } ?: 0

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val currentLineFontSize = if (isLandscape) {
                            if (isExpanded) 50.sp else 36.sp
                        } else 24.sp
                        val otherLineFontSize = if (isLandscape) 28.sp else 15.sp
                        val nextLineFontSize = if (isLandscape) {
                            if (isExpanded) 44.sp else 32.sp
                        } else 20.sp
                        val lineHeightDp = if (isLandscape) 60.dp else 44.dp
                        val horizontalPadding = if (isExpanded) 48.dp else if (isLandscape) 40.dp else 24.dp
                        val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                        val lineHeightPx = with(LocalDensity.current) { lineHeightDp.toPx() }
                        val lineHeight3xPx = lineHeightPx * 3f

                        // 计算当前行在 LazyColumn 视口中的 Y 中心坐标
                        // 滚动时 firstVisibleItemIndex / firstVisibleItemScrollOffset 变化，触发 recomposition
                        val firstVisibleIdx = lazyListState.firstVisibleItemIndex
                        val firstVisibleOffset = lazyListState.firstVisibleItemScrollOffset
                        // 垂直 padding 必须 >= 0，防止 boxHeightPx < lineHeightPx 时崩溃
                        val verticalPaddingPx = maxOf(0f, (boxHeightPx - lineHeightPx) / 2f)
                        val currentLineCenterY = verticalPaddingPx +
                            (currentLineIndex - firstVisibleIdx) * lineHeightPx -
                            firstVisibleOffset +
                            lineHeightPx / 2f

                        // 行偏移量：PAD 当前行在第3行(偏移2)，手机竖屏当前行在第5行(偏移4)，横屏不需要歌词
                        val rowOffset = if (isLandscape) 0 else if (isExpanded) 2 else 4

                        // 目标行始终为 currentLineIndex（切歌时自动更新）
                        // 只监听 currentLineIndex，避免播放进度变化时频繁触发滚动
                        LaunchedEffect(currentLineIndex) {
                            if (lines.isNotEmpty() && currentLineIndex in lines.indices) {
                                // debounce：只在前一个动画真正完成后才启动新的
                                kotlinx.coroutines.delay(100)
                                if (!lazyListState.isScrollInProgress) {
                                    lazyListState.animateScrollToItem(
                                        index = currentLineIndex,
                                        scrollOffset = (verticalPaddingPx + (currentLineIndex - rowOffset) * lineHeightPx).toInt()
                                    )
                                }
                            }
                        }

                        // 渐变叠加层：绝对定位于当前行中心线，上下各 1.5 倍行高
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 单一垂直渐变背景：定位于当前行中心线，上下各 1.5x 行高（在歌词之后）
                            // 上下限 clamp 防止 offset 越界
                            val gradientTopY = (currentLineCenterY - lineHeight3xPx / 2f).coerceIn(0f, boxHeightPx - lineHeight3xPx)
                            val gradientHeight = lineHeight3xPx.coerceAtMost(boxHeightPx)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = with(LocalDensity.current) { gradientTopY.toDp() })
                                    .height(with(LocalDensity.current) { gradientHeight.toDp() })
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Black.copy(alpha = 0f),
                                                0.5f to Color.Black.copy(alpha = 0.80f),
                                                1f to Color.Black.copy(alpha = 0f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(0.dp)
                                    )
                            )

                            val verticalPadding = with(LocalDensity.current) { verticalPaddingPx.toDp() }
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizontalPadding),
                                horizontalAlignment = Alignment.Start,
                                contentPadding = PaddingValues(vertical = verticalPadding)
                            ) {
                                itemsIndexed(
                                    items = lines,
                                    key = { idx, _ -> idx }
                                ) { idx, lyricLine ->
                                    val isCurrent = idx == currentLineIndex
                                    val isNext = idx == currentLineIndex + 1
                                    val alpha by animateFloatAsState(
                                        targetValue = if (isCurrent) 1f else 0.45f,
                                        animationSpec = tween(300),
                                        label = "lyricAlpha_$idx"
                                    )
                                    val textColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                    Box {
                                        // 阴影层（黑色描边）
                                        if (isCurrent) {
                                            Text(
                                                text = lyricLine.text,
                                                color = Color.Black.copy(alpha = 0.8f),
                                                fontSize = currentLineFontSize,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(x = 1.5.dp, y = 1.5.dp)
                                                    .graphicsLayer {
                                                        scaleX = if (isCurrent) 1.05f else 1f
                                                        scaleY = if (isCurrent) 1.05f else 1f
                                                    }
                                            )
                                        }
                                        // 主文字层
                                        Text(
                                            text = lyricLine.text,
                                            fontSize = if (isCurrent) currentLineFontSize else if (isNext) nextLineFontSize else otherLineFontSize,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = if (isCurrent) 1.05f else 1f
                                                    scaleY = if (isCurrent) 1.05f else 1f
                                                }
                                                .clickable { onSeek(lyricLine.timestamp) }
                                                .padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // 顶部渐变遮罩（边缘渐隐）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(LocalDensity.current) { (boxHeightPx / 2).toDp() })
                                    .align(Alignment.TopCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            // 底部渐变遮罩
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(LocalDensity.current) { (boxHeightPx / 2).toDp() })
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )

                            LaunchedEffect(isUserScrolling, currentLineIndex) {
                                if (!isUserScrolling && lines.isNotEmpty()) {
                                    kotlinx.coroutines.delay(5000)
                                    if (!lazyListState.isScrollInProgress) {
                                        lazyListState.animateScrollToItem(index = currentLineIndex)
                                    }
                                }
                            }
                        }
                    }
                }
                is LyricState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = lyricState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is LyricState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "正在加载歌词...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // 进度条（始终显示）
            val config = LocalConfiguration.current
            val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                // 横屏：底部横向 Slider
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column {
                        Slider(
                            value = if (isDragging) dragProgress else {
                                if (playbackState.duration > 0) {
                                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                                } else 0f
                            },
                            onValueChange = { newProgress ->
                                if (!isDragging) isDragging = true
                                dragProgress = newProgress
                                onDrag((newProgress * playbackState.duration).toLong())
                            },
                            onValueChangeFinished = {
                                onSeek((dragProgress * playbackState.duration).toLong())
                                isDragging = false
                            },
                            valueRange = 0f..1f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(playbackState.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // 竖屏：右侧纵向进度条（可拖动）+ 时间标签
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                val progress = if (playbackState.duration > 0) {
                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                } else 0f
                // 用 Channel 把 pointerInput 协程里的绝对 Y 坐标传进组合层级
                val dragChannel = remember { Channel<Float>(Channel.RENDEZVOUS) }
                LaunchedEffect(dragChannel) {
                    for (absY in dragChannel) {
                        dragProgress = absY.coerceIn(0f, 1f)
                    }
                }
                val displayProgress = if (isDragging) dragProgress else progress
                val density = LocalDensity.current

                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .fillMaxHeight(0.95f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragProgress = progress
                                },
                                onDragEnd = {
                                    onSeek((dragProgress * playbackState.duration).toLong())
                                    isDragging = false
                                },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, _ ->
                                    // 用手指的绝对 Y 位置（相对于进度条顶部）算进度
                                    val absProgress = change.position.y / size.height
                                    dragChannel.trySend(absProgress.coerceIn(0f, 1f))
                                }
                            )
                        }
                ) {
                    // 使用 density.density 和 Dp.value 做像素换算，避免 toPx/toDp 扩展函数
                    val densityVal = density.density
                    val barHeightPx = maxHeight.value * densityVal
                    val thumbDiaPx = 18f * densityVal
                    // 圆形 Y：progress=0→顶部，progress=1→底部（初始在顶部，向下移动）
                    val thumbY = (barHeightPx * displayProgress - thumbDiaPx / 2).coerceIn(0f, barHeightPx - thumbDiaPx)
                    // 红色填充高度（从顶部往下）
                    val fillHeightPx = barHeightPx * displayProgress

                    // 轨道背景（灰色，居中）
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 已播放进度（红色，居中）
                    Box(
                        modifier = Modifier
                            .height((fillHeightPx / densityVal).dp)
                            .width(6.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                AppColors.Accent,
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 圆形指示器（居中）
                    Box(
                        modifier = Modifier
                            .offset(y = (thumbY / densityVal).dp)
                            .size(18.dp)
                            .align(Alignment.TopCenter)
                            .background(AppColors.Accent, CircleShape)
                    )
                }
                // 时间标签
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 30.dp)
                        .fillMaxHeight(0.95f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Accent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = formatDuration(playbackState.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

// ===== 辅助函数 =====
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ===== 添加到歌单弹窗 =====
@Composable
private fun SavePlaylistDialog(
    songCount: Int,
    playlists: List<UserPlaylist>,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var showNewPlaylistInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20B1E10),
        title = { Text("保存到歌单", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (songCount > 0) {
                    Text("将 $songCount 首歌曲保存到歌单", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (showNewPlaylistInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("歌单名称") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showNewPlaylistInput = false }) { Text("取消", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    showNewPlaylistInput = false
                                    onDismiss()
                                }
                            }
                        ) { Text("创建", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text("暂无歌单，请先创建", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPlaylist(playlist.id)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${playlist.songs.size}首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showNewPlaylistInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("创建新歌单", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
        }
    )
}

// ===== 定时关闭弹窗 =====
@Composable
fun SleepTimerDialog(
    isActive: Boolean,
    remainingSeconds: Long,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }

    val presetMinutes = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭 🌙") },
        text = {
            Column {
                if (isActive) {
                    // 定时已开启，显示剩余时间和取消按钮
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Text(
                        text = "距离停止播放还有",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${mins}分${secs}秒",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消定时", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    // 未开启，显示预设时间选项
                    Text(
                        text = "播放将在以下时间后自动停止：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetMinutes.forEach { mins ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onStartTimer(mins)
                                    onDismiss()
                                },
                                label = { Text("${mins}分钟") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                            label = { Text("自定义") },
                            suffix = { Text("分钟") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val mins = customMinutes.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    onStartTimer(mins)
                                    onDismiss()
                                }
                            },
                            enabled = customMinutes.isNotEmpty() && (customMinutes.toIntOrNull() ?: 0) > 0
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {}
    )
}

/** 手机横屏播放页面：左侧封面+信息+进度条，右侧滚动歌词 */
@Composable
fun PlayerScreenLandscape(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    isFavorite: Boolean,
    lyricState: LyricState,
    onBackClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    imageLoader: coil.ImageLoader
) {
    val backgroundColor = Color(0xFF0D0D1A)
    val accentColor = AppColors.Accent

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // 模糊背景封面
        currentSong?.let { song ->
            val coverUrl = song.coverUrl ?: song.pic
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.3f },
                contentScale = ContentScale.Crop
            )
        }

        if (currentSong == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无正在播放的歌曲", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧：黑胶唱片 + 控制面板（占 2/5）
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val coverSize = minOf(maxWidth * 0.9f, maxHeight * 0.9f)

                    // 1. 大黑胶唱片铺满（旋转动画）
                    VinylRecord(
                        albumArtUrl = currentSong.coverUrl ?: currentSong.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier.size(coverSize),
                        imageLoader = imageLoader
                    )

                    // 2. 底部半透明控制面板（进度条 + 按钮）
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        Color.Black.copy(alpha = 0.3f),
                                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 进度条
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Slider(
                                        value = if (playbackState.duration > 0) {
                                            playbackState.position.toFloat() / playbackState.duration.toFloat()
                                        } else 0f,
                                        onValueChange = { fraction ->
                                            onSeek((fraction * playbackState.duration).toLong())
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatTime(playbackState.position),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = formatTime(playbackState.duration),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 播放控制按钮
                                val screenWidth = LocalConfiguration.current.screenWidthDp
                                val btnSize = (24 + (screenWidth - 360) * 0.03f).coerceIn(22f, 32f).dp

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onTogglePlayMode) {
                                        val (icon, desc) = when (playMode) {
                                            0 -> Icons.Default.Repeat to "列表循环"
                                            1 -> Icons.Default.Repeat to "单曲循环"
                                            else -> Icons.Default.Shuffle to "随机播放"
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = desc,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(btnSize)
                                        )
                                    }

                                    IconButton(onClick = onPrevious) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "上一首",
                                            tint = Color.White,
                                            modifier = Modifier.size(btnSize)
                                        )
                                    }

                                    FilledIconButton(
                                        onClick = onTogglePlay,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = accentColor
                                        ),
                                        modifier = Modifier.size(btnSize * 1.5f)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "暂停" else "播放",
                                            tint = Color.White,
                                            modifier = Modifier.size(btnSize)
                                        )
                                    }

                                    IconButton(onClick = onNext) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "下一首",
                                            tint = Color.White,
                                            modifier = Modifier.size(btnSize)
                                        )
                                    }

                                    IconButton(onClick = onToggleFavorite) {
                                        FavoriteIcon(
                                            isFavorite = isFavorite,
                                            tint = if (isFavorite) AppColors.FavoriteActive else Color.White.copy(alpha = 0.8f),
                                            contentDescription = "收藏",
                                            modifier = Modifier.size(btnSize)
                                        )
                                    }
                                }
                            }

                    // 左上角大字歌名（与歌词当前行样式一致）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentSong.name,
                            color = Color.Black.copy(alpha = 0.8f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                        )
                        Text(
                            text = currentSong.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ==================== 右侧：滚动歌词（占 3/5）====================
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                ) {
                    when (val state = lyricState) {
                        is LyricState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                        is LyricState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "歌词加载失败",
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is LyricState.Success -> {
                            val lyrics = state.lyrics
                            if (lyrics.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "纯音乐，欣赏中...",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                val currentIndex = lyrics.indexOfLast { it.timestamp <= playbackState.position }
                                    .coerceAtLeast(0)

                                val listState = rememberLazyListState()
                                val density = LocalDensity.current
                                val itemSpacing = with(density) { 12.dp.toPx() }
                                val itemHeight = with(density) { 32.dp.toPx() }
                                val totalItemHeight = itemHeight + itemSpacing
                                val paddingTop = with(density) { 16.dp.toPx() }

                                LaunchedEffect(currentIndex) {
                                    listState.animateScrollToItem(
                                        index = maxOf(0, currentIndex - 1),
                                        scrollOffset = -((listState.layoutInfo.viewportSize.height / 2) - (2 * totalItemHeight + itemHeight / 2)).toInt()
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = listState,
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                                ) {
                                    itemsIndexed(lyrics) { index, line ->
                                        val isCurrent = index == currentIndex
                                        val isNext = index == currentIndex + 1
                                        val alpha = if (isCurrent) 1f else 0.5f
                                        val fontSize = if (isCurrent) 24.sp else if (isNext) 20.sp else 14.sp

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            // 主文字层
                                            Box {
                                                // 阴影层（黑色描边）
                                                if (isCurrent) {
                                                    Text(
                                                        text = line.text.ifEmpty { " " },
                                                        color = Color.Black.copy(alpha = 0.8f),
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                                                    )
                                                }
                                                // 主文字层
                                                Text(
                                                    text = line.text.ifEmpty { " " },
                                                    color = Color.White.copy(alpha = alpha),
                                                    fontSize = fontSize,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(start = 0.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is LyricState.Idle -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "歌词加载中...",
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

/**
 * 收藏图标：选中时白色描边 + AppColors.FavoriteActive 填充
 */
@Composable
private fun FavoriteIcon(
    isFavorite: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = contentDescription,
        tint = if (isFavorite) AppColors.FavoriteActive else Color.Transparent,
        modifier = modifier
    )
    Icon(
        imageVector = Icons.Outlined.FavoriteBorder,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
    )
}
