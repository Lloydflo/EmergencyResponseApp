package com.ers.emergencyresponseapp.features.assigned

import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.home.IncidentType

data class IncidentDto(
    val assignment_id: String? = null,
    val id: String,
    val type: String,
    val priority: String? = null,
    val location: String,
    val status: String,
    val description: String? = null,
    val assignedTo: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val unit_code: String? = null,
    val unit_type: String? = null,
    val unit_status: String? = null,
    val assigned_at: String? = null
)

fun IncidentDto.toDomain(): Incident {
    return Incident(
        id = this.id,
        type = IncidentType.entries.firstOrNull {
            it.name.equals(this.type.trim(), ignoreCase = true)
        } ?: IncidentType.MEDICAL,
        priority = IncidentPriority.entries.firstOrNull {
            it.name.equals(this.priority?.trim(), ignoreCase = true)
        } ?: IncidentPriority.MEDIUM,
        location = this.location,
        timeReported = parseAssignedAt(this.assigned_at),
        status = IncidentStatus.entries.firstOrNull {
            it.name.equals(this.status.trim(), ignoreCase = true)
        } ?: IncidentStatus.REPORTED,
        description = this.description ?: "",
        assignedTo = this.assignedTo,
        latitude = this.latitude,
        longitude = this.longitude
    )
}
private fun parseAssignedAt(value: String?): java.util.Date {
    if (value.isNullOrBlank()) return java.util.Date()

    return try {
        java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).parse(value) ?: java.util.Date()
    } catch (_: Exception) {
        java.util.Date()
    }
}