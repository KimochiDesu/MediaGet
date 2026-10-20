package com.erdbot.mediagrab.extract

import com.erdbot.mediagrab.extract.facebook.FacebookExtractor
import com.erdbot.mediagrab.extract.tiktok.TikTokExtractor
import com.erdbot.mediagrab.extract.twitter.TwitterExtractor
import com.erdbot.mediagrab.extract.youtube.YouTubeExtractor
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Hits the real services. Run with LIVE_TESTS=1; skipped otherwise. */
class LiveExtractorTest {

    private val loggedOut = object : WebSession {
        override val loginCookie = "none"
        override fun cookieHeader(): String? = null
    }

    @Before
    fun onlyWhenEnabled() = assumeTrue(System.getenv("LIVE_TESTS") == "1")

    /** The file must actually be downloadable by the app's HTTP client without auth. */
    private fun assertFetchable(url: String, expectedType: String) {
        val request = Request.Builder().url(url)
            .header("User-Agent", Http.DESKTOP_UA)
            .header("Range", "bytes=0-1023")
            .build()
        Http().client.newCall(request).execute().use {
            assertTrue("HTTP ${it.code} for $url", it.code == 200 || it.code == 206)
            assertTrue("type ${it.header("Content-Type")}", it.header("Content-Type").orEmpty().startsWith(expectedType))
        }
    }

    @Test
    fun twitterVideoEndToEnd() = runTest {
        val post = TwitterExtractor().extract("https://x.com/NASA/status/2110094874024219133")
        assertEquals("NASA", post.author)
        val best = post.items.single().best
        assertTrue(best.url.startsWith("https://video.twimg.com/"))
        assertFetchable(best.url, "video/mp4")
    }

    @Test
    fun twitterPhotosEndToEnd() = runTest {
        val post = TwitterExtractor().extract("https://twitter.com/NASA/status/2108285056170996122")
        assertEquals(2, post.items.size)
        assertTrue(post.items.all { it.type == MediaType.IMAGE })
    }

    @Test
    fun facebookPublicVideoEndToEnd() = runTest {
        val post = FacebookExtractor(loggedOut).extract("https://www.facebook.com/facebook/videos/10153231379946729/")
        val video = post.items.single()
        assertEquals(2, video.variants.size)
        assertFetchable(video.best.url, "video/mp4")
    }

    /** The reel share link that failed on the user's phone before the page fallback existed. */
    @Test
    fun facebookReelShareLinkEndToEnd() = runTest {
        val post = FacebookExtractor(loggedOut).extract("https://www.facebook.com/share/r/1JTLViWBhU/")
        assertEquals("2186476288942684", post.id)
        assertFetchable(post.items.single().best.url, "video/mp4")
    }

    @Test
    fun youtubeHdEndToEnd()= runTest(timeout = 2.minutes) {
        val post = YouTubeExtractor().extract("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        println("YouTube: ${post.caption} by ${post.author}")
        val variants = post.items.single().variants
        variants.forEach { println("  ${it.label} ${it.mimeType} merge=${it.audioUrl != null}") }

        val hd = variants.first { it.height >= 720 }
        assertTrue("HD needs a merged audio track", hd.audioUrl != null)
        assertFetchable(hd.url, "video/mp4")
        assertFetchable(hd.audioUrl!!, "audio/mp4")
        assertTrue(variants.any { it.mimeType == "audio/mp4" })
        assertTrue(variants.any { it.audioUrl == null && it.mimeType == "video/mp4" }) // 360p ready-made
    }

    @Test
    fun tiktokVideoEndToEnd() = runTest {
        val post = TikTokExtractor().extract("https://www.tiktok.com/@scout2015/video/6718335390845095173")
        assertEquals("scout2015", post.author)
        assertFetchable(post.items.single().best.url, "video/mp4")
    }

    @Test
    fun youtubeShortsUrlIsRecognised() {
        assertTrue(YouTubeExtractor().canHandle("https://youtube.com/shorts/aqz-KE-bpKQ"))
        assertTrue(YouTubeExtractor().canHandle("https://youtu.be/aqz-KE-bpKQ?si=abc"))
    }
}
