@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val insets = WindowInsets.systemBars.asPaddingValues()
        val statusTop = with(density) { insets.calculateTopPadding().toPx() }
        val navBottom = with(density) { insets.calculateBottomPadding().toPx() }
        fun dp(v: Float) = with(density) { v.dp.toPx() }
        val g = remember(W, H, statusTop, navBottom, navVisible, wide) { geometry(W, H, statusTop, navBottom, navVisible, wide, ::dp) }
        val p = state.progress.value
        val t = clamp01(p / 0.85f)                    // pieces land exactly on their spots before the end
        val fullAlpha = clamp01((p - 0.55f) / 0.45f)   // things only the full player has
        val range = (g.mini.top - g.full.top).coerceAtLeast(1f)

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
        val dragState = rememberDraggableState { d -> scope.launch { state.progress.snapTo(clamp01(state.progress.value - d / range)) } }
        var fromFull by remember { mutableStateOf(false) }
        val settle: suspend (Float) -> Unit = { v ->
            val cur = state.progress.value
            // released past 28% of the way: complete the move; otherwise spring back
            val goFull = if (abs(v) > 900f) v < 0 else if (fromFull) cur > 0.72f else cur > 0.28f
            state.progress.animateTo(if (goFull) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 420f), initialVelocity = -v / range)
        }

        // ---------- the sheet box (mini player background → full player background)
        Box(
            Modifier
                .offset { IntOffset(box.left.toInt(), box.top.toInt()) }
                .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                .shadow(if (t < 0.02f) 8.dp else 0.dp, RoundedCornerShape(r1.dp, r1.dp, r2.dp, r2.dp))
                .clip(RoundedCornerShape(r1.dp, r1.dp, r2.dp, r2.dp))
                .background(bg)
                .draggable(dragState, Orientation.Vertical, enabled = state.panel.value < 0.05f, onDragStarted = { fromFull = state.progress.value > 0.5f }, onDragStopped = { v -> settle(v) })
                .then(if (p < 0.05f) Modifier.clickable(onClickLabel = "Open player") { scope.launch { state.progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) } } else Modifier)
                .pointerSwipe(enabled = p < 0.05f, onNext = act.next, onPrev = act.previous)
                .testTag(if (p < 0.5f) "mini_player" else "full_player")
        ) {
            // mini player's own progress fill (the whole mini player is the progress bar)
            val fillAlpha = clamp01(1f - t * 2.5f)
            if (fillAlpha > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(ui.progress).alpha(fillAlpha).background(colors.miniFill).testTag("mini_fill"))
        }

        // ---------- full-player-only content (fades in late)
        if (fullAlpha > 0f) FullChrome(g, colors, ui, song, liked, contextLine, sleepActive, fullAlpha, state, scope, onOpenMenu, onSleep, onSpeed, onSound, onPlaylist, ::dp)

        // ---------- flying pieces
        val cover = lerpR(g.coverMini, g.coverFull, t)
        val coverR = lerpF(g.coverMini.width / 2f, dp(32f), t)
        Box(
            Modifier.offset { IntOffset(cover.left.toInt(), cover.top.toInt()) }
                .size(with(density) { cover.width.toDp() })
                .then(if (p > 0.5f) Modifier.pointerSwipe(true, act.next, act.previous).pointerInput(song.id) { detectTapGestures(onDoubleTap = { act.like(song.id) }) } else Modifier)
                .semantics { contentDescription = "Album cover of ${song.title}" }
        ) { Cover(song, with(density) { cover.width.toDp() }, RoundedCornerShape(with(density) { coverR.toDp() })) }

        val tx = lerpF(g.titleMini.x, g.titleFull.x, t); val ty = lerpF(g.titleMini.y, g.titleFull.y, t)
        val tw = lerpF(g.titleWMini, g.titleWFull, t)
        Column(Modifier.offset { IntOffset(tx.toInt(), ty.toInt()) }.width(with(density) { tw.toDp() })) {
            Text(song.title, color = lerp(colors.onMini, colors.onFull, t), fontSize = lerpF(15f, 26f, t).sp, lineHeight = lerpF(20f, 32f, t).sp,
                fontWeight = if (t > 0.5f) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("player_title"))
            Row {
                Text(song.artist, color = lerp(colors.onMiniVariant, colors.onFull.copy(alpha = 0.8f), t), fontSize = lerpF(13f, 16f, t).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t > 0.3f && song.album.isNotBlank()) Text(" · ${song.album}", color = colors.onFull.copy(alpha = 0.8f * clamp01((t - 0.3f) / 0.7f)),
                    fontSize = lerpF(13f, 16f, t).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        RoundBtn(lerpR(g.prevMini, g.prevFull, t), colors.chip,
            lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipPrevious, "Previous", lerpF(20f, 32f, t), CircleShape, "btn_prev") { act.previous() }
        val play = lerpR(g.playMini, g.playFull, t)
        RoundBtn(play, colors.accent, colors.onAccent, if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (ui.isPlaying) "Pause" else "Play", lerpF(24f, 44f, t), RoundedCornerShape(with(density) { lerpF(play.height / 2, dp(28f), t).toDp() }), "btn_play") { act.togglePlay() }
        RoundBtn(lerpR(g.nextMini, g.nextFull, t), colors.chip,
            lerp(colors.onMini, colors.onChip, t), Icons.Rounded.SkipNext, "Next", lerpF(20f, 32f, t), CircleShape, "btn_next") { act.next() }

        // ---------- Up next / Lyrics / Info panel (full player only)
        if (fullAlpha > 0f) Panel(g, colors, ui, song, state, fullAlpha, scope, ::dp)
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

/** Horizontal swipe = next / previous song. */
@Composable
private fun Modifier.pointerSwipe(enabled: Boolean, onNext: () -> Unit, onPrev: () -> Unit): Modifier {
    if (!enabled) return this
    val th = with(LocalDensity.current) { 72.dp.toPx() }
    return this.pointerInput(Unit) {
        var dx = 0f
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx < -th) onNext() else if (dx > th) onPrev() }) { c, d -> dx += d; c.consume() }
    }
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
    val playW = dp(104f); val playH = dp(80f)
    val playX = (paneW - playW) / 2f
    val side = dp(64f)
    val prevFull = Rect(playX - dp(24f) - side, controlsY + dp(8f), playX - dp(24f), controlsY + dp(8f) + side)
    val nextFull = Rect(playX + playW + dp(24f), controlsY + dp(8f), playX + playW + dp(24f) + side, controlsY + dp(8f) + side)
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
        // shuffle & repeat
        val ctrlY = with(d) { (g.controlsY + dp(40f)).toDp() } - 24.dp
        IconButton(onClick = act.toggleShuffle, modifier = Modifier.offset(x = 12.dp, y = ctrlY).testTag("shuffle")) {
            Icon(if (ui.shuffle) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, if (ui.shuffle) "Shuffle on" else "Shuffle off", tint = c.onFull)
        }
        IconButton(onClick = act.cycleRepeat, modifier = Modifier.offset(x = paneW - 60.dp, y = ctrlY).testTag("repeat")) {
            Icon(when (ui.repeat) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn; Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn; else -> Icons.Rounded.Repeat },
                when (ui.repeat) { Player.REPEAT_MODE_ONE -> "Repeat this song"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" }, tint = c.onFull)
        }
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

