package com.zongting.zongting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.data.repository.FavoriteRepository
import com.zongting.zongting.data.repository.MusicRepository
import com.zongting.zongting.data.repository.PlaybackStateRepository
import com.zongting.zongting.data.repository.PlaylistRepository
import com.zongting.zongting.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val favoriteRepository: FavoriteRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackStateRepository: PlaybackStateRepository
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

    // 播放模式：0=顺序播放, 1=单曲循环, 2=随机播放
    private val _playMode = MutableStateFlow(0)
    val playMode: StateFlow<Int> = _playMode.asStateFlow()

    // 最近播放列表
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // 用户自定义歌单
    val userPlaylists: StateFlow<List<UserPlaylist>> = playlistRepository.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        // 恢复上次播放状态
        viewModelScope.launch {
            val state = playbackStateRepository.playbackStateFlow.first()
            if (state.playlist.isNotEmpty()) {
                _currentPlaylist.value = state.playlist
                _currentIndex.value = state.currentIndex
                _currentSong.value = state.playlist.getOrNull(state.currentIndex)
                // 同步到 PlayerManager，使下一首/上一首按钮在重启后可用
                PlayerManager.setPlaylist(state.playlist, state.currentIndex)
                // 自动恢复播放
                state.playlist.getOrNull(state.currentIndex)?.let { song ->
                    val url = getPlayUrl(song.rid, song.source)
                    if (url != null) {
                        PlayerManager.playSong(song, url, state.playlist)
                    }
                }
            }
        }

        // 注册最近播放计时器回调：歌曲播放超过10秒后加入最近播放
        PlayerManager.onSongBecameRecent = { song ->
            addToRecentlyPlayed(song)
        }
    }

    /** 将歌曲加入最近播放（最大100首，去重，由 PlayerManager 10秒计时器调用） */
    private fun addToRecentlyPlayed(song: Song) {
        val recent = _recentlyPlayed.value.toMutableList()
        recent.removeAll { it.rid == song.rid }
        recent.add(0, song)
        if (recent.size > 100) recent.removeAt(recent.lastIndex)
        _recentlyPlayed.value = recent
    }

    fun isFavorite(rid: Long): Boolean = _favoriteSongs.value.contains(rid)

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun createPlaylistAndAddSong(name: String, song: Song) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylistAndGetId(name)
            playlistRepository.addSongToPlaylist(id, song)
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        viewModelScope.launch { playlistRepository.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { playlistRepository.deletePlaylist(id) }
    }

    fun addSongToPlaylist(playlistId: String, song: Song, onDone: () -> Unit) {
        viewModelScope.launch { playlistRepository.addSongToPlaylist(playlistId, song); onDone() }
    }

    fun addSongsToPlaylist(playlistId: String, songs: List<Song>, onDone: () -> Unit) {
        viewModelScope.launch { playlistRepository.addSongsToPlaylist(playlistId, songs); onDone() }
    }

    fun createPlaylistAndAddSongs(name: String, songs: List<Song>, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val playlist = playlistRepository.createPlaylist(name)
            playlistRepository.addSongsToPlaylist(playlist.id, songs)
            onDone(playlist.id)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songRid: Long) {
        viewModelScope.launch { playlistRepository.removeSongFromPlaylist(playlistId, songRid) }
    }

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
        // 检查歌曲是否已在当前播放列表中
        val existingPlaylist = _currentPlaylist.value.toMutableList()
        val existingIndex = existingPlaylist.indexOfFirst { it.rid == song.rid }

        val newPlaylist: List<Song>
        val targetIndex: Int

        if (replace) {
            // 替换模式：清空并播放新歌单
            newPlaylist = playlist.take(50)
            targetIndex = 0
        } else if (existingIndex >= 0) {
            // 歌曲已在播放列表中：保持列表不变，只切换到该位置播放
            newPlaylist = existingPlaylist
            targetIndex = existingIndex
        } else {
            // 歌曲不在播放列表中：插入到第一首
            existingPlaylist.removeAll { it.rid == song.rid }
            existingPlaylist.add(0, song)
            newPlaylist = existingPlaylist.take(50)
            targetIndex = 0
        }

        _currentSong.value = song
        _currentPlaylist.value = newPlaylist
        _currentIndex.value = targetIndex
        _isPlaying.value = true

        // 最近播放由 PlayerManager 的10秒计时器在播放超过10秒后自动添加

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

    /** 将歌曲添加到队列末尾并立即播放 */
    fun appendToQueueAndPlay(song: Song) {
        val newPlaylist = _currentPlaylist.value + song
        _currentPlaylist.value = newPlaylist
        _currentSong.value = song
        // 最近播放由 PlayerManager 的10秒计时器在播放超过10秒后自动添加
        val seq = ++_playUrlFetchSeq
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val url = getPlayUrl(song.rid, song.source)
            if (seq == _playUrlFetchSeq && url != null) {
                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                PlayerManager.appendToQueueAndPlay(song, url)
            } else if (seq == _playUrlFetchSeq) {
                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
            }
        }
    }

    /** 只将歌曲添加到队列末尾，不自动播放 */
    fun addSongsToQueue(songs: List<Song>) {
        val newPlaylist = (_currentPlaylist.value + songs).take(50)
        _currentPlaylist.value = newPlaylist
    }

    /**
     * 将歌曲插入到当前播放歌曲的上一首位置，并立即播放该歌曲
     */
    fun playSongPrev(song: Song) {
        val currentIdx = _currentIndex.value
        val existing = _currentPlaylist.value.toMutableList()
        // 去重：已存在的歌曲先移除
        existing.removeAll { it.rid == song.rid }
        // 插入到当前歌曲位置（会把它及之后的所有歌曲往后推）
        val insertPos = currentIdx.coerceAtMost(existing.size)
        existing.add(insertPos, song)
        _currentPlaylist.value = existing.take(50)
        _currentIndex.value = insertPos
        _currentSong.value = song
        _isPlaying.value = true
        // 最近播放由 PlayerManager 的10秒计时器在播放超过10秒后自动添加
        val seq = ++_playUrlFetchSeq
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val url = getPlayUrl(song.rid, song.source)
            if (seq == _playUrlFetchSeq && url != null) {
                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                PlayerManager.playSong(song, url, existing.take(50))
            } else if (seq == _playUrlFetchSeq) {
                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
            }
        }
    }

    /**
     * 将歌曲插入到当前播放歌曲的下一首位置，并立即播放该歌曲
     */
    fun playSongNext(song: Song) {
        val currentIdx = _currentIndex.value
        val existing = _currentPlaylist.value.toMutableList()
        // 去重：已存在的歌曲先移除
        existing.removeAll { it.rid == song.rid }
        val insertPos = (currentIdx + 1).coerceAtMost(existing.size)
        existing.add(insertPos, song)
        _currentPlaylist.value = existing.take(50)
        _currentIndex.value = insertPos
        _currentSong.value = song
        _isPlaying.value = true
        // 最近播放由 PlayerManager 的10秒计时器在播放超过10秒后自动添加
        val seq = ++_playUrlFetchSeq
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val url = getPlayUrl(song.rid, song.source)
            if (seq == _playUrlFetchSeq && url != null) {
                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                PlayerManager.playSong(song, url, existing.take(50))
            } else if (seq == _playUrlFetchSeq) {
                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
            }
        }
    }

    /** 点击歌曲：如果已在当前播放列表中则直接播放，否则追加到队列并播放 */
    fun playOrAppendSong(song: Song) {
        val idx = _currentPlaylist.value.indexOfFirst { it.rid == song.rid }
        _currentSong.value = song
        if (idx >= 0) {
            viewModelScope.launch {
                _playbackState.value = PlaybackState(isLoading = true)
                val url = getPlayUrl(song.rid, song.source)
                if (url != null) {
                    _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                    PlayerManager.playSong(song, url, _currentPlaylist.value)
                } else {
                    _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
                }
            }
        } else {
            appendToQueueAndPlay(song)
        }
    }

    /** 播放整个列表（用于"播放全部"按钮） */
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.size - 1)
        _currentPlaylist.value = songs
        val song = songs[index]
        _currentSong.value = song
        viewModelScope.launch {
            _playbackState.value = PlaybackState(isLoading = true)
            val url = getPlayUrl(song.rid, song.source)
            if (url != null) {
                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                PlayerManager.playSong(song, url, songs)
            } else {
                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
            }
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
                    // 保持歌单歌曲原有 source（酷我歌单为 "kuwo"，网易云歌单为 "netease"）
                    val songs = playlistData.musicList.map {
                        it.copy(source = it.source ?: "kuwo", playable = true)
                    }
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
     * 点击歌单时：队列为空则替换播放，否则插入到当前歌曲下一首并立即播放
     */
    fun playPlaylistIfEmpty(playlistId: Long) {
        if (_currentPlaylist.value.isEmpty()) {
            playPlaylist(playlistId)
        } else {
            viewModelScope.launch {
                val result = repository.getPlaylistDetail(playlistId)
                result.fold(
                    onSuccess = { playlistData ->
                        val songs = playlistData.musicList.map {
                            it.copy(source = it.source ?: "kuwo", playable = true)
                        }
                        if (songs.isNotEmpty()) {
                            // 插入到当前歌曲的下一首位置，然后立即播放第一首
                            val currentIdx = _currentIndex.value
                            val currentSong = _currentSong.value
                            val existing = _currentPlaylist.value.toMutableList()
                            // 去除即将插入歌曲中已在队列里的（避免重复）
                            val existingRids = existing.map { it.rid }.toSet()
                            val newSongs = songs.filter { it.rid !in existingRids }
                            val insertPos = (currentIdx + 1).coerceAtMost(existing.size)
                            existing.addAll(insertPos, newSongs)
                            _currentPlaylist.value = existing.take(50)
                            _currentIndex.value = insertPos
                            _currentSong.value = newSongs.first()
                            _isPlaying.value = true
                            val seq = ++_playUrlFetchSeq
                            _playbackState.value = PlaybackState(isLoading = true)
                            val url = getPlayUrl(newSongs.first().rid, newSongs.first().source)
                            if (seq == _playUrlFetchSeq && url != null) {
                                _playbackState.value = PlaybackState(playUrl = url, isLoading = false)
                                PlayerManager.playSong(newSongs.first(), url, existing.take(50))
                            } else if (seq == _playUrlFetchSeq) {
                                _playbackState.value = PlaybackState(error = "无法获取播放地址", isLoading = false)
                            }
                        }
                    },
                    onFailure = { /* 静默失败 */ }
                )
            }
        }
    }

    private suspend fun getPlayUrl(rid: Long, source: String = "kuwo"): String? {
        val cacheKey = "${source}_$rid"
        val cached = _playUrlCache[cacheKey]
        android.util.Log.d("KuwoDebug", "MainViewModel.getPlayUrl cacheKey=$cacheKey cached=$cached")
        cached?.let { return it }

        // 获取播放地址
        val result = repository.getPlayUrl(rid, source = source)
        android.util.Log.d("KuwoDebug", "MainViewModel.getPlayUrl result=$result")
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
        // 切歌时重置播放进度，避免新歌显示旧歌的结束位置
        _playbackState.value = _playbackState.value.copy(position = 0L, duration = 0L)
    }

    fun playNext() {
        when (_playMode.value) {
            1 -> {
                // ★ 单曲循环：回到开头重新播放当前歌曲
                PlayerManager.seekTo(0)
            }
            2 -> {
                // ★ 随机播放：随机选一首播放（不能和当前相同）
                val playlist = _currentPlaylist.value
                if (playlist.size > 1) {
                    val currentIdx = _currentIndex.value
                    val randomIdx = (playlist.indices - currentIdx).random()
                    playSong(playlist[randomIdx], playlist)
                } else if (playlist.isNotEmpty()) {
                    PlayerManager.seekTo(0)
                }
            }
            else -> {
                // ★ 顺序播放：正常切下一首
                PlayerManager.seekToNext()
            }
        }
    }

    fun playPrevious() {
        when (_playMode.value) {
            1 -> {
                // ★ 单曲循环：回到开头重新播放当前歌曲
                PlayerManager.seekTo(0)
            }
            2 -> {
                // ★ 随机播放：随机选一首播放
                val playlist = _currentPlaylist.value
                if (playlist.isNotEmpty()) {
                    val randomIdx = playlist.indices.random()
                    playSong(playlist[randomIdx], playlist)
                }
            }
            else -> {
                // ★ 顺序播放：正常切上一首
                PlayerManager.seekToPrevious()
            }
        }
    }

    fun togglePlayMode() {
        _playMode.value = (_playMode.value + 1) % 3
        // 同步设置 ExoPlayer 的 repeat/shuffle 模式
        PlayerManager.getPlayer()?.let { p ->
            when (_playMode.value) {
                1 -> {
                    p.repeatMode = Player.REPEAT_MODE_ONE
                    p.shuffleModeEnabled = false
                }
                2 -> {
                    p.repeatMode = Player.REPEAT_MODE_ALL
                    p.shuffleModeEnabled = true
                }
                else -> {
                    p.repeatMode = Player.REPEAT_MODE_OFF
                    p.shuffleModeEnabled = false
                }
            }
        }
    }

    fun seekTo(position: Long) {
        _playbackState.value = _playbackState.value.copy(position = position)
        PlayerManager.seekTo(position)
    }

    fun updateProgress(position: Long, duration: Long) {
        // ExoPlayer.duration 在媒体未完全加载时可能为0，用歌曲自身时长兜底
        // 注意：Song.duration 来自 Kuwo API，单位是秒；而 PlaybackState.duration 是毫秒
        val effectiveDuration = if (duration > 0) duration else ((_currentSong.value?.duration ?: 0) * 1000L)
        _playbackState.value = _playbackState.value.copy(
            position = position,
            duration = effectiveDuration
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

    /** 保存当前播放状态到磁盘（供 MainActivity onPause 时调用） */
    fun savePlaybackState() {
        viewModelScope.launch {
            playbackStateRepository.savePlaybackState(_currentPlaylist.value, _currentIndex.value)
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
