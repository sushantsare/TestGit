package com.sushant.gallery

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Puts realistic-looking sample photos (and one video) on the emulator for tests and screenshots. */
object Seed {
    val root: File get() = Environment.getExternalStorageDirectory()
    val camera get() = File(root, "DCIM/Camera")
    val shots get() = File(root, "Pictures/Screenshots")
    val whatsapp get() = File(root, "Pictures/WhatsApp Images")
    val downloads get() = File(root, "Download")

    fun shell(inst: Instrumentation, cmd: String): String {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    fun grantAllFiles(inst: Instrumentation) {
        val pkg = inst.targetContext.packageName
        shell(inst, "appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow")
        shell(inst, "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE")
        shell(inst, "pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE")
        Thread.sleep(500)
    }

    private val palettes = listOf(
        intArrayOf(0xFFFF9A5A.toInt(), 0xFFFFD7A0.toInt(), 0xFF5A2D46.toInt()),
        intArrayOf(0xFF5BB8F0.toInt(), 0xFFDFF1FB.toInt(), 0xFF23463A.toInt()),
        intArrayOf(0xFF281E50.toInt(), 0xFFE66E78.toInt(), 0xFF140C24.toInt()),
        intArrayOf(0xFF3C8DC5.toInt(), 0xFFF5D06B.toInt(), 0xFF0B6E6E.toInt()),
        intArrayOf(0xFF6CC8C3.toInt(), 0xFFFFF3C4.toInt(), 0xFF00393B.toInt()),
    )

    fun photo(file: File, seed: Int, takenMs: Long, w: Int = 1200, h: Int = 1600) {
        file.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palettes[seed % palettes.size]
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.6f, p[0], p[1], Shader.TileMode.CLAMP) })
        c.drawCircle(w * (0.3f + (seed % 5) * 0.1f), h * 0.32f, w * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF5DC.toInt() })
        val hill = Path().apply {
            moveTo(0f, h.toFloat()); lineTo(0f, h * 0.62f)
            var x = 0f; var i = 0
            while (x < w) { x += w / 6f; lineTo(x, h * (0.5f + ((seed + i) % 4) * 0.06f)); i++ }
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(hill, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p[2] })
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        runCatching {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fmt.format(takenMs))
                setAttribute(ExifInterface.TAG_MAKE, "Google"); setAttribute(ExifInterface.TAG_MODEL, "Pixel 8")
                setAttribute(ExifInterface.TAG_F_NUMBER, "1.9"); setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "64")
                setLatLong(25.19, 85.52)
                saveAttributes()
            }
        }
        file.setLastModified(takenMs)
    }

    fun screenshot(file: File, seed: Int, takenMs: Long) {
        file.parentFile?.mkdirs()
        val w = 1080; val h = 2400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF5F6F8.toInt())
        c.drawRect(0f, 0f, w.toFloat(), 220f, Paint().apply { color = if (seed % 2 == 0) 0xFF25D366.toInt() else 0xFF0078FF.toInt() })
        val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 300f; var i = 0
        while (y < h - 200) {
            val left = (i + seed) % 2 == 0
            bubble.color = if (left) 0xFFFFFFFF.toInt() else 0xFFDCF8C6.toInt()
            val bw = 500f + ((i * 37) % 300); val x = if (left) 40f else w - 40f - bw
            c.drawRoundRect(x, y, x + bw, y + 160f, 36f, 36f, bubble); y += 210f; i++
        }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.setLastModified(takenMs)
    }

    /** Creates the sample library (idempotent) and waits for the media scanner. */
    fun library(inst: Instrumentation, now: Long = System.currentTimeMillis()): List<File> {
        val day = 86_400_000L
        val files = ArrayList<File>()
        val ages = listOf(0, 0, 0, 1, 1, 1, 1, 3, 3, 6, 6, 12, 12, 12, 25, 40, 40, 70, 70, 120)
        ages.forEachIndexed { i, age ->
            val f = File(if (i % 4 == 3) whatsapp else camera, "IMG_2026_%03d.jpg".format(i))
            if (!f.exists()) photo(f, i, now - age * day - i * 60_000L)
            files += f
        }
        repeat(4) { i ->
            val f = File(shots, "Screenshot_2026_%02d.png".format(i))
            if (!f.exists()) screenshot(f, i, now - (i * 2 + 1) * day)
            files += f
        }
        val v = File(camera, "VID_2026_001.mp4")
        if (!v.exists()) {
            inst.context.assets.open("sample_clip.mp4").use { i -> v.outputStream().use { o -> i.copyTo(o) } }
            v.setLastModified(now - day)
        }
        files += v
        scan(inst, files)
        return files
    }

    fun scan(inst: Instrumentation, files: List<File>) {
        val latch = CountDownLatch(files.size)
        MediaScannerConnection.scanFile(inst.targetContext, files.map { it.path }.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
    }
}
