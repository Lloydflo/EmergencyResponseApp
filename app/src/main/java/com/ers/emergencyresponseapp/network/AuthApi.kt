package com.ers.emergencyresponseapp.network

import retrofit2.http.Body
import retrofit2.http.POST

data class UserDto(
    val id: Int,
    val name: String?,
    val email: String
)

/** Request payload for POST /api/login */
data class LoginRequest(
    val email: String
)

/** Response payload from POST /api/login */
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null
)

/** Request payload for POST /api/send-otp */
data class SendOtpRequest(
    val email: String
)

/** Response payload from POST /api/send-otp */
data class SendOtpResponse(
    val success: Boolean,
    val message: String
)

/** Request payload for POST /api/verify-otp */
data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

/** Response payload from POST /api/verify-otp */
data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null
)

interface AuthApi {
    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/send-otp")
    suspend fun sendOtp(@Body body: SendOtpRequest): SendOtpResponse

    @POST("api/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): VerifyOtpResponse
}
