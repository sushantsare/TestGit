package com.sushant.grabbit

import com.sushant.grabbit.engine.Images
import com.sushant.grabbit.engine.InstaPostParser
import com.sushant.grabbit.engine.RedditParser
import com.sushant.grabbit.engine.TweetParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagesTest {
    @Test fun recognisesLinks() {
        assertTrue(Images.isDirectImage("https://i.redd.it/abc123.jpeg"))
        assertFalse(Images.isDirectImage("https://www.w3schools.com/html/mov_bbb.mp4"))
        assertEquals("440322224407314432", Images.tweetId("https://x.com/TheEllenShow/status/440322224407314432?s=20"))
        assertEquals("C9xAbc_12", Images.instaCode("https://www.instagram.com/p/C9xAbc_12/?img_index=2"))
        assertEquals("1abcd2", Images.redditId("https://www.reddit.com/r/pics/comments/1abcd2/some_title/"))
        assertEquals("jpg", Images.extOf("https://pbs.twimg.com/media/Bh7w.jpg?format=jpg&name=orig"))
        assertEquals("webp", Images.extOf("https://example.com/a/b.webp"))
    }

    @Test fun instagramShortcode() {
        assertEquals("0", Images.instaMediaId("A"))
        assertEquals("1", Images.instaMediaId("B"))
        assertEquals("64", Images.instaMediaId("BA"))
    }

    @Test fun tweetPhotosAndVideo() {
        val j = JSONObject("""{"mediaDetails":[
            {"type":"photo","media_url_https":"https://pbs.twimg.com/media/AAA.jpg"},
            {"type":"video","media_url_https":"https://pbs.twimg.com/thumb.jpg","video_info":{"duration_millis":12000,"variants":[
              {"content_type":"application/x-mpegURL","url":"https://v/x.m3u8"},
              {"content_type":"video/mp4","bitrate":832000,"url":"https://v/low.mp4"},
              {"content_type":"video/mp4","bitrate":2176000,"url":"https://v/high.mp4"}]}}]}""")
        val items = TweetParser.items(j)
        assertEquals(2, items.size)
        assertEquals("https://pbs.twimg.com/media/AAA?format=jpg&name=orig", items[0].url)
        assertTrue(items[1].video)
        assertEquals("https://v/high.mp4", items[1].url)
        assertEquals(12, items[1].duration)
    }

    @Test fun instagramCarousel() {
        val item = JSONObject("""{"carousel_media":[
            {"image_versions2":{"candidates":[{"url":"small","width":320,"height":320},{"url":"big","width":1080,"height":1080}]}},
            {"image_versions2":{"candidates":[{"url":"cover","width":1080,"height":1920}]},"video_versions":[{"url":"vid","width":720,"height":1280}],"video_duration":9.5}]}""")
        val items = InstaPostParser.items(item)
        assertEquals(listOf("big", "vid"), items.map { it.url })
        assertTrue(items[1].video)
    }

    @Test fun redditGallery() {
        val post = JSONObject("""{"is_gallery":true,"gallery_data":{"items":[{"media_id":"b"},{"media_id":"a"}]},
            "media_metadata":{"a":{"s":{"u":"https://preview.redd.it/a.jpg?x=1&amp;y=2"}},"b":{"s":{"u":"https://preview.redd.it/b.jpg"}}}}""")
        assertEquals(listOf("https://preview.redd.it/b.jpg", "https://preview.redd.it/a.jpg?x=1&y=2"), RedditParser.items(post).map { it.url })
        val single = JSONObject("""{"url_overridden_by_dest":"https://i.redd.it/xyz.png"}""")
        assertEquals(1, RedditParser.items(single).size)
        assertTrue(RedditParser.items(JSONObject("""{"url":"https://v.redd.it/abc"}""")).isEmpty())
    }
}
