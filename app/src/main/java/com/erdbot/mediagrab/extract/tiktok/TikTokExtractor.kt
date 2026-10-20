package com.erdbot.mediagrab.extract.tiktok

import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Extractor
import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.asBrowserDocument
import okhttp3.Request

/**
 * Resolves public TikTok videos and photo slideshows, no login.
 * Order: the embed player (its video URLs download without cookies, unlike the main site's) →
 * the mobile post page, which is the only place slideshow photos appear. The desktop post page
 * answers scripted clients with a bot challenge, so it isn't used.
 */
class TikTokExtractor(private val http: Http = Http()) : Extractor {

    override val platform = Platform.TIKTOK

    override fun canHandle(url: String) = tiktokUrl.containsMatchIn(url)

    override suspend fun extract(url: String): MediaPost {
        val postUrl = if (postId(url) != null) url else resolveShareLink(url)
        val id = postId(postUrl) ?: throw ExtractionException("That TikTok link doesn't point to a video or photo post.")

        val embed = fetch("https://www.tiktok.com/embed/v2/$id", Http.DESKTOP_UA)
        val state = TikTokParser.embedState(id, embed.body)
        if (state != null && !TikTokParser.embedIsError(state)) TikTokParser.parseEmbed(id, state)?.let { return it }

        // Slideshows, and videos the embed player won't show.
        val pageUrl = if (userInUrl.containsMatchIn(postUrl)) postUrl else "https://www.tiktok.com/share/video/$id"
        val (item, status) = TikTokParser.pageItem(fetch(pageUrl, MOBILE_UA).body)
        if (item == null) {
            if (status == TikTokParser.STATUS_NOT_FOUND || (state != null && TikTokParser.embedIsError(state))) {
                throw NotFoundException("This TikTok was deleted, is private, or isn't available in your region.")
            }
            throw ExtractionException("TikTok didn't return this post. Try again in a minute.")
        }
        TikTokParser.parseSlideshow(id, item)?.let { return it }
        throw ExtractionException("Couldn't find a downloadable file in this TikTok.")
    }

    /** vm.tiktok.com, vt.tiktok.com and tiktok.com/t/ links redirect to the full post URL. */
    private suspend fun resolveShareLink(url: String): String {
        val response = fetch(url, MOBILE_UA)
        if (postId(response.finalUrl) == null) {
            throw NotFoundException("This TikTok share link has expired or doesn't lead to a post.")
        }
        return response.finalUrl
    }

    private suspend fun fetch(url: String, userAgent: String): Http.Response =
        http.execute(Request.Builder().url(url).asBrowserDocument().header("User-Agent", userAgent).apply {
            // Client hints must agree with the User-Agent or the request looks forged.
            if (userAgent == MOBILE_UA) {
                header("sec-ch-ua-mobile", "?1")
                header("sec-ch-ua-platform", "\"Android\"")
            }
        }.build())

    companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        private val tiktokUrl = Regex("""^https?://(?:[a-z0-9-]+\.)?tiktok\.com/""", RegexOption.IGNORE_CASE)
        private val userInUrl = Regex("""tiktok\.com/@[\w.-]+/""")

        private val idPatterns = listOf(
            Regex("""/(?:video|photo)/(\d{15,})"""),
            Regex("""/embed(?:/v2)?/(\d{15,})"""),
            Regex("""/v/(\d{15,})\.html"""),
        )

        fun postId(url: String): String? = idPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
    }
}
