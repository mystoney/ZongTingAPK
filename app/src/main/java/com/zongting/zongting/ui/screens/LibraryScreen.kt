package com.zongting.zongting.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    favoriteSongs: List<Song>,
    recentlyPlayed: List<Song>,
    userPlaylists: List<UserPlaylist>,
    currentPlaylist: List<Song>,
    onSongClick: (song: Song) -> Unit,
    onSongLongPress: (song: Song) -> Unit,
    onPlayAll: (songs: List<Song>, startIndex: Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (id: String, name: String) -> Unit,
    onDeletePlaylist: (id: String) -> Unit,
    onAddSongToPlaylist: (playlistId: String, song: Song) -> Unit,
    onRemoveSongFromPlaylist: (playlistId: String, songRid: Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("我喜欢", "最近播放", "我的歌单", "下载管理")

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var songForDialog by remember { mutableStateOf<Song?>(null) }

    // 创建歌单对话框
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    // 重命名对话框
    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                onRenamePlaylist(playlist.id, newName)
                playlistToRename = null
            }
        )
    }

    // 删除确认对话框
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("删除歌单") },
            text = { Text("确定要删除歌单「${playlist.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(playlist.id)
                    playlistToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 歌曲操作对话框（添加到歌单）
    songForDialog?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = userPlaylists,
            onSelect = { playlistId ->
                onAddSongToPlaylist(playlistId, song)
                songForDialog = null
            },
            onCreateAndAdd = { name ->
                onCreatePlaylist(name)
                songForDialog = null
            },
            onDismiss = { songForDialog = null }
        )
    }

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
                currentPlaylist = currentPlaylist,
                onSongClick = onSongClick,
                onSongLongPress = { song ->
                    onSongLongPress(song)
                    songForDialog = song
                },
                onToggleFavorite = onToggleFavorite,
                onPlayAll = { onPlayAll(favoriteSongs, 0) }
            )
            1 -> RecentlyPlayedTab(
                songs = recentlyPlayed,
                currentPlaylist = currentPlaylist,
                onSongClick = onSongClick,
                onSongLongPress = { song ->
                    onSongLongPress(song)
                    songForDialog = song
                },
                onPlayAll = { onPlayAll(recentlyPlayed, 0) }
            )
            2 -> MyPlaylistsTab(
                playlists = userPlaylists,
                onPlaylistClick = { playlist ->
                    if (playlist.songs.isNotEmpty()) {
                        onPlayAll(playlist.songs, 0)
                    }
                },
                onPlaylistLongPress = { playlist ->
                    playlistToRename = playlist
                },
                onPlaylistDelete = { playlist ->
                    playlistToDelete = playlist
                },
                onRemoveSong = { playlistId, songRid ->
                    onRemoveSongFromPlaylist(playlistId, songRid)
                },
                onSongClick = onSongClick,
                onSongLongPress = { song ->
                    onSongLongPress(song)
                    songForDialog = song
                },
                onPlayAll = { playlist ->
                    if (playlist.songs.isNotEmpty()) {
                        onPlayAll(playlist.songs, 0)
                    }
                }
            )
            3 -> EmptyState(
                icon = Icons.Default.Download,
                title = "暂无下载",
                subtitle = "点击下载按钮缓存歌曲"
            )
        }
    }

    // FAB：仅在"我的歌单"Tab显示
    if (selectedTab == 2) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.padding(end = 16.dp, bottom = 180.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建歌单")
            }
        }
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("播放全部")
    }
}

@Composable
private fun MyPlaylistsTab(
    playlists: List<UserPlaylist>,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlaylistLongPress: (UserPlaylist) -> Unit,
    onPlaylistDelete: (UserPlaylist) -> Unit,
    onRemoveSong: (playlistId: String, songRid: Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onPlayAll: (UserPlaylist) -> Unit
) {
    var expandedPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }

    expandedPlaylist?.let { playlist ->
        PlaylistDetailSheet(
            playlist = playlist,
            onDismiss = { expandedPlaylist = null },
            onRemoveSong = { songRid -> onRemoveSong(playlist.id, songRid) },
            onSongClick = onSongClick,
            onSongLongPress = onSongLongPress,
            onPlayAll = { onPlayAll(playlist) }
        )
        return
    }

    if (playlists.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            title = "暂无歌单",
            subtitle = "点击右下角 + 创建你的第一个歌单"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 165.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onLongClick = { onPlaylistLongPress(playlist) },
                    onDelete = { onPlaylistDelete(playlist) },
                    onExpandClick = { expandedPlaylist = playlist }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistListItem(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onExpandClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onExpandClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.songs.size} 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("播放全部") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        showMenu = false
                        onLongClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailSheet(
    playlist: UserPlaylist,
    onDismiss: () -> Unit,
    onRemoveSong: (Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onPlayAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }

            Text(
                text = "${playlist.songs.size} 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 播放全部按钮
            if (playlist.songs.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (playlist.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("歌单为空，从其他页面添加歌曲吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                    items(playlist.songs, key = { it.rid }) { song ->
                        SongItemWithRemove(
                            song = song,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongPress(song) },
                            onRemove = { onRemoveSong(song.rid) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongItemWithRemove(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.RemoveCircleOutline,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun FavoriteTab(
    songs: List<Song>,
    currentPlaylist: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayAll: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Favorite,
            title = "暂无收藏",
            subtitle = "点击心形图标收藏喜欢的歌曲"
        )
    } else {
        Column {
            PlayAllButton(onClick = onPlayAll)
            SongList(
                songs = songs,
                currentPlaylist = currentPlaylist,
                onSongClick = onSongClick,
                onSongLongPress = onSongLongPress,
                showFavorite = true,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun RecentlyPlayedTab(
    songs: List<Song>,
    currentPlaylist: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onPlayAll: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "暂无播放历史",
            subtitle = "播放的歌曲会显示在这里"
        )
    } else {
        Column {
            PlayAllButton(onClick = onPlayAll)
            SongList(
                songs = songs,
                currentPlaylist = currentPlaylist,
                onSongClick = onSongClick,
                onSongLongPress = onSongLongPress,
                showFavorite = false,
                onToggleFavorite = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongList(
    songs: List<Song>,
    currentPlaylist: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    showFavorite: Boolean,
    onToggleFavorite: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 165.dp)
    ) {
        items(songs, key = { it.rid }) { song ->
            SongItem(
                song = song,
                isFavorite = showFavorite,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongPress(song) },
                onFavoriteClick = if (showFavorite) {{ onToggleFavorite(song) }} else null
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongItem(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pic120,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
private fun AddToPlaylistDialog(
    song: Song,
    playlists: List<UserPlaylist>,
    onSelect: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (newName.isNotBlank()) onCreateAndAdd(newName) },
                    enabled = newName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            Column {
                Text(
                    text = "《${song.name}》",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreate = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("新建歌单", color = MaterialTheme.colorScheme.primary)
                }

                if (playlists.isEmpty()) {
                    Text(
                        "还没有歌单，创建一个吧",
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalDivider()
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlist.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name)
                                Text(
                                    "${playlist.songs.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
