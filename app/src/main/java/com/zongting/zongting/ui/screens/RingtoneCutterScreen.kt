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
                playbackPositionMs = state.playbackPositionMs,
                onStartChange = { viewModel.updateStart(it) },
                onEndChange = { viewModel.updateEnd(it) },
                onDragEnd = { viewModel.previewClip() },
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

            // ===== 预览播放按钮（紧凑）=====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (state.isPlaying) "预览中..." else "点击预览截取片段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 歌词区（当前行红色居中）=====
            if (lyrics.isNotEmpty()) {
                RingtoneLyricView(
                    lyrics = lyrics,
                    playbackPositionMs = state.playbackPositionMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

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

// ===== 歌曲信息头部（紧凑版）=====
@Composable
private fun SongInfoHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pic,
            contentDescription = "专辑封面",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
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
    playbackPositionMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val CLIP_DURATION_MS = 50_000L

    // 立即获取轨道宽度（同步方式，避免 LaunchedEffect 延迟）
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    // 纯本地拖动状态，拖完才上报 ViewModel
    var localStartMs by remember(startMs) { mutableLongStateOf(startMs) }
    var isDragging by remember { mutableStateOf(false) }

    // 同步外部 startMs 变化到本地（仅当非拖动时）
    LaunchedEffect(startMs) {
        if (!isDragging) {
            localStartMs = startMs
        }
    }

    fun msToPx(ms: Long): Float {
        if (trackWidthPx <= 0f || durationMs <= 0L) return 0f
        return (ms.toFloat() / durationMs) * trackWidthPx
    }

    fun pxToMs(px: Float): Long {
        if (trackWidthPx <= 0f || durationMs <= 0L) return 0L
        return ((px / trackWidthPx) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    BoxWithConstraints(
        modifier = modifier
            .height(80.dp)
            .onSizeChanged { size ->
                // 同步获取宽度，立即可用
                if (size.width > 0) trackWidthPx = size.width.toFloat()
            }
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        LaunchedEffect(maxW) { if (trackWidthPx == 0f) trackWidthPx = maxW }

        val maxStartMs = (durationMs - CLIP_DURATION_MS).coerceAtLeast(0L)

        // ===== 背景轨道 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(3.dp)
                )
        )

        // ===== 播放进度覆盖层 =====
        if (trackWidthPx > 0f && durationMs > 0L && playbackPositionMs > 0L) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { msToPx(playbackPositionMs).toDp() })
                    .width(2.dp)
                    .height(6.dp)
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.tertiary,
                        RoundedCornerShape(1.dp)
                    )
            )
        }

        // ===== 50秒选中高亮块 =====
        val blockWidthPx = if (trackWidthPx > 0f && durationMs > 0L)
            (CLIP_DURATION_MS.toFloat() / durationMs) * trackWidthPx
        else 0f
        val blockWidthDp = with(density) { maxOf(blockWidthPx, 72f).toDp() }
        val blockOffsetX = with(density) { msToPx(localStartMs).toDp() }

        Box(
            modifier = Modifier
                .offset(x = blockOffsetX)
                .width(blockWidthDp)
                .height(32.dp)
                .align(Alignment.CenterStart) // 只负责垂直居中，水平用 offset 控制
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (trackWidthPx <= 0f || durationMs <= 0L) return@detectDragGestures
                            val deltaMs = pxToMs(dragAmount.x)
                            val newStart = (localStartMs + deltaMs).coerceIn(0L, maxStartMs)
                            localStartMs = newStart
                        },
                        onDragEnd = {
                            if (localStartMs != startMs) {
                                onStartChange(localStartMs)
                                onEndChange((localStartMs + CLIP_DURATION_MS).coerceAtMost(durationMs))
                            }
                            onDragEnd()
                            isDragging = false
                        },
                        onDragCancel = {
                            localStartMs = startMs
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 拖动手柄指示（三条横线）
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                repeat(3) { _ ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .size(width = 32.dp, height = 2.dp)
                            .background(
                                Color.White.copy(alpha = 0.7f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }

        // ===== 固定时间标签：轨道左端 =====
        Text(
            text = formatTime(0L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = 0.dp)
                .align(Alignment.BottomStart)
        )

        // ===== 固定时间标签：轨道右端 =====
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
        )

        // ===== 选中块起点时间 =====
        Text(
            text = formatTime(localStartMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset(x = with(density) { (msToPx(localStartMs) - 2f).toDp() })
                .align(Alignment.TopStart)
        )
    }
}

// ===== 歌词视图（当前行红色居中）=====
@Composable
private fun RingtoneLyricView(
    lyrics: List<LyricLine>,
    playbackPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val RED = Color(0xFFFF3B30)

    // 实时计算当前行（基于播放进度）
    val currentLineIndex = lyrics.indices.lastOrNull { lyrics[it].timestamp <= playbackPositionMs } ?: 0

    // 播放进度变化时自动滚动到当前行
    LaunchedEffect(playbackPositionMs, currentLineIndex) {
        if (lyrics.isNotEmpty() && currentLineIndex in lyrics.indices) {
            lazyListState.animateScrollToItem(index = currentLineIndex)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val lineHeightPx = 48f // 估算行高
        val verticalPadding = with(LocalDensity.current) { ((boxHeightPx - lineHeightPx) / 2).toDp() }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = verticalPadding)
        ) {
            itemsIndexed(lyrics) { index, lyricLine ->
                val isCurrentLine = index == currentLineIndex
                Text(
                    text = lyricLine.text.ifEmpty { "♪" },
                    fontSize = if (isCurrentLine) 18.sp else 14.sp,
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentLine) RED else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ===== 歌词时间轴视图（拖动时显示对应歌词）=====
@Composable
private fun LyricTimelineView(
    lyrics: List<LyricLine>,
    startMs: Long,
    endMs: Long,
    playbackPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 找到播放进度对应的歌词行
    val effectivePos = if (playbackPositionMs > 0L) playbackPositionMs else (startMs + endMs) / 2
    val currentLine = lyrics.findLast { it.timestamp <= effectivePos }

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
