package com.usvisa.appointment.data.api

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class PersistentCookieJar(private val context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, List<Cookie>>()

    init {
        loadFromPrefs()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = this.cookies.getOrDefault(url.host, emptyList()).toMutableList()
        cookies.forEach { newCookie ->
            hostCookies.removeAll { it.name == newCookie.name }
            hostCookies.add(newCookie)
        }
        this.cookies[url.host] = hostCookies
        saveToPrefs(url.host, hostCookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies[url.host] ?: emptyList()
    }

    private fun saveToPrefs(host: String, cookies: List<Cookie>) {
        val editor = prefs.edit()
        val cookieStrings = cookies.map { "${it.name}=${it.value}" }
        editor.putStringSet("cookies_$host", cookieStrings.toSet())
        editor.apply()
    }

    private fun loadFromPrefs() {
        val allKeys = prefs.all.keys
        allKeys.filter { it.startsWith("cookies_") }.forEach { key ->
            val host = key.removePrefix("cookies_")
            val cookieStrings = prefs.getStringSet(key, emptySet()) ?: return@forEach
            val parsedCookies = cookieStrings.mapNotNull { str ->
                val parts = str.split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(host)
                        .path("/")
                        .build()
                } else null
            }
            if (parsedCookies.isNotEmpty()) {
                cookies[host] = parsedCookies
            }
        }
    }

    fun getSessionCookie(host: String): String {
        return cookies[host]
            ?.firstOrNull { it.name == "_yatri_session" }
            ?.value ?: ""
    }

    fun clearCookies() {
        cookies.clear()
        prefs.edit().clear().apply()
    }

    fun getHeaderString(host: String): String {
        return cookies[host]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
    }
}

class ApiClient(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://ais.usvisa-info.com/en-ca/niv/"
        private const val TAG = "ApiClient"
    }

    val cookieJar = PersistentCookieJar(context)

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    private val redirectInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        response
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Referer", "https://ais.usvisa-info.com/en-ca/niv/users/sign_in")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
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
