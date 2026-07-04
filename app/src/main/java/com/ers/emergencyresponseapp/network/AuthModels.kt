package com.ers.emergencyresponseapp.network

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Int,
    val name: String? = null,
    val email: String,
    val role: String? = null,
    val department: String? = null,

    @SerializedName("unit_code")
    val unitCode: String? = null,

    @SerializedName("unit_type")
    val unitType: String? = null,

    @SerializedName("unit_status")
    val unitStatus: String? = null
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

data class UpsertUserResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null,
    val user_id: Int? = null
)

data class UploadProfileImageResponse(
    val success: Boolean,
    val message: String? = null,
    val user_id: Int? = null,
    val profile_image_path: String? = null,
    val profile_image_url: String? = null
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