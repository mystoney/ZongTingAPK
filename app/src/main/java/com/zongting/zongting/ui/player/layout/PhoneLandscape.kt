package com.zongting.zongting.ui.player.layout

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.FavoriteIcon
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.PlayModeIcon
import com.zongting.zongting.ui.PlayPauseIcon
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.ui.SkipNextIcon
import com.zongting.zongting.ui.SkipPreviousIcon
import com.zongting.zongting.ui.theme.AppColors
import com.zongting.zongting.ui.player.util.formatDuration
import com.zongting.zongting.ui.player.component.VinylRecord

/**
 * Phone landscape (横屏手机) player layout. Two-column:
 *  - Left 40% — vinyl record + bottom control panel (slider, play/pause/next/previous,
 *    play-mode, favorite).
 *  - Right 60% — auto-scrolling lyrics list, current line highlighted + bolded.
 *
 * The full-screen background is a faded copy of the current cover. Originally
 * `fun PlayerScreenLandscape` in PlayerScreen.kt, hoisted here so the
 * `PlayerRoute` entry point can dispatch to it on `isLandscapePhone`.
 *
 * Note: this layout pre-dates the [com.zongting.zongting.ui.player.component.PlayerBottomBar]
 * component, so it still has its own inline bottom controls. Future refactor:
 * replace the inline slider + button row with [PlayerBottomBar].
 */
@Composable
fun PhoneLandscape(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    isFavorite: Boolean,
    lyricState: LyricState,
    onBackClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    imageLoader: ImageLoader
) {
    val backgroundColor = Color(0xFF0D0D1A)
    val accentColor = AppColors.Accent

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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧：黑胶唱片 + 控制面板（占 2/5）
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val coverSize = minOf(maxWidth * 0.9f, maxHeight * 0.9f)

                    // 1. 大黑胶唱片铺满（旋转动画）
                    VinylRecord(
                        albumArtUrl = currentSong.coverUrl ?: currentSong.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier.size(coverSize),
                        imageLoader = imageLoader
                    )

                    // 2. 底部半透明控制面板（进度条 + 按钮）
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 进度条
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = if (playbackState.duration > 0) {
                                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                                } else 0f,
                                onValueChange = { fraction ->
                                    onSeek((fraction * playbackState.duration).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(playbackState.position),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatDuration(playbackState.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 播放控制按钮
                        val screenWidth = LocalConfiguration.current.screenWidthDp
                        val btnSize = (24 + (screenWidth - 360) * 0.03f).coerceIn(22f, 32f).dp

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onTogglePlayMode) {
                                PlayModeIcon(playMode = playMode, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(btnSize))
                            }

                            SkipPreviousIcon(
                                onClick = onPrevious,
                                tint = Color.White,
                                modifier = Modifier.size(btnSize)
                            )

                            FilledIconButton(
                                onClick = onTogglePlay,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = accentColor
                                ),
                                modifier = Modifier.size(btnSize * 1.5f)
                            ) {
                                PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, modifier = Modifier.size(btnSize))
                            }

                            SkipNextIcon(
                                onClick = onNext,
                                tint = Color.White,
                                modifier = Modifier.size(btnSize)
                            )

                            IconButton(onClick = onToggleFavorite) {
                                FavoriteIcon(
                                    isFavorite = isFavorite,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    contentDescription = "收藏",
                                    modifier = Modifier.size(btnSize)
                                )
                            }
                        }
                    }

                    // 左上角大字歌名（与歌词当前行样式一致）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentSong.name,
                            color = Color.Black.copy(alpha = 0.8f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                        )
                        Text(
                            text = currentSong.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ==================== 右侧：滚动歌词（占 3/5）====================
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                ) {
                    when (val state = lyricState) {
                        is LyricState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                        is LyricState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "歌词加载失败",
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is LyricState.Success -> {
                            val lyrics = state.lyrics
                            if (lyrics.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "纯音乐，欣赏中...",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                val currentIndex = lyrics.indexOfLast { it.timestamp <= playbackState.position }
                                    .coerceAtLeast(0)

                                val listState = rememberLazyListState()
                                val density = LocalDensity.current
                                val itemSpacing = with(density) { 12.dp.toPx() }
                                val itemHeight = with(density) { 32.dp.toPx() }
                                val totalItemHeight = itemHeight + itemSpacing

                                LaunchedEffect(currentIndex) {
                                    listState.animateScrollToItem(
                                        index = maxOf(0, currentIndex - 1),
                                        scrollOffset = -((listState.layoutInfo.viewportSize.height / 2) - (2 * totalItemHeight + itemHeight / 2)).toInt()
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = listState,
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                                ) {
                                    itemsIndexed(lyrics) { index, line ->
                                        val isCurrent = index == currentIndex
                                        val isNext = index == currentIndex + 1
                                        val alpha = if (isCurrent) 1f else 0.5f
                                        val fontSize = if (isCurrent) 24.sp else if (isNext) 20.sp else 14.sp

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Box {
                                                if (isCurrent) {
                                                    Text(
                                                        text = line.text.ifEmpty { " " },
                                                        color = Color.Black.copy(alpha = 0.8f),
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                                                    )
                                                }
                                                Text(
                                                    text = line.text.ifEmpty { " " },
                                                    color = Color.White.copy(alpha = alpha),
                                                    fontSize = fontSize,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(start = 0.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is LyricState.Idle -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "歌词加载中...",
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
