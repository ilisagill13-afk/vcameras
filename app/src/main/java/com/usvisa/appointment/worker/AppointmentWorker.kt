package com.usvisa.appointment.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.data.repository.AppointmentRepository
import com.usvisa.appointment.data.repository.RepoResult
import com.usvisa.appointment.notification.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AppointmentWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AppointmentWorker"
        const val WORK_NAME = "visa_appointment_monitor"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"

        fun schedule(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AppointmentWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                (intervalMinutes / 2).coerceAtLeast(1), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_INTERVAL_MINUTES to intervalMinutes))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Scheduled periodic work every $intervalMinutes minutes")
        }

        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AppointmentWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork("${WORK_NAME}_immediate")
            Log.d(TAG, "Cancelled monitoring work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker executing check...")

        val prefsManager = PreferencesManager(context)
        val settings = prefsManager.settingsFlow.first()

        if (!settings.isLoggedIn) {
            Log.d(TAG, "Not logged in, skipping check")
            return Result.failure()
        }

        if (settings.startDate.isEmpty() || settings.endDate.isEmpty()) {
            Log.d(TAG, "Date range not configured, skipping")
            return Result.failure()
        }

        val repository = AppointmentRepository.getInstance(context)

        return try {
            val result = repository.checkAndBookSlots()

            when (result) {
                is RepoResult.Success -> {
                    Log.d(TAG, "Success: ${result.data}")
                    val isBooked = result.data.contains("at") && !result.data.startsWith("Found")

                    if (isBooked && settings.autoBook) {
                        // Appointment was booked successfully
                        val parts = result.data.split(" at ")
                        val date = parts.getOrElse(0) { result.data }
                        val time = parts.getOrElse(1) { "" }
                        NotificationHelper.notifyBookingSuccess(
                            context, date, time, settings.facilityName
                        )
                        // Cancel further monitoring since appointment is booked
                        cancel(context)
                    } else if (!isBooked) {
                        // Slots found but not booked (notify-only mode)
                        val earliestDate = result.data.substringAfter("Earliest: ")
                        NotificationHelper.notifySlotFound(context, earliestDate, settings.facilityName)
                    }
                    Result.success()
                }
                is RepoResult.Error -> {
                    Log.d(TAG, "No slots found or error: ${result.message}")
                    // Don't notify on "no slots" errors — that's expected
                    if (result.message.contains("error", ignoreCase = true) &&
                        !result.message.contains("No available slots") &&
                        !result.message.contains("No slots available")) {
                        Log.e(TAG, "Unexpected error: ${result.message}")
                    }
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            Result.retry()
        }
    }
}
