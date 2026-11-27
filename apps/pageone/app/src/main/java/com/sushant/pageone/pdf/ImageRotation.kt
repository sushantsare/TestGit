package com.sushant.pageone.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Photos from the camera carry their rotation in EXIF; turn them upright before they become a page. */
object ImageRotation {
    fun upright(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val deg = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }?.let {
                when (it.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (deg == 0) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
    }
}
