package com.ers.emergencyresponseapp.network

import com.google.gson.annotations.SerializedName

data class AlternativeRouteRequestBody(
    @SerializedName("incident_id")
    val incidentId: String,

    @SerializedName("assignment_id")
    val assignmentId: String?,

    @SerializedName("responder_id")
    val responderId: Int,

    @SerializedName("start_lat")
    val startLat: Double,

    @SerializedName("start_lng")
    val startLng: Double,

    @SerializedName("destination_lat")
    val destinationLat: Double,

    @SerializedName("destination_lng")
    val destinationLng: Double
)

data class AlternativeRouteRequestResponse(
    val success: Boolean = false,

    @SerializedName("request_id")
    val requestId: Long? = null,

    val status: String? = null,
    val message: String? = null
)

data class AlternativeRoutePointDto(
    val sequence: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class AlternativeRouteStatusResponse(
    val success: Boolean = false,

    @SerializedName("request_id")
    val requestId: Long? = null,

    val status: String = "",

    val points: List<AlternativeRoutePointDto> =
        emptyList(),

    @SerializedName("distance_m")
    val distanceMeters: Double? = null,

    @SerializedName("duration_s")
    val durationSeconds: Double? = null,

    val message: String? = null
)