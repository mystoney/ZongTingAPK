package com.zongting.zongting.ui.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.ui.player.util.formatDuration

/**
 * The "album cover" tab of the PlayerScreen's HorizontalPager.
 *
 * Switches between a two-column landscape layout (lyrics on the left,
 * vinyl on the right) and a single-column portrait layout (vinyl on
 * top, song metadata and progress slider below). The branching is
 * driven by [LocalConfiguration.orientation].
 *
 * Originally a `private fun` in PlayerScreen.kt.
 */
@Composable
fun AlbumCoverPage(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    playMode: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onDrag: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    imageLoader: ImageLoader
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // ===== 横屏布局：左边文字信息，右边唱片 =====
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：歌曲信息（竖向排列，字号加大，底部对齐）
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentSong != null) {
                    val song = currentSong
                    // 上部：歌曲信息（字号加大）
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 歌名阴影
                        Text(
                            text = song.name,
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
                        )
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.displayMedium,
                            fontSize = 34.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // 歌手阴影
                        Text(
                            text = song.artist,
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 专辑名阴影
                        Text(
                            text = song.album,
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
                        )
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // 下部：进度条和时间（底部对齐）
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // 进度条
                        var isDragging by remember { mutableStateOf(false) }
                        var dragProgress by remember { mutableFloatStateOf(0f) }

                        Slider(
                            value = if (isDragging) dragProgress else {
                                if (playbackState.duration > 0) {
                                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                                } else 0f
                            },
                            onValueChange = { newProgress ->
                                if (!isDragging) isDragging = true
                                dragProgress = newProgress
                                onDrag((newProgress * playbackState.duration).toLong())
                            },
                            onValueChangeFinished = {
                                onSeek((dragProgress * playbackState.duration).toLong())
                                isDragging = false
                            },
                            valueRange = 0f..1f,
                        )

                        // 时间显示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(playbackState.duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 右侧：唱片
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                currentSong?.let { song ->
                    VinylRecord(
                        albumArtUrl = song.coverUrl ?: song.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f),
                        imageLoader = imageLoader
                    )
                }
            }
        }
    } else {
        // ===== 竖屏布局：上下排列，唱片居中 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (currentSong != null) {
                val song = currentSong

                // 专辑封面 - 唱片样式
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    VinylRecord(
                        albumArtUrl = song.coverUrl ?: song.pic,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(1f),
                        imageLoader = imageLoader
                    )
                }

                // 歌曲信息
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 进度条
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }

                Slider(
                    value = if (isDragging) dragProgress else {
                        if (playbackState.duration > 0) {
                            playbackState.position.toFloat() / playbackState.duration.toFloat()
                        } else 0f
                    },
                    onValueChange = { newProgress ->
                        if (!isDragging) isDragging = true
                        dragProgress = newProgress
                        onDrag((newProgress * playbackState.duration).toLong())
                    },
                    onValueChangeFinished = {
                        onSeek((dragProgress * playbackState.duration).toLong())
                        isDragging = false
                    },
                    valueRange = 0f..1f,
                )

                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(playbackState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
