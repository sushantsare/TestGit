package com.sushant.tarang.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** Small shared state between the playback service and the screens (same process). */
class PlaybackHub {
    /** Shuffle on/off. Shuffle really reorders the queue (current song first), so Up next always shows what plays. */
    val shuffle = MutableStateFlow(false)
    /** "Bollywood" in "Playing from folder · Bollywood". */
    val contextKind = MutableStateFlow("Folder")
    val contextLabel = MutableStateFlow("")
    /** Sleep timer: wall-clock end, or 0; or stop after the current song. */
    val sleepEndsAt = MutableStateFlow(0L)
    val sleepAfterSong = MutableStateFlow(false)
    val sleepFade = MutableStateFlow(true)
    /** True once the user pressed play since the app started (Home hides its "Continue listening" card then). */
    val playedThisSession = MutableStateFlow(false)
    /** Last error shown as a snackbar (e.g. a file that can't be played). */
    val error = MutableStateFlow<String?>(null)
}

object Cmd {
    const val LIKE = "tarang.LIKE"
    const val SHUFFLE = "tarang.SHUFFLE"
    const val REPEAT = "tarang.REPEAT"
    const val SLEEP = "tarang.SLEEP"                   // args "minutes" (0 = cancel), "afterSong", "fade"
    const val END_QUEUE = "tarang.END_QUEUE"
    const val CONTEXT = "tarang.CONTEXT"               // args "kind", "label"
    const val CLEAR_QUEUE = "tarang.CLEAR_QUEUE"       // removes everything except the song playing now
    const val SET_SHUFFLE = "tarang.SET_SHUFFLE"       // arg "mode": "toggle" | "apply" (shuffle the new list) | "off" (forget shuffle, keep order)
}
