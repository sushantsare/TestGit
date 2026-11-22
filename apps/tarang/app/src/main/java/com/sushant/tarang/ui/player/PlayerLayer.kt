@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.sushant.tarang.ui.components.PlayPauseGlyph
import com.sushant.tarang.ui.components.rememberPlayMorph
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.Song
import com.sushant.tarang.data.formatDuration
import com.sushant.tarang.playback.PlayerUi
import com.sushant.tarang.ui.LocalPlayerActions
import com.sushant.tarang.ui.components.Cover
import com.sushant.tarang.ui.components.LocalCovers
import com.sushant.tarang.ui.theme.CoverColors
import com.sushant.tarang.ui.theme.LocalIsDark
import com.sushant.tarang.ui.theme.coverColors
import com.sushant.tarang.ui.theme.SystemBarIcons
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 0 = mini player, 1 = full player. Driven by the finger while dragging, then settles with a spring. */
class SheetState {
    val progress = Animatable(0f)
    val panel = Animatable(0f)     // Up next / Lyrics / Info panel: 0 = peek, 1 = open
    var tab by mutableIntStateOf(0)
    val isFull get() = progress.value > 0.5f
}

@Composable fun rememberSheetState() = remember { SheetState() }

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpR(a: Rect, b: Rect, t: Float) = Rect(lerpF(a.left, b.left, t), lerpF(a.top, b.top, t), lerpF(a.right, b.right, t), lerpF(a.bottom, b.bottom, t))
private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Where every flying piece sits in the mini player and in the full player (pixels in this layer). */
private class Geometry(
    val mini: Rect, val full: Rect,
    val coverMini: Rect, val coverFull: Rect,
    val titleMini: Offset2, val titleFull: Offset2, val titleWMini: Float, val titleWFull: Float,
    val playMini: Rect, val playFull: Rect, val prevMini: Rect, val prevFull: Rect, val nextMini: Rect, val nextFull: Rect,
    val seekY: Float, val controlsY: Float, val extrasY: Float, val paneW: Float, val twoPane: Boolean, val topY: Float, val peekH: Float,
    val shuffleCx: Float, val repeatCx: Float,
) : PanelGeo {
    override val fullH get() = full.height
    override val fullW get() = full.width
    override val panelTopY get() = topY
    override val panelPeek get() = peekH
    override val panelTwoPane get() = twoPane
    override val panelPaneW get() = paneW
}
private data class Offset2(val x: Float, val y: Float)

@Composable
fun PlayerLayer(
    state: SheetState,
    ui: PlayerUi,
    song: Song?,
    liked: Boolean,
    navVisible: Boolean,
    wide: Boolean,
    contextLine: Pair<String, String>,
    sleepActive: Boolean,
    onOpenMenu: () -> Unit,
    onSleep: () -> Unit, onSpeed: () -> Unit, onSound: () -> Unit, onPlaylist: () -> Unit,
    navBar: @Composable (Modifier) -> Unit,
) {
    val act = LocalPlayerActions.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dark = LocalIsDark.current
    val fallback = MaterialTheme.colorScheme
    val covers = LocalCovers.current
    val seed by produceState<Int?>(null, song?.id) { value = song?.let { covers.seed(it) } }
    val target = remember(seed, dark, fallback) { coverColors(seed, dark, fallback) }
    val colors = animateCoverColors(target)

    SystemBarIcons(light = if (state.progress.value > 0.5f && song != null) colors.full.luminance() > 0.5f else !dark)

    BackHandler(enabled = state.progress.value > 0.5f) {
        scope.launch { if (state.panel.value > 0.5f) state.panel.animateTo(0f, spring()) else state.progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f)) }
    }
    // the Up next panel always rests fully open or fully closed, and is closed whenever the player is not full
    LaunchedEffect(state.progress.isRunning, state.progress.targetValue) {
        if (!state.progress.isRunning && state.progress.value < 0.5f && state.panel.value != 0f) state.panel.snapTo(0f)
    }

    // ---------- vertical drag: open from the mini player, close from anywhere on the full player
    var range by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val drag = remember { DragTrack() }
    fun dragStart() { drag.start = state.progress.value; drag.pos = drag.start }
    fun dragBy(d: Float) {
        drag.pos = clamp01(drag.pos - d / range)
        val v = drag.pos
        drag.job = scope.launch { state.progress.snapTo(v) }   // applied in order; settle waits for the last one
    }
    val dragState = rememberDraggableState { d -> dragBy(d) }
    val settle: suspend (Float) -> Unit = { v ->
        drag.job?.join()                                      // no late finger update can stop the closing/opening midway
        val moved = drag.pos - drag.start                     // + = towards full, - = towards mini
        // once a swipe is detected it always completes in that direction
        val goFull = when {
            v < -150f -> true
            v > 150f -> false
            moved > 0.02f -> true
            moved < -0.02f -> false
            else -> drag.start > 0.5f                         // barely moved: stay where it was
        }
        state.progress.animateTo(if (goFull) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 380f), initialVelocity = -v / range)
    }
    val collapse = PlayerDrag(
        onStart = { dragStart() },
        onDelta = { d -> dragBy(d) },
        onStop = { v -> scope.launch { settle(v) } },
    )
    val currentId = rememberUpdatedState(ui.currentId)

    // ---------- sideways swipe: cover follows the finger, slides out and the next cover slides in
    val swipeX = remember { Animatable(0f) }
    // ---------- record-style spin of the round mini-player cover
    val spin = remember { Animatable(0f) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val canSpin = remember {
        val scale = android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        scale > 0f && !pm.isPowerSaveMode
    }
    LaunchedEffect(ui.isPlaying, canSpin) {
        if (!canSpin) { spin.snapTo(0f); return@LaunchedEffect }
        if (ui.isPlaying) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                spin.snapTo((spin.value + (now - last) / 1e9f * 36f) % 360f)   // one turn every 10 s
                last = now
            }
        } else spin.animateTo(spin.value + 8f, tween(600, easing = androidx.compose.animation.core.LinearOutSlowInEasing))   // ease to a stop
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            // full player: a downward swipe anywhere closes it (children that need the gesture, like the seek bar, use it first)
            .then(if (song != null && (state.progress.value > 0.001f || dragging)) Modifier.draggable(
                dragState, Orientation.Vertical, enabled = state.panel.value < 0.05f,
                onDragStarted = { dragging = true; dragStart() },
                onDragStopped = { v -> dragging = false; settle(v) },
            ) else Modifier)
    ) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val insets = WindowInsets.systemBars.asPaddingValues()
        val statusTop = with(density) { insets.calculateTopPadding().toPx() }
        val navBottom = with(density) { insets.calculateBottomPadding().toPx() }
        fun dp(v: Float) = with(density) { v.dp.toPx() }
        val g = remember(W, H, statusTop, navBottom, navVisible, wide) { geometry(W, H, statusTop, navBottom, navVisible, wide, ::dp) }
        val p = state.progress.value
        val t = clamp01(p / 0.85f)                    // pieces land exactly on their spots before the end
        val fullAlpha = clamp01((p - 0.55f) / 0.45f)   // things only the full player has
        range = (g.mini.top - g.full.top).coerceAtLeast(1f)

        // ---------- the nav bar (compact screens): slides away as the player opens
        if (navVisible) {
            val navShift = (dp(68f) + dp(8f) + navBottom) * p
            navBar(Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = navShift; alpha = 1f - p })
        }
        if (song == null) return@BoxWithConstraints

        val box = lerpR(g.mini, g.full, t)
        val bottomR = if (navVisible) 10f else 24f
        val r1 = lerpF(24f, 0f, t); val r2 = lerpF(bottomR, 0f, t)
        val bg = lerp(colors.mini, colors.full, t)
        val swipeW = lerpF(g.mini.width, W, t)
        val swipeMod = Modifier.songSwipe(swipeX, swipeW, scope, { dir ->
            val before = currentId.value
            val changed = if (dir < 0) act.swipeNext() else act.swipePrevious()
            if (!changed) { act.toast(if (dir < 0) "This is the last song in the queue" else "This is the first song in the queue"); false }
            else {
                // wait until the player has really moved on, so the new cover (not the old one) slides in
                kotlinx.coroutines.withTimeoutOrNull(2500) { snapshotFlow { currentId.value }.first { it != before } }
                true
            }
        })

        // ---------- the sheet box (mini player background → full player background)
        Box(
            Modifier
                .offset { IntOffset(box.left.toInt(), box.top.toInt()) }
                .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                .shadow(if (t < 0.02f) 8.dp else 0.dp, RoundedCornerShape(r1.dp, r1.dp, r2.dp, r2.dp))
                .clip(RoundedCornerShape(r1.dp, r1.dp, r2.dp, r2.dp))
                .background(bg)
                .draggable(dragState, Orientation.Vertical, enabled = state.panel.value < 0.05f, onDragStarted = { dragStart() }, onDragStopped = { v -> settle(v) })
                .then(if (p < 0.05f) Modifier.clickable(onClickLabel = "Open player") { scope.launch { state.progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) } }.then(swipeMod) else Modifier)
                .testTag(if (p < 0.5f) "mini_player" else "full_player")
        ) {
            // mini player's own progress fill (the whole mini player is the progress bar)
            val fillAlpha = clamp01(1f - t * 2.5f)
            if (fillAlpha > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(ui.progress).alpha(fillAlpha).background(colors.miniFill).testTag("mini_fill"))
        }

        // ---------- full-player-only content (fades in late)
        if (fullAlpha > 0f) FullChrome(g, colors, ui, song, liked, contextLine, sleepActive, fullAlpha, state, scope, onOpenMenu, onSleep, onSpeed, onSound, onPlaylist, ::dp)

        // ---------- flying pieces (cover and title follow a sideways swipe)
        val sx = swipeX.value
        val swipeAlpha = 1f - (abs(sx) / swipeW.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.9f
        val cover = lerpR(g.coverMini, g.coverFull, t)
        val coverR = lerpF(g.coverMini.width / 2f, dp(32f), t)
        // in the mini player the sliding pieces are clipped to the capsule (and stop short of the buttons)
        val miniClip = if (t < 0.5f) Rect(box.left, box.top, lerpR(g.prevMini, g.prevFull, t).left - dp(4f), box.bottom) else null
        val clipR = dp(r1); val clipRB = dp(r2)
        fun Modifier.inMini(pieceLeft: Float, pieceTop: Float): Modifier = if (miniClip == null) this else this.drawWithContent {
            val path = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    miniClip.left - pieceLeft, miniClip.top - pieceTop, miniClip.right - pieceLeft, miniClip.bottom - pieceTop,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(clipR), bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(clipRB),
                ))
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        }
        Box(
            Modifier.offset { IntOffset(cover.left.toInt(), cover.top.toInt()) }
                .size(with(density) { cover.width.toDp() })
                .inMini(cover.left, cover.top)
                .graphicsLayer {
                    translationX = sx; alpha = swipeAlpha
                    rotationZ = spin.value * (1f - t)                       // spins as a record in the mini player, unwinds while opening
                    val k = 1f - (abs(sx) / swipeW.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.12f; scaleX = k; scaleY = k
                }
                .then(if (p > 0.5f) swipeMod.pointerInput(song.id) { detectTapGestures(onDoubleTap = { act.like(song.id) }) } else Modifier)
                .semantics { contentDescription = "Album cover of ${song.title}" }
                .testTag("player_cover")
        ) { Cover(song, with(density) { cover.width.toDp() }, RoundedCornerShape(with(density) { coverR.toDp() })) }

        val tx = lerpF(g.titleMini.x, g.titleFull.x, t); val ty = lerpF(g.titleMini.y, g.titleFull.y, t)
        val tw = lerpF(g.titleWMini, g.titleWFull, t)
        Column(Modifier.offset { IntOffset(tx.toInt(), ty.toInt()) }.width(with(density) { tw.toDp() }).inMini(tx, ty).graphicsLayer { translationX = sx * 0.6f; alpha = swipeAlpha }) {
            Text(song.title, color = lerp(colors.onMini, colors.onFull, t), fontSize = lerpF(15f, 26f, t).sp, lineHeight = lerpF(20f, 32f, t).sp,
                fontWeight = if (t > 0.5f) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("player_title"))
            // artist first (it gets the space), album after it; no stray dot when either is missing
            val artistColor = lerp(colors.onMiniVariant, colors.onFull.copy(alpha = 0.8f), t)
            val albumA = clamp01((t - 0.3f) / 0.7f)
            Text(artistLine(song, artistColor, colors.onFull.copy(alpha = 0.8f * albumA), showAlbum = albumA > 0f), fontSize = lerpF(13f, 16f, t).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("player_artist"))
        }

        RoundBtn(lerpR(g.prevMini, g.prevFull, t), colors.chip,
            lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipPrevious, "Previous", lerpF(20f, 28f, t), CircleShape, "btn_prev") { act.previous() }
        val play = lerpR(g.playMini, g.playFull, t)
        val (morph, round) = rememberPlayMorph(ui.isPlaying)
        val squareR = lerpF(dp(16f), dp(28f), t)
        val playR = squareR + (minOf(play.width, play.height) / 2 - squareR) * round
        Box(
            Modifier.offset { IntOffset(play.left.toInt(), play.top.toInt()) }
                .size(with(density) { play.width.toDp() }, with(density) { play.height.toDp() })
                .clip(RoundedCornerShape(with(density) { playR.toDp() })).background(colors.accent)
                .clickable(onClickLabel = if (ui.isPlaying) "Pause" else "Play") { act.togglePlay() }
                .semantics { contentDescription = if (ui.isPlaying) "Pause" else "Play" }.testTag("btn_play"),
            contentAlignment = Alignment.Center,
        ) { PlayPauseGlyph(morph, colors.onAccent, lerpF(24f, 40f, t).dp) }
        RoundBtn(lerpR(g.nextMini, g.nextFull, t), colors.chip,
            lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipNext, "Next", lerpF(20f, 28f, t), CircleShape, "btn_next") { act.next() }

        // ---------- Up next / Lyrics / Info panel (full player only)
        if (fullAlpha > 0f) Panel(g, colors, ui, song, state, fullAlpha, scope, collapse, ::dp)
    }
}

/** Lets the panel's bottom strip pass a downward swipe on to "close the player". */
class PlayerDrag(val onStart: () -> Unit, val onDelta: (Float) -> Unit, val onStop: (Float) -> Unit)

/** Where a vertical drag started and where the finger has taken it (updated at once, not after a frame). */
private class DragTrack { var start = 0f; var pos = 0f; var job: kotlinx.coroutines.Job? = null }

/** "Artist · Album" with the artist first; missing parts are left out (no stray separators). */
fun artistLine(song: Song, artistColor: Color, albumColor: Color, showAlbum: Boolean): androidx.compose.ui.text.AnnotatedString {
    val artist = song.artist.takeIf { it.isNotBlank() && it != com.sushant.tarang.data.UNKNOWN_ARTIST }
    val album = song.album.takeIf { it.isNotBlank() && it != com.sushant.tarang.data.UNKNOWN_ALBUM }
    return androidx.compose.ui.text.buildAnnotatedString {
        when {
            artist != null -> {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = artistColor)); append(artist); pop()
                if (showAlbum && album != null) { pushStyle(androidx.compose.ui.text.SpanStyle(color = albumColor)); append(" · $album"); pop() }
            }
            album != null -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = artistColor)); append(album); pop() }
            else -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = artistColor)); append(com.sushant.tarang.data.UNKNOWN_ARTIST); pop() }
        }
    }
}

/**
 * Sideways swipe to change song: the cover follows the finger; past a quarter of the width (or a flick) it slides out,
 * the song really changes, and the new song's cover slides in from the other side. A short swipe springs back.
 * [change] gets -1 for next, +1 for previous and returns false when there is no song that way.
 */
private fun Modifier.songSwipe(x: Animatable<Float, AnimationVector1D>, width: Float, scope: kotlinx.coroutines.CoroutineScope,
                               onChange: suspend (Int) -> Boolean): Modifier = this.pointerInput(width) {
    val tracker = VelocityTracker()
    detectHorizontalDragGestures(
        onDragStart = { tracker.resetTracking() },
        onDragEnd = {
            val v = tracker.calculateVelocity().x
            val dx = x.value
            val goNext = dx < -width * 0.25f || v < -1000f
            val goPrev = dx > width * 0.25f || v > 1000f
            scope.launch {
                if (goNext || goPrev) {
                    val dir = if (goNext) -1 else 1
                    x.animateTo(dir * width, tween(170))
                    if (onChange(dir)) {
                        x.snapTo(-dir * width * 0.6f)
                        x.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 360f))
                    } else x.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f))
                } else x.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
            }
        },
        onDragCancel = { scope.launch { x.animateTo(0f, spring()) } },
    ) { change, d ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        scope.launch { x.snapTo(x.value + d) }
    }
}

@Composable
private fun RoundBtn(r: Rect, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, iconSize: Float,
                     shape: androidx.compose.ui.graphics.Shape, tag: String, onClick: () -> Unit) {
    val d = LocalDensity.current
    Box(
        Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }
            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
            .clip(shape).background(bg)
            .clickable(onClickLabel = desc, onClick = onClick)
            .semantics { contentDescription = desc }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize.dp)) }
}

private fun geometry(W: Float, H: Float, statusTop: Float, navBottom: Float, navVisible: Boolean, wide: Boolean, dp: (Float) -> Float): Geometry {
    val miniW = min(W - dp(32f), dp(580f)).let { if (wide) min(it, dp(580f)) else it }
    val miniX = (W - miniW) / 2f
    val miniBottomGap = if (navVisible) navBottom + dp(4f) + dp(68f) + dp(4f) else navBottom + dp(14f)
    val miniY = H - miniBottomGap - dp(72f)
    val mini = Rect(miniX, miniY, miniX + miniW, miniY + dp(72f))
    val full = Rect(0f, 0f, W, H)
    val twoPane = W > H || W >= dp(840f)
    val paneW = if (twoPane) W * 0.5f else W
    val peekH = if (twoPane) 0f else dp(64f) + navBottom
    val topY = statusTop
    // full layout, top to bottom
    val fixed = dp(56f) + dp(10f) + dp(22f) + dp(34f) + dp(30f) + dp(58f) + dp(80f) + dp(24f) + dp(68f) + dp(16f)
    val avail = H - statusTop - fixed - peekH
    val coverSize = min(paneW - dp(72f), avail).coerceIn(dp(120f), dp(420f))
    val coverX = (paneW - coverSize) / 2f
    val contentH = fixed + coverSize
    val startY = if (twoPane) statusTop + max(0f, (H - statusTop - navBottom - contentH) / 2f) else statusTop
    val coverY = startY + dp(56f) + dp(10f)
    val titleY = coverY + coverSize + dp(22f)
    val seekY = titleY + dp(34f) + dp(30f)
    val controlsY = seekY + dp(58f)
    // one evenly spaced row: shuffle · prev · PLAY · next · repeat (equal gaps between what you see)
    val playW = dp(104f); val playH = dp(80f)
    val side = dp(56f); val small = dp(24f); val margin = dp(24f)
    val gap = ((paneW - 2 * margin - (2 * small + 2 * side + playW)) / 4f).coerceAtLeast(dp(6f))
    val rowW = 2 * small + 2 * side + playW + 4 * gap
    val rowX = (paneW - rowW) / 2f
    val shuffleCx = rowX + small / 2
    val prevX = rowX + small + gap
    val playX = prevX + side + gap
    val nextX = playX + playW + gap
    val repeatCx = nextX + side + gap + small / 2
    val sideY = controlsY + (playH - side) / 2
    val prevFull = Rect(prevX, sideY, prevX + side, sideY + side)
    val nextFull = Rect(nextX, sideY, nextX + side, sideY + side)
    // mini layout (old Tarang): round cover 52, title, then prev 40 · play 48 · next 40
    val right = mini.right - dp(12f)
    val nextMini = Rect(right - dp(40f), mini.top + dp(16f), right, mini.top + dp(56f))
    val playMini = Rect(nextMini.left - dp(8f) - dp(48f), mini.top + dp(12f), nextMini.left - dp(8f), mini.top + dp(60f))
    val prevMini = Rect(playMini.left - dp(8f) - dp(40f), mini.top + dp(16f), playMini.left - dp(8f), mini.top + dp(56f))
    return Geometry(
        mini, full,
        Rect(mini.left + dp(10f), mini.top + dp(10f), mini.left + dp(62f), mini.top + dp(62f)),
        Rect(coverX, coverY, coverX + coverSize, coverY + coverSize),
        Offset2(mini.left + dp(76f), mini.top + dp(13f)), Offset2(dp(24f), titleY),
        prevMini.left - dp(8f) - (mini.left + dp(76f)), paneW - dp(24f) - dp(64f),
        playMini, Rect(playX, controlsY, playX + playW, controlsY + playH), prevMini, prevFull, nextMini, nextFull,
        seekY, controlsY, controlsY + playH + dp(24f), paneW, twoPane, startY, peekH,
        shuffleCx, repeatCx,
    )
}

@Composable
private fun animateCoverColors(t: CoverColors): CoverColors {
    val spec = tween<Color>(450)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "cc").value
    return CoverColors(a(t.full), a(t.onFull), a(t.accent), a(t.onAccent), a(t.chip), a(t.onChip), a(t.mini), a(t.miniFill), a(t.onMini),
        a(t.onMiniVariant), a(t.panel), a(t.onPanel), a(t.onPanelVariant), a(t.outline))
}

/** Top bar, heart, wavy seek bar, shuffle/repeat and the Sleep · Speed · Sound · Playlist row. */
@Composable
private fun FullChrome(
    g: Geometry, c: CoverColors, ui: PlayerUi, song: Song, liked: Boolean, contextLine: Pair<String, String>, sleepActive: Boolean,
    alpha: Float, state: SheetState, scope: kotlinx.coroutines.CoroutineScope, onOpenMenu: () -> Unit, onSleep: () -> Unit, onSpeed: () -> Unit, onSound: () -> Unit, onPlaylist: () -> Unit,
    dp: (Float) -> Float,
) {
    val act = LocalPlayerActions.current
    val d = LocalDensity.current
    val paneW = with(d) { g.paneW.toDp() }
    val topAlpha = 1f - state.panel.value
    Box(Modifier.fillMaxSize().alpha(alpha)) {
        // top bar
        if (topAlpha > 0.02f || g.twoPane) Row(Modifier.offset { IntOffset(0, g.topY.toInt()) }.width(paneW).height(56.dp).alpha(if (g.twoPane) 1f else topAlpha).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { state.progress.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 380f)) } }, modifier = Modifier.testTag("collapse")) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimise player", tint = c.onFull)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM ${contextLine.first.uppercase()}", color = c.onFull.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(contextLine.second.ifEmpty { "Your music" }, color = c.onFull, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpenMenu, modifier = Modifier.testTag("player_menu")) { Icon(Icons.Rounded.MoreVert, "More options", tint = c.onFull) }
        }
        // heart next to the title
        val heartY = with(d) { (g.coverFull.bottom + dp(22f)).toDp() }
        IconButton(onClick = { act.like(song.id) }, modifier = Modifier.offset(x = paneW - 64.dp, y = heartY - 4.dp).testTag("like")) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like", tint = c.accent, modifier = Modifier.size(28.dp))
        }
        // seek bar + times
        Column(Modifier.offset { IntOffset(0, g.seekY.toInt()) }.width(paneW).padding(horizontal = 20.dp)) {
            WavySeekBar(ui.positionMs, ui.durationMs, ui.isPlaying, c.accent, c.onFull.copy(alpha = 0.2f), onSeek = { act.seekTo(it) },
                modifier = Modifier.fillMaxWidth().height(28.dp).testTag("seek"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Text(formatDuration(ui.positionMs), color = c.onFull, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(ui.durationMs), color = c.onFull, style = MaterialTheme.typography.labelMedium)
            }
        }
        // shuffle & repeat at the ends of the evenly spaced row; a dot underneath when on
        val ctrlY = with(d) { (g.controlsY + dp(40f)).toDp() } - 24.dp
        ToggleIcon(with(d) { g.shuffleCx.toDp() }, ctrlY, Icons.Rounded.Shuffle, ui.shuffle,
            if (ui.shuffle) "Shuffle on" else "Shuffle off", c, "shuffle", act.toggleShuffle)
        ToggleIcon(with(d) { g.repeatCx.toDp() }, ctrlY, if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            ui.repeat != Player.REPEAT_MODE_OFF,
            when (ui.repeat) { Player.REPEAT_MODE_ONE -> "Repeat this song"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" }, c, "repeat", act.cycleRepeat)
        // Sleep · Speed · Sound · Playlist
        Row(Modifier.offset { IntOffset(0, g.extrasY.toInt()) }.width(paneW).padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Extra(Icons.Rounded.Bedtime, if (sleepActive) "Sleep on" else "Sleep", c, sleepActive, "extra_sleep", onSleep)
            Extra(Icons.Rounded.Speed, if (ui.speed != 1f) "%.2gx".format(ui.speed) else "Speed", c, ui.speed != 1f, "extra_speed", onSpeed)
            Extra(Icons.Rounded.Tune, "Sound", c, false, "extra_sound", onSound)
            Extra(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist", c, false, "extra_playlist", onPlaylist)
        }
    }
}

@Composable
private fun Extra(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, c: CoverColors, on: Boolean, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp).testTag(tag)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (on) c.accent else c.chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (on) c.onAccent else c.onFull, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = c.onFull, style = MaterialTheme.typography.labelMedium)
    }
}


@Composable
private fun ToggleIcon(cx: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean,
                       desc: String, c: CoverColors, tag: String, onClick: () -> Unit) {
    Box(Modifier.offset(x = cx - 24.dp, y = y).size(48.dp, 56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = desc, onClick = onClick)
        .semantics { contentDescription = desc }.testTag(tag), contentAlignment = Alignment.TopCenter) {
        Icon(icon, null, tint = if (on) c.accent else c.onFull.copy(alpha = 0.85f), modifier = Modifier.padding(top = 12.dp).size(26.dp))
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(5.dp).clip(CircleShape).background(c.accent))
    }
}
