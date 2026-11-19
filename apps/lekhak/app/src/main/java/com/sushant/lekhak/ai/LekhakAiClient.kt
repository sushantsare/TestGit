package com.sushant.lekhak.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Built-in provider "Lekhak Premium" — Google Gemini generateContent API. */
class LekhakAiClient(
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/",
    private val client: OkHttpClient = defaultClient,
) {
    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
        val SMART_LABELS = listOf("Casual", "Polished", "Witty")
    }

    suspend fun generate(req: AiRequest, apiKey: String, model: String): List<Suggestion> {
        if (apiKey.isBlank()) throw GeminiKeyMissingException()
        val prompt = if (req.mode == Mode.SMART_REPLY) Prompts.geminiSmartReply(req) else Prompts.geminiRewrite(req)
        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", prompt) }) })
                })
            })
        }
        val request = Request.Builder()
            .url("${baseUrl}models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val (code, text) = client.await(request)
        if (code !in 200..299) throw OpenAIApiException(apiErrorMessage(text).ifBlank { "HTTP $code" })
        val output = extractText(text)
        if (output.isBlank()) throw OpenAIApiException("empty response")
        val labels = if (req.mode == Mode.SMART_REPLY) SMART_LABELS
        else listOf("Polished (${req.tone})", "Concise", "Friendly")
        val list = ResponseParser.parseGemini(output, labels)
        if (list.isEmpty()) throw OpenAIApiException("empty response")
        return list
    }

    /** Joins all non-"thought" text parts of the first candidate. */
    fun extractText(json: String): String = try {
        val root = JsonParser.parseString(json).asJsonObject
        val parts = root.getAsJsonArray("candidates")?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")?.getAsJsonArray("parts") ?: JsonArray()
        parts.mapNotNull { p ->
            val o = p.asJsonObject
            if (o.get("thought")?.asBoolean == true) null else o.get("text")?.asString
        }.joinToString("")
    } catch (_: Exception) {
        ""
    }
}
