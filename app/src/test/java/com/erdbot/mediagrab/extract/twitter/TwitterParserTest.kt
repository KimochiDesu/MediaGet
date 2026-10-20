package com.erdbot.mediagrab.extract.twitter

import com.erdbot.mediagrab.extract.Fixtures
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.RateLimitedException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures were captured live from cdn.syndication.twimg.com and api.x.com on 2026-10-14. */
class TwitterParserTest {

    private val videoId = "2110094874024219133"

    @Test
    fun syndicationVideoKeepsMp4VariantsBestFirst() {
        val post = TwitterParser.parseSyndication(videoId, Fixtures.json("twitter_syndication_video.json"))
        assertEquals("NASA", post.author)
        assertEquals(1, post.items.size)
        val video = post.items.single()
        assertEquals(MediaType.VIDEO, video.type)
        assertEquals(4, video.variants.size) // the HLS playlist is dropped
        assertTrue(video.variants.all { it.mimeType == "video/mp4" })
        assertEquals(10368000, video.best.bitrate)
        assertEquals(1080, video.best.height)
        assertEquals(video.variants.sortedByDescending { it.bitrate }, video.variants)
    }

    @Test
    fun syndicationPhotosUseOriginalSize() {
        val post = TwitterParser.parseSyndication("2108285056170996122", Fixtures.json("twitter_syndication_photos.json"))
        assertEquals(2, post.items.size)
        val photo = post.items.first()
        assertEquals(MediaType.IMAGE, photo.type)
        assertEquals("https://pbs.twimg.com/media/HUIiFOpW8AABTfc?format=jpg&name=orig", photo.best.url)
        assertEquals(2048, photo.best.width)
    }

    @Test
    fun graphqlMatchesSyndication() {
        val gql = TwitterParser.parseGraphql(videoId, Fixtures.json("twitter_graphql_video.json"))
        val synd = TwitterParser.parseSyndication(videoId, Fixtures.json("twitter_syndication_video.json"))
        assertEquals("NASA", gql.author)
        assertEquals(synd.items.single().best.url, gql.items.single().best.url)
    }

    @Test
    fun graphqlUnwrapsVisibilityResults() {
        val inner = Fixtures.json("twitter_graphql_video.json")
            .getJSONObject("data").getJSONObject("tweetResult").getJSONObject("result")
        val wrapped = JSONObject().put(
            "data",
            JSONObject().put(
                "tweetResult",
                JSONObject().put("result", JSONObject().put("__typename", "TweetWithVisibilityResults").put("tweet", inner)),
            ),
        )
        assertEquals(1, TwitterParser.parseGraphql(videoId, wrapped).items.size)
    }

    @Test(expected = NotFoundException::class)
    fun graphqlTombstoneIsNotFound() {
        TwitterParser.parseGraphql(
            "1",
            JSONObject("""{"data":{"tweetResult":{"result":{"__typename":"TweetTombstone"}}}}"""),
        )
    }

    @Test(expected = LoginRequiredException::class)
    fun graphqlProtectedAccountAsksForLogin() {
        TwitterParser.parseGraphql(
            "1",
            JSONObject("""{"data":{"tweetResult":{"result":{"__typename":"TweetUnavailable","reason":"Protected"}}}}"""),
        )
    }

    @Test
    fun graphqlErrorCodesMapToFriendlyErrors() {
        fun errorFor(code: Int) = runCatching {
            TwitterParser.parseGraphql("1", JSONObject("""{"errors":[{"code":$code,"message":"x"}]}"""))
        }.exceptionOrNull()
        assertTrue(errorFor(144) is NotFoundException)
        assertTrue(errorFor(88) is RateLimitedException)
        assertTrue(errorFor(179) is LoginRequiredException)
    }

    @Test
    fun originalPhotoUrl() {
        assertEquals(
            "https://pbs.twimg.com/media/abc?format=png&name=orig",
            TwitterParser.originalPhotoUrl("https://pbs.twimg.com/media/abc.png"),
        )
    }

    @Test
    fun syndicationTokenMatchesEmbedWidgetAlgorithm() {
        // Value cross-checked against a Python port of ((id / 1e15) * PI).toString(36).
        assertEquals("6dq1a2xwd93", TwitterExtractor.syndicationToken("20"))
    }
}
