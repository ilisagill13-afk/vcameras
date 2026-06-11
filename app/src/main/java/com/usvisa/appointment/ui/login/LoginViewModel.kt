package com.usvisa.appointment.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.FacilityFromPage
import com.usvisa.appointment.data.repository.RepoResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
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

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = "")
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = "")
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Please enter email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")

            when (val result = repository.login(state.email, state.password)) {
                is RepoResult.Success -> {
                    val loginResult = result.data
                    // Auto-set facility ID if only one consulate found on the page
                    if (loginResult.facilities.size == 1) {
                        val f = loginResult.facilities.first()
                        prefsManager.saveAppointmentSettings(
                            facilityId = f.id,
                            facilityName = f.name,
                            startDate = "",
                            endDate = "",
                            intervalMinutes = 5,
                            autoBook = true,
                            notifyOnFound = true,
                            manualScheduleId = "",
                            manualFacilityId = ""
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        scheduleId = loginResult.scheduleId,
                        detectedFacilities = loginResult.facilities,
                        error = ""
                    )
                }
                is RepoResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
