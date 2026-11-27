package com.sushant.pageone.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.OutputStream

/** A file going into Merge: a PDF or an image (each image becomes one page). */
data class MergeItem(val uri: Uri, val name: String, val isImage: Boolean, val pages: Int, val size: Long)

enum class CompressLevel(val maxDim: Int, val quality: Float) { LIGHT(2200, 0.82f), BALANCED(1500, 0.65f), STRONG(1000, 0.45f) }

/** Editing work on PDFs (PDFBox-Android, Apache 2.0). Everything here runs off the main thread. */
object PdfTools {
    private const val TAG = "PageOneTools"

    private fun load(file: File, password: String?): PDDocument =
        if (password != null) PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
        else PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())

    fun isEncrypted(file: File): Boolean = try { load(file, null).close(); false } catch (e: InvalidPasswordException) { true } catch (e: Exception) { false }

    fun decryptCopy(context: Context, src: File, password: String): File {
        val out = File(File(context.cacheDir, "open").apply { mkdirs() }, "dec_${src.path.hashCode().toUInt()}.pdf")
        try {
            load(src, password).use { d -> d.isAllSecurityToBeRemoved = true; d.save(out) }
        } catch (e: InvalidPasswordException) { throw PasswordNeeded(true) }
        return out
    }

    fun pageText(file: File, password: String?, page: Int): String = runCatching {
        load(file, password).use { d ->
            PDFTextStripper().apply { startPage = page + 1; endPage = page + 1 }.getText(d)
        }
    }.getOrDefault("")

    fun outline(file: File, password: String?): List<OutlineItem> = runCatching {
        load(file, password).use { d ->
            val out = ArrayList<OutlineItem>()
            fun walk(node: PDOutlineNode, level: Int) {
                for (item in node.children()) {
                    val page = runCatching { item.findDestinationPage(d)?.let { d.pages.indexOf(it) } ?: -1 }.getOrDefault(-1)
                    if (!item.title.isNullOrBlank()) out += OutlineItem(item.title.trim(), page, level)
                    if (out.size < 2000) walk(item, level + 1)
                }
            }
            d.documentCatalog.documentOutline?.let { walk(it, 0) }
            out
        }
    }.onFailure { Log.w(TAG, "outline: ${it.message}") }.getOrDefault(emptyList())

    fun formFields(file: File, password: String?): List<FormField> = runCatching {
        load(file, password).use { d ->
            val form = d.documentCatalog.acroForm ?: return@use emptyList<FormField>()
            val out = ArrayList<FormField>()
            for (f in form.fieldTree) {
                if (f !is PDTerminalField) continue
                for (w in f.widgets) {
                    val r = w.rectangle ?: continue
                    val pageObj = w.page
                    var idx = if (pageObj != null) d.pages.indexOf(pageObj) else -1
                    if (idx < 0) idx = (0 until d.numberOfPages).firstOrNull { i -> d.getPage(i).annotations.any { it.cosObject == w.cosObject } } ?: continue
                    val crop = d.getPage(idx).cropBox
                    val rect = RectF(r.lowerLeftX - crop.lowerLeftX, crop.upperRightY - r.upperRightY, r.upperRightX - crop.lowerLeftX, crop.upperRightY - r.lowerLeftY)
                    out += when (f) {
                        is PDTextField -> FormField(f.fullyQualifiedName, idx, rect, FormField.Kind.TEXT, f.valueAsString ?: "", false, f.isReadOnly, f.isMultiline)
                        is PDCheckBox -> FormField(f.fullyQualifiedName, idx, rect, FormField.Kind.CHECKBOX, "", f.isChecked, f.isReadOnly, false)
                        else -> FormField(f.fullyQualifiedName, idx, rect, FormField.Kind.OTHER, f.valueAsString ?: "", false, true, false)
                    }
                }
            }
            out
        }
    }.onFailure { Log.w(TAG, "forms: ${it.message}") }.getOrDefault(emptyList())

    /**
     * Write highlights, drawings, notes and form values into a copy of [src], saved to [out].
     * Annotations are drawn into the page itself so every PDF app shows them; notes also get a sticky-note popup.
     */
    fun saveEdits(src: File, password: String?, annots: List<Annot>, forms: Map<String, String>, checks: Map<String, Boolean>, out: OutputStream) {
        load(src, password).use { d ->
            d.isAllSecurityToBeRemoved = false
            d.documentCatalog.acroForm?.let { form ->
                for ((name, v) in forms) runCatching { (form.getField(name) as? PDTextField)?.setValue(v) }.onFailure { Log.w(TAG, "field $name: ${it.message}") }
                for ((name, c) in checks) runCatching { (form.getField(name) as? PDCheckBox)?.let { if (c) it.check() else it.unCheck() } }
            }
            for ((pageIdx, list) in annots.groupBy { it.page }) {
                if (pageIdx !in 0 until d.numberOfPages) continue
                val page = d.getPage(pageIdx)
                val crop = page.cropBox
                fun x(v: Float) = crop.lowerLeftX + v
                fun y(v: Float) = crop.upperRightY - v
                PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (a in list) {
                        when (a) {
                            is Annot.Mark -> {
                                cs.saveGraphicsState()
                                val (r, g, b) = rgb(a.color)
                                if (a.underline) {
                                    cs.setStrokingColor(r, g, b); cs.setLineWidth(1.4f)
                                    for (rc in a.rects) { cs.moveTo(x(rc.left), y(rc.bottom) + 0.8f); cs.lineTo(x(rc.right), y(rc.bottom) + 0.8f) }
                                    cs.stroke()
                                } else {
                                    cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.38f })
                                    cs.setNonStrokingColor(r, g, b)
                                    for (rc in a.rects) cs.addRect(x(rc.left), y(rc.bottom), rc.width(), rc.height())
                                    cs.fill()
                                }
                                cs.restoreGraphicsState()
                            }
                            is Annot.Ink -> if (a.points.size > 1) {
                                cs.saveGraphicsState()
                                val (r, g, b) = rgb(a.color)
                                if (a.marker) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.4f })
                                cs.setStrokingColor(r, g, b); cs.setLineWidth(a.width); cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                                cs.moveTo(x(a.points[0].x), y(a.points[0].y))
                                for (p in a.points.drop(1)) cs.lineTo(x(p.x), y(p.y))
                                cs.stroke()
                                cs.restoreGraphicsState()
                            }
                            is Annot.Note -> {
                                val s = Annot.NOTE_SIZE
                                cs.saveGraphicsState()
                                cs.setNonStrokingColor(0.97f, 0.85f, 0.32f)
                                cs.addRect(x(a.at.x), y(a.at.y + s), s, s); cs.fill()
                                cs.setStrokingColor(0.45f, 0.36f, 0.05f); cs.setLineWidth(1f)
                                for (i in 1..3) { cs.moveTo(x(a.at.x + 4), y(a.at.y + 4f * i + 1)); cs.lineTo(x(a.at.x + s - 4), y(a.at.y + 4f * i + 1)) }
                                cs.stroke()
                                cs.restoreGraphicsState()
                            }
                        }
                    }
                }
                for (n in list.filterIsInstance<Annot.Note>()) runCatching {
                    page.annotations.add(PDAnnotationText().apply {
                        rectangle = PDRectangle(x(n.at.x), y(n.at.y + Annot.NOTE_SIZE), Annot.NOTE_SIZE, Annot.NOTE_SIZE)
                        contents = n.text
                    })
                }
            }
            d.save(out)
        }
    }

    private fun rgb(c: Int) = Triple(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    /** Merge PDFs and images in order into [out]. */
    fun merge(context: Context, items: List<MergeItem>, out: OutputStream, progress: (Int, Int) -> Unit) {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dest ->
            val merger = PDFMergerUtility()
            val opened = ArrayList<PDDocument>()
            try {
                items.forEachIndexed { i, item ->
                    progress(i, items.size)
                    if (item.isImage) addImagePage(context, dest, item.uri)
                    else {
                        val tmp = File(context.cacheDir, "merge_$i.pdf")
                        context.contentResolver.openInputStream(item.uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                        val src = PDDocument.load(tmp, MemoryUsageSetting.setupTempFileOnly())
                        opened += src
                        merger.appendDocument(dest, src)
                    }
                }
                progress(items.size, items.size)
                dest.save(out)
            } finally {
                opened.forEach { runCatching { it.close() } }
                File(context.cacheDir, ".").listFiles()?.filter { it.name.startsWith("merge_") }?.forEach { it.delete() }
            }
        }
    }

    private fun addImagePage(context: Context, dest: PDDocument, uri: Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Can't read image")
        val upright = ImageRotation.upright(context, uri, bmp)
        val img = JPEGFactory.createFromImage(dest, upright, 0.85f)
        val w = 595f; val h = w * upright.height / upright.width
        val page = PDPage(PDRectangle(w, h))
        dest.addPage(page)
        PDPageContentStream(dest, page).use { it.drawImage(img, 0f, 0f, w, h) }
        if (upright !== bmp) upright.recycle()
        bmp.recycle()
    }

    /** Copy the chosen pages (0-based, in order) into a new PDF. */
    fun extract(src: File, password: String?, pages: List<Int>, out: OutputStream) {
        load(src, password).use { s ->
            PDDocument().use { d ->
                for (i in pages) d.importPage(s.getPage(i))
                d.save(out)
            }
        }
    }

    fun pageCount(src: File, password: String?): Int = runCatching { load(src, password).use { it.numberOfPages } }.getOrDefault(0)

    /** Shrink by re-saving large photos inside the PDF as smaller JPEGs. Text and vector art stay sharp. */
    fun compress(src: File, password: String?, level: CompressLevel, out: OutputStream, progress: (Int, Int) -> Unit) {
        load(src, password).use { d ->
            val n = d.numberOfPages
            for (i in 0 until n) {
                progress(i, n)
                val res = d.getPage(i).resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                    if (x.softMask != null || x.bitsPerComponent <= 1 || x.isStencil) continue
                    runCatching {
                        val bmp = x.image ?: return@runCatching
                        val scale = minOf(1f, level.maxDim.toFloat() / maxOf(bmp.width, bmp.height))
                        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
                        val jpg = JPEGFactory.createFromImage(d, scaled, level.quality)
                        if (jpg.cosObject.length < x.cosObject.length) res.put(name, jpg)
                        if (scaled !== bmp) scaled.recycle()
                        bmp.recycle()
                    }.onFailure { Log.w(TAG, "image $name p$i: ${it.message}") }
                }
            }
            progress(n, n)
            d.save(out)
        }
    }
}
