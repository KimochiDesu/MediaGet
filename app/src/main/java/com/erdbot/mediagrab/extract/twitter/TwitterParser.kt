package com.erdbot.mediagrab.extract.twitter

import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.RateLimitedException
import com.erdbot.mediagrab.extract.Variant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Both the syndication (embed) endpoint and the GraphQL endpoint describe media with the
 * same v1.1 "media entity" shape: type, media_url_https and video_info.variants.
 */
object TwitterParser {

    private val resolutionInUrl = Regex("""/(\d{2,5})x(\d{2,5})/""")

    fun parseSyndication(id: String, json: JSONObject): MediaPost {
        if (json.optString("__typename") == "TweetTombstone") {
            throw NotFoundException("This post is unavailable.")
        }
        var media = json.optJSONArray("mediaDetails")
        var source = json
        if (media.isNullOrEmpty()) {
            val quoted = json.optJSONObject("quoted_tweet")
            media = quoted?.optJSONArray("mediaDetails")
            if (quoted != null) source = quoted
        }
        val user = source.optJSONObject("user")
        return MediaPost(
            platform = Platform.TWITTER,
            id = id,
            author = user?.optString("screen_name").orEmpty(),
            caption = source.optString("text"),
            items = parseMediaEntities(media),
        )
    }

    fun parseGraphql(id: String, json: JSONObject): MediaPost {
        json.optJSONArray("errors")?.optJSONObject(0)?.let { error ->
            val message = error.optString("message", "Unknown error")
            throw when (error.optInt("code")) {
                34, 144 -> NotFoundException("Post not found. It may have been deleted.")
                63 -> ExtractionException("This account has been suspended.")
                88 -> RateLimitedException("X is rate limiting requests. Wait a minute and try again.")
                179 -> LoginRequiredException("This account is protected.")
                else -> ExtractionException("X error: $message")
            }
        }
        var result = json.optJSONObject("data")?.optJSONObject("tweetResult")?.optJSONObject("result")
            ?: throw NotFoundException("Post not found. It may have been deleted.")
        when (result.optString("__typename")) {
            "TweetWithVisibilityResults" -> result = result.optJSONObject("tweet") ?: result
            "TweetTombstone", "TweetUnavailable" -> {
                val why = (result.optString("reason") + " " + result.optJSONObject("tombstone")?.toString().orEmpty()).lowercase()
                if ("protect" in why) {
                    throw LoginRequiredException("This account is protected. Log in to X with an account that follows them.")
                }
                throw NotFoundException("This post is unavailable (deleted, age-restricted, or protected).")
            }
        }
        return postFromGraphqlTweet(id, result, allowQuoted = true)
    }

    private fun postFromGraphqlTweet(id: String, tweet: JSONObject, allowQuoted: Boolean): MediaPost {
        val legacy = tweet.optJSONObject("legacy") ?: JSONObject()
        val media = legacy.optJSONObject("extended_entities")?.optJSONArray("media")
            ?: legacy.optJSONObject("entities")?.optJSONArray("media")
        if (media.isNullOrEmpty() && allowQuoted) {
            val quoted = tweet.optJSONObject("quoted_status_result")?.optJSONObject("result")
            if (quoted != null) return postFromGraphqlTweet(id, quoted, allowQuoted = false)
        }
        val userResult = tweet.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result")
        val screenName = userResult?.optJSONObject("core")?.optString("screen_name").orEmpty()
            .ifEmpty { userResult?.optJSONObject("legacy")?.optString("screen_name").orEmpty() }
        return MediaPost(
            platform = Platform.TWITTER,
            id = id,
            author = screenName,
            caption = legacy.optString("full_text"),
            items = parseMediaEntities(media),
        )
    }

    fun parseMediaEntities(media: JSONArray?): List<MediaItem> {
        if (media == null) return emptyList()
        return (0 until media.length()).mapNotNull { i -> parseEntity(media.getJSONObject(i)) }
    }

    private fun parseEntity(entity: JSONObject): MediaItem? {
        val thumb = entity.optString("media_url_https")
        return when (entity.optString("type")) {
            "photo" -> {
                val info = entity.optJSONObject("original_info")
                MediaItem(
                    type = MediaType.IMAGE,
                    thumbnailUrl = thumb,
                    variants = listOf(
                        Variant(
                            url = originalPhotoUrl(thumb),
                            mimeType = "image/jpeg",
                            width = info?.optInt("width") ?: 0,
                            height = info?.optInt("height") ?: 0,
                        ),
                    ),
                )
            }
            "video", "animated_gif" -> {
                val variants = videoVariants(entity.optJSONObject("video_info")?.optJSONArray("variants"))
                if (variants.isEmpty()) return null
                MediaItem(
                    type = if (entity.optString("type") == "animated_gif") MediaType.GIF else MediaType.VIDEO,
                    thumbnailUrl = thumb,
                    variants = variants,
                )
            }
            else -> null
        }
    }

    /** MP4 renditions only (HLS playlists would need muxing), highest bitrate first. */
    private fun videoVariants(variants: JSONArray?): List<Variant> {
        if (variants == null) return emptyList()
        return (0 until variants.length())
            .map { variants.getJSONObject(it) }
            .filter { it.optString("content_type") == "video/mp4" && it.optString("url").isNotEmpty() }
            .map {
                val url = it.optString("url")
                val size = resolutionInUrl.find(url)
                Variant(
                    url = url,
                    mimeType = "video/mp4",
                    width = size?.groupValues?.get(1)?.toInt() ?: 0,
                    height = size?.groupValues?.get(2)?.toInt() ?: 0,
                    bitrate = it.optInt("bitrate"),
                )
            }
            .sortedByDescending { it.bitrate }
    }

    /** pbs.twimg.com serves the full-resolution upload when asked for name=orig. */
    fun originalPhotoUrl(url: String): String {
        val base = url.substringBefore('?')
        val dot = base.lastIndexOf('.')
        if (dot <= base.lastIndexOf('/')) return url
        return "${base.substring(0, dot)}?format=${base.substring(dot + 1)}&name=orig"
    }

    private fun JSONArray?.isNullOrEmpty() = this == null || length() == 0
}
