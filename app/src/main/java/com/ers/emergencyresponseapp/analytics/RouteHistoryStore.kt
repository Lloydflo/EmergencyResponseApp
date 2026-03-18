package com.ers.emergencyresponseapp.analytics

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "route_history"
private const val KEY_HISTORY = "history"
private const val KEY_ACTIVE = "active_route"

// Lightweight, local-only route history storage.
data class RouteHistoryEntry(
    val incidentId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val straightLineMeters: Float,
    val durationMillis: Long
)

object RouteHistoryStore {
    fun startRoute(
        context: Context,
        incidentId: String,
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = JSONObject()
        active.put("incidentId", incidentId)
        active.put("startTime", System.currentTimeMillis())
        active.put("startLat", startLat)
        active.put("startLng", startLng)
        active.put("destLat", destLat)
        active.put("destLng", destLng)
        prefs.edit().putString(KEY_ACTIVE, active.toString()).apply()
    }

    fun completeRoute(context: Context, incidentId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeStr = prefs.getString(KEY_ACTIVE, null) ?: return
        val active = JSONObject(activeStr)
        if (active.optString("incidentId") != incidentId) return

        val startTime = active.optLong("startTime", 0L)
        if (startTime == 0L) return

        val startLat = active.optDouble("startLat", 0.0)
        val startLng = active.optDouble("startLng", 0.0)
        val destLat = active.optDouble("destLat", 0.0)
        val destLng = active.optDouble("destLng", 0.0)

        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, destLat, destLng, results)
        val straightLineMeters = results[0]
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        val entry = JSONObject()
        entry.put("incidentId", incidentId)
        entry.put("startedAtMillis", startTime)
        entry.put("endedAtMillis", endTime)
        entry.put("straightLineMeters", straightLineMeters)
        entry.put("durationMillis", duration)

        val history = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        history.put(entry)
        prefs.edit()
            .putString(KEY_HISTORY, history.toString())
            .remove(KEY_ACTIVE)
            .apply()
    }

    fun getHistory(context: Context): List<RouteHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        val result = ArrayList<RouteHistoryEntry>(history.length())
        for (i in 0 until history.length()) {
            val obj = history.getJSONObject(i)
            result.add(
                RouteHistoryEntry(
                    incidentId = obj.optString("incidentId"),
                    startedAtMillis = obj.optLong("startedAtMillis"),
                    endedAtMillis = obj.optLong("endedAtMillis"),
                    straightLineMeters = obj.optDouble("straightLineMeters", 0.0).toFloat(),
                    durationMillis = obj.optLong("durationMillis")
                )
            )
        }
        return result
    }
}
