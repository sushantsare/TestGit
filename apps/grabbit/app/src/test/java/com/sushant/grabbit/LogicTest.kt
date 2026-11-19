package com.sushant.grabbit

import com.sushant.grabbit.download.Progress
import com.sushant.grabbit.ui.fmtDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class LogicTest {
    @Test
    fun parsesTotalSize(): Unit {
        assertEquals(128_500_000L, Progress.totalBytes("[download]  45.3% of ~ 128.5MB at 2.31MB/s ETA 00:40"))
        assertEquals(128L * 1024 * 1024 + 512L * 1024, Progress.totalBytes("[download]  45.3% of ~ 128.5MiB at 2.31MiB/s"))
        assertEquals((1.5 * 1024 * 1024 * 1024).toLong(), Progress.totalBytes("[download]  2.0% of 1.50GiB at 5MiB/s"))
        assertNull(Progress.totalBytes("[Merger] Merging formats into x.mp4"))
    }

    @Test
    fun sizeText(): Unit {
        val total = 100L * 1024 * 1024
        assertEquals("50.0 MB of 100.0 MB", Progress.sizeText(50f, total))
        assertEquals("", Progress.sizeText(50f, 0))
        assertEquals("100.0 MB of 100.0 MB", Progress.sizeText(140f, total))
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }.timeInMillis

    @Test
    fun compactDates(): Unit {
        val now = at(2026, Calendar.NOVEMBER, 19, 12, 0)
        assertEquals("4:42 AM", fmtDate(at(2026, Calendar.NOVEMBER, 19, 4, 42), now, Locale.US))
        assertEquals("18 Nov", fmtDate(at(2026, Calendar.NOVEMBER, 18, 23, 0), now, Locale.US))
        assertEquals("3 Jan 2025", fmtDate(at(2025, Calendar.JANUARY, 3, 9, 0), now, Locale.US))
    }
}
