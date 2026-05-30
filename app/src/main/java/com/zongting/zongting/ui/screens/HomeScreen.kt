package com.zongting.zongting.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.ceil
import kotlin.collections.chunked
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import android.util.Log
import com.zongting.zongting.data.model.Banner
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.Playlist
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.AdaptiveLayout
import com.zongting.zongting.ui.PAD_MAX_CONTENT_WIDTH_DP
import com.zongting.zongting.ui.MainViewModel
import com.zongting.zongting.data.repository.UpdatePhase
import com.zongting.zongting.data.repository.UpdateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onPlaylistClick: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaylistPlay: (Long) -> Unit,
    onSongPlay: (Song) -> Unit,
    onBangClick: (String) -> Unit,  // 热门榜单点击 → 跳转排行榜
    onBangPlay: (Bang) -> Unit,  // 排行榜全部播放
    windowSizeClass: WindowSizeClass? = null,
    isLandscapePhone: Boolean = false,
    onGoToPlayer: () -> Unit = {}
) {
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val uiState by viewModel.uiState.collectAsState()
    val updatePhase by updateViewModel.updatePhase.collectAsState()
    val updateEvent by updateViewModel.updateEvent.collectAsState()

    // 进度条尺寸
    val barWidthDp = 90.dp
    val barHeightDp = 22.dp

    // 直接调用加载，ViewModel 层已有防重复守卫
    LaunchedEffect(Unit) {
        Log.d("HomeDebug", "HomeScreen: LaunchedEffect fired, calling loadHomeDataIfNeeded")
        viewModel.loadHomeDataIfNeeded()
    }

    if (isExpanded) {
        // 平板横屏：内容区固定高度 + Column 撑满，miniplayer 自然遮挡底部（5列2行网格）
        HomeScreenContent(
            uiState = uiState,
            updatePhase = updatePhase,
            updateEvent = updateEvent,
            viewModel = viewModel,
            updateViewModel = updateViewModel,
            onPlaylistClick = onPlaylistClick,
            onSongClick = onSongClick,
            onPlaylistPlay = onPlaylistPlay,
            onSongPlay = onSongPlay,
            onBangClick = onBangClick,
            onBangPlay = onBangPlay,
            isExpanded = true,
            availableWidthDp = PAD_MAX_CONTENT_WIDTH_DP.dp
        )
    } else if (isLandscapePhone) {
        // 手机横屏：简化布局，无Banner，点击图标直接进入播放页
        HomeScreenLandscape(
            uiState = uiState,
            onGoToPlayer = onGoToPlayer
        )
    } else {
        // 手机布局：保持原有行为
        HomeScreenContent(
            uiState = uiState,
            updatePhase = updatePhase,
            updateEvent = updateEvent,
            viewModel = viewModel,
            updateViewModel = updateViewModel,
            onPlaylistClick = onPlaylistClick,
            onSongClick = onSongClick,
            onPlaylistPlay = onPlaylistPlay,
            onSongPlay = onSongPlay,
            onBangClick = onBangClick,
            onBangPlay = onBangPlay,
            isExpanded = false,
            availableWidthDp = null
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    updatePhase: UpdatePhase,
    updateEvent: UpdateEvent?,
    viewModel: HomeViewModel,
    updateViewModel: UpdateViewModel,
    onPlaylistClick: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaylistPlay: (Long) -> Unit,
    onSongPlay: (Song) -> Unit,
    onBangClick: (String) -> Unit,
    onBangPlay: (Bang) -> Unit,
    isExpanded: Boolean,
    availableWidthDp: Dp?,
) {
    val barWidthDp = 90.dp
    val barHeightDp = 22.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部标题
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 根据阶段显示进度条/按钮
                    when (updatePhase) {
                        UpdatePhase.Idle -> { /* 不显示 */ }

                        UpdatePhase.UpdateAvailable -> {
                            Box(
                                modifier = Modifier
                                    .width(barWidthDp)
                                    .height(barHeightDp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        updateViewModel.onConfirmDownload()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "下载更新",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        UpdatePhase.Downloading -> {
                            val progress = (updateEvent as? UpdateEvent.Downloading)?.progress ?: 0
                            val fraction = (progress / 100f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(barWidthDp)
                                    .height(barHeightDp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = fraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "正在下载",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        UpdatePhase.Downloaded -> {
                            Box(
                                modifier = Modifier
                                    .width(barWidthDp)
                                    .height(barHeightDp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        updateViewModel.onConfirmInstall()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "等待安装",
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refreshHomeData() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新推荐"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (uiState.isLoading) {
            Log.d("HomeDebug", "HomeScreen: showing spinner, playlists=${uiState.playlists.size}")
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 165.dp)
            ) {
                // 轮播图
                if (uiState.banners.isNotEmpty()) {
                    item {
                        BannerCarousel(banners = uiState.banners)
                    }
                }

                // 推荐内容垂直滚动（热门歌曲 / 每日推荐 / 排行榜）
                item {
                    RecommendContent(
                        playlists = uiState.playlists,
                        hotSongs = uiState.hotSongs,
                        hotBangs = uiState.hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        onBangPlay = onBangPlay,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                }

                // 错误提示
                uiState.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BannerCarousel(banners: List<Banner>) {
    val pagerState = rememberPagerState(pageCount = { banners.size })
    val scope = rememberCoroutineScope()
    var selectedBannerImage by remember { mutableStateOf<String?>(null) }
    var selectedImageSize by remember { mutableStateOf(Pair(1f, 1f)) }
    val configuration = LocalConfiguration.current
    val screenWidthPx = configuration.screenWidthDp.dp

    // 自动轮播
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % banners.size)
        }
    }

    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp)
        ) { page ->
            val banner = banners[page]
            val imageUrl = banner.newPic.ifEmpty { banner.pic }
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedBannerImage = imageUrl },
                contentScale = ContentScale.Crop
            )
        }

        // 可点击的指示器圆点
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(banners.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        .clickable {
                            scope.launch {
                                pagerState.scrollToPage(index)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    cardWidth: Dp,
    iconWidth: Dp
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(iconWidth)
        ) {
            val iconDpWidth: Dp = iconWidth
            // 按钮：直径=图标宽×30%，三角形=按钮×80%，offset=80%
            val btnSizeDp: Dp = iconDpWidth * 0.30f      // 按钮=图标×30%
            val icSizeDp: Dp = btnSizeDp * 0.80f        // 三角=按钮×80%
            val xOffset = (iconDpWidth.value * 0.80f - btnSizeDp.value / 2).dp
            val yOffset = xOffset

            // 图标区域：独立Box包裹并裁剪
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = playlist.img300,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // 播放按钮覆盖层：圆心对齐图标右下角，完全在图标内
            // 按钮=图标×8%，三角形=按钮×80%，offset=75%
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xOffset, y = yOffset)
                    .size(btnSizeDp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(btnSizeDp / 2))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放全部",
                    tint = Color.White,
                    modifier = Modifier.size(icSizeDp),
                )
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 热门榜单 — 横向滚动卡片 */
@Composable
fun HotBangsRow(bangs: List<Bang>, onBangClick: (String) -> Unit, onBangPlay: (Bang) -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val cardWidth: Dp = ((screenWidth - 32 - 24) / 3).dp
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bangs) { bang ->
            BangCard(bang = bang, onClick = { onBangClick(bang.id) }, onPlayClick = { onBangPlay(bang) }, cardWidth = cardWidth)
        }
    }
}

/** 每日推荐 — 横向滚动小卡片（2列一行 x3行） */
@Composable
fun DailyRecommendRow(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onPlaylistPlay: (Long) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val cardWidth: Dp = ((screenWidth - 32 - 16) / 3).dp
    val rows = playlists.chunked(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { playlist ->
                    PlaylistCard(
                        playlist,
                        { onPlaylistClick(playlist.id) },
                        { onPlaylistPlay(playlist.id) },
                        cardWidth,
                        cardWidth
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    isExpanded: Boolean = false
) {
    val scale = if (isExpanded) 1.3f else 1f
    val baseCoverSize = 48.dp
    val baseBadgeSize = 15.dp
    val baseBadgeIconSize = 12.dp
    val scaledCoverSize = (baseCoverSize.value * scale).dp.coerceAtLeast(baseCoverSize)
    val scaledBadgeSize = (baseBadgeSize.value * scale).dp.coerceAtLeast(baseBadgeSize)
    val scaledBadgeIconSize = (baseBadgeIconSize.value * scale).dp.coerceAtLeast(baseBadgeIconSize)

    ListItem(
        headlineContent = {
            Text(
                text = song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${song.artist} - ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Box {
                AsyncImage(
                    model = song.pic120,
                    contentDescription = null,
                    modifier = Modifier
                        .size(scaledCoverSize)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                // VIP/版权限制锁图标
                if (!song.playable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(scaledBadgeSize)
                            .background(
                                if (song.fee == 1)
                                    Color(0xFFFF6B35).copy(alpha = 0.9f)  // VIP 橙色
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.fee == 1) {
                            Text(
                                text = "VIP",
                                fontSize = 7.sp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "版权限制",
                                modifier = Modifier.size(scaledBadgeIconSize),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Text(
                text = song.displayDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** 垂直滚动展示全部推荐内容（热门歌曲 / 每日推荐 / 推荐歌单 / 排行榜） */
@Composable
fun RecommendContent(
    playlists: List<Playlist>,
    hotSongs: List<Song>,
    hotBangs: List<Bang>,
    onPlaylistClick: (Long) -> Unit,
    onPlaylistPlay: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongPlay: (Song) -> Unit,
    onBangClick: (String) -> Unit,
    onBangPlay: (Bang) -> Unit,
    isExpanded: Boolean = false,
    availableWidthDp: Dp? = null
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val numCols = if (isExpanded) 6 else 3
        val totalGaps = 8.dp * (numCols - 1)
        val cardW = (maxWidth - totalGaps) / numCols

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 每日推荐
            if (playlists.isNotEmpty()) {
                SectionTitle("每日推荐")
                playlists.chunked(numCols).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { playlist ->
                            PlaylistCard(
                                playlist,
                                { onPlaylistClick(playlist.id) },
                                { onPlaylistPlay(playlist.id) },
                                cardW,
                                cardW
                            )
                        }
                        repeat(numCols - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 排行榜
            if (hotBangs.isNotEmpty()) {
                SectionTitle("排行榜")
                hotBangs.chunked(numCols).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { bang ->
                            BangCard(
                                bang = bang,
                                onClick = { onBangClick(bang.id) },
                                onPlayClick = { onBangPlay(bang) },
                                cardWidth = cardW
                            )
                        }
                    }
                }
            }

            // 热门歌曲
            if (hotSongs.isNotEmpty()) {
                SectionTitle("热门歌曲")
                hotSongs.chunked(numCols).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { song ->
                            SongCard(
                                song = song,
                                allSongs = hotSongs,
                                onClick = { onSongClick(song, hotSongs) },
                                onPlayClick = { onSongPlay(song) },
                                cardWidth = cardW
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/** 歌曲卡片 — 样式与 PlaylistCard 完全一致，仅数据源为 Song */
@Composable
fun SongCard(
    song: Song,
    allSongs: List<Song>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    cardWidth: Dp
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(cardWidth)
        ) {
            val iconDpWidth: Dp = cardWidth
            val btnSizeDp: Dp = iconDpWidth * 0.30f
            val icSizeDp: Dp = btnSizeDp * 0.80f
            val xOffset = (iconDpWidth.value * 0.80f - btnSizeDp.value / 2).dp
            val yOffset = xOffset

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = song.pic120,
                    contentDescription = song.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xOffset, y = yOffset)
                    .size(btnSizeDp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(btnSizeDp / 2))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(icSizeDp),
                )
            }
        }
        Text(
            text = song.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 排行榜图标卡片 */
@Composable
fun BangCard(
    bang: Bang,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    cardWidth: Dp
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = bang.pic,
                contentDescription = bang.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 播放按钮覆盖层：圆心对齐图标右下角，完全在图标内
            // 按钮=图标×30%，三角形=按钮×80%，offset=80%
            val iconDpWidth: Dp = cardWidth
            val btnSizeDp: Dp = iconDpWidth * 0.30f      // 按钮=图标×30%
            val icSizeDp: Dp = btnSizeDp * 0.80f         // 三角=按钮×80%
            val xOffset = (iconDpWidth.value * 0.80f - btnSizeDp.value / 2).dp
            val yOffset = xOffset
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xOffset, y = yOffset)
                    .size(btnSizeDp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(btnSizeDp / 2))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放全部",
                    tint = Color.White,
                    modifier = Modifier.size(icSizeDp),
                )
            }
        }
        Text(
            text = bang.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 手机横屏首页：播放列表为空时显示，无Banner/无NavBar/无miniplayer */
@Composable
fun HomeScreenLandscape(
    uiState: HomeUiState,
    onGoToPlayer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.error != null) {
            Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 主入口图标（取消"首页"图标，用户已说不需要）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放入口（播放列表为空时提示）
                    LandscapeIconButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "播放",
                        onClick = onGoToPlayer,
                        isExpanded = true
                    )
                    // 搜索入口
                    LandscapeIconButton(
                        icon = Icons.Filled.Search,
                        label = "搜索",
                        onClick = onGoToPlayer,
                        isExpanded = true
                    )
                    // 排行榜入口
                    LandscapeIconButton(
                        icon = Icons.Filled.Leaderboard,
                        label = "排行榜",
                        onClick = onGoToPlayer,
                        isExpanded = true
                    )
                    // 音乐库入口
                    LandscapeIconButton(
                        icon = Icons.Filled.LibraryMusic,
                        label = "音乐库",
                        onClick = onGoToPlayer,
                        isExpanded = true
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isExpanded: Boolean = false
) {
    val scale = if (isExpanded) 1.3f else 1f
    val baseSize = 72.dp
    val baseIconSize = 40.dp
    val scaledSize = (baseSize.value * scale).dp.coerceAtLeast(baseSize)
    val scaledIconSize = (baseIconSize.value * scale).dp.coerceAtLeast(baseIconSize)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(scaledSize)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(scaledIconSize)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LandscapeSmallCard(
    title: String,
    coverUrl: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
