package com.zongting.zongting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playMode: Int = 0,
    onTogglePlayMode: () -> Unit = {},
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass? = null
) {
    val isExpanded = windowSizeClass?.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded
    // 横屏 Expanded 高度为普通的 2.5 倍（普通约 64dp × 2.5 ≈ 160dp）
    val coverSize = if (isExpanded) 120.dp else 48.dp
    val horizontalPadding = if (isExpanded) 16.dp else 8.dp
    val verticalPadding = if (isExpanded) 20.dp else 8.dp
    val iconSize = if (isExpanded) 48.dp else 28.dp
    val textStyleTitle = if (isExpanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium
    val textStyleArtist = if (isExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall

    // 乐观更新：本地记住当前图标状态，点击立即切换，异步同步真实 playMode
    var localPlayMode by remember { mutableIntStateOf(playMode) }
    LaunchedEffect(playMode) { localPlayMode = playMode }

    val playModeIcon = when (localPlayMode) {
        1 -> Icons.Filled.RepeatOne
        2 -> Icons.Filled.Shuffle
        else -> Icons.Filled.Repeat
    }
    val playModeDesc = when (localPlayMode) {
        0 -> "顺序播放"
        1 -> "单曲循环"
        else -> "随机播放"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xB30B1E10),
                        0.5f to Color(0xF20B1E10),
                        1f to Color(0xB30B1E10)
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = song.coverUrl ?: song.pic120,
                contentDescription = null,
                modifier = Modifier
                    .size(coverSize)
                    .clip(RoundedCornerShape(if (isExpanded) 8.dp else 4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(if (isExpanded) 20.dp else 12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.name,
                    style = textStyleTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = textStyleArtist,
                    color = Color(0xFFB9F6CA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 喜欢按钮
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(iconSize * 2f)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                    tint = if (isFavorite) Color(0xFFFF5252) else Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 播放模式按钮
            IconButton(
                onClick = {
                    localPlayMode = (localPlayMode + 1) % 3
                    onTogglePlayMode()
                },
                modifier = Modifier.size(iconSize * 2f)
            ) {
                Icon(
                    imageVector = playModeIcon,
                    contentDescription = playModeDesc,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 上一首
            IconButton(onClick = onPrevious, modifier = Modifier.size(iconSize * 2f)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 播放/暂停按钮
            IconButton(onClick = onPlayPause, modifier = Modifier.size(iconSize * 2f)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 下一首
            IconButton(onClick = onNext, modifier = Modifier.size(iconSize * 2f)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
