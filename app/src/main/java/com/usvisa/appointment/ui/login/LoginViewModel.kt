package com.usvisa.appointment.ui.login

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.api.WebViewFetcher
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.FacilityFromPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val showWebView: Boolean = false,
    val error: String = "",
    val isLoggedIn: Boolean = false,
    val scheduleId: String = "",
    val detectedFacilities: List<FacilityFromPage> = emptyList(),
    val autoLoggingIn: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val repository = AppointmentRepository.getInstance(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = prefsManager.settingsFlow.first()
            _uiState.value = _uiState.value.copy(
                email = settings.email,
                password = settings.password,
                isLoggedIn = settings.isLoggedIn,
                scheduleId = settings.scheduleId
            )
            // If credentials are already saved, skip the form and go straight to WebView login
            if (settings.email.isNotEmpty() && settings.password.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(autoLoggingIn = true)
                startWebViewLogin()
            }
        }
    }

    fun onEmailChange(email: String) = _uiState.value.let {
        _uiState.value = it.copy(email = email, error = "")
    }

    fun onPasswordChange(password: String) = _uiState.value.let {
        _uiState.value = it.copy(password = password, error = "")
    }

    // Show the WebView overlay to handle Cloudflare + auto-login
    fun startWebViewLogin() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter email and password")
            return
        }
        _uiState.value = state.copy(showWebView = true, error = "", isLoading = true)
    }

    // Called by WebViewLoginScreen when login succeeds
    fun onWebViewLoginSuccess(scheduleId: String, csrfToken: String, cookieHeader: String) {
        viewModelScope.launch {
            // Save credentials first so reLogin() can use them if the cookie transfer fails
            prefsManager.saveLoginInfo(_uiState.value.email, _uiState.value.password)

            // Import WebView cookies into OkHttp so API calls work
            importWebViewCookies(cookieHeader)

            // Prime the hidden WebViewFetcher with the fresh session cookies so
            // background monitoring API calls use Chromium TLS (bypasses Cloudflare JA3)
            WebViewFetcher.getInstance(getApplication()).initAfterLogin()

            // Persist session
            prefsManager.saveSessionData(
                scheduleId = scheduleId,
                sessionCookie = extractSessionCookie(cookieHeader),
                csrfToken = csrfToken
            )

            // Fallback: if getCookie() missed the session (renderer sync delay), use the
            // API-based re-login while cf_clearance is still fresh in the OkHttp jar.
            if (repository.apiClient.cookieJar.sessionCookieOverride.isEmpty()) {
                android.util.Log.w("LoginVM", "No session cookie captured — attempting API re-login")
                repository.reLogin()
            }

            // Try to load appointment page to detect facility IDs
            val facilities = if (scheduleId.isNotEmpty()) {
                tryDetectFacilities(scheduleId)
            } else emptyList()

            // Auto-set facility if only one found
            if (facilities.size == 1) {
                val f = facilities.first()
                prefsManager.saveAppointmentSettings(
                    facilityId = f.id,
                    facilityName = f.name,
                    startDate = "", endDate = "",
                    intervalSeconds = 30,
                    autoBook = true,
                    notifyOnFound = true,
                    manualScheduleId = "",
                    manualFacilityId = ""
                )
            }

            _uiState.value = _uiState.value.copy(
                showWebView = false,
                isLoading = false,
                isLoggedIn = true,
                scheduleId = scheduleId,
                detectedFacilities = facilities,
                error = ""
            )
        }
    }

    // Called by WebViewLoginScreen when login fails
    fun onWebViewLoginError(error: String) {
        _uiState.value = _uiState.value.copy(
            showWebView = false,
            isLoading = false,
            autoLoggingIn = false,
            error = error
        )
    }

    private fun importWebViewCookies(cookieHeader: String) {
        val host = "ais.usvisa-info.com"
        val httpUrl = "https://$host/".toHttpUrl()

        // Read cookies from multiple paths so path-scoped cookies (_yatri_session) are captured.
        // Later sources in the list win on name collision; niv path is last so it takes priority.
        val sources = mutableListOf(cookieHeader)
        for (path in listOf("https://$host/", "https://$host/en-ca/niv/")) {
            runCatching { CookieManager.getInstance().getCookie(path) }
                .getOrNull()?.let { if (it.isNotEmpty()) sources.add(it) }
        }

        val merged = linkedMapOf<String, String>()
        sources.forEach { src ->
            src.split(";").forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val name = part.substring(0, idx).trim()
                    if (name.isNotEmpty()) merged[name] = part.substring(idx + 1).trim()
                }
            }
        }
        android.util.Log.d("LoginVM", "importWebViewCookies: merged keys=${merged.keys.joinToString()}, hasSession=${merged.containsKey("_yatri_session")}")
        if (merged.isEmpty()) return

        val cookies = merged.mapNotNull { (name, value) ->
            runCatching {
                Cookie.Builder().name(name).value(value).domain(host).path("/").build()
            }.getOrNull()
        }
        if (cookies.isNotEmpty()) {
            repository.apiClient.cookieJar.saveFromResponse(httpUrl, cookies)
        }

        // Pin the session so no subsequent saveFromResponse call (e.g. from a 302 redirect
        // response during tryDetectFacilities) can overwrite it in loadForRequest.
        val session = merged["_yatri_session"].orEmpty()
        if (session.isNotEmpty()) {
            repository.apiClient.cookieJar.sessionCookieOverride = session
        }
    }

    private fun extractSessionCookie(cookieHeader: String): String {
        return cookieHeader.split(";")
            .firstOrNull { it.trim().startsWith("_yatri_session=") }
            ?.substringAfter("=")?.trim() ?: ""
    }

    private suspend fun tryDetectFacilities(scheduleId: String): List<FacilityFromPage> {
        return try {
            val resp = repository.apiClient.service.getAppointmentPage(scheduleId)
            val html = resp.body()?.string() ?: return emptyList()
            repository.extractFacilitiesFromPage(html)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
