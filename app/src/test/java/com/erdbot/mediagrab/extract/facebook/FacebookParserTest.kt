package com.erdbot.mediagrab.extract.facebook

import com.erdbot.mediagrab.extract.Fixtures
import com.erdbot.mediagrab.extract.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookParserTest {

    /** Captured live from facebook.com/plugins/video.php without login on 2026-10-15. */
    @Test
    fun pluginPageYieldsHdAndSd() {
        val post = FacebookParser.parse("10153231379946729", Fixtures.text("facebook_plugin_video.html"))!!
        val video = post.items.single()
        assertEquals(MediaType.VIDEO, video.type)
        assertEquals(2, video.variants.size)
        assertEquals(720, video.best.height)
        assertTrue(video.variants.all { it.url.startsWith("https://video") && ".fbcdn.net/" in it.url && ".mp4" in it.url })
        assertTrue(video.variants.none { "\\/" in it.url })
    }

    /** A public reel page fetched anonymously (via a /share/r/ link) on 2026-10-15. */
    @Test
    fun reelPageUsesBrowserNativeUrls() {
        val post = FacebookParser.parse("2186476288942684", Fixtures.text("facebook_reel_page.html"))!!
        val video = post.items.single()
        assertEquals(2, video.variants.size)
        assertTrue(video.variants.all { ".fbcdn.net/" in it.url && "\\/" !in it.url })
    }

    @Test
    fun loggedInPageKeys() {
        val html = """{"browser_native_hd_url":"https:\/\/video.xx.fbcdn.net\/v\/hd.mp4?a=1&2",""" +
            """"browser_native_sd_url":"https:\/\/video.xx.fbcdn.net\/v\/sd.mp4","owner":{"__typename":"User","name":"Jane Doe"}}"""
        val post = FacebookParser.parse("1", html)!!
        assertEquals("Jane Doe", post.author)
        assertEquals("https://video.xx.fbcdn.net/v/hd.mp4?a=1&2", post.items.single().best.url)
    }

    /** Synthetic page in the shape of a logged-in multi-photo post (no anonymous sample was obtainable). */
    @Test
    fun postPhotosKeepLargestPerImageAndSkipProfilePictures() {
        val html = """
            {"owner":{"__typename":"Page","name":"Some Page"},
             "profile_picture":{"uri":"https:\/\/scontent.xx.fbcdn.net\/v\/t39.30808-1\/avatar.jpg"},
             "all_subattachments":{"nodes":[
               {"media":{"__typename":"Photo","viewer_image":{"height":1365,"width":2048,"uri":"https:\/\/scontent.xx.fbcdn.net\/v\/t39.30808-6\/one_n.jpg?stp=a&1"}}},
               {"media":{"__typename":"Photo","photo_image":{"uri":"https:\/\/scontent.xx.fbcdn.net\/v\/t39.30808-6\/one_n.jpg?stp=small","height":320,"width":480}}},
               {"media":{"__typename":"Photo","viewer_image":{"height":1080,"width":1080,"uri":"https:\/\/scontent.xx.fbcdn.net\/v\/t39.30808-6\/two_n.jpg"}}}
             ]}}
        """.trimIndent()
        val post = FacebookParser.parsePhotos("p1", html)!!
        assertEquals("Some Page", post.author)
        assertEquals(2, post.items.size)
        assertTrue(post.items.all { it.type == MediaType.IMAGE })
        assertEquals("https://scontent.xx.fbcdn.net/v/t39.30808-6/one_n.jpg?stp=a&1", post.items[0].best.url)
        assertEquals(2048, post.items[0].best.width)
    }

    @Test
    fun photoFallsBackToShareImage() {
        val html = """<meta property="og:image" content="https://scontent.xx.fbcdn.net/v/t39.30808-6/pic.jpg?a=1&amp;b=2" />"""
        val post = FacebookParser.parsePhotos("1", html)!!
        assertEquals("https://scontent.xx.fbcdn.net/v/t39.30808-6/pic.jpg?a=1&b=2", post.items.single().best.url)
    }

    @Test
    fun photoLinkShapes() {
        assertEquals("1234567890", FacebookExtractor.photoId("https://www.facebook.com/photo/?fbid=1234567890&set=a.1"))
        assertEquals("1234567890", FacebookExtractor.photoId("https://www.facebook.com/photo.php?fbid=1234567890"))
        assertEquals("9876543210", FacebookExtractor.photoId("https://www.facebook.com/NASA/photos/a.123456/9876543210/"))
        assertEquals("pfbid02AbCdEf", FacebookExtractor.photoId("https://www.facebook.com/NASA/posts/pfbid02AbCdEf"))
        assertNull(FacebookExtractor.photoId("https://www.facebook.com/reel/2186476288942684"))
    }

    @Test
    fun noVideoMeansNull()= assertNull(FacebookParser.parse("1", "<html><title>Log in to Facebook</title></html>"))

    @Test
    fun videoIdFromLinkShapes() {
        assertEquals("10153231379946729", FacebookExtractor.videoId("https://www.facebook.com/facebook/videos/10153231379946729/"))
        assertEquals("123456789", FacebookExtractor.videoId("https://www.facebook.com/watch/?v=123456789"))
        assertEquals("987654321", FacebookExtractor.videoId("https://m.facebook.com/reel/987654321?mibextid=x"))
        assertEquals("555555555", FacebookExtractor.videoId("https://www.facebook.com/someone/videos/a-title/555555555/"))
        assertNull(FacebookExtractor.videoId("https://www.facebook.com/share/v/1AbCdEfG/"))
    }
}
