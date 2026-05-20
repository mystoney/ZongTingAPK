package com.zongting.zongting.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.PlaylistData
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: Long) {
        Log.d("HomeDebug", "PlaylistViewModel.loadPlaylist: START playlistId=$playlistId")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getPlaylistDetail(playlistId)
                .onSuccess { playlistData ->
                    Log.d("HomeDebug", "PlaylistViewModel.loadPlaylist: SUCCESS songs=${playlistData.musicList.size}")
                    // 酷我推荐歌单统一设为可播放，不显示版权锁图标
                    val playableSongs = playlistData.musicList.map {
                        it.copy(source = "kuwo", playable = true)
                    }
                    _uiState.value = _uiState.value.copy(
                        playlist = playlistData,
                        songs = playableSongs,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    Log.d("HomeDebug", "PlaylistViewModel.loadPlaylist: FAIL error=${error.message}")
                    _uiState.value = _uiState.value.copy(
                        error = error.message,
                        isLoading = false
                    )
                }
        }
    }
}

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playlist: PlaylistData? = null,
    val songs: List<Song> = emptyList(),
    val error: String? = null
)
