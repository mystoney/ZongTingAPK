package com.zongting.zongting.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    viewModel: SearchViewModel = hiltViewModel(),
    userPlaylists: List<UserPlaylist>,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onAddToPlaylist: (playlistId: String, song: Song) -> Unit,
    onCreateAndAdd: (name: String, song: Song) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { text ->
                    searchText = text
                    if (text.length >= 2) {
                        viewModel.searchSuggest(text)
                    }
                },
                placeholder = { Text("搜索歌曲、歌手") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchText.isNotBlank()) {
                            viewModel.search(searchText)
                        }
                    }
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (searchText.isNotBlank()) {
                        viewModel.search(searchText)
                    }
                },
                modifier = Modifier.height(56.dp)
            ) {
                Text("搜索")
            }
        }

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
                label = { Text("酷我", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                modifier = Modifier.height(32.dp)
            )
            FilterChip(
                selected = uiState.source == "netease",
                onClick = { viewModel.setSource("netease") },
                label = { Text("网易云", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                modifier = Modifier.height(32.dp)
            )
        }

        // 版权筛选（仅网易云搜索结果显示）
        if (uiState.source == "netease" && uiState.searchResults.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.filters.contains("free"),
                    onClick = { viewModel.setFilter("free", !uiState.filters.contains("free")) },
                    label = { Text("免费", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("vip"),
                    onClick = { viewModel.setFilter("vip", !uiState.filters.contains("vip")) },
                    label = { Text("VIP", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = uiState.filters.contains("lock"),
                    onClick = { viewModel.setFilter("lock", !uiState.filters.contains("lock")) },
                    label = { Text("付费", style = LocalTextStyle.current.copy(fontSize = 15.sp)) },
                    modifier = Modifier.height(32.dp)
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
        } else if (uiState.displayedResults.isNotEmpty()) {
            // 搜索结果头部：显示数量 + 全部播放按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "找到 ${uiState.displayedResults.size} 首歌曲${if (uiState.filters.isNotEmpty()) "（已筛选）" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        val allSongs = uiState.displayedResults.filter { it.playable }
                        if (allSongs.isNotEmpty()) {
                            onPlayAll(allSongs)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("全部播放", style = LocalTextStyle.current.copy(fontSize = 15.sp))
                }
            }

            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, top = 4.dp, end = 10.dp, bottom = 165.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.displayedResults) { song ->
                    SongListItemWithLongPress(
                        song = song,
                        playlists = userPlaylists,
                        onClick = { onSongClick(song, uiState.displayedResults) },
                        onLongPress = { },
                        onAddToPlaylist = { playlistId, s -> onAddToPlaylist(playlistId, s) },
                        onCreateAndAdd = { name, s -> onCreateAndAdd(name, s) }
                    )
                }
            }
        } else if (uiState.searchResults.isNotEmpty() && uiState.displayedResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有符合条件的歌曲",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (searchText.isEmpty()) {
            // 搜索建议
            if (uiState.suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "搜索建议",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.suggestions.take(5).forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    searchText = suggestion
                                    viewModel.search(suggestion)
                                },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "输入关键词搜索歌曲",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未找到相关歌曲",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(error)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItemWithLongPress(
    song: Song,
    playlists: List<UserPlaylist>,
    onClick: () -> Unit,
    onLongPress: (Song) -> Unit,
    onAddToPlaylist: (String, Song) -> Unit,
    onCreateAndAdd: (String, Song) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFEEEEEE).copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDialog = true }
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            Box {
                AsyncImage(
                    model = song.coverUrl ?: song.pic120,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                if (!song.playable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(15.dp)
                            .background(
                                if (song.fee == 1) Color(0xFFFF6B35).copy(alpha = 0.9f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (song.fee == 1) Icons.Default.Star else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDialog) {
        AddToPlaylistDialogInline(
            song = song,
            playlists = playlists,
            onSelect = { playlistId -> onAddToPlaylist(playlistId, song); showDialog = false },
            onCreateAndAdd = { name -> onCreateAndAdd(name, song); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun AddToPlaylistDialogInline(
    song: Song,
    playlists: List<UserPlaylist>,
    onSelect: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("《${song.name}》添加到歌单") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("还没有歌单，创建一个吧", style = MaterialTheme.typography.bodyMedium)
                } else {
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.songs.size} 首") },
                            modifier = Modifier.clickable { onSelect(playlist.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("新建歌单")
                }
                if (showCreate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (showCreate && newPlaylistName.isNotBlank()) {
                TextButton(onClick = { onCreateAndAdd(newPlaylistName.trim()) }) {
                    Text("创建并添加")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
