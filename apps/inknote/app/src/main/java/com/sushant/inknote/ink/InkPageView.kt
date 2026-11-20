package com.sushant.inknote.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.sushant.inknote.data.Brush
import com.sushant.inknote.data.Geometry
import com.sushant.inknote.data.Ids
import com.sushant.inknote.data.NoteFiles
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.Stroke
import com.sushant.inknote.data.Tool
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class EditTool { PEN, HIGHLIGHTER, ERASER, LASSO, SHAPE, TEXT }

data class ToolState(
    val tool: EditTool = EditTool.PEN,
    val brush: Int = Brush.BALL,
    val color: Int = 0xFF1F3F9A.toInt(),
    val width: Float = 4f,
    val alpha: Float = 1f,
    val hlColor: Int = 0xFFFFD43B.toInt(),
    val hlWidth: Float = 26f,
    val eraserArea: Boolean = false,
    val eraserSize: Float = 18f,
)

data class Selection(
    val pageId: Long,
    val strokes: Set<Long>,
    val texts: Set<Long>,
    val images: Set<Long>,
    val bounds: FloatArray,
) { val isEmpty get() = strokes.isEmpty() && texts.isEmpty() && images.isEmpty() }

interface PageListener {
    fun onCommit(pageId: Long, content: PageContent)
    fun onSelect(pageId: Long, sel: Selection?)
    fun onTextTap(pageId: Long, x: Float, y: Float, hitTextId: Long?)
    fun onStylusSeen()
    fun onNeedSpace(pageId: Long)
    /** Current recording id and position in ms, or (-1, -1) when not recording. */
    fun stamp(): Pair<Long, Long>
}

/** One page: paper, PDF background, images, text, ink — plus all pen input. Coordinates are page units scaled to the view width. */
@SuppressLint("ViewConstructor")
class InkPageView(context: Context, private val files: NoteFiles) : View(context) {
    var noteId = 0L
    var page: PageEntity? = null
        set(v) { val changed = field?.paper != v?.paper || field?.width != v?.width || field?.height != v?.height || field?.id != v?.id; field = v; if (changed) dirty() }
    var content = PageContent()
        set(v) { if (field !== v) { field = v; dirty() } }
    var palette = PagePalette.LIGHT
        set(v) { if (field != v) { field = v; dirty() } }
    var tools = ToolState()
    var stylusOnly = false
    var shapeHold = true
    var readOnly = false
    var listener: PageListener? = null
    var selection: Selection? = null
        set(v) { field = v; invalidate() }
    var editingTextId: Long = -1
        set(v) { if (field != v) { field = v; dirty() } }
    /** (recordingId, positionMs) while playing back: strokes written later are shown faded. */
    var playback: Pair<Long, Long>? = null
        set(v) { if (field != v) { field = v; dirty() } }

    private val scale get() = if (page == null) 1f else width / page!!.width

    private var cache: Bitmap? = null
    private var cacheValid = false
    private fun dirty() { cacheValid = false; invalidate() }

    // ---- live input state ----
    private val live = ArrayList<Float>()
    private var liveStroke: Stroke? = null
    private var drawing = false
    private var lastMoveAt = 0L
    private var lastMoveX = 0f; private var lastMoveY = 0f
    private var snapped: FloatArray? = null
    private val lasso = ArrayList<Float>()
    private var eraseWork: MutableList<Stroke>? = null
    private var eraseBase: PageContent? = null
    private var eraserPos: Pair<Float, Float>? = null
    private enum class Drag { NONE, MOVE, RESIZE }
    private var drag = Drag.NONE
    private var dragStartX = 0f; private var dragStartY = 0f; private var dragDx = 0f; private var dragDy = 0f; private var dragScale = 1f
    private var downX = 0f; private var downY = 0f
    private var activeTool = EditTool.PEN

    private val holdCheck = Runnable {
        if (drawing && shapeHold && activeTool == EditTool.PEN && snapped == null && SystemClock.uptimeMillis() - lastMoveAt >= 550) trySnap()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF2B5BD7.toInt(); pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f) }
    private val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x142B5BD7 }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B5BD7.toInt() }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF888888.toInt() }
    private val path = Path()

    init { isFocusable = false; setLayerType(LAYER_TYPE_HARDWARE, null) }

    // ================= drawing =================
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); cache?.recycle(); cache = null; dirty() }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(holdCheck); cache?.recycle(); cache = null; cacheValid = false }

    private fun useCache() = width > 0 && height > 0 && width.toLong() * height <= 6_500_000L

    override fun onDraw(c: Canvas) {
        val p = page ?: return
        if (useCache()) {
            if (cache == null || cache!!.width != width || cache!!.height != height) { cache?.recycle(); cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); cacheValid = false }
            if (!cacheValid) { val cc = Canvas(cache!!); drawPage(cc, p); cacheValid = true }
            c.drawBitmap(cache!!, 0f, 0f, paint)
        } else drawPage(c, p)
        val s = scale
        c.save(); c.scale(s, s)
        liveStroke?.let { StrokeRenderer.draw(c, it, palette.dark) }
        if (lasso.size >= 4) {
            path.reset(); path.moveTo(lasso[0], lasso[1]); var i = 2
            while (i < lasso.size) { path.lineTo(lasso[i], lasso[i + 1]); i += 2 }
            selPaint.strokeWidth = 3f / s; c.drawPath(path, selPaint)
        }
        selection?.takeIf { it.pageId == p.id }?.let { sel ->
            val b = sel.bounds
            val r = RectF(b[0] + dragDx, b[1] + dragDy, 0f, 0f)
            r.right = r.left + (b[2] - b[0]) * dragScale; r.bottom = r.top + (b[3] - b[1]) * dragScale
            if (drag != Drag.NONE) drawSelectionPreview(c, sel)
            c.drawRect(r, selFill); selPaint.strokeWidth = 3f / s; c.drawRect(r, selPaint)
            c.drawCircle(r.right, r.bottom, 14f / s, handle)
        }
        eraserPos?.let { (x, y) -> eraserPaint.strokeWidth = 2f / s; c.drawCircle(x, y, tools.eraserSize, eraserPaint) }
        c.restore()
    }

    private fun drawSelectionPreview(c: Canvas, sel: Selection) {
        val b = sel.bounds
        c.save()
        c.translate(b[0] + dragDx, b[1] + dragDy); c.scale(dragScale, dragScale); c.translate(-b[0], -b[1])
        for (st in content.strokes) if (st.id in sel.strokes) StrokeRenderer.draw(c, st, palette.dark)
        c.restore()
    }

    private fun drawPage(c: Canvas, p: PageEntity) {
        val hidden = selection?.takeIf { it.pageId == p.id && drag != Drag.NONE }
        PageRenderer.draw(c, files, noteId, p, content, palette, scale, sync = false, playback = playback,
            hideText = editingTextId, hidden = hidden) { dirty() }
    }

    fun textHeight(stored: String, w: Float): Float = PageRenderer.textHeight(stored, w)

    // ================= input =================
    private fun toPage(v: Float) = v / scale

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = page ?: return false
        if (readOnly) return false
        val toolType = e.getToolType(e.actionIndex.coerceAtMost(e.pointerCount - 1))
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (e.actionMasked == MotionEvent.ACTION_DOWN && isStylus) listener?.onStylusSeen()
        if (e.actionMasked == MotionEvent.ACTION_DOWN && stylusOnly && !isStylus && tools.tool != EditTool.TEXT && tools.tool != EditTool.LASSO) return false
        if (e.pointerCount > 1 || e.actionMasked == MotionEvent.ACTION_CANCEL) { cancelGesture(); return false }
        activeTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER || (isStylus && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)) EditTool.ERASER else tools.tool
        val x = toPage(e.x).coerceIn(0f, p.width); val y = toPage(e.y).coerceIn(0f, p.height)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); downX = x; downY = y; down(x, y, e) }
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP -> up(x, y)
        }
        return true
    }

    private fun cancelGesture() {
        removeCallbacks(holdCheck)
        drawing = false; liveStroke = null; live.clear(); lasso.clear(); snapped = null; eraserPos = null
        eraseWork = null; drag = Drag.NONE; dragDx = 0f; dragDy = 0f; dragScale = 1f
        invalidate()
    }

    private fun pressure(e: MotionEvent, i: Int = -1): Float {
        val raw = if (i < 0) e.pressure else e.getHistoricalPressure(i)
        return if (e.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) 0.5f else raw.coerceIn(0.05f, 1f)
    }

    private fun down(x: Float, y: Float, e: MotionEvent) {
        when (activeTool) {
            EditTool.PEN, EditTool.HIGHLIGHTER, EditTool.SHAPE -> {
                drawing = true; snapped = null; live.clear(); live += x; live += y; live += pressure(e)
                lastMoveAt = SystemClock.uptimeMillis(); lastMoveX = x; lastMoveY = y
                rebuildLive()
            }
            EditTool.ERASER -> { eraseBase = content; eraseWork = content.strokes.toMutableList(); eraseAt(x, y) }
            EditTool.LASSO -> {
                val sel = selection?.takeIf { it.pageId == page?.id }
                if (sel != null) {
                    val b = sel.bounds
                    val hr = 34f / scale
                    if (hypot(x - b[2], y - b[3]) < hr) { drag = Drag.RESIZE; dragStartX = x; dragStartY = y; return }
                    if (x in b[0]..b[2] && y in b[1]..b[3]) { drag = Drag.MOVE; dragStartX = x; dragStartY = y; dirty(); return }
                    listener?.onSelect(page!!.id, null)
                }
                lasso.clear(); lasso += x; lasso += y
            }
            EditTool.TEXT -> Unit
        }
    }

    private fun move(e: MotionEvent) {
        val s = scale
        when (activeTool) {
            EditTool.PEN, EditTool.HIGHLIGHTER, EditTool.SHAPE -> {
                if (!drawing || snapped != null) return
                for (h in 0 until e.historySize) addPoint(e.getHistoricalX(h) / s, e.getHistoricalY(h) / s, pressure(e, h))
                addPoint(e.x / s, e.y / s, pressure(e))
                rebuildLive(); invalidate()
            }
            EditTool.ERASER -> { for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(h) / s, e.getHistoricalY(h) / s); eraseAt(e.x / s, e.y / s) }
            EditTool.LASSO -> {
                val x = e.x / s; val y = e.y / s
                when (drag) {
                    Drag.MOVE -> { dragDx = x - dragStartX; dragDy = y - dragStartY; dirtyIfNeeded() }
                    Drag.RESIZE -> {
                        val b = selection!!.bounds
                        val w0 = max(1f, b[2] - b[0]); val h0 = max(1f, b[3] - b[1])
                        dragScale = max(0.15f, min((x - b[0]) / w0, (y - b[1]) / h0)).coerceAtMost(6f); dirtyIfNeeded()
                    }
                    Drag.NONE -> { lasso += x; lasso += y; invalidate() }
                }
            }
            EditTool.TEXT -> Unit
        }
    }

    private var previewDirty = false
    private fun dirtyIfNeeded() { if (!previewDirty) { previewDirty = true; cacheValid = false }; invalidate() }

    private fun addPoint(x: Float, y: Float, pr: Float) {
        val p = page ?: return
        val cx = x.coerceIn(0f, p.width); val cy = y.coerceIn(0f, p.height)
        val n = live.size
        if (n >= 3 && hypot(cx - live[n - 3], cy - live[n - 2]) < 0.8f) return
        live += cx; live += cy; live += pr
        if (hypot(cx - lastMoveX, cy - lastMoveY) > 6f) {
            lastMoveAt = SystemClock.uptimeMillis(); lastMoveX = cx; lastMoveY = cy
            removeCallbacks(holdCheck); postDelayed(holdCheck, 600)
        }
    }

    private fun rebuildLive() {
        val t = tools
        val hl = activeTool == EditTool.HIGHLIGHTER
        liveStroke = Stroke(0, if (hl) Tool.HIGHLIGHTER else Tool.PEN, if (hl) Brush.BALL else if (activeTool == EditTool.SHAPE) Brush.BALL else t.brush,
            if (hl) t.hlColor else t.color, if (hl) t.hlWidth else t.width, if (hl) 1f else t.alpha, snapped ?: live.toFloatArray())
    }

    private fun xyOf(pts: FloatArray) = FloatArray(pts.size / 3 * 2) { pts[(it / 2) * 3 + it % 2] }

    private fun trySnap() {
        val r = Geometry.recognize(xyOf(live.toFloatArray())) ?: return
        snapped = Geometry.toStrokePoints(r.xy)
        rebuildLive(); invalidate()
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun up(x: Float, y: Float) {
        removeCallbacks(holdCheck)
        val p = page ?: return
        when (activeTool) {
            EditTool.PEN, EditTool.HIGHLIGHTER, EditTool.SHAPE -> {
                if (!drawing) return
                drawing = false
                if (activeTool == EditTool.SHAPE && snapped == null) Geometry.recognize(xyOf(live.toFloatArray()))?.let { snapped = Geometry.toStrokePoints(it.xy) }
                rebuildLive()
                val st = liveStroke ?: return
                val (rid, rms) = listener?.stamp() ?: (-1L to -1L)
                val committed = st.copy(id = Ids.next(), recId = rid, recMs = rms)
                liveStroke = null; live.clear(); snapped = null
                val newContent = content.copy(strokes = content.strokes + committed)
                if (useCache() && cacheValid && cache != null) {
                    val cc = Canvas(cache!!); cc.scale(scale, scale); StrokeRenderer.draw(cc, committed, palette.dark)
                    field_setContentNoDirty(newContent)
                } else content = newContent
                invalidate()
                listener?.onCommit(p.id, newContent)
                val b = committed.bounds()
                if (b[3] > p.height * 0.85f) listener?.onNeedSpace(p.id)
            }
            EditTool.ERASER -> {
                val base = eraseBase; eraseWork = null; eraserPos = null; eraseBase = null
                if (base != null && content !== base) listener?.onCommit(p.id, content)
                invalidate()
            }
            EditTool.LASSO -> {
                val sel = selection
                if (drag != Drag.NONE && sel != null) {
                    val moved = applyTransform(sel)
                    drag = Drag.NONE; dragDx = 0f; dragDy = 0f; dragScale = 1f; previewDirty = false
                    if (moved != null) { content = moved.first; listener?.onCommit(p.id, moved.first); listener?.onSelect(p.id, moved.second) }
                    dirty(); return
                }
                val poly = lasso.toFloatArray(); lasso.clear()
                if (poly.size >= 6) {
                    val strokes = content.strokes.filter { Geometry.strokeInLasso(it, poly) }
                    val texts = content.texts.filter { Geometry.rectInLasso(it.x, it.y, it.x + it.w, it.y + textHeight(it.html, it.w), poly) }
                    val images = content.images.filter { Geometry.rectInLasso(it.x, it.y, it.x + it.w, it.y + it.h, poly) }
                    val boxes = strokes.map { it.bounds() } + texts.map { floatArrayOf(it.x, it.y, it.x + it.w, it.y + textHeight(it.html, it.w)) } +
                        images.map { floatArrayOf(it.x, it.y, it.x + it.w, it.y + it.h) }
                    val b = Geometry.unionBounds(boxes)
                    listener?.onSelect(p.id, if (b == null) null else Selection(p.id, strokes.map { it.id }.toSet(), texts.map { it.id }.toSet(), images.map { it.id }.toSet(), b))
                }
                invalidate()
            }
            EditTool.TEXT -> {
                if (hypot(x - downX, y - downY) < 20f) {
                    val hit = content.texts.lastOrNull { x in it.x..(it.x + it.w) && y in it.y..(it.y + max(40f, textHeight(it.html, it.w))) }
                    listener?.onTextTap(p.id, x, y, hit?.id)
                }
            }
        }
    }

    private fun field_setContentNoDirty(c: PageContent) {
        // keep the incrementally-updated cache: swap the content without invalidating it
        val valid = cacheValid
        content = c
        cacheValid = valid
    }

    /** Applies the pending move / resize to the selected items. */
    private fun applyTransform(sel: Selection): Pair<PageContent, Selection>? {
        if (dragDx == 0f && dragDy == 0f && dragScale == 1f) return null
        val b = sel.bounds
        val k = dragScale; val ox = b[0]; val oy = b[1]
        fun tx(v: Float) = ox + (v - ox) * k + dragDx
        fun ty(v: Float) = oy + (v - oy) * k + dragDy
        val strokes = content.strokes.map { s -> if (s.id in sel.strokes) s.transformed(k, k, ox - ox * k + dragDx, oy - oy * k + dragDy, k) else s }
        val texts = content.texts.map { t -> if (t.id in sel.texts) t.copy(x = tx(t.x), y = ty(t.y), w = t.w * k, h = t.h * k) else t }
        val images = content.images.map { i -> if (i.id in sel.images) i.copy(x = tx(i.x), y = ty(i.y), w = i.w * k, h = i.h * k) else i }
        val nb = floatArrayOf(tx(b[0]), ty(b[1]), tx(b[2]), ty(b[3]))
        return PageContent(strokes, texts, images) to sel.copy(bounds = nb)
    }

    private fun eraseAt(x: Float, y: Float) {
        val w = eraseWork ?: return
        eraserPos = x to y
        val r = tools.eraserSize
        var changed = false
        if (tools.eraserArea) {
            val out = ArrayList<Stroke>(w.size)
            for (s in w) { val parts = Geometry.eraseArea(s, x, y, r) { Ids.next() }; if (parts == null) out += s else { out += parts; changed = true } }
            if (changed) { w.clear(); w.addAll(out) }
        } else changed = w.removeAll { Geometry.strokeHit(it, x, y, r) }
        if (changed) { content = content.copy(strokes = w.toList()) }
        invalidate()
    }

    /** Returns the stroke (recording-stamped) nearest to a tap, for playback seeking. */
    fun strokeAt(viewX: Float, viewY: Float): Stroke? {
        val x = toPage(viewX); val y = toPage(viewY)
        return content.strokes.lastOrNull { it.recId >= 0 && Geometry.strokeHit(it, x, y, 14f) }
    }

}
