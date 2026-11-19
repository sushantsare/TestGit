package com.sushant.lekhak

import com.sushant.lekhak.ai.AiRequest
import com.sushant.lekhak.ai.GeminiKeyMissingException
import com.sushant.lekhak.ai.LekhakAiClient
import com.sushant.lekhak.ai.Mode
import com.sushant.lekhak.ai.OpenAIApiException
import com.sushant.lekhak.ai.OpenAIKeyMissingException
import com.sushant.lekhak.ai.OpenAINetworkException
import com.sushant.lekhak.ai.OpenAIService
import com.sushant.lekhak.ai.Prompts
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AiClientTest {
    private lateinit var server: MockWebServer
    private val smart = AiRequest(Mode.SMART_REPLY, "Them: kal free ho?", "", "Casual", "Hinglish")
    private val rewrite = AiRequest(Mode.REWRITE, "Them: file bhejo", "bhej dunga", "Professional", "English", "make it polite")

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { try { server.shutdown() } catch (_: Exception) {} }

    @Test fun geminiSendsKeyHeaderAndParses(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[
            {"text":"thinking...","thought":true},
            {"text":"[Option 1 - Casual]\nHaan free hoon\n###\n[Option 2 - Polished]\nYes, I am free tomorrow.\n###\n[Option 3 - Witty]\nTere liye toh hamesha!"}]}}]}"""))
        val out = LekhakAiClient(server.url("/v1beta/").toString()).generate(smart, "KEY123", "gemini-flash-latest")
        val req = server.takeRequest()
        assertEquals("KEY123", req.getHeader("x-goog-api-key"))
        assertEquals("/v1beta/models/gemini-flash-latest:generateContent", req.path)
        assertTrue(req.body.readUtf8().contains("Hinglish"))
        assertEquals(3, out.size)
        assertEquals("Haan free hoon", out[0].text)
    }

    @Test fun geminiApiErrorShowsMessage(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid."}}"""))
        try {
            LekhakAiClient(server.url("/").toString()).generate(smart, "bad", "m")
            fail()
        } catch (e: OpenAIApiException) {
            assertTrue(e.message!!.contains("API key not valid"))
        }
    }

    @Test(expected = GeminiKeyMissingException::class)
    fun geminiNeedsKey(): Unit = runBlocking { LekhakAiClient(server.url("/").toString()).generate(smart, "", "m"); Unit }

    @Test fun openAiRewriteParsesPairs(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"1. Polite: I will send it soon.\n2. Professional: I shall share the file shortly.\n3. Friendly: Bhej raha hoon!"}}]}"""))
        val out = OpenAIService().generateRewrite(rewrite, "sk-x", server.url("/v1").toString(), "gpt-4o-mini")
        val req = server.takeRequest()
        assertEquals("Bearer sk-x", req.getHeader("Authorization"))
        assertEquals("/v1/chat/completions", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"max_tokens\":300") && body.contains("bhej dunga"))
        assertEquals(listOf("Polite", "Professional", "Friendly"), out.map { it.first })
    }

    @Test(expected = OpenAIKeyMissingException::class)
    fun openAiNeedsKey(): Unit = runBlocking { OpenAIService().generateSmartReply(smart, "", server.url("/").toString(), "m"); Unit }

    @Test fun networkFailureIsTyped(): Unit = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        try {
            OpenAIService().generateSmartReply(smart, "k", url, "m")
            fail()
        } catch (e: OpenAINetworkException) {
            assertEquals("Could not connect. Check your internet.", e.message)
        }
    }

    @Test fun promptsCarryToneLanguageAndDraft() {
        val p = Prompts.geminiRewrite(rewrite)
        assertTrue(p.contains("bhej dunga") && p.contains("Professional") && p.contains("make it polite"))
        assertTrue(Prompts.geminiSmartReply(smart.copy(language = "Hindi")).contains("Devanagari"))
        assertTrue(Prompts.geminiSmartReply(smart).contains("[Option 3 - Witty]"))
    }
}
