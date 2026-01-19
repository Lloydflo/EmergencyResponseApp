package com.ers.emergencyresponseapp.data

import android.util.Log
import kotlinx.coroutines.delay

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

