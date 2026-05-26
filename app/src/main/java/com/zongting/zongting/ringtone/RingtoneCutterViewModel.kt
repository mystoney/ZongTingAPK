package com.zongting.zongting.ringtone

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.player.PlayerManager
import com.zongting.zongting.ui.LyricLine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RingtoneCutterState(
    val song: Song? = null,
    val durationMs: Long = 0L,           // 总时长（毫秒）
    val startMs: Long = 0L,              // 截取起点
    val endMs: Long = 30_000L,           // 截取终点（默认30秒）
    val isPlaying: Boolean = false,      // 是否正在预览播放
    val playbackPositionMs: Long = 0L,  // 当前播放位置（毫秒）
    val isProcessing: Boolean = false,   // 是否正在裁剪/保存
    val processingMessage: String = "",  // 处理中的提示
    val resultMessage: String = "",       // 结果消息
    val resultType: ResultType = ResultType.NONE,
    val savedUri: Uri? = null,           // 保存到MediaStore的URI
    val hasWriteSettings: Boolean = false, // 是否有WRITE_SETTINGS权限
    val previewStartTimeNanos: Long = 0L    // 预览开始时刻（纳秒），用于过滤seek期间旧位置
) {
    val clipDurationMs: Long get() = endMs - startMs
    val isValid: Boolean get() = clipDurationMs in 1..50_000
}

enum class ResultType {
    NONE, SUCCESS, ERROR
}

@HiltViewModel
class RingtoneCutterViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(RingtoneCutterState())
    val state: StateFlow<RingtoneCutterState> = _state.asStateFlow()

    fun initialize(song: Song?, durationMs: Long, lyrics: List<LyricLine>) {
        val fixedDuration = 50_000L
        val minStart = 1_000L
        val maxStart = (durationMs - fixedDuration).coerceAtLeast(minStart)
        val start = minOf(1_000L, maxStart)
        Log.d("HermesDebug", "HermesDebug initialize: durationMs=$durationMs maxStart=$maxStart start=$start")
        // 先停止播放、seek到截取块起点，再设state + 激活守卫
        // 这样PlayerManager.listener后续回调会因守卫激活而无法覆盖playbackPositionMs
        PlayerManager.pause()
        PlayerManager.seekTo(start)
        val guardNano = System.nanoTime()
        _state.value = RingtoneCutterState(
            song = song,
            durationMs = durationMs,
            startMs = start,
            endMs = start + fixedDuration,
            hasWriteSettings = AudioRingtoneHelper.hasWriteSettingsPermission(context),
            playbackPositionMs = start,  // 白线对齐截取块起点
            previewStartTimeNanos = guardNano  // 激活守卫，屏蔽seek期间listener的旧位置覆盖
        )
    }

    fun updateStart(ms: Long) {
        val fixedDuration = 50_000L
        val minStart = 1_000L
        val maxStart = (_state.value.durationMs - fixedDuration).coerceAtLeast(minStart)
        val newStart = ms.coerceIn(minStart, maxStart)
        _state.value = _state.value.copy(
            startMs = newStart,
            endMs = newStart + fixedDuration
        )
    }

    fun updateEnd(ms: Long) {
        // 固定50秒，end跟随start变化
        val fixedDuration = 50_000L
        val minStart = 1_000L
        val maxStart = (_state.value.durationMs - fixedDuration).coerceAtLeast(minStart)
        // 先算newEnd对应的newStart
        val newEnd = ms.coerceIn(minStart + fixedDuration, _state.value.durationMs)
        val newStart = (newEnd - fixedDuration).coerceIn(minStart, maxStart)
        _state.value = _state.value.copy(
            startMs = newStart,
            endMs = newStart + fixedDuration
        )
    }

    private var positionJob: kotlinx.coroutines.Job? = null

    /** 预览播放截取片段 */
    fun previewClip() {
        val s = _state.value
        if (s.isPlaying) {
            PlayerManager.pause()
            positionJob?.cancel()
            _state.value = s.copy(isPlaying = false, playbackPositionMs = s.startMs)
        } else {
            // 读取当前state的startMs（而非lambda创建时捕获的旧值）
            val startNano = System.nanoTime()
            val currentStart = _state.value.startMs
            _state.value = _state.value.copy(isPlaying = true, playbackPositionMs = currentStart, previewStartTimeNanos = startNano)
            PlayerManager.seekTo(currentStart)
            PlayerManager.play()
            // 轮询播放进度：前200ms强制用startMs，屏蔽seek期间的旧位置
            positionJob?.cancel()
            positionJob = viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(100L)
                    val pos = PlayerManager.currentPosition
                    val end = _state.value.endMs
                    val start = _state.value.startMs
                    val elapsed = System.nanoTime() - _state.value.previewStartTimeNanos
                    // 前200ms认为seek未完成，白线固定在截取块起点
                    val safePos = if (elapsed < 200_000_000L) start else pos
                    _state.value = _state.value.copy(playbackPositionMs = safePos)
                    if (pos >= end) {
                        PlayerManager.pause()
                        _state.value = _state.value.copy(isPlaying = false, playbackPositionMs = _state.value.startMs, previewStartTimeNanos = -1L)
                        break
                    }
                }
            }
        }
    }

    /** 停止预览 */
    fun stopPreview() {
        positionJob?.cancel()
        PlayerManager.pause()
        _state.value = _state.value.copy(isPlaying = false, playbackPositionMs = _state.value.startMs, previewStartTimeNanos = -1L)
    }

    /** 导出音频文件（保存到下载目录） */
    fun exportAudio() {
        viewModelScope.launch {
            val s = _state.value
            val song = s.song ?: return@launch

            _state.value = s.copy(
                isProcessing = true,
                processingMessage = "正在裁剪音频...",
                resultMessage = "",
                resultType = ResultType.NONE
            )

            // 获取当前播放URL
            val player = PlayerManager.getPlayer()
            val currentItem = player?.currentMediaItem
            val audioUrl = currentItem?.localConfiguration?.uri?.toString()

            val result = AudioRingtoneHelper.trimAudio(
                context = context,
                songName = song.name,
                startMs = s.startMs,
                endMs = s.endMs,
                audioUrl = audioUrl
            )

            when (result) {
                is AudioRingtoneHelper.TrimResult.Success -> {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = "正在保存到音乐库...",
                        savedUri = null,
                        resultMessage = "",
                        resultType = ResultType.NONE
                    )

                    // 保存到MediaStore
                    val uri = AudioRingtoneHelper.saveToMediaStore(
                        context = context,
                        sourceFilePath = result.filePath,
                        songName = song.name,
                        artist = song.artist
                    )

                    if (uri != null) {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingMessage = "",
                            resultMessage = "已保存到音乐库：${song.name}_铃声",
                            resultType = ResultType.SUCCESS,
                            savedUri = uri
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingMessage = "",
                            resultMessage = "音频已裁剪，但保存失败",
                            resultType = ResultType.ERROR
                        )
                    }
                }
                is AudioRingtoneHelper.TrimResult.Error -> {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = "",
                        resultMessage = result.message,
                        resultType = ResultType.ERROR
                    )
                }
            }
        }
    }

    /** 设置为系统铃声 */
    fun setAsRingtone(type: AudioRingtoneHelper.RingtoneType) {
        viewModelScope.launch {
            val s = _state.value
            val song = s.song ?: return@launch

            // 检查权限
            if (!AudioRingtoneHelper.hasWriteSettingsPermission(context)) {
                AudioRingtoneHelper.setAsRingtone(context, "", type)
                _state.value = s.copy(
                    resultMessage = "请先授权「修改系统设置」权限后重试",
                    resultType = ResultType.ERROR,
                    hasWriteSettings = false
                )
                return@launch
            }

            _state.value = s.copy(
                isProcessing = true,
                processingMessage = "正在裁剪音频...",
                resultMessage = "",
                resultType = ResultType.NONE
            )

            // 获取当前播放URL
            val player = PlayerManager.getPlayer()
            val currentItem = player?.currentMediaItem
            val audioUrl = currentItem?.localConfiguration?.uri?.toString()

            val result = AudioRingtoneHelper.trimAudio(
                context = context,
                songName = song.name,
                startMs = s.startMs,
                endMs = s.endMs,
                audioUrl = audioUrl
            )

            when (result) {
                is AudioRingtoneHelper.TrimResult.Success -> {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = "正在保存到音乐库..."
                    )

                    // 保存到MediaStore获取content:// URI，再用它设置铃声
                    val uri = AudioRingtoneHelper.saveToMediaStore(
                        context = context,
                        sourceFilePath = result.filePath,
                        songName = song.name,
                        artist = song.artist
                    )

                    if (uri != null) {
                        val success = AudioRingtoneHelper.setMediaStoreAsRingtone(context, uri, type)
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingMessage = "",
                            savedUri = uri,
                            resultMessage = when {
                                success -> when (type) {
                                    AudioRingtoneHelper.RingtoneType.RINGTONE -> "已设为来电铃声"
                                    AudioRingtoneHelper.RingtoneType.NOTIFICATION -> "已设为通知声"
                                    AudioRingtoneHelper.RingtoneType.ALARM -> "已设为闹钟"
                                }
                                else -> "设置失败，请检查权限"
                            },
                            resultType = if (success) ResultType.SUCCESS else ResultType.ERROR
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingMessage = "",
                            resultMessage = "音频已裁剪，但保存失败",
                            resultType = ResultType.ERROR
                        )
                    }
                }
                is AudioRingtoneHelper.TrimResult.Error -> {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = "",
                        resultMessage = result.message,
                        resultType = ResultType.ERROR
                    )
                }
            }
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(
            resultMessage = "",
            resultType = ResultType.NONE
        )
    }

    fun refreshPermissionState() {
        _state.value = _state.value.copy(
            hasWriteSettings = AudioRingtoneHelper.hasWriteSettingsPermission(context)
        )
    }
}
