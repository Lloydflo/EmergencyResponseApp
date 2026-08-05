package com.ers.emergencyresponseapp.presence

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Best-effort signal for an explicit swipe from Android Recents.
 *
 * This service does not keep a permanent foreground notification. Android or a
 * device manufacturer may stop it; the one-hour WorkManager lease remains the
 * authoritative fallback in that case.
 */
class PresenceTaskRemovalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val auth = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val responderId = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0
        if (auth.getBoolean("user_verified", false) && responderId > 0) {
            ResponderPresenceManager.scheduleImmediateOffline(
                context = applicationContext,
                responderId = responderId,
                reason = "removed_from_recents"
            )
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
