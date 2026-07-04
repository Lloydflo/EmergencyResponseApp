package com.ers.emergencyresponseapp.data

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import com.ers.emergencyresponseapp.network.BackupRequestStatusDto
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
    ): Int? {  // returns the new request's id, or null on failure
        return try {
            val response = api.sendBackupRequest(
                responderId, responderName, department, requestedDepartment,
                resources, if (isFullBackup) 1 else 0, incidentId
            )
            if (response.success) response.id else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBackupRequestStatus(requestId: Int): BackupRequestStatusDto? {
        return try {
            val response = api.getBackupRequestStatus(requestId)
            if (response.success) response.request else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getActiveIncidents(responderId: Int): List<IncidentDto> {
        val response = api.getActiveIncidents(responderId)

        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        }

        throw Exception("Failed to load active incidents: ${response.code()}")
    }

}