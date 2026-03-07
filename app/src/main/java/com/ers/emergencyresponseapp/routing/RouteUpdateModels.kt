package com.ers.emergencyresponseapp.routing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Waypoint(val lat: Double, val lng: Double)

data class RouteUpdatePayload(
    val version: Int,
    val summary: String,
    val destLat: Double,
    val destLng: Double,
    val waypoints: List<Waypoint>
)

class RouteUpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("route_update_prefs", Context.MODE_PRIVATE)

    fun saveLatest(incidentId: String, payload: RouteUpdatePayload) {
        val json = JSONObject().apply {
            put("version", payload.version)
            put("summary", payload.summary)
            put("destLat", payload.destLat)
            put("destLng", payload.destLng)

            val arr = JSONArray()
            payload.waypoints.forEach {
                arr.put(JSONObject().apply {
                    put("lat", it.lat)
                    put("lng", it.lng)
                })
            }
            put("waypoints", arr)
        }
        prefs.edit().putString("latest_$incidentId", json.toString()).apply()
    }

    fun getLatest(incidentId: String): RouteUpdatePayload? {
        val raw = prefs.getString("latest_$incidentId", null) ?: return null
        val json = JSONObject(raw)

        val version = json.optInt("version", 0)
        val summary = json.optString("summary", "")
        val destLat = json.optDouble("destLat", Double.NaN)
        val destLng = json.optDouble("destLng", Double.NaN)

        val arr = json.optJSONArray("waypoints") ?: JSONArray()
        val wps = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(Waypoint(o.optDouble("lat"), o.optDouble("lng")))
            }
        }

        if (destLat.isNaN() || destLng.isNaN()) return null
        return RouteUpdatePayload(version, summary, destLat, destLng, wps)
    }

    fun getLastVersion(incidentId: String): Int =
        prefs.getInt("lastver_$incidentId", 0)

    fun setLastVersion(incidentId: String, v: Int) {
        prefs.edit().putInt("lastver_$incidentId", v).apply()
    }
}

/**
 * ✅ Palitan mo 'to ng actual Retrofit call mo:
 * GET /Incidents/route-updates?incidentId=...
 */
object RouteUpdatesRepository {
    suspend fun fetchUpdate(incidentId: String): RouteUpdatePayload? {
        // TODO: integrate your backend response
        // return null if no update available
        return null
    }
}