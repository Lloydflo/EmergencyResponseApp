package com.ers.emergencyresponseapp.features.assigned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.data.IncidentRepository
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Network state is kept separately for assigned and active incidents. A failed
 * refresh never replaces a previously successful list with an empty list.
 */
data class AssignedIncidentsUiState(
    val loadingAssigned: Boolean = false,
    val loadingActive: Boolean = false,
    val incidents: List<IncidentDto> = emptyList(),
    val activeIncidents: List<IncidentDto> = emptyList(),
    val assignedError: String? = null,
    val activeError: String? = null,
    val actionError: String? = null,
    val assignedLastSuccessMillis: Long? = null,
    val activeLastSuccessMillis: Long? = null,
    val hasCompletedAssignedRequest: Boolean = false,
    val hasCompletedActiveRequest: Boolean = false
) {
    val loading: Boolean get() = loadingAssigned || loadingActive
    val error: String? get() = assignedError ?: activeError ?: actionError
}

class AssignedIncidentsViewModel(
    private val repo: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AssignedIncidentsUiState())
    val ui: StateFlow<AssignedIncidentsUiState> = _ui

    private var assignedLoadJob: Job? = null
    private var activeLoadJob: Job? = null
    private var statusUpdateJob: Job? = null

    fun load(responderId: Int) {
        if (responderId <= 0 || assignedLoadJob?.isActive == true) return

        assignedLoadJob = viewModelScope.launch {
            _ui.update { it.copy(loadingAssigned = true, assignedError = null) }

            try {
                val list = repo.getAssignedIncidents(responderId)
                val successTime = System.currentTimeMillis()

                // Publish the fetched assignment list first. Presence sync and
                // acknowledgement are secondary side effects and must not hide a
                // valid incident when one of those endpoints temporarily fails.
                _ui.update {
                    it.copy(
                        loadingAssigned = false,
                        incidents = list,
                        assignedError = null,
                        assignedLastSuccessMillis = successTime,
                        hasCompletedAssignedRequest = true
                    )
                }

                runCatching { repo.syncUnitStatus(responderId) }

                for (incident in list) {
                    val needsAcknowledgement =
                        incident.status.equals("pending", true) ||
                                incident.status.equals("assigned", true)
                    if (!needsAcknowledgement) continue

                    runCatching {
                        repo.markAssignmentReceived(
                            assignmentId = incident.assignment_id ?: incident.id,
                            responderId = responderId
                        )
                    }
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        loadingAssigned = false,
                        assignedError = error.toUserMessage("assigned incidents"),
                        hasCompletedAssignedRequest = true
                    )
                }
            }
        }
    }

    fun loadActive(responderId: Int) {
        if (responderId <= 0 || activeLoadJob?.isActive == true) return

        activeLoadJob = viewModelScope.launch {
            _ui.update { it.copy(loadingActive = true, activeError = null) }

            try {
                val list = repo.getActiveIncidents(responderId)
                _ui.update {
                    it.copy(
                        loadingActive = false,
                        activeIncidents = list,
                        activeError = null,
                        activeLastSuccessMillis = System.currentTimeMillis(),
                        hasCompletedActiveRequest = true
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        loadingActive = false,
                        activeError = error.toUserMessage("active incidents"),
                        hasCompletedActiveRequest = true
                    )
                }
            }
        }
    }

    fun updateStatus(
        assignmentId: String,
        status: String,
        responderId: Int
    ) {
        if (responderId <= 0 || statusUpdateJob?.isActive == true) return

        statusUpdateJob = viewModelScope.launch {
            _ui.update { it.copy(actionError = null) }
            try {
                val success = repo.updateAssignmentStatus(
                    assignmentId = assignmentId,
                    responderId = responderId,
                    status = status
                )

                if (success) {
                    load(responderId)
                } else {
                    _ui.update {
                        it.copy(actionError = "The assignment status update was rejected.")
                    }
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        actionError = error.toUserMessage("the assignment status")
                    )
                }
            }
        }
    }


    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    private fun Throwable.toUserMessage(operation: String): String {
        return when (this) {
            is SocketTimeoutException ->
                "Dispatch timed out while loading $operation. Please try again."

            is IOException ->
                "Unable to reach dispatch while loading $operation. Check your connection."

            else -> message
                ?.takeIf { it.isNotBlank() }
                ?: "Unable to load $operation."
        }
    }
}
