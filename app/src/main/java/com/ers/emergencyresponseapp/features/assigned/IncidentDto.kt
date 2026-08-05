package com.ers.emergencyresponseapp.features.assigned

import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.home.IncidentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val assigned_at: String? = null,
    val incident_id_linked: Boolean? = null
)

fun IncidentDto.toDomain(): Incident = Incident(
    id = id,
    assignmentId = assignment_id,
    type = normalizeIncidentType(type),
    priority = normalizeIncidentPriority(priority),
    location = location.trim().ifBlank { "Location unavailable" },
    timeReported = parseIncidentTime(assigned_at),
    status = normalizeIncidentStatus(status),
    description = description.orEmpty().trim(),
    assignedTo = assignedTo?.trim()?.takeIf { it.isNotBlank() },
    latitude = latitude,
    longitude = longitude
)

private fun normalizeIncidentType(raw: String?): IncidentType {
    val value = raw.orEmpty().trim().lowercase(Locale.US)
    return when {
        value.contains("fire") || value.contains("blaze") || value.contains("smoke") ->
            IncidentType.FIRE
        value.contains("medical") || value.contains("ems") || value.contains("ambulance") ||
                value.contains("health") -> IncidentType.MEDICAL
        value.contains("police") || value.contains("crime") || value.contains("law enforcement") ||
                value.contains("security") -> IncidentType.CRIME
        value.contains("disaster") || value.contains("rescue") || value.contains("evacuation") ||
                value.contains("flood") || value.contains("earthquake") -> IncidentType.DISASTER
        else -> IncidentType.GENERAL
    }
}

private fun normalizeIncidentPriority(raw: String?): IncidentPriority {
    val value = raw.orEmpty().trim().lowercase(Locale.US)
    return when {
        value in setOf("critical", "high", "urgent", "emergency", "1") -> IncidentPriority.HIGH
        value in setOf("medium", "moderate", "normal", "2") -> IncidentPriority.MEDIUM
        value in setOf("low", "routine", "non-urgent", "3") -> IncidentPriority.LOW
        else -> IncidentPriority.UNKNOWN
    }
}

private fun normalizeIncidentStatus(raw: String?): IncidentStatus {
    val value = raw.orEmpty()
        .trim()
        .lowercase(Locale.US)
        .replace('-', '_')
        .replace(' ', '_')

    return when (value) {
        "reported", "new", "open" -> IncidentStatus.REPORTED
        "pending", "assigned", "accepted", "received", "dispatched" -> IncidentStatus.DISPATCHED
        "en_route", "enroute", "on_route", "in_transit" -> IncidentStatus.ON_ROUTE
        "on_scene", "onscene", "arrived" -> IncidentStatus.ON_SCENE
        "pending_review", "for_review", "submitted_review", "review_submitted", "submitted",
        "resolved", "completed", "closed" -> IncidentStatus.RESOLVED
        else -> IncidentStatus.UNKNOWN
    }
}

private fun parseIncidentTime(value: String?): Date {
    if (value.isNullOrBlank()) return Date(0L)

    val formats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    )

    formats.forEach { pattern ->
        runCatching {
            val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            parser.parse(value.trim())
        }.getOrNull()?.let { return it }
    }

    return Date(0L)
}
