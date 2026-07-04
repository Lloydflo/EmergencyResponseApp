package com.ers.emergencyresponseapp.network

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody


data class AssignedIncidentsRequest(val department: String)
data class AssignedIncidentsResponse(
    val success: Boolean,
    val message: String? = null,
    val incidents: List<IncidentDto> = emptyList()
)

// adjust fields to match your incidents table columns
    
interface AuthApi {

    @FormUrlEncoded
    @POST("api/api_app/send-otp.php")
    suspend fun sendOtp(@Field("email") email: String): SendOtpResponse

    @FormUrlEncoded
    @POST("api/api_app/verify-otp.php")
    suspend fun verifyOtp(
        @Field("email") email: String,
        @Field("otp") otp: String
    ): VerifyOtpResponse

    @FormUrlEncoded
    @POST("api/api_app/login.php")
    suspend fun login(
        @Field("email") email: String
    ): LoginResponse

    @FormUrlEncoded
    @POST("api/api_app/upsert-user.php")
    suspend fun upsertUser(
        @Field("id") id: Int,
        @Field("email") email: String,
        @Field("name") name: String? = null,
        @Field("department") department: String? = null,
        @Field("profile_image_path") profileImagePath: String? = null,
        @Field("role") role: String? = null,
        @Field("status") status: String? = null,
        @Field("is_active") isActive: Int? = null,
        @Field("unit_code") unitCode: String? = null,
        @Field("unit_type") unitType: String? = null,
        @Field("vehicle_plate") vehiclePlate: String? = null,
        @Field("unit_status") unitStatus: String? = null,
        @Field("last_login") lastLogin: String? = null
    ): UpsertUserResponse

    @Multipart
    @POST("api/api_app/upload-profile-image.php")
    suspend fun uploadProfileImage(
        @Part("user_id") userId: RequestBody,
        @Part profileImage: MultipartBody.Part
    ): UploadProfileImageResponse

}
