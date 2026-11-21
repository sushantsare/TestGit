package com.sushant.gallery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.gallery.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real files on the emulator: bin move/restore/purge, favourites follow moves, hidden album round trip. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val app get() = inst.targetContext.applicationContext as GalleryApp
    private val dir = File(Seed.root, "Pictures/GalleryRepoTest")

    @Before fun setUp() {
        Seed.grantAllFiles(inst)
        dir.deleteRecursively(); dir.mkdirs()
    }

    private fun waitItem(path: String, present: Boolean = true): MediaItem? = runBlocking {
        withTimeout(20_000) {
            while (true) {
                app.repo.refresh(); delay(700)
                val m = app.repo.items.value.firstOrNull { it.path == path }
                if ((m != null) == present) return@withTimeout m
            }
            null
        }
    }

    @Test fun binMoveRestoreAndPurge(): Unit = runBlocking {
        val f = File(dir, "bin_test.jpg"); Seed.photo(f, 1, System.currentTimeMillis()); Seed.scan(inst, listOf(f))
        val bytes = f.readBytes()
        val item = waitItem(f.path)!!
        app.repo.setFavourite(listOf(item), true)
        val done = app.repo.moveToBin(listOf(item), fromFilmstrip = true)
        assertEquals(1, done.size)
        assertFalse("original gone", f.exists())
        assertTrue("in bin folder", File(done[0].trashPath).exists())
        assertTrue(done[0].wasFavourite)
        waitItem(f.path, present = false)

        assertEquals(1, app.repo.restore(done))
        assertTrue("restored", f.exists())
        assertArrayEquals("same bytes", bytes, f.readBytes())
        delay(500)
        assertTrue("favourite restored", f.path in app.repo.favourites.value)

        // purge: entries older than 30 days are deleted for good
        val again = app.repo.moveToBin(listOf(waitItem(f.path)!!))
        assertEquals(1, app.repo.purgeExpired(now = System.currentTimeMillis() + 31L * 86_400_000L))
        assertFalse(File(again[0].trashPath).exists())
    }

    @Test fun moveKeepsFavourite(): Unit = runBlocking {
        val f = File(dir, "move_me.jpg"); Seed.photo(f, 2, System.currentTimeMillis()); Seed.scan(inst, listOf(f))
        val item = waitItem(f.path)!!
        app.repo.setFavourite(listOf(item), true)
        val target = File(dir, "Sub")
        assertEquals(1, app.repo.transfer(listOf(item), target, copy = false))
        val moved = File(target, "move_me.jpg")
        assertTrue("moved file exists", moved.exists()); assertFalse("old gone", f.exists())
        delay(2000)
        assertTrue("moved file still exists after media update", moved.exists())
        assertTrue("favourite follows the move", moved.path in app.repo.favourites.value)
        assertEquals(1, app.repo.transfer(listOf(waitItem(moved.path)!!), dir, copy = true))
        delay(1000)
        assertTrue("copy exists", File(dir, "move_me.jpg").exists())
        assertTrue("source of copy kept", moved.exists())
    }

    @Test fun hiddenAlbumRoundTrip(): Unit = runBlocking {
        val f = File(dir, "secret.jpg"); Seed.photo(f, 3, System.currentTimeMillis()); Seed.scan(inst, listOf(f))
        val bytes = f.readBytes()
        val item = waitItem(f.path)!!
        assertEquals(1, app.repo.hide(listOf(item)))
        assertFalse("original removed", f.exists())
        delay(500)
        val hidden = app.repo.vaultItems.value.first { it.name == "secret.jpg" }
        val enc = File(app.vault.dir, hidden.vaultId + ".bin")
        assertTrue(enc.exists())
        assertFalse("stored encrypted", enc.readBytes().copyOfRange(100, 200).contentEquals(bytes.copyOfRange(100, 200)))
        assertNotNull(app.vault.thumbBytes(hidden.vaultId!!))
        assertEquals(1, app.repo.unhide(listOf(hidden)))
        assertTrue(f.exists())
        assertArrayEquals(bytes, f.readBytes())
        assertFalse(enc.exists())
    }
}
