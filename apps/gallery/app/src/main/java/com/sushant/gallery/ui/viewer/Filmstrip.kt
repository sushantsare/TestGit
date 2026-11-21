package com.sushant.gallery.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Dates
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.thumbModel
import com.sushant.gallery.ui.theme.LocalFixedColors
import com.sushant.gallery.ui.theme.LocalSpacing
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Jelly Bean–style filmstrip: three cards visible, swipe sideways to browse, flick a card up to move it to the
 * Recycle Bin (Undo on the snackbar), flick down does nothing. Tap the middle card or pinch out to go back.
 */
@Composable
fun Filmstrip(
    vm: MainViewModel,
    list: List<MediaItem>,
    start: Int,
    title: String,
    onIndex: (Int) -> Unit,
    onExit: (Int) -> Unit,
) {
    val fixed = LocalFixedColors.current
    val sp = LocalSpacing.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { list.size }
    val strip = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val isVault = list.firstOrNull()?.vaultId != null
    var showHint by remember { mutableStateOf(vm.prefs.hintCount("film") < 3) }
    val dragY = remember { Animatable(0f) }
    var crossed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (showHint) vm.prefs.bumpHint("film"); runCatching { focus.requestFocus() } }
    LaunchedEffect(pager.currentPage) {
        onIndex(pager.currentPage)
        strip.animateScrollToItem(pager.currentPage)
    }
    BackHandler { onExit(pager.currentPage) }

    fun delete() {
        val m = list.getOrNull(pager.currentPage) ?: return
        if (isVault || deleting) return
        deleting = true
        showHint = false
        scope.launch {
            dragY.animateTo(-3000f, tween(220))
            removedKey = m.key
            vm.moveToBin(listOf(m), fromFilmstrip = true)
            dragY.snapTo(0f)
            crossed = false
            deleting = false
            kotlinx.coroutines.delay(2500)
            if (removedKey == m.key) removedKey = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(fixed.filmBg).windowInsetsPadding(WindowInsets.statusBars)
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    e.key == Key.DirectionRight -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(list.lastIndex)) }; true }
                    e.key == Key.DirectionLeft -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    e.key == Key.DirectionUp || e.key == Key.Delete || e.key == Key.Backspace -> { delete(); true }
                    e.key == Key.Z && e.isCtrlPressed -> { vm.undoLast(); true }
                    e.key == Key.Enter || e.key == Key.Escape -> { onExit(pager.currentPage); true }
                    else -> false
                }
            }
            .testTag("filmstrip"),
    ) {
        AppTopBar(title, onBack = { onExit(pager.currentPage) }, contentColor = fixed.onMedia) {
            Text(
                "${(pager.currentPage + 1).coerceAtMost(list.size)} / ${list.size}", color = fixed.onMedia.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp).testTag("film_count"),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val cardH = if (wide) maxHeight * 0.62f else minOf(sp.filmCardHeight * (maxHeight / 520.dp).coerceIn(1f, 1.35f), maxHeight - 120.dp)
            val cardW = cardH * 0.586f
            val sidePad = (maxWidth - cardW) / 2
            val hPx = with(density) { cardH.toPx() }

            // trash target appears while flicking up
            val progress = (-dragY.value / (hPx * 0.35f)).coerceIn(0f, 1.2f)
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(sp.trashSize)
                    .graphicsLayer { val s = if (progress <= 0.05f) 0f else lerp(0.4f, 1.1f, progress.coerceAtMost(1f)); scaleX = s; scaleY = s }
                    .background(fixed.trash, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Delete, null, tint = fixed.onMedia) }

            FlickHint(showHint && !isVault, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))

            HorizontalPager(
                state = pager,
                key = { list.getOrNull(it)?.key ?: "gone-$it" },
                pageSize = PageSize.Fixed(cardW),
                pageSpacing = sp.filmCardGap,
                contentPadding = PaddingValues(horizontal = sidePad),
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        // pinch out (spread two fingers) = back to the full photo
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var acc = 1f
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.count { it.pressed } >= 2) {
                                    acc *= e.calculateZoom()
                                    e.changes.forEach { it.consume() }
                                    if (acc > 1.35f) { onExit(pager.currentPage); break }
                                }
                            } while (e.changes.any { it.pressed })
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val m = list.getOrNull(page) ?: return@HorizontalPager
                val current = page == pager.currentPage
                val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                val deleteLabel = stringResource(R.string.delete)
                val openLabel = stringResource(R.string.cd_open)
                Box(
                    Modifier.width(cardW).height(cardH)
                        .graphicsLayer {
                            val s = lerp(1f, 0.9f, off)
                            scaleX = s; scaleY = s
                            alpha = if (m.key == removedKey) 0f else lerp(1f, 0.75f, off)
                            if (current) {
                                translationY = dragY.value
                                rotationZ = dragY.value * -0.02f
                            }
                        }
                        .clip(RoundedCornerShape(sp.filmCardRadius))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(
                            if (current && !isVault) Modifier.pointerInput(m.key) {
                                val tracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = { tracker.resetTracking(); crossed = false },
                                    onDragEnd = {
                                        val v = tracker.calculateVelocity().y
                                        if (dragY.value < -hPx * 0.35f || (v < -1800f && dragY.value < -40f)) delete()
                                        else scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                    },
                                    onDragCancel = { scope.launch { dragY.animateTo(0f, spring()) } },
                                ) { change, dy ->
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val next = dragY.value + if (dragY.value + dy > 0f) dy * 0.15f else dy
                                    scope.launch { dragY.snapTo(next.coerceIn(-hPx * 1.5f, hPx * 0.12f)) }
                                    val over = next < -hPx * 0.35f
                                    if (over != crossed) { crossed = over; if (over) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                }
                            } else Modifier,
                        )
                        .pointerInput(page) {
                            detectTapGestures(onTap = {
                                if (page == pager.currentPage) onExit(page) else scope.launch { pager.animateScrollToPage(page) }
                            })
                        }
                        .semantics {
                            contentDescription = (if (m.isVideo) "Video, " else "Photo, ") + Dates.fullDate(m.dateTaken)
                            customActions = buildList {
                                add(CustomAccessibilityAction(openLabel) { onExit(page); true })
                                if (!isVault) add(CustomAccessibilityAction(deleteLabel) { if (page == pager.currentPage) delete() else scope.launch { pager.scrollToPage(page) }; true })
                            }
                        }
                        .testTag(if (current) "film_card_current" else "film_card"),
                ) {
                    AsyncImage(thumbModel(m), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (m.isVideo) {
                        Box(Modifier.align(Alignment.Center).size(44.dp).background(fixed.mediaScrim, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = fixed.onMedia)
                        }
                    }
                }
            }
        }

        // thumbnail strip
        BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 12.dp, bottom = 40.dp)) {
            val pad = maxWidth / 2 - sp.stripThumbW / 2
            LazyRow(
                state = strip, contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("film_strip"),
            ) {
                itemsIndexed(list, key = { _, m -> m.key }) { i, m ->
                    val sel = i == pager.currentPage
                    Box(
                        Modifier.width(if (sel) 36.dp else sp.stripThumbW).height(if (sel) 48.dp else sp.stripThumbH)
                            .then(if (sel) Modifier.border(2.dp, fixed.stripSelected, RoundedCornerShape(7.dp)).padding(3.dp) else Modifier)
                            .clip(RoundedCornerShape(6.dp))
                            .graphicsLayer { alpha = if (sel) 1f else 0.55f }
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    ) { AsyncImage(thumbModel(m), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
private fun FlickHint(visible: Boolean, modifier: Modifier) {
    val fixed = LocalFixedColors.current
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier.background(fixed.hintFill, RoundedCornerShape(50)).border(1.dp, fixed.hintBorder, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp).testTag("flick_hint"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.SwipeUp, null, tint = fixed.onMedia, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.hint_flick), color = fixed.onMedia, style = MaterialTheme.typography.bodySmall)
        }
    }
}
