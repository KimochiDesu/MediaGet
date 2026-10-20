package com.erdbot.mediagrab

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.erdbot.mediagrab.download.DownloadEngine
import com.erdbot.mediagrab.download.DownloadService
import com.erdbot.mediagrab.download.DownloadStore
import com.erdbot.mediagrab.extract.Extractors
import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.facebook.FacebookExtractor
import com.erdbot.mediagrab.extract.instagram.InstagramExtractor
import com.erdbot.mediagrab.extract.tiktok.TikTokExtractor
import com.erdbot.mediagrab.extract.twitter.TwitterExtractor
import com.erdbot.mediagrab.extract.youtube.YouTubeExtractor
import com.erdbot.mediagrab.ui.WebSessions
import com.erdbot.mediagrab.update.Updater

/** Holds the app-wide singletons. */
class App : Application(), ImageLoaderFactory {

    val http by lazy { Http() }

    val extractors by lazy {
        Extractors(
            listOf(
                TwitterExtractor(WebSessions.x, http),
                InstagramExtractor(WebSessions.instagram, http),
                FacebookExtractor(WebSessions.facebook, http),
                YouTubeExtractor(WebSessions.youtube, http),
                TikTokExtractor(http),
            ),
        )
    }

    val downloads by lazy { DownloadStore(this) }

    val settings by lazy { Settings(this) }

    val engine by lazy { DownloadEngine(this, http.client) }

    val updater by lazy { Updater(this, http.client) }

    override fun onCreate() {
        super.onCreate()
        DownloadService.createChannel(this)
    }

    /** Lets the Media tab show thumbnails of downloaded videos straight from the files. */
    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
