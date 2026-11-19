package com.sushant.lekhak

import com.sushant.lekhak.ai.ResponseParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseParserTest {
    private val labels = listOf("Casual", "Polished", "Witty")

    @Test fun parsesGeminiBlocks() {
        val raw = """
[Option 1 - Casual]
Haan bhai, aa raha hoon!
###
[Option 2 - Polished]
"Yes, I will be there by 5."
###
**[Option 3 - Witty]**
Main time pe aaunga, tum chai ready rakho ☕
""".trimIndent()
        val s = ResponseParser.parseGemini(raw, labels)
        assertEquals(3, s.size)
        assertEquals("Casual", s[0].label)
        assertEquals("Haan bhai, aa raha hoon!", s[0].text)
        assertEquals("Yes, I will be there by 5.", s[1].text)
        assertEquals("Witty", s[2].label)
        assertEquals("Main time pe aaunga, tum chai ready rakho ☕", s[2].text)
    }

    @Test fun geminiLabelWithTone() {
        val s = ResponseParser.parseGemini("[Option 1 - Polished (Professional)]\nI will send it today.\n###\n[Option 2 - Concise]\nSending today.", labels)
        assertEquals("Polished (Professional)", s[0].label)
        assertEquals("Concise", s[1].label)
    }

    @Test fun geminiFallbackAddsShortVariant() {
        val s = ResponseParser.parseGemini("Sure, I can do that. Let me check my calendar first.", labels)
        assertEquals(2, s.size)
        assertEquals("Casual", s[0].label)
        assertEquals("Sure, I can do that.", s[1].text)
    }

    @Test fun geminiMissingHeadersUseDefaults() {
        val s = ResponseParser.parseGemini("one\n###\ntwo\n###\nthree", labels)
        assertEquals(listOf("Casual", "Polished", "Witty"), s.map { it.label })
        assertEquals(listOf("one", "two", "three"), s.map { it.text })
    }

    @Test fun parsesNumberedReplies() {
        val s = ResponseParser.parseNumbered("1. Sure!\n2. Sounds good,\nsee you then.\n3) \"Done\"", listOf("A", "B", "C"))
        assertEquals(3, s.size)
        assertEquals("Sure!", s[0].text)
        assertEquals("Sounds good,\nsee you then.", s[1].text)
        assertEquals("Done", s[2].text)
        assertEquals("C", s[2].label)
    }

    @Test fun parsesStyledRewrites() {
        val s = ResponseParser.parseNumbered("1. Polite: Could you send it?\n2. **Professional:** Kindly share the file.\n3. Friendly: Send it na!", listOf("x", "y", "z"))
        assertEquals(listOf("Polite", "Professional", "Friendly"), s.map { it.label })
        assertEquals("Kindly share the file.", s[1].text)
    }

    @Test fun emptyGivesEmpty() {
        assertEquals(0, ResponseParser.parseGemini("   ", labels).size)
        assertEquals(0, ResponseParser.parseNumbered("", labels).size)
    }
}
