package com.ers.emergencyresponseapp.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ers.emergencyresponseapp.BuildConfig
import com.ers.emergencyresponseapp.data.IncidentRepository
import com.ers.emergencyresponseapp.home.ResponderWorkflowPolicy
import com.ers.emergencyresponseapp.network.AlternativeRouteRequestBody
import com.ers.emergencyresponseapp.network.MarkRouteArrivedRequest
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import com.ers.emergencyresponseapp.routing.ActiveRouteMode
import com.ers.emergencyresponseapp.routing.ActiveRouteSessionMetadata
import com.ers.emergencyresponseapp.routing.ActiveRouteSessionStore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val OPEN_STREET_MAP_RASTER_STYLE = """{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {"id": "osm", "type": "raster", "source": "osm", "minzoom": 0, "maxzoom": 19}
  ]
}"""

/**
 * Live navigation map screen showing the responder's current GPS position,
 * the incident destination, a road-following OSRM route, and an optional
 * externally supplied alternative route from the PHP/MySQL integration.
 */
@Composable
fun LiveRouteMapScreen(
    modifier: Modifier = Modifier,
    destinationLat: Double?,
    destinationLng: Double?,
    destinationAddress: String? = null,
    incidentId: String? = null,
    assignmentId: String? = null,
    responderId: Int = 0,
    viewOnly: Boolean = false,
    onBack: () -> Unit = {},
    onCancelRoute: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val incidentRepository = remember { IncidentRepository() }
    val scope = rememberCoroutineScope()
    val activeRouteStore = remember(context.applicationContext) {
        ActiveRouteSessionStore(context.applicationContext)
    }
    val expectedSessionId = remember(incidentId, assignmentId) {
        ActiveRouteSessionStore.identityFor(
            incidentId = incidentId.orEmpty(),
            assignmentId = assignmentId
        )
    }
    val restoredSession = remember(
        responderId,
        expectedSessionId,
        destinationLat,
        destinationLng,
        destinationAddress,
        viewOnly
    ) {
        if (viewOnly || responderId <= 0 || expectedSessionId == null) {
            null
        } else {
            activeRouteStore.read(responderId, expectedSessionId)
                ?: if (
                    destinationLat != null &&
                    destinationLng != null &&
                    ResponderWorkflowPolicy.hasValidCoordinates(destinationLat, destinationLng)
                ) {
                    activeRouteStore.beginSession(
                        ActiveRouteSessionMetadata(
                            incidentId = incidentId.orEmpty(),
                            assignmentId = assignmentId,
                            responderId = responderId,
                            destinationLat = destinationLat,
                            destinationLng = destinationLng,
                            destinationAddress = destinationAddress.orEmpty()
                        )
                    )
                } else {
                    null
                }
        }
    }
    val routeSessionId = restoredSession?.sessionId
    val routeDestinationLat = restoredSession?.destinationLat ?: destinationLat
    val routeDestinationLng = restoredSession?.destinationLng ?: destinationLng
    val routeDestinationAddress = restoredSession?.destinationAddress
        ?.takeIf { it.isNotBlank() }
        ?: destinationAddress
    val routeIncidentId = restoredSession?.incidentId ?: incidentId
    val routeAssignmentId = if (restoredSession != null) {
        restoredSession.assignmentId
    } else {
        assignmentId
    }
    val restoredOriginalRoute = remember(restoredSession?.originalRouteJson) {
        routeResultFromJson(restoredSession?.originalRouteJson)
            ?: RouteResult.EMPTY
    }
    val restoredApprovedRoute = remember(restoredSession?.approvedRouteJson) {
        routeResultFromJson(restoredSession?.approvedRouteJson)
            ?: RouteResult.EMPTY
    }
    val restoredAlternativeSelected =
        restoredSession?.selectedMode == ActiveRouteMode.ALTERNATIVE &&
                restoredApprovedRoute.points.isNotEmpty()

    // Live GPS state.
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
    var currentAccuracyMeters by remember { mutableStateOf<Float?>(null) }
    var hasLiveLocationFix by remember { mutableStateOf(false) }
    var lastUsableLiveFixElapsedRealtimeMs by remember {
        mutableStateOf<Long?>(null)
    }
    val fusedClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Map state.
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
        }
    }

    // Routing/navigation state.
    var originalRouteResult by remember(routeSessionId) {
        mutableStateOf(restoredOriginalRoute)
    }
    var routeResult by remember(routeSessionId) {
        mutableStateOf(
            if (restoredAlternativeSelected) {
                restoredApprovedRoute
            } else {
                restoredOriginalRoute
            }
        )
    }
    var isFetchingRoute by remember { mutableStateOf(false) }
    var initialOriginLat by remember(routeSessionId) {
        mutableStateOf(restoredSession?.initialOriginLat)
    }
    var initialOriginLng by remember(routeSessionId) {
        mutableStateOf(restoredSession?.initialOriginLng)
    }
    var currentStepIndex by remember(routeSessionId) {
        mutableIntStateOf(
            restoredSession?.currentStepIndex
                ?.coerceAtMost(routeResult.steps.lastIndex.coerceAtLeast(0))
                ?: 0
        )
    }
    var isFollowingUser by remember { mutableStateOf(true) }

    // Alternative-route state. The request ID and waiting flag survive a
    // configuration change, allowing status polling to resume after rotation.
    var alternativeRequestId by rememberSaveable(routeSessionId) {
        mutableStateOf(restoredSession?.pendingAlternativeRequestId)
    }
    var alternativeRequestStartedAtMillis by rememberSaveable(routeSessionId) {
        mutableStateOf(restoredSession?.pendingAlternativeRequestedAtMillis)
    }
    var isWaitingForAlternative by rememberSaveable(routeSessionId) {
        mutableStateOf(restoredSession?.pendingAlternativeRequestId != null)
    }
    var alternativeRequestStartLat by rememberSaveable(routeSessionId) {
        mutableStateOf(restoredSession?.pendingAlternativeStartLat)
    }
    var alternativeRequestStartLng by rememberSaveable(routeSessionId) {
        mutableStateOf(restoredSession?.pendingAlternativeStartLng)
    }
    var usingAlternativeRoute by remember(routeSessionId) {
        mutableStateOf(restoredAlternativeSelected)
    }

    // On-scene and dialog state.
    var isNearDestination by remember { mutableStateOf(false) }
    var onSceneSubmitted by remember { mutableStateOf(false) }
    var isCancellingRoute by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !showExitConfirmDialog) {
        if (viewOnly) {
            onBack()
        } else {
            showExitConfirmDialog = true
        }
    }

    // Start/stop GPS updates with the screen.
    DisposableEffect(fusedClient, viewOnly) {
        if (viewOnly) {
            return@DisposableEffect onDispose { }
        }

        var active = true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!active) return

                result.lastLocation?.let { location ->
                    currentLat = location.latitude
                    currentLng = location.longitude
                    val accuracyMeters = location.accuracy
                        .takeIf { location.hasAccuracy() && it.isFinite() && it > 0f }
                    val fixAgeMillis = (
                            SystemClock.elapsedRealtimeNanos() -
                                    location.elapsedRealtimeNanos
                            ).coerceAtLeast(0L) / 1_000_000L
                    val usableLiveFix =
                        ResponderWorkflowPolicy.hasValidCoordinates(
                            location.latitude,
                            location.longitude
                        ) &&
                                accuracyMeters != null &&
                                accuracyMeters <= ResponderWorkflowPolicy
                                    .MAX_ACCEPTABLE_ACCURACY_METERS &&
                                fixAgeMillis <= MAX_LIVE_FIX_AGE_MS

                    currentAccuracyMeters = accuracyMeters
                    hasLiveLocationFix = usableLiveFix
                    lastUsableLiveFixElapsedRealtimeMs = if (usableLiveFix) {
                        location.elapsedRealtimeNanos / 1_000_000L
                    } else {
                        null
                    }

                    if (
                        location.hasBearing() &&
                        location.hasSpeed() &&
                        location.speed > MIN_BEARING_SPEED_MPS
                    ) {
                        currentBearing = location.bearing
                    }
                }
            }
        }

        if (hasPermission) {
            @Suppress("MissingPermission")
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (!active || location == null || hasLiveLocationFix) {
                    return@addOnSuccessListener
                }

                currentLat = location.latitude
                currentLng = location.longitude
                // A cached fix may be old. It can position the marker, but arrival
                // and the frozen route origin wait for a live callback above.
                currentAccuracyMeters = null

                if (
                    location.hasBearing() &&
                    location.hasSpeed() &&
                    location.speed > MIN_BEARING_SPEED_MPS
                ) {
                    currentBearing = location.bearing
                }
            }

            @Suppress("MissingPermission")
            fusedClient.requestLocationUpdates(
                LocationRequest.Builder(LOCATION_UPDATE_INTERVAL_MS)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(LOCATION_MIN_UPDATE_INTERVAL_MS)
                    .build(),
                callback,
                context.mainLooper
            )
        }

        onDispose {
            active = false
            fusedClient.removeLocationUpdates(callback)
        }
    }

    // Keep MapView lifecycle synchronized even when this screen is opened while
    // the host activity is already STARTED/RESUMED.
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        var destroyed = false

        fun startMap() {
            if (!destroyed && !started) {
                mapView.onStart()
                started = true
            }
        }

        fun resumeMap() {
            if (!destroyed && !resumed) {
                startMap()
                mapView.onResume()
                resumed = true
            }
        }

        fun pauseMap() {
            if (!destroyed && resumed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stopMap() {
            if (!destroyed && started) {
                pauseMap()
                mapView.onStop()
                started = false
            }
        }

        fun destroyMap() {
            if (!destroyed) {
                stopMap()
                mapView.onDestroy()
                destroyed = true
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startMap()
                Lifecycle.Event.ON_RESUME -> resumeMap()
                Lifecycle.Event.ON_PAUSE -> pauseMap()
                Lifecycle.Event.ON_STOP -> stopMap()
                Lifecycle.Event.ON_DESTROY -> destroyMap()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startMap()
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            resumeMap()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyMap()
        }
    }

    fun requestAlternativeRoute() {
        val safeIncidentId = routeIncidentId?.trim().orEmpty()
        val responderLat = currentLat
        val responderLng = currentLng
        val incidentLat = routeDestinationLat
        val incidentLng = routeDestinationLng
        val liveFixAgeMs = lastUsableLiveFixElapsedRealtimeMs?.let { timestamp ->
            SystemClock.elapsedRealtime() - timestamp
        }
        val hasUsableLiveFix = hasLiveLocationFix &&
                currentAccuracyMeters?.let { accuracy ->
                    accuracy.isFinite() &&
                            accuracy > 0f &&
                            accuracy <= ResponderWorkflowPolicy
                                .MAX_ACCEPTABLE_ACCURACY_METERS
                } == true &&
                liveFixAgeMs != null &&
                liveFixAgeMs in 0..MAX_LIVE_FIX_AGE_MS

        if (
            isFetchingRoute ||
            originalRouteResult.points.isEmpty() ||
            routeResult.points.isEmpty()
        ) {
            Toast.makeText(
                context,
                "Wait for the original route to lock before requesting another route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (
            safeIncidentId.isBlank() ||
            responderId <= 0 ||
            routeSessionId == null ||
            responderLat == null ||
            responderLng == null ||
            incidentLat == null ||
            incidentLng == null ||
            !hasUsableLiveFix
        ) {
            Toast.makeText(
                context,
                "Wait for an accurate live GPS fix before requesting another route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (isWaitingForAlternative) return

        scope.launch {
            isWaitingForAlternative = true

            try {
                val response = RetrofitProvider.incidentApi.requestAlternativeRoute(
                    AlternativeRouteRequestBody(
                        incidentId = safeIncidentId,
                        assignmentId = routeAssignmentId
                            ?.trim()
                            ?.takeIf { it.isNotBlank() },
                        responderId = responderId,
                        startLat = responderLat,
                        startLng = responderLng,
                        destinationLat = incidentLat,
                        destinationLng = incidentLng
                    )
                )

                if (response.success && response.requestId != null) {
                    val requestedAtMillis = System.currentTimeMillis()
                    val requestStored = activeRouteStore.savePendingAlternative(
                        responderId = responderId,
                        sessionId = routeSessionId,
                        requestId = response.requestId,
                        startLat = responderLat,
                        startLng = responderLng,
                        requestedAtMillis = requestedAtMillis
                    )
                    if (!requestStored) {
                        isWaitingForAlternative = false
                        Toast.makeText(
                            context,
                            "The active response changed. Request the route again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    alternativeRequestStartLat = responderLat
                    alternativeRequestStartLng = responderLng
                    alternativeRequestStartedAtMillis = requestedAtMillis
                    alternativeRequestId = response.requestId

                    Toast.makeText(
                        context,
                        "Alternative route requested.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isWaitingForAlternative = false
                    alternativeRequestStartedAtMillis = null
                    alternativeRequestStartLat = null
                    alternativeRequestStartLng = null

                    Toast.makeText(
                        context,
                        response.message ?: "Unable to request alternative route.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                isWaitingForAlternative = false
                alternativeRequestStartedAtMillis = null
                alternativeRequestStartLat = null
                alternativeRequestStartLng = null

                Log.e(
                    ALTERNATIVE_ROUTE_LOG_TAG,
                    "Request failed",
                    error
                )

                Toast.makeText(
                    context,
                    "Request failed: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun restoreOriginalRoute() {
        if (originalRouteResult.points.isEmpty()) return

        usingAlternativeRoute = false
        routeResult = originalRouteResult
        currentStepIndex = closestRelevantStepIndex(
            route = originalRouteResult,
            currentLat = currentLat,
            currentLng = currentLng
        )
        routeSessionId?.let { sessionId ->
            activeRouteStore.selectMode(
                responderId = responderId,
                sessionId = sessionId,
                mode = ActiveRouteMode.ORIGINAL
            )
            activeRouteStore.saveStepIndex(
                responderId = responderId,
                sessionId = sessionId,
                stepIndex = currentStepIndex
            )
        }
    }

    fun ownsCurrentRouteSession(): Boolean {
        val sessionId = routeSessionId ?: return false
        return activeRouteStore.read(responderId, sessionId) != null
    }

    fun stopAndClearOwnedRoute(): Boolean {
        val sessionId = routeSessionId ?: return false
        if (activeRouteStore.read(responderId, sessionId) == null) return false

        context.stopService(Intent(context, RouteMonitoringService::class.java))
        val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
        if (navPrefs.getString("pending_en_route_session_id", null) == sessionId) {
            navPrefs.edit()
                .putBoolean("pending_en_route_check", false)
                .remove("pending_en_route_incident_id")
                .remove("pending_en_route_session_id")
                .apply()
        }
        return activeRouteStore.clearSession(responderId, sessionId)
    }

    fun confirmOnScene() {
        if (onSceneSubmitted) return

        if (!ownsCurrentRouteSession()) {
            Toast.makeText(
                context,
                "This map is no longer the active response route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val latitude = currentLat
        val longitude = currentLng
        val destinationLatitude = routeDestinationLat
        val destinationLongitude = routeDestinationLng
        val lastFixTimestamp = lastUsableLiveFixElapsedRealtimeMs
        val fixAgeMs = lastFixTimestamp?.let {
            SystemClock.elapsedRealtime() - it
        }
        val permittedRadius = ResponderWorkflowPolicy.arrivalExitRadiusMeters(
            currentAccuracyMeters
        )
        val stillAtIncident =
            isNearDestination &&
                    hasLiveLocationFix &&
                    fixAgeMs != null &&
                    fixAgeMs in 0..MAX_LIVE_FIX_AGE_MS &&
                    permittedRadius != null &&
                    latitude != null &&
                    longitude != null &&
                    destinationLatitude != null &&
                    destinationLongitude != null &&
                    haversineDistanceMeters(
                        latitude,
                        longitude,
                        destinationLatitude,
                        destinationLongitude
                    ) <= permittedRadius
        if (!stillAtIncident) {
            isNearDestination = false
            Toast.makeText(
                context,
                "Wait for a fresh, accurate GPS fix at the incident before reporting on-scene.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val assignmentKey = routeAssignmentId?.takeIf { it.isNotBlank() }
            ?: routeIncidentId?.takeIf { it.isNotBlank() }
        val routeRecordId = routeIncidentId?.toIntOrNull()

        if (assignmentKey == null || responderId <= 0) {
            Toast.makeText(
                context,
                "Unable to report on-scene: assignment information is missing.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        onSceneSubmitted = true
        scope.launch {
            try {
                // This live PHP call is the authoritative status transition. Do
                // not stop tracking or leave the screen until it succeeds.
                incidentRepository.updateAssignmentStatus(
                    assignmentId = assignmentKey,
                    responderId = responderId,
                    status = "on_scene"
                )
            } catch (cancelled: CancellationException) {
                onSceneSubmitted = false
                throw cancelled
            } catch (error: Exception) {
                onSceneSubmitted = false
                Log.e("LiveGPS", "On-scene status update failed", error)
                Toast.makeText(
                    context,
                    "On-scene was not submitted: ${error.message ?: "server unavailable"}",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val routeSummaryMessage = if (routeRecordId != null && routeRecordId > 0) {
                try {
                    val response = RetrofitProvider.incidentApi.markRouteArrived(
                        MarkRouteArrivedRequest(
                            incident_id = routeRecordId,
                            assignment_id = routeAssignmentId?.toIntOrNull(),
                            responder_id = responderId
                        )
                    )

                    when {
                        response.success -> "On-scene reported to command"
                        response.message.orEmpty().contains("already recorded", ignoreCase = true) ->
                            "On-scene reported; arrival was already recorded"
                        else ->
                            "On-scene reported; route summary was not saved: " +
                                    response.message.orEmpty().ifBlank { "server rejected the route summary" }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("LiveGPS", "Route summary save failed", error)
                    "On-scene reported; route summary could not be saved: " +
                            (error.message ?: "server unavailable")
                }
            } else {
                "On-scene reported; no route reference was available for analytics"
            }

            stopAndClearOwnedRoute()

            Toast.makeText(context, routeSummaryMessage, Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    fun cancelLiveRoute() {
        if (isCancellingRoute) return

        if (!ownsCurrentRouteSession()) {
            Toast.makeText(
                context,
                "This map is no longer the active response route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val assignmentKey = routeAssignmentId?.takeIf { it.isNotBlank() }
            ?: routeIncidentId?.takeIf { it.isNotBlank() }
        if (assignmentKey == null || responderId <= 0) {
            Toast.makeText(
                context,
                "Unable to cancel route: assignment information is missing.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        isCancellingRoute = true
        scope.launch {
            try {
                incidentRepository.updateAssignmentStatus(
                    assignmentId = assignmentKey,
                    responderId = responderId,
                    status = "received"
                )

                stopAndClearOwnedRoute()

                showExitConfirmDialog = false
                onCancelRoute()
                onBack()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    "Route was not cancelled: ${error.message ?: "server unavailable"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isCancellingRoute = false
            }
        }
    }

    // Poll the PHP API while the SQL request is pending.
    LaunchedEffect(alternativeRequestId, responderId) {
        val requestId = alternativeRequestId

        if (requestId == null) {
            // Recover from an inconsistent restored state.
            if (isWaitingForAlternative) {
                isWaitingForAlternative = false
            }
            return@LaunchedEffect
        }

        fun clearPendingAlternative(message: String? = null) {
            isWaitingForAlternative = false
            alternativeRequestId = null
            alternativeRequestStartedAtMillis = null
            alternativeRequestStartLat = null
            alternativeRequestStartLng = null
            routeSessionId?.let { sessionId ->
                activeRouteStore.clearPendingAlternative(
                    responderId,
                    sessionId,
                    requestId
                )
            }
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        val requestedAtMillis = alternativeRequestStartedAtMillis
        val initialRequestAgeMillis = requestedAtMillis?.let {
            System.currentTimeMillis() - it
        }
        if (
            requestedAtMillis == null ||
            initialRequestAgeMillis == null ||
            initialRequestAgeMillis !in 0..ALTERNATIVE_ROUTE_TIMEOUT_MS
        ) {
            clearPendingAlternative(
                "The alternative-route request expired. Your current route was kept."
            )
            return@LaunchedEffect
        }

        var pollAttempts = 0
        while (
            pollAttempts < ALTERNATIVE_ROUTE_MAX_POLL_ATTEMPTS &&
            System.currentTimeMillis() - requestedAtMillis in
                0..ALTERNATIVE_ROUTE_TIMEOUT_MS
        ) {
            pollAttempts += 1
            try {
                val response = RetrofitProvider.incidentApi.getAlternativeRouteStatus(
                    requestId = requestId,
                    responderId = responderId
                )

                if (
                    System.currentTimeMillis() - requestedAtMillis !in
                    0..ALTERNATIVE_ROUTE_TIMEOUT_MS
                ) {
                    clearPendingAlternative(
                        "The alternative-route request expired. Your current route was kept."
                    )
                    return@LaunchedEffect
                }

                if (!response.success || response.requestId != requestId) {
                    clearPendingAlternative(
                        "The returned route did not match this request. Your current route was kept."
                    )
                    return@LaunchedEffect
                }

                when (response.status.lowercase()) {
                    "ready" -> {
                        val normalizedPoints = normalizeReceivedRoutePoints(
                            response.points
                                .sortedBy { it.sequence }
                                .map { point ->
                                    point.lat to point.lng
                                }
                        )

                        if (normalizedPoints.size < 2) {
                            clearPendingAlternative(
                                "Received route has insufficient or invalid coordinates.",
                            )
                            return@LaunchedEffect
                        }

                        val expectedStartLat = alternativeRequestStartLat
                        val expectedStartLng = alternativeRequestStartLng
                        val expectedEndLat = routeDestinationLat
                        val expectedEndLng = routeDestinationLng
                        if (
                            expectedStartLat == null ||
                            expectedStartLng == null ||
                            expectedEndLat == null ||
                            expectedEndLng == null
                        ) {
                            clearPendingAlternative(
                                "The route request no longer matches this response. Your current route was kept."
                            )
                            return@LaunchedEffect
                        }

                        val expectedStart = expectedStartLat to expectedStartLng
                        val expectedEnd = expectedEndLat to expectedEndLng

                        val orientedPoints = orientRoutePoints(
                            points = normalizedPoints,
                            start = expectedStart,
                            end = expectedEnd
                        )
                        val startGapMeters = haversineDistanceMeters(
                            expectedStart.first,
                            expectedStart.second,
                            orientedPoints.first().first,
                            orientedPoints.first().second
                        )
                        val endGapMeters = haversineDistanceMeters(
                            expectedEnd.first,
                            expectedEnd.second,
                            orientedPoints.last().first,
                            orientedPoints.last().second
                        )
                        if (
                            startGapMeters > MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS ||
                            endGapMeters > MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
                        ) {
                            clearPendingAlternative(
                                "The returned route does not connect to this responder and incident. Your current route was kept."
                            )
                            return@LaunchedEffect
                        }

                        val drawablePoints = connectToEndpoints(
                            routePoints = orientedPoints,
                            start = expectedStart,
                            end = expectedEnd,
                            maxConnectorMeters = MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
                        )

                        val calculatedDistance = calculatePolylineDistanceMeters(
                            drawablePoints
                        )
                        val directDistance = haversineDistanceMeters(
                            expectedStart.first,
                            expectedStart.second,
                            expectedEnd.first,
                            expectedEnd.second
                        )
                        val maximumPlausibleDistance = maxOf(
                            directDistance * 5.0,
                            MIN_MAXIMUM_ALTERNATIVE_DISTANCE_METERS
                        )
                        if (
                            !calculatedDistance.isFinite() ||
                            calculatedDistance <= 0.0 ||
                            calculatedDistance > maximumPlausibleDistance
                        ) {
                            clearPendingAlternative(
                                "The returned route length is not plausible for this incident. Your current route was kept."
                            )
                            return@LaunchedEffect
                        }

                        val distance = response.distanceMeters
                            ?.takeIf { it.isFinite() && it > 0.0 }
                            ?: calculatedDistance

                        val duration = response.durationSeconds
                            ?.takeIf { it.isFinite() && it > 0.0 }
                            ?: estimateDurationSeconds(distance)

                        val approvedResult = RouteResult(
                            points = drawablePoints,
                            steps = listOf(
                                RouteStep(
                                    instruction = "Alternative route active — follow the highlighted route",
                                    distanceMeters = distance,
                                    targetLat = expectedEnd.first,
                                    targetLng = expectedEnd.second
                                )
                            ),
                            totalDistanceMeters = distance,
                            totalDurationSeconds = duration,
                            isFallback = false
                        )
                        val sessionId = routeSessionId
                        if (
                            sessionId == null ||
                            !activeRouteStore.acceptApprovedAlternative(
                                responderId = responderId,
                                sessionId = sessionId,
                                requestId = requestId,
                                routeJson = routeResultToJson(approvedResult)
                            )
                        ) {
                            clearPendingAlternative(
                                "The active response changed. Your current route was kept."
                            )
                            return@LaunchedEffect
                        }

                        usingAlternativeRoute = true
                        routeResult = approvedResult
                        currentStepIndex = 0
                        isWaitingForAlternative = false
                        alternativeRequestStartLat = null
                        alternativeRequestStartLng = null
                        alternativeRequestStartedAtMillis = null

                        Toast.makeText(
                            context,
                            "Alternative route activated.",
                            Toast.LENGTH_SHORT
                        ).show()

                        alternativeRequestId = null
                        return@LaunchedEffect
                    }

                    "failed" -> {
                        clearPendingAlternative(
                            response.message ?: "No alternative route was found."
                        )
                        return@LaunchedEffect
                    }

                    "cancelled" -> {
                        clearPendingAlternative()
                        return@LaunchedEffect
                    }

                    // pending or processing: continue polling.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(
                    ALTERNATIVE_ROUTE_LOG_TAG,
                    "Status checking failed",
                    error
                )
                // Keep polling because this may only be a temporary network issue.
            }

            delay(ALTERNATIVE_ROUTE_POLL_INTERVAL_MS)
        }

        clearPendingAlternative(
            "The alternative-route request timed out. Your current route was kept."
        )
    }

    // Always redraw the current RouteResult after the style becomes available.
    LaunchedEffect(mapLibreMap, styleReady, routeResult.points) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        map.style?.let { style ->
            updateRouteLine(
                style = style,
                points = routeResult.points
            )
        }
    }

    // Keep the responder marker moving even while an external route is active.
    LaunchedEffect(
        currentLat,
        currentLng,
        currentBearing,
        mapLibreMap,
        styleReady,
        viewOnly,
        isFollowingUser
    ) {
        if (viewOnly) return@LaunchedEffect

        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect

        map.style?.let { style ->
            updateCurrentPositionMarker(
                style = style,
                lat = lat,
                lng = lng,
                bearing = currentBearing
            )
        }

        if (!isFollowingUser) return@LaunchedEffect

        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(
                map.cameraPosition.zoom.takeIf { it > 1.0 }
                    ?: DEFAULT_NAVIGATION_ZOOM
            )
            .build()
    }

    // Proximity hysteresis for the On Scene button.
    LaunchedEffect(
        currentLat,
        currentLng,
        currentAccuracyMeters,
        hasLiveLocationFix,
        lastUsableLiveFixElapsedRealtimeMs,
        routeDestinationLat,
        routeDestinationLng,
        viewOnly
    ) {
        if (viewOnly) {
            isNearDestination = false
            return@LaunchedEffect
        }

        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect
        val destLat = routeDestinationLat ?: return@LaunchedEffect
        val destLng = routeDestinationLng ?: return@LaunchedEffect
        val enterRadius = ResponderWorkflowPolicy.arrivalEnterRadiusMeters(
            currentAccuracyMeters
        )
        val exitRadius = ResponderWorkflowPolicy.arrivalExitRadiusMeters(
            currentAccuracyMeters
        )
        if (enterRadius == null || exitRadius == null) {
            isNearDestination = false
            return@LaunchedEffect
        }

        val distance = haversineDistanceMeters(
            lat,
            lng,
            destLat,
            destLng
        )

        isNearDestination = when {
            distance <= enterRadius -> true
            distance > exitRadius -> false
            else -> isNearDestination
        }
    }

    // Destination marker and view-only camera.
    LaunchedEffect(
        routeDestinationLat,
        routeDestinationLng,
        mapLibreMap,
        styleReady,
        viewOnly
    ) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        val lat = routeDestinationLat ?: return@LaunchedEffect
        val lng = routeDestinationLng ?: return@LaunchedEffect

        map.style?.let { style ->
            updateDestinationMarker(style, lat, lng)
        }

        if (viewOnly) {
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(DEFAULT_NAVIGATION_ZOOM)
                .build()
        }
    }

    // Freeze the first live GPS origin for this response. Later movement only
    // moves the responder marker; it never silently changes the dispatched route.
    LaunchedEffect(
        routeSessionId,
        hasLiveLocationFix,
        lastUsableLiveFixElapsedRealtimeMs,
        currentAccuracyMeters,
        currentLat,
        currentLng,
        viewOnly
    ) {
        if (viewOnly || !hasLiveLocationFix) return@LaunchedEffect
        if (initialOriginLat != null && initialOriginLng != null) return@LaunchedEffect

        val lastFixTimestamp = lastUsableLiveFixElapsedRealtimeMs
            ?: return@LaunchedEffect
        val fixAgeMs = SystemClock.elapsedRealtime() - lastFixTimestamp
        if (
            fixAgeMs !in 0..MAX_LIVE_FIX_AGE_MS ||
            ResponderWorkflowPolicy.arrivalEnterRadiusMeters(
                currentAccuracyMeters
            ) == null
        ) {
            return@LaunchedEffect
        }

        val latitude = currentLat ?: return@LaunchedEffect
        val longitude = currentLng ?: return@LaunchedEffect
        if (!ResponderWorkflowPolicy.hasValidCoordinates(latitude, longitude)) {
            return@LaunchedEffect
        }

        val sessionId = routeSessionId ?: return@LaunchedEffect
        val originSaved = activeRouteStore.saveInitialOrigin(
            responderId = responderId,
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude
        )
        if (originSaved) {
            initialOriginLat = latitude
            initialOriginLng = longitude
        } else {
            // Another instance may have already frozen the same response.
            // Re-read that authoritative first origin instead of overwriting it.
            activeRouteStore.read(responderId, sessionId)?.let { session ->
                initialOriginLat = session.initialOriginLat
                initialOriginLng = session.initialOriginLng
            }
        }
    }

    // Establish the original OSRM route once from the frozen origin. It remains
    // unchanged until the other group returns a valid, approved alternative.
    LaunchedEffect(
        routeSessionId,
        initialOriginLat,
        initialOriginLng,
        routeDestinationLat,
        routeDestinationLng,
        viewOnly,
        originalRouteResult.points.isNotEmpty()
    ) {
        if (viewOnly || originalRouteResult.points.isNotEmpty()) return@LaunchedEffect

        val originLat = initialOriginLat ?: return@LaunchedEffect
        val originLng = initialOriginLng ?: return@LaunchedEffect
        val destLat = routeDestinationLat ?: return@LaunchedEffect
        val destLng = routeDestinationLng ?: return@LaunchedEffect
        if (!ResponderWorkflowPolicy.hasValidCoordinates(originLat, originLng) ||
            !ResponderWorkflowPolicy.hasValidCoordinates(destLat, destLng)
        ) {
            return@LaunchedEffect
        }

        val result = try {
            isFetchingRoute = true
            fetchRoadRoute(
                startLat = originLat,
                startLng = originLng,
                endLat = destLat,
                endLng = destLng
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("LiveRouteMap", "Initial road route failed", error)
            RouteResult.EMPTY
        } finally {
            isFetchingRoute = false
        }

        val finalResult = if (result.points.isNotEmpty()) {
            val startGap = haversineDistanceMeters(
                originLat,
                originLng,
                result.points.first().first,
                result.points.first().second
            )
            val endGap = haversineDistanceMeters(
                destLat,
                destLng,
                result.points.last().first,
                result.points.last().second
            )

            val drawablePoints = connectToEndpoints(
                routePoints = result.points,
                start = originLat to originLng,
                end = destLat to destLng,
                maxConnectorMeters = MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
            )

            result.copy(
                points = drawablePoints,
                steps = result.steps.ifEmpty {
                    listOf(
                        RouteStep(
                            instruction = "Continue on the highlighted route",
                            distanceMeters = result.totalDistanceMeters,
                            targetLat = destLat,
                            targetLng = destLng
                        )
                    )
                },
                startSnapDistanceMeters = maxOf(
                    result.startSnapDistanceMeters,
                    startGap
                ),
                endSnapDistanceMeters = maxOf(
                    result.endSnapDistanceMeters,
                    endGap
                )
            )
        } else {
            val straightDistance = haversineDistanceMeters(
                originLat,
                originLng,
                destLat,
                destLng
            )

            RouteResult(
                points = listOf(originLat to originLng, destLat to destLng),
                steps = listOf(
                    RouteStep(
                        instruction = "Head toward destination (straight-line estimate — live routing unavailable)",
                        distanceMeters = straightDistance,
                        targetLat = destLat,
                        targetLng = destLng
                    )
                ),
                totalDistanceMeters = straightDistance,
                totalDurationSeconds = estimateDurationSeconds(straightDistance),
                isFallback = true
            )
        }

        val sessionId = routeSessionId ?: return@LaunchedEffect
        val routeSaved = activeRouteStore.saveOriginalRoute(
            responderId = responderId,
            sessionId = sessionId,
            routeJson = routeResultToJson(finalResult)
        )
        val authoritativeResult = if (routeSaved) {
            finalResult
        } else {
            routeResultFromJson(
                activeRouteStore.read(responderId, sessionId)?.originalRouteJson
            ) ?: return@LaunchedEffect
        }
        if (authoritativeResult.points.isEmpty()) return@LaunchedEffect

        originalRouteResult = authoritativeResult

        if (!usingAlternativeRoute) {
            routeResult = authoritativeResult
            currentStepIndex = 0
            routeSessionId?.let { sessionId ->
                activeRouteStore.selectMode(responderId, sessionId, ActiveRouteMode.ORIGINAL)
                activeRouteStore.saveStepIndex(responderId, sessionId, 0)
            }
        }
    }

    // Reconcile monotonically against nearby later maneuvers. This catches up
    // after the map was closed and avoids getting stuck when a 3-second GPS
    // interval skips over a narrow maneuver radius.
    LaunchedEffect(
        currentLat,
        currentLng,
        currentAccuracyMeters,
        hasLiveLocationFix,
        lastUsableLiveFixElapsedRealtimeMs,
        routeResult,
        viewOnly
    ) {
        if (viewOnly || !hasLiveLocationFix) return@LaunchedEffect
        val lastFixTimestamp = lastUsableLiveFixElapsedRealtimeMs
            ?: return@LaunchedEffect
        if (
            SystemClock.elapsedRealtime() - lastFixTimestamp !in
            0..MAX_LIVE_FIX_AGE_MS ||
            ResponderWorkflowPolicy.arrivalEnterRadiusMeters(
                currentAccuracyMeters
            ) == null
        ) {
            return@LaunchedEffect
        }

        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect
        val steps = routeResult.steps
        val currentStep = steps.getOrNull(currentStepIndex)
            ?: return@LaunchedEffect

        val distanceToStep = haversineDistanceMeters(
            lat,
            lng,
            currentStep.targetLat,
            currentStep.targetLng
        )

        val lookAheadLastIndex = minOf(steps.lastIndex, currentStepIndex + 6)
        val closestLaterIndex = (currentStepIndex..lookAheadLastIndex)
            .minByOrNull { index ->
                val step = steps[index]
                haversineDistanceMeters(
                    lat,
                    lng,
                    step.targetLat,
                    step.targetLng
                )
            }
            ?: currentStepIndex
        val proximityIndex = if (
            distanceToStep < MANEUVER_ADVANCE_DISTANCE_METERS &&
            currentStepIndex < steps.lastIndex
        ) {
            currentStepIndex + 1
        } else {
            currentStepIndex
        }
        val reconciledIndex = maxOf(
            currentStepIndex,
            closestLaterIndex,
            proximityIndex
        )

        if (reconciledIndex != currentStepIndex) {
            currentStepIndex = reconciledIndex
            routeSessionId?.let { sessionId ->
                activeRouteStore.saveStepIndex(
                    responderId = responderId,
                    sessionId = sessionId,
                    stepIndex = currentStepIndex
                )
            }
        }
    }

    val recenterBottomPadding = when {
        usingAlternativeRoute && isNearDestination -> 300.dp
        usingAlternativeRoute -> 250.dp
        isNearDestination -> 230.dp
        else -> 180.dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapLibreMap = map
                        styleReady = false

                        val mapTilerKey = BuildConfig.MAPTILER_API_KEY.trim()
                        val style = if (mapTilerKey.isNotBlank()) {
                            Style.Builder().fromUri(
                                "https://api.maptiler.com/maps/streets-v2/style.json" +
                                        "?key=$mapTilerKey"
                            )
                        } else {
                            Log.w(
                                "LiveRouteMap",
                                "MAPTILER_API_KEY is not configured; using the OSM raster fallback."
                            )
                            Style.Builder().fromJson(OPEN_STREET_MAP_RASTER_STYLE)
                        }

                        map.setStyle(style) { loadedStyle ->
                            setupRouteSource(loadedStyle)
                            styleReady = true
                        }

                        map.addOnCameraMoveStartedListener { reason ->
                            if (
                                reason ==
                                MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                            ) {
                                isFollowingUser = false
                            }
                        }
                    }
                }
            }
        )

        IconButton(
            onClick = {
                if (viewOnly) {
                    onBack()
                } else {
                    showExitConfirmDialog = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .background(Color.White, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        val currentStep = routeResult.steps.getOrNull(currentStepIndex)
        if (!viewOnly && currentStep != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(
                        top = 56.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1E88E5),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = currentStep.instruction,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                val distanceToManeuver = if (
                    currentLat != null && currentLng != null
                ) {
                    haversineDistanceMeters(
                        currentLat!!,
                        currentLng!!,
                        currentStep.targetLat,
                        currentStep.targetLng
                    )
                } else {
                    null
                }

                Text(
                    text = distanceToManeuver
                        ?.let(::formatDistance)
                        .orEmpty(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!viewOnly) {
                Button(
                    onClick = ::requestAlternativeRoute,
                    enabled =
                    !isWaitingForAlternative &&
                            !isFetchingRoute &&
                            originalRouteResult.points.isNotEmpty() &&
                            routeResult.points.isNotEmpty() &&
                            hasLiveLocationFix &&
                            lastUsableLiveFixElapsedRealtimeMs?.let { timestamp ->
                                SystemClock.elapsedRealtime() - timestamp in
                                        0..MAX_LIVE_FIX_AGE_MS
                            } == true &&
                            ResponderWorkflowPolicy.arrivalEnterRadiusMeters(
                                currentAccuracyMeters
                            ) != null &&
                            currentLat != null &&
                            currentLng != null &&
                            routeDestinationLat != null &&
                            routeDestinationLng != null,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF6C00),
                        disabledContainerColor = Color(0xFFEF6C00)
                            .copy(alpha = 0.78f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (isWaitingForAlternative) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Waiting for alternative route…",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = if (usingAlternativeRoute) {
                                "Request Another Alternative"
                            } else {
                                "Traffic Ahead — Request Alternative"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                if (usingAlternativeRoute) {
                    OutlinedButton(
                        onClick = ::restoreOriginalRoute,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Restore Original Route")
                    }
                }
            }

            if (!viewOnly && isNearDestination) {
                Button(
                    onClick = ::confirmOnScene,
                    enabled = !onSceneSubmitted,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        disabledContainerColor = Color(0xFF2E7D32)
                            .copy(alpha = 0.78f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (onSceneSubmitted) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reporting arrival…",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You're on scene — Confirm Arrival",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                when {
                    viewOnly -> {
                        Column {
                            Text(
                                text = routeDestinationAddress ?: "Pinned location",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Viewing pinned location",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    }

                    isFetchingRoute && routeResult.points.isEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Finding route…",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }

                    routeResult.points.isNotEmpty() -> {
                        Column {
                            Text(
                                text = buildString {
                                    append(
                                        formatDistance(
                                            routeResult.totalDistanceMeters
                                        )
                                    )
                                    append(" • ")
                                    append(
                                        formatDuration(
                                            routeResult.totalDurationSeconds
                                        )
                                    )
                                    if (isFetchingRoute) {
                                        append(" (updating…)")
                                    }
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (usingAlternativeRoute) {
                                Text(
                                    text = "Approved alternative route active • route locked",
                                    fontSize = 12.sp,
                                    color = Color(0xFFEF6C00),
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Original route locked",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = currentAccuracyMeters
                                    ?.takeIf { it.isFinite() && it > 0f }
                                    ?.let { "GPS accuracy ±${it.toInt()} m" }
                                    ?: "Waiting for a live GPS fix for arrival verification",
                                fontSize = 11.sp,
                                color = Color(0xFF757575)
                            )

                            when {
                                routeResult.isFallback -> {
                                    Text(
                                        text = "No live route — showing straight-line estimate",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                }

                                routeResult.startSnapDistanceMeters >
                                        MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS -> {
                                    Text(
                                        text = "You're ${
                                            formatDistance(
                                                routeResult.startSnapDistanceMeters
                                            )
                                        } from the nearest road — route shown starts there, not at your exact position",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                }

                                routeResult.endSnapDistanceMeters >
                                        MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS -> {
                                    Text(
                                        text = "Destination is ${
                                            formatDistance(
                                                routeResult.endSnapDistanceMeters
                                            )
                                        } from the nearest road — last stretch isn't shown",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = routeDestinationAddress ?: "Waiting for GPS…",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (!viewOnly && !isFollowingUser) {
            IconButton(
                onClick = {
                    isFollowingUser = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = recenterBottomPadding,
                        end = 16.dp
                    )
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Recenter on my location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showExitConfirmDialog) {
        Dialog(
            onDismissRequest = {
                showExitConfirmDialog = false
            }
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Navigation Check",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can leave the map while live tracking continues. Use Resume Navigation on Home or tap the tracking notification to return.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCancellingRoute && !onSceneSubmitted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F766E)
                        ),
                        onClick = {
                            showExitConfirmDialog = false
                            onBack()
                        }
                    ) {
                        Text("Exit Map — Keep Responding")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isCancellingRoute && !onSceneSubmitted,
                            onClick = { cancelLiveRoute() }
                        ) {
                            if (isCancellingRoute) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancelling…")
                            } else {
                                Text("Cancel Response")
                            }
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { showExitConfirmDialog = false }
                        ) {
                            Text("Stay on Map")
                        }
                    }
                }
            }
        }
    }
}

private data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val targetLat: Double,
    val targetLng: Double
)

private data class RouteResult(
    val points: List<Pair<Double, Double>>, // latitude, longitude
    val steps: List<RouteStep>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val isFallback: Boolean = false,
    val startSnapDistanceMeters: Double = 0.0,
    val endSnapDistanceMeters: Double = 0.0
) {
    companion object {
        val EMPTY = RouteResult(
            points = emptyList(),
            steps = emptyList(),
            totalDistanceMeters = 0.0,
            totalDurationSeconds = 0.0
        )
    }
}

private fun routeResultToJson(route: RouteResult): String {
    val points = JSONArray()
    route.points.forEach { (latitude, longitude) ->
        points.put(JSONArray().put(latitude).put(longitude))
    }

    val steps = JSONArray()
    route.steps.forEach { step ->
        steps.put(
            JSONObject()
                .put("instruction", step.instruction)
                .put("distanceMeters", step.distanceMeters)
                .put("targetLat", step.targetLat)
                .put("targetLng", step.targetLng)
        )
    }

    return JSONObject()
        .put("points", points)
        .put("steps", steps)
        .put("totalDistanceMeters", route.totalDistanceMeters)
        .put("totalDurationSeconds", route.totalDurationSeconds)
        .put("isFallback", route.isFallback)
        .put("startSnapDistanceMeters", route.startSnapDistanceMeters)
        .put("endSnapDistanceMeters", route.endSnapDistanceMeters)
        .toString()
}

private fun routeResultFromJson(raw: String?): RouteResult? {
    if (raw.isNullOrBlank()) return null

    return runCatching {
        val root = JSONObject(raw)
        val pointArray = root.getJSONArray("points")
        val points = buildList {
            for (index in 0 until pointArray.length()) {
                val pair = pointArray.getJSONArray(index)
                val latitude = pair.getDouble(0)
                val longitude = pair.getDouble(1)
                require(ResponderWorkflowPolicy.hasValidCoordinates(latitude, longitude))
                add(latitude to longitude)
            }
        }
        require(points.size >= 2)

        val stepArray = root.getJSONArray("steps")
        val steps = buildList {
            for (index in 0 until stepArray.length()) {
                val item = stepArray.getJSONObject(index)
                val distance = item.getDouble("distanceMeters")
                val targetLat = item.getDouble("targetLat")
                val targetLng = item.getDouble("targetLng")
                require(distance.isFinite() && distance >= 0.0)
                require(ResponderWorkflowPolicy.hasValidCoordinates(targetLat, targetLng))
                add(
                    RouteStep(
                        instruction = item.getString("instruction").take(500),
                        distanceMeters = distance,
                        targetLat = targetLat,
                        targetLng = targetLng
                    )
                )
            }
        }
        val totalDistance = root.getDouble("totalDistanceMeters")
        val totalDuration = root.getDouble("totalDurationSeconds")
        val startSnap = root.optDouble("startSnapDistanceMeters", 0.0)
        val endSnap = root.optDouble("endSnapDistanceMeters", 0.0)
        require(totalDistance.isFinite() && totalDistance >= 0.0)
        require(totalDuration.isFinite() && totalDuration >= 0.0)
        require(startSnap.isFinite() && startSnap >= 0.0)
        require(endSnap.isFinite() && endSnap >= 0.0)

        val restoredSteps = steps.ifEmpty {
            val target = points.last()
            listOf(
                RouteStep(
                    instruction = "Continue on the saved route",
                    distanceMeters = totalDistance,
                    targetLat = target.first,
                    targetLng = target.second
                )
            )
        }

        RouteResult(
            points = points,
            steps = restoredSteps,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
            isFallback = root.optBoolean("isFallback", false),
            startSnapDistanceMeters = startSnap,
            endSnapDistanceMeters = endSnap
        )
    }.getOrNull()
}

private fun closestRelevantStepIndex(
    route: RouteResult,
    currentLat: Double?,
    currentLng: Double?
): Int {
    if (route.steps.isEmpty() || currentLat == null || currentLng == null) return 0
    return route.steps.indices.minByOrNull { index ->
        val step = route.steps[index]
        haversineDistanceMeters(
            currentLat,
            currentLng,
            step.targetLat,
            step.targetLng
        )
    } ?: 0
}

private const val LOCATION_UPDATE_INTERVAL_MS = 3_000L
private const val LOCATION_MIN_UPDATE_INTERVAL_MS = 1_000L
private const val MAX_LIVE_FIX_AGE_MS = 15_000L
private const val MIN_BEARING_SPEED_MPS = 0.5f
private const val DEFAULT_NAVIGATION_ZOOM = 16.0
private const val MANEUVER_ADVANCE_DISTANCE_METERS = 25.0
private const val MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS = 60.0
private const val ASSUMED_FALLBACK_SPEED_METERS_PER_SECOND = 40_000.0 / 3_600.0
private const val ALTERNATIVE_ROUTE_POLL_INTERVAL_MS = 3_000L
private const val ALTERNATIVE_ROUTE_MAX_POLL_ATTEMPTS = 40
private const val ALTERNATIVE_ROUTE_TIMEOUT_MS = 120_000L
private const val ALTERNATIVE_ROUTE_LOG_TAG = "AlternativeRoute"

private fun setupRouteSource(style: Style) {
    if (style.getSource(ROUTE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
        style.addLayer(
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(
                    AndroidColor.parseColor("#4C8A89")
                ),
                PropertyFactory.lineWidth(5f)
            )
        )
    }

    if (style.getSource(CURRENT_POS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(CURRENT_POS_SOURCE_ID))
        style.addLayer(
            CircleLayer(
                CURRENT_POS_HALO_LAYER_ID,
                CURRENT_POS_SOURCE_ID
            ).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(
                    AndroidColor.parseColor("#1E88E5")
                ),
                PropertyFactory.circleOpacity(0.25f)
            )
        )

        if (style.getImage(ARROW_ICON_ID) == null) {
            style.addImage(
                ARROW_ICON_ID,
                createDirectionArrowBitmap()
            )
        }

        style.addLayer(
            SymbolLayer(
                CURRENT_POS_LAYER_ID,
                CURRENT_POS_SOURCE_ID
            ).withProperties(
                PropertyFactory.iconImage(ARROW_ICON_ID),
                PropertyFactory.iconRotate(
                    Expression.get("bearing")
                ),
                PropertyFactory.iconRotationAlignment(
                    Property.ICON_ROTATION_ALIGNMENT_MAP
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f)
            )
        )
    }

    if (style.getSource(DEST_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(DEST_SOURCE_ID))
        style.addLayer(
            CircleLayer(DEST_LAYER_ID, DEST_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(
                    AndroidColor.parseColor("#D32F2F")
                ),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
            )
        )
    }
}

private fun createDirectionArrowBitmap(): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(
        size,
        size,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#1E88E5")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center - 4f, circlePaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(center, center, center - 4f, strokePaint)

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(center, size * 0.22f)
        lineTo(size * 0.68f, size * 0.62f)
        lineTo(center, size * 0.50f)
        lineTo(size * 0.32f, size * 0.62f)
        close()
    }
    canvas.drawPath(path, arrowPaint)

    return bitmap
}

private val routingClient = OkHttpClient()

@Suppress("SpellCheckingInspection")
private suspend fun fetchRoadRoute(
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double
): RouteResult = withContext(Dispatchers.IO) {
    try {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                    "$startLng,$startLat;$endLng,$endLat" +
                    "?overview=full&geometries=geojson&steps=true"

        val request = Request.Builder()
            .url(url)
            .build()

        routingClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                Log.e(
                    "OSRMRouting",
                    "Route request failed: ${response.code} " +
                            "${response.message}; body=$errorBody"
                )
                return@withContext RouteResult.EMPTY
            }

            val body = response.body?.string()
                ?: return@withContext RouteResult.EMPTY
            val json = JSONObject(body)

            if (json.optString("code") != "Ok") {
                Log.e(
                    "OSRMRouting",
                    "OSRM returned code=${json.optString("code")} " +
                            "message=${json.optString("message")}"
                )
                return@withContext RouteResult.EMPTY
            }

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                Log.e("OSRMRouting", "No routes in response")
                return@withContext RouteResult.EMPTY
            }

            val route = routes.getJSONObject(0)
            val coordinates = route
                .getJSONObject("geometry")
                .getJSONArray("coordinates")

            val points = (0 until coordinates.length()).map { index ->
                val coordinate = coordinates.getJSONArray(index)
                coordinate.getDouble(1) to coordinate.getDouble(0)
            }

            val steps = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")

            if (legs != null) {
                for (legIndex in 0 until legs.length()) {
                    val stepArray = legs
                        .getJSONObject(legIndex)
                        .optJSONArray("steps")
                        ?: continue

                    for (stepIndex in 0 until stepArray.length()) {
                        val stepJson = stepArray.getJSONObject(stepIndex)
                        val maneuver = stepJson.optJSONObject("maneuver")
                            ?: continue
                        val location = maneuver.optJSONArray("location")
                            ?: continue

                        val streetName = stepJson
                            .optString("name")
                            .ifBlank { "the road" }

                        steps.add(
                            RouteStep(
                                instruction = buildInstruction(
                                    type = maneuver.optString(
                                        "type",
                                        "continue"
                                    ),
                                    modifier = maneuver.optString(
                                        "modifier"
                                    ),
                                    streetName = streetName
                                ),
                                distanceMeters = stepJson.optDouble(
                                    "distance",
                                    0.0
                                ),
                                targetLat = location.optDouble(1),
                                targetLng = location.optDouble(0)
                            )
                        )
                    }
                }
            }

            val waypoints = json.optJSONArray("waypoints")
            val startSnapDistance = if (
                waypoints != null && waypoints.length() > 0
            ) {
                waypoints.optJSONObject(0)
                    ?.optDouble("distance", 0.0)
                    ?: 0.0
            } else {
                0.0
            }

            val endSnapDistance = if (
                waypoints != null && waypoints.length() > 0
            ) {
                waypoints.optJSONObject(waypoints.length() - 1)
                    ?.optDouble("distance", 0.0)
                    ?: 0.0
            } else {
                0.0
            }

            RouteResult(
                points = points,
                steps = steps,
                totalDistanceMeters = route.optDouble(
                    "distance",
                    0.0
                ),
                totalDurationSeconds = route.optDouble(
                    "duration",
                    0.0
                ),
                startSnapDistanceMeters = startSnapDistance,
                endSnapDistanceMeters = endSnapDistance
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e(
            "OSRMRouting",
            "Route fetch exception",
            error
        )
        RouteResult.EMPTY
    }
}

private fun buildInstruction(
    type: String,
    modifier: String,
    streetName: String
): String {
    val direction = modifier
        .replace("_", " ")
        .trim()
        .takeIf { it.isNotEmpty() }

    return when (type) {
        "depart" -> "Head out toward $streetName"
        "arrive" -> "Arrive at destination"
        "turn" -> if (direction != null) {
            "Turn $direction onto $streetName"
        } else {
            "Turn onto $streetName"
        }
        "new name" -> "Continue onto $streetName"
        "continue" -> if (direction != null) {
            "Continue $direction onto $streetName"
        } else {
            "Continue onto $streetName"
        }
        "merge" -> "Merge onto $streetName"
        "on ramp" -> "Take the ramp onto $streetName"
        "off ramp" -> "Take the exit onto $streetName"
        "fork" -> if (direction != null) {
            "Keep $direction at the fork onto $streetName"
        } else {
            "Continue at the fork onto $streetName"
        }
        "end of road" -> if (direction != null) {
            "Turn $direction onto $streetName"
        } else {
            "Turn onto $streetName"
        }
        "roundabout", "rotary" ->
            "Enter the roundabout, then exit onto $streetName"
        "roundabout turn" -> if (direction != null) {
            "At the roundabout, turn $direction onto $streetName"
        } else {
            "At the roundabout, continue onto $streetName"
        }
        else -> "Continue onto $streetName"
    }
}

private fun normalizeReceivedRoutePoints(
    points: List<Pair<Double, Double>>
): List<Pair<Double, Double>> {
    if (points.size !in 2..MAX_ALTERNATIVE_ROUTE_POINTS) return emptyList()

    val normalized = mutableListOf<Pair<Double, Double>>()

    points.forEach { point ->
        val lat = point.first
        val lng = point.second

        if (
            !lat.isFinite() ||
            !lng.isFinite() ||
            lat !in -90.0..90.0 ||
            lng !in -180.0..180.0
        ) {
            // Never silently repair a server-supplied operational route. One
            // invalid coordinate invalidates the whole returned payload.
            return emptyList()
        }

        val previous = normalized.lastOrNull()
        if (
            previous == null ||
            haversineDistanceMeters(
                previous.first,
                previous.second,
                lat,
                lng
            ) >= MIN_ROUTE_POINT_SPACING_METERS
        ) {
            normalized.add(lat to lng)
        }
    }

    return normalized
}

private fun orientRoutePoints(
    points: List<Pair<Double, Double>>,
    start: Pair<Double, Double>?,
    end: Pair<Double, Double>?
): List<Pair<Double, Double>> {
    if (points.size < 2 || start == null || end == null) {
        return points
    }

    val forwardScore = haversineDistanceMeters(
        start.first,
        start.second,
        points.first().first,
        points.first().second
    ) + haversineDistanceMeters(
        end.first,
        end.second,
        points.last().first,
        points.last().second
    )

    val reverseScore = haversineDistanceMeters(
        start.first,
        start.second,
        points.last().first,
        points.last().second
    ) + haversineDistanceMeters(
        end.first,
        end.second,
        points.first().first,
        points.first().second
    )

    return if (reverseScore < forwardScore) {
        points.asReversed()
    } else {
        points
    }
}

private fun connectToEndpoints(
    routePoints: List<Pair<Double, Double>>,
    start: Pair<Double, Double>,
    end: Pair<Double, Double>,
    maxConnectorMeters: Double
): List<Pair<Double, Double>> {
    if (routePoints.isEmpty()) return listOf(start, end)

    val result = routePoints.toMutableList()
    val first = result.first()
    val startGap = haversineDistanceMeters(
        start.first,
        start.second,
        first.first,
        first.second
    )

    if (
        startGap > MIN_CONNECTOR_DISTANCE_METERS &&
        startGap <= maxConnectorMeters
    ) {
        result.add(0, start)
    }

    val last = result.last()
    val endGap = haversineDistanceMeters(
        end.first,
        end.second,
        last.first,
        last.second
    )

    if (
        endGap > MIN_CONNECTOR_DISTANCE_METERS &&
        endGap <= maxConnectorMeters
    ) {
        result.add(end)
    }

    return result
}

private fun haversineDistanceMeters(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lng2 - lng1)

    val a = sin(latitudeDelta / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(longitudeDelta / 2).pow(2.0)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun calculatePolylineDistanceMeters(
    points: List<Pair<Double, Double>>
): Double {
    if (points.size < 2) return 0.0

    return points.zipWithNext().sumOf { (start, end) ->
        haversineDistanceMeters(
            start.first,
            start.second,
            end.first,
            end.second
        )
    }
}

private fun estimateDurationSeconds(distanceMeters: Double): Double {
    return distanceMeters / ASSUMED_FALLBACK_SPEED_METERS_PER_SECOND
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1_000.0) {
        "%.1f km".format(meters / 1_000.0)
    } else {
        "${meters.toInt()} m"
    }
}

private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60.0).toInt()

    return when {
        seconds >= 0.0 && seconds < 60.0 -> "<1 min"
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        else -> "$minutes min"
    }
}

private fun updateRouteLine(
    style: Style,
    points: List<Pair<Double, Double>>
) {
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        ?: return

    if (points.size < 2) {
        source.setGeoJson(
            """{"type":"FeatureCollection","features":[]}"""
        )
        return
    }

    source.setGeoJson(
        LineString.fromLngLats(
            points.map { (lat, lng) ->
                Point.fromLngLat(lng, lat)
            }
        )
    )
}

private fun updateCurrentPositionMarker(
    style: Style,
    lat: Double,
    lng: Double,
    bearing: Float
) {
    val source = style.getSourceAs<GeoJsonSource>(CURRENT_POS_SOURCE_ID)
        ?: return

    val properties = JsonObject().apply {
        addProperty("bearing", bearing)
    }

    source.setGeoJson(
        Feature.fromGeometry(
            Point.fromLngLat(lng, lat),
            properties
        )
    )
}

private fun updateDestinationMarker(
    style: Style,
    lat: Double,
    lng: Double
) {
    val source = style.getSourceAs<GeoJsonSource>(DEST_SOURCE_ID)
        ?: return

    source.setGeoJson(Point.fromLngLat(lng, lat))
}

private const val MIN_ROUTE_POINT_SPACING_METERS = 0.5
private const val MAX_ALTERNATIVE_ROUTE_POINTS = 20_000
private const val MIN_MAXIMUM_ALTERNATIVE_DISTANCE_METERS = 10_000.0
private const val MIN_CONNECTOR_DISTANCE_METERS = 15.0
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val CURRENT_POS_SOURCE_ID = "current-pos-source"
private const val DEST_SOURCE_ID = "dest-source"
private const val CURRENT_POS_LAYER_ID = "current-pos-layer"
private const val CURRENT_POS_HALO_LAYER_ID = "current-pos-halo-layer"
private const val DEST_LAYER_ID = "dest-layer"
private const val ARROW_ICON_ID = "current-pos-arrow-icon"
