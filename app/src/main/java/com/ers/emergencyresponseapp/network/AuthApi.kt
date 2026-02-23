package com.ers.emergencyresponseapp.network

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Body


data class AssignedIncidentsRequest(val department: String)
data class AssignedIncidentsResponse(
    val success: Boolean,
    val message: String? = null,
    val incidents: List<IncidentDto> = emptyList()
)

// adjust fields to match your incidents table columns

interface AuthApi {

    @FormUrlEncoded
    @POST("api/send-otp.php")
    suspend fun sendOtp(@Field("email") email: String): SendOtpResponse

    @FormUrlEncoded
    @POST("api/verify-otp.php")
    suspend fun verifyOtp(
        @Field("email") email: String,
        @Field("otp") otp: String
    ): VerifyOtpResponse

    @FormUrlEncoded
    @POST("api/login.php")
    suspend fun login(
        @Field("email") email: String
    ): LoginResponse

    @FormUrlEncoded
    @POST("api/Incidents/get-assigned-incidents.php")
    suspend fun getAssignedIncidents(
        @Field("email") email: String
    ): IncidentsResponse
}

interface IncidentsApi {
    @POST("api/Incidents/get-assigned-incidents.php")
    suspend fun getAssignedIncidents(@Body body: AssignedIncidentsRequest): AssignedIncidentsResponse
}