package com.ers.emergencyresponseapp.network  // ✅ must match folder

import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

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