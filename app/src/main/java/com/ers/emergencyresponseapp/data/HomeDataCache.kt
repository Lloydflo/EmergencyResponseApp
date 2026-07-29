package com.ers.emergencyresponseapp.data

import android.content.Context
import com.ers.emergencyresponseapp.features.assigned.IncidentDto
import com.ers.emergencyresponseapp.network.MyBackupRequestDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Small last-known-good cache used only as a safety fallback when dispatch is
 * unreachable. HomeScreen refreshes it after successful server responses and
 * confirms an empty assignment/active list twice before removing a previously
 * non-empty snapshot, which prevents one transient empty response from hiding
 * an operational incident.
 */
class HomeDataCache(context: Context) {
    data class Snapshot<T>(
        val items: List<T>,
        val savedAtMillis: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val incidentListType = object : TypeToken<List<IncidentDto>>() {}.type
    private val backupListType = object : TypeToken<List<MyBackupRequestDto>>() {}.type

    fun readAssigned(responderId: Int): Snapshot<IncidentDto>? {
        if (responderId <= 0) return null
        return read(
            dataKey = assignedDataKey(responderId),
            timestampKey = assignedTimeKey(responderId),
            type = incidentListType
        )
    }

    fun saveAssigned(responderId: Int, incidents: List<IncidentDto>, savedAtMillis: Long) {
        if (responderId <= 0) return
        save(
            dataKey = assignedDataKey(responderId),
            timestampKey = assignedTimeKey(responderId),
            items = incidents,
            savedAtMillis = savedAtMillis
        )
    }

    fun readActive(responderId: Int): Snapshot<IncidentDto>? {
        if (responderId <= 0) return null
        return read(
            dataKey = activeDataKey(responderId),
            timestampKey = activeTimeKey(responderId),
            type = incidentListType
        )
    }

    fun saveActive(responderId: Int, incidents: List<IncidentDto>, savedAtMillis: Long) {
        if (responderId <= 0) return
        save(
            dataKey = activeDataKey(responderId),
            timestampKey = activeTimeKey(responderId),
            items = incidents,
            savedAtMillis = savedAtMillis
        )
    }

    fun readBackupRequests(responderId: Int): Snapshot<MyBackupRequestDto>? {
        if (responderId <= 0) return null
        return read(
            dataKey = backupDataKey(responderId),
            timestampKey = backupTimeKey(responderId),
            type = backupListType
        )
    }

    fun saveBackupRequests(
        responderId: Int,
        requests: List<MyBackupRequestDto>,
        savedAtMillis: Long
    ) {
        if (responderId <= 0) return
        save(
            dataKey = backupDataKey(responderId),
            timestampKey = backupTimeKey(responderId),
            items = requests,
            savedAtMillis = savedAtMillis
        )
    }

    fun clearForResponder(responderId: Int) {
        if (responderId <= 0) return
        preferences.edit()
            .remove(assignedDataKey(responderId))
            .remove(assignedTimeKey(responderId))
            .remove(activeDataKey(responderId))
            .remove(activeTimeKey(responderId))
            .remove(backupDataKey(responderId))
            .remove(backupTimeKey(responderId))
            .apply()
    }

    private fun <T> read(dataKey: String, timestampKey: String, type: java.lang.reflect.Type): Snapshot<T>? {
        if (!preferences.contains(dataKey)) return null
        val raw = preferences.getString(dataKey, null) ?: return null
        val savedAt = preferences.getLong(timestampKey, 0L)

        return runCatching {
            val items: List<T> = gson.fromJson(raw, type) ?: emptyList()
            Snapshot(items = items, savedAtMillis = savedAt)
        }.getOrNull()
    }

    private fun <T> save(
        dataKey: String,
        timestampKey: String,
        items: List<T>,
        savedAtMillis: Long
    ) {
        runCatching { gson.toJson(items) }
            .onSuccess { json ->
                preferences.edit()
                    .putString(dataKey, json)
                    .putLong(timestampKey, savedAtMillis)
                    .apply()
            }
    }

    private fun assignedDataKey(id: Int) = "assigned_data_$id"
    private fun assignedTimeKey(id: Int) = "assigned_time_$id"
    private fun activeDataKey(id: Int) = "active_data_$id"
    private fun activeTimeKey(id: Int) = "active_time_$id"
    private fun backupDataKey(id: Int) = "backup_data_$id"
    private fun backupTimeKey(id: Int) = "backup_time_$id"

    private companion object {
        const val PREFS_NAME = "home_last_known_good_cache"
    }
}
