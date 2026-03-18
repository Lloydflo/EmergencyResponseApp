package com.ers.emergencyresponseapp.features.assigned  // ✅ correct

import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.features.assigned.IncidentDto

data class IncidentDto(
    val id: String,
    val type: String,
    val priority: String? = null,
    val location: String,
    val status: String,
    val description: String? = null,
    val assignedTo: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

fun IncidentDto.toDomain(): Incident {
    return Incident(
        id           = this.id,
        type         = IncidentType.entries.firstOrNull {
            it.name.equals(this.type.trim(), ignoreCase = true)
        } ?: IncidentType.MEDICAL,
        priority     = IncidentPriority.entries.firstOrNull {
            it.name.equals(this.priority?.trim(), ignoreCase = true)
        } ?: IncidentPriority.MEDIUM,
        location     = this.location,
        timeReported = java.util.Date(),
        status       = IncidentStatus.entries.firstOrNull {
            it.name.equals(this.status.trim(), ignoreCase = true)
        } ?: IncidentStatus.REPORTED,
        description  = this.description ?: "",
        assignedTo   = this.assignedTo,
        latitude     = this.latitude,
        longitude    = this.longitude
    )
}
// ❌ NO interface IncidentApi here — delete it if present