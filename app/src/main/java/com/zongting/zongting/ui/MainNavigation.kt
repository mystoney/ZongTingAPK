package com.zongting.zongting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Search : Screen("search", "搜索", Icons.Default.Search)
    object Library : Screen("library", "音乐库", Icons.Default.LibraryMusic)
    object Rankings : Screen("rankings", "排行榜", Icons.Default.Leaderboard)
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
    mainViewModel: MainViewModel = hiltViewModel()
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

    LaunchedEffect(Unit) {
        com.zongting.zongting.player.PlayerManager.onPlayingChanged = { playing ->
            mainViewModel.updatePlayingState(playing)
        }
        com.zongting.zongting.player.PlayerManager.onSongChanged = { song, index ->
            mainViewModel.updateCurrentSong(song, index)
        }
    }

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        },
                        onSongClick = { song, playlist ->
                            mainViewModel.playSong(song, playlist)
                        },
                        onPlaylistPlay = { playlistId ->
                            mainViewModel.playPlaylistIfEmpty(playlistId)
                        }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        onSongClick = { song, playlist ->
                            mainViewModel.playSong(song, playlist)
                        }
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        favoriteSongs = favoriteSongList.value,
                        recentlyPlayed = recentlyPlayed.value,
                        userPlaylists = userPlaylists.value,
                        onSongClick = { song, playlist ->
                            mainViewModel.appendToQueueAndPlay(song)
                        },
                        onSongLongPress = { song ->
                            mainViewModel.appendToQueueAndPlay(song)
                            // 对话框由 LibraryScreen 内部状态控制
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
                            mainViewModel.addSongToPlaylist(playlistId, song)
                        },
                        onRemoveSongFromPlaylist = { playlistId, songRid ->
                            mainViewModel.removeSongFromPlaylist(playlistId, songRid)
                        }
                    )
                }
                composable(Screen.Rankings.route) {
                    RankingsScreen(
                        onSongClick = { song, playlist ->
                            mainViewModel.playSong(song, playlist)
                        }
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
                            mainViewModel.playSong(song, playlist)
                        },
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
        }
    }
}
