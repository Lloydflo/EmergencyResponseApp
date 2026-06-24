package com.ers.emergencyresponseapp.network

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import retrofit2.Response
import retrofit2.http.*

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
    ): Response<Unit>

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
}