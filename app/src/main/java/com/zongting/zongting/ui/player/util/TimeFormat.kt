package com.zongting.zongting.ui.player.util

/**
 * Time-formatting helpers used across the player UI.
 *
 * Behaviour is intentionally identical to the legacy formatter in the
 * old PlayerScreen.kt — `MM:SS` for any non-negative millisecond
 * value. Kept as a separate file so callers don't have to import
 * the entire PlayerScreen just to format a duration.
 *
 * [formatTime] is a no-op alias of [formatDuration] that used to
 * exist as a separate definition in PlayerScreen.kt; it stays here
 * for source-level compatibility.
 */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatTime(ms: Long): String = formatDuration(ms)
