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
        private const val HOST = "ais.usvisa-info.com"
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

    // ─── HTML Parsing ──────────────────────────────────────────────────────────

    private fun extractCsrfToken(html: String): String? {
        val patterns = listOf(
            Regex("""<meta\s+name="csrf-token"\s+content="([^"]+)""""),
            Regex("""<meta\s+content="([^"]+)"\s+name="csrf-token""""),
            Regex("""input[^>]+name="authenticity_token"[^>]+value="([^"]+)""""),
            Regex("""authenticity_token.*?value="([^"]+)"""")
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // Extracts all /schedule/{id}/ occurrences from HTML or URL
    private fun extractScheduleIds(html: String, url: String = ""): List<String> {
        val pattern = Regex("""/schedule/(\d+)/""")
        val fromUrl = pattern.find(url)?.groupValues?.get(1)
        val fromHtml = pattern.findAll(html).map { it.groupValues[1] }.toList()
        return (listOfNotNull(fromUrl) + fromHtml).distinct()
    }

    // Extracts facility options from the appointment form select element
    fun extractFacilitiesFromPage(html: String): List<FacilityFromPage> {
        val selectRegex = Regex(
            """<select[^>]*name="appointments\[consulate_appointment\]\[facility_id\]"[^>]*>(.*?)</select>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val optionRegex = Regex("""<option\s+value="(\d+)"[^>]*>([^<]+)</option>""")

        val selectMatch = selectRegex.find(html) ?: return emptyList()
        return optionRegex.findAll(selectMatch.groupValues[1]).map { m ->
            FacilityFromPage(m.groupValues[1].trim(), m.groupValues[2].trim())
        }.filter { it.id.isNotEmpty() }.toList()
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): RepoResult<LoginResult> {
        return try {
            // 1. GET login page → CSRF token + _yatri_session cookie
            val pageResp = apiClient.service.getLoginPage()
            if (!pageResp.isSuccessful)
                return RepoResult.Error("Cannot load login page (HTTP ${pageResp.code()})")

            val pageHtml = pageResp.body()?.string() ?: ""
            val csrfToken = extractCsrfToken(pageHtml)
                ?: return RepoResult.Error("Could not read security token from login page")

            Log.d(TAG, "CSRF token obtained: ${csrfToken.take(15)}…")

            // 2. POST JSON credentials — website returns JSON redirect path
            val loginResp = apiClient.service.loginJson(
                csrfToken = csrfToken,
                request = LoginJsonRequest(
                    user = UserCredentials(email = email, password = password)
                )
            )

            val code = loginResp.code()
            Log.d(TAG, "Login HTTP $code")

            if (!loginResp.isSuccessful) {
                // 401/422 → wrong credentials
                val errBody = loginResp.errorBody()?.string() ?: ""
                val errMsg = Regex(""""error"\s*:\s*"([^"]+)"""").find(errBody)?.groupValues?.get(1)
                return RepoResult.Error(errMsg ?: "Login failed (HTTP $code). Check email/password.")
            }

            val loginJson = loginResp.body()
            if (loginJson?.error != null) {
                return RepoResult.Error(loginJson.error)
            }

            // 3. Follow redirect_path to account/groups page
            val redirectPath = loginJson?.redirectPath
                ?: return RepoResult.Error("Login succeeded but no redirect. Try re-logging from browser first.")

            Log.d(TAG, "Login redirect: $redirectPath")

            // redirectPath is like "/en-ca/niv/groups/12345" or "/en-ca/niv/schedule/12345/..."
            val groupsResp = apiClient.service.getPage("https://$HOST$redirectPath")
            val groupsHtml = groupsResp.body()?.string() ?: ""
            val groupsUrl = groupsResp.raw().request.url.toString()

            // 4. Extract schedule IDs from the groups/account page
            val scheduleIds = extractScheduleIds(groupsHtml, groupsUrl)
            Log.d(TAG, "Found schedule IDs: $scheduleIds")

            val scheduleId = scheduleIds.firstOrNull()
                ?: return RepoResult.Error(
                    "Logged in, but no visa application found. " +
                    "Make sure you have a scheduled visa appointment in your account."
                )

            // 5. Load appointment page to get real facility IDs
            val apptResp = apiClient.service.getAppointmentPage(scheduleId)
            val apptHtml = apptResp.body()?.string() ?: ""
            val freshCsrf = extractCsrfToken(apptHtml) ?: csrfToken
            val facilities = extractFacilitiesFromPage(apptHtml)

            val sessionCookie = apiClient.cookieJar.getSessionCookie(HOST)
            prefs.saveSessionData(scheduleId, sessionCookie, freshCsrf)
            prefs.saveLoginInfo(email, password)

            Log.d(TAG, "Login complete. Schedule=$scheduleId, Facilities=$facilities")
            RepoResult.Success(LoginResult(scheduleId, facilities, freshCsrf))

        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    // ─── Slot Checking ─────────────────────────────────────────────────────────

    suspend fun getAvailableDays(
        scheduleId: String,
        facilityId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): RepoResult<List<AvailableDay>> {
        return try {
            val resp = apiClient.service.getAvailableDays(scheduleId, facilityId)

            if (resp.code() == 401 || resp.code() == 403) {
                return RepoResult.Error("Session expired", resp.code())
            }
            if (!resp.isSuccessful) {
                return RepoResult.Error("HTTP ${resp.code()} fetching available days")
            }

            val all = resp.body() ?: emptyList()
            val filtered = all.filter { day ->
                runCatching {
                    val d = LocalDate.parse(day.date, DATE_FORMAT)
                    !d.isBefore(startDate) && !d.isAfter(endDate)
                }.getOrDefault(false)
            }.sortedBy { it.date }

            Log.d(TAG, "Available days total=${all.size}, in range=${filtered.size}")
            RepoResult.Success(filtered)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching days", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun getAvailableTimes(
        scheduleId: String,
        facilityId: String,
        date: String
    ): RepoResult<AvailableTimes> {
        return try {
            val resp = apiClient.service.getAvailableTimes(scheduleId, facilityId, date)
            if (!resp.isSuccessful)
                return RepoResult.Error("HTTP ${resp.code()} fetching times for $date")

            RepoResult.Success(resp.body() ?: AvailableTimes(emptyList(), emptyList()))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching times", e)
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

        // 1. Get available days in range
        val daysResult = getAvailableDays(scheduleId, facilityId, startDate, endDate)
        if (daysResult is RepoResult.Error) return daysResult

        val days = (daysResult as RepoResult.Success).data
        if (days.isEmpty())
            return RepoResult.Error("No available slots in the selected date range")

        val earliest = days.first()
        Log.d(TAG, "Earliest slot: ${earliest.date}")

        // 2. Get time slots for earliest date
        val timesResult = getAvailableTimes(scheduleId, facilityId, earliest.date)
        if (timesResult is RepoResult.Error) return timesResult

        val times = (timesResult as RepoResult.Success).data
        val timeList = times.availableTimes.ifEmpty { times.businessTimes }
        if (timeList.isEmpty())
            return RepoResult.Error("No time slots on ${earliest.date}")

        val selectedTime = timeList.first()
        Log.d(TAG, "Booking: ${earliest.date} at $selectedTime")

        // 3. Get a fresh CSRF token (required for the PUT/POST booking)
        val apptResp = apiClient.service.getAppointmentPage(scheduleId)
        val apptHtml = apptResp.body()?.string() ?: ""
        val csrf = extractCsrfToken(apptHtml)
            ?: prefs.settingsFlow.first().csrfToken

        if (csrf.isEmpty())
            return RepoResult.Error("Could not obtain security token for booking")

        // 4. Submit booking
        return try {
            val bookResp = apiClient.service.bookAppointment(
                scheduleId = scheduleId,
                authenticityToken = csrf,
                facilityId = facilityId,
                date = earliest.date,
                time = selectedTime
            )

            val respCode = bookResp.code()
            val respBody = bookResp.body()?.string() ?: ""
            Log.d(TAG, "Booking HTTP $respCode")

            when {
                bookResp.isSuccessful || respCode == 302 -> {
                    // Check for inline error message in HTML response
                    val errorMsg = extractBookingError(respBody)
                    if (errorMsg != null)
                        RepoResult.Error("Booking rejected: $errorMsg")
                    else
                        RepoResult.Success("${earliest.date} at $selectedTime")
                }
                respCode == 422 -> {
                    val errorMsg = extractBookingError(respBody)
                    RepoResult.Error(errorMsg ?: "Booking rejected (slot may be taken)")
                }
                else -> RepoResult.Error("Booking failed HTTP $respCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Booking exception", e)
            RepoResult.Error("Booking error: ${e.message}")
        }
    }

    private fun extractBookingError(html: String): String? {
        val patterns = listOf(
            Regex("""<div[^>]*class="[^"]*error[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""<p[^>]*class="[^"]*error[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""alert-error[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val text = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    // ─── Main Entry Point ──────────────────────────────────────────────────────

    suspend fun checkAndBookSlots(): RepoResult<String> {
        val settings = prefs.settingsFlow.first()

        if (!settings.isLoggedIn)
            return RepoResult.Error("Not logged in")

        // Use manual override if provided, else auto-detected
        val scheduleId = settings.manualScheduleId.ifEmpty { settings.scheduleId }
        val facilityId = settings.manualFacilityId.ifEmpty { settings.facilityId }

        if (scheduleId.isEmpty())
            return RepoResult.Error("Schedule ID not found. Please log in again or enter it manually in Settings.")
        if (facilityId.isEmpty())
            return RepoResult.Error("Facility (Consulate) ID not set. Please configure it in Settings.")
        if (settings.startDate.isEmpty() || settings.endDate.isEmpty())
            return RepoResult.Error("Date range not configured")

        val startDate = runCatching { LocalDate.parse(settings.startDate, DATE_FORMAT) }.getOrNull()
            ?: return RepoResult.Error("Invalid start date: ${settings.startDate}")
        val endDate = runCatching { LocalDate.parse(settings.endDate, DATE_FORMAT) }.getOrNull()
            ?: return RepoResult.Error("Invalid end date: ${settings.endDate}")

        val result = if (settings.autoBook) {
            bookEarliestAvailableSlot(scheduleId, facilityId, startDate, endDate)
        } else {
            val daysResult = getAvailableDays(scheduleId, facilityId, startDate, endDate)
            when (daysResult) {
                is RepoResult.Success ->
                    if (daysResult.data.isEmpty())
                        RepoResult.Error("No slots in the selected range")
                    else
                        RepoResult.Success("Found ${daysResult.data.size} slots. Earliest: ${daysResult.data.first().date}")
                is RepoResult.Error -> daysResult
            }
        }

        // Auto re-login on session expiry, then retry once
        if (result is RepoResult.Error && (result.code == 401 || result.code == 403)) {
            Log.d(TAG, "Session expired — re-logging in")
            val fresh = prefs.settingsFlow.first()
            if (fresh.email.isEmpty()) return result
            val loginResult = login(fresh.email, fresh.password)
            if (loginResult is RepoResult.Error) return loginResult

            // Retry with fresh session
            return checkAndBookSlots()
        }

        return result
    }

    suspend fun relogin(): Boolean {
        val s = prefs.settingsFlow.first()
        if (s.email.isEmpty() || s.password.isEmpty()) return false
        return login(s.email, s.password) is RepoResult.Success
    }
}

data class LoginResult(
    val scheduleId: String,
    val facilities: List<FacilityFromPage>,
    val csrfToken: String
)
