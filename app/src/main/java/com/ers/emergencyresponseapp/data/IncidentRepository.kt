package com.ers.emergencyresponseapp.data

import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import com.ers.emergencyresponseapp.network.BackupRequestStatusDto
import com.ers.emergencyresponseapp.network.MyResourceRequestDto
import com.ers.emergencyresponseapp.network.ResourceRequestStatusDto
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.MyBackupRequestDto

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
    ): Result<Int> {
        return try {
            val response = api.sendBackupRequest(
                responderId, responderName, department, requestedDepartment,
                resources, if (isFullBackup) 1 else 0, incidentId
            )
            if (response.success && response.id != null) {
                Result.success(response.id)
            } else {
                Result.failure(Exception(response.message ?: "Unknown server error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
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

    suspend fun sendResourceRequest(
        responderId: Int,
        responderName: String,
        category: String,
        resourceName: String,
        quantity: Int,
        urgency: String,
        incidentId: String,
        location: String,
        notes: String
    ): Result<Int> {
        return try {
            val response = api.sendResourceRequest(
                responderId, responderName, category, resourceName,
                quantity, urgency, incidentId, location, notes
            )
            if (response.success && response.id != null) {
                Result.success(response.id)
            } else {
                Result.failure(Exception(response.message ?: "Unknown server error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getResourceRequestStatus(requestId: Int): ResourceRequestStatusDto? {
        return try {
            val response = api.getResourceRequestStatus(requestId)
            if (response.success) response.request else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMyResourceRequests(responderId: Int): List<MyResourceRequestDto> {
        return try {
            val response = api.getMyResourceRequests(responderId)
            if (response.success) response.requests ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun cancelResourceRequest(requestId: Int, responderId: Int): Result<Unit> {
        return try {
            val response = api.cancelResourceRequest(requestId, responderId)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Unknown server error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getMyBackupRequests(responderId: Int): List<MyBackupRequestDto> {
        return try {
            val response = api.getMyBackupRequests(responderId)
            if (response.success) response.requests ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

}