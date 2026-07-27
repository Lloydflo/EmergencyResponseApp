package com.ers.emergencyresponseapp.features.assigned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.data.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AssignedIncidentsUiState(
    val loading: Boolean = false,
    val incidents: List<IncidentDto> = emptyList(),
    val activeIncidents: List<IncidentDto> = emptyList(),
    val error: String? = null
)



class AssignedIncidentsViewModel(
    private val repo: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AssignedIncidentsUiState())
    val ui: StateFlow<AssignedIncidentsUiState> = _ui

    fun load(responderId: Int) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)

            try {
                val list = repo.getAssignedIncidents(responderId)
                repo.syncUnitStatus(responderId)

                list
                    .filter { it.status.equals("pending", true) || it.status.equals("assigned", true) }
                    .forEach { incident ->
                        repo.markAssignmentReceived(
                            assignmentId = incident.assignment_id ?: incident.id,
                            responderId = responderId
                        )
                    }

                _ui.value = _ui.value.copy(
                    loading = false,
                    incidents = list,
                    error = null
                )

            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load assigned incidents"
                )
            }
        }
    }
    fun loadActive(responderId: Int) {
        viewModelScope.launch {
            try {
                val list = repo.getActiveIncidents(responderId)

                _ui.value = _ui.value.copy(
                    activeIncidents = list,
                    error = null
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    error = e.message ?: "Failed to load active incidents"
                )
            }
        }
    }

    fun updateStatus(
        assignmentId: String,
        status: String,
        responderId: Int
    ) {
        viewModelScope.launch {
            try {
                val success = repo.updateAssignmentStatus(
                    assignmentId = assignmentId,
                    responderId = responderId,
                    status = status
                )

                if (success) {
                    load(responderId)
                } else {
                    _ui.value = _ui.value.copy(error = "The assignment status update was rejected.")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    error = e.message ?: "Failed to update the assignment status"
                )
            }
        }
    }
}