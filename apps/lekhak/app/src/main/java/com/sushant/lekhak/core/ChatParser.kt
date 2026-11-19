package com.sushant.lekhak.core

/** One piece of on-screen text found in the WhatsApp chat, with its horizontal position. */
data class RawText(
    val text: String,
    val left: Int,
    val right: Int,
    /** True when the text came from WhatsApp's own message-text view (cleanest source). */
    val isMessageView: Boolean = false,
    /** Top edge on screen, used to keep messages in reading order. */
    val top: Int = 0,
)

/**
 * Turns raw on-screen text from a WhatsApp chat into a short, labelled conversation:
 *   Them: kal aa rahe ho?
 *   You: haan, 5 baje
 * Pure Kotlin so it can be unit tested.
 */
object ChatParser {
    const val MAX_MESSAGES = 10
    const val MAX_LENGTH = 250

    private val exactNoise = setOf(
        "whatsapp", "chats", "updates", "calls", "communities", "status",
        "type a message", "message", "online", "typing...", "typing…", "today", "yesterday",
        "voice call", "video call", "camera", "attach", "emoji", "send", "record voice message",
        "photo", "video", "sticker", "gif", "deleted message", "this message was deleted",
        "you deleted this message",
    )
    private val containsNoise = listOf(
        "end-to-end encrypted", "end to end encrypted", "messages and calls are",
        "lekhak", "drafting", "last seen", "tap for more info", "unread message",
    )
    private val timeRegex = Regex("^[0-9]{1,2}:[0-9]{2}\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$", RegexOption.IGNORE_CASE)
    private val months = "(january|february|march|april|may|june|july|august|september|october|november|december)"
    private val dateRegex = Regex("^([0-9]{1,2}\\s+$months(\\s+[0-9]{4})?|$months\\s+[0-9]{1,2}(,\\s*[0-9]{4})?)$", RegexOption.IGNORE_CASE)

    fun isNoise(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_LENGTH) return true
        val low = s.lowercase()
        if (low in exactNoise) return true
        if (containsNoise.any { low.contains(it) }) return true
        if (timeRegex.matches(low)) return true
        if (dateRegex.matches(s)) return true
        if (s.all { !it.isLetterOrDigit() }) return true // only punctuation / ticks
        return false
    }

    /**
     * Labels a message as yours or theirs. Explicit "You:" text wins; otherwise the bubble's
     * position decides: WhatsApp draws your messages on the right and theirs on the left.
     */
    fun label(item: RawText, screenWidth: Int): String {
        var text = item.text.trim()
        for (prefix in listOf("Selected: You:", "You:")) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                return "You: " + text.substring(prefix.length).trim()
            }
        }
        val center = (item.left + item.right) / 2
        val mine = screenWidth > 0 && center > screenWidth / 2
        text = text.replace(Regex("\\s+"), " ")
        return (if (mine) "You: " else "Them: ") + text
    }

    fun build(items: List<RawText>, screenWidth: Int, max: Int = MAX_MESSAGES): String {
        val source = if (items.any { it.isMessageView }) items.filter { it.isMessageView } else items
        return source
            .sortedBy { it.top }
            .filterNot { isNoise(it.text) }
            .map { label(it, screenWidth) }
            .distinct()
            .takeLast(max)
            .joinToString("\n")
    }
}
