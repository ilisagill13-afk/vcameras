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

data class LoginRequest(
    @SerializedName("user") val user: UserCredentials,
    @SerializedName("policy_confirmed") val policyConfirmed: Int = 1
)

data class UserCredentials(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
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
    val scheduleId: String = "",
    val facilityId: String = "89",
    val facilityName: String = "Calgary",
    val startDate: String = "",
    val endDate: String = "",
    val checkIntervalMinutes: Int = 5,
    val isLoggedIn: Boolean = false,
    val sessionCookie: String = "",
    val csrfToken: String = "",
    val autoBook: Boolean = true,
    val notifyOnFound: Boolean = true
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
