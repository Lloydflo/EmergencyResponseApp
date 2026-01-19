package com.ers.emergencyresponseapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ers.emergencyresponseapp.analytics.DefaultAnalyticsLogger
import com.ers.emergencyresponseapp.config.CallConfig
import com.ers.emergencyresponseapp.config.AgencyInfo
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.home.ResponderStatus
import com.ers.emergencyresponseapp.home.composables.DepartmentSelectionDialog
import com.ers.emergencyresponseapp.home.composables.IncidentTypeSummary
import com.ers.emergencyresponseapp.home.composables.QuickActionButtons
import com.ers.emergencyresponseapp.home.composables.ResponderStatusIndicator
import com.ers.emergencyresponseapp.home.composables.ActiveIncidents
import androidx.compose.ui.tooling.preview.Preview
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.util.Date
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import com.ers.emergencyresponseapp.data.DefaultIncidentRepository
import kotlinx.coroutines.launch

// --- Simple types used for incoming emergency requests (local, UI-only) ---
private enum class ResponderOnlineStatus { Online, Offline }
private enum class EmergencyPriority(val title: String, val color: Color) {
    High("High", Color(0xFFD32F2F)),
    Medium("Medium", Color(0xFFFFA000)),
    Low("Low", Color(0xFF388E3C))
}
private data class EmergencyRequest(
    val id: Int,
    val type: String,
    val distance: String,
    val timestamp: String,
    val priority: EmergencyPriority,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null, // human-friendly address if available
    val description: String? = null,
    val status: String = "Reported"
)

// --- Incident -> Agency mapping (reusable, production-safe) ---
// Use AgencyInfo from CallConfig; old IncidentAgency removed

private fun mapIncidentTypeToAgency(typeRaw: String?): AgencyInfo {
    // Delegate to centralized CallConfig for hotline numbers and agency info
    return CallConfig.agencyForKey(typeRaw)
}

// --- Small reusable UI pieces (copied/merged from RespondersScreen, UI-only) ---
@Composable
private fun ResponderAvatar(
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    drawableRes: Int? = null,
    status: ResponderOnlineStatus = ResponderOnlineStatus.Offline,
    contentDescription: String = "Responder avatar"
) {
    val context = LocalContext.current

    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.Center) {
        when {
            drawableRes != null -> {
                val painter = painterResource(id = drawableRes)
                Image(painter = painter, contentDescription = contentDescription, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
            imageUri != null -> {
                val bitmap = remember(imageUri) {
                    try {
                        val uri = imageUri.toUri()
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) { null }
                }
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
                }
            }
            else -> Icon(imageVector = Icons.Default.AccountCircle, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
        }

        val dotColor = if (status == ResponderOnlineStatus.Online) Color(0xFF98EE9C) else Color.Gray
        Box(
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(width = 2.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
        )
    }
}

@Composable
private fun MapWithMarkers(
    modifier: Modifier = Modifier,
    responderLat: Double?,
    responderLng: Double?,
    requests: List<EmergencyRequest> = emptyList(),
    assignedId: Int? = null
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.LightGray)) {
                // If no coordinates available show placeholder
                val anyCoords = responderLat != null || requests.any { it.latitude != null && it.longitude != null }
                if (!anyCoords) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location Pin", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Text(text = "Live Responder Location (Preview)", modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    // Build lists of coordinates available
                    val latList = mutableListOf<Double>()
                    val lngList = mutableListOf<Double>()
                    responderLat?.let { latList.add(it) }
                    responderLng?.let { lngList.add(it) }
                    for (r in requests) {
                        r.latitude?.let { latList.add(it) }
                        r.longitude?.let { lngList.add(it) }
                    }

                    val minLat = latList.minOrNull() ?: 0.0
                    val maxLat = latList.maxOrNull() ?: minLat
                    val minLng = lngList.minOrNull() ?: 0.0
                    val maxLng = lngList.maxOrNull() ?: minLng

                    val latSpan = (maxLat - minLat).takeIf { it > 0.0 } ?: 0.01
                    val lngSpan = (maxLng - minLng).takeIf { it > 0.0 } ?: 0.01

                    fun latToFraction(lat: Double) = 1f - (((lat - minLat) / latSpan).toFloat().coerceIn(0f, 1f))
                    fun lngToFraction(lng: Double) = (((lng - minLng) / lngSpan).toFloat().coerceIn(0f, 1f))

                    // Draw markers using Canvas so we don't rely on Modifier.offset (avoids import/version issues)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Draw each incident as a circle marker
                        for (req in requests) {
                            val lat = req.latitude
                            val lng = req.longitude
                            if (lat != null && lng != null) {
                                val x = lngToFraction(lng) * canvasW
                                val y = latToFraction(lat) * canvasH
                                val isAssigned = assignedId != null && req.id == assignedId
                                val radius = if (isAssigned) 12.dp.toPx() else 9.dp.toPx()
                                drawCircle(color = if (isAssigned) Color.Red else Color(0xFFFFA000), radius = radius, center = Offset(x, y))
                            }
                        }

                        // Draw responder marker on top
                        responderLat?.let { rLat ->
                            responderLng?.let { rLng ->
                                val x = lngToFraction(rLng) * canvasW
                                val y = latToFraction(rLat) * canvasH
                                val radius = 10.dp.toPx()
                                drawCircle(color = Color.Blue, radius = radius, center = Offset(x, y))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Basic responder identity & avatar
    var responderImageUri by remember { mutableStateOf<String?>(null) }
    var responderDrawableRes by remember { mutableStateOf<Int?>(null) }
    var responderName by remember { mutableStateOf<String?>("Alex Johnson") }

    // Online/offline and availability
    var onlineStatus by remember { mutableStateOf(ResponderOnlineStatus.Online) }
    var responderAvailable by remember { mutableStateOf(true) }
    // Responder status shown in the UI (Available -> On Duty -> In Scene)
    var responderStatus by remember { mutableStateOf(ResponderStatus.AVAILABLE) }

    // Incoming requests and assigned incident
    val incomingRequests = remember { mutableStateListOf<EmergencyRequest>() }
    var assignedIncident by remember { mutableStateOf<EmergencyRequest?>(null) }

    // Dialog state for call confirmation
    var showDepartmentSelection by remember { mutableStateOf(false) }

    // Mark-complete confirmation state
    var showMarkCompleteDialog by remember { mutableStateOf(false) }
    var markTargetIncident by remember { mutableStateOf<EmergencyRequest?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Existing incidents list (kept for the IncidentTypeSummary and ActiveIncidents composables)
    var incidentsList by remember { mutableStateOf(listOf<Incident>()) }
    var activeIncidents by remember { mutableStateOf(listOf<Incident>()) }

    // Helper functions for incidents (small mocked data)
    fun loadIncidents() {
        val mockIncidents = listOf(
            Incident("1", IncidentType.FIRE, IncidentPriority.HIGH, "123 Main St", Date(), IncidentStatus.REPORTED, "Building fire"),
            Incident("2", IncidentType.MEDICAL, IncidentPriority.MEDIUM, "456 Oak Ave", Date(), IncidentStatus.DISPATCHED, "Heart attack"),
            Incident("3", IncidentType.CRIME, IncidentPriority.LOW, "789 Pine Ln", Date(), IncidentStatus.ON_SCENE, "Shoplifting")
        )
        incidentsList = mockIncidents
        Log.d("HomeScreen", "Loaded ${mockIncidents.size} mock incidents")
    }

    fun calculateCounts() {
        // simple counts for logging (kept lightweight)
        var f = 0; var m = 0; var c = 0; var d = 0
        for (inc in incidentsList) {
            when (inc.type) {
                IncidentType.FIRE -> f++
                IncidentType.MEDICAL -> m++
                IncidentType.CRIME -> c++
                IncidentType.DISASTER -> d++
            }
        }
        Log.d("HomeScreen", "Counts updated - Fire:$f Medical:$m Crime:$c Disaster:$d")
    }

    fun filterActiveIncidents(list: List<Incident>) = list.filter { it.status != IncidentStatus.RESOLVED }
    fun refreshActiveIncidents() { activeIncidents = filterActiveIncidents(incidentsList) }

    // Nav to incident using Google Maps (UI-only, safe)
    fun navigateToLocation(lat: Double?, lng: Double?, address: String?) {
        // Prefer coordinates if available (more accurate), otherwise fall back to address query
        try {
            if (lat != null && lng != null) {
                // Use Google Maps navigation to coordinates
                val gmmIntentUri = ("google.navigation:q=$lat,$lng").toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                val resolveInfo = context.packageManager.resolveActivity(mapIntent, 0)
                if (resolveInfo != null) {
                    context.startActivity(mapIntent)
                    return
                } else {
                    // Fallback to geo URI with label
                    val geoUri = ("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address ?: "Incident")})").toUri()
                    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
                    val fallbackResolve = context.packageManager.resolveActivity(geoIntent, 0)
                    if (fallbackResolve != null) { context.startActivity(geoIntent); return }
                }
            }

            // If coordinates not available or failed, fall back to address-based navigation
            if (!address.isNullOrBlank()) {
                val gmmIntentUri = ("google.navigation:q=${Uri.encode(address)}").toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                val resolveInfo = context.packageManager.resolveActivity(mapIntent, 0)
                if (resolveInfo != null) { context.startActivity(mapIntent); return } else {
                    val geoIntent = Intent(Intent.ACTION_VIEW, ("geo:0,0?q=${Uri.encode(address)}").toUri())
                    val fallbackResolve = context.packageManager.resolveActivity(geoIntent, 0)
                    if (fallbackResolve != null) { context.startActivity(geoIntent); return }
                }
            }

            // Nothing available
            Toast.makeText(context, "No location available for navigation", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show()
            Log.d("HomeScreen", "Error launching navigation: ${e.message}")
        }
    }

    // Assignment handlers (UI-only placeholders)
    val acceptHandler: (EmergencyRequest) -> Unit = { req -> acceptIncident(incomingRequests, req) { assigned -> assignedIncident = assigned; responderAvailable = false; onlineStatus = ResponderOnlineStatus.Offline; Log.i("Assignment", "Assigned incident ${assigned.id}") } }
    val releaseHandler = { assignedIncident = null; responderAvailable = true; onlineStatus = ResponderOnlineStatus.Online }

    // Demo feeder and auto-assignment
    LaunchedEffect(Unit) { demoFeedIncomingRequests(incomingRequests) }
    LaunchedEffect(incomingRequests.size) {
        val snapshot = incomingRequests.toMutableList()
        snapshot.evaluateAndAssignIfAvailable({ responderAvailable }, acceptHandler)
    }

    // Load incidents on compose
    LaunchedEffect(Unit) { loadIncidents() }
    LaunchedEffect(incidentsList) { calculateCounts(); refreshActiveIncidents() }

    // Location sharing
    var isLocationShared by remember { mutableStateOf(false) }
    var currentLatitude by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(10000) // 10 seconds
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentLatitude = null
        currentLongitude = null
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(), onResult = { isGranted: Boolean ->
        if (isGranted) {
            isLocationShared = true
            startLocationUpdates()
        } else {
            isLocationShared = false
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    })

    val handleShareLocationToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                isLocationShared = true
                startLocationUpdates()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            isLocationShared = false
            stopLocationUpdates()
        }
    }

    Scaffold(topBar = { ResponderTopAppBar(status = onlineStatus, imageUri = responderImageUri, drawableRes = responderDrawableRes, responderName = responderName) }) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item { ResponderStatusIndicator(status = responderStatus) }

            item {
                QuickActionButtons(
                    isLocationShared = isLocationShared,
                    onShareLocationToggle = handleShareLocationToggle,
                    onCallCommand = { showDepartmentSelection = true }
                )
            }

            item {
                if (showDepartmentSelection) {
                    DepartmentSelectionDialog(onDismiss = { showDepartmentSelection = false })
                }
            }

            // Assigned incident card
            item {
                if (assignedIncident != null) {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header: "Assigned Incident"        ID • Distance   [High/Medium/Low]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Assigned Incident", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(text = "ID ${assignedIncident!!.id} • ${assignedIncident!!.distance}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                // Priority badge with border
                                Box(
                                    modifier = Modifier
                                        .border(2.dp, assignedIncident!!.priority.color, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when {
                                            assignedIncident!!.priority == EmergencyPriority.High -> "High"
                                            assignedIncident!!.priority == EmergencyPriority.Medium -> "Medium"
                                            else -> "Low"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = assignedIncident!!.priority.color
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Status: [status]     Timestamp
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFA000), CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Status: ${assignedIncident!!.status}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFA000))
                                }
                                Text(assignedIncident!!.timestamp, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 📍 [Address]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = assignedIncident!!.address ?: "Location not specified",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                            }

                            // [Description]
                            assignedIncident!!.description?.let { desc ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray, maxLines = 2)
                            }

                            // Type: Fire
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Type: ${assignedIncident!!.type}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                            Spacer(modifier = Modifier.height(12.dp))

                            // [Navigate] [Mark Complete]
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { navigateToLocation(assignedIncident!!.latitude, assignedIncident!!.longitude, assignedIncident!!.address) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Navigate", fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { markTargetIncident = assignedIncident; showMarkCompleteDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                                    modifier = Modifier.weight(1.1f).height(44.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Mark Complete", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { IncidentTypeSummary(incidents = incidentsList) }

            item {
                MapWithMarkers(responderLat = currentLatitude, responderLng = currentLongitude, requests = incomingRequests.toList(), assignedId = assignedIncident?.id)
            }

            item {
                val activeCount = if (responderAvailable && assignedIncident == null) 1 else 0
                val ongoingCount = incomingRequests.size
                val responseTimeAvg = "${(5..8).random()}.0m"
                QuickStatusCards(activeCount = activeCount, ongoingCount = ongoingCount, responseTimeAvg = responseTimeAvg)
            }

            item { ActiveIncidents(incidents = activeIncidents) }

            item { EmergencyRequestList(requests = incomingRequests) }

            item {
                ActionButtons(
                    currentStatus = responderStatus,
                    onStatusChange = { newStatus ->
                        responderStatus = newStatus
                        when (newStatus) {
                            ResponderStatus.AVAILABLE -> {
                                responderAvailable = true
                            }
                            ResponderStatus.ON_DUTY -> {
                                responderAvailable = true
                            }
                            ResponderStatus.BUSY -> {
                                responderAvailable = false
                            }
                        }
                    },
                    onViewAssignedCases = { /* TODO: navigate to assigned cases screen */ },
                    onOpenCoordinationPortal = { /* TODO: open coordination portal (map/chat) */ }
                )
            }

            item {
                if (showMarkCompleteDialog && markTargetIncident != null) {
                    AlertDialog(
                        onDismissRequest = { showMarkCompleteDialog = false; markTargetIncident = null },
                        title = { Text("Mark Incident Complete?", fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to mark this incident as resolved?") },
                        confirmButton = {
                            Button(onClick = {
                                val target = markTargetIncident
                                showMarkCompleteDialog = false
                                markTargetIncident = null
                                if (target != null) {
                                    coroutineScope.launch {
                                        val success = try {
                                            DefaultAnalyticsLogger.logEvent("mark_complete_attempt", mapOf("incident_id" to target.id.toString(), "incident_type" to target.type))
                                            DefaultIncidentRepository.updateIncidentStatus(target.id, "Resolved")
                                        } catch (e: Exception) { Log.d("HomeScreen", "Mark update failed: ${e.message}"); false }

                                        if (success) {
                                            releaseAssignment(releaseHandler)
                                            DefaultAnalyticsLogger.logEvent("mark_complete_success", mapOf("incident_id" to target.id.toString()))
                                            Toast.makeText(context, "Incident marked as complete!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            DefaultAnalyticsLogger.logEvent("mark_complete_failure", mapOf("incident_id" to target.id.toString()))
                                            Toast.makeText(context, "Failed to mark incident as complete.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }) { Text("Yes") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showMarkCompleteDialog = false; markTargetIncident = null }) { Text("No") }
                        }
                    )
                }
            }

            // Bottom spacer to give breathing room when scrolled to the end
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponderTopAppBar(status: ResponderOnlineStatus, imageUri: String? = null, drawableRes: Int? = null, responderName: String? = null) {
    TopAppBar(
        title = { Text(responderName ?: "Responder Dashboard", fontWeight = FontWeight.Bold) },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = status.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                // small avatar - use the reusable ResponderAvatar so the function isn't unused
                Box(modifier = Modifier.size(36.dp)) {
                    ResponderAvatar(modifier = Modifier.fillMaxSize(), imageUri = imageUri, drawableRes = drawableRes, status = status)
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.tertiary)
    )
}

@Composable
private fun EmergencyRequestCard(request: EmergencyRequest) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, contentDescription = request.type, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = request.type, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Badge(containerColor = request.priority.color) { Text(request.priority.name, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "${request.distance} • ${request.timestamp}")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { /* view */ }, modifier = Modifier.height(36.dp)) { Text("View") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { /* accept */ }, modifier = Modifier.height(36.dp)) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun EmergencyRequestList(requests: List<EmergencyRequest>) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(text = "Incoming Emergency Requests", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp).heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(requests) { req -> EmergencyRequestCard(req) }
        }
    }
}

@Composable
private fun QuickStatusCards(activeCount: Int, ongoingCount: Int, responseTimeAvg: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.weight(1f)) { Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(activeCount.toString(), fontWeight = FontWeight.Bold); Text("Active") } }
        Card(modifier = Modifier.weight(1f)) { Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(ongoingCount.toString(), fontWeight = FontWeight.Bold); Text("Ongoing") } }
        Card(modifier = Modifier.weight(1f)) { Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(responseTimeAvg, fontWeight = FontWeight.Bold); Text("Avg RT") } }
    }
}

@Composable
fun ActionButtons(
    modifier: Modifier = Modifier,
    currentStatus: ResponderStatus = ResponderStatus.AVAILABLE,
    onStatusChange: (ResponderStatus) -> Unit = {},
    onViewAssignedCases: () -> Unit = {},
    onOpenCoordinationPortal: () -> Unit = {}
) {
    // Use enum-provided displayName and color (keeps UI in sync with the model)
    val statusLabel = currentStatus.displayName
    val statusColor = currentStatus.color

    // Helper to compute the next status in the cycle (exhaustive)
    fun nextStatus(s: ResponderStatus) = when (s) {
        ResponderStatus.AVAILABLE -> ResponderStatus.ON_DUTY
        ResponderStatus.ON_DUTY -> ResponderStatus.BUSY
        ResponderStatus.BUSY -> ResponderStatus.AVAILABLE
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The main status button shows the current status and cycles on click
        Button(
            onClick = { onStatusChange(nextStatus(currentStatus)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = statusColor)
        ) {
            Text(text = statusLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        // Secondary actions
        OutlinedButton(onClick = onViewAssignedCases, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text("View Assigned Cases")
        }

        OutlinedButton(onClick = onOpenCoordinationPortal, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text("Open Coordination Portal")
        }
    }
}

// --- Helpers (restored) ---

private fun MutableList<EmergencyRequest>.evaluateAndAssignIfAvailable(responderAvailable: () -> Boolean, accept: (EmergencyRequest) -> Unit) {
    if (!responderAvailable()) return
    val order = listOf(EmergencyPriority.High, EmergencyPriority.Medium, EmergencyPriority.Low)
    for (p in order) {
        val candidate = this.firstOrNull { it.priority == p }
        if (candidate != null) { accept(candidate); return }
    }
}

private fun acceptIncident(incoming: MutableList<EmergencyRequest>, incident: EmergencyRequest, onAssigned: (EmergencyRequest) -> Unit) {
    incoming.removeAll { it.id == incident.id }
    val assigned = incident.copy(status = "Assigned")
    onAssigned(assigned)
}

private fun releaseAssignment(onReleased: () -> Unit) { onReleased() }

private suspend fun demoFeedIncomingRequests(incoming: MutableList<EmergencyRequest>) {
    // lightweight demo feed to populate UI preview (includes address + description)
    delay(600L)
    incoming.add(EmergencyRequest(101, "Medical", "2.0 km", "Just now", EmergencyPriority.Medium, latitude = 37.4219, longitude = -122.0839, address = "1600 Amphitheatre Pkwy, Mountain View, CA", description = "Suspected cardiac arrest"))
    delay(800L)
    incoming.add(EmergencyRequest(102, "Fire", "4.2 km", "Just now", EmergencyPriority.High, latitude = 37.4245, longitude = -122.0860, address = "200 Castro St, Mountain View, CA", description = "Two-story building fire"))
    delay(1100L)
    incoming.add(EmergencyRequest(103, "Crime", "1.1 km", "Just now", EmergencyPriority.Low, latitude = 37.4190, longitude = -122.0820, address = "50 N Rengstorff Ave, Mountain View, CA", description = "Reported shoplifting"))
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    EmergencyResponseAppTheme {
        // Preview only the DepartmentSelectionDialog to avoid runtime-only APIs in the full HomeScreen preview
        DepartmentSelectionDialog(onDismiss = {})
    }
}

@Preview(name = "HomeScreen - UI Preview", showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenUiPreview() {
    EmergencyResponseAppTheme {
        // Sample data (UI-only) to render the major HomeScreen pieces without runtime APIs
        val sampleAssigned = EmergencyRequest(201, "Fire", "1.2 km", "2m ago", EmergencyPriority.High, latitude = null, longitude = null, address = "123 Sample St", description = "Building fire")
        val sampleIncoming = listOf(
            EmergencyRequest(202, "Medical", "3.4 km", "5m ago", EmergencyPriority.Medium, latitude = null, longitude = null, address = "456 Example Ave", description = "Unconscious person"),
            EmergencyRequest(203, "Crime", "0.8 km", "1m ago", EmergencyPriority.Low, latitude = null, longitude = null, address = "789 Test Blvd", description = "Theft reported")
        )

        val sampleIncidents = listOf(
            Incident("1", IncidentType.FIRE, IncidentPriority.HIGH, "123 Main St", Date(), IncidentStatus.REPORTED, "Building fire"),
            Incident("2", IncidentType.MEDICAL, IncidentPriority.MEDIUM, "456 Oak Ave", Date(), IncidentStatus.DISPATCHED, "Heart attack")
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResponderTopAppBar(status = ResponderOnlineStatus.Online, responderName = "Alex Johnson")
            Spacer(modifier = Modifier.height(8.dp))
            ResponderStatusIndicator(status = ResponderStatus.AVAILABLE)

            QuickActionButtons(
                isLocationShared = false,
                onShareLocationToggle = {},
                onCallCommand = {}
            )

            // Assigned card preview
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Assigned Incident", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = "ID ${sampleAssigned.id} • ${sampleAssigned.distance}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Box(
                            modifier = Modifier
                                .border(2.dp, sampleAssigned.priority.color, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when {
                                    sampleAssigned.priority == EmergencyPriority.High -> "High"
                                    sampleAssigned.priority == EmergencyPriority.Medium -> "Medium"
                                    else -> "Low"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = sampleAssigned.priority.color
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFA000), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Status: ${sampleAssigned.status}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFA000))
                        }
                        Text(sampleAssigned.timestamp, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = sampleAssigned.address ?: "Location not specified", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    sampleAssigned.description?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray, maxLines = 2)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Type: ${sampleAssigned.type}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { }, modifier = Modifier.weight(1f).height(44.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", fontSize = 13.sp)
                        }
                        Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White), modifier = Modifier.weight(1.1f).height(44.dp), shape = RoundedCornerShape(10.dp)) {
                            Text("Mark Complete", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            IncidentTypeSummary(incidents = sampleIncidents)

            MapWithMarkers(responderLat = null, responderLng = null, requests = sampleIncoming, assignedId = sampleAssigned.id)

            val activeCount = 2
            val ongoingCount = sampleIncoming.size
            val responseTimeAvg = "6.4m"
            QuickStatusCards(activeCount = activeCount, ongoingCount = ongoingCount, responseTimeAvg = responseTimeAvg)

            ActiveIncidents(incidents = sampleIncidents)
            EmergencyRequestList(requests = sampleIncoming)

            ActionButtons(onStatusChange = {}, onViewAssignedCases = {}, onOpenCoordinationPortal = {})

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
