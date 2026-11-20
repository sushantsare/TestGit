package com.sushant.saveo.ui.viewer

import android.os.Build
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sushant.saveo.R
import com.sushant.saveo.data.Diagnostics
import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.MediaKind
import com.sushant.saveo.ui.MainViewModel
import com.sushant.saveo.ui.Media
import com.sushant.saveo.ui.ViewerSession
import com.sushant.saveo.ui.components.MediaImage
import com.sushant.saveo.ui.components.dpToPx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Frost = Color.White.copy(alpha = 0.24f)

/**
 * Full-screen viewer: the status sits in a rounded card over a blurred copy of its colours.
 * Smooth swiping: HorizontalPager keeps one page on each side composed (already decoded, with the grid
 * thumbnail as instant placeholder), one shared TextureView player is attached only to the settled page,
 * and the background only changes once a swipe has settled.
 */
@Composable
fun ViewerScreen(vm: MainViewModel, session: ViewerSession, inPane: Boolean, modifier: Modifier = Modifier) {
    val items = session.items
    val itemsNow by rememberUpdatedState(items)
    val pager = rememberPagerState(initialPage = session.startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { itemsNow.size }
    val savedKeys by vm.savedKeys.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Media?>(null) }
    val focus = remember { FocusRequester() }
    val thumbPx = dpToPx(190.dp)
    val fullPx = (LocalConfiguration.current.screenWidthDp * LocalContext.current.resources.displayMetrics.density * 1.4f).toInt().coerceIn(600, 1800)

    // one player for the whole viewer
    val player = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var playing by remember { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var firstFrame by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying || player.playWhenReady }
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlayerError(error: PlaybackException) { videoError = true }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0) }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(speed) { player.playbackParameters = PlaybackParameters(speed) }

    // Prepare video only for the page the swipe settled on
    LaunchedEffect(pager.settledPage, items) {
        val m = items.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        firstFrame = false; videoError = false; position = 0; duration = 0; controls = true
        if (m.kind == MediaKind.VIDEO) {
            player.setMediaItem(MediaItem.fromUri(m.uri))
            player.prepare()
            player.playWhenReady = true
            playing = true
        } else {
            player.stop(); player.clearMediaItems(); playing = false
        }
    }
    LaunchedEffect(playing, pager.settledPage) {
        while (playing) { position = player.currentPosition; if (player.duration > 0) duration = player.duration; delay(200) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Labels and buttons follow the page that is mostly on screen; playback and background follow the settled page
    val current = items.getOrNull(pager.currentPage)
    val settled = items.getOrNull(pager.settledPage)

    Box(
        modifier.fillMaxSize().background(Color(0xFF111312))
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(items.size - 1)) }; true }
                    Key.DirectionLeft -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    Key.Spacebar -> {
                        if (settled?.kind == MediaKind.VIDEO) { if (player.isPlaying) player.pause() else player.play() }
                        true
                    }
                    Key.Escape -> { vm.closeViewer(); true }
                    else -> false
                }
            }
            .testTag("viewer"),
    ) {
        // Blurred background of the settled item (changes only after a swipe settles)
        Crossfade(settled?.key, animationSpec = tween(350), label = "bg") { key ->
            val m = items.firstOrNull { it.key == key }
            if (m != null) {
                // Pre-blurred once into a tiny bitmap: no per-frame blur cost while swiping
                MediaImage(m.uri, m.kind, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }, sizePx = 40, blurred = true)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!inPane) FrostButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), Modifier.testTag("viewer_back")) { vm.closeViewer() }
                else Spacer(Modifier.size(48.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current?.let { whenText(it.time) } ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    val type = stringResource(if (current?.kind == MediaKind.VIDEO) R.string.video else R.string.photo)
                    Text("$type · ${current?.source?.label ?: ""} · ${stringResource(R.string.n_of_m, pager.currentPage + 1, items.size)}", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                FrostButton(Icons.Rounded.Info, stringResource(R.string.details)) { details = current }
            }

            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth(),
                key = { itemsNow.getOrNull(it)?.key ?: it }, beyondViewportPageCount = 1,
                pageSpacing = 12.dp, userScrollEnabled = !zoomed,
            ) { page ->
                val m = itemsNow.getOrNull(page) ?: return@HorizontalPager
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().widthIn(max = 640.dp).shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1B1F1D)),
                    ) {
                        if (m.kind == MediaKind.IMAGE) {
                            ZoomableImage(m, fullPx, thumbPx, active = page == pager.settledPage, onZoom = { zoomed = it })
                        } else {
                            MediaImage(m.uri, m.kind, Modifier.fillMaxSize(), sizePx = fullPx, contentScale = ContentScale.Fit, placeholderSizePx = thumbPx)
                            if (page == pager.settledPage && !videoError) {
                                AndroidView(
                                    factory = { c -> (LayoutInflater.from(c).inflate(R.layout.player_texture, android.widget.FrameLayout(c), false) as PlayerView).also { it.player = player } },
                                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (firstFrame) 1f else 0f },
                                    onRelease = { it.player = null },
                                )
                                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) })
                                if (!firstFrame) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White, strokeWidth = 3.dp)
                                androidx.compose.animation.AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                                    VideoControls(
                                        position, duration, playing, muted, loop, speed,
                                        onSeek = { player.seekTo(it); position = it },
                                        onPlayPause = { if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true } },
                                        onMute = { muted = !muted }, onLoop = { loop = !loop },
                                        onSpeed = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f } },
                                    )
                                }
                            }
                            if (page == pager.settledPage && videoError) {
                                Text(stringResource(R.string.video_error), color = Color.White, textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(16.dp))
                            }
                        }
                    }
                }
            }

            // Page dots (or count when many)
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                if (items.size in 2..15) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.indices.forEach { i ->
                        Box(Modifier.size(width = if (i == pager.currentPage) 18.dp else 6.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f)))
                    }
                }
            }

            // Actions
            if (current != null) Row(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (session.fromSaved) {
                    BigPill(stringResource(R.string.share), Icons.Rounded.Share, false, Modifier.weight(1f)) { vm.share(listOf(current)) }
                    FrostButton(Icons.Rounded.Repeat, stringResource(R.string.repost), big = true) { vm.share(listOf(current), repostTo = current.source.pkg ?: "com.whatsapp") }
                    FrostButton(Icons.Rounded.Delete, stringResource(R.string.delete), big = true, modifier = Modifier.testTag("viewer_delete")) {
                        current.saved?.let { s -> vm.viewerRemoved(current.key); vm.delete(listOf(s.id)) }
                    }
                } else {
                    val isSaved = current.status?.matchKey?.let { it in savedKeys } == true
                    val isSaving = current.key in saving
                    BigPill(
                        stringResource(if (isSaved) R.string.saved_check else R.string.save_to_gallery),
                        if (isSaved) Icons.Rounded.Check else Icons.Rounded.Download, isSaved, Modifier.weight(1f).testTag("viewer_save"),
                        busy = isSaving,
                    ) { if (!isSaved) vm.save(current) }
                    FrostButton(Icons.Rounded.Share, stringResource(R.string.share), big = true) { vm.share(listOf(current)) }
                    FrostButton(Icons.Rounded.Repeat, stringResource(R.string.repost), big = true) { vm.share(listOf(current), repostTo = current.source.pkg ?: "com.whatsapp") }
                }
            }
        }
    }

    details?.let { m ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.done)) } },
            title = { Text(stringResource(R.string.details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (m.kind == MediaKind.VIDEO) R.string.video else R.string.photo) + (if (m.kind == MediaKind.VIDEO && duration > 0 && m.key == current?.key) " · " + Formats.duration(duration) else ""))
                    Text(stringResource(R.string.from_source, m.source.label))
                    Text(Diagnostics.fmt(m.time))
                    Text(sizeText(m.sizeBytes))
                    m.saved?.let { Text(stringResource(R.string.saved_on, Diagnostics.fmt(it.savedAt))) }
                }
            },
        )
    }
}

private fun sizeText(b: Long): String = when {
    b >= 1024 * 1024 -> "%.1f MB".format(b / 1024f / 1024f)
    b >= 1024 -> "%d KB".format(b / 1024)
    else -> "$b B"
}

@Composable
private fun whenText(t: Long): String {
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(t))
    return when (Formats.dayGroup(t)) {
        Formats.DayGroup.TODAY -> "${stringResource(R.string.today)}, $time"
        Formats.DayGroup.YESTERDAY -> "${stringResource(R.string.yesterday)}, $time"
        Formats.DayGroup.EARLIER -> java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(t))
    }
}

/** Pinch / double-tap zoom that lets single-finger swipes pass through to the pager while not zoomed. */
@Composable
private fun ZoomableImage(m: Media, fullPx: Int, thumbPx: Int, active: Boolean, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale > 1.01f, active) { if (active) onZoom(scale > 1.01f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { p ->
                    if (scale > 1.01f) { scale = 1f; offset = Offset.Zero }
                    else { scale = 2.5f; offset = Offset((size.width / 2f - p.x) * 1.5f, (size.height / 2f - p.y) * 1.5f) }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1.01f) {
                            val z = event.calculateZoom(); val pan = event.calculatePan()
                            val ns = (scale * z).coerceIn(1f, 5f)
                            val maxX = size.width * (ns - 1) / 2f; val maxY = size.height * (ns - 1) / 2f
                            offset = if (ns <= 1.01f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            scale = ns
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics { contentDescription = "Photo" },
    ) {
        MediaImage(
            m.uri, m.kind,
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            sizePx = fullPx, contentScale = ContentScale.Fit, placeholderSizePx = thumbPx,
        )
    }
}

@Composable
private fun VideoControls(
    position: Long, duration: Long, playing: Boolean, muted: Boolean, loop: Boolean, speed: Float,
    onSeek: (Long) -> Unit, onPlayPause: () -> Unit, onMute: () -> Unit, onLoop: () -> Unit, onSpeed: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Formats.duration(position), color = Color.White, fontSize = 12.sp)
            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (duration > 0) onSeek((it * duration).toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("seek"),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF7EE2AE), inactiveTrackColor = Color.White.copy(alpha = 0.35f)),
            )
            Text(Formats.duration(duration), color = Color.White, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CtlButton(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, stringResource(if (muted) R.string.unmute else R.string.mute), Color.White, onMute)
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), tint = Color(0xFF14804F)) }
            CtlButton(Icons.Rounded.Repeat, stringResource(if (loop) R.string.loop_on else R.string.loop_off), if (loop) Color(0xFF7EE2AE) else Color.White, onLoop)
            val label = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"
            Text(
                label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSpeed)
                    .semantics { contentDescription = "Playback speed $label" }
                    .background(Color.White.copy(alpha = 0.2f), CircleShape).padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun CtlButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint)
    }
}

@Composable
private fun FrostButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(if (big) 56.dp else 48.dp).clip(CircleShape).background(Frost).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White) }
}

@Composable
private fun BigPill(text: String, icon: ImageVector, done: Boolean, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    val bg = if (done) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3CCB89), Color(0xFF148654)))
    else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color.White))
    val fg = if (done) Color.White else Color(0xFF14804F)
    Row(
        modifier.heightIn(min = 56.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.5.dp) else Icon(icon, null, tint = fg)
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}
