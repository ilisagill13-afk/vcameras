package com.usvisa.appointment.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Makes HTTP requests via a hidden WebView using the JavaScript fetch() API.
 *
 * OkHttp uses Java's TLS stack which has a different JA3 fingerprint than Chrome.
 * Cloudflare detects this and blocks OkHttp even with valid cf_clearance cookies.
 * This class uses the Android System WebView (Chromium) which has the same TLS
 * fingerprint as Chrome — Cloudflare lets it through.
 *
 * The hidden WebView is loaded on the same origin (ais.usvisa-info.com) so
 * fetch() calls are same-origin and bypass CORS entirely.
 */
class WebViewFetcher(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var siteLoaded = false
    private val seq = AtomicInteger(0)

    companion object {
        private const val TAG = "WebViewFetcher"
        const val SITE = "https://ais.usvisa-info.com/en-ca/niv/"

        @Volatile
        private var instance: WebViewFetcher? = null

        fun getInstance(ctx: Context): WebViewFetcher =
            instance ?: synchronized(this) {
                instance ?: WebViewFetcher(ctx.applicationContext).also { instance = it }
            }
    }

    /**
     * Creates the hidden WebView and loads the base URL so fetch() calls are same-origin.
     * Must be called after the user has logged in (so cf_clearance cookie is present).
     * Safe to call from any thread.
     */
    fun initAfterLogin() {
        val run = Runnable {
            if (webView != null) return@Runnable
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    // Block any redirect to sign_in to prevent cookie pollution
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
                    ): Boolean {
                        if (request.url.toString().contains("sign_in")) {
                            Log.w(TAG, "Hidden WebView blocked redirect to sign_in")
                            siteLoaded = true  // still mark ready; fetch() will detect expired session
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "Hidden WebView loaded: $url")
                        siteLoaded = true
                    }
                }
                loadUrl(SITE)
            }
            webView = wv
            Log.d(TAG, "WebViewFetcher hidden WebView created")
        }

        if (Looper.myLooper() == Looper.getMainLooper()) run.run()
        else mainHandler.post(run)
    }

    /**
     * Waits until the site page is loaded (up to [timeoutMs] ms).
     */
    private suspend fun waitUntilReady(timeoutMs: Long = 15_000) {
        if (siteLoaded) return
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!siteLoaded && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(100)
        }
    }

    /**
     * Performs a GET request via JavaScript fetch() in the hidden WebView.
     *
     * Returns:
     * - The response body text on success
     * - "SESSION_EXPIRED" if the server redirected to login
     * - "ERROR:..." on network/JS error
     */
    suspend fun fetchGet(url: String): String {
        if (webView == null) initAfterLogin()
        waitUntilReady()

        return withTimeout(25_000) {
            suspendCancellableCoroutine { cont ->
                val cbName = "_f${seq.incrementAndGet()}"
                mainHandler.post {
                    val wv = webView ?: run {
                        if (cont.isActive) cont.resume("ERROR:no_webview")
                        return@post
                    }
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun done(data: String) {
                            mainHandler.post { wv.removeJavascriptInterface(cbName) }
                            if (cont.isActive) cont.resume(data)
                        }
                    }, cbName)

                    val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
                    wv.evaluateJavascript("""
                        (function(){
                            fetch('$safeUrl', {
                                credentials: 'include',
                                headers: {
                                    'X-Requested-With': 'XMLHttpRequest',
                                    'Accept': 'application/json'
                                }
                            })
                            .then(function(r) {
                                if (r.status === 401 || r.status === 403) {
                                    $cbName.done('SESSION_EXPIRED:' + r.status);
                                } else if (r.redirected || r.url.indexOf('sign_in') >= 0) {
                                    $cbName.done('SESSION_EXPIRED:redirect');
                                } else {
                                    r.text().then(function(t) { $cbName.done(t); });
                                }
                            })
                            .catch(function(e) { $cbName.done('ERROR:' + e.message); });
                        })();
                    """.trimIndent(), null)
                }
            }
        }
    }

    fun reset() {
        mainHandler.post {
            webView?.destroy()
            webView = null
            siteLoaded = false
        }
        instance = null
    }
}
