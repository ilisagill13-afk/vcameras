package com.usvisa.appointment.data.api

import com.usvisa.appointment.data.model.AvailableDay
import com.usvisa.appointment.data.model.AvailableTimes
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VisaApiService {

    @GET("users/sign_in")
    suspend fun getLoginPage(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("users/sign_in")
    suspend fun login(
        @Field("user[email]") email: String,
        @Field("user[password]") password: String,
        @Field("policy_confirmed") policyConfirmed: Int = 1,
        @Field("utf8") utf8: String = "✓",
        @Header("X-CSRF-Token") csrfToken: String
    ): Response<ResponseBody>

    @GET("users/sign_out")
    suspend fun logout(): Response<ResponseBody>

    @GET("schedule/{scheduleId}/appointment")
    suspend fun getAppointmentPage(
        @Path("scheduleId") scheduleId: String
    ): Response<ResponseBody>

    @GET("schedule/{scheduleId}/appointment/days/{facilityId}.json")
    suspend fun getAvailableDays(
        @Path("scheduleId") scheduleId: String,
        @Path("facilityId") facilityId: String,
        @Query("appointments[expedite]") expedite: Boolean = false
    ): Response<List<AvailableDay>>

    @GET("schedule/{scheduleId}/appointment/times/{facilityId}.json")
    suspend fun getAvailableTimes(
        @Path("scheduleId") scheduleId: String,
        @Path("facilityId") facilityId: String,
        @Query("date") date: String,
        @Query("appointments[expedite]") expedite: Boolean = false
    ): Response<AvailableTimes>

    @FormUrlEncoded
    @POST("schedule/{scheduleId}/appointment")
    suspend fun bookAppointment(
        @Path("scheduleId") scheduleId: String,
        @Field("authenticity_token") authenticityToken: String,
        @Field("appointments[consulate_appointment][facility_id]") facilityId: String,
        @Field("appointments[consulate_appointment][date]") date: String,
        @Field("appointments[consulate_appointment][time]") time: String,
        @Field("utf8") utf8: String = "✓",
        @Field("_method") method: String = "put"
    ): Response<ResponseBody>

    @GET("schedule/{scheduleId}/payment")
    suspend fun getPaymentInfo(
        @Path("scheduleId") scheduleId: String
    ): Response<ResponseBody>
}
