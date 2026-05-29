package com.zongting.zongting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val iconSize = if (isExpanded) 36.dp else 24.dp
    val textStyleTitle = if (isExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    val textStyleArtist = if (isExpanded) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xE60B1E10),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = song.pic120,
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
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(iconSize * 1.5f)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消喜欢" else "我喜欢",
                    tint = if (isFavorite) Color(0xFFFF5252) else Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 上一首
            IconButton(onClick = onPrevious, modifier = Modifier.size(iconSize * 1.5f)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 播放/暂停按钮
            IconButton(onClick = onPlayPause, modifier = Modifier.size(iconSize * 1.5f)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            // 下一首
            IconButton(onClick = onNext, modifier = Modifier.size(iconSize * 1.5f)) {
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
