package com.sushant.gymmanager.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stores member photos as compact JPEGs inside internal storage (filesDir/member_photos). */
object PhotoStore {
    private const val MAX_SIDE = 800

    fun dir(context: Context): File = File(context.filesDir, "member_photos").apply { mkdirs() }

    fun newFile(context: Context, prefix: String = "photo"): File =
        File(dir(context), "${prefix}_${UUID.randomUUID().toString().take(8)}.jpg")

    /** Copies a picked image into internal storage, downscaled and rotated upright. Returns the absolute path. */
    fun importFromUri(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scaled = scaleDown(bitmap)
        val upright = if (rotation != 0f) {
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(rotation) }, true)
        } else scaled
        val file = newFile(context)
        FileOutputStream(file).use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    }.getOrNull()

    private fun scaleDown(src: Bitmap): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= MAX_SIDE) return src
        val ratio = MAX_SIDE.toFloat() / largest
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun clearAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
