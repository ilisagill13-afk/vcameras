package com.usvisa.appointment.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.api.ProxyConfig
import com.usvisa.appointment.data.model.AppSettings
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.FacilityFromPage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Proxy

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val email: String = "",
    val password: String = "",
    val facilityId: String = "",
    val facilityName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val intervalSecondsText: String = "30",
    val autoBook: Boolean = true,
    val notifyOnFound: Boolean = true,
    val manualScheduleId: String = "",
    val manualFacilityId: String = "",
    val detectedFacilities: List<FacilityFromPage> = emptyList(),
    val isLoadingFacilities: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPortText: String = "8080",
    val proxyType: String = "HTTP"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val repository = AppointmentRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.settingsFlow.first().let { s ->
                val scheduleId = s.manualScheduleId.ifEmpty { s.scheduleId }
                _uiState.value = SettingsUiState(
                    settings = s,
                    email = s.email,
                    password = s.password,
                    facilityId = s.facilityId,
                    facilityName = s.facilityName,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    intervalSecondsText = s.checkIntervalSeconds.toString(),
                    autoBook = s.autoBook,
                    notifyOnFound = s.notifyOnFound,
                    manualScheduleId = s.manualScheduleId,
                    manualFacilityId = s.manualFacilityId,
                    isLoadingFacilities = scheduleId.isNotEmpty(),
                    proxyEnabled = s.proxyEnabled,
                    proxyHost = s.proxyHost,
                    proxyPortText = s.proxyPort.toString(),
                    proxyType = s.proxyType
                )
                applyProxy(s.proxyEnabled, s.proxyHost, s.proxyPort, s.proxyType)
                if (scheduleId.isNotEmpty()) fetchFacilities(scheduleId)
            }
        }
    }

    private fun fetchFacilities(scheduleId: String) {
        viewModelScope.launch {
            try {
                val resp = repository.apiClient.service.getAppointmentPage(scheduleId)
                val html = resp.body()?.string() ?: ""
                val facilities = repository.extractFacilitiesFromPage(html)
                _uiState.update { it.copy(detectedFacilities = facilities, isLoadingFacilities = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFacilities = false) }
            }
        }
    }

    fun onEmailChange(v: String)        = _uiState.update { it.copy(email = v, isSaved = false) }
    fun onPasswordChange(v: String)     = _uiState.update { it.copy(password = v, isSaved = false) }
    fun onProxyEnabledChange(v: Boolean)= _uiState.update { it.copy(proxyEnabled = v, isSaved = false) }
    fun onProxyHostChange(v: String)    = _uiState.update { it.copy(proxyHost = v, isSaved = false) }
    fun onProxyPortChange(v: String)    = _uiState.update { it.copy(proxyPortText = v, isSaved = false) }
    fun onProxyTypeChange(v: String)    = _uiState.update { it.copy(proxyType = v, isSaved = false) }

    fun onFacilitySelected(facility: FacilityFromPage) =
        _uiState.update { it.copy(facilityId = facility.id, facilityName = facility.name, isSaved = false) }

    fun onStartDateChange(date: String)       = _uiState.update { it.copy(startDate = date, isSaved = false) }
    fun onEndDateChange(date: String)         = _uiState.update { it.copy(endDate = date, isSaved = false) }
    fun onIntervalSecondsChange(text: String) = _uiState.update { it.copy(intervalSecondsText = text, isSaved = false) }
    fun onAutoBookChange(v: Boolean)          = _uiState.update { it.copy(autoBook = v, isSaved = false) }
    fun onNotifyOnFoundChange(v: Boolean)     = _uiState.update { it.copy(notifyOnFound = v, isSaved = false) }
    fun onManualScheduleIdChange(id: String)  = _uiState.update { it.copy(manualScheduleId = id, isSaved = false) }
    fun onManualFacilityIdChange(id: String)  = _uiState.update { it.copy(manualFacilityId = id, isSaved = false) }

    private fun applyProxy(enabled: Boolean, host: String, port: Int, type: String) {
        ProxyConfig.proxy = if (enabled && host.isNotBlank()) {
            val proxyType = if (type == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(proxyType, InetSocketAddress(host, port))
        } else {
            Proxy.NO_PROXY
        }
    }

    fun saveSettings() {
        val s = _uiState.value
        val seconds = s.intervalSecondsText.trim().toIntOrNull()?.coerceAtLeast(5) ?: 30
        val proxyPort = s.proxyPortText.trim().toIntOrNull()?.coerceIn(1, 65535) ?: 8080
        viewModelScope.launch {
            if (s.email.isNotBlank() && s.password.isNotBlank()) {
                prefsManager.saveLoginInfo(s.email, s.password)
            }
            prefsManager.saveProxySettings(s.proxyEnabled, s.proxyHost, proxyPort, s.proxyType)
            applyProxy(s.proxyEnabled, s.proxyHost, proxyPort, s.proxyType)
            prefsManager.saveAppointmentSettings(
                facilityId = s.facilityId,
                facilityName = s.facilityName,
                startDate = s.startDate,
                endDate = s.endDate,
                intervalSeconds = seconds,
                autoBook = s.autoBook,
                notifyOnFound = s.notifyOnFound,
                manualScheduleId = s.manualScheduleId,
                manualFacilityId = s.manualFacilityId
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
