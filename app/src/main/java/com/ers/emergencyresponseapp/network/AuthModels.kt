package com.ers.emergencyresponseapp.network

import com.google.gson.annotations.SerializedName

data class UserDto(

    val id: Int,

    val name: String? = null,

    @SerializedName("username")
    val username: String? = null,

    val email: String,

    val role: String? = null,

    val department: String? = null,

    @SerializedName("unit_code")
    val unitCode: String? = null,

    @SerializedName("unit_type")
    val unitType: String? = null,

    @SerializedName("unit_status")
    val unitStatus: String? = null,

    @SerializedName("profile_image_path")
    val profileImagePath: String? = null
)

data class SendOtpResponse(
    val success: Boolean,
    val message: String
)

data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val user: UserDto? = null
)

data class UploadProfileImageResponse(
    val success: Boolean,
    val message: String? = null,
    val user_id: Int? = null,
    val profile_image_path: String? = null,
    val profile_image_url: String? = null
)

data class UpdateProfileResponse(
    val success: Boolean,
    val message: String? = null
)

data class LogoutResponse(
    val success: Boolean,
    val message: String? = null,
    val user_id: Int? = null
)
