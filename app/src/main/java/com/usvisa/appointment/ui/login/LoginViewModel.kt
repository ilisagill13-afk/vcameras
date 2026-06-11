package com.usvisa.appointment.ui.login

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val detectedFacilities: List<FacilityFromPage> = emptyList()
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
                isLoggedIn = settings.isLoggedIn,
                scheduleId = settings.scheduleId
            )
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
            // Import WebView cookies into OkHttp so API calls work
            importWebViewCookies(cookieHeader)

            // Persist session
            prefsManager.saveSessionData(
                scheduleId = scheduleId,
                sessionCookie = extractSessionCookie(cookieHeader),
                csrfToken = csrfToken
            )
            prefsManager.saveLoginInfo(_uiState.value.email, _uiState.value.password)

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
            error = error
        )
    }

    private fun importWebViewCookies(cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        val host = "ais.usvisa-info.com"
        val httpUrl = "https://$host/".toHttpUrl()
        val cookies = cookieHeader.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                Cookie.Builder()
                    .name(part.substring(0, idx).trim())
                    .value(part.substring(idx + 1).trim())
                    .domain(host)
                    .path("/")
                    .build()
            } else null
        }
        if (cookies.isNotEmpty()) {
            repository.apiClient.cookieJar.saveFromResponse(httpUrl, cookies)
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
