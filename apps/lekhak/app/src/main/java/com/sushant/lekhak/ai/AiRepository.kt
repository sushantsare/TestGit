package com.sushant.lekhak.ai

import com.sushant.lekhak.core.LekhakServiceManager as M

/** Picks the provider chosen in Settings and returns suggestion cards. */
class AiRepository(
    private val gemini: LekhakAiClient = LekhakAiClient(),
    private val openAi: OpenAIService = OpenAIService(),
) {
    suspend fun generate(req: AiRequest): List<Suggestion> = if (M.useGemini) {
        gemini.generate(req, M.effectiveGeminiKey(), M.geminiModel)
    } else if (req.mode == Mode.SMART_REPLY) {
        openAi.generateSmartReply(req, M.customOpenAiKey, M.customOpenAiUrl, M.customOpenAiModel)
    } else {
        openAi.generateRewrite(req, M.customOpenAiKey, M.customOpenAiUrl, M.customOpenAiModel)
            .map { (label, text) -> Suggestion(label, text) }
    }
}
