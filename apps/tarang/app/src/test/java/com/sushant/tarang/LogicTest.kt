package com.sushant.tarang

import com.sushant.tarang.data.*
import com.sushant.tarang.playback.Biquad
import com.sushant.tarang.ui.screens.buildStats
import com.sushant.tarang.ui.screens.hourLabel
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class LogicTest {
    // ---------------- lyrics
    @Test fun lrcSyncedLinesSortedWithOffset() {
        val l = LrcParser.parse("[ti:Test]\n[offset:+500]\n[00:12.30]Second\n[00:05.00]First\n[01:00]Third", "x")!!
        assertTrue(l.synced)
        assertEquals(listOf("First", "Second", "Third"), l.lines.map { it.text })
        assertEquals(4_500L, l.lines[0].timeMs)
        assertEquals(11_800L, l.lines[1].timeMs)
        assertEquals(1, l.indexAt(12_000))
        assertEquals(-1, l.indexAt(1_000))
    }

    @Test fun lrcRepeatedStampsAndPlainText() {
        val l = LrcParser.parse("[00:01.00][00:30.00]Chorus\n[00:10.00]Verse", "x")!!
        assertEquals(listOf(1_000L, 10_000L, 30_000L), l.lines.map { it.timeMs })
        val p = LrcParser.parse("\n\nLine one\nLine two", "x")!!
        assertFalse(p.synced); assertEquals("Line one", p.lines.first().text)
        assertNull(LrcParser.parse("   ", "x"))
    }

    // ---------------- artists and formatting
    @Test fun artistsSplit() {
        assertEquals(listOf("Arijit Singh", "Shreya Ghoshal"), splitArtists("Arijit Singh, Shreya Ghoshal"))
        assertEquals(listOf("Pritam", "Arijit Singh"), splitArtists("Pritam feat. Arijit Singh"))
        assertEquals(listOf("A", "B", "C"), splitArtists("A & B / C"))
        assertEquals(listOf(UNKNOWN_ARTIST), splitArtists("  "))
    }

    @Test fun formatting() {
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("0:00", formatDuration(-5))
        assertEquals("2 h 5 m", formatListen(125 * 60_000L))
        assertEquals("45 m", formatListen(45 * 60_000L))
        assertEquals("1 song", plural(1, "song"))
        assertEquals("1,200 songs", plural(1200, "song"))
        assertEquals("4.2 MB", formatSize(4_200_000))
        assertEquals("10 PM – 11 PM", hourLabel(22))
        assertEquals("12 AM – 1 AM", hourLabel(0))
    }

    // ---------------- equalizer filter maths
    @Test fun biquadPeakingHitsGainAtCentre() {
        val b = Biquad(); b.peaking(44_100.0, 1_000.0, 1.0, 6.0)
        assertEquals(6.0, b.responseDb(44_100.0, 1_000.0), 0.1)
        assertTrue(b.responseDb(44_100.0, 50.0) < 0.5)
        val s = Biquad(); s.lowShelf(44_100.0, 110.0, 9.0)
        assertTrue(s.responseDb(44_100.0, 30.0) > 7.5)
        assertTrue(s.responseDb(44_100.0, 5_000.0) < 0.5)
    }

    // ---------------- play/pause morph
    @Test fun playPauseMorphEndsOnBothIcons() {
        // morph 0 = play triangle tip at the right middle; 1 = the right pause bar's top-right corner
        assertEquals(0.85f, com.sushant.tarang.ui.components.playPausePoint(1, 2, 0f), 0.001f)
        assertEquals(0.76f, com.sushant.tarang.ui.components.playPausePoint(1, 2, 1f), 0.001f)
        assertEquals((0.85f + 0.76f) / 2, com.sushant.tarang.ui.components.playPausePoint(1, 2, 0.5f), 0.001f)
    }

    // ---------------- stats
    @Test fun streakCountsDaysInARow() {
        assertEquals(3, streakDays(setOf(10, 9, 8, 5), 10))
        assertEquals(2, streakDays(setOf(9, 8), 10))      // yesterday still counts
        assertEquals(0, streakDays(setOf(7), 10))
    }

    @Test fun statsSumRealListeningAndRankSongs() {
        val day = 86_400_000L
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 20, 21, 0, 0) }.timeInMillis
        val from = now - 3 * day
        val period = StatsPeriod(from, from - 7 * day, (0 until 7).map { "d$it" to from + it * day })
        fun song(id: Long, artist: String) = Song(id, "S$id", artist, "Al", 1, artist, 200_000, 1, "audio/mpeg", "", "/m", "m", "f", 0, 0, 0)
        val byId = mapOf(1L to song(1, "Arijit Singh"), 2L to song(2, "Arijit Singh, Pritam"))
        val rows = listOf(
            PlayEntity(1, 1, from + day + 1000, 120_000, true),
            PlayEntity(2, 1, from + day + 5000, 90_000, true),
            PlayEntity(3, 2, from + 2 * day, 20_000, false),        // under 30 s: time counts, play doesn't
            PlayEntity(4, 2, from - 2 * day, 60_000, true),          // previous period
        )
        val st = buildStats(rows, period, 0, byId, now)
        assertEquals(230_000L, st.totalMs)
        assertEquals(60_000L, st.prevMs)
        assertEquals(2, st.plays)
        assertEquals(1L, st.topSongs.first().first.id)
        assertEquals("Arijit Singh", st.topArtists.first().first)
        assertEquals(210_000L, st.bars[1].second)
        assertEquals(1, st.songsHeard)
    }

    @Test fun unshuffleBringsBackTheOldOrder() {
        // queue was 1,2,3,4,5; shuffled with 3 playing first: 3,5,1,4,2; song 9 added while shuffled
        val now = listOf("3", "5", "1", "4", "2", "9")
        val order = com.sushant.tarang.playback.QueueOrder.restore(now, listOf(1L, 2L, 3L, 4L, 5L))
        assertEquals(listOf("1", "2", "3", "4", "5", "9"), order.map { now[it] })
        // duplicates are matched one by one
        val dup = listOf("2", "1", "2")
        assertEquals(listOf("1", "2", "2"), com.sushant.tarang.playback.QueueOrder.restore(dup, listOf(1L, 2L, 2L)).map { dup[it] })
    }
}
