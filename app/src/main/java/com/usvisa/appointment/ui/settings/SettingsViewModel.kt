package com.usvisa.appointment.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.model.AppSettings
import com.usvisa.appointment.data.model.CANADA_FACILITIES
import com.usvisa.appointment.data.model.FacilityOption
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val selectedFacility: FacilityOption = CANADA_FACILITIES[0],
    val startDate: String = "",
    val endDate: String = "",
    val intervalMinutes: Int = 5,
    val autoBook: Boolean = true,
    val notifyOnFound: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.settingsFlow.first().let { settings ->
                val facility = CANADA_FACILITIES.find { it.id == settings.facilityId }
                    ?: CANADA_FACILITIES[0]
                _uiState.value = SettingsUiState(
                    settings = settings,
                    selectedFacility = facility,
                    startDate = settings.startDate,
                    endDate = settings.endDate,
                    intervalMinutes = settings.checkIntervalMinutes,
                    autoBook = settings.autoBook,
                    notifyOnFound = settings.notifyOnFound
                )
            }
        }
    }

    fun onFacilitySelected(facility: FacilityOption) {
        _uiState.update { it.copy(selectedFacility = facility, isSaved = false) }
    }

    fun onStartDateChange(date: String) {
        _uiState.update { it.copy(startDate = date, isSaved = false) }
    }

    fun onEndDateChange(date: String) {
        _uiState.update { it.copy(endDate = date, isSaved = false) }
    }

    fun onIntervalChange(minutes: Int) {
        _uiState.update { it.copy(intervalMinutes = minutes, isSaved = false) }
    }

    fun onAutoBookChange(enabled: Boolean) {
        _uiState.update { it.copy(autoBook = enabled, isSaved = false) }
    }

    fun onNotifyOnFoundChange(enabled: Boolean) {
        _uiState.update { it.copy(notifyOnFound = enabled, isSaved = false) }
    }

    fun saveSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            prefsManager.saveAppointmentSettings(
                facilityId = state.selectedFacility.id,
                facilityName = state.selectedFacility.city,
                startDate = state.startDate,
                endDate = state.endDate,
                intervalMinutes = state.intervalMinutes,
                autoBook = state.autoBook,
                notifyOnFound = state.notifyOnFound
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
