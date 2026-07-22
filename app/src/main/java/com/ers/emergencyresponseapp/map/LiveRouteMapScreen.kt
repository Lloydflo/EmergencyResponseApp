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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ers.emergencyresponseapp.BuildConfig
import com.ers.emergencyresponseapp.analytics.RouteHistoryStore
import com.ers.emergencyresponseapp.features.assigned.AssignedIncidentsViewModel
import com.ers.emergencyresponseapp.network.AlternativeRouteRequestBody
import com.ers.emergencyresponseapp.network.MarkRouteArrivedRequest
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
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
    val assignedVm: AssignedIncidentsViewModel = viewModel()
    val scope = rememberCoroutineScope()

    // Live GPS state.
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
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
    var routeResult by remember { mutableStateOf(RouteResult.EMPTY) }
    var isFetchingRoute by remember { mutableStateOf(false) }
    var lastFetchLat by remember { mutableStateOf<Double?>(null) }
    var lastFetchLng by remember { mutableStateOf<Double?>(null) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isFollowingUser by remember { mutableStateOf(true) }

    // Alternative-route state. The request ID and waiting flag survive a
    // configuration change, allowing status polling to resume after rotation.
    var alternativeRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var isWaitingForAlternative by rememberSaveable {
        mutableStateOf(false)
    }
    var usingAlternativeRoute by remember {
        mutableStateOf(false)
    }

    // On-scene and dialog state.
    var isNearDestination by remember { mutableStateOf(false) }
    var onSceneSubmitted by remember { mutableStateOf(false) }
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
                if (!active || location == null) return@addOnSuccessListener

                currentLat = location.latitude
                currentLng = location.longitude

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
        val safeIncidentId = incidentId?.trim().orEmpty()
        val responderLat = currentLat
        val responderLng = currentLng
        val incidentLat = destinationLat
        val incidentLng = destinationLng

        if (
            safeIncidentId.isBlank() ||
            responderId <= 0 ||
            responderLat == null ||
            responderLng == null ||
            incidentLat == null ||
            incidentLng == null
        ) {
            Toast.makeText(
                context,
                "Responder or incident location is unavailable.",
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
                        assignmentId = assignmentId
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
                    alternativeRequestId = response.requestId

                    Toast.makeText(
                        context,
                        "Alternative route requested.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isWaitingForAlternative = false

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

    fun returnToAutomaticRoute() {
        usingAlternativeRoute = false
        routeResult = RouteResult.EMPTY
        currentStepIndex = 0
        lastFetchLat = null
        lastFetchLng = null
    }

    fun confirmOnScene() {
        if (onSceneSubmitted) return
        onSceneSubmitted = true

        assignedVm.updateStatus(
            assignmentId = assignmentId ?: incidentId ?: "",
            status = "on_scene",
            responderId = responderId
        )

        context.stopService(
            Intent(context, RouteMonitoringService::class.java)
        )

        context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pending_en_route_check", false)
            .remove("pending_en_route_incident_id")
            .apply()

        incidentId?.let { id ->
            RouteHistoryStore.completeRoute(context, id)
        }

        scope.launch {
            try {
                val response = RetrofitProvider.incidentApi.markRouteArrived(
                    MarkRouteArrivedRequest(
                        incident_id = incidentId?.toIntOrNull() ?: 0,
                        responder_id = responderId
                    )
                )

                Toast.makeText(
                    context,
                    if (response.success) {
                        "On-scene reported to command"
                    } else {
                        response.message ?: "Arrival already recorded"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(
                    "LiveGPS",
                    "Route arrived save failed",
                    error
                )

                Toast.makeText(
                    context,
                    "Arrival was saved locally, but command could not be reached.",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Navigate only after the API attempt, otherwise this screen's
            // coroutine scope would be cancelled before the request finishes.
            onBack()
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

        while (true) {
            try {
                val response = RetrofitProvider.incidentApi.getAlternativeRouteStatus(
                    requestId = requestId,
                    responderId = responderId
                )

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
                            isWaitingForAlternative = false
                            alternativeRequestId = null

                            Toast.makeText(
                                context,
                                "Received route has insufficient or invalid coordinates.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@LaunchedEffect
                        }

                        val orientedPoints = orientRoutePoints(
                            points = normalizedPoints,
                            start = if (currentLat != null && currentLng != null) {
                                currentLat!! to currentLng!!
                            } else {
                                null
                            },
                            end = if (destinationLat != null && destinationLng != null) {
                                destinationLat to destinationLng
                            } else {
                                null
                            }
                        )

                        val drawablePoints = if (
                            currentLat != null &&
                            currentLng != null &&
                            destinationLat != null &&
                            destinationLng != null
                        ) {
                            connectToEndpoints(
                                routePoints = orientedPoints,
                                start = currentLat!! to currentLng!!,
                                end = destinationLat to destinationLng,
                                maxConnectorMeters = MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
                            )
                        } else {
                            orientedPoints
                        }

                        val calculatedDistance = calculatePolylineDistanceMeters(
                            drawablePoints
                        )

                        val distance = response.distanceMeters
                            ?.takeIf { it.isFinite() && it > 0.0 }
                            ?: calculatedDistance

                        val duration = response.durationSeconds
                            ?.takeIf { it.isFinite() && it > 0.0 }
                            ?: estimateDurationSeconds(distance)

                        val finalTarget = if (
                            destinationLat != null && destinationLng != null
                        ) {
                            destinationLat to destinationLng
                        } else {
                            drawablePoints.last()
                        }

                        usingAlternativeRoute = true
                        routeResult = RouteResult(
                            points = drawablePoints,
                            steps = listOf(
                                RouteStep(
                                    instruction = "Alternative route active — follow the highlighted route",
                                    distanceMeters = distance,
                                    targetLat = finalTarget.first,
                                    targetLng = finalTarget.second
                                )
                            ),
                            totalDistanceMeters = distance,
                            totalDurationSeconds = duration,
                            isFallback = false
                        )
                        currentStepIndex = 0
                        isWaitingForAlternative = false

                        Toast.makeText(
                            context,
                            "Alternative route activated.",
                            Toast.LENGTH_SHORT
                        ).show()

                        alternativeRequestId = null
                        return@LaunchedEffect
                    }

                    "failed" -> {
                        isWaitingForAlternative = false

                        Toast.makeText(
                            context,
                            response.message ?: "No alternative route was found.",
                            Toast.LENGTH_LONG
                        ).show()

                        alternativeRequestId = null
                        return@LaunchedEffect
                    }

                    "cancelled" -> {
                        isWaitingForAlternative = false
                        alternativeRequestId = null
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
        destinationLat,
        destinationLng,
        viewOnly
    ) {
        if (viewOnly) {
            isNearDestination = false
            return@LaunchedEffect
        }

        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect
        val destLat = destinationLat ?: return@LaunchedEffect
        val destLng = destinationLng ?: return@LaunchedEffect

        val distance = haversineDistanceMeters(
            lat,
            lng,
            destLat,
            destLng
        )

        isNearDestination = when {
            distance <= ON_SCENE_ENTER_DISTANCE_METERS -> true
            distance > ON_SCENE_EXIT_DISTANCE_METERS -> false
            else -> isNearDestination
        }
    }

    // Destination marker and view-only camera.
    LaunchedEffect(
        destinationLat,
        destinationLng,
        mapLibreMap,
        styleReady,
        viewOnly
    ) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        val lat = destinationLat ?: return@LaunchedEffect
        val lng = destinationLng ?: return@LaunchedEffect

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

    // Normal OSRM route. This effect is disabled while an external alternative
    // route is active so it cannot overwrite the received geometry.
    LaunchedEffect(
        currentLat,
        currentLng,
        destinationLat,
        destinationLng,
        mapLibreMap,
        styleReady,
        viewOnly,
        usingAlternativeRoute
    ) {
        if (viewOnly) {
            routeResult = RouteResult.EMPTY
            return@LaunchedEffect
        }

        if (usingAlternativeRoute) {
            isFetchingRoute = false
            return@LaunchedEffect
        }

        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        val cLat = currentLat
        val cLng = currentLng
        val destLat = destinationLat
        val destLng = destinationLng

        if (
            cLat == null ||
            cLng == null ||
            destLat == null ||
            destLng == null
        ) {
            routeResult = RouteResult.EMPTY
            return@LaunchedEffect
        }

        val previousLat = lastFetchLat
        val previousLng = lastFetchLng
        val movedEnough = previousLat == null ||
                previousLng == null ||
                haversineDistanceMeters(
                    previousLat,
                    previousLng,
                    cLat,
                    cLng
                ) > ROUTE_RECALCULATION_DISTANCE_METERS

        if (!movedEnough && routeResult.points.isNotEmpty()) {
            return@LaunchedEffect
        }

        val result = try {
            isFetchingRoute = true
            fetchRoadRoute(
                startLat = cLat,
                startLng = cLng,
                endLat = destLat,
                endLng = destLng
            )
        } finally {
            isFetchingRoute = false
        }

        // The effect can be cancelled/restarted while the network request is in
        // flight. Do not commit a normal route after an external route activates.
        if (usingAlternativeRoute) return@LaunchedEffect

        val finalResult = if (result.points.isNotEmpty()) {
            val startGap = haversineDistanceMeters(
                cLat,
                cLng,
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
                start = cLat to cLng,
                end = destLat to destLng,
                maxConnectorMeters = MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
            )

            result.copy(
                points = drawablePoints,
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
                cLat,
                cLng,
                destLat,
                destLng
            )

            RouteResult(
                points = listOf(cLat to cLng, destLat to destLng),
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

        routeResult = finalResult
        lastFetchLat = cLat
        lastFetchLng = cLng
        currentStepIndex = 0
    }

    // Advance the current OSRM instruction as the responder approaches it.
    LaunchedEffect(currentLat, currentLng, routeResult, viewOnly) {
        if (viewOnly) return@LaunchedEffect

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

        if (
            distanceToStep < MANEUVER_ADVANCE_DISTANCE_METERS &&
            currentStepIndex < steps.lastIndex
        ) {
            currentStepIndex += 1
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

                        val styleUrl =
                            "https://api.maptiler.com/maps/streets-v2/style.json" +
                                    "?key=${BuildConfig.MAPTILER_API_KEY}"

                        map.setStyle(styleUrl) { style ->
                            setupRouteSource(style)
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
                            currentLat != null &&
                            currentLng != null &&
                            destinationLat != null &&
                            destinationLng != null,
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
                        onClick = ::returnToAutomaticRoute,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Return to Automatic Route")
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
                                text = destinationAddress ?: "Pinned location",
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
                                    text = "Alternative route active",
                                    fontSize = 12.sp,
                                    color = Color(0xFFEF6C00),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

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
                            text = destinationAddress ?: "Waiting for GPS…",
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
                        text = "You're about to leave live navigation.\nAre you still responding to this incident?",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showExitConfirmDialog = false

                                context.stopService(
                                    Intent(
                                        context,
                                        RouteMonitoringService::class.java
                                    )
                                )

                                context.getSharedPreferences(
                                    "nav_prefs",
                                    Context.MODE_PRIVATE
                                )
                                    .edit()
                                    .putBoolean(
                                        "pending_en_route_check",
                                        false
                                    )
                                    .remove(
                                        "pending_en_route_incident_id"
                                    )
                                    .apply()

                                if (
                                    !incidentId.isNullOrBlank() &&
                                    responderId > 0
                                ) {
                                    assignedVm.updateStatus(
                                        assignmentId =
                                        assignmentId ?: incidentId,
                                        status = "received",
                                        responderId = responderId
                                    )
                                }

                                onCancelRoute()
                                onBack()
                            }
                        ) {
                            Text("Cancel Route")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E)
                            ),
                            onClick = {
                                showExitConfirmDialog = false
                            }
                        ) {
                            Text("Continue")
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

private const val LOCATION_UPDATE_INTERVAL_MS = 3_000L
private const val LOCATION_MIN_UPDATE_INTERVAL_MS = 1_000L
private const val MIN_BEARING_SPEED_MPS = 0.5f
private const val DEFAULT_NAVIGATION_ZOOM = 16.0
private const val ON_SCENE_ENTER_DISTANCE_METERS = 10.0
private const val ON_SCENE_EXIT_DISTANCE_METERS = 25.0
private const val MANEUVER_ADVANCE_DISTANCE_METERS = 25.0
private const val ROUTE_RECALCULATION_DISTANCE_METERS = 25.0
private const val MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS = 60.0
private const val ASSUMED_FALLBACK_SPEED_METERS_PER_SECOND = 40_000.0 / 3_600.0
private const val ALTERNATIVE_ROUTE_POLL_INTERVAL_MS = 3_000L
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
            return@forEach
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
private const val MIN_CONNECTOR_DISTANCE_METERS = 15.0
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val CURRENT_POS_SOURCE_ID = "current-pos-source"
private const val DEST_SOURCE_ID = "dest-source"
private const val CURRENT_POS_LAYER_ID = "current-pos-layer"
private const val CURRENT_POS_HALO_LAYER_ID = "current-pos-halo-layer"
private const val DEST_LAYER_ID = "dest-layer"
private const val ARROW_ICON_ID = "current-pos-arrow-icon"