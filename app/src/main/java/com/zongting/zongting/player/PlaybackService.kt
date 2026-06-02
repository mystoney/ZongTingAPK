package com.zongting.zongting.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zongting.zongting.MainActivity
import com.zongting.zongting.data.model.Song
import kotlinx.coroutines.*

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 配置音频属性
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 创建 PendingIntent 用于点击通知跳转到 App
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建 MediaSession（Service 自己管理 player，不再单独创建）
        mediaSession = MediaSession.Builder(this, ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build())
            .setSessionActivity(pendingIntent)
            .build()

        // 初始化 PlayerManager（传入 session.player，与 MediaSession 共用同一实例）
        PlayerManager.initialize(mediaSession!!.player, mediaSession!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

// ==================== 播放器管理器 ====================
object PlayerManager {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private var currentPlaylist: List<Song> = emptyList()
    private var currentIndex: Int = 0
    private val urlCache = mutableMapOf<Long, String>()  // rid -> playUrl

    // 播放状态监听器
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onPositionChanged: ((Long, Long) -> Unit)? = null
    var onSongChanged: ((Song?, Int) -> Unit)? = null
    /** 歌曲播放超过10秒后触发，用于"最近播放"延迟添加 */
    var onSongBecameRecent: ((Song) -> Unit)? = null

    // 最近播放10秒计时器
    private var recentTimerJob: Job? = null
    private var recentTimerSong: Song? = null
    private var recentTimerStartMs: Long = 0L
    private var recentTimerFired: Boolean = false

    // URL 获取回调（由 MainViewModel 设置）
    private var urlFetcher: (suspend (Song) -> String?)? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 防止切歌时重复触发
    private var isFetchingUrlForIndex: Int = -1

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    val currentPosition: Long
        get() = player?.currentPosition ?: 0L

    val duration: Long
        get() = player?.duration ?: 0L

    val currentSong: Song?
        get() = currentPlaylist.getOrNull(currentIndex)

    /** 注入 URL 获取函数（由 MainViewModel 调用） */
    fun setUrlFetcher(fetcher: (suspend (Song) -> String?)?) {
        urlFetcher = fetcher
    }

    fun initialize(player: Player, session: MediaSession) {
        this.player = player
        mediaSession = session

        // 注册播放器监听器
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged?.invoke(playing)
                // 管理最近播放10秒计时器
                if (playing) {
                    val song = currentSong
                    // 没有活动计时器时启动（切歌后第一次播放 / 从暂停恢复）
                    // 注意：onMediaItemTransition 会把 recentTimerJob 置 null，所以这里必须用"job 为空"作为启动条件
                    if (song != null && !recentTimerFired && recentTimerJob == null) {
                        recentTimerSong = song
                        recentTimerStartMs = System.currentTimeMillis()
                        recentTimerJob = scope.launch {
                            delay(10_000L)  // 等待10秒
                            val current = currentSong
                            if (current?.rid == song.rid && !recentTimerFired) {
                                recentTimerFired = true
                                onSongBecameRecent?.invoke(song)
                            }
                        }
                    }
                } else {
                    // 暂停：取消计时器（暂停期间不计时），但保留 recentTimerSong 和 recentTimerFired 状态以便恢复
                    recentTimerJob?.cancel()
                    recentTimerJob = null
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val pos = player?.currentPosition ?: 0L
                    val dur = player?.duration ?: 0L
                    if (dur > 0) onPositionChanged?.invoke(pos, dur)
                } else if (state == Player.STATE_ENDED) {
                    Log.d("ZongTing", "onPlaybackStateChanged: STATE_ENDED, triggering playNext")
                    val p = player ?: return
                    if (p.hasNextMediaItem()) {
                        p.playWhenReady = false
                        p.seekToNextMediaItem()
                    } else {
                        Log.d("ZongTing", "  -> no next item, looping to index 0")
                        p.seekTo(0, 0)
                        p.playWhenReady = true
                        p.play()
                    }
                }
            }

            // ExoPlayer 切换 MediaItem 时触发（自动切歌或用户切换）
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val p = player ?: return
                val newIndex = p.currentMediaItemIndex
                if (p.mediaItemCount == 0 || newIndex < 0) {
                    Log.d("ZongTing", "onMediaItemTransition: playlist empty or invalid index, skipping")
                    return
                }
                Log.d("ZongTing", "onMediaItemTransition: newIndex=$newIndex, currentIndex=$currentIndex, mediaItemCount=${p.mediaItemCount}, reason=$reason, mediaId=${mediaItem?.mediaId}")
                if (newIndex < 0 || newIndex >= currentPlaylist.size) {
                    Log.d("ZongTing", "  -> out of bounds (newIndex=$newIndex, playlist.size=${currentPlaylist.size}), skipping")
                    return
                }
                val newSong = currentPlaylist[newIndex]
                if (newSong.rid == currentPlaylist.getOrNull(currentIndex)?.rid) {
                    Log.d("ZongTing", "  -> same song, skipping")
                    return
                }

                // ★ 新歌切换：重置最近播放计时器
                recentTimerJob?.cancel()
                recentTimerJob = null
                recentTimerSong = newSong
                recentTimerStartMs = System.currentTimeMillis()
                recentTimerFired = false

                currentIndex = newIndex
                onSongChanged?.invoke(newSong, newIndex)
                Log.d("ZongTing", "  -> switching to song: ${newSong.name}, rid=${newSong.rid}")
                val cachedUrl = urlCache[newSong.rid]
                val currentUri = p.getMediaItemAt(newIndex).localConfiguration?.uri?.toString() ?: ""
                if (!cachedUrl.isNullOrEmpty() && currentUri.isNotEmpty() && currentUri != cachedUrl) {
                    Log.d("ZongTing", "  -> URL cached, replacing media item (currentUri was empty=$currentUri)")
                    val updatedItem = p.getMediaItemAt(newIndex).buildUpon().setUri(cachedUrl).build()
                    p.replaceMediaItem(newIndex, updatedItem)
                    p.playWhenReady = true
                    p.play()
                } else if (!cachedUrl.isNullOrEmpty() && currentUri.isNotEmpty()) {
                    Log.d("ZongTing", "  -> URL cached and URI already valid, skipping replace")
                } else if (!cachedUrl.isNullOrEmpty() && currentUri.isEmpty()) {
                    Log.d("ZongTing", "  -> URL cached but URI empty, replacing media item")
                    val updatedItem = p.getMediaItemAt(newIndex).buildUpon().setUri(cachedUrl).build()
                    p.replaceMediaItem(newIndex, updatedItem)
                    p.playWhenReady = true
                    p.play()
                } else if (isFetchingUrlForIndex != newIndex) {
                    isFetchingUrlForIndex = newIndex
                    Log.d("ZongTing", "  -> URL not cached, fetching async")
                    scope.launch {
                        val url = urlFetcher?.invoke(newSong)
                        if (url != null) {
                            Log.d("ZongTing", "  -> URL fetched, updating media item and playing")
                            urlCache[newSong.rid] = url
                            val updatedItem = p.getMediaItemAt(newIndex).buildUpon().setUri(url).build()
                            p.replaceMediaItem(newIndex, updatedItem)
                            p.playWhenReady = true
                            p.play()
                        } else {
                            Log.d("ZongTing", "  -> URL fetch failed")
                        }
                        isFetchingUrlForIndex = -1
                    }
                }
            }
        })
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        currentPlaylist = songs
        currentIndex = startIndex
    }

    fun playSong(song: Song, playUrl: String, playlist: List<Song> = listOf(song)) {
        val idx = if (playlist.size > 1) playlist.indexOf(song).coerceAtLeast(0) else 0
        val isSingleSong = playlist.size <= 1
        currentPlaylist = playlist
        isFetchingUrlForIndex = -1
        urlCache[song.rid] = playUrl

        player?.let { p ->
            if (isSingleSong) {
                // 单首歌：直接重建简单列表
                p.clearMediaItems()
                p.addMediaItem(buildMediaItem(song, playUrl))
                p.playWhenReady = false
                p.prepare()
                currentIndex = 0
                p.playWhenReady = true
                p.play()
            } else {
                // 多首歌：检查目标歌曲是否已在当前播放列表中
                val currentMediaIds = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId }
                val songMediaId = song.rid.toString()
                val existingIdx = currentMediaIds.indexOf(songMediaId)

                if (existingIdx >= 0 && p.mediaItemCount == playlist.size) {
                    // 歌曲已在列表中且列表长度一致：直接 seek 到该位置
                    // 注意：不在这里 replaceMediaItem，让 onMediaItemTransition 回调统一处理
                    // （避免双重触发：seekTo 会触发回调，回调里会检测 URL 并 replaceMediaItem）
                    Log.d("ZongTing", "playSong EXISTING: existingIdx=$existingIdx, targetIdx=$idx, playing=${p.isPlaying}")
                    p.seekTo(existingIdx, 0)
                    currentIndex = existingIdx
                    // 如果 URL 已缓存，直接更新该位置的 MediaItem（避免等回调异步处理）
                    val currentItem = p.getMediaItemAt(existingIdx)
                    val currentUri = currentItem.localConfiguration?.uri?.toString() ?: ""
                    if (currentUri.isEmpty() || currentUri == "") {
                        val updatedItem = currentItem.buildUpon().setUri(playUrl).build()
                        p.replaceMediaItem(existingIdx, updatedItem)
                    }
                    p.play()
                } else {
                    // 不在列表中或列表不一致：重建整个列表
                    // ★ 优化：先预取所有歌曲 URL，全部就绪后再 prepare，避免 ExoPlayer 拿空 URI 崩溃
                    p.playWhenReady = false
                    scope.launch {
                        val fetchedUrls = playlist.mapIndexed { i, s ->
                            async {
                                if (urlCache.containsKey(s.rid)) {
                                    urlCache[s.rid]!!
                                } else {
                                    urlFetcher?.invoke(s)?.also { urlCache[s.rid] = it }
                                }
                            }
                        }.awaitAll().mapIndexed { i, url -> i to (url ?: "") }
                        val urlMap = fetchedUrls.toMap().toMutableMap()
                        urlMap[idx] = playUrl  // 当前歌曲一定用传入的有效 URL

                        withContext(Dispatchers.Main) {
                            p.clearMediaItems()
                            playlist.forEachIndexed { i, s ->
                                p.addMediaItem(buildMediaItem(s, urlMap[i] ?: ""))
                            }
                            p.prepare()
                            p.seekTo(idx, 0)
                            currentIndex = idx
                            p.playWhenReady = true
                            p.play()
                        }
                    }
                }
            }
        }
    }

    /** 将歌曲添加到当前播放列表末尾（不立即播放） */
    fun addToQueue(song: Song, playUrl: String) {
        urlCache[song.rid] = playUrl
        currentPlaylist = currentPlaylist + song
        player?.let { p ->
            p.addMediaItem(buildMediaItem(song, playUrl))
        }
    }

    /** 将歌曲添加到队列末尾并立即播放 */
    fun appendToQueueAndPlay(song: Song, playUrl: String) {
        urlCache[song.rid] = playUrl
        currentPlaylist = currentPlaylist + song
        val newIndex = currentPlaylist.size - 1
        player?.let { p ->
            p.addMediaItem(buildMediaItem(song, playUrl))
            if (!p.isPlaying) {
                p.seekTo(newIndex, 0)
                p.play()
            }
        }
    }

    private fun buildMediaItem(song: Song, url: String): MediaItem {
        return MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(android.net.Uri.parse(song.pic))
                    .build()
            )
            .build()
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                // ★ 修复：ENDED 状态后 playWhenReady 可能为 false，显式设为 true 再播放
                it.playWhenReady = true
                it.play()
            }
        }
    }

    fun seekTo(position: Long) { player?.seekTo(position) }

    fun seekToNext() {
        val p = player ?: return
        val count = p.mediaItemCount
        if (count == 0) return
        val idx = p.currentMediaItemIndex
        Log.d("ZongTing", "seekToNext: mediaItemCount=$count, currentIndex=$idx, currentPlaylist.size=${currentPlaylist.size}")
        if (idx == count - 1) {
            // ★ 循环：最后一首 → 切到第一首
            Log.d("ZongTing", "  -> wrap to first (index 0)")
            p.seekTo(0, 0)
        } else {
            p.seekToNextMediaItem()
        }
    }

    fun seekToPrevious() {
        val p = player ?: return
        val count = p.mediaItemCount
        if (count == 0) return
        val idx = p.currentMediaItemIndex
        Log.d("ZongTing", "seekToPrevious: mediaItemCount=$count, currentIndex=$idx, currentPlaylist.size=${currentPlaylist.size}")
        if (idx == 0) {
            // ★ 循环：第一首 → 切到最后一首
            Log.d("ZongTing", "  -> wrap to last (index ${count - 1})")
            p.seekTo(count - 1, 0)
        } else {
            p.seekToPreviousMediaItem()
        }
    }

    fun getPlayer(): Player? = player

    fun release() {
        scope.cancel()
        player?.release()
        mediaSession?.release()
        player = null
        mediaSession = null
    }
}
