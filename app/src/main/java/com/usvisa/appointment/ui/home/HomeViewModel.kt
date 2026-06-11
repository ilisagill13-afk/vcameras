package com.usvisa.appointment.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
    val monitoringStatus: MonitoringStatus = MonitoringStatus(),
    val isCheckingNow: Boolean = false,
    val lastCheckResult: String = "",
    val isWorkerRunning: Boolean = false,
    val logMessages: List<String> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsManager = PreferencesManager(application)
    private val repository = AppointmentRepository.getInstance(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val logMessages = mutableListOf<String>()

    init {
        viewModelScope.launch {
            prefsManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        // Observe WorkManager state
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(AppointmentWorker.WORK_NAME)
                .collect { workInfos ->
                    val isRunning = workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.ENQUEUED
                    }
                    _uiState.update { it.copy(isWorkerRunning = isRunning) }
                }
        }
    }

    fun startMonitoring() {
        val settings = _uiState.value.settings
        if (!settings.isLoggedIn) {
            addLog("ERROR: Not logged in")
            return
        }
        if (settings.startDate.isEmpty() || settings.endDate.isEmpty()) {
            addLog("ERROR: Date range not configured. Go to Settings.")
            return
        }

        AppointmentWorker.schedule(getApplication(), settings.checkIntervalMinutes.toLong())

        // Start foreground service for persistent notification
        val serviceIntent = Intent(getApplication(), AppointmentForegroundService::class.java).apply {
            action = AppointmentForegroundService.ACTION_START
        }
        try {
            getApplication<Application>().startForegroundService(serviceIntent)
        } catch (e: Exception) {
            // Fallback if foreground service can't start
        }

        _uiState.update { it.copy(
            monitoringStatus = it.monitoringStatus.copy(isRunning = true)
        )}
        addLog("Monitoring started — checking every ${settings.checkIntervalMinutes} minutes")
        addLog("Date range: ${settings.startDate} → ${settings.endDate}")
        addLog("Consulate: ${settings.facilityName}")
    }

    fun stopMonitoring() {
        AppointmentWorker.cancel(getApplication())

        val serviceIntent = Intent(getApplication(), AppointmentForegroundService::class.java).apply {
            action = AppointmentForegroundService.ACTION_STOP
        }
        try {
            getApplication<Application>().startService(serviceIntent)
        } catch (e: Exception) { }

        _uiState.update { it.copy(
            monitoringStatus = it.monitoringStatus.copy(isRunning = false)
        )}
        addLog("Monitoring stopped")
    }

    fun checkNow() {
        if (_uiState.value.isCheckingNow) return

        _uiState.update { it.copy(isCheckingNow = true) }
        addLog("Checking for available slots...")

        viewModelScope.launch {
            when (val result = repository.checkAndBookSlots()) {
                is RepoResult.Success -> {
                    addLog("✓ ${result.data}")
                    _uiState.update { it.copy(
                        isCheckingNow = false,
                        lastCheckResult = result.data,
                        monitoringStatus = it.monitoringStatus.copy(
                            lastChecked = System.currentTimeMillis(),
                            lastResult = result.data,
                            slotsFound = it.monitoringStatus.slotsFound + 1
                        )
                    )}

                    val settings = _uiState.value.settings
                    if (result.data.contains(" at ") && !result.data.startsWith("Found")) {
                        val parts = result.data.split(" at ")
                        NotificationHelper.notifyBookingSuccess(
                            getApplication(), parts[0], parts.getOrElse(1) { "" }, settings.facilityName
                        )
                        stopMonitoring()
                    } else {
                        NotificationHelper.notifySlotFound(getApplication(), result.data, settings.facilityName)
                    }
                }
                is RepoResult.Error -> {
                    addLog("✗ ${result.message}")
                    _uiState.update { it.copy(
                        isCheckingNow = false,
                        lastCheckResult = result.message,
                        monitoringStatus = it.monitoringStatus.copy(
                            lastChecked = System.currentTimeMillis(),
                            lastResult = result.message,
                            checkCount = it.monitoringStatus.checkCount + 1
                        )
                    )}
                }
            }
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

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        logMessages.add(0, logEntry)
        if (logMessages.size > 50) logMessages.removeLastOrNull()
        _uiState.update { it.copy(logMessages = logMessages.toList()) }
    }
}
