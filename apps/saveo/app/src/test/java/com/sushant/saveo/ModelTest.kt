package com.sushant.saveo

import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.MediaKind
import com.sushant.saveo.data.MediaTypes
import com.sushant.saveo.data.matchKeyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test fun photoWithMusicIsVideo() {
        // WhatsApp delivers photo + music statuses as MP4: the real MIME type wins
        assertEquals(MediaKind.VIDEO, MediaTypes.kindOf("AbC123.mp4", "video/mp4"))
        assertEquals(MediaKind.VIDEO, MediaTypes.kindOf("weird.jpg", "video/mp4"))
        assertEquals(MediaKind.IMAGE, MediaTypes.kindOf("x.jpg", null))
        assertEquals(MediaKind.IMAGE, MediaTypes.kindOf("x.WEBP", null))
        assertEquals(MediaKind.VIDEO, MediaTypes.kindOf("x.3gp", null))
        assertNull(MediaTypes.kindOf("notes.txt", null))
    }

    @Test fun hiddenAndHelperFilesSkipped() {
        assertFalse(MediaTypes.isStatusFile(".nomedia", null))
        assertFalse(MediaTypes.isStatusFile(".trashed-1.jpg", null))
        assertFalse(MediaTypes.isStatusFile("abc.tmp", null))
        assertFalse(MediaTypes.isStatusFile("", null))
        assertTrue(MediaTypes.isStatusFile("4F2A.jpg", "image/jpeg"))
    }

    @Test fun mimeTypes() {
        assertEquals("image/jpeg", MediaTypes.mimeFor("a.JPG", MediaKind.IMAGE))
        assertEquals("video/mp4", MediaTypes.mimeFor("a.mp4", MediaKind.VIDEO))
        assertEquals("video/mp4", MediaTypes.mimeFor("noext", MediaKind.VIDEO))
        assertEquals("image/jpeg", MediaTypes.mimeFor("noext", MediaKind.IMAGE))
    }

    @Test fun durations() {
        assertEquals("0:00", Formats.duration(0))
        assertEquals("0:15", Formats.duration(15_000))
        assertEquals("0:30", Formats.duration(29_600))
        assertEquals("1:02", Formats.duration(62_000))
        assertEquals("1:00:05", Formats.duration(3_605_000))
    }

    @Test fun dayGroups() {
        val now = System.currentTimeMillis()
        val start = Formats.startOfDay(now)
        assertEquals(Formats.DayGroup.TODAY, Formats.dayGroup(start + 1, now))
        assertEquals(Formats.DayGroup.YESTERDAY, Formats.dayGroup(start - 1, now))
        assertEquals(Formats.DayGroup.EARLIER, Formats.dayGroup(start - Formats.DAY - 1, now))
    }

    @Test fun binDaysLeft() {
        val now = 1_000_000_000_000L
        assertEquals(30, Formats.binDaysLeft(now, now))
        assertEquals(29, Formats.binDaysLeft(now - Formats.DAY, now))
        assertEquals(1, Formats.binDaysLeft(now - 29 * Formats.DAY - 1000, now))
        assertEquals(0, Formats.binDaysLeft(now - 31 * Formats.DAY, now))
    }

    @Test fun matchKey() {
        assertEquals("a.jpg|10", matchKeyOf("a.jpg", 10))
    }
}
