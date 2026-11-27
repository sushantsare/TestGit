package com.sushant.grabbit.download

/**
 * Per-download choices made on the result card / share sheet. Stored with the job (input data + a tag)
 * so Retry keeps them.
 */
data class DlOpts(
    /** Subtitle language code ("" = none), e.g. "en", "hi". */
    val subLang: String = "",
    /** True when the chosen subtitles are auto-generated / auto-translated. */
    val subAuto: Boolean = false,
    /** "embed" (inside the video), "file" (separate .srt), "both". */
    val subMode: String = "embed",
    /** Trim range in seconds; -1 = not trimmed. */
    val trimStart: Long = -1,
    val trimEnd: Long = -1,
    /** One file per chapter. */
    val splitChapters: Boolean = false,
    /** Audio downloads: "mp3", "m4a", "opus" or "best" (original, no conversion). */
    val audioFormat: String = "mp3",
    /** File name chosen by the user ("" = use the title). */
    val name: String = "",
    /** Folder to save into (tree uri); "" = Default. */
    val folder: String = "",
) {
    val trimmed get() = trimStart >= 0 && trimEnd > trimStart
    val keepSubFiles get() = subLang.isNotEmpty() && subMode != "embed"

    fun encode(): String = listOf(
        "sl=$subLang", "sa=${if (subAuto) 1 else 0}", "sm=$subMode", "ts=$trimStart", "te=$trimEnd",
        "ch=${if (splitChapters) 1 else 0}", "af=$audioFormat",
        "nm=${java.net.URLEncoder.encode(name, "UTF-8")}",
        "fd=${java.net.URLEncoder.encode(folder, "UTF-8")}",
    ).joinToString(";")

    companion object {
        fun decode(s: String?): DlOpts {
            if (s.isNullOrBlank()) return DlOpts()
            val m = s.split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
            return DlOpts(
                subLang = m["sl"].orEmpty(),
                subAuto = m["sa"] == "1",
                subMode = m["sm"]?.takeIf { it in setOf("embed", "file", "both") } ?: "embed",
                trimStart = m["ts"]?.toLongOrNull() ?: -1,
                trimEnd = m["te"]?.toLongOrNull() ?: -1,
                splitChapters = m["ch"] == "1",
                audioFormat = m["af"]?.takeIf { it in AUDIO_FORMATS } ?: "mp3",
                name = m["nm"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }.orEmpty(),
                folder = m["fd"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }.orEmpty(),
            )
        }

        val AUDIO_FORMATS = listOf("mp3", "m4a", "opus", "best")
    }
}

/** Settings that apply to every download (read from Prefs by the worker). */
data class GlobalOpts(
    /** 0 = most compatible (H.264/AAC in MP4), 1 = highest quality (VP9/AV1 allowed). */
    val videoFormat: Int = 0,
    val faster: Boolean = true,
    val embedThumb: Boolean = true,
    /** SponsorBlock categories to remove from YouTube videos (empty = off). */
    val sponsorCats: Set<String> = emptySet(),
    /** Use aria2c (many connections per file) for whole-file downloads. */
    val multiConn: Boolean = false,
)

/** Builds the yt-dlp options for one download. Pure, so it is unit-tested. */
object DownloadArgs {
    const val CHAPTER_PREFIX = "ch_"

    fun build(
        quality: String, playlist: Boolean, outDir: String, opts: DlOpts, g: GlobalOpts, isYouTube: Boolean,
    ): List<Pair<String, String?>> {
        val a = mutableListOf<Pair<String, String?>>()
        fun o(k: String, v: String? = null) { a += k to v }
        o("--no-mtime"); o("--no-warnings")
        o(if (playlist) "--yes-playlist" else "--no-playlist")
        if (playlist) o("--ignore-errors")
        val template = if (playlist) "%(playlist_index)03d - %(title).70B [%(id).30B].%(ext)s" else "%(title).80B [%(id).40B].%(ext)s"
        o("-o", "$outDir/$template")
        if (g.faster) o("--concurrent-fragments", "8")
        if (g.faster && g.multiConn) {
            // One file over many connections (YouTube, X, direct links); streams in small pieces keep the native downloader
            o("--downloader", "aria2c")
            o("--downloader", "dash,m3u8:native")
            o("--downloader-args", "aria2c:-x 16 -s 16 -k 1M --file-allocation=none --summary-interval=1")
        }

        val audio = quality == "audio" || (quality.length > 1 && quality[0] == 'a' && quality.drop(1).all { it.isDigit() })
        if (audio) {
            val kbps = if (quality.startsWith("a")) quality.drop(1).toIntOrNull() else null
            o("-f", if (kbps != null) "ba[abr>=${kbps - 8}]/ba/b" else "ba/b")
            if (kbps != null) o("-S", "+abr")
            o("-x")
            o("--audio-format", opts.audioFormat)
            if (opts.audioFormat != "best") o("--audio-quality", if (kbps != null) "${kbps}K" else "0")
            o("--embed-metadata")
            if (g.embedThumb && opts.audioFormat in setOf("mp3", "m4a")) {
                o("--embed-thumbnail"); o("--convert-thumbnails", "jpg")
            }
        } else {
            val h = quality.removePrefix("h").toIntOrNull()
            o("-f", "bv*+ba/b")
            if (g.videoFormat == 0) {
                // Most compatible: H.264 + AAC plays in every gallery and player
                o("-S", if (h != null) "res:$h,vcodec:h264,acodec:aac" else "res,vcodec:h264,acodec:aac")
                o("--merge-output-format", "mp4")
            } else {
                // Highest quality: best codec at the resolution (VP9/AV1), MP4 when possible, else MKV
                o("-S", if (h != null) "res:$h" else "res")
                o("--merge-output-format", "mp4/mkv")
            }
            o("--embed-metadata")
            if (opts.subLang.isNotEmpty()) {
                o(if (opts.subAuto) "--write-auto-subs" else "--write-subs")
                o("--sub-langs", opts.subLang)
                o("--convert-subs", "srt")
                if (opts.subMode != "file") o("--embed-subs")
            }
        }
        // Trim is done after the download by Trimmer (yt-dlp's --download-sections fails on phones)
        if (!playlist && opts.splitChapters) {
            o("--split-chapters")
            o("-o", "chapter:$outDir/$CHAPTER_PREFIX%(section_number)03d - %(section_title).90B.%(ext)s")
        }
        if (isYouTube && g.sponsorCats.isNotEmpty()) o("--sponsorblock-remove", g.sponsorCats.sorted().joinToString(","))
        return a
    }
}
