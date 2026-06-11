package com.usvisa.appointment.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed — checking if monitoring should resume")

        CoroutineScope(Dispatchers.IO).launch {
            val settings = PreferencesManager(context).settingsFlow.first()
            if (settings.isLoggedIn &&
                settings.startDate.isNotEmpty() &&
                settings.endDate.isNotEmpty()) {
                Log.d("BootReceiver", "Resuming monitoring after reboot")
                AppointmentForegroundService.startService(context)
            }
        }
    }
}
