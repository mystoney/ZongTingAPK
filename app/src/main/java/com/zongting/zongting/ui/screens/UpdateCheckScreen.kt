package com.zongting.zongting.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.zongting.zongting.data.model.VersionInfo
import com.zongting.zongting.data.repository.UpdateEvent
import com.zongting.zongting.data.repository.UpdatePhase
import com.zongting.zongting.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    val updateEvent: StateFlow<UpdateEvent?> = updateRepository.updateEvent
    val updatePhase: StateFlow<UpdatePhase> = updateRepository.updatePhase

    /** 用户点击"下载" */
    fun onConfirmDownload() {
        updateRepository.confirmDownload()
    }

    /** 用户点击"取消" */
    fun onDismiss() {
        updateRepository.dismissUpdate()
    }

    /** 用户选择"立即安装" */
    fun onConfirmInstall() {
        updateRepository.confirmInstall()
    }

    /** 用户选择"稍后安装" */
    fun onDeferInstall() {
        updateRepository.deferInstall()
    }
}

/**
 * 统一的更新对话框：
 * - Checking: 检测中，显示转圈
 * - UpdateAvailable: 发现新版本，询问是否下载
 * - Downloading: 下载中，显示进度条
 * - Downloaded: 下载完成，询问是否立即安装
 * - Error: 显示错误信息
 */
@Composable
fun UpdateDialog(
    versionInfo: VersionInfo,
    updateEvent: UpdateEvent?,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmInstall: () -> Unit,
    onDeferInstall: () -> Unit
) {
    val isChecking = updateEvent is UpdateEvent.Checking
    val isDownloading = updateEvent is UpdateEvent.Downloading
    val isDownloaded = updateEvent is UpdateEvent.Downloaded
    val errorMsg = (updateEvent as? UpdateEvent.Error)?.message
    val progress = (updateEvent as? UpdateEvent.Downloading)?.progress ?: 0

    Dialog(
        onDismissRequest = { /* 用户必须做明确选择才能关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = when {
                        isChecking -> "正在检测当前版本是否需要更新"
                        isDownloading -> "正在下载更新"
                        isDownloaded -> "下载完成"
                        errorMsg != null -> "更新失败"
                        else -> "🎵 发现新版本"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 检测中：转圈
                if (isChecking) {
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 有新版本：显示版本号和更新日志
                if (!isChecking && !isDownloading && !isDownloaded && errorMsg == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "v${versionInfo.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (versionInfo.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = versionInfo.releaseNotes,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6
                            )
                        }
                    }
                }

                // 下载中：进度条
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "下载中... $progress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 下载完成：提示
                if (isDownloaded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "新版本已下载完成，是否立即安装并重启？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 错误信息
                errorMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 按钮区域（根据状态显示不同按钮）
                if (!isChecking) {
                    Spacer(modifier = Modifier.height(24.dp))

                    when {
                        // 下载完成：立即安装 / 稍后
                        isDownloaded -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDeferInstall,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("稍后")
                                }
                                Button(
                                    onClick = onConfirmInstall,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("立即安装")
                                }
                            }
                        }

                        // 下载中：仅显示进度，不显示按钮
                        isDownloading -> {
                            // 不显示按钮
                        }

                        // 有错误：显示确定按钮关闭
                        errorMsg != null -> {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("确定")
                            }
                        }

                        // 有新版本待下载：下载 / 取消
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("取消")
                                }
                                Button(
                                    onClick = onConfirmDownload,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("下载")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
