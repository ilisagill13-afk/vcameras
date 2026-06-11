package com.usvisa.appointment.worker

import android.content.Context
import androidx.work.*
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
        // If service is not running but should be, restart it
        if (!AppointmentForegroundService.isRunning.value) {
            AppointmentForegroundService.startService(applicationContext)
        }
        return Result.success()
    }
}
