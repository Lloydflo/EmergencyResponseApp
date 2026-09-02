package com.ers.emergencyresponseapp.alert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dispatcher-issued "quick action" protocol kinds. */
enum class ProtocolAlertKind {
    BROADCAST,
    LOCKDOWN,
    MASS_CASUALTY
}

data class CriticalAlert(
    val id: String,
    val kind: ProtocolAlertKind,
    val title: String,
    val message: String,
    val priority: String,
    val receivedAtMillis: Long
)

/**
 * Process-wide queue of unacknowledged dispatcher protocol alerts (Emergency
 * Broadcast, Lockdown Protocol, Mass Casualty). [com.ers.emergencyresponseapp.messaging.EmergencyFirebaseMessagingService]
 * pushes into this queue whenever such an alert arrives — while the app is in
 * the foreground, background, or was closed.
 *
 * Unlike ordinary notifications, these are command-level alerts: the queue
 * exists so a blocking, full-screen overlay can be rendered on top of
 * whatever screen the responder is currently looking at (see
 * `CriticalAlertOverlay`), and it survives navigation between screens because
 * it lives above the nav host in `MainActivity`. An alert stays in the queue
 * until the responder explicitly acknowledges it — it is never silently
 * cleared by navigating away or backgrounding the app.
 */
object CriticalAlertCenter {
    private const val MAX_QUEUED = 20

    private val _alerts = MutableStateFlow<List<CriticalAlert>>(emptyList())
    val alerts: StateFlow<List<CriticalAlert>> = _alerts.asStateFlow()

    fun push(alert: CriticalAlert) {
        val current = _alerts.value
        if (current.any { it.id == alert.id }) return
        _alerts.value = (current + alert).takeLast(MAX_QUEUED)
    }

    fun acknowledge(id: String) {
        _alerts.value = _alerts.value.filterNot { it.id == id }
    }

    fun acknowledgeAll() {
        _alerts.value = emptyList()
    }
}
