package com.ers.emergencyresponseapp.notification

import android.content.Context
import android.os.Build
import android.util.Log
import com.ers.emergencyresponseapp.BuildConfig
import com.ers.emergencyresponseapp.data.NotificationRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object PushTokenManager {
    private const val PREFS = "push_notification_prefs"
    private const val KEY_TOKEN = "fcm_token"

    suspend fun registerCurrentToken(
        context: Context,
        responderId: Int,
        repository: NotificationRepository = NotificationRepository()
    ): Result<Unit> = runCatching {
        require(responderId > 0) { "Invalid responder account" }
        val token = FirebaseMessaging.getInstance().token.await().trim()
        require(token.isNotBlank()) { "Firebase did not return a device token" }

        repository.registerDeviceToken(
            responderId = responderId,
            token = token,
            appVersion = BuildConfig.VERSION_NAME,
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(120)
        ).getOrThrow()

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }.onFailure { error ->
        Log.w("PushToken", "Unable to register FCM token", error)
    }

    suspend fun registerProvidedToken(
        context: Context,
        responderId: Int,
        token: String,
        repository: NotificationRepository = NotificationRepository()
    ): Result<Unit> = runCatching {
        if (responderId <= 0 || token.isBlank()) return@runCatching
        repository.registerDeviceToken(
            responderId = responderId,
            token = token,
            appVersion = BuildConfig.VERSION_NAME,
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(120)
        ).getOrThrow()
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    suspend fun unregisterCurrentToken(
        context: Context,
        responderId: Int,
        repository: NotificationRepository = NotificationRepository()
    ): Result<Unit> = runCatching {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = preferences.getString(KEY_TOKEN, "").orEmpty()
        if (responderId > 0 && token.isNotBlank()) {
            repository.unregisterDeviceToken(responderId, token).getOrThrow()
        }
        preferences.edit().remove(KEY_TOKEN).apply()
    }.onFailure { error ->
        Log.w("PushToken", "Unable to unregister FCM token", error)
    }
}
