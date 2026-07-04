package com.ers.emergencyresponseapp.network

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class SaveRoutePointRequest(
    val incident_id: Int,
    val responder_id: Int,
    val latitude: Double,
    val longitude: Double,
    val speed: Float?,
    val heading: Float?,
    val status: String
)

data class SaveRoutePointResponse(
    val success: Boolean,
    val message: String?
)

data class MarkRouteArrivedRequest(
    val incident_id: Int,
    val responder_id: Int
)

data class MarkRouteArrivedResponse(
    val success: Boolean,
    val message: String?
)

data class MarkIncidentCompleteResponse(
    val success: Boolean,
    val message: String? = null,
    val incident_id: Int? = null,
    val completion_image_path: String? = null,
    val review_status: String? = null
)

data class BackupRequestStatusDto(
    val id: Int,
    val status: String,
    val requested_department: String,
    val resources: String,
    val is_full_backup: Int,
    val created_at: String,
    val updated_at: String?
)

data class BackupRequestStatusResponse(
    val success: Boolean,
    val message: String? = null,
    val request: BackupRequestStatusDto? = null
)

data class SendBackupRequestResponse(
    val success: Boolean,
    val message: String? = null,
    val id: Int? = null
)

interface IncidentApi {

    @GET("api/api_app/get-assigned-incidents.php")
    suspend fun getAssignedIncidents(
        @Query("responder_id") responderId: Int
    ): Response<List<IncidentDto>>

    @FormUrlEncoded
    @POST("api/api_app/update-assignment-status.php")
    suspend fun updateAssignmentStatus(
        @Field("assignment_id") assignmentId: String,
        @Field("responder_id") responderId: Int,
        @Field("status") status: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/api_app/mark-assignment-received.php")
    suspend fun markAssignmentReceived(
        @Field("incident_id") incidentId: String,
        @Field("responder_id") responderId: Int
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/api_app/sync-unit-status.php")
    suspend fun syncUnitStatus(
        @Field("responder_id") responderId: Int
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/api_app/send-backup-request.php")
    suspend fun sendBackupRequest(
        @Field("responder_id") responderId: Int,
        @Field("responder_name") responderName: String,
        @Field("department") department: String,
        @Field("requested_department") requestedDepartment: String,
        @Field("resources") resources: String,
        @Field("is_full_backup") isFullBackup: Int,
        @Field("incident_id") incidentId: String
    ): SendBackupRequestResponse

    @GET("api/api_app/get-active-incidents.php")
    suspend fun getActiveIncidents(
        @Query("responder_id") responderId: Int
    ): Response<List<IncidentDto>>

    @POST("api/api_app/save-route-point.php")
    suspend fun saveRoutePoint(
        @Body request: SaveRoutePointRequest
    ): SaveRoutePointResponse

    @POST("api/api_app/mark-route-arrived.php")
    suspend fun markRouteArrived(
        @Body request: MarkRouteArrivedRequest
    ): MarkRouteArrivedResponse

    @Multipart
    @POST("api/api_app/mark-incident-complete.php")
    suspend fun markIncidentComplete(
        @Part("assignment_id") assignmentId: RequestBody,
        @Part("responder_id") responderId: RequestBody,
        @Part("notes") notes: RequestBody,
        @Part proofImage: MultipartBody.Part
    ): MarkIncidentCompleteResponse

    @GET("api/api_app/get-backup-request-status.php")
    suspend fun getBackupRequestStatus(
        @Query("request_id") requestId: Int
    ): BackupRequestStatusResponse
}