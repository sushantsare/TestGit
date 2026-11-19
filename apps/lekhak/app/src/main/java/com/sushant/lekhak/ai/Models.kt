package com.sushant.lekhak.ai

data class Suggestion(val label: String, val text: String, val isError: Boolean = false)

enum class Mode { SMART_REPLY, REWRITE }

data class AiRequest(
    val mode: Mode,
    val history: String,
    val draft: String,
    val tone: String,
    val language: String,
    val customInstruction: String = "",
)

sealed class AiException(message: String) : Exception(message)
class OpenAIKeyMissingException : AiException("Please add your OpenAI API key in Settings")
class GeminiKeyMissingException : AiException("Please add your Gemini API key in Lekhak Settings")
class OpenAINetworkException : AiException("Could not connect. Check your internet.")
class OpenAIApiException(detail: String = "") : AiException(
    if (detail.isBlank()) "AI generation failed. Try again." else "AI generation failed: $detail"
)
