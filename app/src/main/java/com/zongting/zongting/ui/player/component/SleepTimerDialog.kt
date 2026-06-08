package com.zongting.zongting.ui.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Sleep-timer dialog. When [isActive] is true, the dialog shows the
 * remaining time and a single "取消定时" button. When false, it shows
 * the four preset chips (15 / 30 / 45 / 60 minutes) plus a custom
 * minutes input.
 *
 * Originally `fun SleepTimerDialog` in PlayerScreen.kt (non-private,
 * since the legacy entry was calling it from elsewhere). Hoisted here
 * so any player layout can invoke it via the same `PlayerActions`
 * callbacks (`onStartTimer`, `onCancelTimer`, `onDismiss`).
 */
@Composable
fun SleepTimerDialog(
    isActive: Boolean,
    remainingSeconds: Long,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }

    val presetMinutes = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭 🌙") },
        text = {
            Column {
                if (isActive) {
                    // 定时已开启，显示剩余时间和取消按钮
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Text(
                        text = "距离停止播放还有",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${mins}分${secs}秒",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C4DFF),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消定时", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    // 未开启，显示预设时间选项
                    Text(
                        text = "播放将在以下时间后自动停止：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetMinutes.forEach { mins ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onStartTimer(mins)
                                    onDismiss()
                                },
                                label = { Text("${mins}分钟") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                            label = { Text("自定义") },
                            suffix = { Text("分钟") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val mins = customMinutes.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    onStartTimer(mins)
                                    onDismiss()
                                }
                            },
                            enabled = customMinutes.isNotEmpty() && (customMinutes.toIntOrNull() ?: 0) > 0
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {}
    )
}
