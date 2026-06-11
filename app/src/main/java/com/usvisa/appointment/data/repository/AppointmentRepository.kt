package com.usvisa.appointment.data.repository

import android.content.Context
import android.util.Log
import com.usvisa.appointment.data.api.ApiClient
import com.usvisa.appointment.data.model.AvailableDay
import com.usvisa.appointment.data.model.AvailableTimes
import com.usvisa.appointment.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String, val code: Int = 0) : RepoResult<Nothing>()
}

class AppointmentRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppointmentRepo"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        @Volatile
        private var instance: AppointmentRepository? = null

        fun getInstance(context: Context): AppointmentRepository {
            return instance ?: synchronized(this) {
                instance ?: AppointmentRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    val apiClient = ApiClient(context)
    private val preferencesManager = PreferencesManager(context)

    // Parse CSRF token from HTML
    private fun extractCsrfToken(html: String): String? {
        val patterns = listOf(
            Regex("""<meta name="csrf-token" content="([^"]+)""""),
            Regex("""<meta content="([^"]+)" name="csrf-token""""),
            Regex("""authenticity_token[^>]+value="([^"]+)"""")
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    // Parse schedule ID from URL or HTML
    private fun extractScheduleId(html: String, url: String): String? {
        // Try from URL path like /en-ca/niv/schedule/12345678/appointment
        val urlPattern = Regex("""/schedule/(\d+)/""")
        val urlMatch = urlPattern.find(url)
        if (urlMatch != null) return urlMatch.groupValues[1]

        // Try from HTML links
        val htmlPattern = Regex("""/schedule/(\d+)/""")
        val htmlMatch = htmlPattern.find(html)
        if (htmlMatch != null) return htmlMatch.groupValues[1]

        return null
    }

    suspend fun login(email: String, password: String): RepoResult<String> {
        return try {
            // Step 1: GET login page for CSRF token
            val loginPageResponse = apiClient.service.getLoginPage()
            if (!loginPageResponse.isSuccessful) {
                return RepoResult.Error("Failed to load login page: ${loginPageResponse.code()}")
            }

            val loginPageHtml = loginPageResponse.body()?.string() ?: ""
            val csrfToken = extractCsrfToken(loginPageHtml)
                ?: return RepoResult.Error("Could not extract CSRF token from login page")

            Log.d(TAG, "Got CSRF token: ${csrfToken.take(20)}...")

            // Step 2: POST credentials
            val loginResponse = apiClient.service.login(
                email = email,
                password = password,
                csrfToken = csrfToken
            )

            val responseBody = loginResponse.body()?.string() ?: ""
            val responseCode = loginResponse.code()

            Log.d(TAG, "Login response code: $responseCode")

            // Check for login errors in HTML
            if (responseBody.contains("Invalid Email or password") ||
                responseBody.contains("incorrect") ||
                responseBody.contains("invalid")) {
                return RepoResult.Error("Invalid email or password")
            }

            // Step 3: Get account page to find schedule ID
            val accountResponse = apiClient.service.getAppointmentPage("")
            val accountHtml = accountResponse.body()?.string() ?: responseBody
            val finalUrl = accountResponse.raw().request.url.toString()

            var scheduleId = extractScheduleId(accountHtml, finalUrl)

            // Try from login response URL if redirect happened
            if (scheduleId == null) {
                val loginFinalUrl = loginResponse.raw().request.url.toString()
                scheduleId = extractScheduleId(responseBody, loginFinalUrl)
            }

            // Try fetching schedule list page
            if (scheduleId == null) {
                scheduleId = tryFetchScheduleId()
            }

            if (scheduleId == null) {
                return RepoResult.Error("Login succeeded but could not find schedule ID. Please ensure you have a pending visa appointment.")
            }

            val newCsrfToken = extractCsrfToken(accountHtml) ?: csrfToken
            val sessionCookie = apiClient.cookieJar.getSessionCookie("ais.usvisa-info.com")

            preferencesManager.saveSessionData(scheduleId, sessionCookie, newCsrfToken)
            preferencesManager.saveLoginInfo(email, password)

            Log.d(TAG, "Login successful. Schedule ID: $scheduleId")
            RepoResult.Success(scheduleId)

        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    private suspend fun tryFetchScheduleId(): String? {
        return try {
            // The appointments list page typically contains links with schedule IDs
            val response = apiClient.service.getPaymentInfo("")
            val body = response.body()?.string() ?: return null
            val url = response.raw().request.url.toString()
            extractScheduleId(body, url)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAvailableDays(
        scheduleId: String,
        facilityId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): RepoResult<List<AvailableDay>> {
        return try {
            val response = apiClient.service.getAvailableDays(scheduleId, facilityId)

            if (response.code() == 401 || response.code() == 403) {
                return RepoResult.Error("Session expired. Please login again.", response.code())
            }

            if (!response.isSuccessful) {
                return RepoResult.Error("Failed to fetch available days: ${response.code()}")
            }

            val allDays = response.body() ?: emptyList()

            // Filter by date range
            val filtered = allDays.filter { day ->
                try {
                    val date = LocalDate.parse(day.date, DATE_FORMAT)
                    !date.isBefore(startDate) && !date.isAfter(endDate)
                } catch (e: Exception) {
                    false
                }
            }.sortedBy { it.date }

            Log.d(TAG, "Total available days: ${allDays.size}, In range: ${filtered.size}")
            RepoResult.Success(filtered)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching available days", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun getAvailableTimes(
        scheduleId: String,
        facilityId: String,
        date: String
    ): RepoResult<AvailableTimes> {
        return try {
            val response = apiClient.service.getAvailableTimes(scheduleId, facilityId, date)

            if (!response.isSuccessful) {
                return RepoResult.Error("Failed to fetch times: ${response.code()}")
            }

            val times = response.body() ?: AvailableTimes(emptyList(), emptyList())
            RepoResult.Success(times)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching times", e)
            RepoResult.Error("Network error: ${e.message}")
        }
    }

    suspend fun bookEarliestAvailableSlot(
        scheduleId: String,
        facilityId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): RepoResult<String> {
        // 1. Get available days in range
        val daysResult = getAvailableDays(scheduleId, facilityId, startDate, endDate)
        if (daysResult is RepoResult.Error) return daysResult

        val availableDays = (daysResult as RepoResult.Success).data
        if (availableDays.isEmpty()) {
            return RepoResult.Error("No available slots found in the selected date range")
        }

        // 2. Take the earliest date
        val earliestDay = availableDays.first()
        Log.d(TAG, "Earliest available date: ${earliestDay.date}")

        // 3. Get times for earliest date
        val timesResult = getAvailableTimes(scheduleId, facilityId, earliestDay.date)
        if (timesResult is RepoResult.Error) return timesResult

        val times = (timesResult as RepoResult.Success).data
        val availableTimes = times.availableTimes.ifEmpty { times.businessTimes }

        if (availableTimes.isEmpty()) {
            return RepoResult.Error("No time slots available for ${earliestDay.date}")
        }

        val selectedTime = availableTimes.first()
        Log.d(TAG, "Booking slot: ${earliestDay.date} at $selectedTime")

        // 4. Get fresh CSRF token for booking
        val settings = preferencesManager.settingsFlow.first()
        val appointmentPageResponse = apiClient.service.getAppointmentPage(scheduleId)
        val appointmentHtml = appointmentPageResponse.body()?.string() ?: ""
        val csrfToken = extractCsrfToken(appointmentHtml) ?: settings.csrfToken

        if (csrfToken.isEmpty()) {
            return RepoResult.Error("Could not get CSRF token for booking")
        }

        // 5. Book the appointment
        return try {
            val bookResponse = apiClient.service.bookAppointment(
                scheduleId = scheduleId,
                authenticityToken = csrfToken,
                facilityId = facilityId,
                date = earliestDay.date,
                time = selectedTime
            )

            val responseCode = bookResponse.code()
            val responseBody = bookResponse.body()?.string() ?: ""

            Log.d(TAG, "Booking response: $responseCode")

            if (bookResponse.isSuccessful || responseCode == 302) {
                if (responseBody.contains("error") || responseBody.contains("Error")) {
                    val errorPattern = Regex("""<div[^>]*class="[^"]*error[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
                    val errorMatch = errorPattern.find(responseBody)
                    val errorMsg = errorMatch?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
                    if (!errorMsg.isNullOrEmpty()) {
                        return RepoResult.Error("Booking failed: $errorMsg")
                    }
                }
                RepoResult.Success("${earliestDay.date} at $selectedTime")
            } else {
                RepoResult.Error("Booking failed with code $responseCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Booking exception", e)
            RepoResult.Error("Booking error: ${e.message}")
        }
    }

    suspend fun reloginIfNeeded(): Boolean {
        val settings = preferencesManager.settingsFlow.first()
        if (settings.email.isEmpty() || settings.password.isEmpty()) return false

        val result = login(settings.email, settings.password)
        return result is RepoResult.Success
    }

    suspend fun checkAndBookSlots(): RepoResult<String> {
        val settings = preferencesManager.settingsFlow.first()

        if (!settings.isLoggedIn || settings.scheduleId.isEmpty()) {
            return RepoResult.Error("Not logged in")
        }

        if (settings.startDate.isEmpty() || settings.endDate.isEmpty()) {
            return RepoResult.Error("Date range not configured")
        }

        val startDate = try {
            LocalDate.parse(settings.startDate, DATE_FORMAT)
        } catch (e: Exception) {
            return RepoResult.Error("Invalid start date: ${settings.startDate}")
        }

        val endDate = try {
            LocalDate.parse(settings.endDate, DATE_FORMAT)
        } catch (e: Exception) {
            return RepoResult.Error("Invalid end date: ${settings.endDate}")
        }

        val result = if (settings.autoBook) {
            bookEarliestAvailableSlot(
                scheduleId = settings.scheduleId,
                facilityId = settings.facilityId,
                startDate = startDate,
                endDate = endDate
            )
        } else {
            // Just check, don't book
            val daysResult = getAvailableDays(
                scheduleId = settings.scheduleId,
                facilityId = settings.facilityId,
                startDate = startDate,
                endDate = endDate
            )
            when (daysResult) {
                is RepoResult.Success -> {
                    if (daysResult.data.isEmpty()) {
                        RepoResult.Error("No slots available in the selected range")
                    } else {
                        RepoResult.Success("Found ${daysResult.data.size} slots. Earliest: ${daysResult.data.first().date}")
                    }
                }
                is RepoResult.Error -> daysResult
            }
        }

        // If session expired, re-login and retry once
        if (result is RepoResult.Error && (result.code == 401 || result.code == 403)) {
            Log.d(TAG, "Session expired, re-logging in...")
            val reloginSuccess = reloginIfNeeded()
            if (reloginSuccess) {
                val freshSettings = preferencesManager.settingsFlow.first()
                return if (settings.autoBook) {
                    bookEarliestAvailableSlot(
                        scheduleId = freshSettings.scheduleId,
                        facilityId = freshSettings.facilityId,
                        startDate = startDate,
                        endDate = endDate
                    )
                } else {
                    val daysResult = getAvailableDays(
                        scheduleId = freshSettings.scheduleId,
                        facilityId = freshSettings.facilityId,
                        startDate = startDate,
                        endDate = endDate
                    )
                    when (daysResult) {
                        is RepoResult.Success -> {
                            if (daysResult.data.isEmpty()) RepoResult.Error("No slots available")
                            else RepoResult.Success("Found ${daysResult.data.size} slots. Earliest: ${daysResult.data.first().date}")
                        }
                        is RepoResult.Error -> daysResult
                    }
                }
            }
        }

        return result
    }
}
