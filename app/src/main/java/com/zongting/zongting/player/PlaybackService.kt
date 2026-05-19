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
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val pos = player?.currentPosition ?: 0L
                    val dur = player?.duration ?: 0L
                    if (dur > 0) onPositionChanged?.invoke(pos, dur)
                }
            }

            // ExoPlayer 切换 MediaItem 时触发（自动切歌或用户切换）
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val p = player ?: return
                val newIndex = p.currentMediaItemIndex
                // ★ 修复：playlist 为空时直接跳过，避免 getMediaItemAt crash
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
                // 跳过同一首（playlist 重设时也会触发）
                if (newSong.rid == currentPlaylist.getOrNull(currentIndex)?.rid) {
                    Log.d("ZongTing", "  -> same song, skipping")
                    return
                }

                currentIndex = newIndex
                onSongChanged?.invoke(newSong, newIndex)
                Log.d("ZongTing", "  -> switching to song: ${newSong.name}, rid=${newSong.rid}")
                // 如果 URL 已缓存，直接更新 MediaItem 的 URI
                val cachedUrl = urlCache[newSong.rid]
                if (!cachedUrl.isNullOrEmpty()) {
                    Log.d("ZongTing", "  -> URL cached, replacing media item")
                    // ★ 修复：用 replaceMediaItem 替代 setMediaItem，避免破坏播放列表
                    val updatedItem = p.getMediaItemAt(newIndex).buildUpon().setUri(cachedUrl).build()
                    p.replaceMediaItem(newIndex, updatedItem)
                } else if (isFetchingUrlForIndex != newIndex) {
                    // URL 未缓存，暂停，异步获取，获取后更新并继续
                    isFetchingUrlForIndex = newIndex
                    Log.d("ZongTing", "  -> URL not cached, fetching async, player state before pause: ${p.playbackState}")
                    p.pause()
                    scope.launch {
                        val url = urlFetcher?.invoke(newSong)
                        if (url != null) {
                            Log.d("ZongTing", "  -> URL fetched, updating media item and playing")
                            urlCache[newSong.rid] = url
                            val updatedItem = p.getMediaItemAt(newIndex).buildUpon().setUri(url).build()
                            p.replaceMediaItem(newIndex, updatedItem)
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
                    p.clearMediaItems()
                    playlist.forEachIndexed { i, s ->
                        val url = if (i == idx) playUrl else (urlCache[s.rid] ?: "")
                        p.addMediaItem(buildMediaItem(s, url))
                    }
                    p.playWhenReady = false
                    p.prepare()
                    p.seekTo(idx, 0)
                    currentIndex = idx
                    p.playWhenReady = true
                    p.play()

                    // 预取后续几首歌的 URL
                    scope.launch {
                        playlist.forEachIndexed { i, s ->
                            if (i != idx && !urlCache.containsKey(s.rid)) {
                                val u = urlFetcher?.invoke(s)
                                if (u != null) urlCache[s.rid] = u
                            }
                        }
                    }
                }
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
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(position: Long) { player?.seekTo(position) }

    fun seekToNext() {
        val p = player
        Log.d("ZongTing", "seekToNext: mediaItemCount=${p?.mediaItemCount}, currentIndex=${p?.currentMediaItemIndex}, currentPlaylist.size=${currentPlaylist.size}, currentIndex_var=$currentIndex")
        p?.seekToNextMediaItem()
    }

    fun seekToPrevious() {
        val p = player
        Log.d("ZongTing", "seekToPrevious: mediaItemCount=${p?.mediaItemCount}, currentIndex=${p?.currentMediaItemIndex}, currentPlaylist.size=${currentPlaylist.size}, currentIndex_var=$currentIndex")
        p?.seekToPreviousMediaItem()
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
