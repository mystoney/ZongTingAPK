package com.zongting.zongting.ui.player

import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.model.UserPlaylist
import com.zongting.zongting.ui.LyricState
import com.zongting.zongting.ui.PlaybackState

/**
 * Read-only snapshot of everything the PlayerScreen family of composables
 * needs to render. Built by [PlayerRoute] from MainViewModel's StateFlows
 * once, then passed down — layouts and components don't touch the ViewModel
 * directly. See [PlayerActions] for the callback counterpart.
 *
 * Why a single data class:
 *  - 3 layouts (portrait/landscape/pad) and 6 components would otherwise
 *    each need to declare 8-10 props. One state object is testable in
 *    isolation and shrinks signatures to 3-4 params.
 *  - Keeps a clean separation: this is the *what*, [PlayerActions] is
 *    the *what to do*.
 */
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackState = PlaybackState(),
    val lyricState: LyricState = LyricState.Idle,
    val currentPlaylist: List<Song> = emptyList(),
    val favoriteSet: Set<Long> = emptySet(),
    val playMode: Int = 0,
    val userPlaylists: List<UserPlaylist> = emptyList(),
    val isTimerActive: Boolean = false,
    val timerRemaining: Long = 0L,
) {
    /** Convenience: is the current song marked as a favorite? */
    val isFavorite: Boolean
        get() = currentSong?.let { favoriteSet.contains(it.rid) } ?: false

    /** True if the player has an active song to render against. */
    val hasSong: Boolean
        get() = currentSong != null

    companion object {
        /** Default state — useful for @Preview composables. */
        val Empty = PlayerUiState()
    }
}
