package com.sushant.lekhak

import com.sushant.lekhak.core.ChatParser
import com.sushant.lekhak.core.RawText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatParserTest {
    private val w = 1080

    @Test fun noiseIsFiltered() {
        listOf("WhatsApp", "online", "typing...", "Today", "YESTERDAY", "10:45 am", "9:05", "12:30 PM",
            "Messages and calls are end-to-end encrypted.", "Lekhak", "last seen today at 9:00",
            "14 November 2026", "November 14, 2026", "✓✓", "x".repeat(251)).forEach {
            assertTrue("should be noise: $it", ChatParser.isNoise(it))
        }
    }

    @Test fun realMessagesAreKept() {
        listOf("5 baje", "ok", "100", "Kal milte hain", "Where are you?", "हाँ ठीक है").forEach {
            assertFalse("should be kept: $it", ChatParser.isNoise(it))
        }
    }

    @Test fun positionDecidesSender() {
        assertEquals("Them: hello", ChatParser.label(RawText("hello", 40, 500), w))
        assertEquals("You: hi", ChatParser.label(RawText("hi", 700, 1040), w))
        // long messages: theirs spans left side, yours spans right side
        assertEquals("Them: long", ChatParser.label(RawText("long", 30, 900), w))
        assertEquals("You: long", ChatParser.label(RawText("long", 180, 1050), w))
    }

    @Test fun explicitYouPrefixWins() {
        assertEquals("You: done", ChatParser.label(RawText("You: done", 10, 300), w))
        assertEquals("You: ok bro", ChatParser.label(RawText("Selected: You: ok bro", 10, 300), w))
    }

    @Test fun buildPrefersMessageViewsSortsDedupesAndKeepsLastTen() {
        val items = mutableListOf(RawText("Rahul", 200, 600, top = 10)) // header name, not a message view
        for (i in 1..14) items += RawText("msg $i", if (i % 2 == 0) 700 else 40, if (i % 2 == 0) 1040 else 500, true, top = 100 + i * 50)
        items += RawText("msg 14", 700, 1040, true, top = 2000) // duplicate
        val out = ChatParser.build(items.shuffled(), w).lines()
        assertEquals(10, out.size)
        assertEquals("Them: msg 5", out.first())
        assertEquals("You: msg 14", out.last())
        assertFalse(out.any { it.contains("Rahul") })
    }

    @Test fun fallsBackToLeafTextWhenNoMessageViews() {
        val out = ChatParser.build(listOf(
            RawText("Today", 400, 680, top = 50),
            RawText("kab aaoge?", 40, 400, top = 100),
            RawText("10:41 pm", 300, 380, top = 110),
            RawText("shaam ko", 760, 1040, top = 200),
        ), w)
        assertEquals("Them: kab aaoge?\nYou: shaam ko", out)
    }
}
