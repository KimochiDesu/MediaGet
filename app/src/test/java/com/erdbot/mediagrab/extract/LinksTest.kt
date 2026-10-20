package com.erdbot.mediagrab.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {

    @Test
    fun tweetIdFromCommonLinkShapes() {
        assertEquals("2110094874024219133", Links.tweetId("https://x.com/NASA/status/2110094874024219133"))
        assertEquals("20", Links.tweetId("https://twitter.com/jack/status/20?s=46&t=abc"))
        assertEquals("123", Links.tweetId("https://mobile.twitter.com/someone/statuses/123"))
        assertEquals("456", Links.tweetId("https://x.com/i/web/status/456"))
        assertEquals("789", Links.tweetId("https://vxtwitter.com/a_b/status/789/video/1"))
        assertNull(Links.tweetId("https://x.com/NASA"))
    }

    @Test
    fun instagramShortcodes() {
        assertEquals("BsOGulcndj-", Links.instagramShortcode("https://www.instagram.com/p/BsOGulcndj-/"))
        assertEquals("C2lHlbdo3s8", Links.instagramShortcode("https://instagram.com/reel/C2lHlbdo3s8/?igsh=xyz"))
        assertEquals("C2lHlbdo3s8", Links.instagramShortcode("https://www.instagram.com/reels/C2lHlbdo3s8"))
        assertEquals("Abc_123", Links.instagramShortcode("https://www.instagram.com/someuser/p/Abc_123/"))
        assertNull(Links.instagramShortcode("https://www.instagram.com/someuser/"))
    }

    @Test
    fun instagramStories() {
        assertEquals("nasa" to "3456789012345678901", Links.instagramStory("https://www.instagram.com/stories/nasa/3456789012345678901/"))
        assertNull(Links.instagramStory("https://www.instagram.com/p/BsOGulcndj-/"))
    }

    @Test
    fun firstUrlFromSharedText() {
        assertEquals(
            "https://x.com/NASA/status/1",
            Links.firstUrl("Check this out! https://x.com/NASA/status/1 via @NASA"),
        )
        assertEquals("https://www.instagram.com/p/abc/", Links.firstUrl("(https://www.instagram.com/p/abc/)."))
        assertNull(Links.firstUrl("no link here"))
    }

    @Test
    fun shortcodeDecodesToMediaId() {
        // "B" = 1 in the shortcode alphabet, "A" = 0.
        assertEquals("1", Links.instagramMediaId("B"))
        assertEquals("64", Links.instagramMediaId("BA"))
        // Cross-checked with an independent Python decoder.
        assertEquals("1949525278281554174", Links.instagramMediaId("BsOGulcndj-"))
        // Private-post shortcodes carry extra characters after the first 11.
        assertEquals(Links.instagramMediaId("BsOGulcndj-"), Links.instagramMediaId("BsOGulcndj-XYZ123"))
    }
}
