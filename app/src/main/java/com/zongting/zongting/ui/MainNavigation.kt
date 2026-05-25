package com.zongting.zongting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zongting.zongting.ui.screens.*
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.data.repository.UpdateEvent
import com.zongting.zongting.data.repository.UpdatePhase

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Search : Screen("search", "搜索", Icons.Default.Search)
    object Library : Screen("library", "音乐库", Icons.Default.LibraryMusic)
    object Rankings : Screen("rankings", "排行榜", Icons.Default.Leaderboard) {
        fun createRoute(bangId: String?) = if (bangId != null) "rankings/$bangId" else "rankings"
    }
    object Playlist : Screen("playlist/{playlistId}", "歌单", Icons.Default.QueueMusic) {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
    object Player : Screen("player", "播放", Icons.Default.PlayCircle)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Rankings,
    Screen.Library
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    mainViewModel: MainViewModel = hiltViewModel(),
    updateViewModel: com.zongting.zongting.ui.screens.UpdateViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentSong = mainViewModel.currentSong.collectAsState()
    val isPlaying = mainViewModel.isPlaying.collectAsState()
    val favoriteSongs = mainViewModel.favoriteSongs.collectAsState()
    val favoriteSongList = mainViewModel.favoriteSongList.collectAsState()
    val recentlyPlayed = mainViewModel.recentlyPlayed.collectAsState()
    val userPlaylists = mainViewModel.userPlaylists.collectAsState(initial = emptyList())
    var expandedPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }

    // 监听更新事件
    val updateEvent by updateViewModel.updateEvent.collectAsState()
    val updatePhase by updateViewModel.updatePhase.collectAsState()
    val pendingVersionInfo = remember { mutableStateOf<com.zongting.zongting.data.model.VersionInfo?>(null) }

    LaunchedEffect(updateEvent) {
        val event = updateEvent
        when (event) {
            is UpdateEvent.UpdateAvailable -> {
                pendingVersionInfo.value = event.versionInfo
            }
            else -> {
                pendingVersionInfo.value = null
            }
        }
    }

    LaunchedEffect(Unit) {
        com.zongting.zongting.player.PlayerManager.onPlayingChanged = { playing ->
            mainViewModel.updatePlayingState(playing)
        }
        com.zongting.zongting.player.PlayerManager.onSongChanged = { song, index ->
            mainViewModel.updateCurrentSong(song, index)
        }
    }

    val showBottomBar = currentDestination?.route?.let { route ->
        bottomNavItems.any { item ->
            val base = item.route.split("/{")[0]
            route == item.route || route.startsWith(base + "/")
        }
    } ?: true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentDestination?.route == screen.route ||
                            (screen.route != "rankings" && currentDestination?.route?.startsWith(screen.route) == true)
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = hiltViewModel(),
                        updateViewModel = updateViewModel,
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        },
                        onSongClick = { song, playlist ->
                            mainViewModel.playSongPrev(song)
                        },
                        onPlaylistPlay = { playlistId ->
                            mainViewModel.playPlaylist(playlistId)
                        },
                        onSongPlay = { song ->
                            mainViewModel.playSongPrev(song)
                        },
                        onBangClick = { bangId ->
                            navController.navigate(Screen.Rankings.createRoute(bangId))
                        }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        userPlaylists = userPlaylists.value,
                        onSongClick = { song, playlist ->
                            mainViewModel.playSongPrev(song)
                        },
                        onPlayAll = { songs ->
                            mainViewModel.playSongs(songs, 0)
                        },
                        onAddToPlaylist = { playlistId, song ->
                            mainViewModel.addSongToPlaylist(playlistId, song) {}
                        },
                        onCreateAndAdd = { name, song ->
                            mainViewModel.createPlaylistAndAddSong(name, song)
                        }
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        favoriteSongs = favoriteSongList.value,
                        recentlyPlayed = recentlyPlayed.value,
                        userPlaylists = userPlaylists.value,
                        currentPlaylist = mainViewModel.currentPlaylist.value,
                        currentPlayingSong = currentSong.value,
                        onSongClick = { song ->
                            mainViewModel.playSongPrev(song)
                        },
                        onSongLongPress = { song ->
                            mainViewModel.playSongPrev(song)
                        },
                        onPlayAll = { songs, startIndex ->
                            if (songs.isNotEmpty()) {
                                mainViewModel.playSongs(songs, startIndex)
                            }
                        },
                        onToggleFavorite = { song ->
                            mainViewModel.toggleFavorite(song)
                        },
                        onCreatePlaylist = { name ->
                            mainViewModel.createPlaylist(name)
                        },
                        onRenamePlaylist = { id, name ->
                            mainViewModel.renamePlaylist(id, name)
                        },
                        onDeletePlaylist = { id ->
                            mainViewModel.deletePlaylist(id)
                        },
                        onAddSongToPlaylist = { playlistId, song ->
                            mainViewModel.addSongToPlaylist(playlistId, song) {
                                expandedPlaylist = userPlaylists.value.find { it.id == playlistId }
                            }
                        },
                        onAddSongsToPlaylist = { playlistId, songs ->
                            mainViewModel.addSongsToPlaylist(playlistId, songs) {
                                expandedPlaylist = userPlaylists.value.find { it.id == playlistId }
                            }
                        },
                        onCreateAndAddSongs = { name, songs ->
                            mainViewModel.createPlaylistAndAddSongs(name, songs) { newId ->
                                expandedPlaylist = userPlaylists.value.find { it.id == newId }
                            }
                        },
                        onSongsAdded = { playlistId ->
                            expandedPlaylist = userPlaylists.value.find { it.id == playlistId }
                        },
                        onRemoveSongFromPlaylist = { playlistId, songRid ->
                            mainViewModel.removeSongFromPlaylist(playlistId, songRid)
                        }
                    )
                }
                composable("rankings") {
                    RankingsScreen(
                        mainViewModel = mainViewModel,
                        onSongClick = { _, _ -> },
                        initialBangId = null
                    )
                }
                composable(
                    route = "rankings/{bangId}",
                    arguments = listOf(navArgument("bangId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bangId = backStackEntry.arguments?.getString("bangId")
                    RankingsScreen(
                        mainViewModel = mainViewModel,
                        onSongClick = { _, _ -> },
                        initialBangId = bangId
                    )
                }
                composable(
                    route = Screen.Playlist.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    PlaylistScreen(
                        playlistId = playlistId,
                        onBackClick = { navController.popBackStack() },
                        onSongClick = { song, playlist ->
                            mainViewModel.playSongPrev(song)
                        },
                        currentPlaylist = mainViewModel.currentPlaylist.value,
                        onPlayAll = {
                            mainViewModel.playPlaylist(playlistId)
                        }
                    )
                }
                composable(Screen.Player.route) {
                    PlayerScreen(
                        onBackClick = { navController.popBackStack() },
                        viewModel = mainViewModel
                    )
                }
            }

            val showMiniPlayer = currentSong.value != null &&
                currentDestination?.route != Screen.Player.route
            if (showMiniPlayer) {
                MiniPlayer(
                    song = currentSong.value!!,
                    isPlaying = isPlaying.value,
                    isFavorite = favoriteSongs.value.contains(currentSong.value!!.rid),
                    onPlayPause = { mainViewModel.togglePlayPause() },
                    onToggleFavorite = { mainViewModel.toggleFavorite(currentSong.value!!) },
                    onPrevious = { mainViewModel.playPrevious() },
                    onNext = { mainViewModel.playNext() },
                    onClick = { navController.navigate(Screen.Player.route) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            // 更新弹窗（有新版本时自动弹出）
            pendingVersionInfo.value?.let { versionInfo ->
                UpdateDialog(
                    versionInfo = versionInfo,
                    updateEvent = updateEvent,
                    onConfirmDownload = { updateViewModel.onConfirmDownload() },
                    onDismiss = { updateViewModel.onDismiss() },
                    onConfirmInstall = { updateViewModel.onConfirmInstall() },
                    onDeferInstall = { updateViewModel.onDeferInstall() }
                )
            }
        }
    }

}
