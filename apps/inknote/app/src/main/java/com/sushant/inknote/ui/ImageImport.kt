package com.sushant.inknote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.sushant.inknote.data.Ids
import com.sushant.inknote.data.ImageItem
import com.sushant.inknote.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Copies pictures into a note (downscaled, rotation fixed) and places them on pages, adding pages when one is full. */
object ImageImport {
    fun readScaled(context: Context, uri: Uri, maxSide: Int = 2400): ByteArray? = runCatching {
        val cr = context.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= maxSide || o.outHeight / (s * 2) >= maxSide) s *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
        val rot = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()

    /** Returns the number of images added. */
    suspend fun addImages(context: Context, repo: Repository, noteId: Long, startPageId: Long, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val note = repo.note(noteId) ?: return@withContext 0
        var page = repo.page(startPageId) ?: return@withContext 0
        var content = repo.readPage(noteId, page.id)
        var y = (content.images.maxOfOrNull { it.y + it.h } ?: 0f).let { if (it == 0f) (content.strokes.maxOfOrNull { s -> s.bounds()[3] } ?: 60f) + 20f else it + 30f }.coerceAtLeast(60f)
        var added = 0
        for (u in uris) {
            val bytes = readScaled(context, u) ?: continue
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            val maxW = page.width * 0.84f
            var w = maxW; var h = w * o.outHeight / o.outWidth.coerceAtLeast(1)
            if (h > page.height * 0.8f) { h = page.height * 0.8f; w = h * o.outWidth / o.outHeight.coerceAtLeast(1) }
            if (y + h > page.height - 40f && (content.images.isNotEmpty() || content.strokes.isNotEmpty())) {
                repo.savePage(noteId, page.id, content)
                val newId = repo.addPage(noteId, page.idx)
                page = repo.page(newId) ?: break
                content = repo.readPage(noteId, page.id); y = 60f
            }
            val name = "img_${Ids.next()}.jpg"
            repo.files.writeImage(noteId, name, bytes, note.locked)
            content = content.copy(images = content.images + ImageItem(Ids.next(), (page.width - w) / 2, y, w, h, name))
            y += h + 30f; added++
        }
        repo.savePage(noteId, page.id, content)
        added
    }
}
