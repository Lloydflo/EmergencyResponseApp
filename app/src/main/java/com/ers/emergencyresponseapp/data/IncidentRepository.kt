package com.ers.emergencyresponseapp.data

import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.IncidentApi
import com.ers.emergencyresponseapp.network.IncidentDto

class IncidentRepository {

    private val api = RetrofitProvider.incidentApi

    suspend fun getAssignedIncidents(department: String): List<IncidentDto> {
        val token = RetrofitProvider.getToken()

        val response = api.getAssignedIncidents(
            token = "Bearer $token",
            department = department
        )

        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }

        throw Exception("Failed to load incidents: ${response.code()}")
    }

    suspend fun completeIncident(incidentId: String): Boolean {
        val token = RetrofitProvider.getToken()
        val response = api.markIncidentComplete(incidentId, "Bearer $token")
        return response.isSuccessful
    }
}