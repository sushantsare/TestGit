package com.sushant.inknote

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.sushant.inknote.ink.RichText
import com.sushant.inknote.ui.screens.snippet
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RichTextTest {
    @Test fun spansSurviveSaving() {
        val sb = android.text.SpannableStringBuilder("Hamlet notes\n☐ revise Act III\nbullet line")
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        sb.setSpan(ForegroundColorSpan(0xFFC0392B.toInt()), 7, 12, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        sb.setSpan(BulletSpan(16), 30, sb.length, Spanned.SPAN_PARAGRAPH)
        val back = RichText.decode(RichText.encode(sb))
        assertEquals(sb.toString(), back.toString())
        assertTrue(back.getSpans(0, 6, StyleSpan::class.java).any { it.style == Typeface.BOLD })
        assertEquals(0xFFC0392B.toInt(), back.getSpans(7, 12, ForegroundColorSpan::class.java).single().foregroundColor)
        assertEquals(1, back.getSpans(0, back.length, BulletSpan::class.java).size)
        assertEquals("Hamlet notes\n☐ revise Act III\nbullet line", RichText.plain(RichText.encode(sb)))
    }

    @Test fun oldPlainTextStillReads() { assertEquals("just text", RichText.decode("just text").toString()) }

    @Test fun searchSnippetHighlightsEveryMatch() {
        val s = snippet("Romantic poets: Wordsworth, Shelley, Keats and keats again", "keats", Color.Black, Color.Yellow)
        assertEquals(2, s.spanStyles.size)
    }
}
