package com.sushant.inknote.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry used by the ink engine (unit tested). Points are flat x,y arrays unless said otherwise. */
object Geometry {
    fun fitScale(oldW: Float, oldH: Float, newW: Float, newH: Float): Float = min(1f, min(newW / oldW, newH / oldH))

    fun pointInPolygon(x: Float, y: Float, poly: FloatArray): Boolean {
        var inside = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i * 2]; val yi = poly[i * 2 + 1]; val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-9f) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** A stroke is lasso-selected when at least 60 % of its points are inside the loop. */
    fun strokeInLasso(s: Stroke, poly: FloatArray): Boolean {
        if (poly.size < 6) return false
        var inside = 0
        for (i in 0 until s.size) if (pointInPolygon(s.x(i), s.y(i), poly)) inside++
        return inside >= max(1, (s.size * 0.6f).toInt())
    }

    fun rectInLasso(l: Float, t: Float, r: Float, b: Float, poly: FloatArray): Boolean =
        pointInPolygon((l + r) / 2, (t + b) / 2, poly)

    fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    fun strokeHit(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val rr = r + s.width / 2
        if (s.size == 1) return hypot(s.x(0) - x, s.y(0) - y) <= rr
        for (i in 1 until s.size) if (distToSegment(x, y, s.x(i - 1), s.y(i - 1), s.x(i), s.y(i)) <= rr) return true
        return false
    }

    /** Area eraser: removes points within [r] of (x, y), splitting the stroke into the remaining pieces. */
    fun eraseArea(s: Stroke, x: Float, y: Float, r: Float, newId: () -> Long): List<Stroke>? {
        val keep = BooleanArray(s.size) { hypot(s.x(it) - x, s.y(it) - y) > r + s.width / 2 }
        if (keep.all { it }) return null
        val out = ArrayList<Stroke>()
        var i = 0
        while (i < s.size) {
            if (!keep[i]) { i++; continue }
            val start = i
            while (i < s.size && keep[i]) i++
            if (i - start >= 2) out += s.copy(id = newId(), pts = s.pts.copyOfRange(start * 3, i * 3))
        }
        return out
    }

    fun pathLength(xy: FloatArray): Float {
        var l = 0f
        for (i in 1 until xy.size / 2) l += hypot(xy[i * 2] - xy[i * 2 - 2], xy[i * 2 + 1] - xy[i * 2 - 1])
        return l
    }

    /** Ramer–Douglas–Peucker simplification; returns indices of kept points. */
    fun rdp(xy: FloatArray, eps: Float): List<Int> {
        val n = xy.size / 2
        if (n < 3) return (0 until n).toList()
        val keep = BooleanArray(n); keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>(); stack.add(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxD = 0f; var idx = -1
            for (i in a + 1 until b) {
                val d = distToSegment(xy[i * 2], xy[i * 2 + 1], xy[a * 2], xy[a * 2 + 1], xy[b * 2], xy[b * 2 + 1])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > eps) { keep[idx] = true; stack.add(a to idx); stack.add(idx to b) }
        }
        return (0 until n).filter { keep[it] }
    }

    enum class Shape { LINE, POLYLINE, TRIANGLE, RECTANGLE, POLYGON, CIRCLE, ELLIPSE }
    data class Recognized(val shape: Shape, val xy: FloatArray)

    /** Turns a rough hand-drawn shape into a neat one, or returns null when it doesn't look like a shape. */
    fun recognize(xy: FloatArray): Recognized? {
        val n = xy.size / 2
        if (n < 4) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until n) { l = min(l, xy[i * 2]); r = max(r, xy[i * 2]); t = min(t, xy[i * 2 + 1]); b = max(b, xy[i * 2 + 1]) }
        val w = r - l; val h = b - t; val diag = hypot(w, h)
        if (diag < 12f) return null
        val len = pathLength(xy)
        val ends = hypot(xy[(n - 1) * 2] - xy[0], xy[(n - 1) * 2 + 1] - xy[1])
        if (ends / len > 0.92f) return Recognized(Shape.LINE, floatArrayOf(xy[0], xy[1], xy[(n - 1) * 2], xy[(n - 1) * 2 + 1]))
        val closed = ends < 0.22f * max(w, h)
        val idx = rdp(xy, 0.07f * diag)
        if (!closed) {
            return if (idx.size in 3..4) Recognized(Shape.POLYLINE, FloatArray(idx.size * 2) { xy[idx[it / 2] * 2 + it % 2] }) else null
        }
        // closed: drop the closing duplicate vertex
        val verts = idx.toMutableList()
        if (verts.size >= 2 && hypot(xy[verts.last() * 2] - xy[verts.first() * 2], xy[verts.last() * 2 + 1] - xy[verts.first() * 2 + 1]) < 0.22f * max(w, h)) verts.removeAt(verts.size - 1)
        val cx = (l + r) / 2; val cy = (t + b) / 2; val rx = w / 2; val ry = h / 2
        var err = 0f
        for (i in 0 until n) { val dx = (xy[i * 2] - cx) / max(rx, 1f); val dy = (xy[i * 2 + 1] - cy) / max(ry, 1f); err += abs(sqrt(dx * dx + dy * dy) - 1f) }
        err /= n
        when (verts.size) {
            3 -> if (err > 0.1f) return Recognized(Shape.TRIANGLE, closedPoly(verts.flatMap { listOf(xy[it * 2], xy[it * 2 + 1]) }))
            4 -> if (err > 0.08f) {
                return if (axisAligned(xy, verts)) Recognized(Shape.RECTANGLE, closedPoly(listOf(l, t, r, t, r, b, l, b)))
                else Recognized(Shape.RECTANGLE, closedPoly(verts.flatMap { listOf(xy[it * 2], xy[it * 2 + 1]) }))
            }
        }
        if (err < 0.2f) {
            val circle = abs(rx - ry) / max(rx, ry) < 0.18f
            val rr = (rx + ry) / 2
            val ex = if (circle) rr else rx; val ey = if (circle) rr else ry
            val steps = 72
            return Recognized(if (circle) Shape.CIRCLE else Shape.ELLIPSE, FloatArray((steps + 1) * 2) {
                val a = (it / 2) * (2 * Math.PI / steps); if (it % 2 == 0) (cx + ex * cos(a)).toFloat() else (cy + ey * sin(a)).toFloat()
            })
        }
        if (verts.size in 5..8) return Recognized(Shape.POLYGON, closedPoly(verts.flatMap { listOf(xy[it * 2], xy[it * 2 + 1]) }))
        return null
    }

    private fun axisAligned(xy: FloatArray, v: List<Int>): Boolean {
        for (i in v.indices) {
            val a = v[i]; val b = v[(i + 1) % v.size]
            val ang = Math.toDegrees(atan2((xy[b * 2 + 1] - xy[a * 2 + 1]).toDouble(), (xy[b * 2] - xy[a * 2]).toDouble())).let { abs(it) % 90 }
            if (ang > 18 && ang < 72) return false
        }
        return true
    }

    private fun closedPoly(p: List<Float>): FloatArray = (p + listOf(p[0], p[1])).toFloatArray()

    /** Densifies an x,y polyline into x,y,pressure triples so shapes render like normal strokes. */
    fun toStrokePoints(xy: FloatArray, step: Float = 6f): FloatArray {
        val out = ArrayList<Float>()
        val n = xy.size / 2
        for (i in 0 until n) {
            val x = xy[i * 2]; val y = xy[i * 2 + 1]
            if (i > 0) {
                val px = xy[i * 2 - 2]; val py = xy[i * 2 - 1]
                val d = hypot(x - px, y - py); val k = (d / step).toInt()
                for (j in 1 until k) { val f = j / k.toFloat(); out += px + (x - px) * f; out += py + (y - py) * f; out += 0.5f }
            }
            out += x; out += y; out += 0.5f
        }
        return out.toFloatArray()
    }

    fun unionBounds(boxes: List<FloatArray>): FloatArray? {
        if (boxes.isEmpty()) return null
        return floatArrayOf(boxes.minOf { it[0] }, boxes.minOf { it[1] }, boxes.maxOf { it[2] }, boxes.maxOf { it[3] })
    }
}
