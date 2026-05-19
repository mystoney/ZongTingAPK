package com.zongting.zongting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.repository.FavoriteRepository
import com.zongting.zongting.data.repository.MusicRepository
import com.zongting.zongting.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylist: StateFlow<List<Song>> = _currentPlaylist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _lyricState = MutableStateFlow<LyricState>(LyricState.Idle)
    val lyricState: StateFlow<LyricState> = _lyricState.asStateFlow()

    private val _playUrlCache = mutableMapOf<String, String>()
    private var _playUrlFetchSeq = 0  // 请求序列号，防止旧请求覆盖新歌

    // 收藏的歌曲rid集合
    private val _favoriteSongs = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongs: StateFlow<Set<Long>> = _favoriteSongs.asStateFlow()

    // 收藏歌曲的完整信息（用于展示列表）
    private val _favoriteSongList = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongList: StateFlow<List<Song>> = _favoriteSongList.asStateFlow()

    // 最近播放列表
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    init {
        // 设置 PlayerManager 的 URL 获取器（用于切歌时动态获取播放 URL）
        PlayerManager.setUrlFetcher { song ->
            getPlayUrl(song.rid, song.source)
        }
        // 从磁盘加载收藏歌曲
        viewModelScope.launch {
            val songs = favoriteRepository.favoriteSongs.first()
            _favoriteSongList.value = songs
            _favoriteSongs.value = songs.map { it.rid }.toSet()
        }
    }

    fun isFavorite(rid: Long): Boolean = _favoriteSongs.value.contains(rid)

    fun toggleFavorite(song: Song) {
        val current = _favoriteSongs.value.toMutableSet()
        val currentList = _favoriteSongList.value.toMutableList()
        if (current.contains(song.rid)) {
            current.remove(song.rid)
            currentList.removeAll { it.rid == song.rid }
            viewModelScope.launch {
                favoriteRepository.removeFavorite(song.rid)
            }
        } else {
            current.add(song.rid)
            currentList.add(0, song)
            viewModelScope.launch {
                favoriteRepository.addFavorite(song)
            }
        }
        _favoriteSongs.value = current
        _favoriteSongList.value = currentList
    }

    fun playSong(song: Song, playlist: List<Song> = listOf(song), replace: Boolean = false) {
        // 清空现有播放列表，替换为新歌单
        val newPlaylist = if (replace) {
            playlist.take(50)
        } else {
            // 选歌时插入到播放列表第一首，保持队列连贯
            val existing = _currentPlaylist.value.toMutableList()
            existing.removeAll { it.rid == song.rid }
            existing.add(0, song)
            existing.take(50)
        }
        _currentSong.value = song
        _currentPlaylist.value = newPlaylist
        _currentIndex.value = 0
        _isPlaying.value = true

        // 添加到最近播放（最多保留50首，去重）
        val recent = _recentlyPlayed.value.toMutableList()
        recent.removeAll { it.rid == song.rid }
        recent.add(0, song)
        if (recent.size > 50) recent.removeAt(recent.lastIndex)
        _recentlyPlayed.value = recent

        // 异步获取播放地址并开始播放
        val seq = ++_playUrlFetchSeq  // 递增序列号
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val url = getPlayUrl(song.rid, song.source)
            // 只有序列号未变化（期间没有被新点击取消）才更新播放器
            if (seq == _playUrlFetchSeq && url != null) {
                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                // 真正调用播放器
                PlayerManager.playSong(song, url, newPlaylist)
            } else if (seq == _playUrlFetchSeq) {
                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
            }
            // seq != _playUrlFetchSeq → 期间有新歌曲被点击，静默丢弃此结果
        }
    }

    /**
     * 播放整个歌单：替换当前播放列表并从第一首开始播放
     */
    fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val result = repository.getPlaylistDetail(playlistId)
            result.fold(
                onSuccess = { playlistData ->
                    val songs = playlistData.musicList.map { it.copy(source = "kuwo", playable = true) }
                    if (songs.isNotEmpty()) {
                        playSong(songs.first(), songs, replace = true)
                    } else {
                        _playbackState.value = PlaybackState(error = "歌单为空", isLoading = false)
                    }
                },
                onFailure = { e ->
                    _playbackState.value = PlaybackState(error = e.message ?: "获取歌单失败", isLoading = false)
                }
            )
        }
    }

    /**
     * 仅在播放列表为空时播放歌单，否则不操作
     */
    fun playPlaylistIfEmpty(playlistId: Long) {
        if (_currentPlaylist.value.isEmpty()) {
            playPlaylist(playlistId)
        }
    }

    private suspend fun getPlayUrl(rid: Long, source: String = "kuwo"): String? {
        val cacheKey = "${source}_$rid"
        _playUrlCache[cacheKey]?.let { return it }

        // 获取播放地址
        val result = repository.getPlayUrl(rid, source = source)
        return result.getOrNull()?.also { _playUrlCache[cacheKey] = it }
    }

    fun togglePlayPause() {
        PlayerManager.togglePlayPause()
    }

    fun updatePlayingState(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun updateCurrentSong(song: Song?, index: Int) {
        _currentSong.value = song
        _currentIndex.value = index
    }

    fun playNext() {
        PlayerManager.seekToNext()
    }

    fun playPrevious() {
        PlayerManager.seekToPrevious()
    }

    fun seekTo(position: Long) {
        _playbackState.value = _playbackState.value.copy(position = position)
        PlayerManager.seekTo(position)
    }

    fun updateProgress(position: Long, duration: Long) {
        _playbackState.value = _playbackState.value.copy(
            position = position,
            duration = duration
        )
    }

    /** 获取并解析歌词 - 酷我失败时自动从网易云搜索取歌词 */
    fun fetchLyric(musicId: Long, source: String = "kuwo", name: String = "", artist: String = "") {
        viewModelScope.launch {
            _lyricState.value = LyricState.Loading
            val result = repository.getLyric(musicId, source, name, artist)
            result.fold(
                onSuccess = { rawLines ->
                    android.util.Log.d("KuwoDebug", "fetchLyric onSuccess: rawLines.size=${rawLines.size}, first3=${rawLines.take(3)}")
                    val parsed = rawLines.mapNotNull { raw ->
                        val ms = parseTimeToMs(raw.time)
                        android.util.Log.v("KuwoDebug", "  parseTimeToMs(${raw.time}) = $ms, lyric=${raw.lineLyric.take(20)}")
                        if (ms != null && raw.lineLyric.isNotBlank()) {
                            LyricLine(ms, raw.lineLyric.trim())
                        } else null
                    }
                    android.util.Log.d("KuwoDebug", "fetchLyric parsed.size=${parsed.size}")
                    if (parsed.isNotEmpty()) {
                        _lyricState.value = LyricState.Success(parsed)
                    } else {
                        _lyricState.value = LyricState.Error("歌词为空")
                    }
                },
                onFailure = { e ->
                    _lyricState.value = LyricState.Error(e.message ?: "获取歌词失败")
                }
            )
        }
    }

    /** 将 "7.87" 格式的时间字符串转换为毫秒 */
    private fun parseTimeToMs(timeStr: String): Long? {
        return try {
            val seconds = timeStr.toDouble()
            (seconds * 1000).toLong()
        } catch (e: Exception) {
            null
        }
    }
}

data class PlaybackState(
    val playUrl: String? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
)
