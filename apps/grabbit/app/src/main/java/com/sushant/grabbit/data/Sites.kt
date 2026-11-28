package com.sushant.grabbit.data

import android.net.Uri

data class LoginSite(
    val key: String,
    val name: String,
    val loginUrl: String,
    val domains: List<String>,
    /** Cookie that only exists once the user is really logged in (used to auto-detect a finished login). */
    val session: String,
)

object Sites {
    val logins = listOf(
        LoginSite("instagram", "Instagram", "https://www.instagram.com/accounts/login/", listOf("instagram.com"), "sessionid"),
        LoginSite("threads", "Threads", "https://www.threads.com/login", listOf("threads.com", "threads.net"), "sessionid"),
        LoginSite("facebook", "Facebook", "https://m.facebook.com/login/", listOf("facebook.com"), "c_user"),
        LoginSite("x", "X (Twitter)", "https://x.com/i/flow/login", listOf("x.com", "twitter.com"), "auth_token"),
        LoginSite("youtube", "YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com/", listOf("youtube.com", "google.com"), "LOGIN_INFO"),
        LoginSite("reddit", "Reddit", "https://www.reddit.com/login/", listOf("reddit.com"), "reddit_session"),
        LoginSite("tiktok", "TikTok", "https://www.tiktok.com/login", listOf("tiktok.com"), "sessionid"),
        LoginSite("pinterest", "Pinterest", "https://www.pinterest.com/login/", listOf("pinterest.com"), "_pinterest_sess"),
        LoginSite("vimeo", "Vimeo", "https://vimeo.com/log_in", listOf("vimeo.com"), "vimeo"),
    )

    private val URL_RE = Regex("""https?://[^\s<>"']+""")

    /** Pulls the first link out of shared text like "Watch this! https://youtu.be/xyz" */
    fun extractUrl(text: String): String? =
        URL_RE.find(text)?.value?.trimEnd(')', ']', '.', ',', '!', '?', ';')?.let(::cleanUrl)

    /** Tracking parts that change nothing about the video. */
    private val TRACKING = setOf("si", "drli", "pp", "feature", "utm_source", "utm_medium", "utm_campaign", "igsh", "igshid")

    /**
     * Tidies a link before fetching: YouTube Music playlists become normal YouTube playlist links
     * (the engine reads those more reliably) and share-tracking parts are dropped.
     * Pure string work, so it is unit-tested.
     */
    fun cleanUrl(url: String): String {
        val hashIdx = url.indexOf('#')
        val noHash = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val q = noHash.indexOf('?')
        val base = if (q >= 0) noHash.substring(0, q) else noHash
        val params = if (q >= 0) noHash.substring(q + 1).split('&').filter { it.isNotBlank() } else emptyList()
        val kept = params.filter { it.substringBefore('=').lowercase() !in TRACKING }
        val host = base.substringAfter("://").substringBefore('/').lowercase()
        val path = "/" + base.substringAfter("://").substringAfter('/', "")
        var newBase = base
        if (host == "music.youtube.com" && path.startsWith("/playlist")) newBase = "https://www.youtube.com/playlist"
        return if (kept.isEmpty()) newBase else newBase + "?" + kept.joinToString("&")
    }

    fun siteName(url: String): String {
        val host = (runCatching { Uri.parse(url).host }.getOrNull() ?: return "Web")
            .lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        fun h(vararg d: String) = d.any { host == it || host.endsWith(".$it") }
        return when {
            h("youtube.com", "youtu.be") -> "YouTube"
            h("instagram.com") -> "Instagram"
            h("facebook.com", "fb.watch", "fb.com") -> "Facebook"
            h("x.com", "twitter.com") -> "X"
            h("reddit.com", "redd.it") -> "Reddit"
            h("tiktok.com") -> "TikTok"
            h("pinterest.com", "pin.it") -> "Pinterest"
            h("vimeo.com") -> "Vimeo"
            h("dailymotion.com", "dai.ly") -> "Dailymotion"
            h("sharechat.com") -> "ShareChat"
            h("mojapp.in") -> "Moj"
            h("linkedin.com") -> "LinkedIn"
            h("twitch.tv") -> "Twitch"
            h("soundcloud.com") -> "SoundCloud"
            h("threads.net") -> "Threads"
            h("snapchat.com") -> "Snapchat"
            else -> host.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
