package com.usvisa.appointment.worker

import android.content.Context
import androidx.work.*
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Kept only as a fallback for devices where ForegroundService gets killed.
// The primary monitoring is done by AppointmentForegroundService (coroutine loop).
class AppointmentWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "visa_appointment_watchdog"

        // Watchdog: if service was killed, restart it after 15 minutes
        fun scheduleWatchdog(context: Context) {
            val req = PeriodicWorkRequestBuilder<AppointmentWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val settings = PreferencesManager(applicationContext).settingsFlow.first()
        // Only restart the service if the user is still logged in
        if (!AppointmentForegroundService.isRunning.value && settings.isLoggedIn) {
            AppointmentForegroundService.startService(applicationContext)
        }
        return Result.success()
    }
}
