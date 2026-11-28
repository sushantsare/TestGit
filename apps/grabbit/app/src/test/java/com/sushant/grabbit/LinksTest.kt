package com.sushant.grabbit

import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.engine.Errors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun youtubeMusicPlaylistBecomesYoutube() {
        assertEquals("https://www.youtube.com/playlist?list=PLAdFiDLf78fc",
            Sites.cleanUrl("https://music.youtube.com/playlist?list=PLAdFiDLf78fc&si=la54-Alb-V48oB53&drli=1"))
    }

    @Test fun trackingRemovedOtherwiseUnchanged() {
        assertEquals("https://youtu.be/abc", Sites.cleanUrl("https://youtu.be/abc?si=XYZ"))
        assertEquals("https://www.youtube.com/watch?v=abc&t=30", Sites.cleanUrl("https://www.youtube.com/watch?v=abc&pp=ygU&t=30"))
        assertEquals("https://music.youtube.com/watch?v=abc&list=RD1", Sites.cleanUrl("https://music.youtube.com/watch?v=abc&list=RD1&si=x"))
        assertEquals("https://www.instagram.com/p/C9x/", Sites.cleanUrl("https://www.instagram.com/p/C9x/?igsh=abc"))
        assertEquals("https://x.com/a/status/1", Sites.cleanUrl("https://x.com/a/status/1"))
    }

    @Test fun sharedTextIsCleaned() {
        assertEquals("https://www.youtube.com/playlist?list=PL1",
            Sites.extractUrl("Listen: https://music.youtube.com/playlist?list=PL1&si=a."))
    }

    @Test fun plainPlaylistAndUpdateErrors() {
        val tab = Errors.friendly("ERROR: [youtube:tab] PLAdFiDLf78fc: Unable to recognize tab page; please report this issue on https://github.com/yt-dlp/yt-dlp/issues?q= , filling out the appropriate issue template. Confirm you are on the latest version using  yt-dlp -U")
        assertTrue(tab.startsWith("This playlist couldn't be opened"))
        assertFalse(tab.contains("github"))
        val upd = Errors.friendly("ERROR: [youtube] abc: Some new failure; please report this issue on https://github.com/yt-dlp/yt-dlp/issues . Confirm you are on the latest version using yt-dlp -U")
        assertTrue(upd.startsWith("YouTube changed something"))
    }
}
