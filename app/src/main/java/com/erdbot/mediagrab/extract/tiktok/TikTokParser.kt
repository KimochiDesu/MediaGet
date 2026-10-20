package com.erdbot.mediagrab.extract.tiktok

import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.Variant
import org.json.JSONArray
import org.json.JSONObject

/**
 * TikTok pages carry their data as JSON in a <script> tag:
 * - the embed player (/embed/v2/{id}) in __FRONTITY_CONNECT_STATE__, whose video URLs download without cookies;
 * - the mobile post page in "api-data" (videoDetail.itemInfo.itemStruct), which also describes photo slideshows.
 */
object TikTokParser {

    /** The page's statusCode for a post that was deleted or never existed. */
    const val STATUS_NOT_FOUND = 10204

    fun scriptJson(html: String, id: String): JSONObject? {
        val start = html.indexOf("<script id=\"$id\"").takeIf { it >= 0 } ?: return null
        val bodyStart = html.indexOf('>', start) + 1
        val end = html.indexOf("</script>", bodyStart).takeIf { it > 0 } ?: return null
        return runCatching { JSONObject(html.substring(bodyStart, end)) }.getOrNull()
    }

    /** The embed page's state for [id]; null when the page has none (blocked or changed). */
    fun embedState(id: String, html: String): JSONObject? =
        scriptJson(html, "__FRONTITY_CONNECT_STATE__")
            ?.optJSONObject("source")?.optJSONObject("data")?.optJSONObject("/embed/v2/$id")

    /** True when the embed player says the post is gone, private or region-locked. */
    fun embedIsError(state: JSONObject) = state.optBoolean("isError") || state.optJSONObject("videoData") == null

    /** A video post from the embed player; null for slideshows or when no video URL is present. */
    fun parseEmbed(id: String, state: JSONObject): MediaPost? {
        val data = state.optJSONObject("videoData") ?: return null
        if (data.optJSONObject("imagePostInfo") != null) return null
        val info = data.optJSONObject("itemInfos") ?: return null
        val video = info.optJSONObject("video") ?: return null
        val url = video.optJSONArray("urls")?.strings()?.firstOrNull() ?: return null
        val meta = video.optJSONObject("videoMeta")
        val variant = Variant(url, "video/mp4", width = meta?.optInt("width") ?: 0, height = meta?.optInt("height") ?: 0)
        return MediaPost(
            platform = Platform.TIKTOK,
            id = id,
            author = data.optJSONObject("authorInfos")?.optString("uniqueId").orEmpty(),
            caption = info.optString("text"),
            items = listOf(MediaItem(MediaType.VIDEO, info.optJSONArray("covers")?.strings()?.firstOrNull().orEmpty(), listOf(variant))),
        )
    }

    /** The post from the mobile page's "api-data"; returns the TikTok status code when there is no post. */
    fun pageItem(html: String): Pair<JSONObject?, Int> {
        val detail = scriptJson(html, "api-data")?.optJSONObject("videoDetail") ?: return null to -1
        val item = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
        return item to detail.optInt("statusCode", -1)
    }

    /** A photo slideshow, one item per photo; null when [item] isn't one. */
    fun parseSlideshow(id: String, item: JSONObject): MediaPost? {
        val images = item.optJSONObject("imagePost")?.optJSONArray("images") ?: return null
        val photos = (0 until images.length()).mapNotNull { i ->
            val image = images.optJSONObject(i) ?: return@mapNotNull null
            val url = image.optJSONObject("imageURL")?.optJSONArray("urlList")?.strings()?.firstOrNull()
                ?: return@mapNotNull null
            val variant = Variant(url, "image/jpeg", width = image.optInt("imageWidth"), height = image.optInt("imageHeight"))
            MediaItem(MediaType.IMAGE, url, listOf(variant))
        }
        if (photos.isEmpty()) return null
        return MediaPost(
            platform = Platform.TIKTOK,
            id = id,
            author = item.optJSONObject("author")?.optString("uniqueId").orEmpty(),
            caption = item.optString("desc"),
            items = photos,
        )
    }

    private fun JSONArray.strings() = (0 until length()).map { optString(it) }.filter { it.startsWith("http") }
}
