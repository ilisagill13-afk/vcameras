package com.usvisa.appointment.data.repository

import android.content.Context
import android.util.Log
import com.usvisa.appointment.data.api.ApiClient
import com.usvisa.appointment.data.model.*
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String, val code: Int = 0) : RepoResult<Nothing>()
}

data class FacilityFromPage(val id: String, val name: String)

class AppointmentRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppointmentRepo"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        @Volatile
        private var instance: AppointmentRepository? = null

        fun getInstance(context: Context): AppointmentRepository =
            instance ?: synchronized(this) {
                instance ?: AppointmentRepository(context.applicationContext).also { instance = it }
            }
    }

    val apiClient = ApiClient(context)
    private val prefs = PreferencesManager(context)

    // ─── HTML Helpers ──────────────────────────────────────────────────────────

    private fun extractCsrfToken(html: String): String? {
        listOf(
            Regex("""<meta\s+name="csrf-token"\s+content="([^"]+)""""),
            Regex("""<meta\s+content="([^"]+)"\s+name="csrf-token""""),
            Regex("""name="authenticity_token"[^>]+value="([^"]+)"""")
        ).forEach { p -> p.find(html)?.let { return it.groupValues[1] } }
        return null
    }

    fun extractFacilitiesFromPage(html: String): List<FacilityFromPage> {
        val selectRx = Regex(
            """<select[^>]*name="appointments\[consulate_appointment\]\[facility_id\]"[^>]*>(.*?)</select>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val optRx = Regex("""<option\s+value="(\d+)"[^>]*>([^<]+)</option>""")
        val sel = selectRx.find(html) ?: return emptyList()
        return optRx.findAll(sel.groupValues[1])
            .map { FacilityFromPage(it.groupValues[1].trim(), it.groupValues[2].trim()) }
            .filter { it.id.isNotEmpty() }
            .toList()
    }

    // ─── Slot Checking ─────────────────────────────────────────────────────────

    // ─── Auto Re-Login ─────────────────────────────────────────────────────────

    suspend fun reLogin(): Boolean {
        return try {
            val settings = prefs.settingsFlow.first()
            if (settings.email.isEmpty() || settings.password.isEmpty()) return false

            // GET login page → fresh CSRF token (uses existing cf_clearance cookie)
            val loginPageResp = apiClient.service.getLoginPage()
            val html = loginPageResp.body()?.string() ?: return false
            val csrf = extractCsrfToken(html) ?: return false

            val loginResp = apiClient.service.loginJson(
                csrfToken = csrf,
                request = LoginJsonRequest(UserCredentials(settings.email, settings.password))
            )

            if (!loginResp.isSuccessful) {
                Log.w(TAG, "Re-login HTTP ${loginResp.code()}")
                return false
            }

            val redirectPath = loginResp.body()?.redirectPath ?: return false
            val scheduleId = Regex("/groups/(\\d+)").find(redirectPath)?.groupValues?.get(1) ?: ""
            if (scheduleId.isEmpty()) return false

            val sessionCookie = apiClient.cookieJar.getSessionCookie("ais.usvisa-info.com")
            prefs.saveSessionData(scheduleId, sessionCookie, csrf)

            Log.d(TAG, "Auto re-login success, scheduleId=$scheduleId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto re-login failed", e)
            false
        }
    }

    private fun apptReferer(scheduleId: String) =
        "https://ais.usvisa-info.com/en-ca/niv/schedule/$scheduleId/appointment"

    suspend fun getAvailableDays(
        scheduleId: String,
        facilityId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): RepoResult<List<AvailableDay>> {
        return try {
            val resp = apiClient.service.getAvailableDays(scheduleId, facilityId, apptReferer(scheduleId))

            when (resp.code()) {
                401, 403 -> return RepoResult.Error(
                    "Session expired — open app and log in again", resp.code()
                )
            }
            if (!resp.isSuccessful)
                return RepoResult.Error("HTTP ${resp.code()} fetching available days")

            val all = resp.body() ?: emptyList()
            val filtered = all.filter { day ->
                runCatching {
                    val d = LocalDate.parse(day.date, DATE_FORMAT)
                    !d.isBefore(startDate) && !d.isAfter(endDate)
                }.getOrDefault(false)
            }.sortedBy { it.date }

            Log.d(TAG, "Available: total=${all.size}, in range=${filtered.size}")
            RepoResult.Success(filtered)

        } catch (e: Exception) {
            Log.e(TAG, "Days error", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun getAvailableTimes(
        scheduleId: String,
        facilityId: String,
        date: String
    ): RepoResult<AvailableTimes> {
        return try {
            val resp = apiClient.service.getAvailableTimes(scheduleId, facilityId, apptReferer(scheduleId), date)
            if (!resp.isSuccessful)
                return RepoResult.Error("HTTP ${resp.code()} fetching times for $date")
            RepoResult.Success(resp.body() ?: AvailableTimes(emptyList(), emptyList()))
        } catch (e: Exception) {
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    // ─── Auto Booking ──────────────────────────────────────────────────────────

    suspend fun bookEarliestAvailableSlot(
        scheduleId: String,
        facilityId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): RepoResult<String> {

        val daysResult = getAvailableDays(scheduleId, facilityId, startDate, endDate)
        if (daysResult is RepoResult.Error) return daysResult

        val days = (daysResult as RepoResult.Success).data
        if (days.isEmpty())
            return RepoResult.Error("No available slots in the selected date range")

        val earliest = days.first()
        Log.d(TAG, "Earliest: ${earliest.date}")

        val timesResult = getAvailableTimes(scheduleId, facilityId, earliest.date)
        if (timesResult is RepoResult.Error) return timesResult

        val times = (timesResult as RepoResult.Success).data
        val timeList = times.availableTimes.ifEmpty { times.businessTimes }
        if (timeList.isEmpty())
            return RepoResult.Error("No time slots on ${earliest.date}")

        val selectedTime = timeList.first()

        // Refresh CSRF token before booking
        val apptResp = runCatching { apiClient.service.getAppointmentPage(scheduleId) }.getOrNull()
        val apptHtml = apptResp?.body()?.string() ?: ""
        val csrf = extractCsrfToken(apptHtml)
            ?: prefs.settingsFlow.first().csrfToken

        if (csrf.isEmpty())
            return RepoResult.Error("Could not get security token — please re-login")

        return try {
            val bookResp = apiClient.service.bookAppointment(
                scheduleId = scheduleId,
                authenticityToken = csrf,
                facilityId = facilityId,
                date = earliest.date,
                time = selectedTime
            )
            val code = bookResp.code()
            val body = bookResp.body()?.string() ?: ""
            Log.d(TAG, "Booking HTTP $code")

            when {
                bookResp.isSuccessful || code == 302 -> {
                    val err = extractInlineError(body)
                    if (err != null) RepoResult.Error("Booking rejected: $err")
                    else RepoResult.Success("${earliest.date} at $selectedTime")
                }
                code == 422 -> RepoResult.Error(
                    extractInlineError(body) ?: "Slot was taken — will retry"
                )
                code == 401 || code == 403 -> RepoResult.Error(
                    "Session expired — open app and log in again", code
                )
                else -> RepoResult.Error("Booking failed HTTP $code")
            }
        } catch (e: Exception) {
            RepoResult.Error("Booking error: ${e.message}")
        }
    }

    private fun extractInlineError(html: String): String? {
        listOf(
            Regex("""<div[^>]*class="[^"]*error[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""<p[^>]*class="[^"]*error[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        ).forEach { p ->
            val text = p.find(html)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    // ─── Main entry point called by service / ViewModel ───────────────────────

    suspend fun checkAndBookSlots(): RepoResult<String> {
        val result = doCheckAndBook()
        // On session expiry, try silent re-login once then retry
        if (result is RepoResult.Error && result.code in listOf(401, 403)) {
            Log.d(TAG, "Session expired — attempting auto re-login")
            return if (reLogin()) {
                Log.d(TAG, "Re-login succeeded, retrying check")
                doCheckAndBook()
            } else {
                RepoResult.Error("SESSION_EXPIRED", 401)
            }
        }
        return result
    }

    private suspend fun doCheckAndBook(): RepoResult<String> {
        val settings = prefs.settingsFlow.first()

        if (!settings.isLoggedIn)
            return RepoResult.Error("Not logged in")

        val scheduleId = settings.manualScheduleId.ifEmpty { settings.scheduleId }
        val facilityId = settings.manualFacilityId.ifEmpty { settings.facilityId }

        if (scheduleId.isEmpty())
            return RepoResult.Error("Schedule ID missing — re-login or set manually in Settings")
        if (facilityId.isEmpty())
            return RepoResult.Error("Facility ID not set — go to Settings")
        if (settings.startDate.isEmpty() || settings.endDate.isEmpty())
            return RepoResult.Error("Date range not configured")

        val startDate = runCatching { LocalDate.parse(settings.startDate, DATE_FORMAT) }.getOrNull()
            ?: return RepoResult.Error("Invalid start date: ${settings.startDate}")
        val endDate = runCatching { LocalDate.parse(settings.endDate, DATE_FORMAT) }.getOrNull()
            ?: return RepoResult.Error("Invalid end date: ${settings.endDate}")

        return if (settings.autoBook) {
            bookEarliestAvailableSlot(scheduleId, facilityId, startDate, endDate)
        } else {
            val daysResult = getAvailableDays(scheduleId, facilityId, startDate, endDate)
            when (daysResult) {
                is RepoResult.Success ->
                    if (daysResult.data.isEmpty()) RepoResult.Error("No slots in range")
                    else RepoResult.Success(
                        "Found ${daysResult.data.size} slot(s). Earliest: ${daysResult.data.first().date}"
                    )
                is RepoResult.Error -> daysResult
            }
        }
    }
}
