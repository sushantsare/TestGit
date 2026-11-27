package com.sushant.grabbit.download

/** What yt-dlp is doing, read from its output lines, so the app never just sits at 99%. */
object Stages {
    const val DOWNLOAD = "dl"
    const val WORK = "work"
    const val SAVE = "save"

    /** Status text for a processing line, or null if the line isn't one. */
    fun statusFor(line: String): String? = when {
        line.contains("[Merger]") -> "Merging video and audio…"
        line.contains("[ExtractAudio]") -> "Converting audio…"
        line.contains("[VideoConvertor]") || line.contains("[VideoRemuxer]") -> "Converting video…"
        line.contains("[SponsorBlock]") || line.contains("[ModifyChapters]") -> "Cutting sponsor segments…"
        line.contains("[EmbedSubtitle]") -> "Adding subtitles…"
        line.contains("[SplitChapters]") -> "Splitting chapters…"
        line.contains("[FixupM3u8]") || line.contains("[Fixup") -> "Fixing the video file…"
        line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") || line.contains("[ThumbnailsConvertor]") -> "Adding cover and details…"
        else -> null
    }

    private val ARIA = Regex("""\[#\w+\s+([\d.]+\w+)/([\d.]+\w+)\((\d+)%\).*?DL:([\d.]+\w+)(?:\s+ETA:(\w+))?""")

    /** aria2c's own progress line: percent, speed ("2.4MiB"), done/total text. */
    data class Aria(val pct: Float, val speed: String, val done: String, val total: String)

    fun aria(line: String): Aria? = ARIA.find(line)?.let {
        Aria(it.groupValues[3].toFloat(), it.groupValues[4] + "/s", it.groupValues[1], it.groupValues[2])
    }
}
