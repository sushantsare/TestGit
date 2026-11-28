package com.sushant.grabbit

import com.sushant.grabbit.engine.YtPlaylist
import com.sushant.grabbit.engine.YtPlaylistParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class YtPlaylistTest {
    private val web = JSONObject("""
    {"header":{"playlistHeaderRenderer":{"title":{"simpleText":"Road songs"},"ownerText":{"runs":[{"text":"Sushant"}]}}},
     "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"itemSectionRenderer":{"contents":[{"playlistVideoListRenderer":{"contents":[
        {"playlistVideoRenderer":{"videoId":"aaaaaaaaaaa","title":{"runs":[{"text":"Song A"}]},"lengthSeconds":"215",
          "thumbnail":{"thumbnails":[{"url":"s.jpg","width":120},{"url":"m.jpg","width":246}]}}},
        {"playlistVideoRenderer":{"videoId":"bbbbbbbbbbb","title":{"runs":[{"text":"[Private video]"}]},"isPlayable":false}},
        {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOKEN2"}}}}
      ]}}]}}]}}}}]}},
     "secondary":{"videoRenderer":{"videoId":"zzzzzzzzzzz","title":{"simpleText":"Suggested"}}}}
    """)

    @Test fun readsWebPlaylist() {
        val v = YtPlaylistParser.videos(web)
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), v.map { it.id }) // suggestions ignored
        val e = YtPlaylistParser.toEntries(v)
        assertEquals("Song A", e[0].title); assertEquals(215L, e[0].duration); assertEquals("m.jpg", e[0].thumb)
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa", e[0].url)
        assertFalse(e[1].available)
        assertEquals("TOKEN2", YtPlaylistParser.continuation(web))
        assertEquals("Road songs" to "Sushant", YtPlaylistParser.header(web))
    }

    @Test fun readsMusicPlaylist() {
        val j = JSONObject("""
        {"header":{"musicResponsiveHeaderRenderer":{"title":{"runs":[{"text":"My mix"}]},"straplineTextOne":{"runs":[{"text":"Sushant"}]}}},
         "contents":{"x":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"ccccccccccc"},
           "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Track C"}]}}}],
           "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:05"}]}}}]}}]}}
        """)
        val v = YtPlaylistParser.videos(j)
        assertEquals("ccccccccccc", v.single().id); assertEquals("Track C", v.single().title); assertEquals(185L, v.single().duration)
        assertNull(YtPlaylistParser.continuation(j))
        assertEquals("My mix" to "Sushant", YtPlaylistParser.header(j))
    }

    @Test fun playlistIdFromLinks() {
        assertEquals("PLAdFiDLf78fc", YtPlaylist.idFrom("https://youtube.com/playlist?list=PLAdFiDLf78fc&si=x"))
        assertEquals("PLAdFiDLf78fc", YtPlaylist.idFrom("https://music.youtube.com/playlist?list=PLAdFiDLf78fc"))
        assertNull(YtPlaylist.idFrom("https://www.instagram.com/p/abc/?list=PLx"))
        assertNull(YtPlaylist.idFrom("https://www.youtube.com/watch?v=abc"))
    }
}
