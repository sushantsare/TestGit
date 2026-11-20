package com.sushant.grabbit

import com.sushant.grabbit.engine.InstaParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstaStoryTest {
    private val feed = JSONObject("""
        {"reels":{"123":{"id":"123","items":[
          {"pk":"111","id":"111_123","image_versions2":{"candidates":[{"url":"https://cdn/img1.jpg"}]}},
          {"pk":"222","id":"222_123","video_duration":9.6,
           "user":{"username":"amy"},
           "image_versions2":{"candidates":[{"url":"https://cdn/thumb2.jpg"}]},
           "video_versions":[{"width":480,"height":854,"url":"https://cdn/low.mp4"},{"width":720,"height":1280,"url":"https://cdn/high.mp4"}]}
        ]}},"status":"ok"}
    """.trimIndent())

    @Test
    fun picksSharedStoryAndBestVideo(): Unit {
        val s = InstaParser.pick(feed, "123", "222")!!
        assertEquals("https://cdn/high.mp4", s.video)
        assertEquals("https://cdn/thumb2.jpg", s.thumb)
        assertEquals("amy", s.user)
        assertEquals(9L, s.duration)
        assertEquals(2, InstaParser.count(feed, "123"))
    }

    @Test
    fun photoStoryHasNoVideo(): Unit {
        val s = InstaParser.pick(feed, "123", "111")!!
        assertEquals("", s.video)
    }

    @Test
    fun missingStoryOrReel(): Unit {
        assertNull(InstaParser.pick(feed, "123", "999"))
        assertNull(InstaParser.pick(feed, "456", null))
    }

    @Test
    fun readsReelsMediaListFormat(): Unit {
        val alt = JSONObject("""{"reels_media":[{"id":"highlight:9","items":[{"pk":"5","video_versions":[{"width":1,"height":1,"url":"https://cdn/h.mp4"}]}]}]}""")
        assertEquals("https://cdn/h.mp4", InstaParser.pick(alt, "highlight:9", null)!!.video)
    }

    @Test
    fun readsMediaInfoResponse(): Unit {
        val j = JSONObject("""{"items":[{"pk":"4034","user":{"username":"amy"},"video_versions":[{"width":720,"height":1280,"url":"https://cdn/s.mp4"}]}],"status":"ok"}""")
        val s = InstaParser.fromMediaInfo(j)!!
        assertEquals("https://cdn/s.mp4", s.video)
        assertEquals("amy", s.user)
        assertNull(InstaParser.fromMediaInfo(JSONObject("""{"items":[]}""")))
    }

    @Test
    fun findsAccountIdInStoryPage(): Unit {
        val page = """<script>{"user":{"username":"amyleona367","profile_pic_url":"x","id":"58123456789"}}</script>"""
        assertEquals("58123456789", InstaParser.userIdFromPage(page, "amyleona367"))
        assertEquals("777", InstaParser.userIdFromPage("""{"pk":"777","full_name":"A","username":"amy"}""", "amy"))
        assertEquals("555", InstaParser.userIdFromPage("""{"profile_id":"555"}""", "someone"))
        assertNull(InstaParser.userIdFromPage("<html>nothing</html>", "amy"))
    }
}
