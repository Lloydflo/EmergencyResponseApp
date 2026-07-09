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

data class ResourceRequestStatusDto(
    val id: Int,
    val resource_name: String,
    val category: String,
    val quantity: Int,
    val urgency: String,
    val status: String,
    val created_at: String,
    val updated_at: String?
)

data class ResourceRequestStatusResponse(
    val success: Boolean,
    val message: String? = null,
    val request: ResourceRequestStatusDto? = null
)

data class SendResourceRequestResponse(
    val success: Boolean,
    val message: String? = null,
    val id: Int? = null
)

data class MyResourceRequestDto(
    val id: Int,
    val resource_name: String,
    val category: String,
    val quantity: Int,
    val urgency: String,
    val status: String,
    val incident_id: String?,
    val location: String,
    val notes: String?,
    val created_at: String,
    val updated_at: String?
)

data class MyResourceRequestsResponse(
    val success: Boolean,
    val message: String? = null,
    val requests: List<MyResourceRequestDto>? = null
)

data class CancelResourceRequestResponse(
    val success: Boolean,
    val message: String? = null
)

data class MyBackupRequestDto(
    val id: Int,
    val responder_id: Int,
    val requested_department: String,
    val resources: String,
    val is_full_backup: Int,
    val incident_id: String?,
    val status: String,
    val created_at: String,
    val updated_at: String?
)

data class MyBackupRequestsResponse(
    val success: Boolean,
    val message: String? = null,
    val requests: List<MyBackupRequestDto>? = null
)

data class CancelBackupRequestBody(
    val request_id: Int,
    val responder_id: Int
)

data class CancelBackupRequestResponse(
    val success: Boolean,
    val message: String?
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
        @Query("request_id") requestId: Int,
        @Query("responder_id") responderId: Int
    ): BackupRequestStatusResponse

    @FormUrlEncoded
    @POST("api/api_app/send-resource-request.php")
    suspend fun sendResourceRequest(
        @Field("responder_id") responderId: Int,
        @Field("responder_name") responderName: String,
        @Field("category") category: String,
        @Field("resource_name") resourceName: String,
        @Field("quantity") quantity: Int,
        @Field("urgency") urgency: String,
        @Field("incident_id") incidentId: String,
        @Field("location") location: String,
        @Field("notes") notes: String
    ): SendResourceRequestResponse

    @GET("api/api_app/get-resource-request-status.php")
    suspend fun getResourceRequestStatus(
        @Query("request_id") requestId: Int
    ): ResourceRequestStatusResponse

    @GET("api/api_app/get-my-resource-requests.php")
    suspend fun getMyResourceRequests(
        @Query("responder_id") responderId: Int
    ): MyResourceRequestsResponse

    @FormUrlEncoded
    @POST("api/api_app/cancel-resource-request.php")
    suspend fun cancelResourceRequest(
        @Field("request_id") requestId: Int,
        @Field("responder_id") responderId: Int
    ): CancelResourceRequestResponse

    @GET("api/api_app/get-my-backup-requests.php")
    suspend fun getMyBackupRequests(
        @Query("responder_id") responderId: Int
    ): MyBackupRequestsResponse

    @FormUrlEncoded
    @POST("api/api_app/cancel-backup-request.php")
    suspend fun cancelBackupRequest(
        @Field("request_id") requestId: Int,
        @Field("responder_id") responderId: Int
    ): CancelBackupRequestResponse

}