package com.zongting.zongting.ringtone

import android.content.Context
import android.net.Uri
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
    val hasWriteSettings: Boolean = false // 是否有WRITE_SETTINGS权限
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
        _state.value = RingtoneCutterState(
            song = song,
            durationMs = durationMs,
            startMs = 0L,
            endMs = minOf(durationMs, 50_000L),
            hasWriteSettings = AudioRingtoneHelper.hasWriteSettingsPermission(context)
        )
    }

    fun updateStart(ms: Long) {
        _state.value = _state.value.copy(
            startMs = ms.coerceIn(0, _state.value.durationMs),
            endMs = maxOf(_state.value.endMs, ms + 1000) // 保证最小1秒间隔
        )
    }

    fun updateEnd(ms: Long) {
        val maxEnd = minOf(_state.value.durationMs, _state.value.startMs + 50_000L)
        _state.value = _state.value.copy(
            endMs = ms.coerceIn(_state.value.startMs + 1000, maxEnd)
        )
    }

    private var positionJob: kotlinx.coroutines.Job? = null

    /** 预览播放截取片段 */
    fun previewClip() {
        val s = _state.value
        if (s.isPlaying) {
            PlayerManager.pause()
            positionJob?.cancel()
            _state.value = s.copy(isPlaying = false, playbackPositionMs = 0L)
        } else {
            PlayerManager.seekTo(s.startMs)
            PlayerManager.play()
            // 轮询播放进度
            positionJob?.cancel()
            positionJob = viewModelScope.launch {
                while (true) {
                    val pos = PlayerManager.currentPosition
                    val end = _state.value.endMs
                    _state.value = _state.value.copy(playbackPositionMs = pos)
                    // 播放到截取终点自动停止
                    if (pos >= end) {
                        PlayerManager.pause()
                        _state.value = _state.value.copy(isPlaying = false, playbackPositionMs = 0L)
                        break
                    }
                    kotlinx.coroutines.delay(100L)
                }
            }
            _state.value = s.copy(isPlaying = true, playbackPositionMs = s.startMs)
        }
    }

    /** 停止预览 */
    fun stopPreview() {
        positionJob?.cancel()
        PlayerManager.pause()
        _state.value = _state.value.copy(isPlaying = false, playbackPositionMs = 0L)
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
                        processingMessage = "正在设置为铃声..."
                    )

                    val success = AudioRingtoneHelper.setAsRingtone(
                        context = context,
                        filePath = result.filePath,
                        type = type
                    )

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = "",
                        resultMessage = if (success) {
                            when (type) {
                                AudioRingtoneHelper.RingtoneType.RINGTONE -> "已设为来电铃声"
                                AudioRingtoneHelper.RingtoneType.NOTIFICATION -> "已设为通知声"
                                AudioRingtoneHelper.RingtoneType.ALARM -> "已设为闹钟"
                            }
                        } else {
                            "设置失败，请检查权限"
                        },
                        resultType = if (success) ResultType.SUCCESS else ResultType.ERROR
                    )
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
