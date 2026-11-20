package com.sushant.grabbit

import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.DownloadArgs
import com.sushant.grabbit.download.GlobalOpts
import com.sushant.grabbit.engine.SubtitleOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadArgsTest {
    private fun args(q: String, o: DlOpts = DlOpts(), g: GlobalOpts = GlobalOpts(), yt: Boolean = false, pl: Boolean = false) =
        DownloadArgs.build(q, pl, "/tmp/x", o, g, yt)
    private fun List<Pair<String, String?>>.v(k: String) = firstOrNull { it.first == k }?.second
    private fun List<Pair<String, String?>>.has(k: String) = any { it.first == k }

    @Test
    fun optionsRoundTrip(): Unit {
        val o = DlOpts("hi", true, "both", 12, 95, true, "opus")
        assertEquals(o, DlOpts.decode(o.encode()))
        assertEquals(DlOpts(), DlOpts.decode(""))
        assertEquals(DlOpts(), DlOpts.decode("garbage"))
    }

    @Test
    fun compatibleVsHighestQuality(): Unit {
        assertEquals("res:720,vcodec:h264,acodec:aac", args("h720").v("-S"))
        assertEquals("mp4", args("h720").v("--merge-output-format"))
        val hq = args("h720", g = GlobalOpts(videoFormat = 1))
        assertEquals("res:720", hq.v("-S"))
        assertEquals("mp4/mkv", hq.v("--merge-output-format"))
    }

    @Test
    fun audioFormatsAndBitrate(): Unit {
        val m4a = args("a128", DlOpts(audioFormat = "m4a"))
        assertEquals("m4a", m4a.v("--audio-format"))
        assertEquals("128K", m4a.v("--audio-quality"))
        assertTrue(m4a.has("--embed-thumbnail"))
        val orig = args("audio", DlOpts(audioFormat = "best"))
        assertEquals("best", orig.v("--audio-format"))
        assertFalse(orig.has("--audio-quality"))
        assertFalse(args("audio", DlOpts(audioFormat = "opus")).has("--embed-thumbnail"))
    }

    @Test
    fun subtitles(): Unit {
        val inside = args("best", DlOpts(subLang = "en"))
        assertTrue(inside.has("--write-subs")); assertTrue(inside.has("--embed-subs"))
        assertEquals("en", inside.v("--sub-langs")); assertEquals("srt", inside.v("--convert-subs"))
        val file = args("best", DlOpts(subLang = "hi", subAuto = true, subMode = "file"))
        assertTrue(file.has("--write-auto-subs")); assertFalse(file.has("--embed-subs"))
        assertFalse(args("best").has("--sub-langs"))
        assertFalse(args("audio", DlOpts(subLang = "en")).has("--sub-langs"))
    }

    @Test
    fun trimChaptersSponsorFaster(): Unit {
        val a = args("best", DlOpts(trimStart = 10, trimEnd = 70, splitChapters = true), GlobalOpts(sponsorCats = setOf("sponsor", "intro")), yt = true)
        assertEquals("*10-70", a.v("--download-sections"))
        assertTrue(a.has("--force-keyframes-at-cuts"))
        assertTrue(a.has("--split-chapters"))
        assertTrue(a.any { it.second?.startsWith("chapter:/tmp/x/ch_") == true })
        assertEquals("intro,sponsor", a.v("--sponsorblock-remove"))
        assertEquals("4", a.v("--concurrent-fragments"))
        assertFalse(args("best", g = GlobalOpts(sponsorCats = setOf("sponsor"))).has("--sponsorblock-remove"))
        assertFalse(args("best", g = GlobalOpts(faster = false)).has("--concurrent-fragments"))
        assertFalse(args("best", DlOpts(trimStart = 5, trimEnd = 9), pl = true).has("--download-sections"))
    }

    @Test
    fun subtitleListPutsUploadedFirstAndLimitsAuto(): Unit {
        val auto = listOf("en", "hi", "fr", "de", "sw", "en-orig", "ja")
        val list = SubtitleOptions.build(listOf("en", "es", "live_chat"), auto, "en", "hi")
        assertEquals(listOf("en", "es"), list.filter { !it.auto }.map { it.code })
        val autos = list.filter { it.auto }.map { it.code }
        assertTrue("hi" in autos); assertTrue("ja" in autos); assertFalse("sw" in autos)
        assertFalse("en-orig" in autos) // uploaded English already exists
        assertEquals("English", list.first().name)
    }
}
