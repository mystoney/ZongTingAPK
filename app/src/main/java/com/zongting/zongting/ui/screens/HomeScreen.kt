package com.zongting.zongting.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
    windowSizeClass: WindowSizeClass? = null
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
            isExpanded = true,
            availableWidthDp = PAD_MAX_CONTENT_WIDTH_DP.dp
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
                    Text(
                        "纵听",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE53935)
                    )

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
                // 轮播图（所有模式都显示）
                if (uiState.banners.isNotEmpty()) {
                    item {
                        BannerCarousel(banners = uiState.banners)
                    }
                }

                // Expanded 平板横屏：内容区由 AdaptiveLayout 接管，此处不再渲染推荐内容
                if (!isExpanded) {
                    item {
                        RecommendPager(
                            playlists = uiState.playlists,
                            hotSongs = uiState.hotSongs,
                            hotBangs = uiState.hotBangs,
                            onPlaylistClick = onPlaylistClick,
                            onPlaylistPlay = onPlaylistPlay,
                            onSongClick = onSongClick,
                            onSongPlay = onSongPlay,
                            onBangClick = onBangClick,
                            isExpanded = isExpanded,
                            availableWidthDp = availableWidthDp
                        )
                    }
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
                .height(140.dp)
                .padding(horizontal = 16.dp)
        ) { page ->
            val banner = banners[page]
            // 优先用 newPic（高清图），pic 可能是空或旧图
            val imageUrl = banner.newPic.ifEmpty { banner.pic }
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
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
    cardWidth: Int,
    iconWidth: Int
) {
    Column(
        modifier = Modifier
            .width(cardWidth.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(iconWidth.dp)
        ) {
            val iconDpWidth: Dp = iconWidth.dp
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
fun HotBangsRow(bangs: List<Bang>, onBangClick: (String) -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val cardWidth = (screenWidth - 32 - 24) / 3
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bangs) { bang ->
            BangCard(bang = bang, onClick = { onBangClick(bang.id) }, cardWidth = cardWidth)
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
    val cardWidth = (screenWidth - 32 - 16) / 3
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
    onClick: () -> Unit
) {
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                // VIP/版权限制锁图标
                if (!song.playable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(15.dp)
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
                                modifier = Modifier.size(12.dp),
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

/** 两页横向翻页：热门歌曲 / 推荐（仅 Expanded 模式）；三页横向翻页：热门歌曲 / 每日推荐 / 推荐歌单（普通模式） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendPager(
    playlists: List<Playlist>,
    hotSongs: List<Song>,
    hotBangs: List<Bang>,
    onPlaylistClick: (Long) -> Unit,
    onPlaylistPlay: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongPlay: (Song) -> Unit,
    onBangClick: (String) -> Unit,
    isExpanded: Boolean = false,
    availableWidthDp: Dp? = null
) {
    // 顺序：热门歌曲(0) / 推荐(1) / 排行榜(2)
    // Expanded 模式：合并为 3 页（热门歌曲 / 推荐 / 排行榜）
    val pagerState = rememberPagerState(
        initialPage = if (isExpanded) 1 else 1,
        pageCount = { if (isExpanded) 3 else 3 }
    )
    val scope = rememberCoroutineScope()

    Column {
        // Tab 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Expanded：热门歌曲(0) / 推荐(1) / 排行榜(2)；普通：热门歌曲(0) / 推荐(1) / 排行榜(2)
            val titles = if (isExpanded) listOf("热门歌曲", "推荐", "排行榜") else listOf("热门歌曲", "推荐", "排行榜")
            titles.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Tab 下划线指示器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(3) { index ->
                    val widthFraction = if (pagerState.currentPage == index) 1f / 3 else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Transparent
                            )
                    )
                }
            }
        }

        // 横向翻页内容（高度由内容撑起，可滚动）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            beyondBoundsPageCount = 0
        ) { page ->
            if (isExpanded) {
                // Expanded：3页（热门歌曲 / 推荐 / 排行榜）
                when (page) {
                    0 -> RecommendPage(
                        playlists = emptyList(),
                        hotSongs = hotSongs,
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.HOT_SONG,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                    1 -> RecommendPage(
                        playlists = playlists,
                        hotSongs = emptyList(),
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.PLAYLIST,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                    2 -> RecommendPage(
                        playlists = emptyList(),
                        hotSongs = emptyList(),
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.RANKINGS,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                }
            } else {
                // 普通模式：3页（热门歌曲 / 推荐 / 排行榜）
                when (page) {
                    0 -> RecommendPage(
                        playlists = emptyList(),
                        hotSongs = hotSongs,
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.HOT_SONG,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                    1 -> RecommendPage(
                        playlists = playlists,
                        hotSongs = emptyList(),
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.DAILY_PLAYLIST,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                    2 -> RecommendPage(
                        playlists = emptyList(),
                        hotSongs = emptyList(),
                        hotBangs = hotBangs,
                        onPlaylistClick = onPlaylistClick,
                        onPlaylistPlay = onPlaylistPlay,
                        onSongClick = onSongClick,
                        onSongPlay = onSongPlay,
                        onBangClick = onBangClick,
                        pageType = PageType.RANKINGS,
                        isExpanded = isExpanded,
                        availableWidthDp = availableWidthDp
                    )
                }
            }
        }
    }
}

enum class PageType { DAILY, PLAYLIST, HOT_SONG, RANKINGS, DAILY_PLAYLIST }

/** 单页推荐内容（复用现有 PlaylistCard 样式） */
@Composable
private fun RecommendPage(
    playlists: List<Playlist>,
    hotSongs: List<Song>,
    hotBangs: List<Bang>,
    onPlaylistClick: (Long) -> Unit,
    onPlaylistPlay: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onSongPlay: (Song) -> Unit,
    onBangClick: (String) -> Unit,
    pageType: PageType,
    isExpanded: Boolean = false,
    availableWidthDp: Dp? = null
) {
    // 平板横屏模式下使用限制的最大内容宽度计算卡片宽度
    val effectiveWidth = availableWidthDp?.value ?: LocalConfiguration.current.screenWidthDp.toFloat()
    // 普通模式：3列；Expanded：固定卡片宽度（约160dp），横向滑动
    val columns = if (isExpanded) -1 else 3
    val horizontalPadding = 32 // 16dp * 2
    // Expanded 固定卡片宽度（高度约160dp，按1:1比例）
    val cardSize = 160
        val cardWidth = if (isExpanded) cardSize else {
            val columnSpacing = 8 * 2
            ((effectiveWidth - horizontalPadding - columnSpacing) / 3).toInt()
        }

        // 平板横屏 Expanded 模式：6列 x 2行 固定网格，miniplayer 自然遮挡底部
        if (isExpanded) {
            val gridColumns = 6
            val gridRows = 2

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (pageType) {
                    PageType.HOT_SONG -> {
                        val gridItems = playlists.take(gridColumns * gridRows)
                        Text(
                            text = "推荐歌单",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        gridItems.chunked(gridColumns).forEachIndexed { rowIndex, rowItems ->
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
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            if (rowIndex < gridRows - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        if (hotSongs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "热门歌曲",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            hotSongs.take(gridColumns * gridRows).chunked(gridColumns).forEachIndexed { rowIndex, rowItems ->
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
                                            cardWidth = cardWidth
                                        )
                                    }
                                    repeat(gridColumns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                                if (rowIndex < 1) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    PageType.PLAYLIST, PageType.DAILY, PageType.DAILY_PLAYLIST -> {
                        val gridItems = playlists.take(gridColumns * gridRows)
                        Text(
                            text = if (pageType == PageType.DAILY) "每日推荐" else "推荐歌单",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        gridItems.chunked(gridColumns).forEachIndexed { rowIndex, rowItems ->
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
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            if (rowIndex < gridRows - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    PageType.RANKINGS -> {
                        val gridItems = hotBangs.take(gridColumns * gridRows)
                        Text(
                            text = "排行榜",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        gridItems.chunked(gridColumns).forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { bang ->
                                    BangCard(
                                        bang = bang,
                                        onClick = { onBangClick(bang.id) },
                                        cardWidth = cardWidth
                                    )
                                }
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            if (rowIndex < gridRows - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            val cardHeight = cardWidth + 48
        } else {
        // 普通模式：固定3列网格
        val columnSpacing = 8 * 2
        val actualCardWidth = ((effectiveWidth - horizontalPadding - columnSpacing) / 3).toInt()
        val actualCardHeight = actualCardWidth + 48

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (pageType) {
                PageType.HOT_SONG -> {
                    val rows = hotSongs.chunked(3)
                    rows.forEachIndexed { rowIndex, rowItems ->
                        item {
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
                                        cardWidth = actualCardWidth
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                PageType.RANKINGS -> {
                    val rows = hotBangs.chunked(3)
                    rows.forEach { rowItems ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { bang ->
                                    BangCard(
                                        bang = bang,
                                        onClick = { onBangClick(bang.id) },
                                        cardWidth = actualCardWidth
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                PageType.PLAYLIST, PageType.DAILY, PageType.DAILY_PLAYLIST -> {
                    val rows = playlists.chunked(3)
                    rows.forEach { rowItems ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { playlist ->
                                    PlaylistCard(
                                        playlist,
                                        { onPlaylistClick(playlist.id) },
                                        { onPlaylistPlay(playlist.id) },
                                        actualCardWidth,
                                        actualCardWidth
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 歌曲卡片 — 样式与 PlaylistCard 完全一致，仅数据源为 Song */
@Composable
fun SongCard(
    song: Song,
    allSongs: List<Song>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    cardWidth: Int
) {
    Column(
        modifier = Modifier
            .width(cardWidth.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(cardWidth.dp)
        ) {
            val iconDpWidth: Dp = cardWidth.dp
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
    cardWidth: Int
) {
    Column(
        modifier = Modifier
            .width(cardWidth.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = bang.pic,
                contentDescription = bang.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
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
