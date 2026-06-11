package com.usvisa.appointment.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.usvisa.appointment.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "visa_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_SCHEDULE_ID = stringPreferencesKey("schedule_id")
        val KEY_FACILITY_ID = stringPreferencesKey("facility_id")
        val KEY_FACILITY_NAME = stringPreferencesKey("facility_name")
        val KEY_START_DATE = stringPreferencesKey("start_date")
        val KEY_END_DATE = stringPreferencesKey("end_date")
        val KEY_INTERVAL_SECONDS = intPreferencesKey("check_interval_seconds")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val KEY_CSRF_TOKEN = stringPreferencesKey("csrf_token")
        val KEY_AUTO_BOOK = booleanPreferencesKey("auto_book")
        val KEY_NOTIFY_ON_FOUND = booleanPreferencesKey("notify_on_found")
        val KEY_MANUAL_SCHEDULE_ID = stringPreferencesKey("manual_schedule_id")
        val KEY_MANUAL_FACILITY_ID = stringPreferencesKey("manual_facility_id")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                email = prefs[KEY_EMAIL] ?: "",
                password = prefs[KEY_PASSWORD] ?: "",
                scheduleId = prefs[KEY_SCHEDULE_ID] ?: "",
                facilityId = prefs[KEY_FACILITY_ID] ?: "",
                facilityName = prefs[KEY_FACILITY_NAME] ?: "",
                startDate = prefs[KEY_START_DATE] ?: "",
                endDate = prefs[KEY_END_DATE] ?: "",
                checkIntervalSeconds = prefs[KEY_INTERVAL_SECONDS] ?: 30,
                isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false,
                sessionCookie = prefs[KEY_SESSION_COOKIE] ?: "",
                csrfToken = prefs[KEY_CSRF_TOKEN] ?: "",
                autoBook = prefs[KEY_AUTO_BOOK] ?: true,
                notifyOnFound = prefs[KEY_NOTIFY_ON_FOUND] ?: true,
                manualScheduleId = prefs[KEY_MANUAL_SCHEDULE_ID] ?: "",
                manualFacilityId = prefs[KEY_MANUAL_FACILITY_ID] ?: ""
            )
        }

    suspend fun saveLoginInfo(email: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = email
            prefs[KEY_PASSWORD] = password
        }
    }

    suspend fun saveSessionData(scheduleId: String, sessionCookie: String, csrfToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_ID] = scheduleId
            prefs[KEY_SESSION_COOKIE] = sessionCookie
            prefs[KEY_CSRF_TOKEN] = csrfToken
            prefs[KEY_IS_LOGGED_IN] = scheduleId.isNotEmpty()
        }
    }

    suspend fun saveAppointmentSettings(
        facilityId: String,
        facilityName: String,
        startDate: String,
        endDate: String,
        intervalSeconds: Int,
        autoBook: Boolean,
        notifyOnFound: Boolean,
        manualScheduleId: String,
        manualFacilityId: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FACILITY_ID] = facilityId
            prefs[KEY_FACILITY_NAME] = facilityName
            prefs[KEY_START_DATE] = startDate
            prefs[KEY_END_DATE] = endDate
            prefs[KEY_INTERVAL_SECONDS] = intervalSeconds
            prefs[KEY_AUTO_BOOK] = autoBook
            prefs[KEY_NOTIFY_ON_FOUND] = notifyOnFound
            prefs[KEY_MANUAL_SCHEDULE_ID] = manualScheduleId
            prefs[KEY_MANUAL_FACILITY_ID] = manualFacilityId
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_SESSION_COOKIE] = ""
            prefs[KEY_CSRF_TOKEN] = ""
            prefs[KEY_SCHEDULE_ID] = ""
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
