package com.ers.emergencyresponseapp.messaging

import android.content.Context
import com.ers.emergencyresponseapp.AppScreenTracker
import com.ers.emergencyresponseapp.AppState
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.notification.PushTokenManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EmergencyFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        when (data["type"].orEmpty()) {
            "private_chat" -> {
                val intendedRecipient = data["recipient_id"]?.toIntOrNull()
                if (intendedRecipient != null && intendedRecipient != responderId) return

                val threadId = data["thread_id"].orEmpty()
                if (isViewingThread(threadId)) return

                val senderId = data["sender_id"].orEmpty()
                val messageId = data["message_id"].orEmpty()
                AppNotificationManager.showPrivateChat(
                    context = applicationContext,
                    eventKey = "private:$threadId:$messageId",
                    peerId = senderId,
                    threadId = threadId,
                    senderName = data["sender_name"].orEmpty(),
                    body = data["body"].orEmpty()
                )
            }

            "department_chat" -> {
                val groupId = data["group_id"]?.toIntOrNull() ?: return
                val threadId = "group_$groupId"
                if (isViewingThread(threadId)) return

                val messageId = data["message_id"].orEmpty()
                AppNotificationManager.showDepartmentChat(
                    context = applicationContext,
                    eventKey = "department:$groupId:$messageId",
                    groupId = groupId,
                    groupName = data["group_name"].orEmpty(),
                    senderName = data["sender_name"].orEmpty(),
                    body = data["body"].orEmpty()
                )
            }

            "broadcast" -> {
                val broadcastId = data["broadcast_id"]?.toLongOrNull() ?: return
                AppNotificationManager.showBroadcast(
                    context = applicationContext,
                    eventKey = "broadcast:$broadcastId",
                    broadcastId = broadcastId,
                    incidentId = data["incident_id"]?.toLongOrNull() ?: 0L,
                    priority = data["priority"].orEmpty(),
                    title = data["title"].orEmpty(),
                    body = data["body"].orEmpty()
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
}
