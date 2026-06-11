package com.usvisa.appointment.worker

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.usvisa.appointment.notification.NotificationHelper

class AppointmentForegroundService : Service() {

    companion object {
        private const val TAG = "ForegroundService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting foreground service")
                val notification = NotificationHelper.buildMonitoringNotification(
                    this,
                    "Monitoring for available visa appointment slots..."
                )
                startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        NotificationHelper.cancelMonitoringNotification(this)
    }
}
