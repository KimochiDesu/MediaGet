package com.erdbot.mediagrab.extract.instagram

import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.Variant
import org.json.JSONArray
import org.json.JSONObject

/** Parses the three response shapes Instagram serves for a post. */
object InstagramParser {

    // ---- Private web API (api/v1/media/{id}/info, feed/reels_media): "items" shape ----

    fun parseApiItem(id: String, item: JSONObject): MediaPost = MediaPost(
        platform = Platform.INSTAGRAM,
        id = id,
        author = item.optJSONObject("user")?.optString("username").orEmpty(),
        caption = item.optJSONObject("caption")?.optString("text").orEmpty(),
        items = apiMediaItems(item),
    )

    fun apiMediaItems(item: JSONObject): List<MediaItem> {
        val carousel = item.optJSONArray("carousel_media")
        if (item.optInt("media_type") == 8 && carousel != null) {
            return objects(carousel).mapNotNull { apiSingle(it) }
        }
        return listOfNotNull(apiSingle(item))
    }

    private fun apiSingle(item: JSONObject): MediaItem? {
        val images = objects(item.optJSONObject("image_versions2")?.optJSONArray("candidates"))
            .map { Variant(it.optString("url"), "image/jpeg", it.optInt("width"), it.optInt("height")) }
            .filter { it.url.isNotEmpty() }
            .sortedByDescending { it.width }
        val thumb = images.lastOrNull()?.url ?: images.firstOrNull()?.url.orEmpty()
        val videos = objects(item.optJSONArray("video_versions"))
            .map { Variant(it.optString("url"), "video/mp4", it.optInt("width"), it.optInt("height")) }
            .filter { it.url.isNotEmpty() }
            .distinctBy { it.url }
            .sortedByDescending { it.width }
        return when {
            videos.isNotEmpty() -> MediaItem(MediaType.VIDEO, thumb, videos)
            images.isNotEmpty() -> MediaItem(MediaType.IMAGE, thumb, images)
            else -> null
        }
    }

    // ---- Public GraphQL (xdt_shortcode_media) and embed "shortcode_media": edges shape ----

    fun parseShortcodeMedia(id: String, media: JSONObject): MediaPost {
        val children = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        val nodes = if (children != null && children.length() > 0) {
            objects(children).mapNotNull { it.optJSONObject("node") }
        } else {
            listOf(media)
        }
        val caption = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()
        return MediaPost(
            platform = Platform.INSTAGRAM,
            id = id,
            author = media.optJSONObject("owner")?.optString("username").orEmpty(),
            caption = caption,
            items = nodes.mapNotNull { graphNode(it) },
        )
    }

    private fun graphNode(node: JSONObject): MediaItem? {
        val dims = node.optJSONObject("dimensions")
        val width = dims?.optInt("width") ?: 0
        val height = dims?.optInt("height") ?: 0
        val display = node.optString("display_url")
        if (node.optBoolean("is_video")) {
            val video = node.optString("video_url")
            if (video.isEmpty()) return null
            return MediaItem(MediaType.VIDEO, display, listOf(Variant(video, "video/mp4", width, height)))
        }
        val resources = objects(node.optJSONArray("display_resources"))
            .map { Variant(it.optString("src"), "image/jpeg", it.optInt("config_width"), it.optInt("config_height")) }
            .filter { it.url.isNotEmpty() }
            .sortedByDescending { it.width }
        val variants = resources.ifEmpty {
            if (display.isEmpty()) return null
            listOf(Variant(display, "image/jpeg", width, height))
        }
        return MediaItem(MediaType.IMAGE, display.ifEmpty { variants.last().url }, variants)
    }

    // ---- Embed page HTML (/p/{code}/embed/captioned/) ----

    private val contextJson = Regex(""""contextJSON":("(?:[^"\\]|\\.)*")""")
    private val additionalData = Regex("""window\.__additionalDataLoaded\('extra',(\{.*?\})\);</script>""", RegexOption.DOT_MATCHES_ALL)
    private val embedImage = Regex("""class="EmbeddedMediaImage"[^>]*?src="([^"]+)"""")
    private val embedUsername = Regex("""class="UsernameText"[^>]*>([^<]+)<""")

    /** Returns null when the embed carries no usable media (e.g. it is a login wall). */
    fun parseEmbed(id: String, html: String): MediaPost? {
        shortcodeMediaFromEmbed(html)?.let { media ->
            val post = parseShortcodeMedia(id, media)
            if (post.items.isNotEmpty()) return post
        }
        // Single-image posts still render a plain <img>; videos and carousels need the JSON above.
        val src = embedImage.find(html)?.groupValues?.get(1)?.let(::unescapeHtml) ?: return null
        return MediaPost(
            platform = Platform.INSTAGRAM,
            id = id,
            author = embedUsername.find(html)?.groupValues?.get(1)?.trim().orEmpty(),
            caption = "",
            items = listOf(MediaItem(MediaType.IMAGE, src, listOf(Variant(src, "image/jpeg")))),
        )
    }

    private fun shortcodeMediaFromEmbed(html: String): JSONObject? {
        contextJson.find(html)?.groupValues?.get(1)?.let { literal ->
            runCatching {
                // The value is a JSON string literal whose content is itself JSON.
                val inner = JSONArray("[$literal]").getString(0)
                JSONObject(inner).optJSONObject("gql_data")?.optJSONObject("shortcode_media")
            }.getOrNull()?.let { return it }
        }
        additionalData.find(html)?.groupValues?.get(1)?.let { raw ->
            runCatching {
                val root = JSONObject(raw)
                root.optJSONObject("shortcode_media")
                    ?: root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun unescapeHtml(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")

    private fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}
