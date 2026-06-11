package com.usvisa.appointment.worker

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.RepoResult
import com.usvisa.appointment.notification.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class AppointmentForegroundService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        // Shared state so HomeViewModel can observe it
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastLog = MutableStateFlow("")
        val lastLog: StateFlow<String> = _lastLog.asStateFlow()

        private val _checkCount = MutableStateFlow(0)
        val checkCount: StateFlow<Int> = _checkCount.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, AppointmentForegroundService::class.java)
                .apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AppointmentForegroundService::class.java)
                .apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            // null intent = service restarted by system (START_STICKY)
            null -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        val notification = NotificationHelper.buildMonitoringNotification(
            this, "Starting slot monitoring..."
        )
        startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
        _isRunning.value = true
        _checkCount.value = 0

        val prefs = PreferencesManager(this)
        val repo = AppointmentRepository.getInstance(this)

        monitoringJob = serviceScope.launch {
            while (isActive) {
                val settings = prefs.settingsFlow.first()
                val intervalSec = settings.checkIntervalSeconds.coerceAtLeast(5)

                updateNotification("Checking slots…")
                log("Checking for slots…")

                try {
                    when (val result = repo.checkAndBookSlots()) {
                        is RepoResult.Success -> {
                            val data = result.data
                            _checkCount.value++
                            val isBooked = data.contains(" at ") && !data.startsWith("Found")

                            if (isBooked && settings.autoBook) {
                                val parts = data.split(" at ")
                                val date = parts[0]
                                val time = parts.getOrElse(1) { "" }
                                log("✓ BOOKED: $data")
                                NotificationHelper.notifyBookingSuccess(
                                    this@AppointmentForegroundService,
                                    date, time, settings.facilityName
                                )
                                stopMonitoring()
                                return@launch
                            } else {
                                log("✓ Slot found: $data")
                                if (settings.notifyOnFound) {
                                    val earliest = data.substringAfter("Earliest: ")
                                    NotificationHelper.notifySlotFound(
                                        this@AppointmentForegroundService,
                                        earliest, settings.facilityName
                                    )
                                }
                                updateNotification("Slot found: $data")
                            }
                        }
                        is RepoResult.Error -> {
                            _checkCount.value++
                            val msg = result.message
                            log("✗ $msg")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Check error", e)
                    log("Error: ${e.message}")
                }

                // Randomise wait: ±(intervalSec/3) jitter, but never below configured minimum
                val jitterRange = (intervalSec / 3).coerceAtLeast(2)
                val jitter = Random.nextInt(-jitterRange, jitterRange + 1)
                val actualDelay = (intervalSec + jitter).coerceAtLeast(intervalSec).toLong()
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                updateNotification("[$timeStr] Waiting ${actualDelay}s (next check)…")
                log("Next check in ${actualDelay}s")
                delay(actualDelay * 1000L)
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationHelper.buildMonitoringNotification(this, text)
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
    }

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _lastLog.value = "[$ts] $msg"
        Log.d(TAG, msg)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        serviceScope.cancel()
        _isRunning.value = false
    }
}
