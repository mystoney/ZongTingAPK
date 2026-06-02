package com.zongting.zongting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.FavoriteIcon
import com.zongting.zongting.ui.PlayModeIcon
import com.zongting.zongting.ui.PlayPauseIcon
import com.zongting.zongting.ui.theme.AppColors

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

    // 按钮区域：动态计算图标大小，根据屏幕宽度自适应
    // 5个按钮（喜欢/播放模式/上一首/播放暂停/下一首）在剩余空间内均分
    // 竖屏手机（宽度约 360dp）：图标约 24dp，按钮触控区 40dp
    // 横屏/PAD（宽度更大）：图标约 32dp，按钮触控区 48dp
    val iconSize = 24.dp
    val buttonTouchTarget = 40.dp

    val horizontalPadding = if (isExpanded) 16.dp else 8.dp
    val verticalPadding = if (isExpanded) 20.dp else 8.dp
    val coverSize = if (isExpanded) 64.dp else 48.dp
    val textStyleTitle = if (isExpanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium
    val textStyleArtist = if (isExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall

    // 主播放按钮稍大
    val playIconSize = 28.dp
    val playTouchTarget = 44.dp

    // 乐观更新：本地记住当前图标状态，点击立即切换，异步同步真实 playMode
    var localPlayMode by remember { mutableIntStateOf(playMode) }
    LaunchedEffect(playMode) { localPlayMode = playMode }

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

            // 按钮区域：使用 Row + weight 均分，让图标自适应屏幕宽度
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 喜欢按钮
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(buttonTouchTarget)
                ) {
                    FavoriteIcon(
                        isFavorite = isFavorite,
                        tint = if (isFavorite) AppColors.FavoriteActive else AppColors.FavoriteInactive,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // 播放模式按钮
                IconButton(
                    onClick = {
                        localPlayMode = (localPlayMode + 1) % 3
                        onTogglePlayMode()
                    },
                    modifier = Modifier.size(buttonTouchTarget)
                ) {
                    PlayModeIcon(playMode = localPlayMode, tint = Color.White, modifier = Modifier.size(iconSize))
                }

                // 上一首
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(buttonTouchTarget)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // 播放/暂停按钮（稍大）
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(playTouchTarget)
                ) {
                    PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, modifier = Modifier.size(playIconSize))
                }

                // 下一首
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(buttonTouchTarget)
                ) {
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
}
