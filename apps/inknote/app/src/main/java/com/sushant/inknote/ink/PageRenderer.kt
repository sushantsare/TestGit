package com.sushant.inknote.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.sushant.inknote.data.NoteFiles
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageEntity
import kotlin.math.max

/** Draws a whole page (paper, PDF, images, text, ink). Used by the editor, thumbnails, export and printing. */
object PageRenderer {
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }

    fun textLayout(stored: String, w: Float, dark: Boolean = false): StaticLayout {
        val sp = RichText.decode(stored)
        val tp = TextPaint(textPaint).apply { color = if (dark) 0xFFECE8E1.toInt() else 0xFF1C1B1F.toInt() }
        return StaticLayout.Builder.obtain(sp, 0, sp.length, tp, max(1, w.toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
    }

    fun textHeight(stored: String, w: Float): Float = textLayout(stored, w).height.toFloat()

    /**
     * @param pxPerUnit canvas pixels per page unit
     * @param sync render PDF pages synchronously (export / thumbnails); otherwise request async and call [onPdfReady]
     */
    fun draw(
        c: Canvas, files: NoteFiles, noteId: Long, p: PageEntity, content: PageContent, pal: PagePalette, pxPerUnit: Float,
        sync: Boolean, playback: Pair<Long, Long>? = null, hideText: Long = -1,
        hidden: Selection? = null, onPdfReady: (() -> Unit)? = null,
    ) {
        c.save(); c.scale(pxPerUnit, pxPerUnit)
        PaperRenderer.draw(c, p.paper, p.width, p.height, pal)
        p.pdfFile?.let { name ->
            val f = files.pdf(noteId, name)
            val px = if (sync) max((p.width * pxPerUnit).toInt(), 1600) else (p.width * pxPerUnit).toInt().coerceAtLeast(1)
            val bmp = if (sync) PdfPageCache.renderNow(f, p.pdfPage, px) else PdfPageCache.get(f, p.pdfPage, px)
            if (bmp != null) c.drawBitmap(bmp, null, RectF(0f, 0f, p.width, p.height), bmpPaint)
            else if (!sync) PdfPageCache.request(f, p.pdfPage, px) { onPdfReady?.invoke() }
        }
        for (im in content.images) {
            if (hidden != null && im.id in hidden.images) continue
            files.imageBitmap(noteId, im.file)?.let { c.drawBitmap(it, null, RectF(im.x, im.y, im.x + im.w, im.y + im.h), bmpPaint) }
        }
        for (t in content.texts) {
            if (t.id == hideText) continue
            if (hidden != null && t.id in hidden.texts) continue
            c.save(); c.translate(t.x, t.y); textLayout(t.html, t.w, pal.dark).draw(c); c.restore()
        }
        val strokes = if (hidden != null) content.strokes.filter { it.id !in hidden.strokes } else content.strokes
        StrokeRenderer.drawAll(c, strokes, pal.dark, playback)
        c.restore()
    }

    fun bitmap(files: NoteFiles, noteId: Long, p: PageEntity, content: PageContent, widthPx: Int, pal: PagePalette = PagePalette.LIGHT): Bitmap {
        val h = (widthPx * p.height / p.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), files, noteId, p, content, pal, widthPx / p.width, sync = true)
        return bmp
    }
}
