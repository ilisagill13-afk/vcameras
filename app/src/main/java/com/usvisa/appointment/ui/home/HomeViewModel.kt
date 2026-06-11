package com.usvisa.appointment.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usvisa.appointment.data.model.AppSettings
import com.usvisa.appointment.data.model.MonitoringStatus
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.RepoResult
import com.usvisa.appointment.notification.NotificationHelper
import com.usvisa.appointment.worker.AppointmentForegroundService
import com.usvisa.appointment.worker.AppointmentWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val isServiceRunning: Boolean = false,
    val isCheckingNow: Boolean = false,
    val totalChecks: Int = 0,
    val logMessages: List<String> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val repository = AppointmentRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val localLog = mutableListOf<String>()

    init {
        viewModelScope.launch {
            prefsManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        // Observe service running state
        viewModelScope.launch {
            AppointmentForegroundService.isRunning.collect { running ->
                _uiState.update { it.copy(isServiceRunning = running) }
            }
        }

        // Reflect service log lines in the UI
        viewModelScope.launch {
            AppointmentForegroundService.lastLog
                .filter { it.isNotEmpty() }
                .collect { logLine ->
                    addLog(logLine, fromService = true)
                }
        }

        // Reflect service check count
        viewModelScope.launch {
            AppointmentForegroundService.checkCount.collect { count ->
                _uiState.update { it.copy(totalChecks = count) }
            }
        }
    }

    fun startMonitoring() {
        val settings = _uiState.value.settings
        if (!settings.isLoggedIn) { addLog("ERROR: Not logged in"); return }
        if (settings.startDate.isEmpty() || settings.endDate.isEmpty()) {
            addLog("ERROR: Configure date range in Settings first"); return
        }
        val facilityId = settings.manualFacilityId.ifEmpty { settings.facilityId }
        if (facilityId.isEmpty()) { addLog("ERROR: Set Facility ID in Settings"); return }

        AppointmentForegroundService.startService(getApplication())
        // Watchdog: WorkManager restarts service if it gets killed
        AppointmentWorker.scheduleWatchdog(getApplication())

        val secs = settings.checkIntervalSeconds
        addLog("Started — checking every ${secs}s")
        addLog("Range: ${settings.startDate} → ${settings.endDate}")
        addLog("Consulate ID: $facilityId (${settings.facilityName})")
    }

    fun stopMonitoring() {
        AppointmentForegroundService.stopService(getApplication())
        AppointmentWorker.cancel(getApplication())
        addLog("Monitoring stopped")
    }

    fun checkNow() {
        if (_uiState.value.isCheckingNow) return
        _uiState.update { it.copy(isCheckingNow = true) }
        addLog("Manual check…")

        viewModelScope.launch {
            when (val result = repository.checkAndBookSlots()) {
                is RepoResult.Success -> {
                    addLog("✓ ${result.data}")
                    val settings = _uiState.value.settings
                    val isBooked = result.data.contains(" at ") && !result.data.startsWith("Found")
                    if (isBooked) {
                        val parts = result.data.split(" at ")
                        NotificationHelper.notifyBookingSuccess(
                            getApplication(), parts[0], parts.getOrElse(1) { "" }, settings.facilityName
                        )
                        stopMonitoring()
                    } else {
                        NotificationHelper.notifySlotFound(getApplication(), result.data, settings.facilityName)
                    }
                }
                is RepoResult.Error -> addLog("✗ ${result.message}")
            }
            _uiState.update { it.copy(isCheckingNow = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopMonitoring()
            repository.apiClient.cookieJar.clearCookies()
            prefsManager.clearSession()
            addLog("Logged out")
        }
    }

    private fun addLog(message: String, fromService: Boolean = false) {
        val entry = if (fromService) message  // service already has timestamp
                    else {
                        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        "[$ts] $message"
                    }
        localLog.add(0, entry)
        if (localLog.size > 100) localLog.removeLastOrNull()
        _uiState.update { it.copy(logMessages = localLog.toList()) }
    }
}
