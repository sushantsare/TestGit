package com.sushant.grabbit.data

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Logins are captured from the in-app WebView and written as a Netscape cookies.txt
 * that yt-dlp reads with --cookies. Everything stays on the phone.
 */
object Cookies {
    private fun file(ctx: Context) = File(ctx.filesDir, "cookies.txt")
    private fun key(site: LoginSite) = "ck_${site.key}"

    fun isLoggedIn(site: LoginSite): Boolean = Prefs.p.getString(key(site), null) != null

    /** Saves the WebView's cookies for this site. Returns how many cookies were found. */
    fun save(ctx: Context, site: LoginSite): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val expiry = System.currentTimeMillis() / 1000 + 60L * 60 * 24 * 365
        val sb = StringBuilder()
        var count = 0
        for (d in site.domains) {
            val seen = HashSet<String>()
            for (u in listOf("https://www.$d", "https://$d", "https://m.$d")) {
                val raw = cm.getCookie(u) ?: continue
                raw.split(";").forEach { part ->
                    val kv = part.trim()
                    val i = kv.indexOf('=')
                    if (i > 0) {
                        val name = kv.substring(0, i)
                        if (seen.add(name)) {
                            sb.append(".$d\tTRUE\t/\tTRUE\t$expiry\t$name\t${kv.substring(i + 1)}\n")
                            count++
                        }
                    }
                }
            }
        }
        if (count > 0) {
            Prefs.p.edit().putString(key(site), sb.toString()).apply()
            rebuild(ctx)
        }
        return count
    }

    fun logout(ctx: Context, site: LoginSite) {
        Prefs.p.edit().remove(key(site)).apply()
        rebuild(ctx)
        val cm = CookieManager.getInstance()
        for (d in site.domains) {
            for (u in listOf("https://www.$d", "https://$d", "https://m.$d")) {
                cm.getCookie(u)?.split(";")?.forEach { part ->
                    val name = part.trim().substringBefore('=')
                    if (name.isNotEmpty()) {
                        cm.setCookie(u, "$name=; Max-Age=0; Path=/")
                        cm.setCookie(u, "$name=; Max-Age=0; Path=/; Domain=.$d")
                    }
                }
            }
        }
        cm.flush()
    }

    private fun rebuild(ctx: Context) {
        val all = Sites.logins.mapNotNull { Prefs.p.getString(key(it), null) }.joinToString("")
        val f = file(ctx)
        if (all.isBlank()) f.delete() else f.writeText("# Netscape HTTP Cookie File\n$all")
    }

    fun apply(ctx: Context, req: YoutubeDLRequest) {
        val f = file(ctx)
        if (f.exists() && f.length() > 0) req.addOption("--cookies", f.absolutePath)
    }
}
