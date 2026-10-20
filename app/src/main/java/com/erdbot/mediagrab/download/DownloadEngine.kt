package com.erdbot.mediagrab.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Http
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Downloads one record into Downloads/MediaGrab through MediaStore (no storage permission needed),
 * merging a separate audio track with MediaMuxer when the record has one.
 */
class DownloadEngine(private val context: Context, private val client: OkHttpClient) {

    private val resolver = context.contentResolver

    suspend fun run(record: DownloadRecord, onProgress: (Float) -> Unit): Uri {
        val uri = createEntry(record.fileName, record.mimeType)
        try {
            if (record.audioUrl == null) {
                resolver.openOutputStream(uri)!!.use { out -> fetch(record.url, out) { onProgress(it) } }
            } else {
                downloadAndMerge(record, uri, onProgress)
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private suspend fun downloadAndMerge(record: DownloadRecord, uri: Uri, onProgress: (Float) -> Unit) {
        val video = File(context.cacheDir, "${record.id}.video.mp4")
        val audio = File(context.cacheDir, "${record.id}.audio.m4a")
        try {
            // Video is the bulk of the bytes: weight it 85% / audio 15% for the progress bar.
            video.outputStream().use { fetch(record.url, it) { p -> onProgress(p * 0.85f) } }
            audio.outputStream().use { fetch(record.audioUrl!!, it) { p -> onProgress(0.85f + p * 0.15f) } }
            resolver.openFileDescriptor(uri, "rw")!!.use { fd -> mux(video, audio, fd.fileDescriptor) }
        } finally {
            video.delete()
            audio.delete()
        }
    }

    /**
     * Streams [url] into [out]. googlevideo.com throttles single long requests, so YouTube
     * files are fetched in 8 MB ranges, the same way YouTube's own player does.
     */
    private suspend fun fetch(url: String, out: OutputStream, onProgress: (Float) -> Unit) {
        val chunked = "googlevideo.com" in url
        var position = 0L
        var total = -1L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(url).header("User-Agent", Http.DESKTOP_UA)
            if (chunked) request.header("Range", "bytes=$position-${position + CHUNK - 1}")
            client.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) throw ExtractionException("Download failed (HTTP ${response.code}).")
                val body = response.body ?: throw ExtractionException("Download returned no data.")
                if (total < 0) {
                    total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: body.contentLength()
                }
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        position += n
                        if (total > 0) onProgress(position.toFloat() / total) else onProgress(-1f)
                    }
                }
                if (!chunked || response.code != 206 || total <= 0 || position >= total) return
            }
        }
    }

    private fun mux(video: File, audio: File, out: java.io.FileDescriptor) {
        val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sources = listOf(video to "video/", audio to "audio/").map { (file, prefix) ->
            val extractor = MediaExtractor().apply { setDataSource(file.path) }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true
            } ?: throw ExtractionException("Downloaded file has no $prefix track.")
            extractor.selectTrack(track)
            extractor to muxer.addTrack(extractor.getTrackFormat(track))
        }
        try {
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            // Interleave by timestamp so the result plays back smoothly while seeking.
            val active = sources.toMutableList()
            while (active.isNotEmpty()) {
                val (extractor, track) = active.minBy { it.first.sampleTime }
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) {
                    active.remove(extractor to track)
                    continue
                }
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            sources.forEach { it.first.release() }
        }
    }

    private fun createEntry(fileName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MediaGrab")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw ExtractionException("Couldn't create the file in Downloads.")
    }

    private companion object {
        const val CHUNK = 8L * 1024 * 1024
    }
}
