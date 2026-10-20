package com.erdbot.mediagrab.extract.twitter

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
import com.erdbot.mediagrab.extract.asBrowserXhr
import com.erdbot.mediagrab.extract.withLogin
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.PI

/**
 * Resolves X/Twitter posts:
 *  1. the public syndication endpoint that powers embedded tweets (no auth at all);
 *  2. the web client's TweetResultByRestId GraphQL query — with the user's x.com login when they
 *     signed in (needed for protected accounts they follow), otherwise with a guest token.
 */
class TwitterExtractor(
    /** The user's x.com login; its login cookie is "auth_token". Null in tests. */
    private val session: WebSession? = null,
    private val http: Http = Http(),
) : Extractor {

    override val platform = Platform.TWITTER

    override fun canHandle(url: String) = Links.tweetId(url) != null

    override suspend fun extract(url: String): MediaPost {
        val id = Links.tweetId(url) ?: throw ExtractionException("Not an X/Twitter post link.")
        val fromSyndication = runCatching { viaSyndication(id) }
        fromSyndication.getOrNull()?.let { if (it.items.isNotEmpty()) return it }

        val loggedIn = session?.isLoggedIn() == true
        val fromGraphql = runCatching { viaGraphql(id, loggedIn) }
        fromGraphql.getOrNull()?.let { return it }

        // Prefer the more specific GraphQL error (protected, rate limited, deleted...).
        val error = fromGraphql.exceptionOrNull() ?: fromSyndication.exceptionOrNull()
        if (error is NotFoundException && !loggedIn) {
            // Logged out, a protected account's posts look exactly like deleted ones.
            throw LoginRequiredException(
                "X won't show this post without a login. If it's from a protected account you follow, log in to X and try again.",
            )
        }
        if (error is ExtractionException) throw error
        fromSyndication.getOrNull()?.let { return it }
        throw ExtractionException("Couldn't read this post.", error)
    }

    private suspend fun viaSyndication(id: String): MediaPost {
        val url = "https://cdn.syndication.twimg.com/tweet-result".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("token", syndicationToken(id))
            .addQueryParameter("lang", "en")
            .build()
        val response = http.execute(
            Request.Builder().url(url).asBrowserXhr().header("Sec-Fetch-Site", "cross-site").build(),
        )
        if (response.code == 404 || response.body.isBlank()) throw NotFoundException("Post not found.")
        if (response.code != 200) throw ExtractionException("Syndication HTTP ${response.code}")
        return TwitterParser.parseSyndication(id, parseJson(response.body))
    }

    private suspend fun viaGraphql(id: String, loggedIn: Boolean): MediaPost {
        val variables = JSONObject()
            .put("tweetId", id)
            .put("withCommunity", false)
            .put("includePromotedContent", false)
            .put("withVoice", false)
        val features = JSONObject().apply { GRAPHQL_FEATURES.forEach { put(it, true) } }
        // Logged-in requests go through x.com like the website's own; guests use api.x.com.
        val host = if (loggedIn) "https://x.com/i/api/graphql" else "https://api.x.com/graphql"
        val url = "$host/$TWEET_QUERY_ID/TweetResultByRestId".toHttpUrl().newBuilder()
            .addQueryParameter("variables", variables.toString())
            .addQueryParameter("features", features.toString())
            .build()
        val request = Request.Builder().url(url)
            .asBrowserXhr()
            .header("Authorization", "Bearer $WEB_BEARER")
        if (loggedIn) {
            val s = session!!
            request.withLogin(
                "x.com",
                mapOf(
                    "Cookie" to s.cookieHeader(),
                    "x-csrf-token" to s.cookie("ct0"),
                    "x-twitter-auth-type" to "OAuth2Session",
                ),
            ).header("x-twitter-active-user", "yes")
        } else {
            request.header("X-Guest-Token", guestToken())
        }
        val response = http.execute(request.build())
        if (response.code == 429) throw RateLimitedException("X is rate limiting requests. Wait a minute and try again.")
        if (response.code == 401 || response.code == 403) {
            throw LoginRequiredException(if (loggedIn) "X rejected your login. Log out and log in to X again." else "X needs a login for this post.")
        }
        if (response.code != 200) throw ExtractionException("X GraphQL HTTP ${response.code}")
        return TwitterParser.parseGraphql(id, parseJson(response.body))
    }

    private suspend fun guestToken(): String {
        val request = Request.Builder()
            .url("https://api.twitter.com/1.1/guest/activate.json")
            .post(ByteArray(0).toRequestBody())
            .asBrowserXhr()
            .header("Sec-Fetch-Site", "cross-site")
            .header("Authorization", "Bearer $WEB_BEARER")
            .build()
        val response = http.execute(request)
        if (response.code == 429) throw RateLimitedException("X is rate limiting requests. Wait a minute and try again.")
        return parseJson(response.body).optString("guest_token").ifEmpty {
            throw ExtractionException("X didn't issue a guest token (HTTP ${response.code}).")
        }
    }

    private fun parseJson(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw ExtractionException("Unexpected response from X.", e)
    }

    companion object {
        /** The public bearer token embedded in the x.com web client (not a user credential). */
        const val WEB_BEARER =
            "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

        /** Query id of TweetResultByRestId in the x.com web bundle; X rotates these occasionally. */
        const val TWEET_QUERY_ID = "xBtHv5-Xsk268T5ng_OGNg"

        val GRAPHQL_FEATURES = listOf(
            "creator_subscriptions_tweet_preview_api_enabled",
            "communities_web_enable_tweet_community_results_fetch",
            "c9s_tweet_anatomy_moderator_badge_enabled",
            "articles_preview_enabled",
            "tweetypie_unmention_optimization_enabled",
            "responsive_web_edit_tweet_api_enabled",
            "graphql_is_translatable_rweb_tweet_is_translatable_enabled",
            "view_counts_everywhere_api_enabled",
            "longform_notetweets_consumption_enabled",
            "responsive_web_twitter_article_tweet_consumption_enabled",
            "freedom_of_speech_not_reach_fetch_enabled",
            "standardized_nudges_misinfo",
            "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled",
            "rweb_video_timestamps_enabled",
            "longform_notetweets_rich_text_read_enabled",
            "longform_notetweets_inline_media_enabled",
            "responsive_web_graphql_exclude_directive_enabled",
            "responsive_web_graphql_timeline_navigation_enabled",
            "rweb_tipjar_consumption_enabled",
        )

        /**
         * The embed widget derives this token as `((id / 1e15) * PI).toString(36)` with zeros
         * and the dot removed.
         */
        fun syndicationToken(id: String): String {
            val value = (id.toDouble() / 1e15) * PI
            return toBase36(value).replace(Regex("(0+|\\.)"), "")
        }

        private fun toBase36(value: Double): String {
            val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
            var integer = value.toLong()
            var fraction = value - integer
            val sb = StringBuilder()
            do {
                sb.insert(0, digits[(integer % 36).toInt()])
                integer /= 36
            } while (integer > 0)
            if (fraction > 0) {
                sb.append('.')
                var n = 0
                while (fraction > 0 && n < 20) {
                    fraction *= 36
                    val d = fraction.toInt()
                    sb.append(digits[d])
                    fraction -= d
                    n++
                }
            }
            return sb.toString()
        }
    }
}
