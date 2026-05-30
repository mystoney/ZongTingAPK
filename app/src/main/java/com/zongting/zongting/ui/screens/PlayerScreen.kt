package com.zongting.zongting.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.LyricLine
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
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
                title = { Text("正在播放") },
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
                        text = { Text("播放") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("歌词") }
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
                        onSeek = { viewModel.seekTo(it) }
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
            playlistListState = rememberLazyListState()
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
    playlistListState: androidx.compose.foundation.lazy.LazyListState
) {
    val playModeIcon = when (playMode) {
        1 -> Icons.Default.RepeatOne
        2 -> Icons.Default.Shuffle
        else -> Icons.Default.Repeat
    }
    val playModeDesc = when (playMode) {
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // 上一首 / 播放暂停 / 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(28.dp))
                }

                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(56.dp), shape = CircleShape) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(28.dp))
                }
            }

            // 附加功能按钮（播放模式 / 播放列表 / 添加到歌单 / 收藏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onTogglePlayMode) {
                    Icon(imageVector = playModeIcon, contentDescription = playModeDesc, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = { onShowPlaylist(true) }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "播放列表", modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onToggleSavePlaylist) {
                    Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = "保存到歌单", modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onSleepTimerClick) {
                    Icon(
                        imageVector = if (isTimerActive) Icons.Default.BedtimeOff else Icons.Default.Bedtime,
                        contentDescription = "定时关闭",
                        tint = if (isTimerActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { onRingtoneCutterClick() }) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "设为铃声",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                        tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
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
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "当前播放列表 (${currentPlaylist.size}首)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (currentPlaylist.isEmpty()) {
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrentSong) {
                                    IconButton(onClick = onTogglePlay) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "暂停" else "播放",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
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

    val rotation = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val animatedRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying) rotation.floatValue + 360f else rotation.floatValue,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 6000, easing = androidx.compose.animation.core.LinearEasing),
        label = "vinyl_rotation"
    )

    // 持续旋转角度（播放时累加，暂停时保持）
    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                kotlinx.coroutines.delay(6000)
                rotation.floatValue += 360f
            }
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

            // 限制所有绘制只在唱片圆形区域内，唱片外保持透明（露出页面背景）
            val vinylClip = android.graphics.Path().apply {
                addCircle(cx, cy, outerR, android.graphics.Path.Direction.CW)
            }
            nc.save()
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
            nc.rotate(animatedRotation, cx, cy)

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

            // 封面中心孔（画实心页面背景色，模拟透明效果）
            nc.drawCircle(cx, cy, holeR, android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#121212")
            })

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

            nc.restore() // 恢复旋转
            nc.restore() // 恢复clip（唱片外保持透明）
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
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = 34.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                        albumArtUrl = song.pic,
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
                        albumArtUrl = song.pic,
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
    onSeek: (Long) -> Unit
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

                    // 实时计算当前行（不用 remember，避免缓存导致高亮滞后）
                    // 实时计算当前行，保证 position 变化时立即更新高亮
                    val currentLineIndex = lines.indices.lastOrNull { lines[it].timestamp <= position } ?: 0

                    val lineHeightDp = 44.dp

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        // 横屏时歌词字体更大，当前行和下一行字体相同
                        val currentLineFontSize = if (isLandscape) 32.sp else 20.sp
                        val otherLineFontSize = if (isLandscape) 24.sp else 15.sp
                        val lineHeightDp2 = if (isLandscape) 60.dp else 44.dp
                        val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                        val lineHeightPx = with(LocalDensity.current) { lineHeightDp2.toPx() }

                        // position 变化时强制重新触发滚动
                        LaunchedEffect(position, currentLineIndex) {
                            if (lines.isNotEmpty() && currentLineIndex in lines.indices) {
                                lazyListState.animateScrollToItem(index = currentLineIndex)
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val verticalPadding = with(LocalDensity.current) { ((boxHeightPx - lineHeightPx) / 2).toDp() }
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                contentPadding = PaddingValues(vertical = verticalPadding)
                            ) {
                                itemsIndexed(lines) { index, lyricLine ->
                                    val isCurrentLine = index == currentLineIndex
                                    val isNextLine = index == currentLineIndex + 1
                                    val alpha by animateFloatAsState(
                                        targetValue = if (isCurrentLine) 1f else 0.45f,
                                        animationSpec = tween(300),
                                        label = "lyricAlpha"
                                    )
                                    Text(
                                        text = lyricLine.text,
                                        fontSize = if (isCurrentLine || isNextLine) currentLineFontSize else otherLineFontSize,
                                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrentLine)
                                            Color(0xFFE53935).copy(alpha = alpha)
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = if (isCurrentLine) 1.05f else 1f
                                                scaleY = if (isCurrentLine) 1.05f else 1f
                                            }
                                            .padding(vertical = 8.dp)
                                            .clickable { onSeek(lyricLine.timestamp) }
                                    )
                                }
                            }

                            // 顶部渐变遮罩
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
                                    dragProgress = displayProgress
                                },
                                onDragEnd = {
                                    onSeek((dragProgress * playbackState.duration).toLong())
                                    isDragging = false
                                },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { _, dragAmount ->
                                    val fractionDelta = -dragAmount / size.height
                                    val newProgress = (displayProgress + fractionDelta).coerceIn(0f, 1f)
                                    if (isDragging) {
                                        dragProgress = newProgress
                                        onDrag((newProgress * playbackState.duration).toLong())
                                    }
                                }
                            )
                        }
                ) {
                    // 使用 density.density 和 Dp.value 做像素换算，避免 toPx/toDp 扩展函数
                    val densityVal = density.density
                    val barHeightPx = maxHeight.value * densityVal
                    val thumbDiaPx = 12f * densityVal
                    // 圆形 Y：进度 0=底部，进度 1=顶部
                    val thumbY = (barHeightPx * (1f - displayProgress) - thumbDiaPx / 2).coerceIn(0f, barHeightPx - thumbDiaPx)

                    // 轨道背景
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 已播放进度（从上往下填充）
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(displayProgress)
                            .width(6.dp)
                            .background(
                                Color(0xFFE53935),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 圆形指示器（直径=进度条宽度的两倍=12dp）
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = (thumbY / densityVal).dp)
                                .size(12.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                    }
                }
                // 时间标签
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp)
                        .fillMaxHeight(0.65f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935),
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
        title = { Text("保存到歌单") },
        text = {
            Column {
                if (songCount > 0) {
                    Text("将 $songCount 首歌曲保存到歌单", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (showNewPlaylistInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showNewPlaylistInput = false }) { Text("取消") }
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    showNewPlaylistInput = false
                                    onDismiss()
                                }
                            }
                        ) { Text("创建") }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text("暂无歌单，请先创建", style = MaterialTheme.typography.bodyMedium)
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
                                    Text("${playlist.songs.size}首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("创建新歌单")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
