package com.erdbot.mediagrab.extract.facebook

import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.Variant
import org.json.JSONArray

/**
 * Facebook embeds the video's direct MP4 links as JSON-escaped strings inside its HTML.
 * The public embed plugin uses hd_src/sd_src; logged-in pages use the newer
 * browser_native_* and playable_url* keys.
 */
object FacebookParser {

    private val hdKeys = listOf("browser_native_hd_url", "playable_url_quality_hd", "hd_src_no_ratelimit", "hd_src")
    private val sdKeys = listOf("browser_native_sd_url", "playable_url", "sd_src_no_ratelimit", "sd_src")
    private val thumbnail = Regex(""""(?:preferred_thumbnail":\{"image":\{"uri|thumbnailImage":\{"uri|thumbnail_src)":"((?:[^"\\]|\\.)+)"""")
    private val ogImage = Regex("""<meta property="og:image" content="([^"]+)"""")
    private val ownerName = Regex(""""(?:owner|video_owner)":\{[^{}]*?"name":"((?:[^"\\]|\\.)+)"""")

    fun parse(id: String, html: String): MediaPost? {
        val hd = firstValue(html, hdKeys)
        val sd = firstValue(html, sdKeys)
        val variants = listOfNotNull(
            hd?.let { Variant(it, "video/mp4", height = 720) },
            sd?.takeIf { it != hd }?.let { Variant(it, "video/mp4", height = 360) },
        )
        if (variants.isEmpty()) return null
        val thumb = thumbnail.find(html)?.groupValues?.get(1)?.let(::unescape)
            ?: ogImage.find(html)?.groupValues?.get(1)?.replace("&amp;", "&")
            ?: ""
        return MediaPost(
            platform = Platform.FACEBOOK,
            id = id,
            author = ownerName.find(html)?.groupValues?.get(1)?.let(::unescape).orEmpty(),
            caption = "",
            items = listOf(MediaItem(MediaType.VIDEO, thumb, variants)),
        )
    }

    // Photo pages and multi-photo posts describe each image as {"height":H,"width":W,"uri":"…"} (any key order)
    // under viewer_image / photo_image / image; profile pictures and emoji live elsewhere.
    private val photoObject = Regex(""""(?:viewer_image|photo_image|currMedia"[^{]{0,200}"image|largeImage)":\{([^{}]*?"uri":"(?:[^"\\]|\\.)+"[^{}]*)\}""")
    private val uriIn = Regex(""""uri":"((?:[^"\\]|\\.)+)"""")
    private val widthIn = Regex(""""width":(\d+)""")
    private val heightIn = Regex(""""height":(\d+)""")

    /** Photos of a photo page or post, largest rendition per image; null when there are none. */
    fun parsePhotos(id: String, html: String): MediaPost? {
        val best = LinkedHashMap<String, Variant>()
        photoObject.findAll(html).forEach { m ->
            val body = m.groupValues[1]
            val url = uriIn.find(body)?.groupValues?.get(1)?.let(::unescape) ?: return@forEach
            if (!isContentPhoto(url)) return@forEach
            val v = Variant(
                url, "image/jpeg",
                width = widthIn.find(body)?.groupValues?.get(1)?.toInt() ?: 0,
                height = heightIn.find(body)?.groupValues?.get(1)?.toInt() ?: 0,
            )
            val key = url.substringBefore('?').substringAfterLast('/')
            if ((best[key]?.width ?: -1) < v.width) best[key] = v
        }
        if (best.isEmpty()) {
            // Public pages always carry a share preview; it is the photo itself, at reduced size.
            val og = ogImage.find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
            if (!isContentPhoto(og)) return null
            best[og] = Variant(og, "image/jpeg")
        }
        return MediaPost(
            platform = Platform.FACEBOOK,
            id = id,
            author = ownerName.find(html)?.groupValues?.get(1)?.let(::unescape).orEmpty(),
            caption = "",
            items = best.values.map { MediaItem(MediaType.IMAGE, it.url, listOf(it)) },
        )
    }

    /** Uploaded photos are served from scontent; skip profile pictures (t39.30808-1) and static UI images. */
    private fun isContentPhoto(url: String) =
        "fbcdn.net" in url && "/t39.30808-1/" !in url && "rsrc.php" !in url && "/emoji" !in url

    private fun firstValue(html: String, keys: List<String>): String? = keys.firstNotNullOfOrNull { key ->
        Regex(""""$key":"((?:[^"\\]|\\.)+)"""").find(html)?.groupValues?.get(1)?.let(::unescape)
    }

    /** Decodes a JSON string body (\/ and % escapes) by letting org.json parse it. */
    fun unescape(jsonStringBody: String): String = JSONArray("[\"$jsonStringBody\"]").getString(0)
}
