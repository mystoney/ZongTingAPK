package com.zongting.zongting.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Banner
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.Playlist
import com.zongting.zongting.data.repository.MusicRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.zongting.zongting.data.model.Song
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadingJob: kotlinx.coroutines.Job? = null

    init {
        // 在 init 中启动加载，确保 ViewModel 创建时自动加载，不依赖任何 Composable 生命周期
        Log.d("HomeDebug", "HomeViewModel.init: starting load (this means VM was CREATED)")
        loadHomeData()
    }

    fun loadHomeDataIfNeeded() {
        Log.d("HomeDebug", "loadHomeDataIfNeeded: loadingJob.active=${loadingJob?.isActive}, banners=${_uiState.value.banners.size}, playlists=${_uiState.value.playlists.size}")
        if (loadingJob?.isActive == true) {
            Log.d("HomeDebug", "loadHomeDataIfNeeded: SKIP - already loading")
            return
        }
        if (_uiState.value.banners.isNotEmpty() || _uiState.value.playlists.isNotEmpty()) {
            Log.d("HomeDebug", "loadHomeDataIfNeeded: SKIP - has data")
            return
        }
        Log.d("HomeDebug", "loadHomeDataIfNeeded: CALLING loadHomeData")
        loadHomeData()
    }

    fun refreshHomeData() {
        Log.d("HomeDebug", "refreshHomeData: force reload")
        // 清空现有数据，强制重新加载
        _uiState.value = HomeUiState()
        loadingJob?.cancel()
        loadHomeData()
    }

    fun loadHomeData() {
        if (loadingJob?.isActive == true) return  // 防止重复加载
        Log.d("HomeDebug", "loadHomeData: START")
        loadingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            Log.d("HomeDebug", "loadHomeData: isLoading=true")

            // 并行加载数据
            val bannersJob = launch { loadBanners() }
            val playlistsJob = launch { loadPlaylists() }
            val hotBangsJob = launch {
                loadHotBangs()
                // 榜单加载完成后立即加载热门歌曲
                loadHotSongs()
            }

            bannersJob.join()
            playlistsJob.join()
            hotBangsJob.join()

            _uiState.value = _uiState.value.copy(isLoading = false)
            Log.d("HomeDebug", "loadHomeData: DONE isLoading=false, banners=${_uiState.value.banners.size}, playlists=${_uiState.value.playlists.size}")
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
        Log.d("HomeDebug", "loadPlaylists: START")
        repository.getRecommendPlaylists(pn = 1, rn = 20)
            .onSuccess { playlists ->
                Log.d("HomeDebug", "loadPlaylists: SUCCESS playlists.size=${playlists.size}")
                _uiState.value = _uiState.value.copy(playlists = playlists)
                // 后台预取前6个歌单详情，用户点击时直接命中缓存
                viewModelScope.launch {
                    playlists.take(6).forEach { playlist ->
                        repository.prefetchPlaylistDetail(playlist.id)
                    }
                }
            }
            .onFailure { error ->
                Log.d("HomeDebug", "loadPlaylists: FAIL ${error.message}")
                _uiState.value = _uiState.value.copy(error = error.message)
            }
    }

    private suspend fun loadHotBangs() {
        Log.d("HomeDebug", "loadHotBangs: START")
        repository.getBangMenu()
            .onSuccess { categories ->
                // 取第一个分类（官方榜）的前8个
                val bangs = categories.firstOrNull()?.list?.take(8) ?: emptyList()
                Log.d("HomeDebug", "loadHotBangs: SUCCESS bangs.size=${bangs.size}")
                _uiState.value = _uiState.value.copy(hotBangs = bangs)
            }
            .onFailure { error ->
                Log.d("HomeDebug", "loadHotBangs: FAIL ${error.message}")
            }
    }

    private suspend fun loadHotSongs() {
        // 从"热歌榜"（第一个 hotBang）加载歌曲作为"热门歌曲"
        val firstBang = _uiState.value.hotBangs.firstOrNull() ?: return
        Log.d("HomeDebug", "loadHotSongs: START bangId=${firstBang.id}")
        repository.getBangMusicList(firstBang.id, pn = 1, rn = 30, sourceId = firstBang.sourceId.ifEmpty { firstBang.id })
            .onSuccess { songs ->
                Log.d("HomeDebug", "loadHotSongs: SUCCESS songs.size=${songs.size}")
                _uiState.value = _uiState.value.copy(hotSongs = songs)
            }
            .onFailure { error ->
                Log.d("HomeDebug", "loadHotSongs: FAIL ${error.message}")
            }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val banners: List<Banner> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val hotBangs: List<Bang> = emptyList(),    // 热门榜单
    val hotSongs: List<Song> = emptyList(),     // 热门歌曲（用于第三页）
    val error: String? = null
)
