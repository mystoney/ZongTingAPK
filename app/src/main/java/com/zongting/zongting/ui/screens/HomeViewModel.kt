package com.zongting.zongting.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Banner
import com.zongting.zongting.data.model.Playlist
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 并行加载数据
            val bannersJob = launch { loadBanners() }
            val playlistsJob = launch { loadPlaylists() }
            val hotSongsJob = launch { loadHotSongs() }

            bannersJob.join()
            playlistsJob.join()
            hotSongsJob.join()

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun loadBanners() {
        // 本地 banner 图片 (assets 中的照片)
        val localBanners = listOf(
            Banner(id = 1001, pic = "file:///android_asset/banner/banner_1.jpg", newPic = "", newPicText = "", url = "", priority = 1),
            Banner(id = 1002, pic = "file:///android_asset/banner/banner_2.jpg", newPic = "", newPicText = "", url = "", priority = 2),
            Banner(id = 1003, pic = "file:///android_asset/banner/banner_3.jpg", newPic = "", newPicText = "", url = "", priority = 3),
            Banner(id = 1004, pic = "file:///android_asset/banner/banner_4.jpg", newPic = "", newPicText = "", url = "", priority = 4),
            Banner(id = 1005, pic = "file:///android_asset/banner/banner_5.jpg", newPic = "", newPicText = "", url = "", priority = 5),
            Banner(id = 1006, pic = "file:///android_asset/banner/banner_6.jpg", newPic = "", newPicText = "", url = "", priority = 6),
            Banner(id = 1007, pic = "file:///android_asset/banner/banner_7.jpg", newPic = "", newPicText = "", url = "", priority = 7),
            Banner(id = 1008, pic = "file:///android_asset/banner/banner_8.jpg", newPic = "", newPicText = "", url = "", priority = 8),
            Banner(id = 1009, pic = "file:///android_asset/banner/banner_9.jpg", newPic = "", newPicText = "", url = "", priority = 9),
            Banner(id = 1010, pic = "file:///android_asset/banner/banner_10.jpg", newPic = "", newPicText = "", url = "", priority = 10),
            Banner(id = 1011, pic = "file:///android_asset/banner/banner_11.jpg", newPic = "", newPicText = "", url = "", priority = 11)
        )

        // 只用本地图片，不加载 API banner
        _uiState.value = _uiState.value.copy(banners = localBanners)
    }

    private suspend fun loadPlaylists() {
        repository.getRecommendPlaylists(pn = 1, rn = 20)
            .onSuccess { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
    }

    private suspend fun loadHotSongs() {
        // 获取网易云音乐热歌榜作为热门歌曲
        repository.getBangMusicList(bangId = "3778678") // 3778678 是网易云热歌榜
            .onSuccess { songs ->
                _uiState.value = _uiState.value.copy(hotSongs = songs)
            }
            .onFailure {
                // 热门歌曲失败不影响主功能
            }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val banners: List<Banner> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val hotSongs: List<Song> = emptyList(),
    val error: String? = null
)
