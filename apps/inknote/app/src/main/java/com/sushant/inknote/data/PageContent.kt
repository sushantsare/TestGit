package com.sushant.inknote.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object Tool { const val PEN = 0; const val HIGHLIGHTER = 1 }
object Brush { const val BALL = 0; const val FOUNTAIN = 1; const val PENCIL = 2; const val BRUSH = 3 }

/** One ink stroke in page units. [pts] holds x, y, pressure triples. */
class Stroke(
    val id: Long,
    val tool: Int,
    val brush: Int,
    val color: Int,
    val width: Float,
    val alpha: Float,
    val pts: FloatArray,
    val recId: Long = -1,
    val recMs: Long = -1,
) {
    val size get() = pts.size / 3
    fun x(i: Int) = pts[i * 3]
    fun y(i: Int) = pts[i * 3 + 1]
    fun p(i: Int) = pts[i * 3 + 2]

    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until size) { val x = x(i); val y = y(i); if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        val pad = width
        return floatArrayOf(l - pad, t - pad, r + pad, b + pad)
    }

    fun copy(
        id: Long = this.id, color: Int = this.color, pts: FloatArray = this.pts,
        width: Float = this.width, recId: Long = this.recId, recMs: Long = this.recMs,
    ) = Stroke(id, tool, brush, color, width, alpha, pts, recId, recMs)

    fun transformed(sx: Float, sy: Float, dx: Float, dy: Float, widthScale: Float = 1f): Stroke {
        val n = pts.copyOf()
        for (i in 0 until size) { n[i * 3] = pts[i * 3] * sx + dx; n[i * 3 + 1] = pts[i * 3 + 1] * sy + dy }
        return copy(pts = n, width = width * widthScale)
    }
}

/** A typed text box; [html] is Android Html (spans: bold, italic, underline, colour, bullets, headings). */
data class TextBox(val id: Long, val x: Float, val y: Float, val w: Float, val h: Float, val html: String, val plain: String)

/** An inserted image stored as a file inside the note folder. */
data class ImageItem(val id: Long, val x: Float, val y: Float, val w: Float, val h: Float, val file: String)

data class PageContent(
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextBox> = emptyList(),
    val images: List<ImageItem> = emptyList(),
) {
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty() && images.isEmpty()

    fun transformed(sx: Float, sy: Float, dx: Float = 0f, dy: Float = 0f): PageContent = PageContent(
        strokes.map { it.transformed(sx, sy, dx, dy, (sx + sy) / 2f) },
        texts.map { it.copy(x = it.x * sx + dx, y = it.y * sy + dy, w = it.w * sx, h = it.h * sy) },
        images.map { it.copy(x = it.x * sx + dx, y = it.y * sy + dy, w = it.w * sx, h = it.h * sy) },
    )

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { o ->
            o.writeInt(MAGIC); o.writeInt(1)
            o.writeInt(strokes.size)
            for (s in strokes) {
                o.writeLong(s.id); o.writeByte(s.tool); o.writeByte(s.brush); o.writeInt(s.color)
                o.writeFloat(s.width); o.writeFloat(s.alpha); o.writeLong(s.recId); o.writeLong(s.recMs)
                o.writeInt(s.pts.size); for (f in s.pts) o.writeFloat(f)
            }
            o.writeInt(texts.size)
            for (t in texts) { o.writeLong(t.id); o.writeFloat(t.x); o.writeFloat(t.y); o.writeFloat(t.w); o.writeFloat(t.h); str(o, t.html); str(o, t.plain) }
            o.writeInt(images.size)
            for (i in images) { o.writeLong(i.id); o.writeFloat(i.x); o.writeFloat(i.y); o.writeFloat(i.w); o.writeFloat(i.h); str(o, i.file) }
        }
        return bos.toByteArray()
    }

    companion object {
        const val MAGIC = 0x494E4B31 // "INK1"

        private fun str(o: DataOutputStream, s: String) { val b = s.toByteArray(Charsets.UTF_8); o.writeInt(b.size); o.write(b) }
        private fun str(i: DataInputStream): String { val n = i.readInt(); val b = ByteArray(n); i.readFully(b); return String(b, Charsets.UTF_8) }

        fun decode(bytes: ByteArray?): PageContent {
            if (bytes == null || bytes.size < 8) return PageContent()
            DataInputStream(ByteArrayInputStream(bytes)).use { i ->
                if (i.readInt() != MAGIC) return PageContent()
                i.readInt()
                val strokes = ArrayList<Stroke>()
                repeat(i.readInt()) {
                    val id = i.readLong(); val tool = i.readByte().toInt(); val brush = i.readByte().toInt(); val color = i.readInt()
                    val w = i.readFloat(); val a = i.readFloat(); val rid = i.readLong(); val rms = i.readLong()
                    val n = i.readInt(); val pts = FloatArray(n) { i.readFloat() }
                    strokes += Stroke(id, tool, brush, color, w, a, pts, rid, rms)
                }
                val texts = ArrayList<TextBox>()
                repeat(i.readInt()) { texts += TextBox(i.readLong(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat(), str(i), str(i)) }
                val images = ArrayList<ImageItem>()
                repeat(i.readInt()) { images += ImageItem(i.readLong(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat(), str(i)) }
                return PageContent(strokes, texts, images)
            }
        }
    }
}

object Ids {
    private var last = 0L
    @Synchronized fun next(): Long { val t = System.currentTimeMillis() * 1000; last = if (t > last) t else last + 1; return last }
}
