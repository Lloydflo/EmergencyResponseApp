package com.ers.emergencyresponseapp.home

import androidx.compose.runtime.mutableStateListOf

/**
 * Simple in-memory incident store for UI demo purposes. Shared across screens so updates in one screen
 * (e.g., marking resolved) are visible in others (e.g., Reviews & Feedback).
 */
object IncidentStore {
    // Observable list so Compose recomposes on changes
    val incidents = mutableStateListOf<Incident>().apply { addAll(getMockIncidents()) }

    fun updateStatus(id: String, newStatus: IncidentStatus) {
        val idx = incidents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            incidents[idx] = incidents[idx].copy(status = newStatus)
        }
    }

    fun markResolved(id: String, assignedTo: String? = null) {
        val idx = incidents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val current = incidents[idx]
            incidents[idx] = current.copy(status = IncidentStatus.RESOLVED, assignedTo = assignedTo ?: current.assignedTo)
        }
    }

    fun assignIncident(id: String, responderName: String) {
        val idx = incidents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val current = incidents[idx]
            incidents[idx] = current.copy(assignedTo = responderName)
        }
    }
}
