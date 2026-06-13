package com.usvisa.appointment.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Fetches JSON endpoints by loading them directly in a hidden WebView.
 *
 * JavaScript fetch() from an off-screen WebView triggers Cloudflare's XHR/AJAX
 * detection and the connection hangs indefinitely (no error, no response).
 * Loading the URL via loadUrl() looks like a normal page navigation to Cloudflare
 * and goes through using Chromium's TLS stack — both bypass the JA3 fingerprint
 * check that blocks OkHttp.
 */
class WebViewFetcher(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

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

    fun initAfterLogin() {
        val run = Runnable {
            if (webView == null) {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
                Log.d(TAG, "Hidden WebView created")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run()
        else mainHandler.post(run)
    }

    /**
     * Loads [url] directly in the hidden WebView (same as a real page navigation),
     * waits for it to finish, then returns the page body text.
     *
     * Returns:
     * - Raw body text (JSON string) on success
     * - "SESSION_EXPIRED" if redirected to sign_in
     * - "ERROR:..." on load failure
     */
    suspend fun fetchGet(url: String): String {
        if (webView == null) {
            initAfterLogin()
            val deadline = System.currentTimeMillis() + 5_000
            while (webView == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(50)
            }
        }

        return withTimeout(30_000) {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    val wv = webView ?: run {
                        if (cont.isActive) cont.resume("ERROR:no_webview")
                        return@post
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            if (!cont.isActive) return
                            if (pageUrl.contains("sign_in")) {
                                cont.resume("SESSION_EXPIRED:redirect")
                                return
                            }
                            // Extract body text — for JSON responses this is the raw JSON string
                            view.evaluateJavascript(
                                "(function(){try{return document.body?document.body.textContent:'';}catch(e){return 'ERROR:'+e.message;}})()"
                            ) { result ->
                                if (!cont.isActive) return@evaluateJavascript
                                val text = result?.trim()?.let { s ->
                                    if (s.startsWith("\"") && s.endsWith("\""))
                                        s.substring(1, s.length - 1)
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n")
                                            .replace("\\\\", "\\")
                                            .replace("\\/", "/")
                                    else s
                                } ?: "ERROR:null_result"
                                Log.d(TAG, "fetchGet response prefix: ${text.take(80)}")
                                if (cont.isActive) cont.resume(text)
                            }
                        }
                    }

                    Log.d(TAG, "fetchGet loading: $url")
                    wv.loadUrl(url)
                }
            }
        }
    }

    fun reset() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
        instance = null
    }
}
