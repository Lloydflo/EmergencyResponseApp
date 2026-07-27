package com.ers.emergencyresponseapp.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ers.emergencyresponseapp.MainActivity
import com.ers.emergencyresponseapp.R

object AppNotificationManager {
    const val CHANNEL_PRIVATE_CHAT = "private_chat_messages"
    const val CHANNEL_DEPARTMENT_CHAT = "department_chat_messages"
    const val CHANNEL_BROADCAST = "emergency_broadcasts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PRIVATE_CHAT,
                    "Private responder messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New direct coordination messages"
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_DEPARTMENT_CHAT,
                    "Department messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New messages from approved inter-agency channels"
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_BROADCAST,
                    "Emergency broadcasts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent and critical operational broadcasts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 180, 500, 180, 700)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        )
    }

    fun showPrivateChat(
        context: Context,
        eventKey: String,
        peerId: String,
        threadId: String,
        senderName: String,
        body: String
    ) {
        if (!canNotify(context) || !NotificationEventStore.markIfNew(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_PRIVATE_CHAT)
            putExtra(NotificationNavigation.EXTRA_PEER_ID, peerId)
            putExtra(NotificationNavigation.EXTRA_THREAD_ID, threadId)
        }
        val content = body.ifBlank { "New private message" }.take(500)
        val notification = NotificationCompat.Builder(context, CHANNEL_PRIVATE_CHAT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName.ifBlank { "Responder message" })
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setGroup("private:$threadId")
            .setContentIntent(pendingIntent(context, eventKey, intent))
            .build()

        NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
    }

    fun showDepartmentChat(
        context: Context,
        eventKey: String,
        groupId: Int,
        groupName: String,
        senderName: String,
        body: String
    ) {
        if (!canNotify(context) || !NotificationEventStore.markIfNew(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_DEPARTMENT_CHAT)
            putExtra(NotificationNavigation.EXTRA_GROUP_ID, groupId)
        }
        val content = buildString {
            if (senderName.isNotBlank()) append(senderName).append(": ")
            append(body.ifBlank { "New department message" })
        }.take(500)

        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTMENT_CHAT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(groupName.ifBlank { "Department coordination" })
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setGroup("department:$groupId")
            .setContentIntent(pendingIntent(context, eventKey, intent))
            .build()

        NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
    }

    fun showBroadcast(
        context: Context,
        eventKey: String,
        broadcastId: Long,
        incidentId: Long,
        priority: String,
        title: String,
        body: String
    ) {
        if (!canNotify(context) || !NotificationEventStore.markIfNew(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_BROADCAST)
            putExtra(NotificationNavigation.EXTRA_BROADCAST_ID, broadcastId)
            putExtra(NotificationNavigation.EXTRA_INCIDENT_ID, incidentId)
        }
        val normalizedPriority = priority.uppercase().ifBlank { "ROUTINE" }
        val notificationTitle = title.ifBlank { "Emergency broadcast • $normalizedPriority" }
        val content = body.ifBlank { "A new operational broadcast was issued." }.take(800)

        val notification = NotificationCompat.Builder(context, CHANNEL_BROADCAST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // Critical broadcasts remain dismissible; acknowledgement is tracked by
            // the server, not by creating a permanently stuck Android notification.
            .setContentIntent(pendingIntent(context, eventKey, intent))
            .build()

        NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
    }

    private fun baseIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    private fun pendingIntent(
        context: Context,
        eventKey: String,
        intent: Intent
    ): PendingIntent = PendingIntent.getActivity(
        context,
        eventKey.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
}

private object NotificationEventStore {
    private const val PREFS = "notification_event_dedupe"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    @Synchronized
    fun markIfNew(context: Context, eventKey: String): Boolean {
        if (eventKey.isBlank()) return false
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = preferences.getStringSet(KEY_EVENTS, emptySet()).orEmpty().toMutableSet()
        if (!current.add(eventKey)) return false

        while (current.size > MAX_EVENTS) {
            current.firstOrNull()?.let(current::remove) ?: break
        }
        preferences.edit().putStringSet(KEY_EVENTS, current).apply()
        return true
    }
}
