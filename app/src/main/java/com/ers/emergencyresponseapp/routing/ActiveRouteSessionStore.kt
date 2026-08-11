package com.ers.emergencyresponseapp.routing

import android.content.Context
import android.content.SharedPreferences

enum class ActiveRouteMode {
    ORIGINAL,
    ALTERNATIVE
}

data class ActiveRouteSessionMetadata(
    val incidentId: String,
    val assignmentId: String? = null,
    val responderId: Int,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationAddress: String = ""
) {
    val sessionId: String?
        get() = ActiveRouteSessionStore.identityFor(
            incidentId = incidentId,
            assignmentId = assignmentId
        )
}

data class ActiveRouteSession(
    val sessionId: String,
    val incidentId: String,
    val assignmentId: String?,
    val responderId: Int,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationAddress: String,
    val initialOriginLat: Double?,
    val initialOriginLng: Double?,
    val originalRouteJson: String?,
    val approvedRouteJson: String?,
    val selectedMode: ActiveRouteMode,
    val pendingAlternativeRequestId: Long?,
    val pendingAlternativeRequestedAtMillis: Long?,
    val pendingAlternativeStartLat: Double?,
    val pendingAlternativeStartLng: Double?,
    val currentStepIndex: Int
)

/**
 * Durable metadata for the single response route currently active on this
 * device. This store owns only its prefixed keys in `nav_prefs`; existing
 * navigation flags in that preference file are intentionally left untouched.
 *
 * Every mutation requires both the responder and the computed session ID. This
 * prevents a stale map screen from overwriting a newer assignment after a fast
 * reassignment or delayed coroutine completion.
 */
class ActiveRouteSessionStore internal constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    )

    /**
     * Starts or refreshes an active session. The first destination, route
     * geometry, selected mode, and progress are preserved while the responder
     * and session ID are unchanged. A different valid active response is
     * rejected; malformed owned state is cleared before a new session begins.
     */
    fun beginSession(metadata: ActiveRouteSessionMetadata): ActiveRouteSession? =
        synchronized(STORE_LOCK) {
            val normalized = metadata.normalizedOrNull() ?: return@synchronized null
            val sessionId = normalized.sessionId ?: return@synchronized null
            val storedResponderId = preferences.getInt(KEY_RESPONDER_ID, 0)
            val validExistingSession = if (
                preferences.getBoolean(KEY_ACTIVE, false) &&
                storedResponderId > 0
            ) {
                readLocked(storedResponderId, null)
            } else {
                null
            }
            val sameSession = validExistingSession?.responderId == normalized.responderId &&
                    validExistingSession.sessionId == sessionId

            // Route replacement is an explicit operational action. Never let
            // an incidental screen open erase another incident's active route.
            if (validExistingSession != null && !sameSession) {
                return@synchronized null
            }

            val editor = preferences.edit()
            if (!sameSession) {
                removeOwnedKeys(editor)
            }

            editor.putBoolean(KEY_ACTIVE, true)

            if (!sameSession) {
                editor
                    .putString(KEY_SESSION_ID, sessionId)
                    .putString(KEY_INCIDENT_ID, normalized.incidentId)
                    .putString(KEY_ASSIGNMENT_ID, normalized.assignmentId)
                    .putInt(KEY_RESPONDER_ID, normalized.responderId)
                    .putString(KEY_DESTINATION_LAT, normalized.destinationLat.toString())
                    .putString(KEY_DESTINATION_LNG, normalized.destinationLng.toString())
                    .putString(KEY_DESTINATION_ADDRESS, normalized.destinationAddress)
                    .putString(KEY_SELECTED_MODE, ActiveRouteMode.ORIGINAL.name)
                    .putInt(KEY_CURRENT_STEP_INDEX, 0)
            }

            editor.apply()
            readLocked(normalized.responderId, sessionId)
        }

    /**
     * Reads the active route for [responderId]. When [expectedSessionId] is
     * supplied, any other active session is rejected.
     */
    fun read(
        responderId: Int,
        expectedSessionId: String? = null
    ): ActiveRouteSession? = synchronized(STORE_LOCK) {
        val normalizedExpectedSessionId = if (expectedSessionId == null) {
            null
        } else {
            expectedSessionId.trim().takeIf { it.isNotEmpty() }
                ?: return@synchronized null
        }
        readLocked(responderId, normalizedExpectedSessionId)
    }

    fun saveInitialOrigin(
        responderId: Int,
        sessionId: String,
        latitude: Double,
        longitude: Double
    ): Boolean = synchronized(STORE_LOCK) {
        if (!isMatchingActiveSession(responderId, sessionId) ||
            !isValidCoordinate(latitude, longitude)
        ) {
            return@synchronized false
        }

        val existingLatitude = preferences.getString(KEY_INITIAL_ORIGIN_LAT, null)
            ?.toDoubleOrNull()
        val existingLongitude = preferences.getString(KEY_INITIAL_ORIGIN_LNG, null)
            ?.toDoubleOrNull()
        if (existingLatitude != null || existingLongitude != null) {
            return@synchronized existingLatitude != null &&
                    existingLongitude != null &&
                    isValidCoordinate(existingLatitude, existingLongitude) &&
                    existingLatitude == latitude &&
                    existingLongitude == longitude
        }

        preferences.edit()
            .putString(KEY_INITIAL_ORIGIN_LAT, latitude.toString())
            .putString(KEY_INITIAL_ORIGIN_LNG, longitude.toString())
            .apply()
        true
    }

    fun saveOriginalRoute(
        responderId: Int,
        sessionId: String,
        routeJson: String
    ): Boolean = synchronized(STORE_LOCK) {
        val normalizedJson = routeJson.trim()
        if (!isMatchingActiveSession(responderId, sessionId) || normalizedJson.isEmpty()) {
            return@synchronized false
        }

        preferences.getString(KEY_ORIGINAL_ROUTE_JSON, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { existingRoute ->
                return@synchronized existingRoute == normalizedJson
            }

        preferences.edit()
            .putString(KEY_ORIGINAL_ROUTE_JSON, normalizedJson)
            .apply()
        true
    }

    /**
     * Commits an externally supplied alternative only for the request that is
     * still pending. Route JSON, selected mode, request cleanup, and progress
     * are written atomically so a delayed map coroutine cannot accept request A
     * after a newer request B has replaced it.
     */
    fun acceptApprovedAlternative(
        responderId: Int,
        sessionId: String,
        requestId: Long,
        routeJson: String
    ): Boolean = synchronized(STORE_LOCK) {
        val normalizedJson = routeJson.trim()
        if (
            !isMatchingActiveSession(responderId, sessionId) ||
            requestId <= 0L ||
            normalizedJson.isEmpty() ||
            preferences.getLong(KEY_PENDING_ALTERNATIVE_REQUEST_ID, 0L) != requestId
        ) {
            return@synchronized false
        }

        preferences.edit()
            .putString(KEY_APPROVED_ROUTE_JSON, normalizedJson)
            .putString(KEY_SELECTED_MODE, ActiveRouteMode.ALTERNATIVE.name)
            .remove(KEY_PENDING_ALTERNATIVE_REQUEST_ID)
            .remove(KEY_PENDING_ALTERNATIVE_REQUESTED_AT)
            .remove(KEY_PENDING_ALTERNATIVE_START_LAT)
            .remove(KEY_PENDING_ALTERNATIVE_START_LNG)
            .putInt(KEY_CURRENT_STEP_INDEX, 0)
            .apply()
        true
    }

    fun selectMode(
        responderId: Int,
        sessionId: String,
        mode: ActiveRouteMode
    ): Boolean = synchronized(STORE_LOCK) {
        if (!isMatchingActiveSession(responderId, sessionId)) {
            return@synchronized false
        }
        if (mode == ActiveRouteMode.ALTERNATIVE &&
            preferences.getString(KEY_APPROVED_ROUTE_JSON, null).isNullOrBlank()
        ) {
            return@synchronized false
        }

        preferences.edit()
            .putString(KEY_SELECTED_MODE, mode.name)
            .apply()
        true
    }

    fun savePendingAlternative(
        responderId: Int,
        sessionId: String,
        requestId: Long,
        startLat: Double,
        startLng: Double,
        requestedAtMillis: Long
    ): Boolean = synchronized(STORE_LOCK) {
        if (
            !isMatchingActiveSession(responderId, sessionId) ||
            requestId <= 0L ||
            !isValidCoordinate(startLat, startLng) ||
            requestedAtMillis <= 0L
        ) {
            return@synchronized false
        }

        preferences.edit()
            .putLong(KEY_PENDING_ALTERNATIVE_REQUEST_ID, requestId)
            .putLong(KEY_PENDING_ALTERNATIVE_REQUESTED_AT, requestedAtMillis)
            .putString(KEY_PENDING_ALTERNATIVE_START_LAT, startLat.toString())
            .putString(KEY_PENDING_ALTERNATIVE_START_LNG, startLng.toString())
            .apply()
        true
    }

    fun clearPendingAlternative(
        responderId: Int,
        sessionId: String,
        requestId: Long
    ): Boolean = synchronized(STORE_LOCK) {
        if (
            !isMatchingActiveSession(responderId, sessionId) ||
            requestId <= 0L ||
            preferences.getLong(KEY_PENDING_ALTERNATIVE_REQUEST_ID, 0L) != requestId
        ) {
            return@synchronized false
        }

        preferences.edit()
            .remove(KEY_PENDING_ALTERNATIVE_REQUEST_ID)
            .remove(KEY_PENDING_ALTERNATIVE_REQUESTED_AT)
            .remove(KEY_PENDING_ALTERNATIVE_START_LAT)
            .remove(KEY_PENDING_ALTERNATIVE_START_LNG)
            .apply()
        true
    }

    fun saveStepIndex(
        responderId: Int,
        sessionId: String,
        stepIndex: Int
    ): Boolean = synchronized(STORE_LOCK) {
        if (!isMatchingActiveSession(responderId, sessionId) || stepIndex < 0) {
            return@synchronized false
        }

        preferences.edit()
            .putInt(KEY_CURRENT_STEP_INDEX, stepIndex)
            .apply()
        true
    }

    /** Clears the active session only when its responder and identity still match. */
    fun clearSession(responderId: Int, sessionId: String): Boolean =
        synchronized(STORE_LOCK) {
            if (!isMatchingActiveSession(responderId, sessionId)) {
                return@synchronized false
            }
            removeOwnedKeys(preferences.edit()).apply()
            true
        }

    /** Unconditionally clears only this store's keys, leaving other nav prefs intact. */
    fun clearSession() = synchronized(STORE_LOCK) {
        removeOwnedKeys(preferences.edit()).apply()
    }

    private fun readLocked(
        responderId: Int,
        expectedSessionId: String?
    ): ActiveRouteSession? {
        if (responderId <= 0 || !preferences.getBoolean(KEY_ACTIVE, false)) return null
        if (preferences.getInt(KEY_RESPONDER_ID, 0) != responderId) return null

        val incidentId = preferences.getString(KEY_INCIDENT_ID, null)
            ?.trim()
            .orEmpty()
        val assignmentId = preferences.getString(KEY_ASSIGNMENT_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val storedSessionId = preferences.getString(KEY_SESSION_ID, null)
            ?.trim()
            .orEmpty()
        val computedSessionId = identityFor(incidentId, assignmentId) ?: return null

        if (incidentId.isEmpty() || storedSessionId != computedSessionId) return null
        if (expectedSessionId != null && storedSessionId != expectedSessionId) return null

        val destinationLat = preferences.getString(KEY_DESTINATION_LAT, null)
            ?.toDoubleOrNull()
            ?: return null
        val destinationLng = preferences.getString(KEY_DESTINATION_LNG, null)
            ?.toDoubleOrNull()
            ?: return null
        if (!isValidCoordinate(destinationLat, destinationLng)) return null

        val initialOriginLat = preferences.getString(KEY_INITIAL_ORIGIN_LAT, null)
            ?.toDoubleOrNull()
        val initialOriginLng = preferences.getString(KEY_INITIAL_ORIGIN_LNG, null)
            ?.toDoubleOrNull()
        val originAbsent = initialOriginLat == null && initialOriginLng == null
        val originValid = initialOriginLat != null && initialOriginLng != null &&
                isValidCoordinate(initialOriginLat, initialOriginLng)
        if (!originAbsent && !originValid) return null

        val approvedRouteJson = preferences.getString(KEY_APPROVED_ROUTE_JSON, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val selectedMode = preferences.getString(
            KEY_SELECTED_MODE,
            ActiveRouteMode.ORIGINAL.name
        )?.let { stored ->
            ActiveRouteMode.entries.firstOrNull { mode -> mode.name == stored }
        } ?: ActiveRouteMode.ORIGINAL
        if (selectedMode == ActiveRouteMode.ALTERNATIVE && approvedRouteJson == null) {
            return null
        }

        val storedPendingAlternativeRequestId = if (
            preferences.contains(KEY_PENDING_ALTERNATIVE_REQUEST_ID)
        ) {
            preferences.getLong(KEY_PENDING_ALTERNATIVE_REQUEST_ID, 0L)
                .takeIf { it > 0L }
        } else {
            null
        }
        val storedPendingAlternativeRequestedAtMillis = if (
            preferences.contains(KEY_PENDING_ALTERNATIVE_REQUESTED_AT)
        ) {
            preferences.getLong(KEY_PENDING_ALTERNATIVE_REQUESTED_AT, 0L)
                .takeIf { it > 0L }
        } else {
            null
        }
        val pendingAlternativeStartLat = preferences
            .getString(KEY_PENDING_ALTERNATIVE_START_LAT, null)
            ?.toDoubleOrNull()
        val pendingAlternativeStartLng = preferences
            .getString(KEY_PENDING_ALTERNATIVE_START_LNG, null)
            ?.toDoubleOrNull()
        val pendingStartValid = pendingAlternativeStartLat != null &&
                pendingAlternativeStartLng != null &&
                isValidCoordinate(pendingAlternativeStartLat, pendingAlternativeStartLng)
        // v17.4 stored only a pending request ID. Ignore that legacy pending
        // request instead of invalidating the otherwise usable locked route.
        val pendingAlternativeRequestId = storedPendingAlternativeRequestId
            ?.takeIf {
                pendingStartValid &&
                        storedPendingAlternativeRequestedAtMillis != null
            }
        val pendingAlternativeRequestedAtMillis =
            storedPendingAlternativeRequestedAtMillis
                ?.takeIf { pendingAlternativeRequestId != null }
        val safePendingStartLat = pendingAlternativeStartLat
            ?.takeIf { pendingAlternativeRequestId != null }
        val safePendingStartLng = pendingAlternativeStartLng
            ?.takeIf { pendingAlternativeRequestId != null }

        val currentStepIndex = preferences.getInt(KEY_CURRENT_STEP_INDEX, 0)
        if (currentStepIndex < 0) return null

        return ActiveRouteSession(
            sessionId = storedSessionId,
            incidentId = incidentId,
            assignmentId = assignmentId,
            responderId = responderId,
            destinationLat = destinationLat,
            destinationLng = destinationLng,
            destinationAddress = preferences.getString(KEY_DESTINATION_ADDRESS, "")
                .orEmpty(),
            initialOriginLat = initialOriginLat,
            initialOriginLng = initialOriginLng,
            originalRouteJson = preferences.getString(KEY_ORIGINAL_ROUTE_JSON, null)
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            approvedRouteJson = approvedRouteJson,
            selectedMode = selectedMode,
            pendingAlternativeRequestId = pendingAlternativeRequestId,
            pendingAlternativeRequestedAtMillis =
                pendingAlternativeRequestedAtMillis,
            pendingAlternativeStartLat = safePendingStartLat,
            pendingAlternativeStartLng = safePendingStartLng,
            currentStepIndex = currentStepIndex
        )
    }

    private fun isMatchingActiveSession(responderId: Int, sessionId: String): Boolean {
        val normalizedSessionId = sessionId.trim()
        return responderId > 0 &&
                normalizedSessionId.isNotEmpty() &&
                preferences.getBoolean(KEY_ACTIVE, false) &&
                preferences.getInt(KEY_RESPONDER_ID, 0) == responderId &&
                preferences.getString(KEY_SESSION_ID, null) == normalizedSessionId
    }

    private fun ActiveRouteSessionMetadata.normalizedOrNull(): ActiveRouteSessionMetadata? {
        val normalizedIncidentId = incidentId.trim()
        val normalizedAssignmentId = assignmentId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedIncidentId.isEmpty() || responderId <= 0) return null
        if (!isValidCoordinate(destinationLat, destinationLng)) return null

        return copy(
            incidentId = normalizedIncidentId,
            assignmentId = normalizedAssignmentId,
            destinationAddress = destinationAddress.trim()
        )
    }

    private fun removeOwnedKeys(editor: SharedPreferences.Editor): SharedPreferences.Editor {
        OWNED_KEYS.forEach(editor::remove)
        return editor
    }

    companion object {
        private const val PREFS_NAME = "nav_prefs"
        private const val KEY_PREFIX = "active_route_session_"
        private const val KEY_ACTIVE = "${KEY_PREFIX}active"
        private const val KEY_SESSION_ID = "${KEY_PREFIX}id"
        private const val KEY_INCIDENT_ID = "${KEY_PREFIX}incident_id"
        private const val KEY_ASSIGNMENT_ID = "${KEY_PREFIX}assignment_id"
        private const val KEY_RESPONDER_ID = "${KEY_PREFIX}responder_id"
        private const val KEY_DESTINATION_LAT = "${KEY_PREFIX}destination_lat"
        private const val KEY_DESTINATION_LNG = "${KEY_PREFIX}destination_lng"
        private const val KEY_DESTINATION_ADDRESS = "${KEY_PREFIX}destination_address"
        private const val KEY_INITIAL_ORIGIN_LAT = "${KEY_PREFIX}initial_origin_lat"
        private const val KEY_INITIAL_ORIGIN_LNG = "${KEY_PREFIX}initial_origin_lng"
        private const val KEY_ORIGINAL_ROUTE_JSON = "${KEY_PREFIX}original_route_json"
        private const val KEY_APPROVED_ROUTE_JSON = "${KEY_PREFIX}approved_route_json"
        private const val KEY_SELECTED_MODE = "${KEY_PREFIX}selected_mode"
        private const val KEY_PENDING_ALTERNATIVE_REQUEST_ID =
            "${KEY_PREFIX}pending_alternative_request_id"
        private const val KEY_PENDING_ALTERNATIVE_REQUESTED_AT =
            "${KEY_PREFIX}pending_alternative_requested_at"
        private const val KEY_PENDING_ALTERNATIVE_START_LAT =
            "${KEY_PREFIX}pending_alternative_start_lat"
        private const val KEY_PENDING_ALTERNATIVE_START_LNG =
            "${KEY_PREFIX}pending_alternative_start_lng"
        private const val KEY_CURRENT_STEP_INDEX = "${KEY_PREFIX}current_step_index"

        private val OWNED_KEYS = setOf(
            KEY_ACTIVE,
            KEY_SESSION_ID,
            KEY_INCIDENT_ID,
            KEY_ASSIGNMENT_ID,
            KEY_RESPONDER_ID,
            KEY_DESTINATION_LAT,
            KEY_DESTINATION_LNG,
            KEY_DESTINATION_ADDRESS,
            KEY_INITIAL_ORIGIN_LAT,
            KEY_INITIAL_ORIGIN_LNG,
            KEY_ORIGINAL_ROUTE_JSON,
            KEY_APPROVED_ROUTE_JSON,
            KEY_SELECTED_MODE,
            KEY_PENDING_ALTERNATIVE_REQUEST_ID,
            KEY_PENDING_ALTERNATIVE_REQUESTED_AT,
            KEY_PENDING_ALTERNATIVE_START_LAT,
            KEY_PENDING_ALTERNATIVE_START_LNG,
            KEY_CURRENT_STEP_INDEX
        )

        private val STORE_LOCK = Any()

        /** Assignment identity wins; incident identity is the fallback. */
        fun identityFor(incidentId: String, assignmentId: String?): String? {
            val normalizedAssignmentId = assignmentId?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedAssignmentId != null) {
                return "assignment:$normalizedAssignmentId"
            }

            val normalizedIncidentId = incidentId.trim()
            return normalizedIncidentId.takeIf { it.isNotEmpty() }
                ?.let { "incident:$it" }
        }

        private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() &&
                    longitude.isFinite() &&
                    latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0
    }
}
