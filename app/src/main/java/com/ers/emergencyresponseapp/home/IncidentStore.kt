package com.ers.emergencyresponseapp.home

import androidx.compose.runtime.mutableStateListOf

/**
 * Simple in-memory incident store for UI demo purposes. Shared across screens so updates in one screen
 * (e.g., marking resolved) are visible in others (e.g., Reviews & Feedback).
 */
object IncidentStore {
    // Observable list so Compose recomposes on changes
    val incidents = mutableStateListOf<Incident>().apply { addAll(getMockIncidents()) }

    // Simple in-memory proof map: incidentId -> proofUri (file:// or content://)
    private val proofMap = mutableMapOf<String, String?>()

    fun storeProof(incidentId: String, proofUri: String?) {
        if (proofUri == null) return
        proofMap[incidentId] = proofUri
    }

    fun getProofUri(incidentId: String): String? = proofMap[incidentId]

    // Simple in-memory notes map: incidentId -> completion notes
    private val notesMap = mutableMapOf<String, String?>()

    fun storeCompletionNotes(incidentId: String, notes: String?) {
        if (notes == null) return
        notesMap[incidentId] = notes
    }

    fun getCompletionNotes(incidentId: String): String? = notesMap[incidentId]

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
