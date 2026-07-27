package com.ers.emergencyresponseapp.notification

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NotificationDestination {
    data class PrivateChat(
        val peerId: String,
        val threadId: String
    ) : NotificationDestination

    data class DepartmentChat(
        val groupId: Int
    ) : NotificationDestination

    data class Broadcast(
        val broadcastId: Long,
        val incidentId: Long
    ) : NotificationDestination
}

/**
 * Process-local hand-off from notification intents to Compose navigation.
 * The target is cleared only after the destination screen has consumed it.
 */
object NotificationNavigation {
    const val EXTRA_DESTINATION = "ers_notification_destination"
    const val EXTRA_PEER_ID = "ers_notification_peer_id"
    const val EXTRA_THREAD_ID = "ers_notification_thread_id"
    const val EXTRA_GROUP_ID = "ers_notification_group_id"
    const val EXTRA_BROADCAST_ID = "ers_notification_broadcast_id"
    const val EXTRA_INCIDENT_ID = "ers_notification_incident_id"

    const val DEST_PRIVATE_CHAT = "private_chat"
    const val DEST_DEPARTMENT_CHAT = "department_chat"
    const val DEST_BROADCAST = "broadcast"

    private val _destination = MutableStateFlow<NotificationDestination?>(null)
    val destination: StateFlow<NotificationDestination?> = _destination.asStateFlow()

    fun publishFromIntent(intent: Intent?) {
        val source = intent ?: return
        val parsed = when (source.getStringExtra(EXTRA_DESTINATION)) {
            DEST_PRIVATE_CHAT -> {
                val peerId = source.getStringExtra(EXTRA_PEER_ID).orEmpty()
                val threadId = source.getStringExtra(EXTRA_THREAD_ID).orEmpty()
                if (peerId.isBlank()) null else NotificationDestination.PrivateChat(peerId, threadId)
            }

            DEST_DEPARTMENT_CHAT -> {
                source.getIntExtra(EXTRA_GROUP_ID, 0)
                    .takeIf { it > 0 }
                    ?.let(NotificationDestination::DepartmentChat)
            }

            DEST_BROADCAST -> {
                val broadcastId = source.getLongExtra(EXTRA_BROADCAST_ID, 0L)
                val incidentId = source.getLongExtra(EXTRA_INCIDENT_ID, 0L)
                if (broadcastId <= 0L) null
                else NotificationDestination.Broadcast(broadcastId, incidentId)
            }

            else -> null
        }

        _destination.value = parsed
        // Remove one-shot routing extras from the Activity intent so an Android
        // configuration change does not reopen an already consumed alert.
        source.removeExtra(EXTRA_DESTINATION)
        source.removeExtra(EXTRA_PEER_ID)
        source.removeExtra(EXTRA_THREAD_ID)
        source.removeExtra(EXTRA_GROUP_ID)
        source.removeExtra(EXTRA_BROADCAST_ID)
        source.removeExtra(EXTRA_INCIDENT_ID)
    }

    fun clear(expected: NotificationDestination? = null) {
        if (expected == null || _destination.value == expected) {
            _destination.value = null
        }
    }
}
