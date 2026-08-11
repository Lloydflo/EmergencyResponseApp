package com.ers.emergencyresponseapp.home

/**
 * Pure responder-workflow rules shared by Home and live navigation.
 *
 * Keeping these decisions outside Compose prevents a stale screen from
 * accidentally regressing an assignment or accepting unsafe coordinates.
 */
enum class IncidentPrimaryAction {
    START_RESPONSE,
    RESUME_NAVIGATION,
    COMPLETE_INCIDENT,
    NONE
}

object ResponderWorkflowPolicy {
    const val MIN_ARRIVAL_RADIUS_METERS = 40.0
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 50.0
    const val ARRIVAL_EXIT_HYSTERESIS_METERS = 20.0

    fun primaryAction(status: IncidentStatus): IncidentPrimaryAction = when (status) {
        IncidentStatus.REPORTED,
        IncidentStatus.DISPATCHED -> IncidentPrimaryAction.START_RESPONSE

        IncidentStatus.ON_ROUTE -> IncidentPrimaryAction.RESUME_NAVIGATION
        IncidentStatus.ON_SCENE -> IncidentPrimaryAction.COMPLETE_INCIDENT
        IncidentStatus.RESOLVED,
        IncidentStatus.UNKNOWN -> IncidentPrimaryAction.NONE
    }

    fun hasValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        return latitude != null &&
                longitude != null &&
                latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }

    fun canResumeStoredRoute(
        storedIncidentId: String?,
        currentIncidentId: String?
    ): Boolean {
        val stored = storedIncidentId?.trim().orEmpty()
        val current = currentIncidentId?.trim().orEmpty()
        return stored.isNotEmpty() && current.isNotEmpty() && stored == current
    }

    fun arrivalEnterRadiusMeters(accuracyMeters: Float?): Double? {
        val accuracy = accuracyMeters
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: return null

        if (accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return null

        return maxOf(MIN_ARRIVAL_RADIUS_METERS, accuracy)
    }

    fun arrivalExitRadiusMeters(accuracyMeters: Float?): Double? {
        return arrivalEnterRadiusMeters(accuracyMeters)
            ?.plus(ARRIVAL_EXIT_HYSTERESIS_METERS)
    }
}
