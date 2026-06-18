package com.ers.emergencyresponseapp.network

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import retrofit2.Response
import retrofit2.http.*

interface IncidentApi {

    @GET("api/api_app/get-assigned-incidents.php")
    suspend fun getAssignedIncidents(
        @Query("responder_id") responderId: Int
    ): Response<List<IncidentDto>>

    @FormUrlEncoded
    @POST("update-assignment-status.php")
    suspend fun updateAssignmentStatus(
        @Field("assignment_id") assignmentId: String,
        @Field("status") status: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("api/api_app/mark-assignment-received.php")
    suspend fun markAssignmentReceived(
        @Field("incident_id") incidentId: String,
        @Field("responder_id") responderId: Int
    ): Response<Unit>
}