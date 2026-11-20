package com.sushant.inknote

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sushant.inknote.data.AudioKind
import com.sushant.inknote.data.Brush
import com.sushant.inknote.data.Ids
import com.sushant.inknote.data.Kind
import com.sushant.inknote.data.Orientation
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageType
import com.sushant.inknote.data.Paper
import com.sushant.inknote.data.RecordingEntity
import com.sushant.inknote.data.Repository
import com.sushant.inknote.data.Stroke
import com.sushant.inknote.data.TextBox
import com.sushant.inknote.data.Tool
import com.sushant.inknote.data.Geometry
import com.sushant.inknote.ink.RichText
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Realistic sample notes for tests and screenshots. */
object Seed {
    const val INK = 0xFF1F3F9A.toInt()

    /** Cursive-looking handwriting: one stroke per word, lines spaced like lined paper. */
    fun handwriting(lines: Int, x0: Float, y0: Float, width: Float, gap: Float = 40f, seed: Int = 1, color: Int = INK, recId: Long = -1, msPerWord: Long = 900): List<Stroke> {
        val r = Random(seed); val out = ArrayList<Stroke>(); var t = 0L
        for (l in 0 until lines) {
            var x = x0 + r.nextInt(0, 20); val base = y0 + l * gap
            val end = x0 + width - r.nextInt(0, (width / 3).toInt())
            while (x < end - 40) {
                val wl = r.nextInt(40, 110).toFloat()
                val pts = ArrayList<Float>(); var px = x
                while (px < x + wl) {
                    val ph = (px - x) / 7f
                    pts += px; pts += base - 4f - 13f * abs(sin(ph)) * (if (r.nextInt(6) == 0) 1.6f else 1f); pts += 0.4f + 0.4f * abs(sin(ph * 0.7f))
                    px += 2.2f
                }
                out += Stroke(Ids.next(), Tool.PEN, Brush.FOUNTAIN, color, 2.6f, 1f, pts.toFloatArray(), recId, if (recId >= 0) t else -1)
                t += msPerWord; x += wl + r.nextInt(14, 26)
            }
        }
        return out
    }

    fun highlight(x: Float, y: Float, w: Float) = Stroke(Ids.next(), Tool.HIGHLIGHTER, Brush.BALL, 0xFFFFD43B.toInt(), 26f, 1f, floatArrayOf(x, y, .5f, x + w, y, .5f))

    fun circle(cx: Float, cy: Float, rx: Float, ry: Float, color: Int = 0xFFC0392B.toInt()): Stroke {
        val xy = FloatArray(146) { val a = (it / 2) * 2 * Math.PI / 72; if (it % 2 == 0) (cx + rx * kotlin.math.cos(a)).toFloat() else (cy + ry * kotlin.math.sin(a)).toFloat() }
        return Stroke(Ids.next(), Tool.PEN, Brush.BALL, color, 3.4f, 1f, Geometry.toStrokePoints(xy))
    }

    fun text(x: Float, y: Float, w: Float, s: String) = TextBox(Ids.next(), x, y, w, 60f, RichText.fromPlain(s), s)

    /** A real (silent) WAV file so playback works without a microphone. */
    fun silentWav(f: File, seconds: Int) {
        val rate = 8000; val n = rate * seconds; val b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + n * 2); b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(rate); b.putInt(rate * 2); b.putShort(2); b.putShort(16); b.put("data".toByteArray()); b.putInt(n * 2)
        f.parentFile?.mkdirs(); f.writeBytes(b.array())
    }

    fun samplePdf(ctx: Context): File {
        val f = File(ctx.cacheDir, "STET Paper 2 - 2024.pdf")
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 15f; isFakeBoldText = true; color = Color.BLACK }
        val body = Paint().apply { textSize = 11f; color = Color.DKGRAY }
        val qs = listOf(
            "1. Who wrote 'Ode to a Nightingale'?" to "(A) Shelley  (B) Byron  (C) John Keats  (D) Wordsworth",
            "2. 'To be, or not to be' appears in which play?" to "(A) Macbeth  (B) Hamlet  (C) Othello  (D) King Lear",
            "3. The term 'negative capability' was coined by" to "(A) Coleridge  (B) Keats  (C) Arnold  (D) Eliot",
            "4. 'The Waste Land' was published in" to "(A) 1915  (B) 1922  (C) 1925  (D) 1930",
            "5. Who is known as the 'Father of English Poetry'?" to "(A) Chaucer  (B) Spenser  (C) Milton  (D) Dryden",
        )
        for (p in 1..3) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p).create())
            val c = page.canvas
            c.drawText("BSEB STET 2024 · Paper II · English", 150f, 70f, title)
            c.drawText("Section A — Literature · Page $p", 210f, 92f, body)
            var y = 140f
            qs.forEach { (q, a) -> c.drawText(q, 60f, y, body); c.drawText(a, 75f, y + 18f, body); y += 62f }
            doc.finishPage(page)
        }
        FileOutputStream(f).use { doc.writeTo(it) }; doc.close()
        return f
    }

    suspend fun all(ctx: Context, repo: Repository) {
        val app = ctx.applicationContext as InkApp
        app.securePrefs.setPin("1234")
        val stet = repo.addFolder("STET", 0xFF2B5BD7.toInt()); val bed = repo.addFolder("B.Ed", 0xFF0E8A6B.toInt())
        val eng = repo.addFolder("English Hons", 0xFFB4561F.toInt()); val per = repo.addFolder("Personal", 0xFF8A3FB8.toInt())

        // Exam form (locked)
        val exam = repo.createNote("Exam form details", folderId = per)
        repo.pages(exam).first().let { p -> repo.savePage(exam, p.id, PageContent(handwriting(6, 60f, 160f, 880f, seed = 21))) }
        repo.setLocked(listOf(exam), true)

        // Pedagogy
        val ped = repo.createNote("Pedagogy – Unit 2", folderId = bed)
        repo.pages(ped).first().let { p -> repo.savePage(ped, p.id, PageContent(handwriting(14, 60f, 160f, 880f, seed = 11, color = 0xFF0E8A6B.toInt()), listOf(text(60f, 60f, 880f, "Learning theories — Piaget & Vygotsky")))) }
        repo.setTags(ped, listOf("B.Ed"))

        // Timetable (landscape)
        val tt = repo.createNote("Class timetable", orientation = Orientation.LANDSCAPE, paper = Paper.TIMETABLE, folderId = bed)
        repo.pages(tt).first().let { p -> repo.savePage(tt, p.id, PageContent(handwriting(7, 200f, 190f, 1100f, gap = 100f, seed = 5))) }

        // Romantic poets lecture (grid, recording)
        val rom = repo.createNote("Romantic Poets lecture", paper = Paper.GRID, folderId = stet)
        val wav = File(repo.files.audioDir(rom), "lecture.wav"); silentWav(wav, 30)
        val recId = repo.addRecording(RecordingEntity(noteId = rom, file = wav.name, name = "Lecture · 15 Nov", durationMs = 30_000, createdAt = System.currentTimeMillis()))
        repo.pages(rom).first().let { p -> repo.savePage(rom, p.id, PageContent(handwriting(10, 60f, 160f, 880f, seed = 9, color = 0xFF2B5BD7.toInt(), recId = recId, msPerWord = 600), listOf(text(60f, 60f, 880f, "Keats — beauty is truth, truth beauty")))) }
        repo.addPage(rom, 0)
        repo.setTags(rom, listOf("STET", "Lecture"))

        // PDF
        repo.importPdf(Uri.fromFile(samplePdf(ctx)), stet).let { pdf ->
            repo.rename(pdf, "STET Paper 2 – 2024")
            val p = repo.pages(pdf).first()
            repo.savePage(pdf, p.id, PageContent(listOf(highlight(p.width * 0.33f, p.height * 0.215f, 190f), circle(p.width * 0.36f, p.height * 0.365f, 70f, 30f, INK)) +
                handwriting(1, p.width * 0.55f, p.height * 0.62f, 330f, seed = 3, color = 0xFFC0392B.toInt())))
            repo.setPinned(listOf(pdf), true); repo.setTags(pdf, listOf("STET", "PYQ"))
        }

        // Shopping (typed)
        repo.createTextNote("Hostel shopping", "${RichText.CHECK_ON}Pens (blue, 3)\n${RichText.CHECK_ON}A4 file\n${RichText.CHECK_OFF}Highlighter\n${RichText.CHECK_OFF}Maggi ×4", per)

        // Hamlet (3 pages, last horizontal)
        val ham = repo.createNote("Hamlet – Act III", paper = Paper.LINED, folderId = eng)
        val pages = repo.pages(ham)
        repo.savePage(ham, pages[0].id, PageContent(
            listOf(highlight(70f, 190f, 330f)) + handwriting(25, 60f, 200f, 880f, seed = 3) + circle(760f, 650f, 110f, 60f),
            listOf(text(60f, 50f, 880f, "Hamlet – Act III, Scene 1"))))
        repo.addPage(ham, 0); repo.addPage(ham, 1, Orientation.LANDSCAPE)
        repo.pages(ham).let { l -> repo.savePage(ham, l[1].id, PageContent(handwriting(20, 60f, 120f, 880f, seed = 4))); repo.savePage(ham, l[2].id, PageContent(handwriting(12, 60f, 120f, 1300f, seed = 8))) }
        repo.setFavourite(ham, true); repo.setPinned(listOf(ham), true); repo.setTags(ham, listOf("English"))

        // Trash item
        val old = repo.createNote("Old timetable", orientation = Orientation.LANDSCAPE, paper = Paper.GRID)
        repo.moveToTrash(listOf(old))
        repo.db.notes().get(ham)!!.let { repo.update(it.copy(pageType = PageType.PAGES)) }
        listOf(Kind.INK, AudioKind.FILE).size
    }
}
