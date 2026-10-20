package com.erdbot.mediagrab.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.erdbot.mediagrab.App
import com.erdbot.mediagrab.MainActivity
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.MediaType
import com.erdbot.mediagrab.extract.Variant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/** Runs downloads in the foreground so they finish even if the user leaves the app. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parallel = Semaphore(2)
    private val started = mutableSetOf<String>()
    private var active = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progressNotification("Starting download…", 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val app = application as App
        app.downloads.records.value
            .filter { it.status == DownloadStatus.QUEUED && started.add(it.id) }
            .forEach { record -> launchDownload(app, record) }
        if (active == 0) stopSelfSafely()
        return START_NOT_STICKY
    }

    private fun launchDownload(app: App, record: DownloadRecord) {
        synchronized(this) { active++ }
        scope.launch {
            parallel.withPermit {
                app.downloads.update(record.id) { it.copy(status = DownloadStatus.RUNNING, progress = 0f) }
                var last = 0
                try {
                    val uri = app.engine.run(record) { p ->
                        app.downloads.setProgress(record.id, p)
                        val pct = (p * 100).toInt()
                        if (pct >= last + 5) {
                            last = pct
                            notify(progressNotification(record.fileName, pct))
                        }
                    }
                    val size = runCatching {
                        contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                    }.getOrDefault(0L)
                    app.downloads.update(record.id) {
                        it.copy(status = DownloadStatus.DONE, progress = 1f, contentUri = uri.toString(), error = null, sizeBytes = size)
                    }
                } catch (e: Exception) {
                    app.downloads.update(record.id) {
                        it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            val remaining = synchronized(this@DownloadService) {
                started.remove(record.id) // so a later retry of this record can run again
                --active
            }
            if (remaining == 0) stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)

    private fun progressNotification(text: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_MEDIA, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MediaGrab is downloading")
            .setContentText(text)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }

        /** Adds the item to the Media history and starts downloading it. */
        fun enqueue(context: Context, post: MediaPost, index: Int, variant: Variant) {
            val item = post.items[index]
            val app = context.applicationContext as App
            app.downloads.add(
                DownloadRecord(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName(post, index, variant),
                    mimeType = variant.mimeType,
                    mediaType = if (variant.mimeType.startsWith("audio")) MediaType.AUDIO else item.type,
                    platform = post.platform,
                    author = post.author,
                    caption = post.caption.take(300),
                    url = variant.url,
                    audioUrl = variant.audioUrl,
                    thumbnailUrl = item.thumbnailUrl,
                    quality = if (variant.mimeType.startsWith("audio")) "Audio" else variant.label.takeIf { it != "original" }.orEmpty(),
                ),
            )
            start(context)
        }

        /** Re-queues a failed download (its source link may have expired; fetching again fixes that). */
        fun retry(context: Context, id: String) {
            (context.applicationContext as App).downloads.update(id) {
                it.copy(status = DownloadStatus.QUEUED, progress = 0f, error = null)
            }
            start(context)
        }

        private fun start(context: Context) =
            context.startForegroundService(Intent(context, DownloadService::class.java))

        fun fileName(post: MediaPost, index: Int, variant: Variant): String {
            val ext = when {
                variant.mimeType.startsWith("audio") -> "m4a"
                variant.mimeType.startsWith("video") -> "mp4"
                variant.url.substringBefore('?').endsWith(".png") || "format=png" in variant.url -> "png"
                variant.url.substringBefore('?').endsWith(".webp") -> "webp"
                else -> "jpg"
            }
            val author = post.author.ifEmpty { "unknown" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
            val suffix = if (post.items.size > 1) "_${index + 1}" else ""
            val quality = if (variant.height > 0) "_${variant.height}p" else ""
            return "${post.platform.name.lowercase()}_${author}_${post.id}$suffix$quality.$ext"
        }
    }
}
