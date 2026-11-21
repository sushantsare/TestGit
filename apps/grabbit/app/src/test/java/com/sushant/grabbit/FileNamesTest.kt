package com.sushant.grabbit

import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.DownloadArgs
import com.sushant.grabbit.download.FileNames
import com.sushant.grabbit.download.GlobalOpts
import com.sushant.grabbit.engine.Errors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test fun removesBadCharacters() {
        assertEquals("a b c d", FileNames.clean("a/b:c*d"))
        assertEquals("Story by @x", FileNames.clean("  Story by @x.. "))
        assertTrue(FileNames.hasBad("what?"))
        assertFalse(FileNames.hasBad("Story by @pall__aavi_07"))
    }

    @Test fun limitsLengthEvenForHindi() {
        val hindi = "नमस्ते दुनिया ".repeat(20)
        val c = FileNames.clean(hindi)
        assertTrue(c.codePointCount(0, c.length) <= FileNames.MAX)
        assertTrue("${c} (12).mp4".toByteArray().size <= 255)
        val longUrlName = "AQML_x-iqcKe_bGz-nhU7Px7gh2kFu3MKLG0g1y3-7vaxVUOfhrk8FzBar-FGDX9gyK6J2GiyDA28wW3YZX3mKK4datoKJUd_FAbG0WYUQ".repeat(3)
        assertEquals(FileNames.MAX, FileNames.clean(longUrlName).length)
    }

    @Test fun uniqueAddsNumber() {
        val taken = setOf("Clip.mp4", "Clip (2).mp4")
        assertEquals("Clip (3).mp4", FileNames.unique("Clip", "mp4") { it in taken })
        assertEquals("New.mp4", FileNames.unique("New", "mp4") { it in taken })
    }

    @Test fun numberedNames() {
        assertEquals("My trip – 01", FileNames.numbered("My trip", 1, 12))
        assertEquals("My trip – 007", FileNames.numbered("My trip", 7, 120))
    }

    @Test fun nameSurvivesOptionRoundTrip() {
        val o = DlOpts(name = "My clip; a=b & more", subLang = "en")
        assertEquals(o, DlOpts.decode(o.encode()))
        assertEquals("", DlOpts.decode("sl=en").name)
    }

    @Test fun downloadTemplateLimitsTitleAndId() {
        val a = DownloadArgs.build("best", false, "/x", DlOpts(), GlobalOpts(), false)
        val tpl = a.first { it.first == "-o" }.second!!
        assertTrue(tpl, "%(title).80B" in tpl && "%(id).40B" in tpl)
        val p = DownloadArgs.build("best", true, "/x", DlOpts(), GlobalOpts(), false).first { it.first == "-o" }.second!!
        assertTrue(p, "%(id).30B" in p)
    }

    @Test fun friendlyLongNameError() {
        assertTrue(Errors.friendly("ERROR: unable to open for writing: [Errno 36] File name too long: '/x'").contains("pen icon"))
    }
}
