package com.ers.emergencyresponseapp.data

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import com.ers.emergencyresponseapp.network.RetrofitProvider

class IncidentRepository {

    private val api = RetrofitProvider.incidentApi

    suspend fun getAssignedIncidents(responderId: Int): List<IncidentDto> {
        val response = api.getAssignedIncidents(responderId)

        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }

        throw Exception("Failed to load assigned incidents: ${response.code()}")
    }

    suspend fun updateAssignmentStatus(
        assignmentId: String,
        status: String
    ): Boolean {
        val response = api.updateAssignmentStatus(
            assignmentId = assignmentId,
            status = status
        )

        return response.isSuccessful
    }
    suspend fun markAssignmentReceived(
        incidentId: String,
        responderId: Int
    ): Boolean {
        val response = api.markAssignmentReceived(incidentId, responderId)
        return response.isSuccessful
    }
}