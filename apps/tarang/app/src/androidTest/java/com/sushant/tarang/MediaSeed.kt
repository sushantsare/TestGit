package com.sushant.tarang

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Puts the real test songs (tags + embedded covers, from androidTest/assets/media) on the emulator, the same way
 * a phone gets music: as files in Music/<folder>, indexed by Android's MediaStore. Runs once; skips files already there.
 */
object MediaSeed {
    fun run(target: Context): Int {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        var added = 0
        for (folder in assets.list("media").orEmpty()) {
            for (name in assets.list("media/$folder").orEmpty()) {
                val path = "media/$folder/$name"
                val audio = name.endsWith(".mp3", true)
                runCatching {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val coll = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
                        val rel = "${Environment.DIRECTORY_MUSIC}/$folder/"
                        val exists = target.contentResolver.query(coll, arrayOf(MediaStore.MediaColumns._ID),
                            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(name, rel), null)?.use { it.count > 0 } == true
                        if (exists) return@runCatching
                        val cv = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, if (audio) "audio/mpeg" else "application/octet-stream")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val uri = target.contentResolver.insert(coll, cv) ?: return@runCatching
                        target.contentResolver.openOutputStream(uri)?.use { out -> assets.open(path).use { it.copyTo(out) } }
                        target.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), folder).apply { mkdirs() }
                        val f = File(dir, name); if (f.exists()) return@runCatching
                        assets.open(path).use { inp -> f.outputStream().use { inp.copyTo(it) } }
                        android.media.MediaScannerConnection.scanFile(target, arrayOf(f.path), null, null)
                    }
                    added++
                }.onFailure { Log.w("Walk", "seed $path failed: ${it.message}") }
            }
        }
        Thread.sleep(if (added > 0) 3_000 else 300)   // let MediaStore read the tags
        Log.i("Walk", "media seeded: $added new files")
        return added
    }
}
