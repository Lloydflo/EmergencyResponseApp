package com.ers.emergencyresponseapp.network  // ✅ must match folder

import retrofit2.Response
import retrofit2.http.*

interface IncidentApi {

    @GET("incidents/assigned")
    suspend fun getAssignedIncidents(
        @Header("Authorization") token: String,
        @Query("department") department: String
    ): Response<List<IncidentDto>>

    @POST("incidents/{id}/complete")
    suspend fun markIncidentComplete(
        @Path("id") incidentId: String,
        @Header("Authorization") token: String
    ): Response<Unit>
}