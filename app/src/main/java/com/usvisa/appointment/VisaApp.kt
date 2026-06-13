package com.usvisa.appointment

import android.app.Application
import com.usvisa.appointment.data.api.WebViewFetcher
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VisaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // If the user is already logged in (app restart), prime the hidden WebViewFetcher
        // immediately so the first background check uses Chromium TLS (not OkHttp/Java TLS).
        CoroutineScope(Dispatchers.Main).launch {
            val settings = PreferencesManager(this@VisaApp).settingsFlow.first()
            if (settings.isLoggedIn) {
                WebViewFetcher.getInstance(this@VisaApp).initAfterLogin()
            }
        }
    }
}
