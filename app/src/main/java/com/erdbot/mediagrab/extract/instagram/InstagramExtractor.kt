package com.erdbot.mediagrab.extract.instagram

import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Extractor
import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.Links
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.RateLimitedException
import com.erdbot.mediagrab.extract.WebSession
import com.erdbot.mediagrab.extract.asBrowserDocument
import com.erdbot.mediagrab.extract.asBrowserXhr
import com.erdbot.mediagrab.extract.withLogin
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/**
 * Resolves Instagram posts, reels and stories.
 * Order: the user's logged-in session (most reliable) → anonymous GraphQL → public embed page.
 * Anonymous access is heavily rate limited by Instagram, so stories and many posts need a login.
 */
class InstagramExtractor(
    /** The user's instagram.com login; its login cookie is "sessionid". */
    private val session: WebSession,
    private val http: Http = Http(),
) : Extractor {

    override val platform = Platform.INSTAGRAM

    override fun canHandle(url: String) =
        Links.instagramShortcode(url) != null || Links.instagramStory(url) != null

    override suspend fun extract(url: String): MediaPost {
        Links.instagramStory(url)?.let { (username, storyId) -> return story(username, storyId) }
        val shortcode = Links.instagramShortcode(url) ?: throw ExtractionException("Not an Instagram post link.")
        return post(shortcode)
    }

    private suspend fun post(shortcode: String): MediaPost {
        val errors = mutableListOf<Throwable>()
        if (session.isLoggedIn()) {
            runCatching { return viaPrivateApi(shortcode) }.onFailure { errors += it }
        }
        for (docId in POST_DOC_IDS) {
            runCatching { return viaGraphql(shortcode, docId) }.onFailure { errors += it }
        }
        runCatching { return viaPublicA1(shortcode) }.onFailure { errors += it }
        runCatching { viaEmbed(shortcode) }.onFailure { errors += it }.getOrNull()?.let { return it }

        errors.firstOrNull { it is NotFoundException || it is RateLimitedException }?.let { throw it }
        if (!session.isLoggedIn()) {
            throw LoginRequiredException("Instagram wants you to log in to see this post. Tap \"Log in to Instagram\" and try again.")
        }
        throw ExtractionException("Couldn't read this Instagram post.", errors.lastOrNull())
    }

    private suspend fun viaPrivateApi(shortcode: String): MediaPost {
        val mediaId = Links.instagramMediaId(shortcode)
        val json = getJson("https://www.instagram.com/api/v1/media/$mediaId/info/")
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: throw NotFoundException("Post not found.")
        return InstagramParser.parseApiItem(shortcode, item).requireMedia()
    }

    private suspend fun viaGraphql(shortcode: String, docId: String): MediaPost {
        val variables = JSONObject()
            .put("shortcode", shortcode)
            .put("fetch_tagged_user_count", JSONObject.NULL)
            .put("hoisted_comment_id", JSONObject.NULL)
            .put("hoisted_reply_id", JSONObject.NULL)
        val body = FormBody.Builder()
            .add("variables", variables.toString())
            .add("doc_id", docId)
            .add("lsd", LSD)
            .build()
        val request = baseRequest("https://www.instagram.com/graphql/query")
            .post(body)
            .header("X-FB-LSD", LSD)
            .header("X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Referer", "https://www.instagram.com/p/$shortcode/")
            .build()
        val json = parse(http.execute(request))
        val media = json.optJSONObject("data")?.let {
            it.optJSONObject("xdt_shortcode_media") ?: it.optJSONObject("shortcode_media")
        } ?: throw ExtractionException("GraphQL returned no media for doc_id $docId")
        return InstagramParser.parseShortcodeMedia(shortcode, media).requireMedia()
    }

    /**
     * The web client's own JSON view of a post. Returns the "items" shape when a session is
     * present, otherwise an older `graphql.shortcode_media`. A useful middle ground between the
     * GraphQL query (whose doc_ids rotate) and the HTML embed page.
     */
    private suspend fun viaPublicA1(shortcode: String): MediaPost {
        val json = getJson("https://www.instagram.com/p/$shortcode/?__a=1&__d=dis")
        json.optJSONArray("items")?.optJSONObject(0)?.let {
            return InstagramParser.parseApiItem(shortcode, it).requireMedia()
        }
        val media = json.optJSONObject("graphql")?.optJSONObject("shortcode_media")
            ?: throw ExtractionException("The __a=1 view had no media.")
        return InstagramParser.parseShortcodeMedia(shortcode, media).requireMedia()
    }

    private suspend fun viaEmbed(shortcode: String): MediaPost {
        val response = http.execute(
            Request.Builder()
                .url("https://www.instagram.com/p/$shortcode/embed/captioned/")
                .asBrowserDocument()
                .build(),
        )
        if (response.code == 404) throw NotFoundException("Post not found.")
        return InstagramParser.parseEmbed(shortcode, response.body)?.requireMedia()
            ?: throw ExtractionException("The embed page had no downloadable media.")
    }

    private suspend fun story(username: String, storyId: String): MediaPost {
        if (!session.isLoggedIn()) {
            throw LoginRequiredException("Stories are only visible when logged in. Tap \"Log in to Instagram\" first.")
        }
        val profile = getJson("https://www.instagram.com/api/v1/users/web_profile_info/?username=$username")
        val userId = profile.optJSONObject("data")?.optJSONObject("user")?.optString("id").orEmpty()
        if (userId.isEmpty()) throw NotFoundException("User @$username not found.")

        val reels = getJson("https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=$userId")
        val reel = reels.optJSONArray("reels_media")?.optJSONObject(0)
            ?: reels.optJSONObject("reels")?.optJSONObject(userId)
            ?: throw NotFoundException("@$username has no active story.")
        val items = reel.optJSONArray("items") ?: throw NotFoundException("@$username has no active story.")
        val all = (0 until items.length()).map { items.getJSONObject(it) }
        val chosen = all.filter { it.optString("pk") == storyId || it.optString("id").startsWith("${storyId}_") }
            .ifEmpty { all }
        return MediaPost(
            platform = Platform.INSTAGRAM,
            id = storyId,
            author = username,
            caption = "",
            items = chosen.flatMap { InstagramParser.apiMediaItems(it) },
        ).requireMedia()
    }

    private fun baseRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
            .asBrowserXhr()
            .header("X-IG-App-ID", WEB_APP_ID)
            .header("X-ASBD-ID", ASBD_ID)
            .header("X-IG-WWW-Claim", WWW_CLAIM)
            .header("X-Requested-With", "XMLHttpRequest")
        return builder.withLogin(
            "instagram.com",
            mapOf("Cookie" to session.cookieHeader(), "X-CSRFToken" to session.cookie("csrftoken")),
        )
    }

    private suspend fun getJson(url: String): JSONObject =
        parse(http.execute(baseRequest(url).header("Referer", "https://www.instagram.com/").build()))

    private fun parse(response: Http.Response): JSONObject {
        when (response.code) {
            401, 403 -> throw LoginRequiredException("Instagram rejected the request. Log in (again) and retry.")
            404 -> throw NotFoundException("Post not found or not visible to this account.")
            429 -> throw RateLimitedException("Instagram is rate limiting requests. Wait a few minutes and try again.")
        }
        val json = try {
            JSONObject(response.body)
        } catch (e: JSONException) {
            // A login wall or challenge comes back as an HTML page instead of JSON.
            throw ExtractionException("Instagram returned a web page instead of data (HTTP ${response.code}).", e)
        }
        if (json.optBoolean("require_login")) throw LoginRequiredException("Instagram requires a login for this.")
        return json
    }

    private fun MediaPost.requireMedia(): MediaPost =
        if (items.isEmpty()) throw ExtractionException("No downloadable media in this post.") else this

    companion object {
        /** Public app id of the instagram.com web client, sent by every web request. */
        const val WEB_APP_ID = "936619743392459"

        /** Constant the web client sends on every XHR (a bundled client-build marker). */
        const val ASBD_ID = "129477"

        /**
         * The web client's rate-limit claim. A fresh session sends "0"; the server then returns
         * a real claim it is meant to echo back. "0" is what the first request of a session sends.
         */
        const val WWW_CLAIM = "0"

        /** The web client's anti-CSRF "lsd" value; any well-formed token is accepted for public queries. */
        const val LSD = "AVqbxe3J_YA"

        /** GraphQL document ids for the single-post query; Instagram rotates these, so try several. */
        val POST_DOC_IDS = listOf("8845758582119845", "6588824391221552")
    }
}
