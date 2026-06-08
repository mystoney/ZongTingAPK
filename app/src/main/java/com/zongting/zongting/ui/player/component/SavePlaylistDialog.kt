package com.zongting.zongting.ui.player.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zongting.zongting.data.model.UserPlaylist

/**
 * Dialog for adding the current song (or set of songs) to one of the
 * user's playlists, or for creating a new playlist on the fly.
 *
 * Originally a `private fun` in PlayerScreen.kt. Hoisted here so the
 * dialog can be shared by every player layout (PhonePortrait,
 * PhoneLandscape, PadLandscape, PadPortrait) without duplicating the
 * UI tree.
 *
 * @param songCount how many songs are about to be added — drives the
 *   "将 N 首歌曲保存到歌单" header.
 * @param playlists existing user playlists to choose from.
 * @param onSelectPlaylist invoked with the chosen playlist id.
 * @param onCreatePlaylist invoked with a brand-new playlist name.
 * @param onDismiss always called when the user closes the dialog.
 */
@Composable
fun SavePlaylistDialog(
    songCount: Int,
    playlists: List<UserPlaylist>,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var showNewPlaylistInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20B1E10),
        title = { Text("保存到歌单", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (songCount > 0) {
                    Text("将 $songCount 首歌曲保存到歌单", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (showNewPlaylistInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("歌单名称") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showNewPlaylistInput = false }) { Text("取消", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    showNewPlaylistInput = false
                                    onDismiss()
                                }
                            }
                        ) { Text("创建", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text("暂无歌单，请先创建", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPlaylist(playlist.id)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${playlist.songs.size}首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showNewPlaylistInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("创建新歌单", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) }
        }
    )
}
