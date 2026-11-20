package com.sushant.inknote

import com.sushant.inknote.data.Geometry
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageSize
import com.sushant.inknote.data.Stroke
import com.sushant.inknote.data.TextBox
import com.sushant.inknote.ml.Handwriting
import com.sushant.inknote.ui.Dates
import com.sushant.inknote.ui.Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private fun circle(cx: Float, cy: Float, r: Float, n: Int = 60, wobble: Float = 3f) = FloatArray((n + 1) * 2) {
        val a = (it / 2) * 2 * Math.PI / n; val rr = r + if ((it / 2) % 3 == 0) wobble else -wobble
        if (it % 2 == 0) (cx + rr * cos(a)).toFloat() else (cy + rr * sin(a)).toFloat()
    }

    private fun poly(vararg p: Float, steps: Int = 12): FloatArray {
        val out = ArrayList<Float>()
        for (i in 0 until p.size / 2 - 1) for (s in 0 until steps) {
            val f = s / steps.toFloat()
            out += p[i * 2] + (p[i * 2 + 2] - p[i * 2]) * f + (if (s % 2 == 0) 1.5f else -1.5f)
            out += p[i * 2 + 1] + (p[i * 2 + 3] - p[i * 2 + 1]) * f
        }
        out += p[p.size - 2]; out += p[p.size - 1]
        return out.toFloatArray()
    }

    @Test fun roughCircleBecomesCircle() {
        val r = Geometry.recognize(circle(300f, 300f, 100f))
        assertNotNull(r); assertEquals(Geometry.Shape.CIRCLE, r!!.shape)
    }

    @Test fun ellipseIsNotCircle() {
        val xy = FloatArray(122) { val a = (it / 2) * 2 * Math.PI / 60; if (it % 2 == 0) (300 + 200 * cos(a)).toFloat() else (300 + 80 * sin(a)).toFloat() }
        assertEquals(Geometry.Shape.ELLIPSE, Geometry.recognize(xy)!!.shape)
    }

    @Test fun straightStrokeBecomesLine() {
        val xy = FloatArray(40) { if (it % 2 == 0) it * 10f else 100f + (if (it % 4 == 1) 1f else -1f) }
        val r = Geometry.recognize(xy)!!
        assertEquals(Geometry.Shape.LINE, r.shape); assertEquals(4, r.xy.size)
    }

    @Test fun roughSquareBecomesRectangle() {
        val r = Geometry.recognize(poly(100f, 100f, 400f, 100f, 400f, 400f, 100f, 400f, 100f, 102f))
        assertNotNull(r); assertEquals(Geometry.Shape.RECTANGLE, r!!.shape)
    }

    @Test fun triangleRecognised() {
        val r = Geometry.recognize(poly(100f, 400f, 250f, 100f, 400f, 400f, 102f, 400f))
        assertNotNull(r); assertEquals(Geometry.Shape.TRIANGLE, r!!.shape)
    }

    @Test fun scribbleIsNotAShape() {
        val xy = FloatArray(200) { if (it % 2 == 0) (it * 3f) % 200 else ((it * 37) % 150).toFloat() }
        assertNull(Geometry.recognize(xy))
    }

    @Test fun pointInPolygon() {
        val sq = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(Geometry.pointInPolygon(5f, 5f, sq)); assertFalse(Geometry.pointInPolygon(15f, 5f, sq))
    }

    @Test fun lassoSelectsStrokesInside() {
        val inside = Stroke(1, 0, 0, 0, 2f, 1f, floatArrayOf(20f, 20f, .5f, 30f, 30f, .5f, 40f, 25f, .5f))
        val outside = Stroke(2, 0, 0, 0, 2f, 1f, floatArrayOf(200f, 200f, .5f, 210f, 210f, .5f))
        val loop = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        assertTrue(Geometry.strokeInLasso(inside, loop)); assertFalse(Geometry.strokeInLasso(outside, loop))
    }

    @Test fun areaEraserSplitsStroke() {
        val pts = FloatArray(30) { if (it % 3 == 0) (it / 3) * 10f else if (it % 3 == 1) 50f else .5f } // 10 points along y=50
        val s = Stroke(1, 0, 0, 0, 2f, 1f, pts)
        var id = 100L
        val parts = Geometry.eraseArea(s, 45f, 50f, 6f) { id++ }!!
        assertEquals(2, parts.size)
        assertTrue(parts.all { p -> (0 until p.size).none { kotlin.math.abs(p.x(it) - 45f) < 6f } })
        assertNull(Geometry.eraseArea(s, 500f, 500f, 6f) { id++ })
    }

    @Test fun strokeHitUsesWidth() {
        val s = Stroke(1, 0, 0, 0, 10f, 1f, floatArrayOf(0f, 0f, .5f, 100f, 0f, .5f))
        assertTrue(Geometry.strokeHit(s, 50f, 7f, 3f)); assertFalse(Geometry.strokeHit(s, 50f, 30f, 3f))
    }

    @Test fun turningPageFitsContentWithoutCropping() {
        val (pw, ph) = PageSize.of("PORTRAIT"); val (lw, lh) = PageSize.of("LANDSCAPE")
        val k = Geometry.fitScale(pw, ph, lw, lh)
        val c = PageContent(listOf(Stroke(1, 0, 0, 0, 4f, 1f, floatArrayOf(pw, ph, .5f))), listOf(TextBox(2, 0f, ph - 100, pw, 100f, "", "")))
        val t = c.transformed(k, k)
        assertTrue(t.strokes[0].x(0) <= lw + 0.01f && t.strokes[0].y(0) <= lh + 0.01f)
        assertTrue(t.texts[0].y + t.texts[0].h <= lh + 0.01f)
        assertEquals(1f, Geometry.fitScale(lw, lh, lw, lh), 0.0001f)
    }

    @Test fun pageContentRoundTrip() {
        val c = PageContent(
            listOf(Stroke(7, 1, 2, -16776961, 3.5f, 0.8f, floatArrayOf(1f, 2f, .3f, 4f, 5f, .9f), 42, 1234)),
            listOf(TextBox(8, 10f, 20f, 300f, 40f, "{\"t\":\"Héllo ☐\"}", "Héllo ☐")),
            listOf(com.sushant.inknote.data.ImageItem(9, 1f, 2f, 3f, 4f, "img_1.jpg")),
        )
        val d = PageContent.decode(c.encode())
        assertEquals(1, d.strokes.size); val s = d.strokes[0]
        assertEquals(42, s.recId); assertEquals(1234, s.recMs); assertEquals(2, s.size); assertEquals(0.9f, s.p(1), 0f)
        assertEquals(c.texts, d.texts); assertEquals(c.images, d.images)
        assertTrue(PageContent.decode(null).isEmpty); assertTrue(PageContent.decode(byteArrayOf(1, 2, 3)).isEmpty)
    }

    @Test fun handwritingLinesGroupTopToBottom() {
        fun st(id: Long, x: Float, y: Float) = Stroke(id, 0, 0, 0, 2f, 1f, floatArrayOf(x, y, .5f, x + 20, y + 10, .5f))
        val lines = Handwriting.groupLines(listOf(st(1, 100f, 200f), st(2, 10f, 50f), st(3, 50f, 52f), st(4, 10f, 205f)))
        assertEquals(2, lines.size); assertEquals(listOf(2L, 3L), lines[0].map { it.id }); assertEquals(listOf(4L, 1L), lines[1].map { it.id })
    }

    @Test fun filterEncoding() {
        listOf(Filter.All, Filter.Pinned, Filter.Favourites, Filter.Locked, Filter.Folder(12), Filter.Tag("STET")).forEach { assertEquals(it, Filter.decode(Filter.encode(it))) }
        assertEquals(Filter.All, Filter.decode("f:abc"))
    }

    @Test fun timeFormat() {
        assertEquals("00:05", Dates.mmss(5_400)); assertEquals("12:40", Dates.mmss(760_000)); assertEquals("1:02:03", Dates.mmss(3_723_000))
    }
}
