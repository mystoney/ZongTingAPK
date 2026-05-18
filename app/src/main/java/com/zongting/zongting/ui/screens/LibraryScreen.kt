package com.zongting.zongting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zongting.zongting.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    favoriteSongs: List<Song>,
    recentlyPlayed: List<Song>,
    onSongClick: (song: Song, playlist: List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("我喜欢", "最近播放", "下载管理")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("我的音乐") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> FavoriteTab(
                songs = favoriteSongs,
                onSongClick = onSongClick,
                onToggleFavorite = onToggleFavorite
            )
            1 -> RecentlyPlayedTab(
                songs = recentlyPlayed,
                onSongClick = onSongClick
            )
            2 -> EmptyState(
                icon = Icons.Default.Download,
                title = "暂无下载",
                subtitle = "点击下载按钮缓存歌曲"
            )
        }
    }
}

@Composable
private fun FavoriteTab(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Favorite,
            title = "暂无收藏",
            subtitle = "点击心形图标收藏喜欢的歌曲"
        )
    } else {
        SongList(
            songs = songs,
            onSongClick = onSongClick,
            showFavorite = true,
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
private fun RecentlyPlayedTab(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "暂无播放历史",
            subtitle = "播放的歌曲会显示在这里"
        )
    } else {
        SongList(
            songs = songs,
            onSongClick = onSongClick,
            showFavorite = false,
            onToggleFavorite = {}
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    showFavorite: Boolean,
    onToggleFavorite: (Song) -> Unit,
    favoriteSet: Set<Long> = emptySet()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 165.dp)
    ) {
        items(songs, key = { it.rid }) { song ->
            SongItem(
                song = song,
                isFavorite = favoriteSet.contains(song.rid),
                onClick = { onSongClick(song, songs) },
                onFavoriteClick = if (showFavorite) {{ onToggleFavorite(song) }} else null
            )
        }
    }
}

@Composable
private fun SongItem(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyLarge,
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
        if (onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
