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
    val description: String
)
