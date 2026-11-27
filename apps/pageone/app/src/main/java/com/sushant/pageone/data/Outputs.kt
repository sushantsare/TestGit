package com.sushant.pageone.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/** Where new files go: Documents/PageOne (with All files access) or Download/PageOne (without). */
class NewFile(val path: String?, val uri: Uri, val name: String)

object Outputs {
    fun create(context: Context, rawName: String, write: (OutputStream) -> Unit): NewFile {
        val name = Format.fileName(rawName)
        if (Environment.isExternalStorageManager()) {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PageOne").apply { mkdirs() }
            val f = unique(dir, name)
            val tmp = File(dir, ".${f.name}.part")
            try {
                tmp.outputStream().use(write)
                if (!tmp.renameTo(f)) { tmp.copyTo(f, true); tmp.delete() }
            } finally { tmp.delete() }
            MediaScannerConnection.scanFile(context, arrayOf(f.path), arrayOf("application/pdf"), null)
            return NewFile(f.path, Uri.fromFile(f), f.name)
        }
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PageOne")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("Can't create file")
        try {
            cr.openOutputStream(uri)?.use(write) ?: throw IllegalStateException("Can't write file")
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) { cr.delete(uri, null, null); throw e }
        return NewFile(null, uri, Library.displayName(cr, uri) ?: name)
    }

    fun unique(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val base = name.removeSuffix(".pdf"); val ext = if (name.endsWith(".pdf")) ".pdf" else ""
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    fun saveImage(context: Context, bmp: Bitmap, name: String): Uri {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PageOne")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("Can't save image")
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }

    /** A Uri other apps can read (FileProvider for real files). */
    fun shareUri(context: Context, ref: DocRef): Uri =
        if (ref.path != null) FileProvider.getUriForFile(context, context.packageName + ".files", File(ref.path)) else Uri.parse(ref.uri)

    fun share(context: Context, ref: DocRef) {
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, shareUri(context, ref)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWith(context: Context, ref: DocRef) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(shareUri(context, ref), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
