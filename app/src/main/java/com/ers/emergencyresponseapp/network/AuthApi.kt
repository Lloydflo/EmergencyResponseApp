package com.ers.emergencyresponseapp.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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

    @Multipart
    @POST("api/api_app/upload-profile-image.php")
    suspend fun uploadProfileImage(
        @Part("user_id") userId: RequestBody,
        @Part profileImage: MultipartBody.Part
    ): UploadProfileImageResponse

    @FormUrlEncoded
    @POST("api/api_app/update_profile.php")
    suspend fun updateProfile(
        @Field("user_id") userId: Int,
        @Field("full_name") fullName: String,
        @Field("username") username: String,
        @Field("email") email: String
    ): UpdateProfileResponse

    @FormUrlEncoded
    @POST("api/api_app/logout.php")
    suspend fun logout(@Field("responder_id") responderId: Int): LogoutResponse
}
