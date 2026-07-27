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
 * Server-backed operations that were previously local-only in the UI.
 * Existing incident, assignment, resource-request, profile, and inter-agency
 * group APIs remain in IncidentRepository/RetrofitProvider.
 */
class OperationalRepository(
    private val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/') + "/api/api_app/",
    private val client: OkHttpClient = RetrofitProvider.okHttpClient
) {
    suspend fun createCoordinationTip(
        senderUserId: Int,
        senderName: String,
        recipientType: String,
        recipientId: String,
        clientReference: String,
        incidentType: String,
        priority: String,
        location: String,
        latitude: Double?,
        longitude: Double?,
        contactNumber: String,
        description: String,
        policeBackupReason: String
    ): Result<CoordinationTipRecord> = runCatching {
        require(senderUserId > 0) { "Invalid responder account" }
        require(recipientType == "private" || recipientType == "group") {
            "Invalid coordination recipient"
        }
        require(recipientId.isNotBlank()) { "Missing coordination recipient" }
        require(clientReference.isNotBlank()) { "Missing tip reference" }
        require(location.isNotBlank()) { "Location is required" }
        require(description.isNotBlank()) { "Description is required" }

        val fields = linkedMapOf(
            "sender_user_id" to senderUserId.toString(),
            "sender_name" to senderName.trim(),
            "recipient_type" to recipientType,
            "recipient_id" to recipientId.trim(),
            "client_reference" to clientReference.trim(),
            "incident_type" to incidentType.trim(),
            "priority" to priority.trim(),
            "location" to location.trim(),
            "contact_number" to contactNumber.trim(),
            "description" to description.trim(),
            "police_backup_reason" to policeBackupReason.trim()
        )
        latitude?.let { fields["latitude"] = it.toString() }
        longitude?.let { fields["longitude"] = it.toString() }

        val json = postForm("create-coordination-tip.php", fields)
        parseCoordinationTip(json.getJSONObject("tip"))
    }

    suspend fun getCoordinationTip(
        tipId: Long,
        requesterUserId: Int
    ): Result<CoordinationTipRecord> = runCatching {
        require(tipId > 0L) { "Invalid incident tip" }
        require(requesterUserId > 0) { "Invalid responder account" }

        val url = endpoint("get-coordination-tip.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("id", tipId.toString())
            .addQueryParameter("requester_user_id", requesterUserId.toString())
            .build()

        val json = getJson(url.toString())
        parseCoordinationTip(json.getJSONObject("tip"))
    }

    suspend fun getAfterActionReports(
        responderId: Int
    ): Result<List<AfterActionReportRecord>> = runCatching {
        require(responderId > 0) { "Invalid responder account" }

        val url = endpoint("get-my-after-action-reports.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("responder_id", responderId.toString())
            .build()

        val json = getJson(url.toString())
        val array = json.optJSONArray("reports")
        buildList {
            for (index in 0 until (array?.length() ?: 0)) {
                val item = array?.optJSONObject(index) ?: continue
                add(parseAfterActionReport(item))
            }
        }
    }

    suspend fun upsertAfterActionReport(
        request: SaveAfterActionReportRequest
    ): Result<AfterActionReportRecord> = runCatching {
        require(request.incidentId > 0) { "Invalid incident" }
        require(request.responderId > 0) { "Invalid responder account" }
        require(request.status == "draft" || request.status == "submitted") {
            "Invalid report status"
        }
        if (request.status == "submitted") {
            require(request.incidentSummary.isNotBlank()) { "Incident summary is required" }
            require(request.actionsTaken.isNotBlank()) { "Actions taken are required" }
            require(!request.followUpRequired || request.followUpDetails.isNotBlank()) {
                "Follow-up details are required"
            }
        }

        val json = postForm(
            "upsert-after-action-report.php",
            linkedMapOf(
                "incident_id" to request.incidentId.toString(),
                "responder_id" to request.responderId.toString(),
                "incident_type" to request.incidentType.trim(),
                "responder_name" to request.responderName.trim(),
                "operational_outcome" to request.operationalOutcome.trim(),
                "incident_summary" to request.incidentSummary.trim(),
                "actions_taken" to request.actionsTaken.trim(),
                "persons_assisted" to request.personsAssisted.coerceAtLeast(0).toString(),
                "injuries" to request.injuries.coerceAtLeast(0).toString(),
                "fatalities" to request.fatalities.coerceAtLeast(0).toString(),
                "resources_used" to request.resourcesUsed.trim(),
                "agencies_involved" to request.agenciesInvolved.trim(),
                "handoff_details" to request.handoffDetails.trim(),
                "safety_issues" to request.safetyIssues.trim(),
                "follow_up_required" to if (request.followUpRequired) "1" else "0",
                "follow_up_details" to request.followUpDetails.trim(),
                "lessons_learned" to request.lessonsLearned.trim(),
                "status" to request.status
            )
        )
        parseAfterActionReport(json.getJSONObject("report"))
    }

    suspend fun submitCommunicationReport(
        reporterId: Int,
        reportedResponderId: Int,
        reason: String
    ): Result<CommunicationReportRecord> = runCatching {
        require(reporterId > 0) { "Invalid reporter account" }
        require(reportedResponderId > 0) { "Invalid reported responder" }
        require(reporterId != reportedResponderId) { "You cannot report your own account" }
        require(reason.isNotBlank()) { "A report reason is required" }

        val json = postForm(
            "submit-communication-report.php",
            linkedMapOf(
                "reporter_id" to reporterId.toString(),
                "reported_responder_id" to reportedResponderId.toString(),
                "reason" to reason.trim()
            )
        )

        CommunicationReportRecord(
            id = json.optLong("report_id"),
            status = json.optString("status", "pending"),
            message = json.optString("message")
        )
    }

    suspend fun getIncidentReviews(
        responderId: Int,
        limit: Int = 100
    ): Result<IncidentReviewResult> = runCatching {
        require(responderId > 0) { "Invalid responder account" }

        val url = endpoint("get-my-incident-reviews.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("responder_id", responderId.toString())
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .build()

        val json = getJson(url.toString())
        val summaryJson = json.optJSONObject("summary") ?: JSONObject()
        val reviewsJson = json.optJSONArray("reviews")

        val reviews = buildList {
            for (index in 0 until (reviewsJson?.length() ?: 0)) {
                val item = reviewsJson?.optJSONObject(index) ?: continue
                add(
                    IncidentReviewRecord(
                        id = item.optLong("id"),
                        incidentId = item.optLong("incident_id"),
                        referenceNo = item.optString("reference_no"),
                        incidentType = item.optString("incident_type"),
                        priority = item.optString("priority"),
                        locationAddress = item.optString("location_address"),
                        reviewStatus = item.optString("review_status"),
                        responseRating = item.optInt("response_rating"),
                        communicationRating = item.optInt("communication_rating"),
                        professionalismRating = item.optInt("professionalism_rating"),
                        outcome = item.optString("outcome"),
                        reviewText = item.optString("review_text"),
                        createdAtMillis = item.optLong("created_at_ms")
                    )
                )
            }
        }

        IncidentReviewResult(
            summary = IncidentReviewSummary(
                reviewCount = summaryJson.optInt("review_count"),
                averageResponseRating = summaryJson.optDouble(
                    "average_response_rating",
                    0.0
                ),
                averageCommunicationRating = summaryJson.optDouble(
                    "average_communication_rating",
                    0.0
                ),
                averageProfessionalismRating = summaryJson.optDouble(
                    "average_professionalism_rating",
                    0.0
                ),
                averageOverallRating = summaryJson.optDouble(
                    "average_overall_rating",
                    0.0
                )
            ),
            reviews = reviews
        )
    }

    suspend fun getRouteAnalytics(
        responderId: Int,
        limit: Int = 50
    ): Result<RouteAnalyticsResult> = runCatching {
        require(responderId > 0) { "Invalid responder account" }

        val url = endpoint("get-route-analytics.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("responder_id", responderId.toString())
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .build()

        val json = getJson(url.toString())
        val summaryJson = json.optJSONObject("summary") ?: JSONObject()
        val routesJson = json.optJSONArray("routes")

        val routes = buildList {
            for (index in 0 until (routesJson?.length() ?: 0)) {
                val item = routesJson?.optJSONObject(index) ?: continue
                add(
                    RouteAnalyticsEntry(
                        id = item.optLong("id"),
                        incidentId = item.optLong("incident_id"),
                        referenceNo = item.optString("reference_no"),
                        incidentType = item.optString("incident_type"),
                        locationAddress = item.optString("location_address"),
                        startedAt = item.optString("started_at"),
                        arrivedAt = item.optString("arrived_at"),
                        durationSeconds = item.optLong("duration_seconds"),
                        totalPoints = item.optInt("total_points"),
                        totalDistanceMeters = item.optDouble("total_distance_meters", 0.0),
                        averageSpeedKmh = item.optDouble("average_speed_kmh", 0.0),
                        maxSpeedKmh = item.optDouble("max_speed_kmh", 0.0)
                    )
                )
            }
        }

        RouteAnalyticsResult(
            summary = RouteAnalyticsSummary(
                totalRoutes = summaryJson.optInt("total_routes"),
                totalDistanceMeters = summaryJson.optDouble("total_distance_meters", 0.0),
                averageDurationSeconds = summaryJson.optDouble("avg_duration_seconds", 0.0),
                averageDistanceMeters = summaryJson.optDouble("avg_distance_meters", 0.0),
                averageSpeedKmh = summaryJson.optDouble("avg_speed_kmh", 0.0),
                maxSpeedKmh = summaryJson.optDouble("max_speed_kmh", 0.0)
            ),
            routes = routes
        )
    }

    private suspend fun postForm(
        endpointName: String,
        fields: Map<String, String>
    ): JSONObject = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()

        val request = Request.Builder()
            .url(endpoint(endpointName))
            .post(form)
            .header("Accept", "application/json")
            .build()

        executeJson(request)
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().trim()
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IOException(
                    "Invalid server response (${response.code})" +
                            body.takeIf { it.isNotBlank() }?.let { raw -> ": ${raw.take(180)}" }.orEmpty()
                )
            }

            if (!response.isSuccessful || !json.optBoolean("success", false)) {
                throw IOException(
                    json.optString("message").ifBlank {
                        "Request failed with HTTP ${response.code}"
                    }
                )
            }
            return json
        }
    }

    private fun endpoint(name: String): String =
        baseUrl.trimEnd('/') + "/" + name.trimStart('/')

    private fun parseCoordinationTip(json: JSONObject): CoordinationTipRecord =
        CoordinationTipRecord(
            id = json.optLong("id"),
            referenceNo = json.optString("reference_no"),
            senderUserId = json.optInt("sender_user_id"),
            recipientType = json.optString("recipient_type"),
            recipientId = json.optString("recipient_id"),
            incidentType = json.optString("incident_type"),
            priority = json.optString("priority"),
            location = json.optString("location"),
            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),
            contactNumber = json.optString("contact_number"),
            description = json.optString("description"),
            policeBackupReason = json.optString("police_backup_reason"),
            senderName = json.optString("sender_name"),
            status = json.optString("status", "pending"),
            createdAtMillis = json.optLong("created_at_ms")
                .takeIf { it > 0L }
                ?: 0L
        )

    private fun parseAfterActionReport(json: JSONObject): AfterActionReportRecord =
        AfterActionReportRecord(
            id = json.optLong("id"),
            incidentId = json.optLong("incident_id"),
            responderId = json.optInt("responder_id"),
            incidentType = json.optString("incident_type"),
            responderName = json.optString("responder_name"),
            operationalOutcome = json.optString("operational_outcome"),
            incidentSummary = json.optString("incident_summary"),
            actionsTaken = json.optString("actions_taken"),
            personsAssisted = json.optInt("persons_assisted"),
            injuries = json.optInt("injuries"),
            fatalities = json.optInt("fatalities"),
            resourcesUsed = json.optString("resources_used"),
            agenciesInvolved = json.optString("agencies_involved"),
            handoffDetails = json.optString("handoff_details"),
            safetyIssues = json.optString("safety_issues"),
            followUpRequired = json.optInt("follow_up_required") == 1 ||
                    json.optBoolean("follow_up_required", false),
            followUpDetails = json.optString("follow_up_details"),
            lessonsLearned = json.optString("lessons_learned"),
            status = json.optString("status", "draft"),
            reviewerNotes = json.optString("reviewer_notes"),
            createdAtMillis = json.optLong("created_at_ms"),
            updatedAtMillis = json.optLong("updated_at_ms")
        )

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name).takeUnless { it.isNaN() }
    }
}

data class CoordinationTipRecord(
    val id: Long,
    val referenceNo: String,
    val senderUserId: Int,
    val recipientType: String,
    val recipientId: String,
    val incidentType: String,
    val priority: String,
    val location: String,
    val latitude: Double?,
    val longitude: Double?,
    val contactNumber: String,
    val description: String,
    val policeBackupReason: String,
    val senderName: String,
    val status: String,
    val createdAtMillis: Long
)

data class SaveAfterActionReportRequest(
    val incidentId: Long,
    val responderId: Int,
    val incidentType: String,
    val responderName: String,
    val operationalOutcome: String,
    val incidentSummary: String,
    val actionsTaken: String,
    val personsAssisted: Int,
    val injuries: Int,
    val fatalities: Int,
    val resourcesUsed: String,
    val agenciesInvolved: String,
    val handoffDetails: String,
    val safetyIssues: String,
    val followUpRequired: Boolean,
    val followUpDetails: String,
    val lessonsLearned: String,
    val status: String
)

data class AfterActionReportRecord(
    val id: Long,
    val incidentId: Long,
    val responderId: Int,
    val incidentType: String,
    val responderName: String,
    val operationalOutcome: String,
    val incidentSummary: String,
    val actionsTaken: String,
    val personsAssisted: Int,
    val injuries: Int,
    val fatalities: Int,
    val resourcesUsed: String,
    val agenciesInvolved: String,
    val handoffDetails: String,
    val safetyIssues: String,
    val followUpRequired: Boolean,
    val followUpDetails: String,
    val lessonsLearned: String,
    val status: String,
    val reviewerNotes: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class CommunicationReportRecord(
    val id: Long,
    val status: String,
    val message: String
)

data class IncidentReviewSummary(
    val reviewCount: Int,
    val averageResponseRating: Double,
    val averageCommunicationRating: Double,
    val averageProfessionalismRating: Double,
    val averageOverallRating: Double
)

data class IncidentReviewRecord(
    val id: Long,
    val incidentId: Long,
    val referenceNo: String,
    val incidentType: String,
    val priority: String,
    val locationAddress: String,
    val reviewStatus: String,
    val responseRating: Int,
    val communicationRating: Int,
    val professionalismRating: Int,
    val outcome: String,
    val reviewText: String,
    val createdAtMillis: Long
)

data class IncidentReviewResult(
    val summary: IncidentReviewSummary,
    val reviews: List<IncidentReviewRecord>
)

data class RouteAnalyticsSummary(
    val totalRoutes: Int,
    val totalDistanceMeters: Double,
    val averageDurationSeconds: Double,
    val averageDistanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double
)

data class RouteAnalyticsEntry(
    val id: Long,
    val incidentId: Long,
    val referenceNo: String,
    val incidentType: String,
    val locationAddress: String,
    val startedAt: String,
    val arrivedAt: String,
    val durationSeconds: Long,
    val totalPoints: Int,
    val totalDistanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double
)

data class RouteAnalyticsResult(
    val summary: RouteAnalyticsSummary,
    val routes: List<RouteAnalyticsEntry>
)

