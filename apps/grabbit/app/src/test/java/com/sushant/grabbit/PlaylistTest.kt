package com.sushant.grabbit

import com.sushant.grabbit.data.PlaylistPick
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.engine.PlaylistParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {
    @Test fun parsesFlatEntries() {
        val j = JSONObject("""{"_type":"playlist","id":"PL1","playlist_count":250,"entries":[
            {"id":"aaa","url":"https://www.youtube.com/watch?v=aaa","title":"One","duration":61,
             "thumbnails":[{"url":"s","width":120},{"url":"m","width":320},{"url":"l","width":1280}]},
            {"id":"bbb","url":"bbb","title":"[Private video]","duration":null},
            {"id":"ccc","title":"Three","availability":"public"}]}""")
        val e = PlaylistParser.entries(j, 100, "YouTube")
        assertEquals(listOf(101, 102, 103), e.map { it.index })
        assertEquals("m", e[0].thumb)
        assertTrue(e[0].available)
        assertFalse(e[1].available)
        assertEquals("https://www.youtube.com/watch?v=ccc", e[2].url)
        assertEquals(250, PlaylistParser.total(j))
    }

    @Test fun ranges() {
        assertEquals(setOf(1, 2, 3, 7, 13, 14, 15), PlaylistPick.range("1-3, 7, -3:", 15))
        assertEquals(setOf(4, 5, 6), PlaylistPick.range("4–6", 15))
        assertEquals(setOf(15), PlaylistPick.range("-1", 15))
        assertEquals((10..20).toSet(), PlaylistPick.range("10-", 20))
        assertEquals(setOf(1, 2), PlaylistPick.range("1 2 99", 10))
        assertNull(PlaylistPick.range("abc", 10))
        assertNull(PlaylistPick.range("", 10))
    }

    @Test fun orders() {
        val l = listOf(1 to 300L, 2 to 100L, 3 to 200L)
        fun o(m: Int) = PlaylistPick.order(l, m, 7L, { it.first }, { it.second }).map { it.first }
        assertEquals(listOf(1, 2, 3), o(PlaylistPick.IN_ORDER))
        assertEquals(listOf(3, 2, 1), o(PlaylistPick.REVERSED))
        assertEquals(listOf(1, 3, 2), o(PlaylistPick.NEWEST))
        assertEquals(o(PlaylistPick.RANDOM), o(PlaylistPick.RANDOM))
        assertEquals(3, o(PlaylistPick.RANDOM).toSet().size)
        // Without dates, newest first = last added first
        assertEquals(listOf(3, 2, 1), PlaylistPick.order(l, PlaylistPick.NEWEST, 1, { it.first }, { 0L }).map { it.first })
    }

    @Test fun fileNames() {
        assertEquals("04 - Motion", PlaylistPick.fileName(4, 48, "Motion"))
        assertEquals("004 - Motion", PlaylistPick.fileName(4, 120, "Motion"))
    }

    @Test fun groupFieldsSurviveRoundTrip() {
        val o = DlOpts(grp = "abc123", plId = "PL;x=1", gt = "Physics & Maths", gi = 12, vid = "v1", sub = "Physics & Maths")
        assertEquals(o, DlOpts.decode(o.encode()))
    }
}
