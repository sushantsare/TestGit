package com.sushant.pageone

import com.sushant.pageone.data.Format
import com.sushant.pageone.data.Library
import com.sushant.pageone.data.PdfEntity
import com.sushant.pageone.pdf.TextMatch
import com.sushant.pageone.ui.reader.Speech
import com.sushant.pageone.ui.screens.keepScore
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class LogicTest {
    @Test fun sizes() {
        assertEquals("512 B", Format.size(512))
        assertEquals("2 KB", Format.size(2048))
        assertEquals("1.2 MB", Format.size(1_258_291))
        assertEquals("38 MB", Format.size(38L * 1024 * 1024))
        assertEquals("3.2 GB", Format.size((3.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun pageRanges() {
        assertEquals(listOf(0, 1, 2, 6), Format.pageRanges("1-3, 7", 10))
        assertEquals(listOf(4, 3, 2), Format.pageRanges("5-3", 10))
        assertEquals(listOf(0, 1), Format.pageRanges("1, 2, 1", 10))
        assertNull(Format.pageRanges("0-2", 10))
        assertNull(Format.pageRanges("3-11", 10))
        assertNull(Format.pageRanges("a,b", 10))
        assertNull(Format.pageRanges("", 10))
    }

    @Test fun fileNames() {
        assertEquals("Notes.pdf", Format.fileName("Notes"))
        assertEquals("a_b.pdf", Format.fileName("a/b"))
        assertEquals("PageOne.pdf", Format.fileName("  "))
        assertEquals("x.PDF", Format.fileName("x.PDF"))
    }

    @Test fun folderNames() {
        assertEquals("WhatsApp Documents", Format.folderName("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"))
        assertEquals("Phone storage", Format.folderName("/storage/emulated/0"))
        assertEquals("Download", Format.folderName("/storage/emulated/0/Download/"))
    }

    @Test fun dayGroups() {
        val now = Calendar.getInstance().apply { set(2026, 10, 27, 15, 0) }.timeInMillis
        assertEquals(Format.Day.TODAY, Format.dayGroup(now - 3_600_000, now))
        assertEquals(Format.Day.YESTERDAY, Format.dayGroup(now - 20 * 3_600_000L, now))
        assertEquals(Format.Day.EARLIER, Format.dayGroup(now - 3 * 86_400_000L, now))
        assertTrue(Format.opened(now - 20 * 3_600_000L, now).startsWith("Opened yesterday"))
    }

    @Test fun textMatching() {
        val t = "The tense of a verb. Tenses are hard. TENSE!"
        assertEquals(listOf(4, 21, 38), TextMatch.indices(t, "tense", matchCase = false, wholeWord = false))
        assertEquals(listOf(4, 38), TextMatch.indices(t, "tense", matchCase = false, wholeWord = true))
        assertEquals(listOf(4), TextMatch.indices(t, "tense", matchCase = true, wholeWord = true))
        assertTrue(TextMatch.ok(t, 999, "x", true, true))   // can't check → keep
    }

    @Test fun fingerprintSameBytesSame() {
        val a = Library.fingerprint(100, ByteArray(10) { 1 }, ByteArray(10) { 2 })
        val b = Library.fingerprint(100, ByteArray(10) { 1 }, ByteArray(10) { 2 })
        val c = Library.fingerprint(101, ByteArray(10) { 1 }, ByteArray(10) { 2 })
        assertEquals(a, b); assertNotEquals(a, c); assertEquals(20, a.length)
    }

    @Test fun sentences() {
        val s = Speech.split("The sun rises in the east. The train leaves at 6 p.m. Does it?  Yes!")
        assertEquals(listOf("The sun rises in the east.", "The train leaves at 6 p.m.", "Does it?", "Yes!"), s)
        assertTrue(Speech.split("   ").isEmpty())
        assertTrue(Speech.split("x".repeat(700)).all { it.length <= 300 })
    }

    @Test fun duplicateKeepsOwnFolderCopy() {
        val own = PdfEntity("/s/Lucent Books/a.pdf", "a.pdf", "/s/Lucent Books", 10, 500)
        val wa = PdfEntity("/s/WhatsApp Documents/a.pdf", "a.pdf", "/s/WhatsApp Documents", 10, 100)
        assertEquals(own, listOf(wa, own).minBy { keepScore(it) })
    }

    @Test fun percent() {
        assertEquals(34, Format.percent(141, 418))
        assertEquals(100, Format.percent(417, 418))
        assertEquals(0, Format.percent(0, 0))
    }
}
