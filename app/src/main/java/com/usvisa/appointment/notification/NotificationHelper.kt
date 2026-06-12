package com.usvisa.appointment.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.usvisa.appointment.MainActivity
import com.usvisa.appointment.R

object NotificationHelper {

    private const val CHANNEL_SLOT_FOUND = "slot_found"
    private const val CHANNEL_BOOKING = "booking"
    private const val CHANNEL_MONITORING = "monitoring"

    const val NOTIFICATION_ID_MONITORING = 1001
    const val NOTIFICATION_ID_SLOT_FOUND = 1002
    const val NOTIFICATION_ID_BOOKED = 1003
    const val NOTIFICATION_ID_ERROR = 1004

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                "Monitoring Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background monitoring service status" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SLOT_FOUND,
                "Slot Found",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification when appointment slots are found"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BOOKING,
                "Appointment Booked",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification when appointment is successfully booked"
                enableVibration(true)
            }
        )
    }

    fun buildMonitoringNotification(context: Context, message: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_visa)
            .setContentTitle("Sardarji Visa Scheduler")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifySlotFound(context: Context, date: String, facilityName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SLOT_FOUND)
            .setSmallIcon(R.drawable.ic_visa)
            .setContentTitle("Appointment Slot Available!")
            .setContentText("Slot found on $date at $facilityName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("An appointment slot is available on $date at $facilityName consulate. Opening app to book...")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SLOT_FOUND, notification)
    }

    fun notifyBookingSuccess(context: Context, date: String, time: String, facilityName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BOOKING)
            .setSmallIcon(R.drawable.ic_visa)
            .setContentTitle("Appointment Booked Successfully!")
            .setContentText("$date at $time — $facilityName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your US visa appointment has been booked!\n\nDate: $date\nTime: $time\nLocation: $facilityName\n\nPlease check your email for confirmation.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BOOKED, notification)
    }

    fun notifyBookingError(context: Context, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_BOOKING)
            .setSmallIcon(R.drawable.ic_visa)
            .setContentTitle("Booking Failed")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ERROR, notification)
    }

    fun notifyLoginRequired(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SLOT_FOUND)
            .setSmallIcon(R.drawable.ic_visa)
            .setContentTitle("Re-Login Required")
            .setContentText("Session expired and auto re-login failed. Tap to log in again.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your visa monitor session expired and could not be renewed automatically (Cloudflare protection). Please open the app and log in again to resume monitoring.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ERROR, notification)
    }

    fun cancelMonitoringNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MONITORING)
    }
}
