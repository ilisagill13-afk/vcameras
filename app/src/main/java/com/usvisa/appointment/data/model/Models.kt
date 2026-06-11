package com.usvisa.appointment.data.model

import com.google.gson.annotations.SerializedName

data class AvailableDay(
    @SerializedName("date") val date: String,
    @SerializedName("business_day") val businessDay: Boolean
)

data class AvailableTimes(
    @SerializedName("available_times") val availableTimes: List<String>,
    @SerializedName("business_times") val businessTimes: List<String>
)

// JSON body for real login (website expects JSON, not form-encoded)
data class LoginJsonRequest(
    @SerializedName("user") val user: UserCredentials,
    @SerializedName("utf8") val utf8: String = "✓"
)

data class UserCredentials(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("policy_confirmed") val policyConfirmed: Int = 1
)

// Server returns {"redirect_path":"/en-ca/niv/groups/12345"} on success
// or {"error":"Invalid Email or password."} on failure
data class LoginJsonResponse(
    @SerializedName("redirect_path") val redirectPath: String?,
    @SerializedName("error") val error: String?
)

data class AppointmentBookingData(
    val scheduleId: String,
    val facilityId: String,
    val date: String,
    val time: String,
    val csrfToken: String
)

data class MonitoringStatus(
    val isRunning: Boolean = false,
    val lastChecked: Long = 0L,
    val nextCheck: Long = 0L,
    val lastResult: String = "",
    val slotsFound: Int = 0,
    val checkCount: Int = 0
)

data class AppSettings(
    val email: String = "",
    val password: String = "",
    val scheduleId: String = "",          // auto-detected after login
    val facilityId: String = "",          // user must set this (from website)
    val facilityName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val checkIntervalSeconds: Int = 30,    // user types exact seconds
    val isLoggedIn: Boolean = false,
    val sessionCookie: String = "",
    val csrfToken: String = "",
    val autoBook: Boolean = true,
    val notifyOnFound: Boolean = true,
    val manualScheduleId: String = "",    // user override for schedule ID
    val manualFacilityId: String = ""     // user override for facility ID
)

data class FacilityOption(
    val id: String,
    val name: String,
    val city: String
)

val CANADA_FACILITIES = listOf(
    FacilityOption("89", "Calgary", "Calgary, AB"),
    FacilityOption("90", "Halifax", "Halifax, NS"),
    FacilityOption("91", "Montreal", "Montreal, QC"),
    FacilityOption("92", "Ottawa", "Ottawa, ON"),
    FacilityOption("93", "Quebec City", "Quebec City, QC"),
    FacilityOption("94", "Toronto", "Toronto, ON"),
    FacilityOption("95", "Vancouver", "Vancouver, BC")
)
