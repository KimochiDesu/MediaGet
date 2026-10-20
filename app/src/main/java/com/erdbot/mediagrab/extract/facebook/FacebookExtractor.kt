package com.erdbot.mediagrab.extract.facebook

import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Extractor
import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.WebSession
import com.erdbot.mediagrab.extract.asBrowserDocument
import com.erdbot.mediagrab.extract.withLogin
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Resolves Facebook videos and reels.
 * Order: the public embed plugin (works for public videos without login) → the video page itself
 * with the user's facebook.com login (for videos only their account can see).
 */
class FacebookExtractor(
    /** The user's facebook.com login; its login cookie is "c_user". */
    private val session: WebSession,
    private val http: Http = Http(),
) : Extractor {

    override val platform = Platform.FACEBOOK

    override fun canHandle(url: String) = facebookUrl.containsMatchIn(url)

    override suspend fun extract(url: String): MediaPost {
        val canonical = resolveShareLink(url)
        val id = videoId(canonical) ?: return extractPhotos(canonical)

        val plugin = "https://www.facebook.com/plugins/video.php".toHttpUrl().newBuilder()
            .addQueryParameter("href", "https://www.facebook.com/watch/?v=$id")
            .addQueryParameter("show_text", "false")
            .build()
        // 1. The embed plugin works for regular videos but returns nothing for reels.
        val pluginPage = fetch(plugin.toString(), withLogin = true)
        FacebookParser.parse(id, pluginPage.body)?.let { return it.withAuthorFrom(canonical) }

        // 2. Public videos and reels embed their MP4 links in the page itself when fetched
        //    anonymously; with a login cookie Facebook serves a different page without them.
        val publicPage = fetch(canonical, withLogin = false)
        FacebookParser.parse(id, publicPage.body)?.let { return it.withAuthorFrom(canonical, publicPage.body) }

        // 3. Videos only the user's account can see.
        if (session.isLoggedIn()) {
            val page = fetch(canonical, withLogin = true)
            FacebookParser.parse(id, page.body)?.let { return it.withAuthorFrom(canonical, page.body) }
            if (page.code == 404) throw NotFoundException("This video was removed or isn't visible to your account.")
            throw ExtractionException("Couldn't find a downloadable file in this Facebook video.")
        }
        throw LoginRequiredException("This video isn't public. Log in to Facebook in MediaGrab and try again.")
    }

    /** Photo pages and posts: whatever media the page carries (a post may also hold a video). */
    private suspend fun extractPhotos(url: String): MediaPost {
        val id = photoId(url) ?: url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        fun parse(html: String) = FacebookParser.parse(id, html) ?: FacebookParser.parsePhotos(id, html)

        val publicPage = fetch(url, withLogin = false)
        parse(publicPage.body)?.let { return it.withAuthorFrom(url, publicPage.body) }
        if (session.isLoggedIn()) {
            val page = fetch(url, withLogin = true)
            parse(page.body)?.let { return it.withAuthorFrom(url, page.body) }
            if (page.code == 404) throw NotFoundException("This post was removed or isn't visible to your account.")
            throw ExtractionException("Couldn't find photos or a video in this Facebook post.")
        }
        throw LoginRequiredException("This post isn't public. Log in to Facebook in MediaGrab and try again.")
    }

    /**
     * The embed page carries no uploader name, but /<page>/videos/<id> links do; for reels the
     * page's og:url has that shape.
     */
    private fun MediaPost.withAuthorFrom(url: String, html: String = ""): MediaPost {
        if (author.isNotEmpty()) return this
        val ogUrl = ogUrl.find(html)?.groupValues?.get(1).orEmpty()
        val page = (pageInUrl.find(url) ?: pageInUrl.find(ogUrl))?.groupValues?.get(1) ?: return this
        return copy(author = page)
    }

    /** fb.watch and /share/ links redirect to the real video URL. */
    private suspend fun resolveShareLink(url: String): String {
        if (videoId(url) != null || photoId(url) != null) return url
        var response = fetch(url, withLogin = false)
        if (response.finalUrl.contains("/login") && session.isLoggedIn()) response = fetch(url, withLogin = true)
        if (response.finalUrl.contains("/login")) {
            throw LoginRequiredException("Facebook wants you to log in to open this share link.")
        }
        return response.finalUrl.ifEmpty { url }
    }

    private suspend fun fetch(url: String, withLogin: Boolean): Http.Response {
        val request = Request.Builder().url(url).asBrowserDocument()
        if (withLogin) request.withLogin("facebook.com", mapOf("Cookie" to session.cookieHeader()))
        return http.execute(request.build())
    }

    companion object {
        private val facebookUrl = Regex("""^https?://(?:[a-z0-9-]+\.)?(?:facebook\.com|fb\.watch|fb\.com)/""", RegexOption.IGNORE_CASE)

        private val pageInUrl = Regex("""facebook\.com/(?!watch|reel|video\.php|share)([A-Za-z0-9.]+)/videos/""")
        private val ogUrl = Regex("""<meta property="og:url" content="([^"]+)"""")

        private val idPatterns = listOf(
            Regex("""/videos/(?:[^/?#]+/)?(\d{6,})"""),
            Regex("""[?&]v=(\d{6,})"""),
            Regex("""/reels?/(\d{6,})"""),
            Regex("""video\.php\?.*?\bv=(\d{6,})"""),
        )

        fun videoId(url: String): String? = idPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

        private val photoPatterns = listOf(
            Regex("""[?&]fbid=(\d{6,})"""),
            Regex("""/photos/(?:[^/?#]+/)?(\d{6,})"""),
            Regex("""/posts/(pfbid[0-9A-Za-z]+|\d{6,})"""),
            Regex("""[?&]story_fbid=(pfbid[0-9A-Za-z]+|\d{6,})"""),
        )

        /** Id of a photo or post link (a post's photos are read from its page). */
        fun photoId(url: String): String? = photoPatterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
    }
}
