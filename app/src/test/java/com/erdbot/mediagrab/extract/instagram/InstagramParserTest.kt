package com.erdbot.mediagrab.extract.instagram

import com.erdbot.mediagrab.extract.Fixtures
import com.erdbot.mediagrab.extract.MediaType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramParserTest {

    @Test
    fun apiCarouselYieldsImageAndVideoBestFirst() {
        val item = Fixtures.json("instagram_api_carousel.json").getJSONArray("items").getJSONObject(0)
        val post = InstagramParser.parseApiItem("abc", item)
        assertEquals("someone", post.author)
        assertEquals("Two things", post.caption)
        assertEquals(listOf(MediaType.IMAGE, MediaType.VIDEO), post.items.map { it.type })
        assertEquals("https://cdn.example/img_1080.jpg", post.items[0].best.url)
        assertEquals("https://cdn.example/v_720.mp4", post.items[1].best.url)
        assertEquals(2, post.items[1].variants.size) // duplicate URL removed
        assertEquals("https://cdn.example/cover.jpg", post.items[1].thumbnailUrl)
    }

    @Test
    fun graphqlSidecar() {
        val media = Fixtures.json("instagram_graphql_sidecar.json")
            .getJSONObject("data").getJSONObject("xdt_shortcode_media")
        val post = InstagramParser.parseShortcodeMedia("abc", media)
        assertEquals("someone", post.author)
        assertEquals("Hello", post.caption)
        assertEquals(2, post.items.size)
        assertEquals("https://cdn.example/a_1080.jpg", post.items[0].best.url)
        assertEquals(MediaType.VIDEO, post.items[1].type)
        assertEquals("https://cdn.example/b.mp4", post.items[1].best.url)
    }

    @Test
    fun embedContextJsonIsDecoded() {
        val media = Fixtures.json("instagram_graphql_sidecar.json")
            .getJSONObject("data").getJSONObject("xdt_shortcode_media")
        val context = JSONObject().put("gql_data", JSONObject().put("shortcode_media", media)).toString()
        // The embed page stores contextJSON as an escaped JSON string literal.
        val literal = JSONObject.quote(context)
        val html = """<html><script>s.handle({"require":[["x",{"contextJSON":$literal}]]});</script></html>"""
        val post = InstagramParser.parseEmbed("abc", html)!!
        assertEquals(2, post.items.size)
    }

    @Test
    fun embedFallsBackToPlainImage() {
        val html = """<div class="Embed"><span class="UsernameText">someone</span>
            <img class="EmbeddedMediaImage" alt="" src="https://cdn.example/x.jpg?a=1&amp;b=2" /></div>"""
        val post = InstagramParser.parseEmbed("abc", html)!!
        assertEquals("someone", post.author)
        assertEquals("https://cdn.example/x.jpg?a=1&b=2", post.items.single().best.url)
    }

    @Test
    fun loginWallEmbedReturnsNull() {
        assertNull(InstagramParser.parseEmbed("abc", "<html><title>Login • Instagram</title></html>"))
    }
}
