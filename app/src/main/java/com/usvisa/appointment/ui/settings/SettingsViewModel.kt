package com.usvisa.appointment.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.model.AppSettings
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val facilityId: String = "",
    val facilityName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val intervalSecondsText: String = "30",
    val autoBook: Boolean = true,
    val notifyOnFound: Boolean = true,
    val manualScheduleId: String = "",
    val manualFacilityId: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.settingsFlow.first().let { s ->
                _uiState.value = SettingsUiState(
                    settings = s,
                    facilityId = s.facilityId,
                    facilityName = s.facilityName,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    intervalSecondsText = s.checkIntervalSeconds.toString(),
                    autoBook = s.autoBook,
                    notifyOnFound = s.notifyOnFound,
                    manualScheduleId = s.manualScheduleId,
                    manualFacilityId = s.manualFacilityId
                )
            }
        }
    }

    fun onFacilityIdChange(id: String)          = _uiState.update { it.copy(facilityId = id, isSaved = false) }
    fun onFacilityNameChange(name: String)       = _uiState.update { it.copy(facilityName = name, isSaved = false) }
    fun onStartDateChange(date: String)          = _uiState.update { it.copy(startDate = date, isSaved = false) }
    fun onEndDateChange(date: String)            = _uiState.update { it.copy(endDate = date, isSaved = false) }
    fun onIntervalSecondsChange(text: String)    = _uiState.update { it.copy(intervalSecondsText = text, isSaved = false) }
    fun onAutoBookChange(v: Boolean)             = _uiState.update { it.copy(autoBook = v, isSaved = false) }
    fun onNotifyOnFoundChange(v: Boolean)        = _uiState.update { it.copy(notifyOnFound = v, isSaved = false) }
    fun onManualScheduleIdChange(id: String)     = _uiState.update { it.copy(manualScheduleId = id, isSaved = false) }
    fun onManualFacilityIdChange(id: String)     = _uiState.update { it.copy(manualFacilityId = id, isSaved = false) }

    fun saveSettings() {
        val s = _uiState.value
        val seconds = s.intervalSecondsText.trim().toIntOrNull()?.coerceAtLeast(5) ?: 30
        viewModelScope.launch {
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
