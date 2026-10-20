package com.erdbot.mediagrab.ui

import android.webkit.CookieManager
import com.erdbot.mediagrab.extract.WebSession

/**
 * A site login held in the in-app browser's cookie store (app-private storage).
 * The user types their password into the site's own page; the app only reuses the cookies.
 */
class CookieManagerSession(
    private val origin: String,
    private val domain: String,
    override val loginCookie: String,
    override val altLoginCookies: List<String> = emptyList(),
) : WebSession {

    override fun cookieHeader(): String? = CookieManager.getInstance().getCookie(origin)

    /** Expires every cookie of this site only, so logging out of one site keeps the other. */
    fun logout() {
        val manager = CookieManager.getInstance()
        cookieHeader()?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$domain")
            manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
        }
        manager.flush()
    }
}

object WebSessions {
    val instagram = CookieManagerSession("https://www.instagram.com", ".instagram.com", "sessionid")
    val facebook = CookieManagerSession("https://www.facebook.com", ".facebook.com", "c_user")
    val x = CookieManagerSession("https://x.com", ".x.com", "auth_token")
    // Google's mobile sign-in doesn't always set LOGIN_INFO; SAPISID is always set for a signed-in youtube.com.
    val youtube = CookieManagerSession(
        "https://www.youtube.com", ".youtube.com", "LOGIN_INFO", listOf("SAPISID", "__Secure-3PAPISID"),
    )
}
