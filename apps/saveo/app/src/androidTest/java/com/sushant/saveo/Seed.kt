package com.sushant.saveo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.saveo.data.Source
import java.io.File

/** Creates a fake WhatsApp status folder: colourful photos + real MP4 videos (one with music). */
object Seed {
    private val palettes = listOf(
        intArrayOf(0xFFFF9A5A.toInt(), 0xFFE3485F.toInt(), 0xFF5B2A6E.toInt()),
        intArrayOf(0xFF7CC6FE.toInt(), 0xFFC4F1BE.toInt(), 0xFF3E8E4E.toInt()),
        intArrayOf(0xFF2C2C54.toInt(), 0xFF40407A.toInt(), 0xFF2C2C54.toInt()),
        intArrayOf(0xFFA8E6CF.toInt(), 0xFF3D84A8.toInt(), 0xFF1E4F6E.toInt()),
        intArrayOf(0xFFFFD3B6.toInt(), 0xFFFFAAA5.toInt(), 0xFFFF8B94.toInt()),
        intArrayOf(0xFF355C7D.toInt(), 0xFF6C5B7B.toInt(), 0xFFC06C84.toInt()),
        intArrayOf(0xFFFDEB71.toInt(), 0xFFF8D800.toInt(), 0xFFE07A1F.toInt()),
    )
    private val captions = listOf("", "", "Keep going.\nGood things take time.", "", "Good morning", "", "")

    fun photo(file: File, i: Int, w: Int = 720, h: Int = 1280) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palettes[i % palettes.size]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), p, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.color = Color.argb(230, 255, 214, 110)
        c.drawCircle(w * 0.5f, h * 0.48f, w * (0.19f + (i % 3) * 0.04f), paint)
        paint.color = Color.argb(220, 30, 20, 50)
        val path = android.graphics.Path().apply {
            moveTo(0f, h * 0.62f); quadTo(w * 0.3f, h * 0.52f, w * 0.55f, h * 0.6f); quadTo(w * 0.8f, h * 0.68f, w.toFloat(), h * 0.58f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        c.drawPath(path, paint)
        val cap = captions[i % captions.size]
        if (cap.isNotEmpty()) {
            paint.color = Color.WHITE; paint.textSize = w * 0.075f; paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); paint.textAlign = Paint.Align.CENTER
            cap.split('\n').forEachIndexed { k, line -> c.drawText(line, w / 2f, h * 0.25f + k * w * 0.1f, paint) }
        }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
    }

    private fun asset(name: String, to: File) {
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        testCtx.assets.open(name).use { i -> to.outputStream().use { o -> i.copyTo(o) } }
    }

    /** Fills <root>/WHATSAPP and <root>/BUSINESS. Returns the root. */
    fun statuses(ctx: Context, photos: Int = 7, withVideos: Boolean = true): File {
        val root = File(ctx.filesDir, "test-statuses").apply { deleteRecursively(); mkdirs() }
        val wa = File(root, Source.WHATSAPP.name).apply { mkdirs() }
        val b = File(root, Source.BUSINESS.name).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        File(wa, ".nomedia").writeText("")
        for (i in 0 until photos) {
            val dir = if (i == 2 || i == 6) b else wa
            val f = File(dir, "IMG-STATUS-%03d.jpg".format(i))
            photo(f, i)
            f.setLastModified(now - (i * 2 + 1) * hour * (if (i >= 5) 6 else 1))
        }
        if (withVideos) {
            val v1 = File(wa, "VID-MUSIC-001.mp4"); asset("sample_music.mp4", v1); v1.setLastModified(now - 90 * 60 * 1000L)
            val v2 = File(wa, "VID-CLIP-002.mp4"); asset("sample_clip.mp4", v2); v2.setLastModified(now - 30 * hour)
        }
        return root
    }

    fun many(ctx: Context, count: Int): File {
        val root = File(ctx.filesDir, "test-many").apply { deleteRecursively(); mkdirs() }
        val wa = File(root, Source.WHATSAPP.name).apply { mkdirs() }
        val base = File(wa, "seed.jpg"); photo(base, 1, 240, 426)
        val bytes = base.readBytes(); base.delete()
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val f = File(wa, "M%05d.jpg".format(i))
            // change one byte so every file has its own hash
            val b = bytes.copyOf(); b[b.size - 3] = (i % 120).toByte()
            f.writeBytes(b); f.setLastModified(now - i * 60_000L)
        }
        return root
    }
}
