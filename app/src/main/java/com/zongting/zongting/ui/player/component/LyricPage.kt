package com.zongting.zongting.ui.player.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.ui.theme.AppColors
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.PlaybackState
import com.zongting.zongting.ui.player.util.formatDuration
import kotlinx.coroutines.channels.Channel

/**
 * The "lyrics" tab of the PlayerScreen's HorizontalPager.
 *
 * Renders one of four states from [LyricState] (Idle / Loading / Success
 * / Error) and overlays a progress slider that switches between a
 * horizontal slider (landscape) and a vertical drag-handle (portrait)
 * depending on orientation. The current line is auto-scrolled to the
 * 40%-from-top position; tapping a line seeks to its timestamp.
 *
 * Originally a `private fun` in PlayerScreen.kt.
 */
@Composable
fun LyricPage(
    currentSong: Song?,
    lyricState: LyricState,
    playbackState: PlaybackState,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDrag: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    isPad: Boolean = false  // true = pad (Compact 以外：Medium 平板竖屏 / Expanded 平板横屏)
) {
    val lazyListState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (lyricState) {
                is LyricState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在加载歌词...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is LyricState.Success -> {
                    val lines = lyricState.lyrics
                    val position = playbackState.position

                    // 直接计算 currentLineIndex，不套 derivedStateOf
                    // derivedStateOf 会阻断 recompose，导致 LaunchedEffect 无法正常响应 currentLineIndex 变化
                    val currentLineIndex = lines.indices.lastOrNull { lines[it].timestamp <= position } ?: 0

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        // 字号：参考网易云，当前行 22sp(+2)，非当前行 14sp，下一行 20sp(+3)
                        val currentLineFontSize = if (isLandscape) {
                            if (isPad) 38.sp else 30.sp
                        } else if (isPad) 38.sp else 22.sp
                        val otherLineFontSize = if (isLandscape) 22.sp else if (isPad) 22.sp else 14.sp
                        val nextLineFontSize = if (isLandscape) {
                            if (isPad) 35.sp else 27.sp
                        } else if (isPad) 35.sp else 20.sp
                        val horizontalPadding = if (isPad) 48.dp else if (isLandscape) 40.dp else 24.dp
                        val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                        val boxHeightDp = maxHeight
                        // 滚动时当前行对齐屏幕 40% 处
                        val scrollToPosition = boxHeightPx * 2 / 5f
                        val lineSpacing = if (isLandscape || isPad) 16.dp else 12.dp
                        val centerPadding = boxHeightDp / 2

                        // 渐变叠加层已移除
                        var lastScrolledIndex by remember { mutableIntStateOf(-1) }
                        LaunchedEffect(currentLineIndex, lazyListState) {
                            if (lines.isNotEmpty() && currentLineIndex in lines.indices) {
                                if (lastScrolledIndex == currentLineIndex) return@LaunchedEffect
                                // 居中位置 = scrollOffset=0，当前行在屏幕中心
                                // 目标 40%：需要内容向下滚，即 scrollOffset 为正值
                                val scrollDownOffset = (boxHeightPx * 0.1f).toInt()
                                lazyListState.scrollToItem(
                                    index = currentLineIndex,
                                    scrollOffset = scrollDownOffset
                                )
                                lastScrolledIndex = currentLineIndex
                            }
                        }

                        // 追踪用户是否在手动滚动列表，驱动 isUserScrolling 状态
                        LaunchedEffect(lazyListState) {
                            snapshotFlow { lazyListState.isScrollInProgress }
                                .collect { scrolling ->
                                    if (scrolling) isUserScrolling = true
                                }
                        }

                        // 顶部和底部边缘渐变遮罩保留，3行高渐变背景已移除
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizontalPadding),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(lineSpacing),
                                contentPadding = PaddingValues(vertical = centerPadding)
                            ) {
                                itemsIndexed(
                                    items = lines,
                                    key = { idx, _ -> idx }
                                ) { idx, lyricLine ->
                                    val isCurrent = idx == currentLineIndex
                                    val isNext = idx == currentLineIndex + 1
                                    // 每个 item 用独立 label，避免所有歌词共享同一个动画状态导致高亮混乱
                                    val alpha by animateFloatAsState(
                                        targetValue = if (isCurrent) 1f else 0.45f,
                                        animationSpec = tween(300),
                                        label = "lyricAlpha_$idx"
                                    )
                                    val textColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                    // 不再用固定高度 Box，让文字自然占据高度（参考网易云）
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        // 阴影层（黑色描边）
                                        if (isCurrent) {
                                            Text(
                                                text = lyricLine.text,
                                                color = Color.Black.copy(alpha = 0.8f),
                                                fontSize = currentLineFontSize,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(x = 1.5.dp, y = 1.5.dp)
                                                    .graphicsLayer {
                                                        scaleX = if (isCurrent) 1.05f else 1f
                                                        scaleY = if (isCurrent) 1.05f else 1f
                                                    }
                                            )
                                        }
                                        // 主文字层
                                        Text(
                                            text = lyricLine.text,
                                            fontSize = if (isCurrent) currentLineFontSize else if (isNext) nextLineFontSize else otherLineFontSize,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = if (isCurrent) 1.05f else 1f
                                                    scaleY = if (isCurrent) 1.05f else 1f
                                                }
                                                .clickable { onSeek(lyricLine.timestamp) }
                                        )
                                    }
                                }
                            }

                            // 顶部渐变遮罩（边缘渐隐）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(LocalDensity.current) { (boxHeightPx / 2).toDp() })
                                    .align(Alignment.TopCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            // 底部渐变遮罩
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(with(LocalDensity.current) { (boxHeightPx / 2).toDp() })
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )

                            // 5秒无操作后自动回正逻辑已移除
                            // 主滚动逻辑（scrollToItem）已在 LaunchedEffect 中保证居中，无需额外补偿
                        }
                    }
                }
                is LyricState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = lyricState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is LyricState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "正在加载歌词...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // 进度条（始终显示）
            val config = LocalConfiguration.current
            val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                // 横屏：底部横向 Slider
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column {
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
                    }
                }
            } else {
                // 竖屏：右侧纵向进度条（可拖动）+ 时间标签
                var isDragging by remember { mutableStateOf(false) }
                var dragProgress by remember { mutableFloatStateOf(0f) }
                val progress = if (playbackState.duration > 0) {
                    playbackState.position.toFloat() / playbackState.duration.toFloat()
                } else 0f
                // 用 Channel 把 pointerInput 协程里的绝对 Y 坐标传进组合层级
                val dragChannel = remember { Channel<Float>(Channel.RENDEZVOUS) }
                LaunchedEffect(dragChannel) {
                    for (absY in dragChannel) {
                        dragProgress = absY.coerceIn(0f, 1f)
                    }
                }
                val displayProgress = if (isDragging) dragProgress else progress
                val density = LocalDensity.current

                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .fillMaxHeight(0.95f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragProgress = progress
                                },
                                onDragEnd = {
                                    onSeek((dragProgress * playbackState.duration).toLong())
                                    isDragging = false
                                },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, _ ->
                                    // 用手指的绝对 Y 位置（相对于进度条顶部）算进度
                                    val absProgress = change.position.y / size.height
                                    dragChannel.trySend(absProgress.coerceIn(0f, 1f))
                                }
                            )
                        }
                ) {
                    // 使用 density.density 和 Dp.value 做像素换算，避免 toPx/toDp 扩展函数
                    val densityVal = density.density
                    val barHeightPx = maxHeight.value * densityVal
                    val thumbDiaPx = 18f * densityVal
                    // 圆形 Y：progress=0→顶部，progress=1→底部（初始在顶部，向下移动）
                    val thumbY = (barHeightPx * displayProgress - thumbDiaPx / 2).coerceIn(0f, barHeightPx - thumbDiaPx)
                    // 红色填充高度（从顶部往下）
                    val fillHeightPx = barHeightPx * displayProgress

                    // 轨道背景（灰色，居中）
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 已播放进度（红色，居中）
                    Box(
                        modifier = Modifier
                            .height((fillHeightPx / densityVal).dp)
                            .width(6.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                AppColors.Accent,
                                RoundedCornerShape(3.dp)
                            )
                    )
                    // 圆形指示器（居中）
                    Box(
                        modifier = Modifier
                            .offset(y = (thumbY / densityVal).dp)
                            .size(18.dp)
                            .align(Alignment.TopCenter)
                            .background(AppColors.Accent, CircleShape)
                    )
                }
                // 时间标签
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 30.dp)
                        .fillMaxHeight(0.95f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((if (isDragging) dragProgress * playbackState.duration else playbackState.position).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Accent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = formatDuration(playbackState.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
