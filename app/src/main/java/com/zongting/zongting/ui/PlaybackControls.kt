package com.zongting.zongting.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.zongting.zongting.ui.theme.AppColors

/**
 * 收藏图标：选中时显示绿色实心心形 + 白色空心叠加，未选中时只显示空心。
 * 统一在各处使用，保持收藏动画和样式一致。
 */
@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // 下层：绿色实心（选中时显示）
    if (isFavorite) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = contentDescription,
            tint = AppColors.FavoriteActive,
            modifier = modifier
        )
    }
    // 上层：空心（始终显示，提供边框轮廓）
    Icon(
        imageVector = Icons.Outlined.FavoriteBorder,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

/**
 * 播放模式图标：根据 playMode 显示对应图标。
 * 0 = 列表循环(Repeat), 1 = 单曲循环(RepeatOne), 2 = 随机播放(Shuffle)
 *
 * 用法示例：
 *   PlayModeIcon(playMode = playMode, modifier = Modifier.size(24.dp))
 *   PlayModeIcon(playMode = localPlayMode, tint = Color.White, modifier = Modifier.size(24.dp))
 */
@Composable
fun PlayModeIcon(
    playMode: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    val (icon, desc) = when (playMode) {
        1 -> Icons.Filled.RepeatOne to "单曲循环"
        2 -> Icons.Filled.Shuffle to "随机播放"
        else -> Icons.Filled.Repeat to "列表循环"
    }
    Icon(
        imageVector = icon,
        contentDescription = contentDescription ?: desc,
        modifier = modifier,
        tint = tint
    )
}

/**
 * 播放/暂停图标：根据 isPlaying 状态切换。
 *
 * 用法示例：
 *   PlayPauseIcon(isPlaying = isPlaying, modifier = Modifier.size(32.dp))
 *   PlayPauseIcon(isPlaying = isPlaying, tint = Color.White, modifier = Modifier.size(32.dp))
 */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    Icon(
        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = contentDescription ?: if (isPlaying) "暂停" else "播放",
        modifier = modifier,
        tint = tint
    )
}
