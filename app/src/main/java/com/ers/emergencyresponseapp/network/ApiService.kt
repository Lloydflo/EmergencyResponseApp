package com.ers.emergencyresponseapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Minimal models for email-only login
data class LoginRequest(val email: String)
data class UserDto(val id: Int, val name: String?, val email: String)
data class LoginResponse(val success: Boolean, val message: String, val user: UserDto?)

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("api/login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>
}
