package com.sushant.gymmanager.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream

/** Saves files into the public Downloads/GymManager folder (kept even if the app is uninstalled). */
object Downloads {
    const val FOLDER = "GymManager"

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    fun save(context: Context, name: String, mime: String, write: (OutputStream) -> Unit): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use(write)
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            if (needsPermission(context)) return null
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use(write)
            Uri.fromFile(file)
        }
    }.getOrNull()

    /** Keeps only the newest [keep] files whose name starts with [prefix]. */
    fun prune(context: Context, prefix: String, keep: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("$prefix%"),
                    "${MediaStore.Downloads.DATE_ADDED} DESC"
                )?.use { c ->
                    var i = 0
                    while (c.moveToNext()) {
                        if (i++ >= keep) {
                            val id = c.getLong(0)
                            resolver.delete(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()), null, null)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                dir.listFiles { f -> f.name.startsWith(prefix) }.orEmpty()
                    .sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
            }
        }
    }
}
