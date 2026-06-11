package com.usvisa.appointment.data.api

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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
        return cookies[url.host] ?: emptyList()
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
                .header("Referer", "https://ais.usvisa-info.com/en-ca/niv/users/sign_in")
            chain.proceed(builder.build())
        }
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val service: VisaApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(VisaApiService::class.java)
}
