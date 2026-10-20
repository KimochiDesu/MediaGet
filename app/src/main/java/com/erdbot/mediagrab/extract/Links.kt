package com.erdbot.mediagrab.extract

import java.math.BigInteger

/** Pulls post identifiers out of the many link shapes the apps share. */
object Links {
    private val urlInText = Regex("""https?://\S+""")

    private val tweet = Regex(
        """^https?://(?:www\.|mobile\.|m\.)?(?:twitter\.com|x\.com|fxtwitter\.com|vxtwitter\.com|fixupx\.com)/(?:[A-Za-z0-9_]+|i(?:/web)?)/status(?:es)?/(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    private val instagramPost = Regex(
        """^https?://(?:www\.|m\.)?instagram\.com/(?:[A-Za-z0-9._]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val instagramStory = Regex(
        """^https?://(?:www\.|m\.)?instagram\.com/stories/([A-Za-z0-9._]+)/(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Shared text is often "Look at this! https://x.com/..."; keep only the first link. */
    fun firstUrl(text: String): String? =
        urlInText.find(text.trim())?.value?.trimEnd('.', ',', ')', '!', '?', '"', '\'')

    fun tweetId(url: String): String? = tweet.find(url)?.groupValues?.get(1)

    fun instagramShortcode(url: String): String? = instagramPost.find(url)?.groupValues?.get(1)

    /** Returns (username, storyId) for a story link. */
    fun instagramStory(url: String): Pair<String, String>? =
        instagramStory.find(url)?.let { it.groupValues[1] to it.groupValues[2] }

    private const val SHORTCODE_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /**
     * Instagram shortcodes are the media id written in a URL-safe base64 alphabet.
     * Private-post shortcodes carry extra trailing characters, so only the first 11 encode the id.
     */
    fun instagramMediaId(shortcode: String): String {
        var id = BigInteger.ZERO
        val sixtyFour = BigInteger.valueOf(64)
        for (c in shortcode.take(11)) {
            val digit = SHORTCODE_ALPHABET.indexOf(c)
            require(digit >= 0) { "Invalid shortcode character '$c'" }
            id = id.multiply(sixtyFour).add(BigInteger.valueOf(digit.toLong()))
        }
        return id.toString()
    }
}
