package com.zongting.zongting.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.ringtone.RingtoneCutterViewModel
import com.zongting.zongting.ringtone.AudioRingtoneHelper
import com.zongting.zongting.ui.LyricLine
import com.zongting.zongting.ui.LyricState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RingtoneCutterScreen(
    onBackClick: () -> Unit,
    viewModel: RingtoneCutterViewModel,
    lyrics: List<LyricLine> = emptyList()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showRingtoneTypeDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.stopPreview()
        onBackClick()
    }

    // 权限检查弹窗
    if (!state.hasWriteSettings && !state.isProcessing) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("需要权限") },
            text = { Text("设置铃声需要「修改系统设置」权限，请在设置中授权后重试。") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
                    ).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }) {
                    Text("去授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.refreshPermissionState() }) {
                    Text("已授权")
                }
            }
        )
    }

    // 铃声类型选择弹窗
    if (showRingtoneTypeDialog) {
        RingtoneTypeDialog(
            onDismiss = { showRingtoneTypeDialog = false },
            onSelect = { type ->
                showRingtoneTypeDialog = false
                viewModel.setAsRingtone(type)
            }
        )
    }

    // 结果提示Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.resultMessage, state.resultType) {
        if (state.resultMessage.isNotEmpty()) {
            val type = when (state.resultType) {
                com.zongting.zongting.ringtone.ResultType.SUCCESS -> SnackbarResult.ActionPerformed
                com.zongting.zongting.ringtone.ResultType.ERROR -> SnackbarResult.Dismissed
                com.zongting.zongting.ringtone.ResultType.NONE -> SnackbarResult.Dismissed
            }
            snackbarHostState.showSnackbar(
                message = state.resultMessage,
                duration = SnackbarDuration.Short
            )
            if (state.resultType != com.zongting.zongting.ringtone.ResultType.SUCCESS) {
                viewModel.clearResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设为铃声") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopPreview()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ===== 歌曲信息 =====
            state.song?.let { song ->
                SongInfoHeader(song = song)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 截取时间轴 =====
            TimelineEditor(
                durationMs = state.durationMs,
                startMs = state.startMs,
                endMs = state.endMs,
                onStartChange = { viewModel.updateStart(it) },
                onEndChange = { viewModel.updateEnd(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 截取时长信息 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "起点 ${formatTime(state.startMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "时长 ${formatDurationLabel(state.clipDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "终点 ${formatTime(state.endMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 预览播放按钮 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.previewClip() },
                    enabled = state.isValid && !state.isProcessing
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "预览",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isPlaying) "预览中..." else "点击预览截取片段",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 歌词时间轴（截取范围内歌词预览）=====
            if (lyrics.isNotEmpty()) {
                LyricTimelineView(
                    lyrics = lyrics,
                    startMs = state.startMs,
                    endMs = state.endMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // ===== 底部按钮区 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 导出按钮
                OutlinedButton(
                    onClick = { viewModel.exportAudio() },
                    enabled = state.isValid && !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出音频")
                }

                // 设为铃声按钮
                Button(
                    onClick = { showRingtoneTypeDialog = true },
                    enabled = state.isValid && !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("设为铃声")
                }
            }

            // 处理中提示
            if (state.isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = state.processingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ===== 歌曲信息头部 =====
@Composable
private fun SongInfoHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pic,
            contentDescription = "专辑封面",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
    }
}

// ===== 时间轴编辑器 =====
@Composable
private fun TimelineEditor(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    fun msToPx(ms: Long): Float {
        if (trackWidthPx <= 0f || durationMs <= 0L) return 0f
        return (ms.toFloat() / durationMs) * trackWidthPx
    }

    fun pxToMs(px: Float): Long {
        if (trackWidthPx <= 0f || durationMs <= 0L) return 0L
        return ((px / trackWidthPx) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    // 拖柄位置用 mutableFloatStateOf，跟踪拖拽过程中的实时位置（避免闭包捕获旧值）
    var startDragPx by remember { mutableFloatStateOf(0f) }
    var endDragPx by remember { mutableFloatStateOf(0f) }

    val handleTouchDp = 20.dp        // 触摸区域（较大方便拖动）
    val handleVisualDp = 12.dp       // 视觉圆圈大小
    val trackHeight = 6.dp
    val totalHeight = handleTouchDp + 24.dp

    BoxWithConstraints(
        modifier = modifier.height(totalHeight)
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        LaunchedEffect(maxW) { trackWidthPx = maxW }
        LaunchedEffect(startMs) { startDragPx = msToPx(startMs) }
        LaunchedEffect(endMs) { endDragPx = msToPx(endMs) }

        // ===== 背景轨道 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(trackHeight / 2)
                )
        )

        // ===== 已选中的高亮区域 =====
        Box(
            modifier = Modifier
                .offset(x = with(density) { msToPx(startMs).toDp() })
                .width(with(density) { (msToPx(endMs) - msToPx(startMs)).coerceAtLeast(0f).toDp() })
                .height(trackHeight)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(trackHeight / 2)
                )
        )

        // ===== 拖柄辅助函数（使用 lambda 避免闭包捕获问题）=====
        fun Modifier.draggableHandle(
            getPositionPx: () -> Float,
            onPositionChange: (Long) -> Unit,
            clampPx: (candidate: Float) -> Float
        ): Modifier = this
            .offset(x = with(density) { getPositionPx().toDp() - handleTouchDp / 2 })
            .size(handleTouchDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (trackWidthPx <= 0f || durationMs <= 0L) return@detectDragGestures
                        val basePx = getPositionPx()
                        val newPx = clampPx(basePx + dragAmount.x)
                        val newMs = pxToMs(newPx)
                        onPositionChange(newMs)
                    }
                )
            }

        // ===== 开始拖柄 =====
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(handleTouchDp)
                .padding(horizontal = (handleTouchDp - handleVisualDp) / 2)
                .draggableHandle(
                    getPositionPx = { startDragPx },
                    onPositionChange = onStartChange,
                    clampPx = { candidate ->
                        msToPx((endMs - 1000L).coerceAtLeast(0L)).let { maxPx ->
                            candidate.coerceIn(0f, maxPx)
                        }
                    }
                )
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .shadow(4.dp, CircleShape)
        )

        // ===== 结束拖柄 =====
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(handleTouchDp)
                .padding(horizontal = (handleTouchDp - handleVisualDp) / 2)
                .draggableHandle(
                    getPositionPx = { endDragPx },
                    onPositionChange = onEndChange,
                    clampPx = { candidate ->
                        msToPx(startMs + 1000L).let { minPx ->
                            msToPx(minOf(durationMs, startMs + 60_000L)).let { maxPx ->
                                candidate.coerceIn(minPx, maxPx)
                            }
                        }
                    }
                )
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .shadow(4.dp, CircleShape)
        )

        // ===== 起始时间标签 =====
        Text(
            text = formatTime(startMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset(x = with(density) { msToPx(startMs).toDp() - handleTouchDp / 2 })
                .align(Alignment.TopStart)
        )

        // ===== 结束时间标签 =====
        Text(
            text = formatTime(endMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset(x = with(density) { msToPx(endMs).toDp() - handleTouchDp / 2 })
                .align(Alignment.TopEnd)
        )
    }
}
// ===== 歌词时间轴视图（拖动时显示对应歌词）=====
@Composable
fun LyricTimelineView(
    lyrics: List<LyricLine>,
    startMs: Long,
    endMs: Long,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 找到截取范围内的歌词行
    val visibleLines = lyrics.filter { it.timestamp in startMs..endMs }
    val currentLine = lyrics.findLast { it.timestamp <= (startMs + endMs) / 2 }

    LaunchedEffect(currentLine, lyrics) {
        currentLine?.let { line ->
            val idx = lyrics.indexOf(line)
            if (idx >= 0) {
                lazyListState.animateScrollToItem(idx.coerceAtLeast(0))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        Color.Transparent
                    )
                )
            )
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 32.dp)
        ) {
            itemsIndexed(lyrics) { index, lyricLine ->
                val isInRange = lyricLine.timestamp in startMs..endMs
                val isNearStart = kotlin.math.abs(lyricLine.timestamp - startMs) < 500
                val isNearEnd = kotlin.math.abs(lyricLine.timestamp - endMs) < 500

                val textColor = when {
                    isNearStart || isNearEnd -> primaryColor
                    isInRange -> onSurface
                    else -> onSurfaceVariant
                }
                val fontWeight = when {
                    isNearStart || isNearEnd -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                val fontSize = if (isNearStart || isNearEnd) 18.sp else 14.sp

                Text(
                    text = lyricLine.text.ifEmpty { "♪" },
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

// ===== 铃声类型选择弹窗 =====
@Composable
private fun RingtoneTypeDialog(
    onDismiss: () -> Unit,
    onSelect: (AudioRingtoneHelper.RingtoneType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择铃声类型") },
        text = {
            Column {
                Text(
                    text = "选择将截取的音频设为哪种系统声音",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                listOf(
                    Triple(AudioRingtoneHelper.RingtoneType.RINGTONE, "来电铃声", "设为默认来电铃声"),
                    Triple(AudioRingtoneHelper.RingtoneType.NOTIFICATION, "通知声", "设为通知提示音"),
                    Triple(AudioRingtoneHelper.RingtoneType.ALARM, "闹钟", "设为闹钟铃声")
                ).forEach { (type, title, desc) ->
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(desc) },
                        leadingContent = {
                            Icon(
                                imageVector = when (type) {
                                    AudioRingtoneHelper.RingtoneType.RINGTONE -> Icons.Default.Phone
                                    AudioRingtoneHelper.RingtoneType.NOTIFICATION -> Icons.Default.Notifications
                                    AudioRingtoneHelper.RingtoneType.ALARM -> Icons.Default.Alarm
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onSelect(type) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ===== 工具函数 =====
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatDurationLabel(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "${seconds}秒"
}
