package com.zongting.zongting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zongting.zongting.ui.screens.*
import com.zongting.zongting.ui.theme.AppColors
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.data.repository.UpdateEvent
import com.zongting.zongting.data.repository.UpdatePhase
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults

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
    windowSizeClass: WindowSizeClass,
    mainViewModel: MainViewModel = hiltViewModel(),
    updateViewModel: com.zongting.zongting.ui.screens.UpdateViewModel = hiltViewModel()
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val configuration = LocalConfiguration.current
    val isLandscapePhone = !isExpanded && configuration.screenWidthDp > configuration.screenHeightDp
    // PAD 判定：宽度 ≥ 600dp（包含 Medium 竖屏 + Expanded 横屏）
    val isPad = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    // PAD 文字统一放大 20%（仅 sp，不影响 dp 布局）
    val fontScaleMultiplier = if (isPad) 1.2f else 1.0f
    val baseDensity = LocalDensity.current
    val effectiveDensity = remember(baseDensity, fontScaleMultiplier) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScaleMultiplier
        )
    }
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

    // 手机横屏时：有歌曲则自动进入播放器，无歌曲则留在首页
    LaunchedEffect(isLandscapePhone, currentSong.value) {
        if (isLandscapePhone && currentSong.value != null) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Player.route) {
                navController.navigate(Screen.Player.route) {
                    launchSingleTop = true
                }
            }
        } else if (isLandscapePhone && currentSong.value == null) {
            // 无歌曲时确保留在首页
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Home.route) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            }
        }
    }

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

    val showBottomBar = !isLandscapePhone && currentDestination?.route?.let { route ->
        bottomNavItems.any { item ->
            val base = item.route.split("/{")[0]
            route == item.route || route.startsWith(base + "/")
        }
    } ?: true

    CompositionLocalProvider(LocalDensity provides effectiveDensity) {
    if (isExpanded) {
        // ── 平板横屏：侧边 NavigationRail ────────────────────────────
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(Modifier.weight(1f))
                bottomNavItems.forEachIndexed { index, screen ->
                    val isSelected = currentDestination?.route == screen.route ||
                        (screen.route != "rankings" && currentDestination?.route?.startsWith(screen.route) == true)
                    NavigationRailItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title, modifier = Modifier.size(30.dp)) },
                        label = { Text(screen.title, maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = AppColors.PrimaryVariant
                        )
                    )
                    if (index < bottomNavItems.size - 1) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            // 主内容区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NavHostContent(navController, mainViewModel, updateViewModel, currentSong, isPlaying, favoriteSongs, favoriteSongList, recentlyPlayed, userPlaylists, expandedPlaylist, pendingVersionInfo.value, updateEvent, updatePhase, windowSizeClass, isLandscapePhone, onPlaylistExpandChange = { expandedPlaylist = it })
            }
        }
    } else {
        // ── 手机 / 平板竖屏：底部 NavigationBar ───────────────────────
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    Surface(color = AppColors.PrimaryVariant) {
                        NavigationBar {
                            bottomNavItems.forEach { screen ->
                                val isSelected = currentDestination?.route == screen.route ||
                                    (screen.route != "rankings" && currentDestination?.route?.startsWith(screen.route) == true)
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) },
                                    selected = isSelected,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AppColors.NavSelected,
                                        selectedTextColor = AppColors.NavSelected,
                                        indicatorColor = AppColors.PrimaryVariant.copy(alpha = 0.5f)
                                    ),
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                )
                            }
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
                NavHostContent(navController, mainViewModel, updateViewModel, currentSong, isPlaying, favoriteSongs, favoriteSongList, recentlyPlayed, userPlaylists, expandedPlaylist, pendingVersionInfo.value, updateEvent, updatePhase, windowSizeClass, isLandscapePhone, onPlaylistExpandChange = { expandedPlaylist = it })
            }
        }
    }
    }
}
// ── NavHost + MiniPlayer + UpdateDialog（手机/平板共用） ──────────────────────────
@Composable
private fun NavHostContent(
    navController: androidx.navigation.NavHostController,
    mainViewModel: MainViewModel,
    updateViewModel: com.zongting.zongting.ui.screens.UpdateViewModel,
    currentSong: State<com.zongting.zongting.data.model.Song?>,
    isPlaying: State<Boolean>,
    favoriteSongs: State<Set<Long>>,
    favoriteSongList: State<List<com.zongting.zongting.data.model.Song>>,
    recentlyPlayed: State<List<com.zongting.zongting.data.model.Song>>,
    userPlaylists: State<List<UserPlaylist>>,
    expandedPlaylist: UserPlaylist?,
    pendingVersionInfo: com.zongting.zongting.data.model.VersionInfo?,
    updateEvent: com.zongting.zongting.data.repository.UpdateEvent?,
    updatePhase: UpdatePhase,
    windowSizeClass: WindowSizeClass,
    isLandscapePhone: Boolean,
    onPlaylistExpandChange: (UserPlaylist?) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val eventState = remember(updateEvent) { updateEvent }
    val phaseState = remember(updatePhase) { updatePhase }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = hiltViewModel(),
                updateViewModel = updateViewModel,
                windowSizeClass = windowSizeClass,
                isLandscapePhone = isLandscapePhone,
                onGoToPlayer = { navController.navigate(Screen.Player.route) },
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
                },
                onBangPlay = { bang ->
                    mainViewModel.playBang(bang)
                }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                windowSizeClass = windowSizeClass,
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
                windowSizeClass = windowSizeClass,
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
                        onPlaylistExpandChange(userPlaylists.value.find { it.id == playlistId })
                    }
                },
                onAddSongsToPlaylist = { playlistId, songs ->
                    mainViewModel.addSongsToPlaylist(playlistId, songs) {
                        onPlaylistExpandChange(userPlaylists.value.find { it.id == playlistId })
                    }
                },
                onCreateAndAddSongs = { name, songs ->
                    mainViewModel.createPlaylistAndAddSongs(name, songs) { newId ->
                        onPlaylistExpandChange(userPlaylists.value.find { it.id == newId })
                    }
                },
                onSongsAdded = { playlistId ->
                    onPlaylistExpandChange(userPlaylists.value.find { it.id == playlistId })
                },
                onRemoveSongFromPlaylist = { playlistId, songRid ->
                    mainViewModel.removeSongFromPlaylist(playlistId, songRid)
                }
            )
        }
        composable("rankings") {
            RankingsScreen(
                rankingsViewModel = hiltViewModel(),
                mainViewModel = mainViewModel,
                onSongClick = { _, _ -> },
                initialBangId = null,
                windowSizeClass = windowSizeClass
            )
        }
        composable(
            route = "rankings/{bangId}",
            arguments = listOf(navArgument("bangId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bangId = backStackEntry.arguments?.getString("bangId")
            RankingsScreen(
                rankingsViewModel = hiltViewModel(),
                mainViewModel = mainViewModel,
                onSongClick = { _, _ -> },
                initialBangId = bangId,
                windowSizeClass = windowSizeClass,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Playlist.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            PlaylistScreen(
                windowSizeClass = windowSizeClass,
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
                windowSizeClass = windowSizeClass,
                isLandscapePhone = isLandscapePhone,
                onBackClick = {
                    if (isLandscapePhone) {
                        navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
                viewModel = mainViewModel
            )
        }
    }

    val showMiniPlayer = !isLandscapePhone && currentSong.value != null &&
        currentDestination?.route != Screen.Player.route
    if (showMiniPlayer) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiniPlayer(
                song = currentSong.value!!,
                isPlaying = isPlaying.value,
                isFavorite = favoriteSongs.value.contains(currentSong.value!!.rid),
                playMode = mainViewModel.playMode.value,
                onTogglePlayMode = { mainViewModel.togglePlayMode() },
                onPlayPause = { mainViewModel.togglePlayPause() },
                onToggleFavorite = { mainViewModel.toggleFavorite(currentSong.value!!) },
                onPrevious = { mainViewModel.playPrevious() },
                onNext = { mainViewModel.playNext() },
                onClick = { navController.navigate(Screen.Player.route) },
                modifier = Modifier.align(Alignment.BottomCenter),
                windowSizeClass = windowSizeClass
            )
        }
    }
    // 更新弹窗（有新版本时自动弹出）
    pendingVersionInfo?.let { versionInfo ->
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

