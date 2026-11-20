package com.sushant.gallery

import android.net.Uri
import com.sushant.gallery.data.AlbumSort
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.PhotoSort
import com.sushant.gallery.data.Repository
import com.sushant.gallery.data.Sorting
import com.sushant.gallery.data.Source
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LibraryTest {
    private val day = 86_400_000L
    private val now = LocalDate.of(2026, 11, 21).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun item(name: String, folder: String, ageDays: Int, size: Long = 100, video: Boolean = false) = MediaItem(
        id = name.hashCode().toLong(), uri = Uri.EMPTY, path = "$folder/$name", name = name, mime = if (video) "video/mp4" else "image/jpeg",
        isVideo = video, dateTaken = now - ageDays * day, dateModified = now - ageDays * day, size = size, width = 0, height = 0,
        durationMs = 0, folder = folder, bucket = folder.substringAfterLast('/'),
    )

    private val lib = listOf(
        item("a.jpg", "/s/DCIM/Camera", 0), item("b.jpg", "/s/DCIM/Camera", 1, 500), item("c.mp4", "/s/Movies", 3, 50, video = true),
        item("d.jpg", "/s/Pictures/Screenshots", 40), item("e.jpg", "/s/Download", 0, 900),
    )

    @Test fun sortsPhotos() {
        assertEquals(listOf("e.jpg", "a.jpg", "b.jpg", "c.mp4", "d.jpg"), Sorting.photos(lib, PhotoSort.DATE_TAKEN, true).map { it.name })
        assertEquals("d.jpg", Sorting.photos(lib, PhotoSort.DATE_TAKEN, false).first().name)
        assertEquals(listOf("e.jpg", "b.jpg"), Sorting.photos(lib, PhotoSort.SIZE, true).take(2).map { it.name })
        assertEquals("a.jpg", Sorting.photos(lib, PhotoSort.NAME, false).first().name)
    }

    @Test fun groupsByDay() {
        val g = Sorting.groups(Sorting.photos(lib, PhotoSort.DATE_TAKEN, true), PhotoSort.DATE_TAKEN, LocalDate.of(2026, 11, 21))
        assertEquals(listOf("Today", "Yesterday", "Wed, 18 Nov", "October 2026"), g.map { it.first })
        assertEquals(2, g[0].second.size)
        assertEquals(1, Sorting.groups(lib, PhotoSort.NAME).size)
    }

    @Test fun albumsCameraFirst() {
        val albums = Sorting.albums(Repository.buildAlbums(lib), AlbumSort.NAME)
        assertEquals(listOf("Camera", "Download", "Movies", "Screenshots"), albums.map { it.name })
        assertEquals(2, albums.first().count)
        assertEquals("a.jpg", albums.first().cover?.name)
        assertEquals("Camera", Sorting.albums(Repository.buildAlbums(lib), AlbumSort.COUNT).first().name)
    }

    @Test fun listsForSources() {
        val repo = Repository.Companion
        val favs = setOf("/s/DCIM/Camera/b.jpg")
        val fake = lib
        assertEquals(1, fake.filter { it.isVideo }.size)
        assertEquals(listOf("b.jpg"), fake.filter { it.path in favs }.map { it.name })
        assertEquals(listOf("a.jpg", "b.jpg"), fake.filter { it.folder == (Source.Folder("/s/DCIM/Camera")).folder }.map { it.name })
        assertEquals(repo.buildAlbums(emptyList()).size, 0)
    }
}
