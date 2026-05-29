package com.zongting.zongting.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    playlistId: Long,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    currentPlaylist: List<Song> = emptyList(),  // 当前播放列表，用于判断是否为空
    onPlayAll: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        Log.d("HomeDebug", "PlaylistScreen: LaunchedEffect fired for playlistId=$playlistId")
        viewModel.loadPlaylist(playlistId)
        hasAutoPlayed = false
    }

    // ★ 新逻辑：歌单加载完成后，若当前播放列表为空，自动播放第一首
    LaunchedEffect(uiState.songs, currentPlaylist) {
        if (!hasAutoPlayed && uiState.songs.isNotEmpty() && currentPlaylist.isEmpty()) {
            Log.d("HomeDebug", "PlaylistScreen: current playlist empty, auto-playing first song")
            onSongClick(uiState.songs.first(), uiState.songs)
            hasAutoPlayed = true
        }
    }

    Log.d("HomeDebug", "PlaylistScreen: COMPOSING isLoading=${uiState.isLoading} playlist=${uiState.playlist?.name} songs=${uiState.songs.size}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部导航
        TopAppBar(
            title = { Text(uiState.playlist?.name ?: "歌单") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { /* 分享 */ }) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.playlist?.let { playlist ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 165.dp)
                ) {
                    // 歌单头部
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            AsyncImage(
                                model = playlist.img300,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playlist.userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${playlist.total}首歌曲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 播放全部按钮
                    item {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("播放全部")
                        }
                    }

                    // 歌曲列表
                    itemsIndexed(uiState.songs) { index, song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(song, uiState.songs) }
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
