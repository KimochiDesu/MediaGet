package com.erdbot.mediagrab.extract.tiktok

import com.erdbot.mediagrab.extract.Fixtures
import com.erdbot.mediagrab.extract.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokParserTest {

    /** Captured live from tiktok.com/embed/v2/{id} without login on 2026-10-21 (trimmed to the state script). */
    @Test
    fun embedPlayerYieldsVideo() {
        val id = "6718335390845095173"
        val state = TikTokParser.embedState(id, Fixtures.text("tiktok_embed_video.html"))!!
        assertFalse(TikTokParser.embedIsError(state))
        val post = TikTokParser.parseEmbed(id, state)!!
        assertEquals("scout2015", post.author)
        assertTrue(post.caption.startsWith("Scramble up ur name"))
        val video = post.items.single()
        assertEquals(MediaType.VIDEO, video.type)
        assertEquals(1024, video.best.height)
        assertTrue(video.best.url.startsWith("https://") && ".tiktokcdn.com/" in video.best.url)
        assertTrue("&amp;" !in video.best.url && "\\u0026" !in video.best.url)
        assertTrue(video.thumbnailUrl.startsWith("https://"))
    }

    @Test
    fun deletedPostIsAnError() {
        val html = """<script id="__FRONTITY_CONNECT_STATE__" type="application/json">""" +
            """{"source":{"data":{"/embed/v2/1":{"isError":true,"code":"400"}}}}</script>"""
        assertTrue(TikTokParser.embedIsError(TikTokParser.embedState("1", html)!!))
    }

    /** Synthetic page in the shape of the mobile site's api-data for a photo post (no live sample was findable). */
    @Test
    fun slideshowFromMobilePage() {
        val html = """<script id="api-data" type="application/json">{"videoDetail":{"statusCode":0,"itemInfo":{"itemStruct":{
            "id":"7","desc":"three photos","author":{"uniqueId":"someone"},
            "imagePost":{"images":[
              {"imageURL":{"urlList":["https:\/\/p16-sign.tiktokcdn.com\/a.jpeg?x=1&y=2","https:\/\/p19-sign.tiktokcdn.com\/a.jpeg"]},"imageWidth":1080,"imageHeight":1440},
              {"imageURL":{"urlList":["https:\/\/p16-sign.tiktokcdn.com\/b.jpeg"]},"imageWidth":1080,"imageHeight":1920}
            ]}}}}}</script>"""
        val (item, status) = TikTokParser.pageItem(html)
        assertEquals(0, status)
        val post = TikTokParser.parseSlideshow("7", item!!)!!
        assertEquals("someone", post.author)
        assertEquals(2, post.items.size)
        assertTrue(post.items.all { it.type == MediaType.IMAGE })
        assertEquals("https://p16-sign.tiktokcdn.com/a.jpeg?x=1&y=2", post.items[0].best.url)
        assertEquals(1440, post.items[0].best.height)
    }

    @Test
    fun missingPostReportsStatus() {
        val html = """<script id="api-data" type="application/json">{"videoDetail":{"statusCode":10204}}</script>"""
        val (item, status) = TikTokParser.pageItem(html)
        assertNull(item)
        assertEquals(TikTokParser.STATUS_NOT_FOUND, status)
    }

    @Test
    fun postIdFromLinkShapes() {
        assertEquals("6718335390845095173", TikTokExtractor.postId("https://www.tiktok.com/@scout2015/video/6718335390845095173?lang=en"))
        assertEquals("7253412088251534594", TikTokExtractor.postId("https://www.tiktok.com/@hara_yoimiya/photo/7253412088251534594"))
        assertEquals("6718335390845095173", TikTokExtractor.postId("https://m.tiktok.com/v/6718335390845095173.html"))
        assertEquals("6718335390845095173", TikTokExtractor.postId("https://www.tiktok.com/embed/v2/6718335390845095173"))
        assertNull(TikTokExtractor.postId("https://vm.tiktok.com/ZTR45GpSF/"))
        assertTrue(TikTokExtractor().canHandle("https://vt.tiktok.com/ZSe4FqkKd"))
        assertFalse(TikTokExtractor().canHandle("https://notiktok.com/@a/video/1"))
    }
}
