package com.zongting.zongting.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.ui.LyricLine
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricState by viewModel.lyricState.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // 当前歌曲变化时自动获取歌词
    LaunchedEffect(currentSong?.rid) {
        currentSong?.let { song ->
            viewModel.fetchLyric(song.rid, song.source, song.name, song.artist)
        }
    }

    // 定期同步播放进度（每秒更新一次 position 和 duration）
    // seek 后的 2 秒内跳过更新，等待 ExoPlayer 真正 seek 到目标位置
    var isSeeking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!isSeeking) {
                val pos = PlayerManager.currentPosition
                val dur = PlayerManager.duration
                if (dur > 0) {
                    viewModel.updateProgress(pos, dur)
                }
            }
            kotlinx.coroutines.delay(500L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部导航
        TopAppBar(
            title = { Text("正在播放") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
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

        // 左右滑动页面
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> AlbumCoverPage(
                    currentSong = currentSong,
                    currentPlaylist = currentPlaylist,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    playMode = viewModel.playMode.value,
                    onTogglePlayMode = { viewModel.togglePlayMode() },
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
                    onToggleFavorite = {
                        currentSong?.let { viewModel.toggleFavorite(it) }
                    },
                    isFavorite = currentSong?.let { viewModel.isFavorite(it.rid) } ?: false,
                    onPlaySong = { song -> viewModel.playSong(song, currentPlaylist) }
                )
                1 -> LyricPage(
                    currentSong = currentSong,
                    lyricState = lyricState,
                    playbackState = playbackState,
                    isPlaying = isPlaying,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onToggleFavorite = {
                        currentSong?.let { viewModel.toggleFavorite(it) }
                    },
                    isFavorite = currentSong?.let { viewModel.isFavorite(it.rid) } ?: false,
                    playMode = viewModel.playMode.value,
                    onTogglePlayMode = { viewModel.togglePlayMode() }
                )
            }
        }
    }
}

@Composable
private fun AlbumCoverPage(
    currentSong: Song?,
    currentPlaylist: List<Song>,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayMode: () -> Unit,
    isFavorite: Boolean,
    onPlaySong: (Song) -> Unit
) {
    // 播放模式：0=顺序播放, 1=单曲循环, 2=随机播放
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val playlistListState = rememberLazyListState()
    val playModeIcon = when (playMode) {
        0 -> Icons.Default.Repeat
        1 -> Icons.Default.RepeatOne
        else -> Icons.Default.Shuffle
    }
    val playModeDesc = when (playMode) {
        0 -> "顺序播放"
        1 -> "单曲循环"
        else -> "随机播放"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        if (currentSong != null) {
            val song = currentSong

            // 专辑封面 — 自适应中间空间，填满可用区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.pic,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
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

            // 进度条（支持拖拽，拖拽时实时更新歌词位置）
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
                    // 实时更新歌词位置（不 seek 播放器，避免播放跳变）
                    onDrag((newProgress * playbackState.duration).toLong())
                },
                onValueChangeFinished = {
                    // 松手时才真正 seek 播放器
                    onSeek((dragProgress * playbackState.duration).toLong())
                    isDragging = false
                },
                valueRange = 0f..1f,
            )

            // 时间显示（拖拽时实时显示目标时间）
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

            Spacer(modifier = Modifier.height(12.dp))

            // 播放控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(28.dp)
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 附加功能按钮（播放模式/播放列表/收藏）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = onTogglePlayMode) {
                        Icon(
                            imageVector = playModeIcon,
                            contentDescription = playModeDesc,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "播放列表",
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
            }
        }
    }

    // 播放列表展开时自动滚动到当前歌曲
    LaunchedEffect(showPlaylistSheet, currentSong) {
        if (showPlaylistSheet && currentSong != null) {
            val index = currentPlaylist.indexOfFirst { it.rid == currentSong.rid }
            if (index >= 0) {
                playlistListState.animateScrollToItem(
                    index = (index - 1).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    // 播放列表底部弹出面板
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
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
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
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

@Composable
fun LyricPage(
    currentSong: Song?,
    lyricState: LyricState,
    playbackState: PlaybackState,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    playMode: Int,
    onTogglePlayMode: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 歌词内容区域
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

                        val currentLineIndex = remember(lines, position) {
                            var idx = 0
                            for ((i, line) in lines.withIndex()) {
                                if (position >= line.timestamp) idx = i
                            }
                            idx
                        }

                        val lineHeightDp = 40.dp
                        val estimatedLineHeightPx = with(LocalDensity.current) { lineHeightDp.toPx().toInt() }

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }
                            val topPaddingPx = boxHeightPx / 2 - estimatedLineHeightPx / 2

                            LaunchedEffect(currentLineIndex) {
                                if (lines.isNotEmpty() && currentLineIndex in lines.indices) {
                                    lazyListState.animateScrollToItem(index = currentLineIndex)
                                }
                            }

                            LaunchedEffect(lazyListState) {
                                snapshotFlow { lazyListState.isScrollInProgress }
                                    .collect { isScrolling ->
                                        if (isScrolling) isUserScrolling = true
                                        else if (isUserScrolling) {
                                            kotlinx.coroutines.delay(500)
                                            if (!lazyListState.isScrollInProgress) isUserScrolling = false
                                        }
                                    }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    contentPadding = PaddingValues(
                                        top = with(LocalDensity.current) { topPaddingPx.toDp() },
                                        bottom = with(LocalDensity.current) { topPaddingPx.toDp() }
                                    )
                                ) {
                                    itemsIndexed(lines) { index, lyricLine ->
                                        val isCurrentLine = index == currentLineIndex
                                        Text(
                                            text = lyricLine.text,
                                            fontSize = if (isCurrentLine) 18.sp else 14.sp,
                                            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrentLine)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                        )
                                    }
                                }

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
                                            lazyListState.animateScrollToItem(
                                                index = currentLineIndex,
                                                scrollOffset = -topPaddingPx
                                            )
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
                            Text(
                                text = lyricState.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is LyricState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "正在加载歌词...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 底部播放控制栏（固定在页面最下方）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // 上一首 / 播放暂停 / 下一首
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "上一首",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "下一首",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 播放模式 / 收藏 按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val playModeIcon = when (playMode) {
                            1 -> Icons.Default.RepeatOne
                            2 -> Icons.Default.Shuffle
                            else -> Icons.Default.Repeat
                        }
                        IconButton(onClick = onTogglePlayMode) {
                            Icon(
                                playModeIcon,
                                contentDescription = "播放模式",
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
                }
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
