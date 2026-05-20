package com.zongting.zongting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zongting.zongting.data.model.Song
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("全部播放", style = MaterialTheme.typography.labelMedium)
                }
            }

            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 165.dp)
            ) {
                items(uiState.displayedResults) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song, uiState.displayedResults) }
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
