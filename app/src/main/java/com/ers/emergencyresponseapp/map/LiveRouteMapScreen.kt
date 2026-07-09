package com.ers.emergencyresponseapp.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ers.emergencyresponseapp.BuildConfig
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ers.emergencyresponseapp.features.assigned.AssignedIncidentsViewModel


/**
 * Live navigation map screen showing the responder's current GPS position (as a
 * heading-aware arrow) and the incident destination, with a real road-following
 * route + turn-by-turn instructions drawn between them.
 */
@Composable
fun LiveRouteMapScreen(
    modifier: Modifier = Modifier,
    destinationLat: Double?,
    destinationLng: Double?,
    destinationAddress: String? = null,
    incidentId: String? = null,
    responderId: Int = 0,
    onBack: () -> Unit = {},
    onCancelRoute: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val assignedVm: AssignedIncidentsViewModel = viewModel()

    // ── Self-managed live GPS position + heading ──
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

// Intercept the hardware/gesture back button too, not just the on-screen arrow
    BackHandler(enabled = true) {
        showExitConfirmDialog = true
    }

    DisposableEffect(Unit) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    // Only trust the bearing when the device is actually moving,
                    // otherwise GPS bearing is noisy/meaningless at a standstill.
                    if (loc.hasBearing() && loc.hasSpeed() && loc.speed > 0.5f) {
                        currentBearing = loc.bearing
                    }
                }
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            @Suppress("MissingPermission")
            fusedClient.requestLocationUpdates(
                LocationRequest.Builder(3000)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(1000)
                    .build(),
                callback,
                context.mainLooper
            )
        }
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    // ── MapView + MapLibre setup ──
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // ── Routing / navigation state ──
    var routeResult by remember { mutableStateOf(RouteResult.EMPTY) }
    var isFetchingRoute by remember { mutableStateOf(false) }
    var lastFetchLat by remember { mutableStateOf<Double?>(null) }
    var lastFetchLng by remember { mutableStateOf<Double?>(null) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Whether the camera should keep snapping to the live position. Starts
    // true (typical nav behavior), but flips off the moment the responder
    // drags/pinches the map themselves, so their pan/zoom to inspect the
    // route isn't immediately yanked back. A "recenter" button lets them
    // opt back into follow mode.
    var isFollowingUser by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mapView = mv

                val styleUrl =
                    "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_API_KEY}"

                mv.getMapAsync { map ->
                    map.setStyle(styleUrl) { style ->
                        setupRouteSource(style)
                        styleReady = true
                    }
                    // A camera move that started from a user gesture (drag, pinch,
                    // two-finger rotate) means they're deliberately looking around —
                    // stop auto-recentering until they ask to come back.
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            isFollowingUser = false
                        }
                    }
                    mapLibreMap = map
                }
                mv
            }
        )

        // Back button overlay
        IconButton(
            onClick = { showExitConfirmDialog = true },
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .background(Color.White, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // ── Turn-by-turn instruction card ──
        val step = routeResult.steps.getOrNull(currentStepIndex)
        if (step != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1E88E5), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = step.instruction,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val distToManeuver = if (currentLat != null && currentLng != null) {
                    haversineDistanceMeters(currentLat!!, currentLng!!, step.targetLat, step.targetLng)
                } else null
                Text(
                    text = distToManeuver?.let { formatDistance(it) } ?: "",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }

        // Recenter button — only shown once the user has panned/zoomed away
        // from live-follow mode.
        if (!isFollowingUser) {
            IconButton(
                onClick = { isFollowingUser = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 16.dp)
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

        // ── Bottom summary card: total distance / ETA / recalculating ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            if (isFetchingRoute && routeResult.points.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text(
                    text = "  Finding route…",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else if (routeResult.points.isNotEmpty()) {
                Column {
                    Text(
                        text = "${formatDistance(routeResult.totalDistanceMeters)} • " +
                                formatDuration(routeResult.totalDurationSeconds) +
                                (if (isFetchingRoute) "  (updating…)" else ""),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (routeResult.isFallback) {
                        Text(
                            text = "No live route — showing straight-line estimate",
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C)
                        )
                    } else if (routeResult.startSnapDistanceMeters > MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS) {
                        Text(
                            text = "You're ${formatDistance(routeResult.startSnapDistanceMeters)} from the nearest road — " +
                                    "route shown starts there, not at your exact position",
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C)
                        )
                    } else if (routeResult.endSnapDistanceMeters > MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS) {
                        Text(
                            text = "Destination is ${formatDistance(routeResult.endSnapDistanceMeters)} from the " +
                                    "nearest road — last stretch isn't shown",
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
            } else {
                Text(text = destinationAddress ?: "Waiting for GPS…", fontSize = 14.sp)
            }
        }
    }
    if (showExitConfirmDialog) {
        Dialog(onDismissRequest = { showExitConfirmDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Navigation Check",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You're about to leave live navigation.\nAre you still responding to this incident?",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showExitConfirmDialog = false

                                context.stopService(Intent(context, RouteMonitoringService::class.java))
                                context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("pending_en_route_check", false)
                                    .remove("pending_en_route_incident_id")
                                    .commit()

                                // Flip the assignment back to "received" server-side, same as HomeScreen's dialog
                                if (!incidentId.isNullOrBlank() && responderId > 0) {
                                    assignedVm.updateStatus(
                                        assignmentId = incidentId,
                                        status = "received",
                                        responderId = responderId
                                    )
                                }

                                onBack()
                            }
                        ) { Text("Cancel Route") }

                        Button(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            onClick = { showExitConfirmDialog = false } // stay on the map, keep navigating
                        ) { Text("Continue") }
                    }
                }
            }
        }
    }

    // Keep MapView lifecycle synced with the Compose host
    DisposableEffect(lifecycleOwner, mapView) {
        val mv = mapView ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Update current position marker + camera (follow mode) as GPS updates
    LaunchedEffect(currentLat, currentLng, currentBearing, mapLibreMap, styleReady) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect
        // Always keep the marker itself up to date — only the camera move is
        // gated on follow mode, so the blue arrow stays accurate even while
        // the user is looking elsewhere on the map.
        map.style?.let { style -> updateCurrentPositionMarker(style, lat, lng, currentBearing) }
        if (!isFollowingUser) return@LaunchedEffect
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(map.cameraPosition.zoom.takeIf { it > 1.0 } ?: 16.0)
            .build()
    }

    // Update destination marker
    LaunchedEffect(destinationLat, destinationLng, mapLibreMap, styleReady) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val lat = destinationLat ?: return@LaunchedEffect
        val lng = destinationLng ?: return@LaunchedEffect
        map.style?.let { style -> updateDestinationMarker(style, lat, lng) }
    }

    // Fetch and draw a real road-following route (throttled so we don't
    // hammer the routing API on every 1-3s GPS tick)
    LaunchedEffect(currentLat, currentLng, destinationLat, destinationLng, mapLibreMap, styleReady) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val cLat = currentLat; val cLng = currentLng

        if (cLat == null || cLng == null || destinationLat == null || destinationLng == null) {
            map.style?.let { style -> updateRouteLine(style, emptyList()) }
            return@LaunchedEffect
        }

        val movedEnough = lastFetchLat == null ||
                haversineDistanceMeters(lastFetchLat!!, lastFetchLng!!, cLat, cLng) > 25.0

        if (!movedEnough && routeResult.points.isNotEmpty()) return@LaunchedEffect

        isFetchingRoute = true
        val result = fetchRoadRoute(cLat, cLng, destinationLat, destinationLng)
        isFetchingRoute = false

        val drawablePoints: List<Pair<Double, Double>>
        val finalResult: RouteResult

        if (result.points.isNotEmpty()) {
            // Guarantee the drawn line always touches the live position and the
            // destination, but ONLY when the gap is small enough to plausibly be
            // a driveway/footpath (e.g. GPS drift, or parked just off the road).
            // If OSRM had to snap the fix/destination much further than that, a
            // straight line across the gap is not a real path — it can cut
            // straight across a river, a building, or fenced property — so we
            // leave that segment out and warn the responder instead of showing
            // a route that implies they can walk/drive straight there.
            val startGap = haversineDistanceMeters(cLat, cLng, result.points.first().first, result.points.first().second)
            val endGap = haversineDistanceMeters(destinationLat, destinationLng, result.points.last().first, result.points.last().second)

            drawablePoints = connectToEndpoints(
                result.points,
                start = cLat to cLng,
                end = destinationLat to destinationLng,
                maxConnectorMeters = MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS
            )
            finalResult = result.copy(
                points = drawablePoints,
                startSnapDistanceMeters = maxOf(result.startSnapDistanceMeters, startGap),
                endSnapDistanceMeters = maxOf(result.endSnapDistanceMeters, endGap)
            )
        } else {
            // Routing failed (bad/missing API key, no internet, no coverage, etc).
            // Fall back to a straight line, but still give the user a real
            // distance/ETA instead of a misleading "0 m • 0 min".
            drawablePoints = listOf(cLat to cLng, destinationLat to destinationLng)
            val straightDistance = haversineDistanceMeters(cLat, cLng, destinationLat, destinationLng)
            val estimatedDuration = straightDistance / (40_000.0 / 3600.0) // assume ~40 km/h
            finalResult = RouteResult(
                points = drawablePoints,
                steps = listOf(
                    RouteStep(
                        instruction = "Head toward destination (straight-line estimate — live routing unavailable)",
                        distanceMeters = straightDistance,
                        targetLat = destinationLat,
                        targetLng = destinationLng
                    )
                ),
                totalDistanceMeters = straightDistance,
                totalDurationSeconds = estimatedDuration,
                isFallback = true
            )
        }

        routeResult = finalResult
        lastFetchLat = cLat
        lastFetchLng = cLng
        currentStepIndex = 0

        map.style?.let { style -> updateRouteLine(style, drawablePoints) }
    }

    // Advance the current instruction as the user nears each maneuver point
    LaunchedEffect(currentLat, currentLng, routeResult) {
        val lat = currentLat ?: return@LaunchedEffect
        val lng = currentLng ?: return@LaunchedEffect
        val steps = routeResult.steps
        if (steps.isEmpty()) return@LaunchedEffect
        val current = steps.getOrNull(currentStepIndex) ?: return@LaunchedEffect
        val distToCurrent = haversineDistanceMeters(lat, lng, current.targetLat, current.targetLng)
        if (distToCurrent < 25.0 && currentStepIndex < steps.lastIndex) {
            currentStepIndex += 1
        }
    }
}

// ── Data types ──

private data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val targetLat: Double,
    val targetLng: Double
)

private data class RouteResult(
    val points: List<Pair<Double, Double>>, // lat, lng
    val steps: List<RouteStep>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val isFallback: Boolean = false,
    // How far (meters) the raw GPS fix / destination had to be snapped to reach
    // the nearest routable road. Large values mean the user (or destination)
    // is off the road network entirely — e.g. across a river with no bridge —
    // so any straight line drawn to close that gap is just a guess, not a path.
    val startSnapDistanceMeters: Double = 0.0,
    val endSnapDistanceMeters: Double = 0.0
) {
    companion object {
        val EMPTY = RouteResult(emptyList(), emptyList(), 0.0, 0.0)
    }
}

// Beyond this, we no longer treat the gap between the raw GPS fix and the
// snapped road as "just walk to the driveway" — it likely means open terrain,
// a river, a fence, etc. sits in between, so we stop faking a straight path
// across it and instead warn the responder to reach the road network first.
private const val MAX_PLAUSIBLE_OFFROAD_CONNECTOR_METERS = 60.0

// ── Style/source helpers ──

private fun setupRouteSource(style: Style) {
    if (style.getSource(ROUTE_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
        style.addLayer(
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#4C8A89")),
                PropertyFactory.lineWidth(5f)
            )
        )
    }
    if (style.getSource(CURRENT_POS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(CURRENT_POS_SOURCE_ID))
        // Soft halo behind the direction arrow
        style.addLayer(
            CircleLayer(CURRENT_POS_HALO_LAYER_ID, CURRENT_POS_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(AndroidColor.parseColor("#1E88E5")),
                PropertyFactory.circleOpacity(0.25f)
            )
        )
        if (style.getImage(ARROW_ICON_ID) == null) {
            style.addImage(ARROW_ICON_ID, createDirectionArrowBitmap())
        }
        style.addLayer(
            SymbolLayer(CURRENT_POS_LAYER_ID, CURRENT_POS_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(ARROW_ICON_ID),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
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
                PropertyFactory.circleColor(AndroidColor.parseColor("#D32F2F")),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
            )
        )
    }
}

/** Draws a simple blue circle with a white directional arrow, used as the live-position icon. */
private fun createDirectionArrowBitmap(): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
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
    startLat: Double, startLng: Double,
    endLat: Double, endLng: Double
): RouteResult = withContext(Dispatchers.IO) {
    try {
        // OSRM's public demo server: free, no API key required. It returns
        // GeoJSON geometry directly (no polyline decoding needed) plus
        // per-step maneuver data for turn-by-turn instructions.
        //
        // NOTE: router.project-osrm.org is a shared demo instance meant for
        // evaluation/light use — no uptime guarantee and it can rate-limit
        // under heavy traffic. For a production emergency-response app,
        // self-host OSRM (Docker image, fully free) or move to a paid-tier
        // provider such as Mapbox Directions (100k free requests/month) or
        // GraphHopper (2,500 free requests/day) once you exceed light use.
        val url = "https://router.project-osrm.org/route/v1/driving/" +
                "$startLng,$startLat;$endLng,$endLat" +
                "?overview=full&geometries=geojson&steps=true"

        val request = Request.Builder().url(url).build()
        routingClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                Log.e(
                    "OSRMRouting",
                    "Route request failed: ${response.code} ${response.message} — body: $errorBody"
                )
                return@withContext RouteResult.EMPTY
            }
            val body = response.body?.string() ?: return@withContext RouteResult.EMPTY
            val json = JSONObject(body)

            val code = json.optString("code", "")
            if (code != "Ok") {
                Log.e("OSRMRouting", "OSRM returned code=$code message=${json.optString("message")}")
                return@withContext RouteResult.EMPTY
            }

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                Log.e("OSRMRouting", "No routes in response")
                return@withContext RouteResult.EMPTY
            }

            val route = routes.getJSONObject(0)
            val coordsArr = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coordsArr.length()).map { i ->
                val c = coordsArr.getJSONArray(i)
                c.getDouble(1) to c.getDouble(0) // (lat, lng) — GeoJSON stores [lng, lat]
            }

            val totalDistance = route.optDouble("distance", 0.0)
            val totalDuration = route.optDouble("duration", 0.0)

            val steps = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null) {
                for (i in 0 until legs.length()) {
                    val stepsArr = legs.getJSONObject(i).optJSONArray("steps") ?: continue
                    for (j in 0 until stepsArr.length()) {
                        val st = stepsArr.getJSONObject(j)
                        val maneuver = st.optJSONObject("maneuver") ?: continue
                        val location = maneuver.optJSONArray("location") ?: continue
                        val targetLng = location.optDouble(0)
                        val targetLat = location.optDouble(1)
                        val streetName = st.optString("name", "").ifBlank { "the road" }
                        val type = maneuver.optString("type", "continue")
                        val modifier = maneuver.optString("modifier", "")
                        steps.add(
                            RouteStep(
                                instruction = buildInstruction(type, modifier, streetName),
                                distanceMeters = st.optDouble("distance", 0.0),
                                targetLat = targetLat,
                                targetLng = targetLng
                            )
                        )
                    }
                }
            }

            // OSRM reports how far each input coordinate was from the road it
            // snapped to in the top-level "waypoints" array — this is how we
            // detect "you're on the wrong side of the river" rather than
            // guessing from geometry.
            val waypoints = json.optJSONArray("waypoints")
            val startSnap = waypoints?.optJSONObject(0)?.optDouble("distance", 0.0) ?: 0.0
            val endSnap = waypoints?.optJSONObject(waypoints.length() - 1)?.optDouble("distance", 0.0) ?: 0.0

            RouteResult(
                points, steps, totalDistance, totalDuration,
                startSnapDistanceMeters = startSnap,
                endSnapDistanceMeters = endSnap
            )
        }
    } catch (e: Exception) {
        Log.e("OSRMRouting", "Route fetch exception: ${e.message}")
        RouteResult.EMPTY
    }
}

/** Turns OSRM's maneuver type/modifier codes into a plain-language instruction. */
private fun buildInstruction(type: String, modifier: String, streetName: String): String {
    val mod = modifier.replace("_", " ")
    return when (type) {
        "depart" -> "Head out toward $streetName"
        "arrive" -> "Arrive at destination"
        "turn" -> "Turn $mod onto $streetName"
        "new name" -> "Continue onto $streetName"
        "continue" -> if (mod.isNotBlank()) "Continue $mod onto $streetName" else "Continue onto $streetName"
        "merge" -> "Merge onto $streetName"
        "on ramp" -> "Take the ramp onto $streetName"
        "off ramp" -> "Take the exit onto $streetName"
        "fork" -> "Keep $mod at the fork onto $streetName"
        "end of road" -> "Turn $mod onto $streetName"
        "roundabout", "rotary" -> "Enter the roundabout, then exit onto $streetName"
        "roundabout turn" -> "At the roundabout, turn $mod onto $streetName"
        else -> "Continue onto $streetName"
    }
}

/** Ensures the drawn polyline always physically touches the live position and destination. */
private fun connectToEndpoints(
    routePoints: List<Pair<Double, Double>>,
    start: Pair<Double, Double>,
    end: Pair<Double, Double>,
    maxConnectorMeters: Double
): List<Pair<Double, Double>> {
    if (routePoints.isEmpty()) return listOf(start, end)
    val result = routePoints.toMutableList()
    // Below this we treat the gap as GPS noise / a short driveway and it's
    // safe to just draw a straight line to close it. Above it, and up to
    // maxConnectorMeters, we still connect (better than a dangling line) but
    // the UI shows an off-road warning. Beyond maxConnectorMeters we don't
    // connect at all — see the caller's comment for why.
    val gapThresholdMeters = 15.0

    val first = result.first()
    val startGap = haversineDistanceMeters(start.first, start.second, first.first, first.second)
    if (startGap > gapThresholdMeters && startGap <= maxConnectorMeters) {
        result.add(0, start)
    }
    val last = result.last()
    val endGap = haversineDistanceMeters(end.first, end.second, last.first, last.second)
    if (endGap > gapThresholdMeters && endGap <= maxConnectorMeters) {
        result.add(end)
    }
    return result
}

private fun nearestPointIndex(points: List<Pair<Double, Double>>, lat: Double, lng: Double): Int {
    var bestIdx = 0
    var bestDist = Double.MAX_VALUE
    points.forEachIndexed { idx, (pLat, pLng) ->
        val d = haversineDistanceMeters(lat, lng, pLat, pLng)
        if (d < bestDist) {
            bestDist = d
            bestIdx = idx
        }
    }
    return bestIdx
}

private fun haversineDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.toInt()} m"

private fun formatDuration(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    return if (mins >= 60) "${mins / 60} hr ${mins % 60} min" else "$mins min"
}

private fun updateRouteLine(style: Style, points: List<Pair<Double, Double>>) {
    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return
    if (points.size < 2) {
        source.setGeoJson(LineString.fromLngLats(emptyList()))
        return
    }
    val lineString = LineString.fromLngLats(points.map { (lat, lng) -> Point.fromLngLat(lng, lat) })
    source.setGeoJson(lineString)
}

private fun updateCurrentPositionMarker(style: Style, lat: Double, lng: Double, bearing: Float) {
    val source = style.getSourceAs<GeoJsonSource>(CURRENT_POS_SOURCE_ID) ?: return
    val properties = JsonObject().apply { addProperty("bearing", bearing) }
    source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lng, lat), properties))
}

private fun updateDestinationMarker(style: Style, lat: Double, lng: Double) {
    val source = style.getSourceAs<GeoJsonSource>(DEST_SOURCE_ID) ?: return
    source.setGeoJson(Point.fromLngLat(lng, lat))
}

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val CURRENT_POS_SOURCE_ID = "current-pos-source"
private const val DEST_SOURCE_ID = "dest-source"
private const val CURRENT_POS_LAYER_ID = "current-pos-layer"
private const val CURRENT_POS_HALO_LAYER_ID = "current-pos-halo-layer"
private const val DEST_LAYER_ID = "dest-layer"
private const val ARROW_ICON_ID = "current-pos-arrow-icon"