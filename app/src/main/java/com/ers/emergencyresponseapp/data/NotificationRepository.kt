package com.ers.emergencyresponseapp.data

import com.ers.emergencyresponseapp.BuildConfig
import com.ers.emergencyresponseapp.network.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Hostinger-backed notification operations.
 *
 * FCM device tokens are persisted in MySQL. Private messages still live in
 * Firebase Realtime Database, but the sender calls the PHP notification bridge
 * only after the Firebase write succeeds. Department messages and broadcasts
 * are pushed from their PHP/MySQL write path.
 */
class NotificationRepository(
    private val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/') + "/api/api_app/",
    private val client: OkHttpClient = RetrofitProvider.okHttpClient
) {
    suspend fun registerDeviceToken(
        responderId: Int,
        token: String,
        appVersion: String,
        deviceName: String
    ): Result<Unit> = runCatching {
        require(responderId > 0) { "Invalid responder account" }
        require(token.isNotBlank()) { "Missing notification token" }

        postForm(
            "register-device-token.php",
            mapOf(
                "responder_id" to responderId.toString(),
                "token" to token.trim(),
                "platform" to "android",
                "app_version" to appVersion.trim(),
                "device_name" to deviceName.trim()
            )
        )
        Unit
    }

    suspend fun unregisterDeviceToken(
        responderId: Int,
        token: String
    ): Result<Unit> = runCatching {
        if (responderId <= 0 || token.isBlank()) return@runCatching Unit
        postForm(
            "unregister-device-token.php",
            mapOf(
                "responder_id" to responderId.toString(),
                "token" to token.trim()
            )
        )
        Unit
    }

    suspend fun notifyPrivateMessage(
        senderId: Int,
        recipientId: Int,
        threadId: String,
        messageId: String,
        senderName: String,
        messageType: String,
        preview: String
    ): Result<PushDeliveryResult> = runCatching {
        require(senderId > 0) { "Invalid sender" }
        require(recipientId > 0) { "Invalid recipient" }
        require(threadId.isNotBlank()) { "Missing chat thread" }
        require(messageId.isNotBlank()) { "Missing message ID" }

        val json = postForm(
            "notify-private-message.php",
            mapOf(
                "sender_id" to senderId.toString(),
                "recipient_id" to recipientId.toString(),
                "thread_id" to threadId,
                "message_id" to messageId,
                "sender_name" to senderName.trim(),
                "message_type" to messageType.trim().lowercase(),
                "preview" to preview.trim().take(240)
            )
        )

        PushDeliveryResult(
            attempted = json.optInt("attempted"),
            delivered = json.optInt("delivered"),
            failed = json.optInt("failed")
        )
    }

    suspend fun getBroadcasts(
        responderId: Int,
        limit: Int = 50
    ): Result<List<BroadcastNotice>> = runCatching {
        require(responderId > 0) { "Invalid responder account" }

        val url = endpoint("get-responder-broadcasts.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("responder_id", responderId.toString())
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .build()

        val json = getJson(url.toString())
        val array = json.optJSONArray("broadcasts")
        buildList {
            for (index in 0 until (array?.length() ?: 0)) {
                val item = array?.optJSONObject(index) ?: continue
                add(
                    BroadcastNotice(
                        id = item.optLong("id"),
                        incidentId = item.optLong("incident_id"),
                        incidentReference = item.optString("incident_reference"),
                        incidentType = item.optString("incident_type"),
                        location = item.optString("location"),
                        priority = item.optString("priority", "routine"),
                        message = item.optString("message"),
                        createdByName = item.optString("created_by_name"),
                        createdAt = item.optString("created_at"),
                        createdAtMillis = item.optLong("created_at_ms"),
                        acknowledged = item.optBoolean("acknowledged", false),
                        acknowledgedAt = item.optString("acknowledged_at")
                    )
                )
            }
        }
    }

    suspend fun acknowledgeBroadcast(
        responderId: Int,
        broadcastId: Long
    ): Result<Unit> = runCatching {
        require(responderId > 0) { "Invalid responder account" }
        require(broadcastId > 0L) { "Invalid broadcast" }

        postForm(
            "acknowledge-broadcast.php",
            mapOf(
                "responder_id" to responderId.toString(),
                "broadcast_id" to broadcastId.toString()
            )
        )
        Unit
    }

    private suspend fun postForm(
        endpointName: String,
        fields: Map<String, String>
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        executeJson(
            Request.Builder()
                .url(endpoint(endpointName))
                .post(body)
                .header("Accept", "application/json")
                .build()
        )
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
        )
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw IOException(
                    "Invalid notification API response (${response.code})" +
                            raw.takeIf { it.isNotBlank() }?.let { ": ${it.take(180)}" }.orEmpty()
                )
            }

            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                throw IOException(
                    json.optString("message").ifBlank {
                        "Notification request failed with HTTP ${response.code}"
                    }
                )
            }
            return json
        }
    }

    private fun endpoint(name: String): String =
        baseUrl.trimEnd('/') + "/" + name.trimStart('/')
}

data class PushDeliveryResult(
    val attempted: Int,
    val delivered: Int,
    val failed: Int
)

data class BroadcastNotice(
    val id: Long,
    val incidentId: Long,
    val incidentReference: String,
    val incidentType: String,
    val location: String,
    val priority: String,
    val message: String,
    val createdByName: String,
    val createdAt: String,
    val createdAtMillis: Long,
    val acknowledged: Boolean,
    val acknowledgedAt: String
)
