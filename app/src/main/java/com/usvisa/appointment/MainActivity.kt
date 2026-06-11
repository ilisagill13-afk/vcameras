package com.usvisa.appointment

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.usvisa.appointment.data.preferences.PreferencesManager
import com.usvisa.appointment.notification.NotificationHelper
import com.usvisa.appointment.ui.navigation.AppNavGraph
import com.usvisa.appointment.ui.navigation.Routes
import com.usvisa.appointment.ui.theme.USVisaAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createChannels(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefsManager = PreferencesManager(this)
        val isLoggedIn = runBlocking { prefsManager.settingsFlow.first().isLoggedIn }
        val startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN

        setContent {
            USVisaAppTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
