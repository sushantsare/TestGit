package com.sushant.grabbit.engine

/** Turns raw yt-dlp errors into messages a normal person can act on. */
object Errors {
    /** yt-dlp stops the whole download when only the subtitle file fails (often HTTP 429 from YouTube). */
    fun isSubtitleError(raw: String?): Boolean =
        raw != null && Regex("(?i)unable to download (video )?subtitles|subtitles? .*http error|timedtext|embedsubtitle").containsMatchIn(raw)

    fun friendly(raw: String?): String {
        val m = raw?.trim().orEmpty()
        if (m.isEmpty()) return "Something went wrong. Please try again."
        val l = m.lowercase()
        return when {
            "file name too long" in l || "errno 36" in l ->
                "The file name was too long. Tap the pen icon to give it a shorter name, then try again."
            isSubtitleError(m) ->
                "The site refused the subtitles right now. Try again in a few minutes, or download without subtitles."
            "unable to resolve host" in l || "network is unreachable" in l || "failed to resolve" in l ||
                "connection refused" in l || "timed out" in l || "no address associated" in l ->
                "No internet connection (or it is too slow). Check your network and try again."
            "unable to recognize tab page" in l || "the playlist does not exist" in l || "playlist does not exist" in l ->
                "This playlist couldn't be opened. It may be private, deleted, or the link was cut short. Open it in YouTube and share it again. Still failing? Tap Update now in Settings → Download engine."
            "confirm you are on the latest version" in l || "please report this issue" in l ->
                "YouTube changed something. Tap Update now in Settings → Download engine, then try again."
            "unsupported url" in l ->
                "This link isn't supported. Make sure it's a direct link to a video or post."
            "not a bot" in l ->
                "YouTube wants to check you're not a bot. Log in to YouTube in Settings → Accounts, or switch between Wi-Fi and mobile data and try again."
            "impersonat" in l && "web client only works when logged-in" in l || "web client only works when logged-in" in l ->
                "This site only allows downloads when you're logged in. Open Settings → Accounts, log in, then try again."
            "sign in to confirm your age" in l || "age-restricted" in l || "confirm your age" in l ->
                "This video is age-restricted. Log in to the site in Settings → Accounts, then try again."
            "sign in to confirm" in l || "use --cookies" in l || "login required" in l ||
                "requires login" in l || "log in" in l && "required" in l || "authentication" in l ||
                "private video" in l || "this content isn't available" in l ->
                "This content needs a login. Log in to the site in Settings → Accounts. Already logged in? Log out and log in again (logins expire), then try again."
            "429" in l || "rate-limit" in l || "rate limit" in l || "too many requests" in l ->
                "The site is limiting requests right now. Wait a few minutes, or log in to the site in Settings → Accounts."
            "requested format is not available" in l || "format is not available" in l ->
                "That quality isn't available for this video. Try another format."
            "no video formats found" in l || "no video could be found" in l || "there is no video" in l ->
                "No video was found at this link. It may be a photo post, deleted, or private."
            "not available in your country" in l || "geo restrict" in l || "geo-restrict" in l ->
                "This video is blocked in your country."
            "video unavailable" in l || "has been removed" in l || "does not exist" in l || "404" in l ->
                "This video is unavailable. It may have been deleted or made private."
            "403" in l || "forbidden" in l ->
                "The site blocked the download. Update the engine in Settings, then try again."
            "no space left" in l || "enospc" in l ->
                "Your phone storage is full. Free up some space and try again."
            else -> m.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }
                ?.removePrefix("ERROR:")?.trim()?.take(300)
                ?: m.lineSequence().lastOrNull { it.isNotBlank() }?.take(300)
                ?: m.take(300)
        }
    }
}
