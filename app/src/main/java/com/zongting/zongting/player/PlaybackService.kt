package com.zongting.zongting.player

import android.app.PendingIntent
import android.content.Intent
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

        // 创建 ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 创建 PendingIntent 用于点击通知跳转到 App
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建 MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // 初始化 PlayerManager
        PlayerManager.initialize(player, mediaSession!!)
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

    private var player: ExoPlayer? = null
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

    fun initialize(exoPlayer: ExoPlayer, session: MediaSession) {
        player = exoPlayer
        mediaSession = session

        // 注册播放器监听器
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged?.invoke(playing)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    onPositionChanged?.invoke(pos, dur)
                }
            }

            // ExoPlayer 切换 MediaItem 时触发（自动切歌或用户切换）
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (newIndex < 0 || newIndex >= currentPlaylist.size) return
                val newSong = currentPlaylist[newIndex]
                // 跳过同一首（playlist 重设时也会触发）
                if (newSong.rid == currentPlaylist.getOrNull(currentIndex)?.rid) return

                currentIndex = newIndex
                onSongChanged?.invoke(newSong, newIndex)
                // 如果 URL 已缓存，直接更新 MediaItem 的 URI
                val cachedUrl = urlCache[newSong.rid]
                if (!cachedUrl.isNullOrEmpty()) {
                    replaceMediaItemUri(newIndex, cachedUrl)
                } else if (isFetchingUrlForIndex != newIndex) {
                    // URL 未缓存，暂停，异步获取，获取后更新并继续
                    isFetchingUrlForIndex = newIndex
                    player?.pause()
                    scope.launch {
                        val url = urlFetcher?.invoke(newSong)
                        if (url != null) {
                            urlCache[newSong.rid] = url
                            replaceMediaItemUri(newIndex, url)
                            player?.play()
                        }
                        isFetchingUrlForIndex = -1
                    }
                }
            }
        })
    }

    /**
     * 替换指定位置的 MediaItem URI（通过 remove + add 实现）
     * Media3 Player 有 addMediaItem(int, MediaItem) 但没有 setMediaItem(List)
     */
    private fun replaceMediaItemUri(index: Int, url: String) {
        player?.let { p ->
            if (index == p.currentMediaItemIndex) {
                // 正在播放的这一首，直接用 setMediaItem 替换
                val oldItem = p.currentMediaItem ?: return
                p.setMediaItem(oldItem.buildUpon().setUri(url).build())
                if (p.isPlaying) p.seekTo(p.currentPosition)
            } else if (index < p.mediaItemCount) {
                // 非当前项：用 getMediaItemAt(index) 获取目标项，再用 remove+add 替换
                val targetItem = p.getMediaItemAt(index)
                val savedPos = if (p.isPlaying) p.currentPosition else 0L
                p.removeMediaItem(index)
                p.addMediaItem(index, targetItem.buildUpon().setUri(url).build())
                p.seekTo(p.currentMediaItemIndex, savedPos)
            }
        }
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        currentPlaylist = songs
        currentIndex = startIndex
    }

    fun playSong(song: Song, playUrl: String, playlist: List<Song> = listOf(song)) {
        val idx = if (playlist.size > 1) playlist.indexOf(song).coerceAtLeast(0) else 0
        currentPlaylist = playlist
        currentIndex = idx
        isFetchingUrlForIndex = -1
        urlCache[song.rid] = playUrl

        player?.let { p ->
            // 用第一个 MediaItem（真实 URL）初始化，然后 addMediaItem 追加其余
            val firstMediaItem = buildMediaItem(song, playUrl)
            p.setMediaItem(firstMediaItem)
            // 追加播放列表其余歌曲（用空 URI 占位，之后 onMediaItemTransition 会补上）
            playlist.forEachIndexed { i, s ->
                if (i != idx) {
                    val url = urlCache[s.rid] ?: ""
                    p.addMediaItem(buildMediaItem(s, url))
                }
            }
            p.seekTo(idx, 0)
            p.prepare()
            p.play()

            // 预取后续几首歌的 URL（异步，不阻塞播放）
            if (playlist.size > 1) {
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
        if (currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            val song = currentPlaylist[currentIndex]
            val cachedUrl = urlCache[song.rid]
            if (!cachedUrl.isNullOrEmpty()) {
                replaceMediaItemUri(currentIndex, cachedUrl)
            }
            player?.next()
        }
    }

    fun seekToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            val song = currentPlaylist[currentIndex]
            val cachedUrl = urlCache[song.rid]
            if (!cachedUrl.isNullOrEmpty()) {
                replaceMediaItemUri(currentIndex, cachedUrl)
            }
            player?.previous()
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
