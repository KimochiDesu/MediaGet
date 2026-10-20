package com.erdbot.mediagrab.extract.youtube

import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Extractor
import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaItem
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.NotFoundException
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.RateLimitedException
import com.erdbot.mediagrab.extract.Variant
import com.erdbot.mediagrab.extract.WebSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Resolves YouTube videos and Shorts through NewPipeExtractor, which keeps up with YouTube's
 * signature/throttling changes. Above 360p YouTube only serves separate video and audio
 * tracks; those variants carry [Variant.audioUrl] and are merged on the device after download.
 */
class YouTubeExtractor(
    /** The user's youtube.com login (for age-restricted videos); null when not used. */
    private val session: WebSession? = null,
    private val http: Http = Http(),
) : Extractor {

    override val platform = Platform.YOUTUBE

    override fun canHandle(url: String) =
        runCatching { ServiceList.YouTube.streamLHFactory.acceptUrl(url) }.getOrDefault(false)

    override suspend fun extract(url: String): MediaPost = withContext(Dispatchers.IO) {
        NewPipeHttp.ensureInitialized(http.client, session)
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: AgeRestrictedContentException) {
            if (session?.isLoggedIn() == true) {
                throw ExtractionException("This video is age-restricted and YouTube refused it even with your login.")
            }
            throw LoginRequiredException("This video is age-restricted. Log in to YouTube (with an adult account) and try again.")
        } catch (e: ContentNotAvailableException) {
            throw NotFoundException(e.message ?: "This video is unavailable.")
        } catch (e: ReCaptchaException) {
            throw RateLimitedException("YouTube is rate limiting requests. Wait a while and try again.")
        } catch (e: Exception) {
            throw ExtractionException("Couldn't read this YouTube video: ${e.message}", e)
        }

        val audio = info.audioStreams.filter { it.isDirect() && it.format == MediaFormat.M4A }
            .maxByOrNull { it.averageBitrate }
        val muxed = info.videoStreams.filter { it.isDirect() && it.format == MediaFormat.MPEG_4 }
            .map { it.toVariant(null) }
        // MediaMuxer can only write H.264 into MP4, so skip VP9/AV1 video-only tracks.
        val videoOnly = if (audio == null) emptyList() else info.videoOnlyStreams
            .filter { it.isDirect() && it.format == MediaFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc1") }
            .map { it.toVariant(audio.content) }
        val videos = (muxed + videoOnly)
            .sortedWith(compareByDescending<Variant> { it.height }.thenBy { it.audioUrl != null })
            .distinctBy { it.height } // a ready-made stream wins over a merge at the same height
        val audioOnly = audio?.let {
            Variant(it.content, "audio/mp4", bitrate = it.averageBitrate * 1000)
        }
        val variants = videos + listOfNotNull(audioOnly)
        if (variants.isEmpty()) throw ExtractionException("YouTube offered no downloadable formats for this video.")

        MediaPost(
            platform = Platform.YOUTUBE,
            id = info.id,
            author = info.uploaderName.orEmpty(),
            caption = info.name.orEmpty(),
            items = listOf(
                MediaItem(
                    type = if (videos.isEmpty()) MediaType.AUDIO else MediaType.VIDEO,
                    thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
                    variants = variants,
                ),
            ),
        )
    }

    private fun Stream.isDirect() = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun VideoStream.toVariant(audioUrl: String?) =
        Variant(content, "video/mp4", width = width, height = height, bitrate = bitrate, audioUrl = audioUrl)
}
