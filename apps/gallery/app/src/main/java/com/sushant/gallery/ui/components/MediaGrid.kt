package com.sushant.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sushant.gallery.R
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.ui.theme.LocalSpacing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A group of the date grid. Empty label = no header. */
data class GridGroup(val label: String, val items: List<MediaItem>)

/**
 * Photo grid used everywhere: date headers, tap, long-press to select, drag across to select more,
 * pinch to change the number of columns, and a fast-scroll thumb with a date bubble.
 */
@Composable
fun MediaGrid(
    groups: List<GridGroup>,
    columns: Int,
    selection: Set<String>,
    favourites: Set<String>,
    onOpen: (MediaItem) -> Unit,
    onSelect: (Collection<String>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    onColumns: ((Int) -> Unit)? = null,
    daysLeft: ((MediaItem) -> Int)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 16.dp,
    header: (@Composable () -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    val haptic = LocalHapticFeedback.current
    val selectionMode = selection.isNotEmpty()
    val byKey = remember(groups) { groups.flatMap { it.items }.associateBy { it.key } }
    val order = remember(groups) { groups.flatMap { it.items }.map { it.key } }
    val indexOf = remember(order) { order.withIndex().associate { it.value to it.index } }
    val selNow by rememberUpdatedState(selection)
    val colsNow by rememberUpdatedState(columns)

    fun keyAt(p: Offset): String? = state.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= it.offset.y && p.y < it.offset.y + it.size.height
    }?.key?.let { it as? String }?.takeIf { it in byKey }

    val gestures = Modifier.pointerInput(order) {
        val slop = viewConfiguration.touchSlop
        val lpTimeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var outcome = 0 // 0 long press, 1 up, 2 moved, 3 two fingers
            var upPos = down.position
            withTimeoutOrNull(lpTimeout) {
                while (outcome == 0) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.count { it.pressed } >= 2) { outcome = 3; break }
                    val c = e.changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed) { outcome = 1; upPos = c?.position ?: upPos; break }
                    if ((c.position - down.position).getDistance() > slop) { outcome = 2; break }
                }
            }
            when (outcome) {
                1 -> keyAt(upPos)?.let { k ->
                    byKey[k]?.let { m -> if (selNow.isNotEmpty()) onSelect(listOf(k), k !in selNow) else onOpen(m) }
                }
                3 -> {
                    if (onColumns == null) return@awaitEachGesture
                    var acc = 1f
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.none { it.pressed }) break
                        if (e.changes.count { it.pressed } >= 2) {
                            acc *= e.calculateZoom()
                            if (acc > 1.3f && colsNow > 2) { onColumns(colsNow - 1); acc = 1f }
                            if (acc < 0.77f && colsNow < 6) { onColumns(colsNow + 1); acc = 1f }
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
                0 -> {
                    val anchor = keyAt(down.position) ?: return@awaitEachGesture
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val wasSelected = anchor in selNow
                    onSelect(listOf(anchor), !wasSelected)
                    val start = indexOf[anchor] ?: return@awaitEachGesture
                    var last = anchor
                    var applied: Set<String> = setOf(anchor)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) { c.consume(); break }
                        if (c.positionChange() != Offset.Zero) c.consume()
                        val h = size.height.toFloat()
                        if (c.position.y < 80f) state.dispatchRawDelta(-18f) else if (c.position.y > h - 80f) state.dispatchRawDelta(18f)
                        val k = keyAt(c.position) ?: continue
                        if (k == last) continue
                        last = k
                        val end = indexOf[k] ?: continue
                        val range = order.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
                        val add = range - applied
                        val remove = applied - range
                        if (add.isNotEmpty()) onSelect(add, !wasSelected)
                        if (remove.isNotEmpty()) onSelect(remove - anchor, wasSelected)
                        applied = range
                    }
                }
                else -> Unit
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(sp.gridGap),
            verticalArrangement = Arrangement.spacedBy(sp.gridGap),
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize().then(gestures).testTag("grid"),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header() }
            groups.forEach { g ->
                if (g.label.isNotEmpty()) {
                    item(key = "h:" + g.label + g.items.first().key, span = { GridItemSpan(maxLineSpan) }, contentType = "label") {
                        val keys = remember(g.items) { g.items.map { it.key } }
                        val all = selectionMode && keys.all { it in selection }
                        SectionHeader(g.label) {
                            if (selectionMode) {
                                IconButton(onClick = { onSelect(keys, !all) }) {
                                    Icon(
                                        if (all) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        stringResource(if (all) R.string.cd_unselect_group else R.string.cd_select_group, g.label),
                                        tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                g.items.forEach { m ->
                    item(key = m.key, contentType = "cell") {
                        val sel = m.key in selection
                        val openLabel = stringResource(R.string.cd_open)
                        val selectLabel = stringResource(R.string.cd_select)
                        MediaCell(
                            item = m, selected = sel, selectionMode = selectionMode, favourite = m.path in favourites,
                            onClick = {}, onLongClick = {}, interactive = false,
                            daysLeft = daysLeft?.invoke(m),
                            modifier = Modifier.semantics {
                                onClick(openLabel) { if (selectionMode) onSelect(listOf(m.key), !sel) else onOpen(m); true }
                                onLongClick(selectLabel) { onSelect(listOf(m.key), !sel); true }
                            },
                        )
                    }
                }
            }
        }
        FastScroller(state, order.size, groups, Modifier.align(Alignment.TopEnd))
    }
}

/** Draggable thumb on the right edge, shown while scrolling long lists, with a date bubble. */
@Composable
private fun FastScroller(state: LazyGridState, count: Int, groups: List<GridGroup>, modifier: Modifier) {
    if (count < 60) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var frac by remember { mutableFloatStateOf(0f) }
    val visible by remember { derivedStateOf { state.isScrollInProgress || dragging } }
    val labels = remember(groups) {
        val l = ArrayList<String>(count); groups.forEach { g -> repeat(g.items.size) { l += g.label } }; l
    }
    val density = LocalDensity.current
    val shown by remember { derivedStateOf { if (dragging) frac else {
        val info = state.layoutInfo
        val total = info.totalItemsCount.coerceAtLeast(1)
        (state.firstVisibleItemIndex.toFloat() / (total - info.visibleItemsInfo.size).coerceAtLeast(1)).coerceIn(0f, 1f)
    } } }
    BoxWithConstraints(modifier.fillMaxSize().padding(vertical = 8.dp)) {
        val trackPx = with(density) { (maxHeight - 48.dp).toPx() }
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            Row(
                Modifier.offset { IntOffset(0, (shown * trackPx).roundToInt()) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = labels.getOrNull((shown * (labels.size - 1)).roundToInt())?.takeIf { it.isNotEmpty() }
                if (dragging && label != null) {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Box(
                    Modifier.padding(start = 8.dp).width(28.dp).height(48.dp)
                        .pointerInput(count) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = true; frac = shown },
                                onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                            ) { ch, dy ->
                                ch.consume()
                                frac = (frac + dy / trackPx).coerceIn(0f, 1f)
                                val target = (frac * (state.layoutInfo.totalItemsCount - 1)).roundToInt()
                                scope.launch { state.scrollToItem(target) }
                            }
                        }
                        .semantics { contentDescription = "Fast scroll" }
                        .testTag("fastscroll"),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(Modifier.size(width = 6.dp, height = 40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
