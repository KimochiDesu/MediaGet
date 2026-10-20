package com.erdbot.mediagrab.download

import android.content.Context
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED }

data class DownloadRecord(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val platform: Platform,
    val author: String,
    val caption: String,
    val url: String,
    /** Separate audio track to merge into [url] (YouTube above 360p). */
    val audioUrl: String? = null,
    val thumbnailUrl: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** 0..1, or -1 when the size is unknown. */
    val progress: Float = 0f,
    /** content:// Uri of the saved file once done. */
    val contentUri: String? = null,
    val error: String? = null,
    /** Shown in the Media tab, e.g. "1080p" or "Audio". */
    val quality: String = "",
    val sizeBytes: Long = 0,
)

/** The download history behind the Media tab, persisted as JSON in app-private storage. */
class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads.json")
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<DownloadRecord>> = _records

    fun add(record: DownloadRecord) = change { listOf(record) + it }

    fun update(id: String, transform: (DownloadRecord) -> DownloadRecord) =
        change { list -> list.map { if (it.id == id) transform(it) else it } }

    fun remove(id: String) = change { list -> list.filterNot { it.id == id } }

    fun get(id: String): DownloadRecord? = _records.value.firstOrNull { it.id == id }

    /** Progress ticks are frequent and not persisted (progress isn't saved to disk anyway). */
    fun setProgress(id: String, progress: Float) =
        _records.update { list -> list.map { if (it.id == id) it.copy(progress = progress) else it } }

    private fun change(transform: (List<DownloadRecord>) -> List<DownloadRecord>) {
        synchronized(this) {
            _records.update(transform)
            save(_records.value)
        }
    }

    private fun save(list: List<DownloadRecord>) {
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id).put("fileName", r.fileName).put("mimeType", r.mimeType)
                    .put("mediaType", r.mediaType.name).put("platform", r.platform.name)
                    .put("author", r.author).put("caption", r.caption).put("url", r.url)
                    .put("audioUrl", r.audioUrl).put("thumbnailUrl", r.thumbnailUrl)
                    .put("createdAt", r.createdAt).put("status", r.status.name)
                    .put("contentUri", r.contentUri).put("error", r.error)
                    .put("quality", r.quality).put("sizeBytes", r.sizeBytes),
            )
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }

    private fun load(): List<DownloadRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val status = DownloadStatus.valueOf(o.getString("status"))
                DownloadRecord(
                    id = o.getString("id"),
                    fileName = o.getString("fileName"),
                    mimeType = o.getString("mimeType"),
                    mediaType = MediaType.valueOf(o.getString("mediaType")),
                    platform = Platform.valueOf(o.getString("platform")),
                    author = o.optString("author"),
                    caption = o.optString("caption"),
                    url = o.getString("url"),
                    audioUrl = o.optString("audioUrl").takeIf { it.isNotEmpty() && it != "null" },
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    createdAt = o.optLong("createdAt"),
                    // Anything interrupted by the app being killed is shown as failed (retryable).
                    status = if (status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED) DownloadStatus.FAILED else status,
                    progress = if (status == DownloadStatus.DONE) 1f else 0f,
                    contentUri = o.optString("contentUri").takeIf { it.isNotEmpty() && it != "null" },
                    error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" }
                        ?: if (status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED) "Interrupted" else null,
                    quality = o.optString("quality"),
                    sizeBytes = o.optLong("sizeBytes"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
