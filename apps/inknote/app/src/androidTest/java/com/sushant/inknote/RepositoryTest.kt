package com.sushant.inknote

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.inknote.data.AppDatabase
import com.sushant.inknote.data.Crypto
import com.sushant.inknote.data.NoteFiles
import com.sushant.inknote.data.Orientation
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageType
import com.sushant.inknote.data.Repository
import com.sushant.inknote.data.Source
import com.sushant.inknote.io.Backup
import com.sushant.inknote.io.ExportFormat
import com.sushant.inknote.io.Exporter
import com.sushant.inknote.work.Indexer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        db = AppDatabase.build(ctx, inMemory = true)
        repo = Repository(ctx, db, NoteFiles(ctx))
    }

    @After fun tearDown() { db.close() }

    @Test fun createAddAndTurnPagesKeepsContent(): Unit = runBlocking {
        val id = repo.createNote("Test")
        val p0 = repo.pages(id).first()
        repo.savePage(id, p0.id, PageContent(Seed.handwriting(3, 60f, 200f, 880f)))
        val before = repo.readPage(id, p0.id).strokes.size
        assertTrue(repo.turnPage(id, p0.id))
        val turned = repo.page(p0.id)!!
        assertEquals(Orientation.LANDSCAPE, turned.orientation)
        assertTrue(turned.width > turned.height)
        val c = repo.readPage(id, p0.id)
        assertEquals(before, c.strokes.size)
        assertTrue("content fits the new page", c.strokes.all { s -> (0 until s.size).all { s.x(it) <= turned.width && s.y(it) <= turned.height } })
        repo.addPage(id, 0, Orientation.PORTRAIT); repo.addPage(id, 0)
        assertEquals(listOf(0, 1, 2), repo.pages(id).map { it.idx })
        assertEquals(3, repo.note(id)!!.pageCount)
        repo.movePage(id, 0, 2)
        assertEquals(p0.id, repo.pages(id)[2].id)
    }

    @Test fun lastPageCannotBeDeleted(): Unit = runBlocking {
        val id = repo.createNote("One page")
        val p = repo.pages(id).single()
        assertFalse(repo.deletePage(id, p.id))
        val p2 = repo.addPage(id, 0)
        assertTrue(repo.deletePage(id, p2))
        assertEquals(1, repo.pages(id).size)
    }

    @Test fun endlessPagesAndPdfPagesAreNotTurned(): Unit = runBlocking {
        val id = repo.createNote("Long", pageType = PageType.ENDLESS)
        assertFalse(repo.turnPage(id, repo.pages(id).first().id))
        val pdf = repo.importPdf(Uri.fromFile(Seed.samplePdf(ctx)))
        val pages = repo.pages(pdf)
        assertEquals(3, pages.size)
        assertNotNull(pages.first().pdfFile)
        assertFalse(repo.turnPage(pdf, pages.first().id))
    }

    @Test fun trashRestoreAndPurge(): Unit = runBlocking {
        val id = repo.createNote("Bin me")
        repo.moveToTrash(listOf(id))
        assertNotNull(repo.note(id)!!.deletedAt)
        repo.restore(listOf(id))
        assertNull(repo.note(id)!!.deletedAt)
        repo.moveToTrash(listOf(id))
        db.notes().update(repo.note(id)!!.copy(deletedAt = System.currentTimeMillis() - 31L * 86_400_000))
        repo.purgeExpiredTrash()
        assertNull(repo.note(id))
        assertFalse(repo.files.noteDir(id).exists())
    }

    @Test fun tagsAndFolders(): Unit = runBlocking {
        val f = repo.addFolder("STET", 1)
        val id = repo.createNote("Tagged", folderId = f)
        repo.setTags(id, listOf("#English", "PYQ", " "))
        assertEquals(listOf("English", "PYQ"), repo.tagsFor(id))
        repo.deleteFolder(f)
        assertNull(repo.note(id)!!.folderId)
    }

    @Test fun lockedNotesAreEncryptedOnDisk(): Unit = runBlocking {
        val id = repo.createNote("Secret")
        val p = repo.pages(id).first()
        repo.savePage(id, p.id, PageContent(Seed.handwriting(2, 60f, 200f, 800f), listOf(Seed.text(60f, 60f, 800f, "bank details"))))
        repo.setLocked(listOf(id), true)
        assertTrue(Crypto.isEncrypted(repo.files.pageFile(id, p.id).readBytes()))
        repo.files.clearCaches()
        assertEquals("bank details", repo.readPage(id, p.id).texts.single().plain)
        repo.setLocked(listOf(id), false)
        assertFalse(Crypto.isEncrypted(repo.files.pageFile(id, p.id).readBytes()))
    }

    @Test fun searchFindsTypedTextAndPdfText(): Unit = runBlocking {
        val app = ctx.applicationContext as InkApp
        val r = app.repo
        val id = r.createTextNote("Shopping list", "Buy Maggi and highlighter")
        Indexer.run(app)
        assertTrue(r.search("magg").any { it.noteId == id && it.source == Source.TYPED })
        assertTrue(r.search("shopping").any { it.noteId == id && it.source == Source.TITLE })
        val pdf = r.importPdf(Uri.fromFile(Seed.samplePdf(ctx)))
        Indexer.run(app)
        assertTrue("OCR found words in the PDF", r.search("Nightingale").any { it.noteId == pdf && it.source == Source.PDF })
        r.deleteForever(listOf(id, pdf))
    }

    @Test fun backupRoundTripIncludingLockedNotes(): Unit = runBlocking {
        val app = ctx.applicationContext as InkApp
        val r = app.repo
        app.securePrefs.setPin("2468")
        val a = r.createNote("Backup A", paper = "GRID")
        r.savePage(a, r.pages(a).first().id, PageContent(Seed.handwriting(4, 60f, 200f, 800f)))
        r.setTags(a, listOf("Saved"))
        val b = r.createNote("Backup B locked")
        r.savePage(b, r.pages(b).first().id, PageContent(texts = listOf(Seed.text(60f, 60f, 600f, "locked words"))))
        r.setLocked(listOf(b), true)
        val out = File(ctx.cacheDir, "test.inkbackup")
        Backup(ctx, r).writeNotes(out, listOf(a, b), "2468")
        val res = Backup(ctx, r).restore(Uri.fromFile(out), "2468")
        assertEquals(2, res.notes); assertEquals(0, res.skippedLocked)
        val restoredA = r.db.notes().all().last { it.title == "Backup A" && it.id != a }
        assertEquals(4, r.readPage(restoredA.id, r.pages(restoredA.id).first().id).strokes.size.coerceAtMost(4))
        assertEquals(listOf("Saved"), r.tagsFor(restoredA.id))
        val restoredB = r.db.notes().all().last { it.title == "Backup B locked" && it.id != b }
        assertTrue(restoredB.locked)
        r.files.clearCaches()
        assertEquals("locked words", r.readPage(restoredB.id, r.pages(restoredB.id).first().id).texts.single().plain)
        // wrong PIN: locked note skipped, others restored
        val res2 = Backup(ctx, r).restore(Uri.fromFile(out), "0000")
        assertEquals(1, res2.notes); assertEquals(1, res2.skippedLocked)
        r.deleteForever(r.db.notes().all().filter { it.title.startsWith("Backup") }.map { it.id })
    }

    @Test fun exportPdfAndImages(): Unit = runBlocking {
        val app = ctx.applicationContext as InkApp
        val r = app.repo
        val id = r.createNote("Export me")
        r.savePage(id, r.pages(id).first().id, PageContent(Seed.handwriting(5, 60f, 200f, 800f)))
        r.addPage(id, 0, Orientation.LANDSCAPE)
        val n = r.note(id)!!
        val pdf = Exporter(ctx, r).export(n, ExportFormat.PDF, null).single()
        assertTrue(pdf.length() > 1000)
        android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { pr ->
            assertEquals(2, pr.pageCount)
            pr.openPage(1).use { assertTrue("second page is horizontal", it.width > it.height) }
        }
        assertEquals(2, Exporter(ctx, r).export(n, ExportFormat.PNG, null).size)
        r.deleteForever(listOf(id))
    }
}
