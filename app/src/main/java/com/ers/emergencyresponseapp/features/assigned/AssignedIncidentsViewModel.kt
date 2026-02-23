package com.ers.emergencyresponseapp.features.assigned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.data.IncidentRepository
import com.ers.emergencyresponseapp.network.IncidentDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AssignedIncidentsUiState(
    val loading: Boolean = false,
    val incidents: List<IncidentDto> = emptyList(),
    val error: String? = null
)

class AssignedIncidentsViewModel(
    private val repo: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AssignedIncidentsUiState())
    val ui: StateFlow<AssignedIncidentsUiState> = _ui

    fun load(department: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val list = repo.getAssignedIncidents(department)
                _ui.value = AssignedIncidentsUiState(loading = false, incidents = list)
            } catch (e: Exception) {
                _ui.value = AssignedIncidentsUiState(loading = false, error = e.message)
            }
        }
    }
}