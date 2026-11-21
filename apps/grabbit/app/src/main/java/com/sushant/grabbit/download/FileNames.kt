package com.sushant.grabbit.download

/** Safe, readable file names for saved downloads (Android allows at most 255 bytes per name). */
object FileNames {
    /** Max characters in a name we create (80 × 3 bytes for Hindi + " (12).mp4" stays under 255 bytes). */
    const val MAX = 80
    const val BAD_CHARS = "/ \\ : * ? \" < > |"
    private val BAD = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

    fun hasBad(raw: String) = BAD.containsMatchIn(raw)

    /** Removes characters Android can't use, tidies spaces and limits the length. */
    fun clean(raw: String): String {
        val s = raw.replace(BAD, " ").replace(Regex("\\s+"), " ").trim().trim('.', ' ')
        return cut(s).trimEnd('.', ' ')
    }

    fun cut(s: String, max: Int = MAX): String =
        if (s.codePointCount(0, s.length) <= max) s else s.substring(0, s.offsetByCodePoints(0, max))

    /** "name.ext", or "name (2).ext", "name (3).ext"… when taken. */
    fun unique(base: String, ext: String, exists: (String) -> Boolean): String {
        val dot = if (ext.isBlank()) "" else ".$ext"
        var name = "$base$dot"
        var i = 2
        while (exists(name)) name = "$base (${i++})$dot"
        return name
    }

    /** "My trip – 01" for playlist / chapter files named by the user. */
    fun numbered(base: String, index: Int, total: Int): String =
        "${cut(base, MAX - 6)} – ${index.toString().padStart(if (total >= 100) 3 else 2, '0')}"
}
