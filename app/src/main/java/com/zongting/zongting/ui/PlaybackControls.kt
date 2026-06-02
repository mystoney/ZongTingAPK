package com.zongting.zongting.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BedtimeOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zongting.zongting.ui.theme.AppColors

// ===== 播放 / 暂停 =====

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

// ===== 上一首 =====

/**
 * 上一首按钮图标。
 *
 * 用法示例：
 *   SkipPreviousIcon(onClick = onPrevious, modifier = Modifier.size(24.dp))
 *   SkipPreviousIcon(onClick = onPrevious, tint = Color.White, modifier = Modifier.size(24.dp))
 */
@Composable
fun SkipPreviousIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "上一首"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.SkipPrevious,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

// ===== 下一首 =====

/**
 * 下一首按钮图标。
 *
 * 用法示例：
 *   SkipNextIcon(onClick = onNext, modifier = Modifier.size(24.dp))
 *   SkipNextIcon(onClick = onNext, tint = Color.White, modifier = Modifier.size(24.dp))
 */
@Composable
fun SkipNextIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "下一首"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

// ===== 收藏 =====

/**
 * 收藏图标：选中时显示绿色实心心形 + 白色空心叠加，未选中时只显示空心。
 * 统一在各处使用，保持收藏动画和样式一致。
 *
 * 用法示例：
 *   FavoriteIcon(isFavorite = isFavorite, tint = Color.White)
 *   FavoriteIcon(isFavorite = isFavorite, tint = Color.White, modifier = Modifier.size(24.dp))
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

// ===== 播放模式 =====

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

// ===== 定时 =====

/**
 * 定时图标：开启时显示 BedtimeOff（紫色），关闭时显示 Bedtime。
 * 状态变化时图标和颜色同步切换。
 *
 * 用法示例：
 *   TimerIcon(isActive = isTimerActive, onClick = onSleepTimerClick)
 *   TimerIcon(isActive = isTimerActive, onClick = onSleepTimerClick, tint = Color.White)
 */
@Composable
fun TimerIcon(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "定时关闭"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isActive) Icons.Filled.BedtimeOff else Icons.Filled.Bedtime,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

// ===== 播放列表 =====

/**
 * 播放列表图标。
 *
 * 用法示例：
 *   PlaylistIcon(onClick = { onShowPlaylist(true) })
 *   PlaylistIcon(onClick = { onShowPlaylist(true) }, tint = Color.White)
 */
@Composable
fun PlaylistIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "播放列表"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

// ===== 添加到播放列表 =====

/**
 * 添加到播放列表图标。
 *
 * 用法示例：
 *   AddToPlaylistIcon(onClick = onToggleSavePlaylist)
 *   AddToPlaylistIcon(onClick = onToggleSavePlaylist, tint = Color.White)
 */
@Composable
fun AddToPlaylistIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "保存到歌单"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.PlaylistAdd,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

// ===== 设为铃声 =====

/**
 * 设为铃声图标。
 *
 * 用法示例：
 *   RingtoneIcon(onClick = onRingtoneCutterClick)
 *   RingtoneIcon(onClick = onRingtoneCutterClick, tint = Color.White)
 */
@Composable
fun RingtoneIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    contentDescription: String = "设为铃声"
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
