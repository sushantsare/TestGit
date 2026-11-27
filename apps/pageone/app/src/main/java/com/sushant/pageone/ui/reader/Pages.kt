package com.sushant.pageone.ui.reader

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.pageone.data.PageMode
import com.sushant.pageone.pdf.Annot
import com.sushant.pageone.pdf.FormField
import com.sushant.pageone.pdf.PdfDoc
import com.sushant.pageone.pdf.Tool
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Paper colour filters: night (inverted) and sepia (warm). */
fun pageFilter(mode: PageMode): ColorFilter? = when (mode) {
    PageMode.NORMAL -> null
    PageMode.NIGHT -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -0.92f, 0f, 0f, 0f, 245f,
        0f, -0.92f, 0f, 0f, 245f,
        0f, 0f, -0.92f, 0f, 245f,
        0f, 0f, 0f, 1f, 0f)))
    PageMode.SEPIA -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.96f, 0f, 0f, 0f, 0f,
        0f, 0.92f, 0f, 0f, 0f,
        0f, 0f, 0.80f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
}

/**
 * Two-finger zoom for the reader. Every touch is one kind of gesture: once a second finger lands it is a zoom
 * for the rest of that touch (the leftover finger can't scroll or tap), so a pinch is never read as a double-tap.
 * [onZoom] gets the step's scale, the finger midpoint before the step and after it (so the page also moves with
 * the fingers); [onEnd] runs when the last finger lifts.
 */
fun Modifier.zoomGestures(vm: ReaderVm, onZoom: (scale: Float, from: Offset, to: Offset) -> Unit, onEnd: () -> Unit) = pointerInput(vm) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zooming = false
        do {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.count { it.pressed } >= 2) {
                zooming = true
                vm.lastMultiTouch = android.os.SystemClock.uptimeMillis()
                val c = e.calculateCentroid(useCurrent = true)
                if (c != Offset.Unspecified) {
                    var z = e.calculateZoom()
                    if (kotlin.math.abs(z - 1f) < 0.002f) z = 1f          // ignore finger tremble
                    onZoom(z, c - e.calculatePan(), c)
                }
            }
            if (zooming) e.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
        } while (e.changes.any { it.pressed })
        if (zooming) { vm.lastMultiTouch = android.os.SystemClock.uptimeMillis(); onEnd() }
    }
}

/** Where a page shows on screen and how to turn finger positions into PDF points. */
class PageGeo(val widthPx: Float, val heightPx: Float, val area: RectF) {
    val scale get() = widthPx / area.width()
    fun toPoint(o: Offset) = PointF(area.left + o.x / scale, area.top + o.y / scale)
    fun toPx(r: RectF) = androidx.compose.ui.geometry.Rect((r.left - area.left) * scale, (r.top - area.top) * scale, (r.right - area.left) * scale, (r.bottom - area.top) * scale)
    fun toPx(p: PointF) = Offset((p.x - area.left) * scale, (p.y - area.top) * scale)
}

@Composable
fun PageView(
    vm: ReaderVm, doc: PdfDoc, index: Int, widthPx: Int, pageMode: PageMode, crop: Boolean, modifier: Modifier = Modifier,
    renderW: Int = widthPx,
) {
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val size = doc.sizes[index]
    var cropBox by remember(index, crop) { mutableStateOf<RectF?>(null) }
    LaunchedEffect(index, crop) { if (crop) cropBox = doc.contentBounds(index) }
    val area = cropBox ?: RectF(0f, 0f, size.width, size.height)
    val rot = vm.rotation
    val rotated = rot % 180 != 0
    val aspect = if (rotated) area.width() / area.height() else area.height() / area.width()
    val hPx = (widthPx * aspect).roundToInt().coerceAtLeast(1)
    val geo = remember(widthPx, area, rot) { PageGeo(widthPx.toFloat(), hPx.toFloat(), area) }
    // low-res first so big books appear instantly, then sharp
    // drawn at the settled zoom: while a pinch is moving, the current image is simply scaled, never thrown away
    val bmp by produceState<Bitmap?>(null, index, renderW, cropBox, rot) {
        if (value == null) value = doc.render(index, (renderW / 3).coerceAtLeast(64), cropBox, rot)
        doc.render(index, renderW, cropBox, rot)?.let { value = it }
    }
    val scope = rememberCoroutineScope()
    val annots = remember(vm.annotVersion, index) { vm.history.all.filter { it.page == index } }
    val hits = vm.hits.filter { it.page == index }
    val current = vm.hits.getOrNull(vm.hitIndex)
    val sel = vm.selection?.takeIf { it.page == index }
    val speakRects by produceState<List<RectF>>(emptyList(), vm.speech.current, vm.speech.page, index) {
        value = if (vm.speech.active && vm.speech.page == index && vm.speech.current.length > 8)
            doc.search(index, vm.speech.current.take(40), false, false).firstOrNull()?.rects.orEmpty() else emptyList()
    }
    val pageFields = vm.fields.filter { it.page == index }
    val editable = rot == 0

    Box(
        modifier.size(with(density) { widthPx.toDp() }, with(density) { hPx.toDp() })
            .onGloballyPositioned { coords = it }
            .shadow(1.dp).background(Color.White).testTag("page_$index")
            .pointerInput(vm.mode, vm.tool, editable, widthPx) {
                if (vm.mode == Mode.ANNOTATE && editable) {
                    when (vm.tool) {
                        Tool.NOTE -> detectTapGestures { o -> vm.noteAt = index to geo.toPoint(o) }
                        Tool.ERASER -> detectDragGestures(onDragStart = { o -> vm.eraseAt(index, geo.toPoint(o)) }) { ch, _ -> ch.consume(); vm.eraseAt(index, geo.toPoint(ch.position)) }
                        else -> {
                            var start: PointF? = null
                            val pts = ArrayList<PointF>()
                            detectDragGestures(
                                onDragStart = { o -> start = geo.toPoint(o); pts.clear(); pts += geo.toPoint(o) },
                                onDragEnd = {
                                    val s = start
                                    if (s != null && (vm.tool == Tool.HIGHLIGHT || vm.tool == Tool.UNDERLINE) && doc.hasRichText) {
                                        val end = pts.last()
                                        scope.launch {
                                            val r = doc.select(index, s, end)
                                            if (r != null) vm.addAnnot(Annot.Mark(index, r.rects, if (vm.tool == Tool.UNDERLINE) vm.penColor else vm.color, vm.tool == Tool.UNDERLINE))
                                            else if (pts.size > 1) vm.addAnnot(Annot.Ink(index, pts.toList(), vm.color, 14f, marker = true))
                                            vm.draft = null
                                        }
                                    } else if (pts.size > 1) {
                                        val marker = vm.tool != Tool.PEN
                                        vm.addAnnot(Annot.Ink(index, pts.toList(), if (marker) vm.color else vm.penColor, if (marker) 14f else 2.2f, marker))
                                        vm.draft = null
                                    } else vm.draft = null
                                },
                                onDragCancel = { vm.draft = null },
                            ) { ch, _ ->
                                ch.consume()
                                pts += geo.toPoint(ch.position)
                                val marker = vm.tool != Tool.PEN
                                vm.draft = Annot.Ink(index, pts.toList(), if (marker) vm.color else vm.penColor, if (marker) 14f else 2.2f, marker)
                            }
                        }
                    }
                } else {
                    detectTapGestures(
                        onTap = { if (!vm.touchIsTap()) Unit else if (vm.selection != null) vm.selection = null else vm.chrome = !vm.chrome },
                        onDoubleTap = { o -> if (vm.touchIsTap()) coords?.let { c -> vm.doubleTap?.invoke(c.localToRoot(o)) } },
                        onLongPress = { o -> if (editable && vm.touchIsTap()) vm.select(index, geo.toPoint(o)) },
                    )
                }
            },
    ) {
        bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), colorFilter = pageFilter(pageMode)) }
        if (editable) Canvas(Modifier.fillMaxSize()) {
            for (h in hits) for (r in h.rects) {
                val rr = geo.toPx(r)
                drawRect(if (h === current) Color(0xCCF2B54A) else Color(0x66F7E26B), rr.topLeft, rr.size)
            }
            for (r in speakRects) { val rr = geo.toPx(r); drawRect(Color(0x669BE7A8), rr.topLeft, rr.size) }
            for (an in annots + listOfNotNull(vm.draft?.takeIf { it.page == index })) drawAnnot(an, geo)
            sel?.let { s ->
                for (r in s.rects) { val rr = geo.toPx(r); drawRect(Color(0x5538A0FF), rr.topLeft, rr.size) }
            }
        }
        if (editable && pageFields.isNotEmpty()) FormLayer(vm, pageFields, geo)
        if (sel != null && editable) SelectionHandles(vm, sel, geo)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnot(a: Annot, geo: PageGeo) {
    when (a) {
        is Annot.Mark -> for (r in a.rects) {
            val rr = geo.toPx(r)
            if (a.underline) drawLine(Color(a.color), Offset(rr.left, rr.bottom), Offset(rr.right, rr.bottom), strokeWidth = 1.6f * geo.scale)
            else drawRect(Color(a.color).copy(alpha = 0.4f), rr.topLeft, rr.size)
        }
        is Annot.Ink -> if (a.points.size > 1) {
            val p = Path()
            a.points.forEachIndexed { i, pt -> val o = geo.toPx(pt); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            drawPath(p, Color(a.color).copy(alpha = if (a.marker) 0.4f else 1f), style = Stroke(width = a.width * geo.scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        is Annot.Note -> {
            val o = geo.toPx(a.at); val s = Annot.NOTE_SIZE * geo.scale
            drawRoundRect(Color(0xFFF7D952), o, Size(s, s), CornerRadius(s * 0.15f))
            for (i in 1..3) drawLine(Color(0xFF735C0D), Offset(o.x + s * 0.22f, o.y + s * 0.22f * i + 2), Offset(o.x + s * 0.78f, o.y + s * 0.22f * i + 2), 1.2f * geo.scale)
        }
    }
}

/** Two round handles to stretch the selection, plus the action popup above it. */
@Composable
private fun BoxScope.SelectionHandles(vm: ReaderVm, sel: Selection, geo: PageGeo) {
    val density = LocalDensity.current
    var start by remember(sel.page, sel.text.isEmpty()) { mutableStateOf(geo.toPx(sel.start)) }
    var end by remember(sel.page, sel.text.isEmpty()) { mutableStateOf(geo.toPx(sel.end)) }
    val handle = 22.dp
    val hpx = with(density) { handle.toPx() }
    @Composable
    fun H(pos: Offset, onMove: (Offset) -> Unit, tag: String) {
        Box(Modifier.offset { IntOffset((pos.x - hpx / 2).roundToInt(), (pos.y + 4).roundToInt()) }.size(handle).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary).testTag(tag)
            .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); onMove(d) } })
    }
    H(start, { d -> start += d; vm.moveSelection(geo.toPoint(start), geo.toPoint(end)) }, "sel_start")
    H(end, { d -> end += d; vm.moveSelection(geo.toPoint(start), geo.toPoint(end)) }, "sel_end")
    val top = sel.rects.minOfOrNull { geo.toPx(it).top } ?: 0f
    val popupY = with(density) { (top - 64.dp.toPx()).coerceAtLeast(0f) }
    SelectionPopup(vm, Modifier.align(Alignment.TopCenter).offset { IntOffset(0, popupY.roundToInt()) })
}

/** Form fields drawn over the page: tap to type or tick. Changes are saved with "Save". */
@Composable
private fun FormLayer(vm: ReaderVm, fields: List<FormField>, geo: PageGeo) {
    val density = LocalDensity.current
    for (f in fields) {
        if (f.kind == FormField.Kind.OTHER) continue
        val r = geo.toPx(f.rect)
        val mod = Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
        when (f.kind) {
            FormField.Kind.TEXT -> {
                val edited = vm.formValues[f.name]
                var focused by remember { mutableStateOf(false) }
                val showBox = edited != null || focused
                BasicTextField(
                    value = edited ?: f.value, onValueChange = { if (!f.readOnly) vm.formValues[f.name] = it },
                    readOnly = f.readOnly, singleLine = !f.multiline,
                    textStyle = TextStyle(fontSize = (minOf(r.height * 0.6f, 14f * geo.scale) / density.density / density.fontScale).sp, color = if (showBox) Color(0xFF1A1A1A) else Color.Transparent),
                    modifier = mod.background(if (showBox) Color(0xFFEFF6FF) else Color(0x1A386947))
                        .border(1.dp, if (focused) Color(0xFF386947) else Color(0x55386947))
                        .padding(horizontal = 2.dp)
                        .onFocusChanged { focused = it.isFocused }.testTag("field_${f.name}"),
                )
            }
            FormField.Kind.CHECKBOX -> {
                val on = vm.formChecks[f.name] ?: f.checked
                Box(mod.border(1.dp, Color(0x88386947)).background(if (vm.formChecks.containsKey(f.name)) Color.White else Color.Transparent)
                    .pointerInput(on) { detectTapGestures { if (!f.readOnly) vm.formChecks[f.name] = !on } }.testTag("check_${f.name}"),
                    contentAlignment = Alignment.Center) {
                    if (vm.formChecks.containsKey(f.name) && on) Text("✓", color = Color(0xFF1A1A1A), fontSize = (r.height * 0.7f / density.density / density.fontScale).sp)
                }
            }
            else -> {}
        }
    }
}


/** Height of a row of pages at a zoom, the same sum PageView does (crop margins aside). */
fun rowHeightPx(doc: PdfDoc, vm: ReaderVm, pages: List<Int>, viewW: Int, zoom: Float, gap: Int): Float {
    val pageW = ((viewW * zoom - gap * (pages.size + 1)) / pages.size).coerceAtLeast(10f)
    return pages.maxOf { i ->
        val sz = doc.sizes[i]
        pageW * (if (vm.rotation % 180 != 0) sz.width / sz.height else sz.height / sz.width)
    }
}

/**
 * Vertical list of pages (or two per row on wide screens). Zoom resizes the pages for real; [ox] is how far the
 * zoomed pages are moved sideways. Pages shorter than the screen sit in the middle.
 */
@Composable
fun ScrollPages(vm: ReaderVm, doc: PdfDoc, list: LazyListState, ox: Float, viewW: Int, pageMode: PageMode, crop: Boolean, spread: Boolean) {
    val density = LocalDensity.current
    val gap = with(density) { 10.dp.roundToPx() }
    val perRow = if (spread) 2 else 1
    fun pageWidth(z: Float) = ((viewW * z - gap * (perRow + 1)) / perRow).roundToInt().coerceAtLeast(10)
    val contentW = (viewW * vm.zoom).roundToInt()
    val pageW = pageWidth(vm.zoom)
    val renderW = pageWidth(vm.renderZoom)
    val rows = (0 until doc.pageCount).chunked(perRow)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            state = list,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                .width(with(density) { contentW.toDp() }).fillMaxHeight()
                .offset { IntOffset(-ox.roundToInt(), 0) }
                .testTag("pages"),
            contentPadding = PaddingValues(top = 104.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            userScrollEnabled = vm.mode != Mode.ANNOTATE,
        ) {
            items(rows.size, key = { it }) { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(with(density) { gap.toDp() }, Alignment.CenterHorizontally)) {
                    for (i in rows[r]) PageView(vm, doc, i, pageW, pageMode, crop, renderW = renderW)
                }
            }
        }
    }
}

/** Where a page sits on screen in page-by-page mode: centred when smaller than the screen, else moved by the pan. */
data class PagedGeo(val w: Float, val h: Float, val x: Float, val y: Float)

fun pagedGeo(doc: PdfDoc, vm: ReaderVm, i: Int, viewW: Int, viewH: Int, zoom: Float, ox: Float, oy: Float): PagedGeo {
    val sz = doc.sizes[i]
    val rotated = vm.rotation % 180 != 0
    val aspect = if (rotated) sz.width / sz.height else sz.height / sz.width
    val fitW = minOf(viewW.toFloat(), viewH * 0.86f / aspect)
    val w = fitW * zoom; val h = w * aspect
    val x = if (w <= viewW) (viewW - w) / 2f else -ox.coerceIn(0f, w - viewW)
    val y = if (h <= viewH) (viewH - h) / 2f else -oy.coerceIn(0f, h - viewH)
    return PagedGeo(w, h, x, y)
}

/** One page per screen, swiped sideways; zoomed pages pan with one finger. */
@Composable
fun PagedPages(vm: ReaderVm, doc: PdfDoc, pager: PagerState, viewW: Int, viewH: Int, ox: Float, oy: Float, pageMode: PageMode, crop: Boolean) {
    HorizontalPager(pager, Modifier.fillMaxSize().testTag("pager"), userScrollEnabled = vm.mode != Mode.ANNOTATE && vm.zoom <= 1.01f, beyondViewportPageCount = 1) { i ->
        val current = i == pager.currentPage
        val g = pagedGeo(doc, vm, i, viewW, viewH, if (current) vm.zoom else 1f, if (current) ox else 0f, if (current) oy else 0f)
        val rw = pagedGeo(doc, vm, i, viewW, viewH, if (current) vm.renderZoom else 1f, 0f, 0f).w
        Box(Modifier.fillMaxSize().clipToBounds()) {
            PageView(vm, doc, i, g.w.roundToInt(), pageMode, crop,
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).offset { IntOffset(g.x.roundToInt(), g.y.roundToInt()) },
                renderW = rw.roundToInt())
        }
    }
}
