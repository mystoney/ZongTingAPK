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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zongting.zongting.BuildConfig
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    favoriteSongs: List<Song>,
    recentlyPlayed: List<Song>,
    userPlaylists: List<UserPlaylist>,
    currentPlaylist: List<Song>,
    currentPlayingSong: Song?,
    onSongClick: (song: Song) -> Unit,
    onSongLongPress: (song: Song) -> Unit,
    onPlayAll: (songs: List<Song>, startIndex: Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (id: String, name: String) -> Unit,
    onDeletePlaylist: (id: String) -> Unit,
    onAddSongToPlaylist: (playlistId: String, song: Song) -> Unit,
    onAddSongsToPlaylist: (playlistId: String, songs: List<Song>) -> Unit,
    onCreateAndAddSongs: (name: String, songs: List<Song>) -> Unit,
    onSongsAdded: (playlistId: String) -> Unit,
    onRemoveSongFromPlaylist: (playlistId: String, songRid: Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var songForDialog by remember { mutableStateOf<Song?>(null) }

    // 待添加到歌单的歌列表（单个或多个）
    var songsPendingAdd by remember { mutableStateOf<List<Song>?>(null) }

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
        AddToPlaylistSheet(
            songs = listOf(song),
            playlists = userPlaylists,
            onSelect = { playlistId ->
                onAddSongToPlaylist(playlistId, song)
                onSongsAdded(playlistId)
                songForDialog = null
            },
            onCreateAndAdd = { name ->
                onCreatePlaylist(name)
                songForDialog = null
            },
            onSongsAdded = onSongsAdded,
            onDismiss = { songForDialog = null }
        )
    }

    // 批量添加歌曲到歌单（当前歌曲/全部歌曲）
    songsPendingAdd?.let { songs ->
        AddToPlaylistSheet(
            songs = songs,
            playlists = userPlaylists,
            onSelect = { playlistId ->
                onAddSongsToPlaylist(playlistId, songs)
                onSongsAdded(playlistId)
                songsPendingAdd = null
            },
            onCreateAndAdd = { name ->
                onCreateAndAddSongs(name, songs)
                songsPendingAdd = null
            },
            onSongsAdded = onSongsAdded,
            onDismiss = { songsPendingAdd = null }
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

        // 左/右侧分栏布局
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧标签列表
            LazyColumn(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                item {
                    LibraryTabItem(
                        icon = Icons.Default.Favorite,
                        title = "我喜欢",
                        isSelected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            expandedPlaylist = null
                        }
                    )
                }
                item {
                    LibraryTabItem(
                        icon = Icons.Default.History,
                        title = "最近播放",
                        isSelected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            expandedPlaylist = null
                        }
                    )
                }
                item {
                    LibraryTabItem(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "我的歌单",
                        isSelected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            expandedPlaylist = null
                        }
                    )
                }
                item {
                    LibraryTabItem(
                        icon = Icons.Default.Download,
                        title = "下载管理",
                        isSelected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            expandedPlaylist = null
                        }
                    )
                }
                item {
                    LibraryTabItem(
                        icon = Icons.Default.Info,
                        title = "关于",
                        isSelected = selectedTab == 4,
                        onClick = {
                            selectedTab = 4
                            expandedPlaylist = null
                        }
                    )
                }
            }

            VerticalDivider()

            // 右侧内容区
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (selectedTab) {
                    0 -> LibraryFavoriteContent(
                        songs = favoriteSongs,
                        currentPlayingSong = currentPlayingSong,
                        currentPlaylist = currentPlaylist,
                        onSongClick = { song -> onSongClick(song) },
                        onSongLongPress = { song ->
                            onSongLongPress(song)
                            songForDialog = song
                        },
                        onToggleFavorite = onToggleFavorite,
                        onPlayAll = { onPlayAll(favoriteSongs, 0) },
                        onAddSong = { song -> songsPendingAdd = listOf(song) },
                        onAddAll = { songsList -> songsPendingAdd = songsList },
                        onSongsAdded = onSongsAdded
                    )
                    1 -> LibraryRecentlyContent(
                        songs = recentlyPlayed,
                        currentPlayingSong = currentPlayingSong,
                        currentPlaylist = currentPlaylist,
                        onSongClick = { song -> onSongClick(song) },
                        onSongLongPress = { song ->
                            onSongLongPress(song)
                            songForDialog = song
                        },
                        onPlayAll = { onPlayAll(recentlyPlayed, 0) },
                        onAddSong = { song -> songsPendingAdd = listOf(song) },
                        onAddAll = { songsList -> songsPendingAdd = songsList },
                        onSongsAdded = onSongsAdded
                    )
                    2 -> LibraryPlaylistContent(
                        playlists = userPlaylists,
                        expandedPlaylist = expandedPlaylist,
                        currentPlayingSong = currentPlayingSong,
                        currentPlaylist = currentPlaylist,
                        onPlaylistClick = { playlist ->
                            if (playlist.songs.isNotEmpty()) {
                                expandedPlaylist = playlist
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
                        onSongClick = { song -> onSongClick(song) },
                        onSongLongPress = { song ->
                            onSongLongPress(song)
                            songForDialog = song
                        },
                        onPlayAll = { playlist -> onPlayAll(playlist.songs, 0) },
                        onAddSong = { song -> songsPendingAdd = listOf(song) },
                        onAddAll = { songsList -> songsPendingAdd = songsList },
                        onBack = { expandedPlaylist = null }
                    )
                    3 -> DownloadManagerContent(
                        onAboutClick = { selectedTab = 4 }
                    )
                    4 -> AboutContent()
                }
            }
        }
    }

    // FAB：仅在"我的歌单"Tab显示
    if (selectedTab == 2 && expandedPlaylist == null) {
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
private fun LibraryTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .background(
                color = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                else
                    Color(0xFFEEEEEE).copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AddToPlaylistDropdown(
    currentPlayingSong: Song?,
    currentPlaylist: List<Song>,
    onAddSong: (Song) -> Unit,
    onAddAll: (List<Song>) -> Unit,
    showAddCurrentSong: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.PlaylistAdd,
                contentDescription = "添加到歌单",
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (showAddCurrentSong && currentPlayingSong != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("添加当前歌曲", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currentPlayingSong.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        expanded = false
                        onAddSong(currentPlayingSong)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text("添加全部到歌单", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${currentPlaylist.size} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    expanded = false
                    onAddAll(currentPlaylist)
                }
            )
        }
    }
}

@Composable
private fun LibraryFavoriteContent(
    songs: List<Song>,
    currentPlayingSong: Song?,
    currentPlaylist: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onAddSong: (Song) -> Unit,
    onAddAll: (List<Song>) -> Unit,
    onSongsAdded: (String) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Favorite,
            title = "暂无收藏",
            subtitle = "点击心形图标收藏喜欢的歌曲"
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 直接显示歌单选择页面（添加到歌单）
                    IconButton(onClick = { onAddAll(songs) }) {
                        Icon(
                            Icons.Default.PlaylistAdd,
                            contentDescription = "添加到歌单",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    FilledTonalButton(
                        onClick = onPlayAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部播放", style = LocalTextStyle.current.copy(fontSize = 13.sp))
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 165.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(songs, key = { it.rid }) { song ->
                    LibrarySongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongPress(song) },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "已收藏",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onFavoriteClick = { onToggleFavorite(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRecentlyContent(
    songs: List<Song>,
    currentPlayingSong: Song?,
    currentPlaylist: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onAddSong: (Song) -> Unit,
    onAddAll: (List<Song>) -> Unit,
    onSongsAdded: (String) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "暂无播放历史",
            subtitle = "播放的歌曲会显示在这里"
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 直接显示歌单选择页面（添加到歌单）
                    IconButton(onClick = { onAddAll(songs) }) {
                        Icon(
                            Icons.Default.PlaylistAdd,
                            contentDescription = "添加到歌单",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    FilledTonalButton(
                        onClick = onPlayAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部播放", style = LocalTextStyle.current.copy(fontSize = 13.sp))
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 165.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(songs, key = { it.rid }) { song ->
                    LibrarySongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongPress(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistContent(
    playlists: List<UserPlaylist>,
    expandedPlaylist: UserPlaylist?,
    currentPlayingSong: Song?,
    currentPlaylist: List<Song>,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlaylistLongPress: (UserPlaylist) -> Unit,
    onPlaylistDelete: (UserPlaylist) -> Unit,
    onRemoveSong: (playlistId: String, songRid: Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onPlayAll: (UserPlaylist) -> Unit,
    onAddSong: (Song) -> Unit,
    onAddAll: (List<Song>) -> Unit,
    onBack: () -> Unit
) {
    if (expandedPlaylist != null) {
        // 歌单详情视图（内联，与 RankingsScreen 风格一致）
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = expandedPlaylist.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 歌曲数量 + 全部播放按钮（与 RankingsScreen 一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${expandedPlaylist.songs.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { onPlayAll(expandedPlaylist) },
                        enabled = expandedPlaylist.songs.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部播放", style = LocalTextStyle.current.copy(fontSize = 13.sp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (expandedPlaylist.songs.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "歌单为空",
                    subtitle = "从其他页面添加歌曲吧"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 165.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(expandedPlaylist.songs, key = { it.rid }) { song ->
                        LibraryPlaylistSongRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongPress(song) },
                            onRemove = { onRemoveSong(expandedPlaylist.id, song.rid) }
                        )
                    }
                }
            }
        }
    } else if (playlists.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            title = "暂无歌单",
            subtitle = "点击右下角 + 创建你的第一个歌单"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 165.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "add_all_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AddToPlaylistDropdown(
                        currentPlayingSong = null,
                        currentPlaylist = currentPlaylist,
                        onAddSong = onAddSong,
                        onAddAll = onAddAll
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                LibraryPlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onLongClick = { onPlaylistLongPress(playlist) },
                    onDelete = { onPlaylistDelete(playlist) }
                )
            }
        }
    }
}

@Composable
private fun LibraryPlaylistItem(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEEEEEE).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
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
    }
}

@Composable
private fun LibrarySongRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEEEEEE).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.pic120,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailing != null) {
                trailing()
            } else if (onFavoriteClick != null) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistSongRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEEEEEE).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.pic120,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = "移除",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistSheet(
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    onSelect: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onSongsAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val titleText = if (songs.size == 1) "《${songs[0].name}》" else "${songs.size} 首歌曲"
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Color(0xF20B1E10),
            title = { Text("新建歌单", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌单名称") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (newName.isNotBlank()) { onCreateAndAdd(newName) } },
                    enabled = newName.isNotBlank()
                ) { Text("创建", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20B1E10),
        title = { Text("添加到歌单", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
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
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("新建歌单", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }

                if (playlists.isEmpty()) {
                    Text(
                        "还没有歌单，创建一个吧",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
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
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${playlist.songs.size} 首",
                                    style = MaterialTheme.typography.bodyLarge,
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
            TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.bodyLarge) }
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
        containerColor = Color(0xF20B1E10),
        title = { Text("创建歌单", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("创建", style = MaterialTheme.typography.bodyLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = MaterialTheme.typography.bodyLarge)
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
private fun DownloadManagerContent(
    onAboutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "0 首下载歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        // 关于入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAboutClick)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("关于", style = MaterialTheme.typography.titleMedium)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AboutContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "纵听",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
