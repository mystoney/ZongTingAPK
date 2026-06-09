package com.zongting.zongting.ui.player.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.graphicsLayer
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.screens.RingtoneCutterScreen
import com.zongting.zongting.ringtone.RingtoneCutterViewModel
import com.zongting.zongting.ui.AddToPlaylistIcon
import com.zongting.zongting.ui.FavoriteIcon
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.PlayModeIcon
import com.zongting.zongting.ui.PlayPauseIcon
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.ui.PlaylistIcon
import com.zongting.zongting.ui.RingtoneIcon
import com.zongting.zongting.ui.SkipNextIcon
import com.zongting.zongting.ui.SkipPreviousIcon
import com.zongting.zongting.ui.TimerIcon
import com.zongting.zongting.ui.player.component.SavePlaylistDialog
import com.zongting.zongting.ui.player.component.SleepTimerDialog
import com.zongting.zongting.ui.player.component.VinylRecord
import com.zongting.zongting.ui.player.util.formatDuration
import com.zongting.zongting.ui.theme.AppColors
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Tablet landscape (横屏 pad) player layout. Top half split into:
 *  - Left 38% — vinyl record with overlaid song meta (source, name, artist).
 *  - Right 62% — auto-scrolling lyrics with current line highlighted.
 *
 * Below that: a full-width slider + time labels, and a single row of
 * core controls (prev / play / next) and function keys (queue, mode,
 * favorite, timer, ringtone, add-to-playlist).
 *
 * The layout also owns local `remember { mutableStateOf(false) }` flags
 * for the four transient UI surfaces: sleep-timer dialog, save-playlist
 * dialog, ringtone-cutter screen, and queue modal-bottom-sheet. These
 * are intentionally local to the layout so the parent (`PlayerRoute`)
 * does not have to thread show/hide state through.
 *
 * Originally `fun PlayerScreenPADLandscape` in PlayerScreen.kt, hoisted
 * here so the `PlayerRoute` entry point can dispatch to it on
 * `isExpanded` (>=840dp width).
 */
@Composable
fun PadLandscape(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    lyricState: LyricState,
    playMode: Int,
    isFavorite: Boolean,
    baseDensity: Density,
    onBackClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    currentPlaylist: List<Song>,
    onPlaySong: (Song) -> Unit,
    playlistListState: LazyListState,
    userPlaylists: List<UserPlaylist>,
    isTimerActive: Boolean,
    timerRemaining: Long,
    onAddSongToPlaylist: (String, Song, () -> Unit) -> Unit,
    onCreatePlaylistAndAddSong: (String, Song) -> Unit,
    imageLoader: ImageLoader,
    onShowPlaylist: (Boolean) -> Unit = {},
    onToggleSavePlaylist: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onRingtoneCutterClick: () -> Unit = {}
) {
    val backgroundColor = Color(0xFF0D0D1A)
    val accentColor = AppColors.Accent

    // 本地 dialog/sheet 状态（解决 return 后父级不渲染的问题）
    var showSleepTimerDialogLocal by remember { mutableStateOf(false) }
    var showSavePlaylistDialogLocal by remember { mutableStateOf(false) }
    var showRingtoneCutterLocal by remember { mutableStateOf(false) }
    var showPlaylistSheetLocal by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // 模糊背景封面
        currentSong?.let { song ->
            val coverUrl = song.coverUrl ?: song.pic
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.3f },
                contentScale = ContentScale.Crop
            )
        }

        if (currentSong == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无正在播放的歌曲", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .navigationBarsPadding()
            ) {
                // ── 上部：左侧唱片 + 右侧歌词 ─────────────────────────────
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 左侧 38%：唱片 + 来源/歌名/歌手（按列宽自适应）
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val configuration = LocalConfiguration.current
                        val screenHeight = configuration.screenHeightDp.dp
                        // cover 按"左列实际宽度"算，不再依赖全屏宽
                        val coverSize = minOf(maxWidth * 0.85f, screenHeight * 0.7f)

                        // 歌名 / 歌手（无降档，固定 sp，由 Stoney 后续按需调整）
                        val nameFontSp = 14f
                        val artistFontSp = 12f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            // 黑胶唱片（旋转动画）
                            VinylRecord(
                                albumArtUrl = currentSong.coverUrl ?: currentSong.pic,
                                isPlaying = isPlaying,
                                modifier = Modifier.size(coverSize * 0.9f),
                                imageLoader = imageLoader
                            )
                        }

                        // 左上角：来源 / 歌名 / 歌手（描边样式，字号随列宽）
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 24.dp, start = 12.dp, end = 12.dp)
                        ) {
                            // 来源小字（固定 14sp）
                            val sourceText = when (currentSong.source) {
                                "kg" -> "酷狗音乐"
                                "qq" -> "QQ音乐"
                                "wy" -> "网易云"
                                "mg" -> "咪咕"
                                else -> currentSong.source.uppercase()
                            }
                            Box {
                                Text(
                                    text = sourceText,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                                )
                                Text(
                                    text = sourceText,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            // 歌名（大字，按列宽降档）
                            Box {
                                Text(
                                    text = currentSong.name,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    fontSize = nameFontSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                                )
                                Text(
                                    text = currentSong.name,
                                    color = Color.White,
                                    fontSize = nameFontSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // 歌手（按列宽降档）
                            Box {
                                Text(
                                    text = currentSong.artist,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    fontSize = artistFontSp.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                                )
                                Text(
                                    text = currentSong.artist,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = artistFontSp.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 右侧 62%：滚动歌词（每行按右列实际宽度自适应字号）
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                    ) {
                        // 留 48dp 右内边距给文字呼吸
                        // 歌词：opt-out 2.5x 字体放大，保持 1.0x
                        CompositionLocalProvider(LocalDensity provides baseDensity) {
                            when (val state = lyricState) {
                                is LyricState.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = accentColor)
                                    }
                                }
                                is LyricState.Error -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("歌词加载失败", color = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                                is LyricState.Success -> {
                                    val lyrics = state.lyrics
                                    if (lyrics.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("纯音乐，欣赏中...", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    } else {
                                        val currentIndex = lyrics.indexOfLast { it.timestamp <= playbackState.position }
                                            .coerceAtLeast(0)

                                        val listState = rememberLazyListState()
                                        val density = LocalDensity.current
                                        val itemSpacing = with(density) { 10.dp.toPx() }
                                        val itemHeight = with(density) { 40.dp.toPx() }
                                        val totalItemHeight = itemHeight + itemSpacing

                                        LaunchedEffect(currentIndex) {
                                            listState.animateScrollToItem(
                                                index = maxOf(0, currentIndex - 1),
                                                scrollOffset = -((listState.layoutInfo.viewportSize.height / 2) - (2 * totalItemHeight + itemHeight / 2)).toInt()
                                            )
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().padding(end = 48.dp),
                                            state = listState,
                                            horizontalAlignment = Alignment.Start,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(vertical = 16.dp)
                                        ) {
                                            itemsIndexed(lyrics) { index, line ->
                                                val isCurrent = index == currentIndex
                                                val isNext = index == currentIndex + 1
                                                val isPlayed = index < currentIndex - 1
                                                val isFarFuture = index > currentIndex + 1
                                                val alpha = if (isCurrent) 1f else 0.5f
                                                val baseFontSp = if (isCurrent) 30f else if (isNext) 26f else 19f
                                                val leadSpace = ""

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = if (isPlayed || isFarFuture) Arrangement.End else Arrangement.Start
                                                ) {
                                                    val displayText = if (isCurrent && line.text.isNotEmpty()) line.text + " " else if (line.text.isEmpty()) " " else line.text
                                                    Box {
                                                        if (isCurrent) {
                                                            Text(
                                                                text = "$leadSpace$displayText",
                                                                color = Color.Black.copy(alpha = 0.8f),
                                                                fontSize = baseFontSp.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "$leadSpace$displayText",
                                                            color = Color.White.copy(alpha = alpha),
                                                            fontSize = baseFontSp.sp,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                is LyricState.Idle -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("歌词加载中...", color = Color.White.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 进度条 + 时间（横跨全宽） ─────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // 时间在进度条上方两端
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playbackState.position),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatDuration(playbackState.duration),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = if (playbackState.duration > 0) {
                            playbackState.position.toFloat() / playbackState.duration.toFloat()
                        } else 0f,
                        onValueChange = { fraction -> onDrag((fraction * playbackState.duration).toLong()) },
                        onValueChangeFinished = { onSeek(playbackState.position) },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                // ── 全部按钮一行 ──────────────────────────────────────────
                val screenWidth = LocalConfiguration.current.screenWidthDp
                val fnBtnSize = (screenWidth * 0.028f).coerceIn(20f, 28f).dp
                val coreBtnSize = (28 + (screenWidth - 360) * 0.04f).coerceIn(26f, 40f).dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkipPreviousIcon(
                        onClick = onPrevious,
                        modifier = Modifier.size(coreBtnSize * 1.3f),
                        tint = Color.White
                    )
                    FilledIconButton(
                        onClick = onTogglePlay,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accentColor),
                        modifier = Modifier.size(coreBtnSize * 1.7f)
                    ) {
                        PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, modifier = Modifier.size(coreBtnSize))
                    }
                    SkipNextIcon(
                        onClick = onNext,
                        modifier = Modifier.size(coreBtnSize * 1.3f),
                        tint = Color.White
                    )
                    PlaylistIcon(
                        onClick = { showPlaylistSheetLocal = true },
                        modifier = Modifier.size(fnBtnSize * 1.4f),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                    IconButton(onClick = onTogglePlayMode, modifier = Modifier.size(fnBtnSize * 1.4f)) {
                        PlayModeIcon(playMode = playMode, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(fnBtnSize))
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(fnBtnSize * 1.4f)) {
                        FavoriteIcon(isFavorite = isFavorite, tint = Color.White.copy(alpha = 0.8f), contentDescription = "收藏", modifier = Modifier.size(fnBtnSize))
                    }
                    TimerIcon(
                        isActive = false,
                        onClick = { showSleepTimerDialogLocal = true },
                        modifier = Modifier.size(fnBtnSize * 1.4f),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                    RingtoneIcon(
                        onClick = { showRingtoneCutterLocal = true },
                        modifier = Modifier.size(fnBtnSize * 1.4f),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                    AddToPlaylistIcon(
                        onClick = { showSavePlaylistDialogLocal = true },
                        modifier = Modifier.size(fnBtnSize * 1.4f),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 定时关闭弹窗（铃声截取界面时隐藏）
        if (showSleepTimerDialogLocal && !showRingtoneCutterLocal) {
            SleepTimerDialog(
                isActive = isTimerActive,
                remainingSeconds = timerRemaining,
                onStartTimer = { mins ->
                    SleepTimerManager.start(context, mins)
                },
                onCancelTimer = {
                    SleepTimerManager.cancelWithNotification(context)
                },
                onDismiss = { showSleepTimerDialogLocal = false }
            )
        }

        // 添加到歌单弹窗（铃声截取界面时隐藏）
        if (showSavePlaylistDialogLocal && !showRingtoneCutterLocal) {
            SavePlaylistDialog(
                songCount = 1,
                playlists = userPlaylists,
                onSelectPlaylist = { playlistId ->
                    currentSong?.let { song -> onAddSongToPlaylist(playlistId, song) {} }
                },
                onCreatePlaylist = { name ->
                    currentSong?.let { song ->
                        onCreatePlaylistAndAddSong(name, song)
                    }
                },
                onDismiss = { showSavePlaylistDialogLocal = false }
            )
        }

        // 播放列表底部弹出面板
        if (showPlaylistSheetLocal) {
            ModalBottomSheet(
                onDismissRequest = { showPlaylistSheetLocal = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = Color(0xF20B1E10)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xF20B1E10))
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "当前播放列表 (${currentPlaylist.size}首)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    if (currentPlaylist.isEmpty()) {
                        Text(
                            text = "播放列表为空",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xF20B1E10))
                                .heightIn(max = 400.dp),
                            state = playlistListState,
                        ) {
                            itemsIndexed(currentPlaylist) { index, song ->
                                val isCurrentSong = song.rid == currentSong?.rid
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .combinedClickable(
                                            onClick = { onPlaySong(song) },
                                            onDoubleClick = { onPlaySong(song) }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
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
                                    if (isCurrentSong) {
                                        val rowIconSize = (24.dp.value * 1.3f).dp.coerceAtLeast(24.dp)
                                        IconButton(onClick = onTogglePlay) {
                                            PlayPauseIcon(
                                                isPlaying = isPlaying,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(rowIconSize)
                                            )
                                        }
                                    }
                                }
                                if (index < currentPlaylist.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 铃声截取界面（全屏覆盖）
        if (showRingtoneCutterLocal) {
            val lyrics = (lyricState as? LyricState.Success)?.lyrics ?: emptyList()
            val durationMs = playbackState.duration.coerceAtLeast(0L)

            val ringtoneViewModel: RingtoneCutterViewModel = hiltViewModel()
            LaunchedEffect(currentSong, durationMs, lyrics) {
                if (durationMs > 0) {
                    ringtoneViewModel.initialize(currentSong, durationMs, lyrics)
                }
            }

            RingtoneCutterScreen(
                onBackClick = {
                    ringtoneViewModel.stopPreview()
                    showRingtoneCutterLocal = false
                },
                viewModel = ringtoneViewModel,
                lyrics = lyrics
            )
        }
    }
}
