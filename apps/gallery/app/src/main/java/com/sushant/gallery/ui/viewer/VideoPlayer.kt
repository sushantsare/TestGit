package com.sushant.gallery.ui.viewer

import android.net.Uri
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sushant.gallery.R

/** Plays one video with the standard controls. Pauses when the app goes to the background. */
@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); player.release() }
    }
    AndroidView(
        factory = { c -> (LayoutInflater.from(c).inflate(R.layout.player_view, FrameLayout(c), false) as PlayerView).also { it.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}
