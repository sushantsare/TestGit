package com.sushant.grabbit

import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.DownloadArgs
import com.sushant.grabbit.download.GlobalOpts
import com.sushant.grabbit.download.Stages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StagesTest {
    @Test fun processingLinesBecomeStatus() {
        assertEquals("Merging video and audio…", Stages.statusFor("[Merger] Merging formats into \"x.mp4\""))
        assertEquals("Converting audio…", Stages.statusFor("[ExtractAudio] Destination: x.mp3"))
        assertEquals("Adding cover and details…", Stages.statusFor("[EmbedThumbnail] ffmpeg: Adding thumbnail"))
        assertNull(Stages.statusFor("[download]  45.3% of 141.32MiB at 2.31MiB/s ETA 00:40"))
    }

    @Test fun readsAria2Progress() {
        val a = Stages.aria("[#2089b0 12MiB/33MiB(36%) CN:16 DL:4.2MiB ETA:5s]")
        assertNotNull(a); a!!
        assertEquals(36f, a.pct)
        assertEquals("4.2MiB/s", a.speed)
        assertEquals("33MiB", a.total)
    }

    @Test fun multiConnectionOnlyWhenAllowed() {
        val on = DownloadArgs.build("best", false, "/x", DlOpts(), GlobalOpts(faster = true, multiConn = true), false)
        assertTrue(on.any { it.first == "--downloader" && it.second == "aria2c" })
        assertTrue(on.any { it.first == "--downloader" && it.second == "dash,m3u8:native" })
        val off = DownloadArgs.build("best", false, "/x", DlOpts(), GlobalOpts(faster = false, multiConn = true), false)
        assertFalse(off.any { it.first == "--downloader" })
        val noAria = DownloadArgs.build("best", false, "/x", DlOpts(), GlobalOpts(faster = true, multiConn = false), false)
        assertFalse(noAria.any { it.first == "--downloader" })
    }
}
