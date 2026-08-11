package com.ers.emergencyresponseapp.messaging

import android.content.Context
import com.ers.emergencyresponseapp.AppScreenTracker
import com.ers.emergencyresponseapp.AppState
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.notification.PushTokenManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue


private val SUPPORTED_NOTIFICATION_TYPES = setOf(
    "private_chat", "private_message", "new_private_message", "chat_message",
    "department_chat", "department_message", "group_chat", "group_message",
    "broadcast", "emergency_broadcast", "new_broadcast", "operational_broadcast",
    "assigned_incident", "new_assigned_incident", "incident_assigned",
    "new_assignment", "dispatch_assignment", "assignment"
)

/**
 * Receives data-only FCM events so alerts can be shown while the app is in the
 * background or the process is not open. The PHP/FCM sender should include the
 * event fields documented in FCM_NOTIFICATION_PAYLOADS.md.
 */
class EmergencyFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val responderId = currentResponderId()
        if (responderId > 0) {
            serviceScope.launch {
                PushTokenManager.registerProvidedToken(
                    context = applicationContext,
                    responderId = responderId,
                    token = token
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val responderId = currentResponderId()
        if (responderId <= 0) return

        val rawType = firstNonBlank(
            data,
            "type",
            "event",
            "event_type",
            "notification_type"
        ).lowercase(Locale.US)
            .replace('-', '_')
            .replace(' ', '_')

        // Be tolerant of legacy dispatch payloads that omitted the event type,
        // or used `type` for the incident classification (for example, "fire").
        val hasAssignmentMarker = firstNonBlank(
            data,
            "assignment_id",
            "assignmentId"
        ).isNotBlank()
        val type = when {
            rawType in SUPPORTED_NOTIFICATION_TYPES -> rawType
            hasAssignmentMarker -> "assigned_incident"
            else -> rawType
        }

        when (type) {
            "private_chat", "private_message", "new_private_message", "chat_message" -> {
                val intendedRecipient = firstInt(data, "recipient_id", "recipientId", "responder_id")
                if (intendedRecipient != null && intendedRecipient != responderId) return

                val threadId = firstNonBlank(data, "thread_id", "threadId")
                val senderId = firstNonBlank(data, "sender_id", "senderId", "peer_id")
                val messageId = firstNonBlank(data, "message_id", "messageId")
                    .ifBlank { message.messageId.orEmpty() }
                markPrivateMessageDelivered(threadId, messageId)
                if (isViewingThread(threadId)) return

                val eventKey = "private:${threadId.ifBlank { senderId }}:${messageId.ifBlank { message.sentTime.toString() }}"

                AppNotificationManager.showPrivateChat(
                    context = applicationContext,
                    eventKey = eventKey,
                    peerId = senderId,
                    threadId = threadId,
                    senderName = firstNonBlank(data, "sender_name", "senderName", "title")
                        .ifBlank { message.notification?.title.orEmpty() },
                    body = firstNonBlank(data, "body", "preview", "message", "text")
                        .ifBlank { message.notification?.body.orEmpty() }
                )
            }

            "department_chat", "department_message", "group_chat", "group_message" -> {
                val intendedRecipient = firstInt(
                    data,
                    "recipient_id",
                    "recipientId",
                    "responder_id"
                )
                if (intendedRecipient != null && intendedRecipient != responderId) return

                val groupId = firstInt(data, "group_id", "groupId", "department_id") ?: return
                val threadId = "group_$groupId"
                if (isViewingThread(threadId)) return

                val messageId = firstNonBlank(data, "message_id", "messageId")
                    .ifBlank { message.messageId.orEmpty() }
                AppNotificationManager.showDepartmentChat(
                    context = applicationContext,
                    eventKey = "department:$groupId:${messageId.ifBlank { message.sentTime.toString() }}",
                    groupId = groupId,
                    groupName = firstNonBlank(data, "group_name", "groupName", "department_name", "title")
                        .ifBlank { message.notification?.title.orEmpty() },
                    senderName = firstNonBlank(data, "sender_name", "senderName"),
                    body = firstNonBlank(data, "body", "preview", "message", "text")
                        .ifBlank { message.notification?.body.orEmpty() }
                )
            }

            "broadcast", "emergency_broadcast", "new_broadcast", "operational_broadcast" -> {
                val intendedRecipient = firstInt(data, "recipient_id", "recipientId", "responder_id")
                if (intendedRecipient != null && intendedRecipient != responderId) return

                val broadcastId = firstLong(data, "broadcast_id", "broadcastId")
                    ?: stableLongId(message.messageId, message.sentTime)
                AppNotificationManager.showBroadcast(
                    context = applicationContext,
                    eventKey = "broadcast:$broadcastId",
                    broadcastId = broadcastId,
                    incidentId = firstLong(data, "incident_id", "incidentId") ?: 0L,
                    priority = firstNonBlank(data, "priority", "severity"),
                    title = firstNonBlank(data, "title", "subject")
                        .ifBlank { message.notification?.title.orEmpty() },
                    body = firstNonBlank(data, "body", "message", "text", "description")
                        .ifBlank { message.notification?.body.orEmpty() }
                )
            }

            "assigned_incident", "new_assigned_incident", "incident_assigned",
            "new_assignment", "dispatch_assignment", "assignment" -> {
                val intendedResponder = firstInt(
                    data,
                    "responder_id",
                    "responderId",
                    "recipient_id",
                    "recipientId"
                )
                if (intendedResponder != null && intendedResponder != responderId) return

                val assignmentId = firstNonBlank(data, "assignment_id", "assignmentId")
                val incidentId = firstLong(data, "incident_id", "incidentId") ?: 0L
                val uniqueId = assignmentId.ifBlank {
                    incidentId.takeIf { it > 0L }?.toString()
                        ?: message.messageId.orEmpty().takeIf { it.isNotBlank() }
                        ?: message.sentTime.toString()
                }

                AppNotificationManager.showAssignedIncident(
                    context = applicationContext,
                    eventKey = "assigned:$uniqueId",
                    assignmentId = assignmentId,
                    incidentId = incidentId,
                    incidentReference = firstNonBlank(
                        data,
                        "reference_no",
                        "reference",
                        "incident_reference"
                    ),
                    incidentType = firstNonBlank(data, "incident_type", "incidentType", "type_label"),
                    priority = firstNonBlank(data, "priority", "severity"),
                    location = firstNonBlank(
                        data,
                        "location",
                        "location_address",
                        "address"
                    ),
                    body = firstNonBlank(data, "body", "message", "description")
                        .ifBlank { message.notification?.body.orEmpty() }
                )
            }
        }
    }

    private fun currentResponderId(): Int = applicationContext
        .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        .getString("user_id", "")
        ?.toIntOrNull()
        ?: 0

    private fun isViewingThread(threadId: String): Boolean =
        threadId.isNotBlank() &&
                AppState.isForeground &&
                AppScreenTracker.currentScreen == "COORDINATION" &&
                AppScreenTracker.currentThreadId == threadId

    /**
     * FCM receipt is the first reliable signal that the destination device
     * received a private message. Never replace READ with a lower status.
     */
    private fun markPrivateMessageDelivered(threadId: String, messageId: String) {
        if (threadId.isBlank() || messageId.isBlank()) return

        FirebaseDatabase.getInstance().reference
            .child("messages")
            .child(threadId)
            .child(messageId)
            .child("status")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val status = currentData.getValue(String::class.java)
                        ?.lowercase(Locale.US)
                    if (status == "sent" || status == "sending") {
                        currentData.value = "delivered"
                    }
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: com.google.firebase.database.DatabaseError?,
                    committed: Boolean,
                    currentData: com.google.firebase.database.DataSnapshot?
                ) = Unit
            })
    }

    private fun firstNonBlank(data: Map<String, String>, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> data[key]?.trim()?.takeIf { it.isNotBlank() } }.orEmpty()

    private fun firstInt(data: Map<String, String>, vararg keys: String): Int? =
        firstNonBlank(data, *keys).toIntOrNull()

    private fun firstLong(data: Map<String, String>, vararg keys: String): Long? =
        firstNonBlank(data, *keys).toLongOrNull()

    private fun stableLongId(messageId: String?, sentTime: Long): Long {
        val hash = messageId.orEmpty().hashCode().toLong().absoluteValue
        return hash.takeIf { it > 0L } ?: sentTime.coerceAtLeast(1L)
    }
}
