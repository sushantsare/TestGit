package com.sushant.grabbit

import com.sushant.grabbit.data.CookieText
import com.sushant.grabbit.engine.Threads
import com.sushant.grabbit.engine.ThreadsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsAndCookiesTest {
    private val page = """
        <html><head><meta property="og:image" content="https://cdn.example/og.jpg?a=1&amp;b=2"></head><body>
        <script type="application/json">{"require":[{"post":{"user":{"username":"nasa"},
        "caption":{"pk":"1","text":"Launch day 🚀\nMore below"},
        "image_versions2":{"candidates":[{"height":1920,"url":"https:\/\/scontent.cdninstagram.com\/v\/thumb.jpg?x=1&y=2","width":1080}]},
        "video_versions":[{"type":101,"url":"https:\/\/scontent.cdninstagram.com\/o1\/v\/main.mp4?efg=abc&oh=1"},{"type":102,"url":"https:\/\/scontent.cdninstagram.com\/low.mp4"}]
        }},{"reply":{"video_versions":[{"type":101,"url":"https:\/\/scontent.cdninstagram.com\/reply.mp4"}]}}]}</script>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesMainPostVideoThumbAndCaption(): Unit {
        val p = ThreadsParser.parse(page, "nasa")
        assertEquals(listOf("https://scontent.cdninstagram.com/o1/v/main.mp4?efg=abc&oh=1"), p.videos)
        assertEquals("https://scontent.cdninstagram.com/v/thumb.jpg?x=1&y=2", p.thumb)
        assertTrue(p.caption.startsWith("Launch day"))
        assertEquals("Launch day 🚀", ThreadsParser.title(p))
    }

    @Test
    fun fallsBackToOgVideoTags(): Unit {
        val html = """<meta property="og:video" content="https://cdn.example/v.mp4?a=1&amp;b=2" />
            <meta property="og:description" content="Hello &quot;world&quot;" />"""
        val p = ThreadsParser.parse(html, "")
        assertEquals(listOf("https://cdn.example/v.mp4?a=1&b=2"), p.videos)
        assertEquals("Hello \"world\"", p.caption)
    }

    @Test
    fun noVideoMeansEmpty(): Unit {
        val p = ThreadsParser.parse("<html>just text</html>", "someone")
        assertTrue(p.videos.isEmpty())
        assertEquals("Threads video by @someone", ThreadsParser.title(p))
    }

    @Test
    fun cookieHeaderMatchesDomain(): Unit {
        val text = "# Netscape HTTP Cookie File\n" +
            ".threads.com\tTRUE\t/\tTRUE\t0\tsessionid\tabc\n" +
            ".threads.com\tTRUE\t/\tTRUE\t0\tcsrftoken\txyz\n" +
            ".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tother\n"
        assertEquals("sessionid=abc; csrftoken=xyz", CookieText.header(text, "www.threads.com"))
        assertEquals("sessionid=other", CookieText.header(text, "instagram.com"))
        assertEquals("", CookieText.header(text, "evil-threads.com"))
    }
}
