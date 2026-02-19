package com.ers.emergencyresponseapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Legacy Retrofit service kept for compatibility with [Network].
 *
 * Reuses auth request/response models from AuthApi.kt to avoid duplicate
 * declarations in the same package.
 */
interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("api/login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>

    @Headers("Content-Type: application/json")
    @POST("api/send-otp")
    suspend fun sendOtp(@Body req: SendOtpRequest): Response<SendOtpResponse>
}