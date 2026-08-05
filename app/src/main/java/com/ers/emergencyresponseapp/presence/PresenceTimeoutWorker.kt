package com.ers.emergencyresponseapp.presence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PresenceTimeoutWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result {
        val responderId = inputData.getInt(
            ResponderPresenceManager.INPUT_RESPONDER_ID,
            0
        )
        val expectedBackgroundAt = inputData.getLong(
            ResponderPresenceManager.INPUT_EXPECTED_BACKGROUND_AT,
            0L
        )
        val force = inputData.getBoolean(
            ResponderPresenceManager.INPUT_FORCE,
            false
        )
        val reason = inputData.getString(
            ResponderPresenceManager.INPUT_REASON
        ).orEmpty().ifBlank { "background_timeout" }

        val completed = ResponderPresenceManager.executeTimeout(
            context = applicationContext,
            responderId = responderId,
            expectedBackgroundAt = expectedBackgroundAt,
            force = force,
            reason = reason
        )
        return if (completed) Result.success() else Result.retry()
    }
}
