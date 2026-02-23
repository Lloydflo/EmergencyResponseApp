package com.ers.emergencyresponseapp.data

import android.util.Log
import kotlinx.coroutines.delay
import com.ers.emergencyresponseapp.network.AssignedIncidentsRequest
import com.ers.emergencyresponseapp.network.IncidentDto
import com.ers.emergencyresponseapp.network.RetrofitProvider

class IncidentRepository {
    suspend fun getAssignedIncidents(department: String): List<IncidentDto> {
        val res = RetrofitProvider.incidentsApi.getAssignedIncidents(
            AssignedIncidentsRequest(department = department.lowercase())
        )
        if (!res.success) throw IllegalStateException(res.message ?: "Failed to load incidents")
        return res.incidents
    }
}

/**
 * Lightweight incident repository stub. Replace with real Firebase/REST implementation.
 */
object DefaultIncidentRepository {
    private const val TAG = "IncidentRepo"

    /**
     * Update the status of an incident. Currently a stub that simulates network latency.
     * Replace with Firebase/Firestore update calls as needed.
     */
    suspend fun updateIncidentStatus(incidentId: Int, newStatus: String): Boolean {
        Log.i(TAG, "Updating incident $incidentId -> $newStatus (stub)")
        // Simulate network delay
        delay(400L)
        // Simulate success
        return true
    }
}

