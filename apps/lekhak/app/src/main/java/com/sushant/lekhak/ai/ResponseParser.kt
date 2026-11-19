package com.sushant.lekhak.ai

/** Parses AI output into suggestion cards. Pure Kotlin for unit tests. */
object ResponseParser {
    private val headerRegex = Regex("^\\s*\\**\\s*\\[\\s*Option\\s*(\\d+)\\s*[-–—:]\\s*(.+?)\\s*]\\s*\\**\\s*", RegexOption.IGNORE_CASE)
    private val numberedRegex = Regex("^\\s*\\**(\\d)[.)]\\**\\s*(.*)$")
    private val styleRegex = Regex("^\\**\\s*(Polite|Professional|Friendly|Casual|Concise|Witty)\\s*\\**\\s*:\\s*\\**\\s*(.*)$", RegexOption.IGNORE_CASE)

    fun clean(text: String): String {
        var t = text.trim()
        t = t.removePrefix("**").removeSuffix("**").trim()
        val quotes = listOf("\"" to "\"", "“" to "”", "'" to "'")
        for ((open, close) in quotes) {
            if (t.length >= 2 && t.startsWith(open) && t.endsWith(close)) t = t.substring(1, t.length - 1).trim()
        }
        return t
    }

    /** Gemini format: blocks separated by ###, each starting with [Option N - Label]. */
    fun parseGemini(raw: String, defaultLabels: List<String>): List<Suggestion> {
        val blocks = raw.split(Regex("(?m)^\\s*#{3,}\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Suggestion>()
        blocks.forEachIndexed { i, block ->
            val m = headerRegex.find(block)
            val (label, body) = if (m != null && m.range.first == 0) {
                m.groupValues[2].trim() to block.substring(m.range.last + 1)
            } else {
                (defaultLabels.getOrNull(i) ?: "Option ${i + 1}") to block
            }
            val text = clean(body)
            if (text.isNotEmpty()) out += Suggestion(label, text)
        }
        if (out.isEmpty()) {
            val whole = clean(raw.replace(headerRegex, ""))
            if (whole.isEmpty()) return emptyList()
            out += Suggestion(defaultLabels.firstOrNull() ?: "Suggestion", whole)
        }
        if (out.size == 1) {
            val first = firstSentence(out[0].text)
            if (first != out[0].text) out += Suggestion("Shorter", first)
        }
        return out.take(3)
    }

    fun firstSentence(text: String): String {
        val idx = text.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '।' }
        return if (idx in 1 until text.length - 1) text.substring(0, idx + 1).trim() else text
    }

    /** OpenAI format: "1. text" or "1. Polite: text"; lines without a number continue the previous item. */
    fun parseNumbered(raw: String, defaultLabels: List<String>): List<Suggestion> {
        val items = mutableListOf<Pair<String?, StringBuilder>>()
        raw.lines().forEach { line ->
            val m = numberedRegex.find(line)
            if (m != null) {
                val rest = m.groupValues[2]
                val sm = styleRegex.find(rest)
                if (sm != null) items += sm.groupValues[1].replaceFirstChar { it.uppercase() } to StringBuilder(sm.groupValues[2])
                else items += null to StringBuilder(rest)
            } else if (items.isNotEmpty() && line.isNotBlank()) {
                items.last().second.append('\n').append(line.trim())
            }
        }
        val out = items.mapIndexedNotNull { i, (label, sb) ->
            val text = clean(sb.toString())
            if (text.isEmpty()) null else Suggestion(label ?: defaultLabels.getOrNull(i) ?: "Option ${i + 1}", text)
        }
        if (out.isEmpty()) {
            val whole = clean(raw)
            return if (whole.isEmpty()) emptyList() else listOf(Suggestion(defaultLabels.firstOrNull() ?: "Suggestion", whole))
        }
        return out.take(3)
    }
}
