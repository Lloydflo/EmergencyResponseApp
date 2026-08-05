package com.ers.emergencyresponseapp.presence

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ers.emergencyresponseapp.AppState
import com.ers.emergencyresponseapp.data.IncidentRepository
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Central policy for responder availability.
 *
 * Presence is intentionally separate from authentication and push reachability:
 * - Leaving the Activity does not log the responder out and does not unregister FCM.
 * - A responder remains assignable for [BACKGROUND_TIMEOUT_MS] while the app is
 *   in the background.
 * - Explicit logout, a best-effort task-removal signal, or the background lease
 *   expiry marks presence offline.
 * - Active route monitoring keeps the responder operationally busy and extends
 *   the background lease instead of reporting the unit as idle/offline.
 */
object ResponderPresenceManager {
    const val BACKGROUND_TIMEOUT_MS: Long = 60L * 60L * 1_000L
    const val FOREGROUND_HEARTBEAT_MS: Long = 5L * 60L * 1_000L
    const val FOREGROUND_LEASE_MS: Long = 12L * 60L * 1_000L

    internal const val INPUT_RESPONDER_ID = "responder_id"
    internal const val INPUT_EXPECTED_BACKGROUND_AT = "expected_background_at"
    internal const val INPUT_FORCE = "force"
    internal const val INPUT_REASON = "reason"

    private const val PREFS = "responder_presence_policy"
    private const val KEY_BACKGROUND_AT = "background_at"
    private const val KEY_ONLINE_UNTIL = "online_until"
    private const val KEY_LOCAL_STATE = "local_state"
    private const val KEY_LAST_REASON = "last_reason"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"
    private const val KEY_RESPONDER_ID = "responder_id"

    private const val STATE_FOREGROUND = "foreground"
    private const val STATE_BACKGROUND = "background"
    private const val STATE_RESPONDING_BACKGROUND = "responding_background"
    private const val STATE_OFFLINE = "offline"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun workName(responderId: Int): String =
        "responder_presence_timeout_$responderId"

    fun onAppForeground(context: Context, responderId: Int) {
        if (responderId <= 0) return
        val appContext = context.applicationContext
        val deadline = System.currentTimeMillis() + FOREGROUND_LEASE_MS
        cancelOfflineWork(appContext, responderId)
        writeLocalState(
            context = appContext,
            responderId = responderId,
            state = STATE_FOREGROUND,
            backgroundAt = 0L,
            onlineUntil = deadline,
            reason = "app_foreground"
        )
        startTaskObserver(appContext)

        scope.launch {
            syncOnlineLease(
                context = appContext,
                responderId = responderId,
                appState = STATE_FOREGROUND,
                onlineUntil = deadline
            )
        }
    }

    fun heartbeatForeground(context: Context, responderId: Int) {
        if (responderId <= 0 || !AppState.isForeground) return
        val appContext = context.applicationContext
        val deadline = System.currentTimeMillis() + FOREGROUND_LEASE_MS
        writeLocalState(
            context = appContext,
            responderId = responderId,
            state = STATE_FOREGROUND,
            backgroundAt = 0L,
            onlineUntil = deadline,
            reason = "foreground_heartbeat"
        )
        scope.launch {
            syncOnlineLease(appContext, responderId, STATE_FOREGROUND, deadline)
        }
    }

    /**
     * Keeps the responder online for one hour after leaving the UI. This method
     * never clears the login session or the FCM token.
     */
    fun onAppBackground(context: Context, responderId: Int) {
        if (responderId <= 0) return
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val routeActive = isRouteActive(appContext)
        val state = if (routeActive) STATE_RESPONDING_BACKGROUND else STATE_BACKGROUND
        val deadline = now + BACKGROUND_TIMEOUT_MS

        writeLocalState(
            context = appContext,
            responderId = responderId,
            state = state,
            backgroundAt = now,
            onlineUntil = deadline,
            reason = "app_background"
        )
        scheduleOfflineWork(
            context = appContext,
            responderId = responderId,
            expectedBackgroundAt = now,
            delayMillis = BACKGROUND_TIMEOUT_MS,
            force = false,
            reason = "background_timeout"
        )

        // Refresh the server lease once when the app leaves the foreground.
        // This is deliberately still ONLINE; the timeout worker owns the later
        // transition to OFFLINE.
        scope.launch {
            syncOnlineLease(appContext, responderId, state, deadline)
        }
    }

    /** Called after connectivity returns. It never extends an expired lease. */
    fun onConnectivityRestored(context: Context, responderId: Int) {
        if (responderId <= 0) return
        val appContext = context.applicationContext
        if (AppState.isForeground) {
            heartbeatForeground(appContext, responderId)
            return
        }

        val preferences = preferences(appContext)
        val backgroundAt = preferences.getLong(KEY_BACKGROUND_AT, 0L)
        val onlineUntil = preferences.getLong(KEY_ONLINE_UNTIL, 0L)
        val now = System.currentTimeMillis()

        if (backgroundAt <= 0L || onlineUntil <= now) {
            scheduleImmediateOffline(appContext, responderId, "expired_while_disconnected")
            return
        }

        val localState = preferences.getString(KEY_LOCAL_STATE, STATE_BACKGROUND)
            .orEmpty()
            .ifBlank { STATE_BACKGROUND }
        scope.launch {
            syncOnlineLease(appContext, responderId, localState, onlineUntil)
        }
    }

    /**
     * Best-effort immediate offline transition when Android reports that the
     * task was explicitly removed from Recents. Push registration is retained.
     */
    fun scheduleImmediateOffline(context: Context, responderId: Int, reason: String) {
        if (responderId <= 0) return
        scheduleOfflineWork(
            context = context.applicationContext,
            responderId = responderId,
            expectedBackgroundAt = preferences(context).getLong(KEY_BACKGROUND_AT, 0L),
            delayMillis = 0L,
            force = true,
            reason = reason
        )
    }

    suspend fun markExplicitlyOffline(
        context: Context,
        responderId: Int,
        reason: String
    ) {
        if (responderId <= 0) return
        val appContext = context.applicationContext
        cancelOfflineWork(appContext, responderId)
        writeLocalState(
            context = appContext,
            responderId = responderId,
            state = STATE_OFFLINE,
            backgroundAt = 0L,
            onlineUntil = 0L,
            reason = reason
        )
        syncOffline(appContext, responderId, reason)
        stopTaskObserver(appContext)
    }

    internal suspend fun executeTimeout(
        context: Context,
        responderId: Int,
        expectedBackgroundAt: Long,
        force: Boolean,
        reason: String
    ): Boolean {
        val appContext = context.applicationContext
        if (responderId <= 0 || !hasActiveSession(appContext, responderId)) {
            return true
        }
        if (AppState.isForeground) {
            return true
        }

        val preferences = preferences(appContext)
        val currentBackgroundAt = preferences.getLong(KEY_BACKGROUND_AT, 0L)
        if (
            currentBackgroundAt <= 0L ||
            (expectedBackgroundAt > 0L && currentBackgroundAt != expectedBackgroundAt)
        ) {
            // A newer foreground/background transition replaced this worker.
            return true
        }

        val elapsed = System.currentTimeMillis() - currentBackgroundAt
        if (!force && elapsed < BACKGROUND_TIMEOUT_MS) {
            scheduleOfflineWork(
                context = appContext,
                responderId = responderId,
                expectedBackgroundAt = currentBackgroundAt,
                delayMillis = BACKGROUND_TIMEOUT_MS - elapsed,
                force = false,
                reason = reason
            )
            return true
        }

        if (isRouteActive(appContext)) {
            // Navigation/location monitoring is an active operational state. Keep
            // the responder reachable and busy, then check again in one hour.
            val now = System.currentTimeMillis()
            val nextDeadline = now + BACKGROUND_TIMEOUT_MS
            writeLocalState(
                context = appContext,
                responderId = responderId,
                state = STATE_RESPONDING_BACKGROUND,
                backgroundAt = now,
                onlineUntil = nextDeadline,
                reason = "active_route_lease_extended"
            )
            syncOnlineLease(
                context = appContext,
                responderId = responderId,
                appState = STATE_RESPONDING_BACKGROUND,
                onlineUntil = nextDeadline
            )
            scheduleOfflineWork(
                context = appContext,
                responderId = responderId,
                expectedBackgroundAt = now,
                delayMillis = BACKGROUND_TIMEOUT_MS,
                force = false,
                reason = "active_route_recheck"
            )
            return true
        }

        if (AppState.isForeground) return true
        if (preferences(appContext).getLong(KEY_BACKGROUND_AT, 0L) != currentBackgroundAt) {
            return true
        }

        writeLocalState(
            context = appContext,
            responderId = responderId,
            state = STATE_OFFLINE,
            backgroundAt = currentBackgroundAt,
            onlineUntil = 0L,
            reason = reason
        )
        return syncOffline(appContext, responderId, reason)
    }

    private suspend fun syncOnlineLease(
        context: Context,
        responderId: Int,
        appState: String,
        onlineUntil: Long
    ) {
        val current = preferences(context)
        val stateStillCurrent = current.getInt(KEY_RESPONDER_ID, 0) == responderId &&
                current.getString(KEY_LOCAL_STATE, "").orEmpty() == appState &&
                current.getLong(KEY_ONLINE_UNTIL, 0L) == onlineUntil
        if (!stateStillCurrent) {
            // A newer lifecycle transition won the race; never let a delayed
            // background write overwrite a newer foreground lease.
            return
        }

        runCatching {
            FirebaseChatRepository().setPresenceState(
                userId = responderId.toString(),
                isOnline = true,
                appState = appState,
                onlineUntil = onlineUntil
            )
        }.onFailure { error ->
            Log.w("RESPONDER_PRESENCE", "Firebase online lease sync failed", error)
        }

        runCatching {
            IncidentRepository().setUnitPresence(
                responderId = responderId,
                presence = "online",
                reason = appState
            )
        }.onFailure { error ->
            Log.w("RESPONDER_PRESENCE", "Dispatch online lease sync failed", error)
        }
        preferences(context).edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
    }

    /** Returns true when both remote presence targets accepted the update. */
    private suspend fun syncOffline(
        context: Context,
        responderId: Int,
        reason: String
    ): Boolean {
        val firebaseOk = runCatching {
            FirebaseChatRepository().setPresenceState(
                userId = responderId.toString(),
                isOnline = false,
                appState = STATE_OFFLINE,
                onlineUntil = 0L
            )
        }.onFailure { error ->
            Log.w("RESPONDER_PRESENCE", "Firebase offline sync failed: $reason", error)
        }.isSuccess

        val dispatchOk = runCatching {
            IncidentRepository().setUnitPresence(
                responderId = responderId,
                presence = "offline",
                reason = reason
            ) != null
        }.onFailure { error ->
            Log.w("RESPONDER_PRESENCE", "Dispatch offline sync failed: $reason", error)
        }.getOrDefault(false)

        preferences(context).edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
        return firebaseOk && dispatchOk
    }

    private fun scheduleOfflineWork(
        context: Context,
        responderId: Int,
        expectedBackgroundAt: Long,
        delayMillis: Long,
        force: Boolean,
        reason: String
    ) {
        val data = Data.Builder()
            .putInt(INPUT_RESPONDER_ID, responderId)
            .putLong(INPUT_EXPECTED_BACKGROUND_AT, expectedBackgroundAt)
            .putBoolean(INPUT_FORCE, force)
            .putString(INPUT_REASON, reason)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PresenceTimeoutWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag(workName(responderId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(responderId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelOfflineWork(context: Context, responderId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(responderId))
    }

    private fun isRouteActive(context: Context): Boolean {
        val routePending = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .getBoolean("pending_en_route_check", false)
        return routePending || RouteMonitoringService.isRunning
    }

    private fun hasActiveSession(context: Context, responderId: Int): Boolean {
        val auth = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val currentResponderId = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0
        return auth.getBoolean("user_verified", false) && currentResponderId == responderId
    }

    private fun writeLocalState(
        context: Context,
        responderId: Int,
        state: String,
        backgroundAt: Long,
        onlineUntil: Long,
        reason: String
    ) {
        preferences(context).edit()
            .putInt(KEY_RESPONDER_ID, responderId)
            .putString(KEY_LOCAL_STATE, state)
            .putLong(KEY_BACKGROUND_AT, backgroundAt)
            .putLong(KEY_ONLINE_UNTIL, onlineUntil)
            .putString(KEY_LAST_REASON, reason)
            .apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun startTaskObserver(context: Context) {
        runCatching {
            context.startService(Intent(context, PresenceTaskRemovalService::class.java))
        }.onFailure { error ->
            // WorkManager remains the authoritative one-hour fallback even when
            // a manufacturer refuses to keep the lightweight service alive.
            Log.w("RESPONDER_PRESENCE", "Task-removal observer was not started", error)
        }
    }

    private fun stopTaskObserver(context: Context) {
        runCatching {
            context.stopService(Intent(context, PresenceTaskRemovalService::class.java))
        }
    }
}
