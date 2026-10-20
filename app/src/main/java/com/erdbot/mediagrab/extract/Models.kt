package com.erdbot.mediagrab.extract

enum class Platform { TWITTER, INSTAGRAM, FACEBOOK, YOUTUBE, TIKTOK }

enum class MediaType { IMAGE, VIDEO, GIF, AUDIO }

/** One downloadable rendition of a media item (e.g. a single bitrate of a video). */
data class Variant(
    val url: String,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    /** Set when [url] is video-only and this separate audio track must be merged in after download. */
    val audioUrl: String? = null,
) {
    val label: String
        get() = when {
            mimeType.startsWith("audio") -> if (bitrate > 0) "Audio ${bitrate / 1000} kbps" else "Audio"
            height > 0 -> "${height}p"
            bitrate > 0 -> "${bitrate / 1000} kbps"
            else -> "original"
        }
}

/** One photo/video inside a post; a carousel has several. */
data class MediaItem(
    val type: MediaType,
    val thumbnailUrl: String,
    /** Best quality first. */
    val variants: List<Variant>,
) {
    val best: Variant get() = variants.first()
}

data class MediaPost(
    val platform: Platform,
    val id: String,
    val author: String,
    val caption: String,
    val items: List<MediaItem>,
)

/** A failure the user can act on, with a message suitable for display. */
open class ExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class NotFoundException(message: String) : ExtractionException(message)

class LoginRequiredException(message: String) : ExtractionException(message)

class RateLimitedException(message: String) : ExtractionException(message)

/** Cookies of the user's own web login for one site, taken from the in-app browser. */
interface WebSession {
    /** Name of the cookie that only exists while logged in (e.g. Instagram's "sessionid"). */
    val loginCookie: String

    /** Other cookies that also prove a login (sites don't always set the main one). */
    val altLoginCookies: List<String> get() = emptyList()

    /** The full Cookie header for the site, or null when there are no cookies. */
    fun cookieHeader(): String?

    fun cookie(name: String): String? =
        cookieHeader()?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    fun isLoggedIn(): Boolean = (listOf(loginCookie) + altLoginCookies).any { !cookie(it).isNullOrEmpty() }
}

/**
 * A site-specific resolver from a post link to its media.
 * Adding a new site means adding one implementation and registering it in [Extractors].
 */
interface Extractor {
    val platform: Platform
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String): MediaPost
}

class Extractors(private val all: List<Extractor>) {
    fun find(url: String): Extractor? = all.firstOrNull { it.canHandle(url) }

    suspend fun extract(url: String): MediaPost {
        val extractor = find(url)
            ?: throw ExtractionException("That link isn't an X/Twitter, Instagram, Facebook, YouTube or TikTok post.")
        return extractor.extract(url)
    }
}
