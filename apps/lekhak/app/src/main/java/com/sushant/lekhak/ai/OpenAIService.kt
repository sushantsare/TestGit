package com.sushant.lekhak.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Custom provider — any OpenAI-compatible chat/completions endpoint. */
class OpenAIService(private val client: OkHttpClient = defaultClient) {
    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun generateSmartReply(req: AiRequest, key: String, baseUrl: String, model: String): List<Suggestion> {
        val out = call(Prompts.openAiSystem(req), Prompts.openAiSmartReply(req), key, baseUrl, model)
        return ResponseParser.parseNumbered(out, listOf("Reply 1", "Reply 2", "Reply 3"))
            .ifEmpty { throw OpenAIApiException() }
    }

    suspend fun generateRewrite(req: AiRequest, key: String, baseUrl: String, model: String): List<Pair<String, String>> {
        val out = call(Prompts.openAiSystem(req), Prompts.openAiRewrite(req), key, baseUrl, model)
        return ResponseParser.parseNumbered(out, listOf("Polite", "Professional", "Friendly"))
            .ifEmpty { throw OpenAIApiException() }
            .map { it.label to it.text }
    }

    private suspend fun call(system: String, user: String, key: String, baseUrl: String, model: String): String {
        if (key.isBlank()) throw OpenAIKeyMissingException()
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 300)
            addProperty("temperature", 0.7)
            add("messages", JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", system) })
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", user) })
            })
        }
        val request = try {
            Request.Builder()
                .url("${base}chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        } catch (_: IllegalArgumentException) {
            throw OpenAIApiException("invalid API URL")
        }
        val (code, text) = client.await(request)
        if (code !in 200..299) throw OpenAIApiException(apiErrorMessage(text).ifBlank { "HTTP $code" })
        return try {
            JsonParser.parseString(text).asJsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
        } catch (_: Exception) {
            throw OpenAIApiException()
        }
    }
}
