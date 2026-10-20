package com.erdbot.mediagrab.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import com.erdbot.mediagrab.BuildConfig
import com.erdbot.mediagrab.extract.ExtractionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A newer release on GitHub. */
data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * Checks the GitHub releases of this repo for a newer version and installs its APK through
 * PackageInstaller. Android only accepts the update when it is signed with the same key as the
 * installed app.
 */
class Updater(private val context: Context, private val client: OkHttpClient) {

    /** The latest release when it is newer than this build and has an APK attached, else null. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST_RELEASE)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MediaGrab/${BuildConfig.VERSION_NAME}")
            .build()
        val body = try {
            client.newCall(request).execute().use {
                if (!it.isSuccessful) throw ExtractionException("GitHub answered HTTP ${it.code}.")
                it.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw ExtractionException("Couldn't reach GitHub: ${e.message ?: "no connection"}", e)
        }
        parseRelease(JSONObject(body), BuildConfig.VERSION_NAME)
    }

    /** Downloads the APK into the cache; [onProgress] gets 0..1, or -1 when the size is unknown. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "MediaGrab/${BuildConfig.VERSION_NAME}").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ExtractionException("Update download failed (HTTP ${response.code}).")
                val body = response.body ?: throw ExtractionException("Update download returned no data.")
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
                var done = 0L
                val buffer = ByteArray(64 * 1024)
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw ExtractionException("Update download failed: ${e.message ?: "no connection"}", e)
        }
        file
    }

    /** Android asks the user once to allow MediaGrab to install apps. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands [apk] to the system installer; [UpdateReceiver] shows its confirmation screen. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("MediaGrab.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, UpdateReceiver::class.java),
                // The installer adds its status extras, so the intent must be mutable.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
        apk.delete()
    }

    companion object {
        const val REPO = "KimochiDesu/MediaGrab"
        private const val LATEST_RELEASE = "https://api.github.com/repos/$REPO/releases/latest"

        /** The update described by a GitHub release JSON, if it is newer than [current] and ships an APK. */
        fun parseRelease(release: JSONObject, current: String): AppUpdate? {
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
            if (!isNewer(version, current)) return null
            val assets = release.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
            return AppUpdate(version, release.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        }

        /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); a "-suffix" is ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
