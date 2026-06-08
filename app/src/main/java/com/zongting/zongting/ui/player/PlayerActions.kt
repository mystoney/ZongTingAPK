package com.zongting.zongting.ui.player

import com.zongting.zongting.data.model.Song

/**
 * Callback bundle for PlayerScreen. All user actions that a player
 * layout / component can emit are gathered here so children never have
 * to receive the MainViewModel. The entry-point ([PlayerRoute]) wires
 * each field to the corresponding ViewModel method (or to a static
 * helper like [PlayerManager.pause]).
 *
 * Layouts and components are tested by passing [PlayerActions.Noop] —
 * they don't need a real ViewModel to render.
 *
 * Field naming convention: every field is the *event* from the UI's
 * perspective ("toggle play", "seek"), not the VM method name. This
 * keeps the contract stable if the underlying VM is refactored.
 */
data class PlayerActions(
    /** User pressed the back/up affordance. */
    val onBack: () -> Unit = {},
    /** Tap on the play/pause button. */
    val onTogglePlay: () -> Unit = {},
    /** Cycle play mode (single / loop / shuffle). */
    val onTogglePlayMode: () -> Unit = {},
    /** Toggle favorite for the current song. */
    val onToggleFavorite: () -> Unit = {},
    /** Skip to previous song in playlist. */
    val onPrevious: () -> Unit = {},
    /** Skip to next song in playlist. */
    val onNext: () -> Unit = {},
    /** Final seek after user releases the progress slider. */
    val onSeek: (Long) -> Unit = {},
    /** Live drag update — feed back into PlaybackState.position. */
    val onDrag: (Long) -> Unit = {},
    /** Fetch the lyric for a given song (idempotent). */
    val onFetchLyric: (Song) -> Unit = {},
    /** Tap a song in the queue → play it. */
    val onPlaySong: (Song) -> Unit = {},
    /** Add current song to an existing playlist, with completion callback. */
    val onAddSongToPlaylist: (String, Song, () -> Unit) -> Unit = { _, _, _ -> },
    /** Create a new playlist and add the current song to it. */
    val onCreatePlaylistAndAddSong: (String, Song) -> Unit = { _, _ -> },
    /** Open (true) or close (false) the queue sheet. */
    val onShowPlaylist: (Boolean) -> Unit = {},
    /** Open the "save to playlist" dialog. */
    val onToggleSavePlaylist: () -> Unit = {},
    /** Open the sleep-timer dialog. */
    val onSleepTimerClick: () -> Unit = {},
    /** Open the ringtone cutter for the current song. */
    val onRingtoneCutterClick: () -> Unit = {},
) {
    companion object {
        /** No-op actions for @Preview and unit-test previews. */
        val Noop = PlayerActions()
    }
}
