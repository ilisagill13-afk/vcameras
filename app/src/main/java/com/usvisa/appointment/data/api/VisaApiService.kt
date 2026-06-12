package com.usvisa.appointment.data.api

import com.usvisa.appointment.data.model.AvailableDay
import com.usvisa.appointment.data.model.AvailableTimes
import com.usvisa.appointment.data.model.LoginJsonRequest
import com.usvisa.appointment.data.model.LoginJsonResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VisaApiService {

    // Step 1: Load login page → extracts CSRF token + session cookie
    @GET("niv/users/sign_in")
    suspend fun getLoginPage(): Response<ResponseBody>

    // Step 2: POST JSON credentials — website expects JSON, NOT form-encoded
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("niv/users/sign_in")
    suspend fun loginJson(
        @Header("X-CSRF-Token") csrfToken: String,
        @Body request: LoginJsonRequest
    ): Response<LoginJsonResponse>

    // Fetch any relative URL (used for groups page after login redirect)
    @GET
    suspend fun getPage(@Url url: String): Response<ResponseBody>

    // Appointment page: contains facility IDs in select dropdown + fresh CSRF token
    @GET("niv/schedule/{scheduleId}/appointment")
    suspend fun getAppointmentPage(
        @Path("scheduleId") scheduleId: String
    ): Response<ResponseBody>

    // Available dates for a consulate within a schedule
    @Headers(
        "X-Requested-With: XMLHttpRequest",
        "Accept: application/json, text/javascript, */*; q=0.01"
    )
    @GET("niv/schedule/{scheduleId}/appointment/days/{facilityId}.json")
    suspend fun getAvailableDays(
        @Path("scheduleId") scheduleId: String,
        @Path("facilityId") facilityId: String,
        @Header("Referer") referer: String,
        @Query("appointments[expedite]") expedite: Boolean = false
    ): Response<List<AvailableDay>>

    // Available time slots for a specific date
    @Headers(
        "X-Requested-With: XMLHttpRequest",
        "Accept: application/json, text/javascript, */*; q=0.01"
    )
    @GET("niv/schedule/{scheduleId}/appointment/times/{facilityId}.json")
    suspend fun getAvailableTimes(
        @Path("scheduleId") scheduleId: String,
        @Path("facilityId") facilityId: String,
        @Header("Referer") referer: String,
        @Query("date") date: String,
        @Query("appointments[expedite]") expedite: Boolean = false
    ): Response<AvailableTimes>

    // Book the appointment (form-encoded PUT via _method override)
    @FormUrlEncoded
    @POST("niv/schedule/{scheduleId}/appointment")
    suspend fun bookAppointment(
        @Path("scheduleId") scheduleId: String,
        @Field("authenticity_token") authenticityToken: String,
        @Field("appointments[consulate_appointment][facility_id]") facilityId: String,
        @Field("appointments[consulate_appointment][date]") date: String,
        @Field("appointments[consulate_appointment][time]") time: String,
        @Field("utf8") utf8: String = "✓",
        @Field("_method") method: String = "put"
    ): Response<ResponseBody>

    @GET("niv/users/sign_out")
    suspend fun logout(): Response<ResponseBody>
}
