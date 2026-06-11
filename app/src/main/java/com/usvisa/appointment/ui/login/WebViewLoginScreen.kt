package com.usvisa.appointment.ui.login

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val TAG = "WebViewLogin"
private const val AIS_HOST = "ais.usvisa-info.com"
private const val LOGIN_URL = "https://$AIS_HOST/en-ca/niv/users/sign_in"

@Composable
fun WebViewLoginScreen(
    email: String,
    password: String,
    onSuccess: (scheduleId: String, csrfToken: String, cookieHeader: String) -> Unit,
    onError: (String) -> Unit
) {
    var statusText by remember { mutableStateOf("Loading login page…") }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        // Real Chrome user-agent — passes Cloudflare bot check
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.6367.82 Mobile Safari/537.36"
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {

                        override fun onPageFinished(view: WebView, url: String) {
                            Log.d(TAG, "Page: $url")
                            when {
                                // ── Login page loaded → auto-fill + submit ──────
                                isLoginPage(url) -> {
                                    statusText = "Filling credentials…"
                                    view.evaluateJavascript(buildFillScript(email, password)) { res ->
                                        Log.d(TAG, "Fill: $res")
                                        view.postDelayed({ submitForm(view) }, 900)
                                    }
                                }

                                // ── Landed on account/groups/schedule page ─────
                                isSuccessPage(url) -> {
                                    statusText = "Logged in — reading account…"
                                    view.evaluateJavascript(EXTRACT_JS) { raw ->
                                        handleExtractResult(raw, url, onSuccess, onError)
                                    }
                                }

                                // ── Back to login with error params ────────────
                                isLoginError(url) -> {
                                    view.evaluateJavascript(
                                        "document.querySelector('.alert-error,.error-message,#flash_error')?.innerText||''"
                                    ) { msg ->
                                        val clean = msg.trim().unquote().ifEmpty { "Invalid email or password" }
                                        onError(clean)
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView, request: WebResourceRequest, error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                onError("Network error: ${error.description}")
                            }
                        }
                    }

                    loadUrl(LOGIN_URL)
                }
            }
        )

        // Bottom status bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xDD000000))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4FC3F7)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusText, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun isLoginPage(url: String) =
    url.contains("/users/sign_in") && !url.contains("?")

private fun isSuccessPage(url: String) =
    url.contains("/groups/") || url.contains("/schedule/") || url.contains("/niv/dashboard")

private fun isLoginError(url: String) =
    url.contains("sign_in") && (url.contains("?") || url.contains("error"))

private fun submitForm(view: WebView) {
    view.evaluateJavascript("""
        (function(){
            var btn = document.querySelector('input[type="submit"],button[type="submit"],.commit');
            if(btn){btn.click();return 'clicked';}
            var form = document.querySelector('form');
            if(form){form.submit();return 'submitted';}
            return 'no-btn';
        })()
    """.trimIndent()) { r -> Log.d(TAG, "Submit: $r") }
}

private fun buildFillScript(email: String, password: String): String {
    val safeEmail = email.replace("'", "\\'")
    val safePass = password.replace("'", "\\'")
    return """
        (function(){
            var e = document.getElementById('user_email')
                ||document.querySelector('input[type="email"]')
                ||document.querySelector('input[name="user[email]"]');
            var p = document.getElementById('user_password')
                ||document.querySelector('input[type="password"]');
            var c = document.querySelector('input[name="policy_confirmed"]');
            if(e) e.value='$safeEmail';
            if(p) p.value='$safePass';
            if(c) c.checked=true;
            [e,p,c].forEach(function(el){
                if(!el) return;
                ['input','change'].forEach(function(t){
                    el.dispatchEvent(new Event(t,{bubbles:true}));
                });
            });
            return 'filled';
        })()
    """.trimIndent()
}

// Extracts schedule ID + CSRF token from the post-login page
private const val EXTRACT_JS = """
    (function(){
        var csrf='';
        var m=document.querySelector('meta[name="csrf-token"]');
        if(m) csrf=m.getAttribute('content')||'';

        var sid='';
        // From links on groups page
        var links=document.querySelectorAll('a[href*="/schedule/"]');
        for(var i=0;i<links.length;i++){
            var lm=links[i].href.match('/schedule/(\\d+)/');
            if(lm){sid=lm[1];break;}
        }
        // From current URL
        if(!sid){
            var um=window.location.href.match('/schedule/(\\d+)/');
            if(um) sid=um[1];
        }
        // From page HTML text
        if(!sid){
            var hm=document.body.innerHTML.match('/schedule/(\\d+)/');
            if(hm) sid=hm[1];
        }

        return JSON.stringify({csrf:csrf,scheduleId:sid,url:window.location.href});
    })()
"""

private fun handleExtractResult(
    raw: String,
    fallbackUrl: String,
    onSuccess: (String, String, String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val json = raw.trim().unquote().unescape()
        val obj = JSONObject(json)
        val csrf = obj.optString("csrf", "")
        var scheduleId = obj.optString("scheduleId", "")
        val pageUrl = obj.optString("url", fallbackUrl)

        // Try schedule ID from URL as last resort
        if (scheduleId.isEmpty()) {
            scheduleId = Regex("/schedule/(\\d+)/").find(pageUrl)?.groupValues?.get(1) ?: ""
        }

        val cookies = CookieManager.getInstance().getCookie("https://$AIS_HOST") ?: ""
        Log.d(TAG, "scheduleId=$scheduleId csrf=${csrf.take(15)}… cookies=${cookies.take(40)}…")

        onSuccess(scheduleId, csrf, cookies)
    } catch (e: Exception) {
        Log.e(TAG, "Extract error", e)
        // Login did succeed even if parsing failed — return empty schedule ID,
        // user can fill it manually in Settings
        val cookies = CookieManager.getInstance().getCookie("https://$AIS_HOST") ?: ""
        onSuccess("", "", cookies)
    }
}

private fun String.unquote() = removePrefix("\"").removeSuffix("\"")
private fun String.unescape() = replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "")
