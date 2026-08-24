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
import java.util.Locale

/**
 * Single notification entry point for coordination messages, broadcasts, and
 * dispatch assignments. Every alert uses a stable event key so an FCM push and
 * the in-app polling fallback cannot notify the responder twice.
 */
object AppNotificationManager {
    const val CHANNEL_PRIVATE_CHAT = "private_chat_messages"
    const val CHANNEL_DEPARTMENT_CHAT = "department_chat_messages"
    const val CHANNEL_BROADCAST = "emergency_broadcasts"
    const val CHANNEL_ASSIGNED_INCIDENT = "assigned_incident_alerts"
    private const val LEGACY_INCIDENT_CHANNEL = "emergency_incidents"

    private val CHAT_VIBRATION = longArrayOf(0, 180, 100, 260)
    private val BROADCAST_VIBRATION = longArrayOf(0, 500, 180, 500, 180, 700)
    private val ASSIGNMENT_VIBRATION = longArrayOf(0, 650, 180, 350, 180, 650)

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
                    vibrationPattern = CHAT_VIBRATION
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_DEPARTMENT_CHAT,
                    "Department messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New messages from approved inter-agency channels"
                    enableVibration(true)
                    vibrationPattern = CHAT_VIBRATION
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    CHANNEL_BROADCAST,
                    "Emergency broadcasts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent and critical operational broadcasts"
                    enableVibration(true)
                    vibrationPattern = BROADCAST_VIBRATION
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    CHANNEL_ASSIGNED_INCIDENT,
                    "Assigned incident alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "New dispatch assignments for this responder"
                    enableVibration(true)
                    vibrationPattern = ASSIGNMENT_VIBRATION
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        )
        // The old polling-only channel is no longer used. Removing it prevents
        // responders from seeing two separate incident-alert controls in Settings.
        manager.deleteNotificationChannel(LEGACY_INCIDENT_CHANNEL)
    }

    /**
     * Atomically reserves an event key for either an in-app alert or a system
     * notification. Polling and FCM use the same key, preventing duplicate alerts.
     */
    fun claimEvent(context: Context, eventKey: String): Boolean =
        NotificationEventStore.markIfNew(context, eventKey)

    fun clearEventHistory(context: Context) {
        NotificationEventStore.clear(context)
    }

    fun showPrivateChat(
        context: Context,
        eventKey: String,
        peerId: String,
        threadId: String,
        senderName: String,
        body: String
    ) {
        if (!canNotify(context) || !claimEvent(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_PRIVATE_CHAT)
            putExtra(NotificationNavigation.EXTRA_PEER_ID, peerId)
            putExtra(NotificationNavigation.EXTRA_THREAD_ID, threadId)
        }
        val content = body.ifBlank { "New private message" }.take(500)
        val notification = NotificationCompat.Builder(context, CHANNEL_PRIVATE_CHAT)
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setColor(ContextCompat.getColor(context, R.color.notification_teal))
            .setContentTitle(senderName.ifBlank { "Responder message" })
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVibrate(CHAT_VIBRATION)
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
        if (!canNotify(context) || !claimEvent(context, eventKey)) return
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
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setColor(ContextCompat.getColor(context, R.color.notification_teal))
            .setContentTitle(groupName.ifBlank { "Department coordination" })
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVibrate(CHAT_VIBRATION)
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
        if (!canNotify(context) || !claimEvent(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_BROADCAST)
            putExtra(NotificationNavigation.EXTRA_BROADCAST_ID, broadcastId)
            putExtra(NotificationNavigation.EXTRA_INCIDENT_ID, incidentId)
        }
        val normalizedPriority = priority.uppercase(Locale.US).ifBlank { "ROUTINE" }
        val notificationTitle = title.ifBlank { "Emergency broadcast • $normalizedPriority" }
        val content = body.ifBlank { "A new operational broadcast was issued." }.take(800)

        val notification = NotificationCompat.Builder(context, CHANNEL_BROADCAST)
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setColor(ContextCompat.getColor(context, R.color.notification_teal))
            .setContentTitle(notificationTitle)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(BROADCAST_VIBRATION)
            .setAutoCancel(true)
            // Critical broadcasts remain dismissible; acknowledgement is tracked by
            // the server, not by creating a permanently stuck Android notification.
            .setContentIntent(pendingIntent(context, eventKey, intent))
            .build()

        NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
    }

    fun showAssignedIncident(
        context: Context,
        eventKey: String,
        assignmentId: String,
        incidentId: Long,
        incidentReference: String,
        incidentType: String,
        priority: String,
        location: String,
        body: String = ""
    ) {
        if (!canNotify(context) || !claimEvent(context, eventKey)) return
        createChannels(context)

        val intent = baseIntent(context).apply {
            putExtra(NotificationNavigation.EXTRA_DESTINATION, NotificationNavigation.DEST_ASSIGNED_INCIDENT)
            putExtra(NotificationNavigation.EXTRA_ASSIGNMENT_ID, assignmentId)
            putExtra(NotificationNavigation.EXTRA_INCIDENT_ID, incidentId)
        }

        val typeLabel = incidentType.trim().ifBlank { "Emergency" }
            .lowercase(Locale.US)
            .replaceFirstChar { it.titlecase(Locale.US) }
        val priorityLabel = priority.trim().uppercase(Locale.US)
        val title = "New $typeLabel incident assigned"
        val details = body.trim().ifBlank {
            buildList {
                location.trim().takeIf { it.isNotBlank() }?.let(::add)
                incidentReference.trim().takeIf { it.isNotBlank() }?.let { add("Ref. $it") }
                priorityLabel.takeIf { it.isNotBlank() }?.let { add("$it priority") }
            }.joinToString(" • ").ifBlank { "Open the app to review the dispatch assignment." }
        }.take(800)

        val notification = NotificationCompat.Builder(context, CHANNEL_ASSIGNED_INCIDENT)
            .setSmallIcon(R.drawable.ic_stat_emergency)
            .setColor(ContextCompat.getColor(context, R.color.notification_teal))
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(ASSIGNMENT_VIBRATION)
            .setAutoCancel(true)
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

    private fun canNotify(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

private object NotificationEventStore {
    private const val PREFS = "notification_event_dedupe"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    @Synchronized
    fun clear(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences.getStringSet(KEY_EVENTS, emptySet())
            .orEmpty()
            .forEach { eventKey ->
                NotificationManagerCompat.from(context).cancel(eventKey.hashCode())
            }
        preferences.edit().remove(KEY_EVENTS).apply()
    }

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
