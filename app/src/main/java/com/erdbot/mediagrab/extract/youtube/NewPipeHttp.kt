package com.erdbot.mediagrab.extract.youtube

import com.erdbot.mediagrab.extract.Http
import com.erdbot.mediagrab.extract.WebSession
import com.erdbot.mediagrab.extract.withLogin
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.security.MessageDigest

/**
 * Lets NewPipeExtractor make its requests through the app's OkHttp client. When the user logged
 * in to YouTube, requests to youtube.com carry their session, signed the way youtube.com signs
 * logged-in requests (SAPISIDHASH); nothing else ever gets those cookies.
 */
class NewPipeHttp(private val client: OkHttpClient, private val session: WebSession?) : Downloader() {

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            request.httpMethod() == "POST" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", Http.DESKTOP_UA)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val host = request.url().toHttpUrlOrNull()?.host.orEmpty()
        if (session?.isLoggedIn() == true && (host == "youtube.com" || host.endsWith(".youtube.com"))) {
            val own = request.headers()["Cookie"]?.joinToString("; ").orEmpty()
            val cookies = listOf(own, session.cookieHeader().orEmpty()).filter { it.isNotBlank() }.joinToString("; ")
            val sapisid = session.cookie("SAPISID") ?: session.cookie("__Secure-3PAPISID")
            builder.withLogin(
                "youtube.com",
                mapOf(
                    "Cookie" to cookies,
                    "Authorization" to sapisid?.let { sapisidHash(it) },
                    "X-Origin" to ORIGIN,
                    "X-Goog-AuthUser" to "0",
                ),
            )
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("YouTube asked for a captcha", request.url())
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private const val ORIGIN = "https://www.youtube.com"

        @Volatile private var initialized = false

        fun ensureInitialized(client: OkHttpClient, session: WebSession?) {
            if (initialized) return
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(NewPipeHttp(client, session), Localization("en", "US"))
                    initialized = true
                }
            }
        }

        /** `SAPISIDHASH <ts>_<sha1("<ts> <SAPISID> <origin>")>`, as youtube.com's own scripts compute it. */
        fun sapisidHash(sapisid: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$nowSeconds $sapisid $ORIGIN".toByteArray())
            return "SAPISIDHASH ${nowSeconds}_" + digest.joinToString("") { "%02x".format(it) }
        }
    }
}
