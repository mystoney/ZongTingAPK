package com.zongting.zongting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    rankingsViewModel: RankingsViewModel = hiltViewModel(),
    mainViewModel: MainViewModel,
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    initialBangId: String? = null,
    windowSizeClass: WindowSizeClass? = null,
    onBackClick: (() -> Unit)? = null
) {
    val leftPanelWidth = when (windowSizeClass?.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 260.dp
        else -> 130.dp
    }
    val uiState by rankingsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        rankingsViewModel.loadRankings()
    }

    // 等 categories 加载完再选中指定榜单（避免竞态）
    LaunchedEffect(uiState.bangCategories, initialBangId) {
        if (initialBangId != null && uiState.bangCategories.isNotEmpty()) {
            rankingsViewModel.selectBang(initialBangId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("排行榜") },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // ── 筛选 + 来源切换行（横屏合并为一行，竖屏保持两行）──────────
        val neteaseEnabled = uiState.source == "netease"
        val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

        if (isLandscape) {
            // 横屏：合并为一行
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val fontSize = if (maxWidth < 600.dp) 12.sp else 15.sp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 来源：酷我 / 网易云
                    FilterChip(
                        selected = uiState.source == "kuwo",
                        onClick = { rankingsViewModel.setSource("kuwo") },
                        label = { Text("酷我", style = LocalTextStyle.current.copy(fontSize = fontSize)) },
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = uiState.source == "netease",
                        onClick = { rankingsViewModel.setSource("netease") },
                        label = { Text("网易云", style = LocalTextStyle.current.copy(fontSize = fontSize)) },
                        modifier = Modifier.height(30.dp)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    // 筛选：免费 / VIP / 单曲购买
                    FilterChip(
                        selected = uiState.filters.contains("free"),
                        onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("free", !uiState.filters.contains("free")) },
                        enabled = neteaseEnabled,
                        label = { Text("免费", style = LocalTextStyle.current.copy(fontSize = fontSize)) },
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = uiState.filters.contains("vip"),
                        onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("vip", !uiState.filters.contains("vip")) },
                        enabled = neteaseEnabled,
                        label = { Text("VIP", style = LocalTextStyle.current.copy(fontSize = fontSize)) },
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = uiState.filters.contains("single"),
                        onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("single", !uiState.filters.contains("single")) },
                        enabled = neteaseEnabled,
                        label = { Text("单曲购买", style = LocalTextStyle.current.copy(fontSize = fontSize)) },
                        modifier = Modifier.height(30.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 全部播放
                    FilledTonalButton(
                        onClick = {
                            if (uiState.displayedSongs.isNotEmpty()) {
                                mainViewModel.playSongs(uiState.displayedSongs, 0)
                            }
                        },
                        enabled = uiState.displayedSongs.isNotEmpty(),
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("播放", style = LocalTextStyle.current.copy(fontSize = fontSize))
                    }
                }
            }
        } else {
            // 竖屏：来源切换行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.source == "kuwo",
                    onClick = { rankingsViewModel.setSource("kuwo") },
                    label = { Text("酷我音乐", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = uiState.source == "netease",
                    onClick = { rankingsViewModel.setSource("netease") },
                    label = { Text("网易云音乐", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
            }
            // 筛选行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filters.contains("free"),
                    onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("free", !uiState.filters.contains("free")) },
                    enabled = neteaseEnabled,
                    label = { Text("免费", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("vip"),
                    onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("vip", !uiState.filters.contains("vip")) },
                    enabled = neteaseEnabled,
                    label = { Text("VIP", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("single"),
                    onClick = { if (neteaseEnabled) rankingsViewModel.setFilter("single", !uiState.filters.contains("single")) },
                    enabled = neteaseEnabled,
                    label = { Text("单曲购买", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        if (uiState.displayedSongs.isNotEmpty()) {
                            mainViewModel.playSongs(uiState.displayedSongs, 0)
                        }
                    },
                    enabled = uiState.displayedSongs.isNotEmpty(),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部播放", style = LocalTextStyle.current.copy(fontSize = 15.sp))
                }
            }
        }

        // ── 主内容：左侧榜单列表 + 右侧歌曲列表 ───────────────────────
        if (uiState.isLoading && uiState.displayedSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                // ── 左侧：榜单列表 ─────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .width(leftPanelWidth)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = 6.dp, top = 0.dp, end = 6.dp, bottom = 165.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.bangCategories.forEach { category ->
                        item(key = "header_${category.name}") {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                        items(
                            items = category.list,
                            key = { it.id }
                        ) { bang ->
                            BangListItem(
                                bang = bang,
                                isSelected = uiState.selectedBangId == bang.id,
                                onClick = { rankingsViewModel.loadBangSongs(bang) }
                            )
                        }
                    }
                }

                // 分割线
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // ── 右侧：歌曲列表（网格） ─────────────────────────────────
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    if (uiState.displayedSongs.isEmpty() && uiState.selectedBang != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无歌曲",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
                        val columns = if (isExpanded) 6 else 3
                        val spacing = if (isExpanded) 6.dp else 8.dp
                        val horizontalPadding = 6.dp * 2
                        val totalSpacing = spacing * (columns - 1) + horizontalPadding
                        val availableWidth = maxWidth - totalSpacing
                        val cardWidth = ((availableWidth - spacing * (columns - 1)) / columns).coerceAtLeast(50.dp)

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            item {
                                if (uiState.selectedBang != null) {
                                    Text(
                                        text = uiState.selectedBang!!.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            items(uiState.displayedSongs.chunked(columns).size) { rowIndex ->
                                val rowItems = uiState.displayedSongs.chunked(columns).getOrNull(rowIndex) ?: emptyList()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing)
                                ) {
                                    rowItems.forEach { song ->
                                        RankingSongCard(
                                            song = song,
                                            cardWidth = cardWidth.value.toInt(),
                                            showFeeBadge = uiState.source == "netease",
                                            onClick = {
                                                mainViewModel.playSongPrev(song)
                                                onSongClick(song, uiState.displayedSongs)
                                            }
                                        )
                                    }
                                    repeat((columns - rowItems.size).coerceAtLeast(0)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
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
fun BangListItem(
    bang: Bang,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected)
                    Color.White.copy(alpha = 0.8f)
                else
                    Color(0xFFEEEEEE).copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = bang.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected)
                    Color.Black.copy(alpha = 0.85f)
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = bang.intro,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    Color.Black.copy(alpha = 0.55f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BangCard(
    bang: Bang,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(90.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = bang.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bang.intro,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RankingSongItem(
    rank: Int,
    song: Song,
    showFeeBadge: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFEEEEEE).copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图 + 收费徽章
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                AsyncImage(
                    model = song.pic120,
                    contentDescription = song.name,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                if (showFeeBadge && !song.playable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .background(
                                if (song.fee == 1)
                                    Color(0xFFFF6B35).copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.fee == 1) {
                            Text(
                                text = "VIP",
                                fontSize = 5.sp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "版权限制",
                                modifier = Modifier.size(7.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 1
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // 时长
            Text(
                text = song.displayDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** 排行榜右侧网格歌曲卡片 — 样式与首页 SongCard 一致 */
@Composable
fun RankingSongCard(
    song: Song,
    cardWidth: Int,
    showFeeBadge: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(cardWidth.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(cardWidth.dp)) {
            val btnSizeDp = cardWidth.dp * 0.30f
            val icSizeDp = btnSizeDp * 0.80f
            val xOffset = (cardWidth.toFloat() * 0.75f - btnSizeDp.value / 2).dp
            val yOffset = xOffset

            // 封面
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
                // VIP / 版权徽章
                if (showFeeBadge && !song.playable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(14.dp)
                            .background(
                                if (song.fee == 1)
                                    Color(0xFFFF6B35).copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(3.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.fee == 1) {
                            Text(
                                text = "VIP",
                                fontSize = 6.sp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "版权限制",
                                modifier = Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            // 播放按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xOffset, y = yOffset)
                    .size(btnSizeDp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(btnSizeDp / 2))
                    .clickable(onClick = onClick),
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
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
