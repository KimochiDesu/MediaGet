package com.erdbot.mediagrab.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Marks a request as carrying the user's login for [domain]. Login headers are only ever sent to
 * that domain (or its subdomains): see [LoginScopeGuard].
 */
class LoginScope(val domain: String)

/** Attaches a site's login headers to a request, scoped so they can't leave that site. */
fun Request.Builder.withLogin(domain: String, headers: Map<String, String?>): Request.Builder {
    tag(LoginScope::class.java, LoginScope(domain))
    headers.forEach { (name, value) -> if (!value.isNullOrEmpty()) header(name, value) }
    return this
}

/**
 * Runs on every network hop, including each redirect OkHttp follows. OkHttp drops Authorization
 * when a redirect changes host but keeps a manually set Cookie header, so without this a redirect
 * off-site would carry the user's session along.
 */
class LoginScopeGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        // Untagged requests never carry a user login (e.g. NewPipe's own YouTube consent cookie).
        val domain = request.tag(LoginScope::class.java)?.domain ?: return chain.proceed(request)
        if (host == domain || host.endsWith(".$domain")) return chain.proceed(request)
        val stripped = request.newBuilder().apply { SENSITIVE.forEach { removeHeader(it) } }.build()
        return chain.proceed(stripped)
    }

    private companion object {
        val SENSITIVE = listOf("Cookie", "X-CSRFToken", "x-csrf-token", "x-twitter-auth-type")
    }
}

/** Thin blocking-to-suspend wrapper so extractors stay readable and testable. */
class Http(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(LoginScopeGuard())
        .build(),
) {
    /** [finalUrl] is where redirects ended, e.g. the real post behind a share link. */
    data class Response(val code: Int, val body: String, val finalUrl: String = "")

    // Metadata requests are spaced out so a single extraction doesn't fire its fallback endpoints
    // back-to-back in a burst no human browser would produce. File downloads don't come through
    // here (they use `client` directly), so pacing never slows an actual download.
    private val pace = Mutex()
    private var lastRequestAt = 0L

    private suspend fun space() = pace.withLock {
        val gap = Random.nextLong(MIN_SPACING_MS, MAX_SPACING_MS)
        val wait = lastRequestAt + gap - System.currentTimeMillis()
        if (wait in 1..MAX_SPACING_MS) delay(wait)
        lastRequestAt = System.currentTimeMillis()
    }

    suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        space()
        try {
            client.newCall(request).execute().use {
                Response(it.code, it.body?.string().orEmpty(), it.request.url.toString())
            }
        } catch (e: IOException) {
            throw ExtractionException("Network error: ${e.message ?: "no connection"}", e)
        }
    }

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        /** Client-hint and language headers that match [DESKTOP_UA] (Chrome 130 on Windows). */
        const val SEC_CH_UA = "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\""
        const val SEC_CH_UA_PLATFORM = "\"Windows\""
        const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

        private const val MIN_SPACING_MS = 400L
        private const val MAX_SPACING_MS = 1100L
    }
}

/** UA, client-hint and language headers every real Chrome request carries, UA-consistent. */
fun Request.Builder.browserIdentity(): Request.Builder = apply {
    header("User-Agent", Http.DESKTOP_UA)
    header("sec-ch-ua", Http.SEC_CH_UA)
    header("sec-ch-ua-mobile", "?0")
    header("sec-ch-ua-platform", Http.SEC_CH_UA_PLATFORM)
    header("Accept-Language", Http.ACCEPT_LANGUAGE)
}

/** Headers Chrome sends when navigating to an HTML page (address bar or a link click). */
fun Request.Builder.asBrowserDocument(): Request.Builder = browserIdentity().apply {
    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
    header("Upgrade-Insecure-Requests", "1")
    header("Sec-Fetch-Site", "none")
    header("Sec-Fetch-Mode", "navigate")
    header("Sec-Fetch-User", "?1")
    header("Sec-Fetch-Dest", "document")
}

/** Headers Chrome sends for an in-page fetch()/XHR. Callers override Sec-Fetch-Site when cross-site. */
fun Request.Builder.asBrowserXhr(): Request.Builder = browserIdentity().apply {
    header("Accept", "*/*")
    header("Sec-Fetch-Site", "same-origin")
    header("Sec-Fetch-Mode", "cors")
    header("Sec-Fetch-Dest", "empty")
}
