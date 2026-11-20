package com.sushant.saveo

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.MediaKind
import com.sushant.saveo.data.SaveResult
import com.sushant.saveo.data.DeleteResult
import com.sushant.saveo.data.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = ctx.applicationContext as SaveoApp
    private val repo get() = app.repository
    private lateinit var root: File

    @Before fun setUp(): Unit = runBlocking {
        app.prefs.resetAll()
        app.database.clearAllTables()
        root = Seed.statuses(ctx)
        app.prefs.testDir = root.absolutePath
    }

    @Test fun scanFindsPhotosAndVideosAndSkipsHelpers(): Unit = runBlocking {
        val r = repo.refresh()
        assertEquals(9, r.items.size)
        assertEquals(setOf(Source.WHATSAPP, Source.BUSINESS), r.connected)
        assertTrue(r.items.none { it.name.startsWith(".") })
        // photo + music status is an MP4 → video
        assertEquals(MediaKind.VIDEO, r.items.first { it.name == "VID-MUSIC-001.mp4" }.kind)
        assertEquals(2, r.items.count { it.source == Source.BUSINESS })
        // newest first
        assertTrue(r.items.zipWithNext().all { (a, b) -> a.modified >= b.modified })
    }

    @Test fun saveIsPermanentAndNeverDuplicated(): Unit = runBlocking {
        val items = repo.refresh().items
        val video = items.first { it.kind == MediaKind.VIDEO }
        val r = repo.save(video)
        assertTrue("saved: $r", r is SaveResult.Saved)
        val saved = (r as SaveResult.Saved).item
        assertTrue(repo.saver.exists(Uri.parse(saved.mediaUri)))
        assertTrue("duration read", saved.durationMs > 1000)
        assertTrue(repo.save(video) is SaveResult.AlreadySaved)
        // same content under another name (e.g. re-posted status) → still a duplicate
        val copy = File(File(root, Source.WHATSAPP.name), "COPY.mp4")
        File(video.uri.path!!).copyTo(copy)
        val again = repo.refresh().items.first { it.name == "COPY.mp4" }
        assertTrue(repo.save(again) is SaveResult.AlreadySaved)
        // the original status file is untouched
        assertTrue(File(video.uri.path!!).exists())
        assertEquals(1, repo.saved.first().size)
        assertTrue(video.matchKey in repo.savedKeys.first())
    }

    @Test fun binMoveRestoreAndPurge(): Unit = runBlocking {
        val photo = repo.refresh().items.first { it.kind == MediaKind.IMAGE }
        val saved = (repo.save(photo) as SaveResult.Saved).item
        val del = repo.moveToBin(listOf(saved.id))
        assertTrue(del is DeleteResult.Done)
        val inBin = repo.bin.first().single()
        assertNotNull(inBin.binPath)
        assertTrue(File(inBin.binPath!!).exists())
        assertFalse(repo.saver.exists(Uri.parse(saved.mediaUri)))
        assertTrue(repo.saved.first().isEmpty())

        repo.restore(listOf(saved.id))
        val back = repo.saved.first().single()
        assertTrue(repo.saver.exists(Uri.parse(back.mediaUri)))
        assertTrue(repo.bin.first().isEmpty())

        // purge: only items older than 30 days go
        repo.moveToBin(listOf(back.id))
        repo.purgeBin(now = System.currentTimeMillis() + 29 * Formats.DAY)
        assertEquals(1, repo.bin.first().size)
        repo.purgeBin(now = System.currentTimeMillis() + 31 * Formats.DAY)
        assertTrue(repo.bin.first().isEmpty())
    }

    @Test fun savingAgainFromBinRestoresInsteadOfDuplicating(): Unit = runBlocking {
        val photo = repo.refresh().items.first { it.kind == MediaKind.IMAGE }
        val saved = (repo.save(photo) as SaveResult.Saved).item
        repo.moveToBin(listOf(saved.id))
        val r = repo.save(photo)
        assertTrue(r is SaveResult.Saved)
        assertEquals(saved.id, (r as SaveResult.Saved).item.id)
        assertTrue(repo.bin.first().isEmpty())
    }

    @Test fun newStatusDetection(): Unit = runBlocking {
        val first = repo.refresh().items
        assertTrue("first run never notifies", repo.markSeenAndGetNew(first).isEmpty())
        val f = File(File(root, Source.WHATSAPP.name), "NEW-1.jpg"); Seed.photo(f, 3)
        val fresh = repo.markSeenAndGetNew(repo.refresh().items)
        assertEquals(listOf("NEW-1.jpg"), fresh.map { it.name })
        assertTrue(repo.markSeenAndGetNew(repo.refresh().items).isEmpty())
    }

    @Test fun lostAccessIsReported(): Unit = runBlocking {
        File(root, "LOST_BUSINESS").writeText("x")
        val r = repo.refresh()
        assertEquals(setOf(Source.BUSINESS), r.lost)
        assertTrue(r.items.none { it.source == Source.BUSINESS })
    }

    @Test fun largeFolderScansFast(): Unit = runBlocking {
        val many = Seed.many(ctx, 5000)
        app.prefs.testDir = many.absolutePath
        val t0 = System.currentTimeMillis()
        val r = repo.refresh()
        val ms = System.currentTimeMillis() - t0
        Log.i("SaveoWalk", "T13 scan 5000 files in ${ms}ms")
        assertEquals(5000, r.items.size)
        assertTrue("scan took $ms ms", ms < 5000)
        many.deleteRecursively()
    }
}
