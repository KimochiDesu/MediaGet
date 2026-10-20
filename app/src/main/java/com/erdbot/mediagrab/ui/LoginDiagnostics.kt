package com.erdbot.mediagrab.ui

import android.util.Log
import com.erdbot.mediagrab.BuildConfig

/**
 * Debug builds only: traces the login pages so login problems can be diagnosed from logcat.
 * Never logs cookie values, form data or URL queries (which can carry one-time codes).
 */
object LoginDiagnostics {
    private const val TAG = "MediaGrabLogin"

    val enabled get() = BuildConfig.DEBUG

    fun log(message: String) {
        if (enabled) Log.d(TAG, message.take(400))
    }

    /** scheme://host/path, without the query or fragment. */
    fun safeUrl(url: String?): String = url.orEmpty().substringBefore('?').substringBefore('#')

    fun cookieNames(origin: String, header: String?): String =
        "$origin cookies: " + header.orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.sorted()
}
