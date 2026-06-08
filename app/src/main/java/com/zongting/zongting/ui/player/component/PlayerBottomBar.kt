package com.zongting.zongting.ui.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.AddToPlaylistIcon
import com.zongting.zongting.ui.FavoriteIcon
import com.zongting.zongting.ui.PlayModeIcon
import com.zongting.zongting.ui.PlayPauseIcon
import com.zongting.zongting.ui.PlaylistIcon
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.ui.RingtoneIcon
import com.zongting.zongting.ui.SkipNextIcon
import com.zongting.zongting.ui.SkipPreviousIcon
import com.zongting.zongting.ui.TimerIcon

/**
 * Bottom control bar of the PlayerScreen. Houses the play/pause/next/previous
 * row, secondary actions (mode / queue / save / timer / ringtone / favorite),
 * the sleep-timer countdown, and a modal bottom-sheet for the current
 * queue list.
 *
 * Originally lived as a `private fun` inside `PlayerScreen.kt`. Hoisted
 * here so it can be shared across phone/pad layouts and reused by the
 * new [com.zongting.zongting.ui.player.layout.PadPortrait] entry point.
 */
@Composable
fun PlayerBottomBar(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    isFavorite: Boolean,
    showPlaylist: Boolean,
    onTogglePlay: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onShowPlaylist: (Boolean) -> Unit,
    onToggleSavePlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onRingtoneCutterClick: () -> Unit,
    isTimerActive: Boolean,
    timerRemaining: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    currentPlaylist: List<Song>,
    onPlaySong: (Song) -> Unit,
    playlistListState: LazyListState,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass
) {
    // 乐观更新：本地记住当前图标状态，点击立即切换，异步同步真实 playMode
    var localPlayMode by remember { mutableIntStateOf(playMode) }
    LaunchedEffect(playMode) { localPlayMode = playMode }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 4.dp
    ) {
        val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
        val scale = if (isExpanded) 1.3f else 1f
        val basePadding = 16.dp
        val baseBtnSize = 48.dp
        val baseFilledBtnSize = 56.dp
        val baseIconSize = 28.dp
        val baseLargeIconSize = 32.dp
        val baseSmallIconSize = 24.dp
        val scaledPadding = (basePadding.value * scale).dp.coerceAtLeast(basePadding)
        val scaledBtnSize = (baseBtnSize.value * scale).dp.coerceAtLeast(baseBtnSize)
        val scaledFilledBtnSize = (baseFilledBtnSize.value * scale).dp.coerceAtLeast(baseFilledBtnSize)
        val scaledIconSize = (baseIconSize.value * scale).dp.coerceAtLeast(baseIconSize)
        val scaledLargeIconSize = (baseLargeIconSize.value * scale).dp.coerceAtLeast(baseLargeIconSize)
        val scaledSmallIconSize = (baseSmallIconSize.value * scale).dp.coerceAtLeast(baseSmallIconSize)

        Column(modifier = Modifier.padding(horizontal = scaledPadding, vertical = 4.dp)) {
            // 上一首 / 播放暂停 / 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkipPreviousIcon(
                    onClick = onPrevious,
                    modifier = Modifier.size(scaledBtnSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )

                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(scaledFilledBtnSize), shape = CircleShape) {
                    PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, modifier = Modifier.size(scaledLargeIconSize))
                }

                SkipNextIcon(
                    onClick = onNext,
                    modifier = Modifier.size(scaledBtnSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // 附加功能按钮（播放模式 / 播放列表 / 添加到歌单 / 收藏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {
                    localPlayMode = (localPlayMode + 1) % 3
                    onTogglePlayMode()
                }) {
                    PlayModeIcon(playMode = localPlayMode, tint = Color.White, modifier = Modifier.size(scaledSmallIconSize))
                }

                PlaylistIcon(
                    onClick = { onShowPlaylist(true) },
                    tint = MaterialTheme.colorScheme.onSurface
                )

                AddToPlaylistIcon(
                    onClick = onToggleSavePlaylist,
                    tint = MaterialTheme.colorScheme.onSurface
                )

                TimerIcon(
                    isActive = isTimerActive,
                    onClick = onSleepTimerClick,
                    tint = if (isTimerActive) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface
                )

                RingtoneIcon(
                    onClick = onRingtoneCutterClick,
                    tint = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onToggleFavorite) {
                    FavoriteIcon(
                        isFavorite = isFavorite,
                        tint = Color.White,
                        contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                        modifier = Modifier.size(scaledSmallIconSize)
                    )
                }
            }

            // 定时关闭剩余时间（仅开启时显示）
            if (isTimerActive && timerRemaining > 0) {
                val mins = timerRemaining / 60
                val secs = timerRemaining % 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "🌙 ${mins}分${secs}秒后停止播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7C4DFF)
                    )
                }
            }
        }
    }

    // 播放列表底部弹出面板
    if (showPlaylist) {
        ModalBottomSheet(
            onDismissRequest = { onShowPlaylist(false) },
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
                                    val isExpandedRow = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                                    val rowScale = if (isExpandedRow) 1.3f else 1f
                                    val rowIconSize = (24.dp.value * rowScale).dp.coerceAtLeast(24.dp)
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
}
