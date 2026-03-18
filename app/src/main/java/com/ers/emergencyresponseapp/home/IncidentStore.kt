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

    // Simple in-memory completion timestamp map: incidentId -> epochMillis
    private val completionTimeMap = mutableMapOf<String, Long?>()

    fun storeCompletionTime(incidentId: String, epochMillis: Long?) {
        if (epochMillis == null) return
        completionTimeMap[incidentId] = epochMillis
    }

    fun getCompletionTime(incidentId: String): Long? = completionTimeMap[incidentId]

    // Review workflow maps
    // When a responder marks done, we set the incident status to PENDING_REVIEW and record proof/notes/time.
    // The responder then submits a review (their feedback) which moves the incident into SUBMITTED_REVIEW state.
    private val submittedReviewMap = mutableMapOf<String, String?>()

    fun submitReview(incidentId: String, text: String) {
        submittedReviewMap[incidentId] = text
        val idx = incidents.indexOfFirst { it.id == incidentId }
        if (idx >= 0) {
            incidents[idx] = incidents[idx].copy(status = IncidentStatus.SUBMITTED_REVIEW)
        }
    }

    fun getSubmittedReview(incidentId: String): String? = submittedReviewMap[incidentId]

    fun adminApproveReview(incidentId: String) {
        // Admin approves -> mark RESOLVED
        val idx = incidents.indexOfFirst { it.id == incidentId }
        if (idx >= 0) {
            incidents[idx] = incidents[idx].copy(status = IncidentStatus.RESOLVED)
            // record completion timestamp
            storeCompletionTime(incidentId, System.currentTimeMillis())
            // clear submitted review entry as it's now processed
            submittedReviewMap.remove(incidentId)
        }
    }

    fun updateStatus(id: String, newStatus: IncidentStatus) {
        val idx = incidents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            incidents[idx] = incidents[idx].copy(status = newStatus)
        }
    }

    fun markPendingReview(id: String, assignedTo: String? = null) {
        val idx = incidents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val current = incidents[idx]
            incidents[idx] = current.copy(status = IncidentStatus.PENDING_REVIEW, assignedTo = assignedTo ?: current.assignedTo)
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
