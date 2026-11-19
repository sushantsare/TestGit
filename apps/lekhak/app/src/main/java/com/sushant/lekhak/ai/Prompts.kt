package com.sushant.lekhak.ai

/** Builds the prompts sent to the AI. Pure Kotlin for unit tests. */
object Prompts {

    fun languageRule(language: String): String = when (language) {
        "Hindi" -> "Hindi, written in Devanagari script"
        "Hinglish" -> "Hinglish (Hindi written in English/Roman letters, mixed naturally with English words, the way Indians text on WhatsApp)"
        else -> language
    }

    fun toneRule(tone: String): String = when (tone) {
        "Grammar Polishing" -> "Grammar Polishing (keep the user's own words and meaning, just fix grammar, spelling and punctuation)"
        else -> tone
    }

    private fun historyBlock(history: String) =
        if (history.isBlank()) "(No earlier messages could be read.)" else history

    fun geminiSmartReply(r: AiRequest) = """
You are Lekhak, a WhatsApp writing assistant. Below is the recent chat. Lines starting with "You:" were sent by me, lines starting with "Them:" were sent by the other person.

CHAT:
${historyBlock(r.history)}

Write 3 different replies I could send next, answering the latest message from Them.
Tone: ${toneRule(r.tone)}
Language: ${languageRule(r.language)}
${if (r.customInstruction.isNotBlank()) "Extra instruction: ${r.customInstruction}\n" else ""}Rules: sound like a real person texting, keep each reply short (1-3 sentences), no hashtags, no quotation marks, no explanations.
Output exactly 3 options separated by a line containing only ###. Start each option with its header on its own line:
[Option 1 - Casual]
[Option 2 - Polished]
[Option 3 - Witty]
""".trimIndent()

    fun geminiRewrite(r: AiRequest) = """
You are Lekhak, a WhatsApp writing assistant. Rewrite my draft message before I send it.

CHAT (for context; "You:" = me, "Them:" = the other person):
${historyBlock(r.history)}

MY DRAFT:
${r.draft}

Tone: ${toneRule(r.tone)}
Language: ${languageRule(r.language)}
${if (r.customInstruction.isNotBlank()) "Extra instruction: ${r.customInstruction}\n" else ""}Rules: keep my meaning, sound natural for WhatsApp, no quotation marks, no explanations.
Output exactly 3 options separated by a line containing only ###. Start each option with its header on its own line:
[Option 1 - Polished (${r.tone})]
[Option 2 - Concise]
[Option 3 - Friendly]
""".trimIndent()

    fun openAiSystem(r: AiRequest) =
        "You are Lekhak, a WhatsApp writing assistant. Write in ${languageRule(r.language)}. " +
            "Tone: ${toneRule(r.tone)}. Sound human and natural, keep messages short, no quotation marks."

    fun openAiSmartReply(r: AiRequest) = buildString {
        append("Recent WhatsApp chat (\"You:\" = me, \"Them:\" = the other person):\n")
        append(historyBlock(r.history)).append("\n\n")
        append("Give 3 concise, natural, human-sounding replies I could send next, matching the conversation's tone.")
        if (r.customInstruction.isNotBlank()) append(" Extra instruction: ${r.customInstruction}.")
        append("\nFormat exactly as a numbered list:\n1. ...\n2. ...\n3. ...")
    }

    fun openAiRewrite(r: AiRequest) = buildString {
        append("Recent WhatsApp chat for context:\n").append(historyBlock(r.history)).append("\n\n")
        append("My draft: ").append(r.draft).append("\n\n")
        append("Rewrite my draft in 3 styles, keeping its meaning.")
        if (r.customInstruction.isNotBlank()) append(" Extra instruction: ${r.customInstruction}.")
        append("\nFormat exactly:\n1. Polite: ...\n2. Professional: ...\n3. Friendly: ...")
    }
}
