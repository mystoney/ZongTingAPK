package com.zongting.zongting.ui.player.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.player.SleepTimerManager
import com.zongting.zongting.ui.screens.RingtoneCutterScreen
import com.zongting.zongting.ringtone.RingtoneCutterViewModel
import com.zongting.zongting.ui.theme.AppColors
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.PlayPauseIcon
import com.zongting.zongting.ui.player.PlayerActions
import com.zongting.zongting.ui.player.PlayerUiState
import com.zongting.zongting.ui.player.component.LyricPage
import com.zongting.zongting.ui.player.component.PlayerBottomBar
import com.zongting.zongting.ui.player.component.SavePlaylistDialog
import com.zongting.zongting.ui.player.component.SleepTimerDialog
import com.zongting.zongting.ui.player.component.VinylRecord

/**
 * Three-column layout for **portrait**-orientation tablets (Medium width
 * size class, height greater than width).
 *
 * Layout — `widthIn(max=1100.dp)` centered, three vertical columns side by side:
 *
 * ┌─────────── AppBar (back / title / quick actions) ───────────┐
 * │  Vinyl + meta    │   LyricPage (no pager)   │  Queue list  │
 * │      0.40        │         0.35             │     0.25     │
 * ├──────────────── PlayerBottomBar (cross-column) ─────────────┤
 *
 * The right column is rendered as a LazyColumn with the up-next queue;
 * tapping a row seeks to and starts that song. Because the queue is
 * always visible, [PlayerBottomBar]'s built-in queue modal is not used
 * here (it would double-render the same data) — its `onShowPlaylist`
 * callback is therefore wired to a no-op.
 *
 * @param state snapshot of all reactive state the player needs.
 * @param actions callback bundle for every user-driven event.
 * @param imageLoader Coil loader used by [VinylRecord] to fetch the
 *   album artwork; supplied by the caller to share the same instance
 *   across the app.
 * @param windowSizeClass propagated to [PlayerBottomBar] so its scale
 *   factor (icon / button sizes) stays consistent with the rest of
 *   the player UI.
 */
@Composable
fun PadPortrait(
    state: PlayerUiState,
    actions: PlayerActions,
    imageLoader: ImageLoader,
    windowSizeClass: WindowSizeClass,
) {
    val queueListState = rememberLazyListState()
    val context = LocalContext.current

    // Dialog state — same pattern as PhonePortrait. The trigger callbacks
    // handed down to PlayerBottomBar flip these local flags instead of
    // relying on a top-level `show*Dialog` (no longer needed since each
    // layout owns its dialog lifecycle now).
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showRingtoneCutter by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // ───── AppBar ─────
            PadPortraitTopBar(state, actions)

            Spacer(Modifier.height(8.dp))

            // ───── Three-column body ─────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1 — Vinyl + meta (40%)
                PadPortraitCoverColumn(
                    state = state,
                    actions = actions,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                )

                // Column 2 — Lyrics (35%)
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                ) {
                    LyricPage(
                        currentSong = state.currentSong,
                        lyricState = state.lyricState,
                        playbackState = state.playbackState,
                        isPlaying = state.isPlaying,
                        onTogglePlay = actions.onTogglePlay,
                        onPrevious = actions.onPrevious,
                        onNext = actions.onNext,
                        onDrag = actions.onDrag,
                        onSeek = actions.onSeek,
                        isPad = true
                    )
                }

                // Column 3 — Queue (25%)
                PadPortraitQueueColumn(
                    state = state,
                    actions = actions,
                    listState = queueListState,
                    modifier = Modifier
                        .weight(0.25f)
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(8.dp))

            // ───── Cross-column bottom bar ─────
            PlayerBottomBar(
                currentSong = state.currentSong,
                isPlaying = state.isPlaying,
                playbackState = state.playbackState,
                playMode = state.playMode,
                isFavorite = state.isFavorite,
                showPlaylist = false,                  // queue already shown in column 3
                onTogglePlay = actions.onTogglePlay,
                onTogglePlayMode = actions.onTogglePlayMode,
                onShowPlaylist = { /* no-op: queue is always visible */ },
                onToggleSavePlaylist = { showSavePlaylistDialog = true },
                onToggleFavorite = actions.onToggleFavorite,
                onSleepTimerClick = { showSleepTimerDialog = true },
                onRingtoneCutterClick = {
                    // PhonePortrait pauses the player before opening the
                    // cutter (no playback underneath while editing). Same
                    // behavior here — PlayerManager is the static singleton.
                    PlayerManager.pause()
                    showRingtoneCutter = true
                },
                isTimerActive = state.isTimerActive,
                timerRemaining = state.timerRemaining,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                currentPlaylist = state.currentPlaylist,
                onPlaySong = actions.onPlaySong,
                playlistListState = queueListState,
                windowSizeClass = windowSizeClass
            )

            // ───── Dialogs / full-screen ringtone cutter ─────
            if (showSleepTimerDialog && !showRingtoneCutter) {
                SleepTimerDialog(
                    isActive = state.isTimerActive,
                    remainingSeconds = state.timerRemaining,
                    onStartTimer = { mins -> SleepTimerManager.start(context, mins) },
                    onCancelTimer = { SleepTimerManager.cancelWithNotification(context) },
                    onDismiss = { showSleepTimerDialog = false }
                )
            }

            if (showSavePlaylistDialog && !showRingtoneCutter) {
                SavePlaylistDialog(
                    songCount = 1,
                    playlists = state.userPlaylists,
                    onSelectPlaylist = { playlistId ->
                        state.currentSong?.let { song ->
                            actions.onAddSongToPlaylist(playlistId, song) {}
                        }
                    },
                    onCreatePlaylist = { name ->
                        state.currentSong?.let { song ->
                            actions.onCreatePlaylistAndAddSong(name, song)
                        }
                    },
                    onDismiss = { showSavePlaylistDialog = false }
                )
            }

            if (showRingtoneCutter) {
                val lyrics = (state.lyricState as? LyricState.Success)?.lyrics ?: emptyList()
                val durationMs = state.playbackState.duration.coerceAtLeast(0L)
                val ringtoneViewModel: RingtoneCutterViewModel = hiltViewModel()
                LaunchedEffect(state.currentSong, durationMs, lyrics) {
                    if (durationMs > 0 && state.currentSong != null) {
                        ringtoneViewModel.initialize(state.currentSong, durationMs, lyrics)
                    }
                }
                RingtoneCutterScreen(
                    onBackClick = { showRingtoneCutter = false },
                    viewModel = ringtoneViewModel,
                    lyrics = lyrics
                )
            }
        }
    }
}

@Composable
private fun PadPortraitTopBar(
    state: PlayerUiState,
    actions: PlayerActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = actions.onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "正在播放",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        // 定时关闭状态
        if (state.isTimerActive && state.timerRemaining > 0) {
            val mins = state.timerRemaining / 60
            val secs = state.timerRemaining % 60
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = Color(0xFF7C4DFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${mins}:${"%02d".format(secs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7C4DFF)
            )
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = actions.onToggleFavorite) {
            Icon(
                imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.Favorite,
                contentDescription = if (state.isFavorite) "取消喜欢" else "我喜欢",
                tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onToggleSavePlaylist) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加到歌单",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onSleepTimerClick) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "定时关闭",
                tint = if (state.isTimerActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = actions.onRingtoneCutterClick) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "铃声剪辑",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PadPortraitCoverColumn(
    state: PlayerUiState,
    actions: PlayerActions,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (song == null) {
            // 静默占位：保持栏位但不渲染任何内容
        } else {
            // VinylRecord
            VinylRecord(
                albumArtUrl = song.coverUrl ?: song.pic,
                isPlaying = state.isPlaying,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f),
                imageLoader = imageLoader
            )
            // Song meta
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PadPortraitQueueColumn(
    state: PlayerUiState,
    actions: PlayerActions,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "队列 (${state.currentPlaylist.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.currentPlaylist.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(state.currentPlaylist, key = { _, s -> s.rid }) { index, song ->
                        QueueRow(
                            index = index,
                            song = song,
                            isCurrent = song.rid == state.currentSong?.rid,
                            isPlaying = state.isPlaying,
                            onClick = { actions.onPlaySong(song) },
                            onPlayPause = actions.onTogglePlay
                        )
                        if (index < state.currentPlaylist.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) AppColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) AppColors.Accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                PlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


