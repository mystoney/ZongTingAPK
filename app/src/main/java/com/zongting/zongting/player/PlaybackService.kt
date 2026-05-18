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
    private var currentPlaylist: List<com.zongting.zongting.data.model.Song> = emptyList()
    private var currentIndex: Int = 0
    private val urlCache = mutableMapOf<Long, String>()  // rid -> playUrl

    // 播放状态监听器
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onPositionChanged: ((Long, Long) -> Unit)? = null

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    val currentPosition: Long
        get() = player?.currentPosition ?: 0L

    val duration: Long
        get() = player?.duration ?: 0L

    val currentSong: com.zongting.zongting.data.model.Song?
        get() = currentPlaylist.getOrNull(currentIndex)

    fun initialize(exoPlayer: ExoPlayer, session: MediaSession) {
        player = exoPlayer
        mediaSession = session

        // 注册播放器监听器，实时同步状态
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
        })
    }

    fun setPlaylist(songs: List<com.zongting.zongting.data.model.Song>, startIndex: Int = 0) {
        currentPlaylist = songs
        currentIndex = startIndex
    }

    fun playSong(song: com.zongting.zongting.data.model.Song, playUrl: String, playlist: List<com.zongting.zongting.data.model.Song> = listOf(song)) {
        if (playlist.size > 1) {
            currentPlaylist = playlist
            currentIndex = playlist.indexOf(song).coerceAtLeast(0)
        } else {
            currentPlaylist = playlist
            currentIndex = 0
        }

        player?.let { p ->
            val mediaItem = MediaItem.Builder()
                .setUri(playUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.name)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(android.net.Uri.parse(song.pic))
                        .build()
                )
                .build()
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            // 缓存 URL
            urlCache[song.rid] = playUrl
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun seekToNext() {
        if (currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            val song = currentPlaylist[currentIndex]
            urlCache[song.rid]?.let { playSong(song, it) }
        }
    }

    fun seekToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            val song = currentPlaylist[currentIndex]
            urlCache[song.rid]?.let { playSong(song, it) }
        }
    }

    fun getPlayer(): Player? = player

    fun release() {
        player?.release()
        mediaSession?.release()
        player = null
        mediaSession = null
    }
}
