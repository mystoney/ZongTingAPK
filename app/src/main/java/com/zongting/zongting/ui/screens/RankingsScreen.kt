package com.zongting.zongting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    viewModel: RankingsViewModel = hiltViewModel(),
    onSongClick: (Song, List<Song>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRankings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("排行榜") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // 来源切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.source == "kuwo",
                onClick = { viewModel.setSource("kuwo") },
                label = { Text("酷我", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(20.dp)
            )
            FilterChip(
                selected = uiState.source == "netease",
                onClick = { viewModel.setSource("netease") },
                label = { Text("网易云", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(20.dp)
            )
        }

        // 版权筛选
        if (uiState.source == "netease" && uiState.bangSongs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.filters.contains("free"),
                    onClick = { viewModel.setFilter("free", !uiState.filters.contains("free")) },
                    label = { Text("免费", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(20.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("vip"),
                    onClick = { viewModel.setFilter("vip", !uiState.filters.contains("vip")) },
                    label = { Text("VIP", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(20.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("lock"),
                    onClick = { viewModel.setFilter("lock", !uiState.filters.contains("lock")) },
                    label = { Text("付费", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(20.dp)
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 165.dp)
            ) {
                // 排行榜分类
                uiState.bangCategories.forEach { category ->
                    item {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(category.list) { bang ->
                                BangCard(
                                    bang = bang,
                                    onClick = { viewModel.loadBangSongs(bang.id) }
                                )
                            }
                        }
                    }
                }

                // 当前选中排行榜的歌曲
                if (uiState.selectedBang != null && uiState.displayedSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.selectedBang!!.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(uiState.displayedSongs) { index, song ->
                        RankingSongItem(
                            rank = index + 1,
                            song = song,
                            onClick = { onSongClick(song, uiState.displayedSongs) }
                        )
                    }
                }
            }
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
            .width(100.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(6.dp)
        ) {
            Text(
                text = bang.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = bang.intro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RankingSongItem(
    rank: Int,
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名号
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!song.playable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
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
                style = MaterialTheme.typography.bodySmall,
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
            text = song.songTimeMinutes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
