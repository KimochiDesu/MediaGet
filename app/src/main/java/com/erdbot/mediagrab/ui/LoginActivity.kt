package com.erdbot.mediagrab.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Shows the site's own login page and closes once that site's session cookie appears.
 *
 * Pop-up windows are real windows here: "Sign in with Google/Apple" on X opens one and reports
 * back to the page that opened it; loading it in place instead leaves a blank page.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var frame: FrameLayout
    private val popups = mutableListOf<WebView>()
    private var site = SITE_INSTAGRAM

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            // Sites hide or block third-party sign-in for embedded browsers, which announce
            // themselves with "; wv" and "Version/4.0" in the user agent. Look like regular Chrome.
            userAgentString = WebSettings.getDefaultUserAgent(view.context)
                .replace("; wv", "")
                .replace(Regex("""Version/\d+(\.\d+)* """), "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) =
                LoginDiagnostics.log("page ${LoginDiagnostics.safeUrl(url)}")

            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, response: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame || response.statusCode >= 400) {
                    LoginDiagnostics.log("http ${response.statusCode} ${request.method} ${LoginDiagnostics.safeUrl(request.url.toString())}")
                }
            }

            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) =
                LoginDiagnostics.log("error ${error.errorCode} ${error.description} ${LoginDiagnostics.safeUrl(request.url.toString())}")

            override fun onPageFinished(view: WebView, url: String) = checkLogin()

            // x.com logs in without a full page load; catch its in-page navigations too.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = checkLogin()
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                LoginDiagnostics.log("popup opened (dialog=$isDialog, gesture=$isUserGesture)")
                val popup = WebView(this@LoginActivity)
                configure(popup)
                popups += popup
                frame.addView(popup, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                LoginDiagnostics.log("popup closed by page")
                closePopup(window)
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR ||
                    message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING
                ) {
                    LoginDiagnostics.log("console ${message.messageLevel()} ${message.message()}")
                }
                return true
            }
        }
    }

    private fun closePopup(popup: WebView) {
        frame.removeView(popup)
        popups -= popup
        popup.destroy()
        checkLogin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        site = intent.getStringExtra(EXTRA_SITE) ?: SITE_INSTAGRAM
        CookieManager.getInstance().setAcceptCookie(true)
        if (LoginDiagnostics.enabled) WebView.setWebContentsDebuggingEnabled(true)
        LoginDiagnostics.log("login screen for $site, UA ${WebSettings.getDefaultUserAgent(this)}")
        webView = WebView(this).also(::configure)
        frame = FrameLayout(this).apply { addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }

        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        setContentView(frame)
        webView.loadUrl(
            when (site) {
                SITE_FACEBOOK -> FACEBOOK_LOGIN
                SITE_X -> X_LOGIN
                SITE_YOUTUBE -> YOUTUBE_LOGIN
                else -> INSTAGRAM_LOGIN
            },
        )
    }

    private fun checkLogin() {
        if (LoginDiagnostics.enabled) {
            val origin = when (site) {
                SITE_FACEBOOK -> "https://www.facebook.com"
                SITE_X -> "https://x.com"
                SITE_YOUTUBE -> "https://www.youtube.com"
                else -> "https://www.instagram.com"
            }
            LoginDiagnostics.log(LoginDiagnostics.cookieNames(origin, CookieManager.getInstance().getCookie(origin)))
        }
        when {
            site == SITE_INSTAGRAM && WebSessions.instagram.isLoggedIn() -> done()
            site == SITE_FACEBOOK && WebSessions.facebook.isLoggedIn() -> done()
            // ct0 (X's CSRF token) is set a moment after auth_token; both are needed for requests.
            site == SITE_X && WebSessions.x.isLoggedIn() && WebSessions.x.cookie("ct0") != null -> done()
            site == SITE_YOUTUBE && WebSessions.youtube.isLoggedIn() -> done()
        }
    }

    private fun done() {
        if (isFinishing) return
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val top = popups.lastOrNull()
        when {
            top != null && top.canGoBack() -> top.goBack()
            top != null -> closePopup(top)
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        popups.toList().forEach { it.destroy() }
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SITE = "site"
        const val SITE_INSTAGRAM = "instagram"
        const val SITE_FACEBOOK = "facebook"
        const val SITE_X = "x"
        const val SITE_YOUTUBE = "youtube"
        private const val X_LOGIN = "https://x.com/i/flow/login"
        private const val INSTAGRAM_LOGIN = "https://www.instagram.com/accounts/login/"
        private const val FACEBOOK_LOGIN = "https://m.facebook.com/login/"
        private const val YOUTUBE_LOGIN =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"

        fun intent(context: Context, site: String) =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_SITE, site)
    }
}
