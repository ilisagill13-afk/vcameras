package com.usvisa.appointment.data.api

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import java.util.concurrent.TimeUnit

// Global proxy holder — updated when user saves proxy settings
object ProxyConfig {
    @Volatile var proxy: Proxy = Proxy.NO_PROXY
}

class PersistentCookieJar(private val context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    init { loadFromPrefs() }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = this.cookies.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            hostCookies.removeAll { it.name == newCookie.name }
            hostCookies.add(newCookie)
        }
        saveToPrefs(url.host, hostCookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = cookies[url.host]?.toMutableList() ?: mutableListOf()

        // Pull fresh cookies from the WebView CookieManager on every request.
        // WebView cookies ALWAYS win over stored — the stored jar may hold an old
        // expired _yatri_session from a previous run that wasn't yet overwritten.
        // Pass the full URL (scheme+host+path, no query) so path-restricted cookies
        // like _yatri_session (path=/en-ca/niv/) are correctly included.
        val cookieUrl = "${url.scheme}://${url.host}${url.encodedPath}"
        val webViewStr = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(cookieUrl)
        }.getOrNull().orEmpty()

        webViewStr.split(";").forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val name = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (name.isNotEmpty()) {
                    runCatching {
                        Cookie.Builder()
                            .name(name).value(value)
                            .domain(url.host).path("/")
                            .build()
                    }.getOrNull()?.let { wvc ->
                        stored.removeAll { it.name == wvc.name }
                        stored.add(wvc)
                    }
                }
            }
        }
        return stored
    }

    private fun saveToPrefs(host: String, cookies: List<Cookie>) {
        prefs.edit()
            .putStringSet("cookies_$host", cookies.map { "${it.name}=${it.value}" }.toSet())
            .apply()
    }

    private fun loadFromPrefs() {
        prefs.all.keys.filter { it.startsWith("cookies_") }.forEach { key ->
            val host = key.removePrefix("cookies_")
            val parsed = (prefs.getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { str ->
                val idx = str.indexOf('=')
                if (idx > 0) {
                    Cookie.Builder()
                        .name(str.substring(0, idx))
                        .value(str.substring(idx + 1))
                        .domain(host)
                        .path("/")
                        .build()
                } else null
            }
            if (parsed.isNotEmpty()) cookies[host] = parsed.toMutableList()
        }
    }

    fun getSessionCookie(host: String): String =
        cookies[host]?.firstOrNull { it.name == "_yatri_session" }?.value ?: ""

    fun clearCookies() {
        cookies.clear()
        prefs.edit().clear().apply()
    }
}

class ApiClient(private val context: Context) {

    companion object {
        const val BASE_URL = "https://ais.usvisa-info.com/en-ca/niv/"
        private const val TAG = "ApiClient"
    }

    val cookieJar = PersistentCookieJar(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(ProxyConfig.proxy)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Log.w(TAG, "Proxy connect failed: $uri $ioe")
            }
        })
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                // Only set default Accept if the request doesn't already have one
                // (login sets its own application/json Accept via @Headers)
                .apply {
                    if (original.header("Accept") == null) {
                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    }
                }
                // Use request-specific Referer if already set, else default to sign-in page
                .apply {
                    if (original.header("Referer") == null)
                        header("Referer", "https://ais.usvisa-info.com/en-ca/niv/users/sign_in")
                }
            chain.proceed(builder.build())
        }
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)      // Don't follow 302s — a redirect means session expired,
        .followSslRedirects(false)   // and following it would overwrite valid cookies with a
                                     // guest session from the login page Set-Cookie header.
        .build()

    val service: VisaApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(VisaApiService::class.java)
}
