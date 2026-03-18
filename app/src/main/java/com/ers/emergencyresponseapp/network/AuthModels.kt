package com.ers.emergencyresponseapp.network

data class UserDto(
    val id: Int,
    val name: String? = null,
    val email: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null
)

data class SendOtpResponse(
    val success: Boolean,
    val message: String,
    val otp: String? = null
)

data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null
)

data class IncidentDto(
    val id: Long,
    val reference_no: String? = null,
    val type: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val title: String? = null,
    val description: String? = null,
    val location_address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class IncidentsResponse(
    val success: Boolean,
    val message: String,
    val incidents: List<IncidentDto> = emptyList()
)