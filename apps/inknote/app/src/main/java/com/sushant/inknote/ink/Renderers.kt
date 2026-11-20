package com.sushant.inknote.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.sushant.inknote.data.Brush
import com.sushant.inknote.data.Paper
import com.sushant.inknote.data.Stroke
import com.sushant.inknote.data.Tool

/** Colours used to draw a page; differ between light and dark theme. */
data class PagePalette(
    val paper: Int, val line: Int, val label: Int, val dark: Boolean,
) {
    companion object {
        val LIGHT = PagePalette(Color.WHITE, 0xFFDCE6F7.toInt(), 0xFF8A8478.toInt(), false)
        val DARK = PagePalette(0xFF1E2024.toInt(), 0xFF2D3A52.toInt(), 0xFF8C877E.toInt(), true)
    }
}

object PaperRenderer {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f }
    private const val GAP = 40f
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Draws paper in page units (canvas already scaled), area 0..w x 0..h. */
    fun draw(c: Canvas, paper: String, w: Float, h: Float, pal: PagePalette) {
        c.drawColor(pal.paper)
        line.color = pal.line; fill.color = pal.line; text.color = pal.label
        when (paper) {
            Paper.LINED -> { var y = GAP * 3; while (y < h - GAP) { c.drawLine(GAP, y, w - GAP, y, line); y += GAP } }
            Paper.GRID -> {
                var x = GAP; while (x < w) { c.drawLine(x, 0f, x, h, line); x += GAP }
                var y = GAP; while (y < h) { c.drawLine(0f, y, w, y, line); y += GAP }
            }
            Paper.DOTTED -> { var y = GAP; while (y < h) { var x = GAP; while (x < w) { c.drawCircle(x, y, 2.4f, fill); x += GAP }; y += GAP } }
            Paper.CORNELL -> {
                val top = h * 0.09f; val bottom = h * 0.8f; val cue = w * 0.28f
                c.drawLine(0f, top, w, top, line); c.drawLine(0f, bottom, w, bottom, line); c.drawLine(cue, top, cue, bottom, line)
                var y = top + GAP; while (y < bottom) { c.drawLine(cue + 12f, y, w - GAP, y, line); y += GAP }
                c.drawText("Cues", 24f, top + 32f, text); c.drawText("Notes", cue + 24f, top + 32f, text); c.drawText("Summary", 24f, bottom + 32f, text)
            }
            Paper.TODO -> {
                var y = 140f
                while (y < h - 60f) {
                    c.drawRoundRect(RectF(60f, y - 28f, 88f, y), 4f, 4f, line)
                    c.drawLine(110f, y, w - 60f, y, line); y += 64f
                }
            }
            Paper.PLANNER -> {
                val top = 90f; val bh = (h - top - 40f) / 7f
                for (i in 0 until 7) {
                    val y = top + i * bh
                    c.drawRoundRect(RectF(40f, y + 6f, w - 40f, y + bh - 6f), 10f, 10f, line)
                    c.drawText(DAYS[i], 60f, y + 36f, text)
                }
            }
            Paper.TIMETABLE -> {
                val cols = 7; val rows = 9; val l = 40f; val t = 80f; val r = w - 40f; val b = h - 60f
                val cw = (r - l) / cols; val rh = (b - t) / rows
                for (i in 0..cols) c.drawLine(l + i * cw, t, l + i * cw, b, line)
                for (j in 0..rows) c.drawLine(l, t + j * rh, r, t + j * rh, line)
                c.drawText("Time", l + 10f, t + rh / 2 + 8f, text)
                for (i in 1 until cols) c.drawText(DAYS[i - 1], l + i * cw + 10f, t + rh / 2 + 8f, text)
            }
        }
    }
}

object StrokeRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    fun displayColor(color: Int, dark: Boolean): Int {
        if (!dark) return color
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        if (hsv[2] < 0.65f) { hsv[2] = 0.93f; hsv[1] = hsv[1] * 0.55f }
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    fun draw(c: Canvas, s: Stroke, dark: Boolean, fade: Boolean = false) {
        if (s.size == 0) return
        val col = displayColor(s.color, dark)
        val baseAlpha = (s.alpha * (if (fade) 0.22f else 1f) * 255).toInt().coerceIn(0, 255)
        p.color = col; p.alpha = baseAlpha
        if (s.tool == Tool.HIGHLIGHTER) {
            p.strokeCap = Paint.Cap.SQUARE; p.strokeWidth = s.width
            p.alpha = (baseAlpha * 0.42f).toInt()
            buildPath(s); c.drawPath(path, p); p.strokeCap = Paint.Cap.ROUND; return
        }
        when (s.brush) {
            Brush.FOUNTAIN, Brush.BRUSH -> {
                val lo = if (s.brush == Brush.BRUSH) 0.15f else 0.45f
                val hi = if (s.brush == Brush.BRUSH) 1.9f else 1.15f
                if (s.size == 1) { p.style = Paint.Style.FILL; c.drawCircle(s.x(0), s.y(0), s.width * (lo + hi * s.p(0)) / 2, p); p.style = Paint.Style.STROKE; return }
                for (i in 1 until s.size) {
                    p.strokeWidth = s.width * (lo + hi * (s.p(i - 1) + s.p(i)) / 2f)
                    c.drawLine(s.x(i - 1), s.y(i - 1), s.x(i), s.y(i), p)
                }
            }
            else -> {
                p.strokeWidth = if (s.brush == Brush.PENCIL) s.width * 0.8f else s.width
                if (s.brush == Brush.PENCIL) p.alpha = (baseAlpha * 0.8f).toInt()
                if (s.size == 1) { p.style = Paint.Style.FILL; c.drawCircle(s.x(0), s.y(0), p.strokeWidth / 2, p); p.style = Paint.Style.STROKE; return }
                buildPath(s); c.drawPath(path, p)
            }
        }
    }

    private fun buildPath(s: Stroke) {
        path.reset(); path.moveTo(s.x(0), s.y(0))
        if (s.size == 2) { path.lineTo(s.x(1), s.y(1)); return }
        for (i in 1 until s.size - 1) {
            val mx = (s.x(i) + s.x(i + 1)) / 2; val my = (s.y(i) + s.y(i + 1)) / 2
            path.quadTo(s.x(i), s.y(i), mx, my)
        }
        path.lineTo(s.x(s.size - 1), s.y(s.size - 1))
    }

    /** Highlighters first so they sit under the ink. */
    fun drawAll(c: Canvas, strokes: List<Stroke>, dark: Boolean, fadeAfter: Pair<Long, Long>? = null) {
        for (pass in 0..1) for (s in strokes) {
            if ((s.tool == Tool.HIGHLIGHTER) != (pass == 0)) continue
            val fade = fadeAfter != null && s.recId == fadeAfter.first && s.recMs > fadeAfter.second
            draw(c, s, dark, fade)
        }
    }
}
