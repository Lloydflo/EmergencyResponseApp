package com.ers.emergencyresponseapp.analytics

import android.util.Log

/**
 * Lightweight analytics logger interface. Replace or extend with Firebase Analytics or other analytics SDK.
 */
interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
}

/**
 * Default implementation that writes to Logcat. This keeps the app decoupled from a concrete analytics SDK.
 */
object DefaultAnalyticsLogger : AnalyticsLogger {
    private const val TAG = "Analytics"

    override fun logEvent(name: String, params: Map<String, String>) {
        if (params.isEmpty()) {
            Log.i(TAG, "Event: $name")
        } else {
            Log.i(TAG, "Event: $name | params=${params.entries.joinToString { "${it.key}=${it.value}" }}")
        }
    }
}

