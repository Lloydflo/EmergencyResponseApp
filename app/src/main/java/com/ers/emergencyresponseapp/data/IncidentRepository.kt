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
        responderId: Int,
        status: String
    ): Boolean {
        val response = api.updateAssignmentStatus(
            assignmentId = assignmentId,
            responderId = responderId,
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

    suspend fun syncUnitStatus(responderId: Int): Boolean {
        val response = api.syncUnitStatus(responderId)
        return response.isSuccessful
    }

    suspend fun sendBackupRequest(
        responderId: Int,
        responderName: String,
        department: String,
        requestedDepartment: String,
        resources: String,
        isFullBackup: Boolean,
        incidentId: String
    ): Boolean {
        val response = api.sendBackupRequest(
            responderId = responderId,
            responderName = responderName,
            department = department,
            requestedDepartment = requestedDepartment,
            resources = resources,
            isFullBackup = if (isFullBackup) 1 else 0,
            incidentId = incidentId
        )

        return response.isSuccessful
    }

    suspend fun getActiveIncidents(responderId: Int): List<IncidentDto> {
        val response = api.getActiveIncidents(responderId)

        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }

        throw Exception("Failed to load active incidents: ${response.code()}")
    }
}