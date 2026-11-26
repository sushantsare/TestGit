@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.media3.common.Player
import com.sushant.tarang.data.Lyrics
import com.sushant.tarang.data.Song
import com.sushant.tarang.data.formatDuration
import com.sushant.tarang.playback.PlayerUi
import com.sushant.tarang.ui.LocalPlayerActions
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.Cover
import com.sushant.tarang.ui.components.LocalCovers
import com.sushant.tarang.ui.components.PlayPauseGlyph
import com.sushant.tarang.ui.components.rememberHaptic
import com.sushant.tarang.ui.components.rememberPlayMorph
import com.sushant.tarang.ui.theme.CoverColors
import com.sushant.tarang.ui.theme.LocalIsDark
import com.sushant.tarang.ui.theme.RoundTitle
import com.sushant.tarang.ui.theme.SystemBarIcons
import com.sushant.tarang.ui.theme.coverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 0 = mini player, 1 = full player. Driven by the finger while dragging, then settles with a spring. */
class SheetState {
    val progress = Animatable(0f)
    /** Up next / Lyrics page over the full player: 0 = closed, 1 = open (the player shrinks into a bar at the top). */
    val panel = Animatable(0f)
    /** 0 = Up next, 1 = Lyrics. */
    var tab by mutableIntStateOf(0)
    val isFull get() = progress.value > 0.5f
}

@Composable fun rememberSheetState() = remember { SheetState() }

internal fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpR(a: Rect, b: Rect, t: Float) = Rect(lerpF(a.left, b.left, t), lerpF(a.top, b.top, t), lerpF(a.right, b.right, t), lerpF(a.bottom, b.bottom, t))
internal fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Quick settle with no bounce (bouncy springs feel laggy on swipes). */
internal fun <T> snapSpec() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Where every moving piece sits: mini player, full player, and the bar at the top while Up next / Lyrics is open. */
private class Geometry(
    val mini: Rect, val full: Rect,
    val coverMini: Rect, val coverFull: Rect, val coverBar: Rect,
    val titleMini: Rect, val titleFull: Rect, val titleBar: Rect,
    val prevMini: Rect, val prevFull: Rect, val prevBar: Rect,
    val playMini: Rect, val playFull: Rect, val playBar: Rect,
    val nextMini: Rect, val nextFull: Rect, val nextBar: Rect,
    val bar: Rect,
    val topY: Float, val seekY: Float, val controlsY: Float, val toolbarY: Float, val lyricY: Float,
    val shuffle: Rect, val repeat: Rect, val heart: Rect,
    val paneW: Float, val twoPane: Boolean, val peekH: Float,
) : PanelGeo {
    override val fullH get() = full.height
    override val fullW get() = full.width
    override val panelTopY get() = topY
    override val panelPeek get() = peekH
    override val panelTwoPane get() = twoPane
    override val panelPaneW get() = paneW
    override val panelOpenTop get() = bar.bottom + 12f
}

@Composable
fun PlayerLayer(
    state: SheetState,
    ui: PlayerUi,
    song: Song?,
    liked: Boolean,
    navVisible: Boolean,
    wide: Boolean,
    sleepActive: Boolean,
    onOpenMenu: () -> Unit,
    onSleep: () -> Unit, onPlaylist: () -> Unit,
    navBar: @Composable (Modifier, Color?) -> Unit,
) {
    val act = LocalPlayerActions.current
    val vm = LocalVm.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dark = LocalIsDark.current
    val fallback = MaterialTheme.colorScheme
    val covers = LocalCovers.current
    val haptic = rememberHaptic()
    val settings by vm.settings.collectAsState()
    val seed by produceState<Int?>(null, song?.id) { value = song?.let { covers.seed(it) } }
    val target = remember(seed, dark, fallback) { coverColors(seed, dark, fallback) }
    val colors = animateCoverColors(target)

    SystemBarIcons(light = if (state.progress.value > 0.5f && song != null) colors.full.luminance() > 0.5f else !dark)

    BackHandler(enabled = state.progress.value > 0.5f) {
        scope.launch { if (state.panel.value > 0.5f) state.panel.animateTo(0f, snapSpec()) else state.progress.animateTo(0f, snapSpec()) }
    }
    // the Up next / Lyrics page is always fully open or closed, and closed whenever the player is not full
    LaunchedEffect(state.progress.isRunning, state.progress.targetValue) {
        if (!state.progress.isRunning && state.progress.value < 0.5f && state.panel.value != 0f) state.panel.snapTo(0f)
    }

    // ---------- ONE owner for the vertical drag between mini and full player
    var range by remember { mutableFloatStateOf(1f) }
    val drag = remember { DragTrack() }
    fun dragStart() { drag.start = state.progress.value; drag.pos = drag.start; drag.active = true }
    fun dragBy(d: Float) {
        drag.pos = clamp01(drag.pos - d / range)
        val v = drag.pos
        drag.job = scope.launch { state.progress.snapTo(v) }   // applied in order; settle waits for the last one
    }
    val dragState = rememberDraggableState { d -> dragBy(d) }
    val settle: suspend (Float) -> Unit = { v ->
        drag.job?.join()                                      // no late finger update can stop the move midway
        drag.active = false
        val moved = drag.pos - drag.start                     // + = towards full, - = towards mini
        // decided by finger speed first, then by which way it moved; it always finishes in that direction
        val goFull = when {
            v < -150f -> true
            v > 150f -> false
            moved > 0.02f -> true
            moved < -0.02f -> false
            else -> drag.start > 0.5f
        }
        state.progress.animateTo(if (goFull) 1f else 0f, snapSpec(), initialVelocity = -v / range)
    }
    val collapse = PlayerDrag(onStart = { dragStart() }, onDelta = { d -> dragBy(d) }, onStop = { v -> scope.launch { settle(v) } })
    val currentId = rememberUpdatedState(ui.currentId)
    val currentIndex = rememberUpdatedState(ui.index)

    // ---------- mini player: sideways swipe changes the song inside the capsule
    val swipeX = remember { Animatable(0f) }
    // ---------- record-style spin of the round cover (mini player and the bar at the top)
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
        } else spin.animateTo(spin.value + 8f, tween(600, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }

    // ---------- the full player's cover carousel (one page per song in the queue)
    val queue = ui.queueIds
    val pager = rememberPagerState(initialPage = ui.index.coerceAtLeast(0)) { queue.size }
    LaunchedEffect(ui.index, queue.size) {
        val i = ui.index
        if (i !in 0 until queue.size || pager.isScrollInProgress) return@LaunchedEffect
        if (pager.currentPage != i) { if (abs(pager.currentPage - i) > 2) pager.scrollToPage(i) else pager.animateScrollToPage(i, animationSpec = snapSpec()) }
    }
    LaunchedEffect(pager) {
        withContext(Dispatchers.Main) {
            snapshotFlow { pager.settledPage }.collect { pg ->
                if (pg != currentIndex.value && pg in 0 until pager.pageCount && state.progress.value > 0.99f) { haptic(HapticFeedbackType.SegmentTick); act.playAt(pg) }
            }
        }
    }

    // ---------- lyrics of this song (for the live line)
    val stored by remember(song?.id) { song?.let { vm.repo.dao.lyricsFlow(it.id) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
    val lyrics by produceState<Lyrics?>(null, song?.id, stored) { value = song?.let { s -> withContext(Dispatchers.IO) { runCatching { vm.repo.lyrics.find(s) }.getOrNull() } } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val insets = WindowInsets.systemBars.asPaddingValues()
        val statusTop = with(density) { insets.calculateTopPadding().toPx() }
        val navBottom = with(density) { insets.calculateBottomPadding().toPx() }
        fun dp(v: Float) = with(density) { v.dp.toPx() }
        val g = remember(W, H, statusTop, navBottom, navVisible, wide) { geometry(W, H, statusTop, navBottom, navVisible, wide, ::dp) }
        val p = state.progress.value
        val q = state.panel.value
        val t = clamp01(p / 0.85f)                    // pieces land on their spots before the end
        val fullAlpha = clamp01((p - 0.55f) / 0.45f) * (1f - clamp01(q * 2f))
        range = (g.mini.top - g.full.top).coerceAtLeast(1f)

        // ---------- the dock (compact screens): slides away as the player opens; same colour as the mini player
        if (navVisible) {
            val navShift = (dp(68f) + dp(8f) + navBottom) * p
            navBar(Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = navShift; alpha = 1f - p }, if (song != null) colors.mini else null)
        }
        if (song == null) return@BoxWithConstraints

        val box = lerpR(g.mini, g.full, t)
        val bottomR = if (navVisible) 10f else 24f
        val r1 = lerpF(24f, 0f, t); val r2 = lerpF(bottomR, 0f, t)
        val sheetShape = RoundedCornerShape(r1.dp, r1.dp, r2.dp, r2.dp)
        val bg = lerp(colors.mini, colors.full, t)
        val swipeW = g.mini.width
        val swipeMod = Modifier.songSwipe(swipeX, swipeW, scope, { dir ->
            val before = currentId.value
            val changed = if (dir < 0) act.swipeNext() else act.swipePrevious()
            if (!changed) { act.toast(if (dir < 0) "This is the last song in the queue" else "This is the first song in the queue"); false }
            else {
                haptic(HapticFeedbackType.SegmentTick)
                kotlinx.coroutines.withTimeoutOrNull(2500) { snapshotFlow { currentId.value }.first { it != before } }
                true
            }
        })

        // ---------- the sheet: mini player capsule that grows into the full player. Everything moving lives inside it,
        // so it is clipped only by the sheet's own rounded outline, and this one drag handles every vertical swipe.
        Box(
            Modifier
                .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
                .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                .shadow(lerpF(3f, 0f, clamp01(t / 0.3f)).dp, sheetShape, clip = false)
                .clip(sheetShape)
                .background(bg)
                .draggable(dragState, Orientation.Vertical, enabled = q < 0.05f,
                    onDragStarted = { dragStart() }, onDragStopped = { v -> settle(v) })
                .then(if (p < 0.05f) Modifier.clickable(onClickLabel = "Open player") { scope.launch { state.progress.animateTo(1f, snapSpec()) } }.then(swipeMod) else Modifier)
                .testTag(if (p < 0.5f) "mini_player" else "full_player")
        ) {
            // mini player's own progress fill (the whole mini player is the progress bar)
            val fillAlpha = clamp01(1f - t * 2.5f)
            if (fillAlpha > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(ui.progress).alpha(fillAlpha).background(colors.miniFill).testTag("mini_fill"))

            // full-size canvas placed so the pieces can use whole-screen positions
            Box(Modifier.layout { m, c ->
                val pl = m.measure(Constraints.fixed(W.roundToInt(), H.roundToInt()))
                layout(c.maxWidth, c.maxHeight) { pl.place(-box.left.roundToInt(), -box.top.roundToInt()) }
            }) {
                // ---------- full-player-only content (fades in late, fades out when Up next / Lyrics opens)
                if (fullAlpha > 0f) FullChrome(g, colors, ui, song, liked, sleepActive, lyrics, fullAlpha, state, scope, onOpenMenu, onSleep, onPlaylist, haptic)

                // ---------- the bar at the top while Up next / Lyrics is open
                if (q > 0f && !g.twoPane) Box(Modifier.offset { IntOffset(g.bar.left.roundToInt(), g.bar.top.roundToInt()) }
                    .size(with(density) { g.bar.width.toDp() }, with(density) { g.bar.height.toDp() })
                    .alpha(clamp01(q * 1.5f)).clip(RoundedCornerShape(28.dp)).background(lerp(colors.full, colors.chip, 0.6f))
                    .clickable(onClickLabel = "Back to the player") { scope.launch { state.panel.animateTo(0f, snapSpec()) } }.testTag("panel_header"))

                // ---------- moving pieces
                val inBar = if (g.twoPane) 0f else q
                fun at(mini: Rect, full: Rect, bar: Rect) = lerpR(lerpR(mini, full, t), bar, inBar)
                val sx = swipeX.value
                val swipeAlpha = 1f - (abs(sx) / swipeW.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.9f
                val cover = at(g.coverMini, g.coverFull, g.coverBar)
                // round first, then square: the cover turns upright while still a circle, never a tilted square
                val circle = if (inBar > 0f) clamp01(inBar / 0.8f) else 1f - clamp01((t - 0.2f) / 0.8f)
                val rot = if (inBar > 0f) clamp01((inBar - 0.8f) / 0.2f) else 1f - clamp01(t / 0.2f)
                val angle = ((spin.value % 360f) + 540f) % 360f - 180f
                val coverR = lerpF(dp(32f), cover.width / 2f, circle)
                // in the mini player only, pieces swiped sideways stay left of the buttons
                val miniClip = if (t < 0.01f && sx != 0f) Rect(box.left, box.top, g.prevMini.left - dp(4f), box.bottom) else null
                fun Modifier.inMini(pieceLeft: Float, pieceTop: Float): Modifier = if (miniClip == null) this else this.drawWithContent {
                    val path = Path().apply {
                        addRoundRect(RoundRect(miniClip.left - pieceLeft, miniClip.top - pieceTop, miniClip.right - pieceLeft, miniClip.bottom - pieceTop,
                            topLeftCornerRadius = CornerRadius(dp(24f)), bottomLeftCornerRadius = CornerRadius(dp(bottomR))))
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                val showPager = p >= 0.999f && inBar <= 0.001f && !drag.active && queue.isNotEmpty() && ui.index in queue.indices
                if (showPager) CoverCarousel(pager, queue, g, settings.carouselPeek, ::dp)
                else Box(
                    Modifier.offset { IntOffset(cover.left.roundToInt(), cover.top.roundToInt()) }
                        .size(with(density) { cover.width.toDp() })
                        .inMini(cover.left, cover.top)
                        .graphicsLayer {
                            translationX = sx; alpha = swipeAlpha
                            rotationZ = angle * rot
                            val k = 1f - (abs(sx) / swipeW.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.12f; scaleX = k; scaleY = k
                        }
                        .semantics { contentDescription = "Album cover of ${song.title}" }
                        .testTag("player_cover")
                ) { Cover(song, with(density) { cover.width.toDp() }, RoundedCornerShape(with(density) { coverR.toDp() })) }

                // title + artist
                val title = at(g.titleMini, g.titleFull, g.titleBar)
                val big = t * (1f - inBar)
                Column(Modifier.offset { IntOffset(title.left.roundToInt(), title.top.roundToInt()) }.width(with(density) { title.width.toDp() })
                    .inMini(title.left, title.top).graphicsLayer { translationX = sx * 0.6f; alpha = swipeAlpha }) {
                    Crossfade(song, animationSpec = tween(160), label = "songText") { s ->
                        Column {
                            Text(s.title, color = lerp(colors.onMini, colors.onFull, t), style = RoundTitle, fontSize = lerpF(15f, 28f, big).sp, lineHeight = lerpF(20f, 34f, big).sp,
                                fontWeight = if (big > 0.5f) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player_title"))
                            val artistColor = lerp(colors.onMiniVariant, colors.onFull.copy(alpha = 0.8f), t)
                            val albumA = clamp01((big - 0.3f) / 0.7f)
                            Text(artistLine(s, artistColor, colors.onFull.copy(alpha = 0.8f * albumA), showAlbum = albumA > 0f), fontSize = lerpF(13f, 16f, big).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("player_artist"))
                        }
                    }
                }

                // ⏮ ⏯ ⏭: separate circles in the mini player, one joined group in the full player and in the bar
                val group = t * (1f - inBar) + inBar          // 0 = separate circles, 1 = joined group
                val prevInt = remember { MutableInteractionSource() }
                val playInt = remember { MutableInteractionSource() }
                val nextInt = remember { MutableInteractionSource() }
                val prevPressed by prevInt.collectIsPressedAsState()
                val playPressed by playInt.collectIsPressedAsState()
                val nextPressed by nextInt.collectIsPressedAsState()
                // pressed button widens, its neighbours squeeze (full player only)
                val squish = t * (1f - inBar)
                val prevGrow by androidx.compose.animation.core.animateFloatAsState(if (prevPressed) 1f else if (playPressed || nextPressed) -0.5f else 0f, spring(0.55f, 600f), label = "pg")
                val playGrow by androidx.compose.animation.core.animateFloatAsState(if (playPressed) 1f else if (prevPressed || nextPressed) -0.5f else 0f, spring(0.55f, 600f), label = "ppg")
                val nextGrow by androidx.compose.animation.core.animateFloatAsState(if (nextPressed) 1f else if (prevPressed || playPressed) -0.5f else 0f, spring(0.55f, 600f), label = "ng")
                val w = dp(16f) * squish
                var prev = at(g.prevMini, g.prevFull, g.prevBar)
                var play = at(g.playMini, g.playFull, g.playBar)
                var next = at(g.nextMini, g.nextFull, g.nextBar)
                // keep the group's outer edges fixed while sizes change
                prev = Rect(prev.left, prev.top, prev.right + w * prevGrow, prev.bottom)
                play = Rect(play.left + w * prevGrow, play.top, play.right + w * prevGrow + w * playGrow, play.bottom)
                next = Rect(next.left + w * prevGrow + w * playGrow, next.top, next.right, next.bottom)
                val outerR = { r: Rect -> min(r.width, r.height) / 2f }
                val innerR = dp(10f)
                GroupBtn(prev, colors.chip, lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipPrevious, "Previous", lerpF(20f, 28f, big), "btn_prev",
                    outerR(prev), lerpF(outerR(prev), innerR, group), prevInt) { haptic(HapticFeedbackType.ContextClick); act.previous() }
                val (morph, round) = rememberPlayMorph(ui.isPlaying)
                val squareR = lerpF(dp(16f), dp(30f), big)
                val playR = squareR + (min(play.width, play.height) / 2 - squareR) * round
                Box(
                    Modifier.offset { IntOffset(play.left.roundToInt(), play.top.roundToInt()) }
                        .size(with(density) { play.width.toDp() }, with(density) { play.height.toDp() })
                        .clip(RoundedCornerShape(with(density) { playR.toDp() })).background(colors.accent)
                        .clickable(interactionSource = playInt, indication = androidx.compose.material3.ripple(), onClickLabel = if (ui.isPlaying) "Pause" else "Play") {
                            haptic(HapticFeedbackType.ContextClick); act.togglePlay()
                        }
                        .semantics { contentDescription = if (ui.isPlaying) "Pause" else "Play" }.testTag("btn_play"),
                    contentAlignment = Alignment.Center,
                ) { PlayPauseGlyph(morph, colors.onAccent, lerpF(24f, 44f, big).dp) }
                GroupBtn(next, colors.chip, lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipNext, "Next", lerpF(20f, 28f, big), "btn_next",
                    lerpF(outerR(next), innerR, group), outerR(next), nextInt) { haptic(HapticFeedbackType.ContextClick); act.next() }

                // ---------- Up next / Lyrics page
                if (p > 0.55f) Panel(g, colors, ui, song, state, clamp01((p - 0.55f) / 0.45f), scope, collapse, lyrics, ::dp)
            }
        }
    }
}

/** Lets the panel's bottom strip pass a downward swipe on to "close the player". */
class PlayerDrag(val onStart: () -> Unit, val onDelta: (Float) -> Unit, val onStop: (Float) -> Unit)

/** Where a vertical drag started and where the finger has taken it (updated at once, not after a frame). */
private class DragTrack { var start = 0f; var pos = 0f; var job: kotlinx.coroutines.Job? = null; var active by mutableStateOf(false) }

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
 * Mini player only: sideways swipe changes the song. The cover follows the finger; past a quarter of the width (or a flick)
 * it slides out, the song changes and the new cover slides in. [change] gets -1 for next, +1 for previous.
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
                    if (onChange(dir)) { x.snapTo(-dir * width * 0.6f); x.animateTo(0f, snapSpec()) }
                    else x.animateTo(0f, snapSpec())
                } else x.animateTo(0f, snapSpec())
            }
        },
        onDragCancel = { scope.launch { x.animateTo(0f, snapSpec()) } },
    ) { change, d ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        scope.launch { x.snapTo(x.value + d) }
    }
}

/** The cover window used by the carousel: a rounded rectangle from [l] to [r] across the page. */
private class WindowShape(private val l: Float, private val r: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val right = max(l, r)
        val rad = min(radius, (right - l) / 2f).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(l, 0f, right, size.height, CornerRadius(rad)))
    }
}

/**
 * Full-player cover carousel. Swiping squeezes the old cover out (its rounded window closes towards the edge while the
 * picture moves only a little) and opens the next one from the other side. Settles with a quick, no-bounce snap.
 * One Peek (setting): the next cover shows as a narrow strip at the edge instead.
 */
@Composable
private fun CoverCarousel(pager: PagerState, queue: List<Long>, g: Geometry, peek: Boolean, dp: (Float) -> Float) {
    val vm = LocalVm.current
    val lib by vm.library.collectAsState()
    val d = LocalDensity.current
    val cs = g.coverFull.width
    val coverX = g.coverFull.left
    val radius = dp(32f)
    val gap = dp(6f)
    val spacing = if (peek) dp(12f) else coverX * 2f
    HorizontalPager(
        state = pager,
        modifier = Modifier.offset { IntOffset(0, g.coverFull.top.roundToInt()) }.width(with(d) { g.paneW.toDp() }).height(with(d) { cs.toDp() }).testTag("cover_carousel"),
        contentPadding = PaddingValues(horizontal = with(d) { coverX.toDp() }),
        pageSpacing = with(d) { spacing.toDp() },
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = snapSpec()),
        key = { i -> "$i-${queue.getOrNull(i)}" },
    ) { page ->
        val s = queue.getOrNull(page)?.let { lib.byId[it] }
        Box(
            Modifier.size(with(d) { cs.toDp() }).graphicsLayer {
                if (!peek) {
                    val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    // the picture moves only 30% as far as the page: it gets squeezed instead of sliding away
                    translationX = o * (cs + spacing) - 0.3f * o * cs
                    val l: Float; val r: Float
                    if (o >= 0f) { l = 0.3f * o * cs; r = cs - 0.7f * o * cs - (if (o > 0f) gap / 2 else 0f) }
                    else { val u = -o; l = 0.7f * u * cs + gap / 2; r = cs - 0.3f * u * cs }
                    clip = true; shape = WindowShape(l, r, radius)
                } else { clip = true; shape = RoundedCornerShape(radius) }
            }.semantics { contentDescription = "Album cover of ${s?.title ?: "song"}" }.testTag(if (page == pager.currentPage) "player_cover" else "cover_$page")
        ) { Cover(s, with(d) { cs.toDp() }, RectangleShape) }
    }
}

@Composable
private fun GroupBtn(r: Rect, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, iconSize: Float, tag: String,
                     leftR: Float, rightR: Float, interaction: MutableInteractionSource, onClick: () -> Unit) {
    val d = LocalDensity.current
    val shape = with(d) { RoundedCornerShape(leftR.toDp(), rightR.toDp(), rightR.toDp(), leftR.toDp()) }
    Box(
        Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
            .clip(shape).background(bg)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClickLabel = desc, onClick = onClick)
            .semantics { contentDescription = desc }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize.dp)) }
}

private fun geometry(W: Float, H: Float, statusTop: Float, navBottom: Float, navVisible: Boolean, wide: Boolean, dp: (Float) -> Float): Geometry {
    val miniW = min(W - dp(32f), dp(580f))
    val miniX = (W - miniW) / 2f
    val miniBottomGap = if (navVisible) navBottom + dp(4f) + dp(68f) + dp(4f) else navBottom + dp(14f)
    val miniY = H - miniBottomGap - dp(72f)
    val mini = Rect(miniX, miniY, miniX + miniW, miniY + dp(72f))
    val full = Rect(0f, 0f, W, H)
    val twoPane = W > H || W >= dp(840f)
    val paneW = if (twoPane) W * 0.5f else W
    val peekH = if (twoPane) 0f else dp(64f) + navBottom
    // full layout, top to bottom: top bar · cover · title · seek · controls · toolbar · lyric line · (strip)
    val fixed = dp(56f) + dp(8f) + dp(20f) + dp(60f) + dp(12f) + dp(52f) + dp(88f) + dp(22f) + dp(64f) + dp(8f) + dp(32f) + dp(8f)
    val avail = H - statusTop - fixed - peekH
    val coverSize = min(paneW - dp(72f), avail).coerceIn(dp(120f), dp(440f))
    val coverX = (paneW - coverSize) / 2f
    val contentH = fixed + coverSize
    val topY = if (twoPane) statusTop + max(0f, (H - statusTop - navBottom - contentH) / 2f) else statusTop
    val coverY = topY + dp(56f) + dp(8f)
    val titleY = coverY + coverSize + dp(20f)
    val seekY = titleY + dp(60f) + dp(12f)
    val controlsY = seekY + dp(52f)
    val toolbarY = controlsY + dp(88f) + dp(22f)
    val lyricY = toolbarY + dp(64f) + dp(8f)
    // controls row: shuffle · [prev | PLAY | next] · repeat
    val margin = dp(18f); val toggle = dp(48f); val tgap = dp(10f); val joint = dp(5f)
    val avail2 = paneW - 2 * margin - 2 * toggle - 2 * tgap
    val playW = (avail2 * 0.47f).coerceAtMost(dp(132f))
    val side = ((avail2 - 2 * joint - playW) / 2f).coerceAtMost(dp(72f))
    val groupW = side * 2 + playW + 2 * joint
    val gx = (paneW - groupW) / 2f
    val prevFull = Rect(gx, controlsY + dp(4f), gx + side, controlsY + dp(84f))
    val playFull = Rect(prevFull.right + joint, controlsY, prevFull.right + joint + playW, controlsY + dp(88f))
    val nextFull = Rect(playFull.right + joint, controlsY + dp(4f), playFull.right + joint + side, controlsY + dp(84f))
    val toggleY = controlsY + dp(20f)
    val shuffle = Rect(margin, toggleY, margin + toggle, toggleY + toggle)
    val repeat = Rect(paneW - margin - toggle, toggleY, paneW - margin, toggleY + toggle)
    val heart = Rect(paneW - dp(24f) - dp(48f), titleY + dp(6f), paneW - dp(24f), titleY + dp(54f))
    // mini layout: round cover 52, title, then prev 40 · play 48 · next 40
    val right = mini.right - dp(12f)
    val nextMini = Rect(right - dp(40f), mini.top + dp(16f), right, mini.top + dp(56f))
    val playMini = Rect(nextMini.left - dp(8f) - dp(48f), mini.top + dp(12f), nextMini.left - dp(8f), mini.top + dp(60f))
    val prevMini = Rect(playMini.left - dp(8f) - dp(40f), mini.top + dp(16f), playMini.left - dp(8f), mini.top + dp(56f))
    // bar at the top while Up next / Lyrics is open: round cover, title, joined ⏮ ⏯ ⏭
    val bar = Rect(dp(14f), topY + dp(8f), W - dp(14f), topY + dp(84f))
    val bRight = bar.right - dp(10f)
    val nextBar = Rect(bRight - dp(40f), bar.top + dp(14f), bRight, bar.top + dp(62f))
    val playBar = Rect(nextBar.left - dp(4f) - dp(58f), bar.top + dp(12f), nextBar.left - dp(4f), bar.top + dp(64f))
    val prevBar = Rect(playBar.left - dp(4f) - dp(40f), bar.top + dp(14f), playBar.left - dp(4f), bar.top + dp(62f))
    val coverBar = Rect(bar.left + dp(10f), bar.top + dp(10f), bar.left + dp(66f), bar.top + dp(66f))
    return Geometry(
        mini, full,
        Rect(mini.left + dp(10f), mini.top + dp(10f), mini.left + dp(62f), mini.top + dp(62f)),
        Rect(coverX, coverY, coverX + coverSize, coverY + coverSize),
        coverBar,
        Rect(mini.left + dp(76f), mini.top + dp(13f), prevMini.left - dp(8f), mini.top + dp(60f)),
        Rect(dp(24f), titleY, paneW - dp(24f) - dp(60f), titleY + dp(60f)),
        Rect(coverBar.right + dp(12f), bar.top + dp(16f), prevBar.left - dp(8f), bar.top + dp(62f)),
        prevMini, prevFull, prevBar, playMini, playFull, playBar, nextMini, nextFull, nextBar,
        bar, topY, seekY, controlsY, toolbarY, lyricY, shuffle, repeat, heart, paneW, twoPane, peekH,
    )
}

@Composable
private fun animateCoverColors(t: CoverColors): CoverColors {
    val spec = tween<Color>(450)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "cc").value
    return CoverColors(a(t.full), a(t.onFull), a(t.accent), a(t.onAccent), a(t.chip), a(t.onChip), a(t.mini), a(t.miniFill), a(t.onMini),
        a(t.onMiniVariant), a(t.panel), a(t.onPanel), a(t.onPanelVariant), a(t.outline))
}

/** Top bar, heart, wavy seek bar, shuffle/repeat, the Sleep · Lyrics · Playlist toolbar and the live lyric line. */
@Composable
private fun FullChrome(
    g: Geometry, c: CoverColors, ui: PlayerUi, song: Song, liked: Boolean, sleepActive: Boolean, lyrics: Lyrics?,
    alpha: Float, state: SheetState, scope: kotlinx.coroutines.CoroutineScope, onOpenMenu: () -> Unit, onSleep: () -> Unit, onPlaylist: () -> Unit,
    haptic: (HapticFeedbackType) -> Unit,
) {
    val act = LocalPlayerActions.current
    val d = LocalDensity.current
    val paneW = with(d) { g.paneW.toDp() }
    fun Rect.pos() = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(with(d) { width.toDp() }, with(d) { height.toDp() })
    Box(Modifier.fillMaxSize().alpha(alpha)) {
        // top bar: round ⌄ · "Now playing" · round ⋮
        Row(Modifier.offset { IntOffset(0, g.topY.roundToInt()) }.width(paneW).height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.KeyboardArrowDown, "Minimise player", c.chip, c.onFull, 44.dp, "collapse") {
                scope.launch { state.progress.animateTo(0f, snapSpec()) }
            }
            Text("Now playing", style = RoundTitle, fontSize = 17.sp, color = c.onFull, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("now_playing"))
            RoundIcon(Icons.Rounded.MoreVert, "More options", c.chip, c.onFull, 44.dp, "player_menu", onClick = onOpenMenu)
        }
        // heart: round, just fills in
        Box(g.heart.pos(), contentAlignment = Alignment.Center) {
            RoundIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like", c.chip, c.accent, 48.dp, "like") {
                haptic(if (liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); act.like(song.id)
            }
        }
        // thick wavy seek bar + times
        Column(Modifier.offset { IntOffset(0, g.seekY.roundToInt()) }.width(paneW).padding(horizontal = 20.dp)) {
            WavySeekBar(ui.positionMs, ui.durationMs, ui.isPlaying, c.accent, c.onFull.copy(alpha = 0.22f), onSeek = { act.seekTo(it) },
                modifier = Modifier.fillMaxWidth().height(30.dp).testTag("seek"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Text(formatDuration(ui.positionMs), color = c.onFull, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(ui.durationMs), color = c.onFull, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        // shuffle & repeat: round; filled when on, outline when off; repeat-one shows a "1"
        Box(g.shuffle.pos()) {
            ToggleCircle(Icons.Rounded.Shuffle, ui.shuffle, if (ui.shuffle) "Shuffle on" else "Shuffle off", c, "shuffle") {
                haptic(if (ui.shuffle) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                act.toggleShuffle(); act.toast(if (ui.shuffle) "Shuffle off" else "Shuffle on")
            }
        }
        Box(g.repeat.pos()) {
            val one = ui.repeat == Player.REPEAT_MODE_ONE
            ToggleCircle(if (one) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, ui.repeat != Player.REPEAT_MODE_OFF,
                when (ui.repeat) { Player.REPEAT_MODE_ONE -> "Repeat this song"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" }, c, "repeat") {
                haptic(HapticFeedbackType.ToggleOn)
                act.cycleRepeat()
                act.toast(when (ui.repeat) { Player.REPEAT_MODE_OFF -> "Repeat all"; Player.REPEAT_MODE_ALL -> "Repeat this song"; else -> "Repeat off" })
            }
        }
        // floating toolbar: Sleep · Lyrics · Playlist
        Box(Modifier.offset { IntOffset(0, g.toolbarY.roundToInt()) }.width(paneW).height(64.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.shadow(10.dp, CircleShape).clip(CircleShape).background(lerp(c.full, c.chip, 0.85f)).padding(6.dp).testTag("player_toolbar"),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ToolItem(Icons.Rounded.Bedtime, if (sleepActive) "Sleep on" else "Sleep", sleepActive, c, "extra_sleep", onSleep)
                ToolItem(Icons.Rounded.Lyrics, "Lyrics", false, c, "extra_lyrics") {
                    state.tab = 1; scope.launch { state.panel.animateTo(1f, snapSpec()) }
                }
                ToolItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist", false, c, "extra_playlist", onPlaylist)
            }
        }
        // live lyric line: one line, synced, not tappable (the Lyrics button opens the lyrics)
        if (!g.twoPane) LiveLyricLine(lyrics, ui.positionMs, c, Modifier.offset { IntOffset(0, g.lyricY.roundToInt()) }.width(paneW).height(32.dp).padding(horizontal = 24.dp))
    }
}

/** The current lyric line: always one row (text shrinks a little to fit), very long lines scroll sideways, ♪ in gaps. */
@Composable
private fun LiveLyricLine(lyrics: Lyrics?, posMs: Long, c: CoverColors, modifier: Modifier) {
    if (lyrics == null || !lyrics.synced) return
    val i = lyrics.indexAt(posMs)
    val text = (if (i >= 0) lyrics.lines[i].text.trim() else "").ifEmpty { "♪" }
    AnimatedContent(text, modifier.testTag("live_lyric"), transitionSpec = {
        (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160)))
    }, label = "lyricLine") { line ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val measurer = rememberTextMeasurer()
            val maxPx = constraints.maxWidth
            val base = RoundTitle.copy(color = c.onFull, fontWeight = FontWeight.ExtraBold)
            // the biggest size from 20 sp down to 14 sp that keeps the line in one row
            val size = remember(line, maxPx) {
                var s = 20f
                while (s > 14f && measurer.measure(line, base.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > maxPx) s -= 0.5f
                s
            }
            val fits = remember(line, maxPx, size) { measurer.measure(line, base.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width <= maxPx }
            Text(line, style = base.copy(fontSize = size.sp), maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                modifier = if (fits) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600))
        }
    }
}

@Composable
private fun ToolItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, c: CoverColors, tag: String, onClick: () -> Unit) {
    Row(Modifier.height(52.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 16.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (on) c.onAccent else c.onFull, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (on) c.onAccent else c.onFull, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: Color, fg: Color, size: Dp, tag: String, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClickLabel = desc, onClick = onClick).semantics { contentDescription = desc }.testTag(tag),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
private fun ToggleCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, desc: String, c: CoverColors, tag: String, onClick: () -> Unit) {
    val bgc by animateColorAsState(if (on) c.accent else Color.Transparent, tween(180), label = "tg")
    val fg by animateColorAsState(if (on) c.onAccent else c.onFull, tween(180), label = "tf")
    val border by animateDpAsState(if (on) 0.dp else 1.5.dp, tween(180), label = "tb")
    Box(Modifier.fillMaxSize().clip(CircleShape).background(bgc).border(border, c.onFull.copy(alpha = 0.35f), CircleShape)
        .clickable(onClickLabel = desc, onClick = onClick).semantics { contentDescription = desc }.testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
    }
}
