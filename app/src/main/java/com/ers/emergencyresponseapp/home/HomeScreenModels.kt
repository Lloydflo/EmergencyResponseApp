package com.ers.emergencyresponseapp.home

import androidx.compose.ui.graphics.Color
import java.util.Date

enum class IncidentType(val displayName: String) {
    FIRE("Fire"),
    MEDICAL("Medical"),
    CRIME("Crime"),
    DISASTER("Disaster")
}

enum class IncidentPriority(val displayName: String, val color: Color) {
    HIGH("High", Color(0xFFD32F2F)),
    MEDIUM("Medium", Color(0xFFFFA000)),
    LOW("Low", Color(0xFF388E3C))
}

enum class IncidentStatus(val displayName: String) {
    REPORTED("Reported"),
    DISPATCHED("Dispatched"),
    ON_ROUTE("On Route"),
    ON_SCENE("On Scene"),
    PENDING_REVIEW("Pending Review"),
    SUBMITTED_REVIEW("Submitted Review"),
    RESOLVED("Resolved")
}

enum class ResponderStatus(val displayName: String, val color: Color) {
    AVAILABLE("Available", Color(0xFF388E3C)),
    ON_DUTY("On Duty", Color(0xFFFFA000)),
    BUSY("Busy", Color(0xFFD32F2F))
}

data class Incident(
    val id: String,
    val type: IncidentType,
    val priority: IncidentPriority,
    val location: String,
    val timeReported: Date,
    val status: IncidentStatus,
    val description: String,
    val assignedTo: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
// network/IncidentDto.kt  (what the API returns)
// network/IncidentDto.kt
data class IncidentDto(
    val id: String,
    val type: String,
    val priority: String? = null,
    val location: String,
    val status: String,
    val description: String? = null,
    val assignedTo: String? = null,
    val timeReported: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

fun IncidentDto.toDomain(): Incident {
    return Incident(
        id          = this.id,
        type        = IncidentType.entries.firstOrNull {
            it.name.equals(this.type.trim(), ignoreCase = true)
        } ?: IncidentType.MEDICAL,
        priority    = IncidentPriority.entries.firstOrNull {
            it.name.equals(this.priority?.trim(), ignoreCase = true)
        } ?: IncidentPriority.MEDIUM,
        location    = this.location,
        timeReported = java.util.Date(),
        status      = IncidentStatus.entries.firstOrNull {
            it.name.equals(this.status.trim(), ignoreCase = true)
        } ?: IncidentStatus.REPORTED,
        description = this.description ?: "",
        assignedTo  = this.assignedTo,
        latitude    = this.latitude,
        longitude   = this.longitude
    )
}
