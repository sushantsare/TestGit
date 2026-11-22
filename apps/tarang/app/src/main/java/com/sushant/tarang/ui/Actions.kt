package com.sushant.tarang.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi

/** Player actions handed down to the player layer and panels. */
class PlayerActions(
    val next: () -> Unit,
    val previous: () -> Unit,
    val togglePlay: () -> Unit,
    val like: (Long) -> Unit,
    val seekTo: (Long) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val playAt: (Int) -> Unit,
    val removeAt: (Int) -> Unit,
    val move: (Int, Int) -> Unit,
    val switchQueue: (Int) -> Unit,
    val clearQueue: (Int) -> Unit,
    val openLyricsEditor: (Long) -> Unit,
    val openEdit: (Long) -> Unit,
)

val LocalPlayerActions = staticCompositionLocalOf<PlayerActions> { error("no player actions") }

@UnstableApi
val LocalVm = staticCompositionLocalOf<MainVm> { error("no vm") }
