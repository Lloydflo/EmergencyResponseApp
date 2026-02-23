package com.ers.emergencyresponseapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.location.Location
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.analytics.RouteHistoryStore
import com.ers.emergencyresponseapp.home.composables.DepartmentSelectionDialog
import com.ers.emergencyresponseapp.home.IncidentStore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun isDeviceLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

// --- Simple types used for incoming emergency requests (local, UI-only) ---
private enum class ResponderOnlineStatus { Online, Offline }
private enum class EmergencyPriority(val color: Color) {
    High(Color(0xFFD32F2F)),
    Medium(Color(0xFFFFA000)),
    Low(Color(0xFF388E3C))
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

private fun saveUriToAppStorage(ctx: android.content.Context, uri: Uri): String? {
    return try {
        val dir = File(ctx.filesDir, "profile_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "profile_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        Log.e("HomeScreen", "Failed to persist profile photo: ${e.message}")
        null
    }
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
                Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            imageUri != null -> {
                val bitmap = remember(imageUri) {
                    try {
                        if (imageUri.startsWith("file://")) {
                            val path = Uri.parse(imageUri).path
                            if (path != null) BitmapFactory.decodeFile(path) else null
                        } else {
                            val uri = imageUri.toUri()
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                    } catch (_: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
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

// --- Helper functions for HomeScreen (must be defined before use) ---
private fun MutableList<EmergencyRequest>.evaluateAndAssignIfAvailable(
    responderAvailable: () -> Boolean,
    accept: (EmergencyRequest) -> Unit
) {
    if (!responderAvailable()) return
    val order = listOf(EmergencyPriority.High, EmergencyPriority.Medium, EmergencyPriority.Low)
    for (p in order) {
        val candidate = this.firstOrNull { it.priority == p }
        if (candidate != null) {
            accept(candidate)
            return
        }
    }
}

private fun acceptIncident(
    incoming: MutableList<EmergencyRequest>,
    incident: EmergencyRequest,
    onAssigned: (EmergencyRequest) -> Unit
) {
    incoming.removeAll { it.id == incident.id }
    val assigned = incident.copy(status = "Assigned")
    onAssigned(assigned)
}

private fun demoFeedIncomingRequests(list: MutableList<EmergencyRequest>) {
    val types = listOf("Medical", "Fire", "Crime", "Disaster")
    val priorities = EmergencyPriority.entries
    val random = java.util.Random()

    fun randomInt(range: IntRange): Int = range.random()

    while (list.size < 6) {
        val id = list.size + 1
        val type = types[random.nextInt(types.size)]
        val priority = priorities[random.nextInt(priorities.size)]
        val distance = "${randomInt(1..20)} km"
        val timestamp = "${randomInt(1..12)}:${randomInt(0..59).toString().padStart(2, '0')} ${if (random.nextBoolean()) "AM" else "PM"}"
        val lat = random.nextDouble() * 180.0 - 90.0
        val lng = random.nextDouble() * 360.0 - 180.0
        val address = "Random ${type} ${id} St"

        list.add(EmergencyRequest(id, type, distance, timestamp, priority, lat, lng, address, "Description for ${type.lowercase()} incident #$id"))
    }
}

private fun openMapPin(context: android.content.Context, lat: Double?, lng: Double?, address: String?) {
    try {
        val primaryUri = when {
            !address.isNullOrBlank() -> ("geo:0,0?q=${Uri.encode(address)}").toUri()
            lat != null && lng != null -> ("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address ?: "Incident")})").toUri()
            else -> null
        }

        if (primaryUri != null) {
            val mapIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply { setPackage("com.google.android.apps.maps") }
            val resolveInfo = context.packageManager.resolveActivity(mapIntent, 0)
            if (resolveInfo != null) { context.startActivity(mapIntent); return }

            val fallbackIntent = Intent(Intent.ACTION_VIEW, primaryUri)
            val fallbackResolve = context.packageManager.resolveActivity(fallbackIntent, 0)
            if (fallbackResolve != null) { context.startActivity(fallbackIntent); return }
        }

        Toast.makeText(context, "No map application available to view location", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.d("HomeScreen", "Error opening map pin: ${e.message}")
        Toast.makeText(context, "Unable to open map", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyRequestCard(request: EmergencyRequest, showBackupBadge: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, contentDescription = request.type, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = request.type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                if (showBackupBadge) {
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(text = "Backup", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Badge(containerColor = request.priority.color) { Text(request.priority.name, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "${request.distance} • ${request.timestamp}", color = MaterialTheme.colorScheme.onSurface)
            if (showBackupBadge) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = request.address ?: "Address unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val ctx = LocalContext.current
                OutlinedButton(onClick = {
                    openMapPin(ctx, request.latitude, request.longitude, request.address)
                }, modifier = Modifier.height(36.dp)) { Text("View") }

                if (!showBackupBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { /* accept */ }, modifier = Modifier.height(36.dp)) { Text("Accept") }
                }
            }
        }
    }
}

@Composable
private fun EmergencyRequestList(requests: List<EmergencyRequest>, title: String = "Incoming Emergency Requests", showBackupBadge: Boolean = false) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp).heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(requests) { req -> EmergencyRequestCard(req, showBackupBadge = showBackupBadge) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(responderRole: String? = null) {
    val context = LocalContext.current
    var isLocationMonitoringEnabled by remember { mutableStateOf(false) }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // kapag bumalik from settings, i-check ulit
        isLocationMonitoringEnabled = isDeviceLocationEnabled(context)
    }
    // Prefer the stored registration department as the authoritative responder role so
    // the Home screen always filters to the user's department even if navigation route lacks it.
    val storedPrefs = context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
    val storedDepartment = storedPrefs.getString("department", null)
    val effectiveRole = storedDepartment?.lowercase() ?: responderRole?.takeIf { it.isNotBlank() }

    // Persist responder/account fields in SharedPreferences so they survive app restarts
    val prefs = context.getSharedPreferences("ers_prefs", android.content.Context.MODE_PRIVATE)
    val locationPermissionRequestedKey = "location_permission_requested"
    val locationMonitoringEnabledKey = "location_monitoring_enabled"

    // Account settings state
    var accountFullName by remember { mutableStateOf(prefs.getString("account_full_name", "") ?: "") }
    var accountUsername by remember { mutableStateOf(prefs.getString("account_username", "") ?: "") }
    var accountEmail by remember { mutableStateOf(prefs.getString("account_email", "") ?: "") }
    var accountPhotoUri by remember { mutableStateOf(prefs.getString("account_photo", null)) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    val pickProfilePhotoLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val storedUri = saveUriToAppStorage(context, uri) ?: uri.toString()
            accountPhotoUri = storedUri
            // Persist immediately so the avatar survives app background/restore.
            try { prefs.edit().putString("account_photo", accountPhotoUri).apply() } catch (_: Exception) { /* ignore */ }
        }
    }

    // Basic responder identity & avatar
    val responderImageUri = accountPhotoUri
    val responderDrawableRes by remember { mutableStateOf<Int?>(null) }
    // Show the account username (from settings) as the displayed responder name.
    var responderName by remember { mutableStateOf(prefs.getString("account_username", prefs.getString("responder_name", "Name") ?: "Name") ?: "Name") }

    // Keep responderName in sync if the account username is changed in settings during runtime
    LaunchedEffect(accountUsername) {
        if (!accountUsername.isNullOrBlank()) {
            responderName = accountUsername
        }
    }

    // Online/offline and availability
    var onlineStatus by remember { mutableStateOf(ResponderOnlineStatus.Online) }
    var responderAvailable by remember { mutableStateOf(true) }
    // Incoming requests and assigned incident
    val incomingRequests = remember { mutableStateListOf<EmergencyRequest>() }

    // New incident notification state
    var showNewIncidentNotification by remember { mutableStateOf(false) }
    var newIncidentMessage by remember { mutableStateOf("") }
    var showAssignedAfterNotification by remember { mutableStateOf(true) }
    var lastNotifiedIncidentId by remember {
        mutableStateOf(prefs.getString("last_notified_incident_id", null))
    }
    var lastAssignedIncidentId by remember {
        mutableStateOf(prefs.getString("last_assigned_incident_id", null))
    }
    var navDestinationIncidentId by remember { mutableStateOf<String?>(null) }
    var navDestinationLat by remember { mutableStateOf<Double?>(null) }
    var navDestinationLng by remember { mutableStateOf<Double?>(null) }
    val onSceneEnabledMap = remember { mutableStateMapOf<String, Boolean>() }

    // Derive the visible incoming requests based on effectiveRole (nav role or stored department)
    val visibleIncomingRequests = remember(incomingRequests, effectiveRole) {
        if (effectiveRole.isNullOrBlank()) incomingRequests.toList()
        else incomingRequests.filter { it.type.equals(effectiveRole, ignoreCase = true) }
    }

    // Dialog state for call confirmation
    var showDepartmentSelection by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }


    // Mark-complete / proof form state (UI only)
    var showMarkCompleteDialog by remember { mutableStateOf(false) }
    // The assigned incident model type used in the Assigned/Active lists is `Incident` (from home package)
    var markTargetIncidentInc by remember { mutableStateOf<Incident?>(null) }
    var proofNotes by remember { mutableStateOf("") }
    var selectedProofUri by remember { mutableStateOf<String?>(null) }

    // Save a Bitmap to a cache file and return a file:// URI string
    fun saveBitmapToCache(bitmap: Bitmap, ctx: android.content.Context): String? {
        return try {
            val cacheDir = ctx.cacheDir
            val file = File(cacheDir, "proof_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to save bitmap: ${e.message}")
            null
        }
    }

    // Camera capture launcher (returns small Bitmap in extras). We save it to cache and store a file:// URI string.
    val takePictureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bmp = run {
                @Suppress("DEPRECATION")
                result.data?.extras?.get("data") as? Bitmap
            }
            if (bmp != null) {
                val savedUri = saveBitmapToCache(bmp, context)
                if (savedUri != null) selectedProofUri = savedUri
            }
        }
    }

    // Existing incidents list (kept for the IncidentTypeSummary and ActiveIncidents composable)
    var incidentsList by remember { mutableStateOf(listOf<Incident>()) }
    var activeIncidents by remember { mutableStateOf(listOf<Incident>()) }

    // Helper functions for incidents (small mocked data)
    fun loadIncidents() {
        // Load incidents from the shared IncidentStore so all screens observe the same data
        incidentsList = IncidentStore.incidents.toList()
        Log.d("HomeScreen", "Loaded ${incidentsList.size} incidents from IncidentStore")
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

    // Return incidents that are not resolved and reported within the last hour
    fun filterActiveIncidents(list: List<Incident>): List<Incident> {
        val oneHourMillis = 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        return list.filter { it.status != IncidentStatus.RESOLVED && (now - it.timeReported.time) <= oneHourMillis }
    }

    fun refreshActiveIncidents() { activeIncidents = filterActiveIncidents(incidentsList) }

    // Periodically refresh active incidents so items older than 1 hour are removed automatically
    LaunchedEffect(Unit) {
        // Initial load so Assigned/Active/Incoming lists have data on first render
        loadIncidents()
        fun persistAssigned(incId: String?) {
            lastAssignedIncidentId = incId
            try {
                val editor = prefs.edit()
                if (incId == null) editor.remove("last_assigned_incident_id")
                else editor.putString("last_assigned_incident_id", incId)
                editor.apply()
            } catch (_: Exception) { /* ignore */ }
        }

        val savedAssigned = lastAssignedIncidentId?.let { id ->
            IncidentStore.incidents.firstOrNull { it.id == id && it.status != IncidentStatus.RESOLVED }
        }
        if (savedAssigned != null) {
            if (savedAssigned.assignedTo != responderName) {
                IncidentStore.assignIncident(savedAssigned.id, responderName)
                incidentsList = IncidentStore.incidents.toList()
            }
        } else if (incidentsList.none { it.assignedTo == responderName && it.status != IncidentStatus.RESOLVED }) {
            incidentsList.firstOrNull { it.status != IncidentStatus.RESOLVED }?.let { inc ->
                IncidentStore.assignIncident(inc.id, responderName)
                incidentsList = IncidentStore.incidents.toList()
                persistAssigned(inc.id)
            }
        }
        if (incomingRequests.isEmpty()) {
            demoFeedIncomingRequests(incomingRequests)
        }
        refreshActiveIncidents()

        while (true) {
            refreshActiveIncidents()
            delay(60_000L) // check every minute
        }
    }

    // Location tracking state (for sharing + On Scene distance checks)
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

    val onSceneLocationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val destinationLat = navDestinationLat
                val destinationLng = navDestinationLng
                val destinationId = navDestinationIncidentId
                val current = locationResult.lastLocation

                if (destinationLat == null || destinationLng == null || destinationId.isNullOrBlank() || current == null) {
                    return
                }

                val results = FloatArray(1)
                Location.distanceBetween(
                    current.latitude,
                    current.longitude,
                    destinationLat,
                    destinationLng,
                    results
                )

                onSceneEnabledMap[destinationId] = results[0] <= 20f
            }
        }
    }

    @Suppress("DEPRECATION")
    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val request = LocationRequest.Builder(10000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
        } catch (se: SecurityException) {
            Log.d("HomeScreen", "Location updates request failed: ${se.message}")
        }
    }

    // Nav to incident using Google Maps (UI-only, safe)
    fun navigateToLocation(lat: Double?, lng: Double?, address: String?) {
        // Prefer coordinates if available (more accurate), otherwise fall back to address query
        try {
            // Persist the last navigation target so it can be resumed later.
            val lastNavPrefs = context.getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE)
            if (lat != null && lng != null) {
                lastNavPrefs.edit()
                    .putString("last_nav_lat", lat.toString())
                    .putString("last_nav_lng", lng.toString())
                    .putString("last_nav_addr", address)
                    .apply()
            } else if (!address.isNullOrBlank()) {
                lastNavPrefs.edit()
                    .putString("last_nav_lat", "")
                    .putString("last_nav_lng", "")
                    .putString("last_nav_addr", address)
                    .apply()
            }

            val savedLat = lastNavPrefs.getString("last_nav_lat", "")
            val savedLng = lastNavPrefs.getString("last_nav_lng", "")
            val savedAddr = lastNavPrefs.getString("last_nav_addr", null)

            val resolvedLat = lat ?: savedLat?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val resolvedLng = lng ?: savedLng?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val resolvedAddr = address ?: savedAddr

            if (resolvedLat != null && resolvedLng != null) {
                 // Use Google Maps navigation to coordinates
                val gmmIntentUri = ("google.navigation:q=$resolvedLat,$resolvedLng").toUri()
                 val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                 val resolveInfo = context.packageManager.resolveActivity(mapIntent, 0)
                 if (resolveInfo != null) {
                     context.startActivity(mapIntent)
                     return
                 } else {
                     // Fallback to geo URI with label
                     val geoUri = ("geo:$resolvedLat,$resolvedLng?q=$resolvedLat,$resolvedLng(${Uri.encode(resolvedAddr ?: "Incident")})").toUri()
                     val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
                     val fallbackResolve = context.packageManager.resolveActivity(geoIntent, 0)
                     if (fallbackResolve != null) { context.startActivity(geoIntent); return }
                 }
             }

             // If coordinates not available or failed, fall back to address-based navigation
            if (!resolvedAddr.isNullOrBlank()) {
                val gmmIntentUri = ("google.navigation:q=${Uri.encode(resolvedAddr)}").toUri()
                 val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                 val resolveInfo = context.packageManager.resolveActivity(mapIntent, 0)
                 if (resolveInfo != null) { context.startActivity(mapIntent); return } else {
                    val geoIntent = Intent(Intent.ACTION_VIEW, ("geo:0,0?q=${Uri.encode(resolvedAddr)}").toUri())
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

    // Report that responder is on scene for an Incident (updates local UI state and notifies admin placeholder)
    fun sendOnSceneReport(incident: Incident) {
        try {
            // update the incident status to ON_SCENE if not already
            val updated = incidentsList.map { if (it.id == incident.id) it.copy(status = IncidentStatus.ON_SCENE) else it }
            incidentsList = updated
            refreshActiveIncidents()
            RouteHistoryStore.completeRoute(context, incident.id)
            fusedLocationClient.removeLocationUpdates(onSceneLocationCallback)
            Toast.makeText(context, "On-scene reported to command", Toast.LENGTH_SHORT).show()
            Log.i("HomeScreen", "On-scene report for incident ${incident.id}")
            // In a full implementation you would POST to backend here
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to report on-scene: ${e.message}")
            Toast.makeText(context, "Failed to send on-scene report", Toast.LENGTH_SHORT).show()
        }
    }

    // Mark an incident as done (move to PENDING_REVIEW) with notes and proof URI.
    fun markIncidentDone(incident: Incident, notes: String, proofUri: String?) {
        try {
            // Prefer the account username (settings) as the author of actions; fall back to responderName variable.
            val prefsName = prefs.getString("account_username", responderName)

            // Move the incident into the pending review workflow instead of resolving immediately.
            IncidentStore.markPendingReview(incident.id, prefsName)

            // Persist proof, notes and completion timestamp in the IncidentStore supportive maps.
            try { IncidentStore.storeProof(incident.id, proofUri) } catch (_: Exception) { /* ignore */ }
            try { IncidentStore.storeCompletionNotes(incident.id, notes) } catch (_: Exception) { /* ignore */ }
            try { IncidentStore.storeCompletionTime(incident.id, System.currentTimeMillis()) } catch (_: Exception) { /* ignore */ }

            // Refresh local lists so UI updates.
            incidentsList = IncidentStore.incidents.toList()
            refreshActiveIncidents()

            if (lastNotifiedIncidentId == incident.id) {
                lastNotifiedIncidentId = null
                try { prefs.edit().remove("last_notified_incident_id").apply() } catch (_: Exception) { /* ignore */ }
            }
            if (lastAssignedIncidentId == incident.id) {
                lastAssignedIncidentId = null
                try { prefs.edit().remove("last_assigned_incident_id").apply() } catch (_: Exception) { /* ignore */ }
            }

            // Auto-assign the next available incident so a new assignment triggers a notification.
            val desiredType: IncidentType? = effectiveRole?.let { roleStr ->
                when (roleStr.trim().lowercase()) {
                    "fire" -> IncidentType.FIRE
                    "medical" -> IncidentType.MEDICAL
                    "crime" -> IncidentType.CRIME
                    else -> null
                }
            }
            val nextIncident = IncidentStore.incidents.firstOrNull { inc ->
                val matchesType = desiredType?.let { inc.type == it } ?: true
                val unassigned = inc.assignedTo.isNullOrBlank()
                val notResolved = inc.status != IncidentStatus.RESOLVED && inc.status != IncidentStatus.PENDING_REVIEW && inc.status != IncidentStatus.SUBMITTED_REVIEW
                matchesType && unassigned && notResolved
            }
            if (nextIncident != null) {
                IncidentStore.assignIncident(nextIncident.id, responderName)
                incidentsList = IncidentStore.incidents.toList()
                refreshActiveIncidents()
                lastAssignedIncidentId = nextIncident.id
                try { prefs.edit().putString("last_assigned_incident_id", nextIncident.id).apply() } catch (_: Exception) { /* ignore */ }
            }
            Toast.makeText(context, "Incident marked pending review and proof saved", Toast.LENGTH_SHORT).show()
            Log.i("HomeScreen", "Marked incident ${incident.id} pending review. Notes: ${notes}. Proof: ${proofUri}")
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to mark incident done: ${e.message}")
            Toast.makeText(context, "Failed to mark complete", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    fun startOnSceneTracking() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val request = LocationRequest.Builder(5000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, onSceneLocationCallback, null)
        } catch (se: SecurityException) {
            Log.d("HomeScreen", "On-scene tracking request failed: ${se.message}")
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentLatitude = null
        currentLongitude = null
    }

    fun hasAlwaysLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    var showAlwaysLocationNotice by remember { mutableStateOf(false) }

    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                showAlwaysLocationNotice = false
                Toast.makeText(context, "Always-on location enabled", Toast.LENGTH_SHORT).show()
            } else {
                showAlwaysLocationNotice = true
                Toast.makeText(context, "Please enable 'Allow all the time' in App settings", Toast.LENGTH_LONG).show()
            }
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(), onResult = { isGranted: Boolean ->
        if (isGranted) {
            if (isDeviceLocationEnabled(context)) {
                Toast.makeText(context, "Turn on Location to start live monitoring", Toast.LENGTH_LONG).show()
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            isLocationShared = true
            isLocationMonitoringEnabled = true
            prefs.edit().putBoolean(locationMonitoringEnabledKey, true).apply()
            startLocationUpdates()
            showAlwaysLocationNotice = !hasAlwaysLocationPermission()
        } else {
            isLocationShared = false
            isLocationMonitoringEnabled = false
            prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    })

    val onScenePermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(), onResult = { isGranted: Boolean ->
        if (isGranted) {
            startOnSceneTracking()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    })

    val handleShareLocationToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                isLocationShared = true
                startLocationUpdates()
                showAlwaysLocationNotice = !hasAlwaysLocationPermission()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            isLocationShared = false
            stopLocationUpdates()
        }
    }


    Scaffold(
        topBar = { /* header removed */ },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Slightly larger FAB for sending backup requests (not full-size FAB, but bigger than Small)
                SmallFloatingActionButton(
                    onClick = { showDepartmentSelection = true },
                    modifier = Modifier.size(48.dp) // modestly larger than the default small FAB
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = "Send Backup Request",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Share location FAB removed per request
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // UI-only state for dialogs opened by the navigation buttons (Settings)
            if (showAlwaysLocationNotice) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Allow location all the time",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "For faster dispatch and safer tracking, set location access to 'Allow all the time'.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showAlwaysLocationNotice = false }) {
                                    Text("Later")
                                }
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                        context.startActivity(intent)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                }) {
                                    Text("Open settings")
                                }
                            }
                        }
                    }
                }
            }

            // Compute counts for the statistic cards using existing incident lists (active incidents)
            val fireCount = activeIncidents.count { it.type == IncidentType.FIRE }
            val medicalCount = activeIncidents.count { it.type == IncidentType.MEDICAL }
            val crimeCount = activeIncidents.count { it.type == IncidentType.CRIME }

            // Resolve assigned incidents once per composition, and show only one.
            val assignedCandidateForRole = run {
                val desiredType: IncidentType? = effectiveRole?.let { roleStr ->
                    when (roleStr.trim().lowercase()) {
                        "fire" -> IncidentType.FIRE
                        "medical" -> IncidentType.MEDICAL
                        "crime" -> IncidentType.CRIME
                        else -> null
                    }
                }

                val source = IncidentStore.incidents
                val savedAssigned = lastAssignedIncidentId?.let { id ->
                    source.firstOrNull { it.id == id && it.status != IncidentStatus.RESOLVED }
                }
                if (savedAssigned != null) {
                    val matchesType = desiredType?.let { savedAssigned.type == it } ?: true
                    if (matchesType) return@run listOf(savedAssigned)
                }
                 val filtered = source.filter { inc ->
                     val matchesType = desiredType?.let { inc.type == it } ?: true
                     val notResolved = inc.status != IncidentStatus.RESOLVED
                     val assignedToMe = inc.assignedTo == responderName
                     matchesType && notResolved && assignedToMe
                 }

                fun prioRank(p: IncidentPriority) = when (p) {
                    IncidentPriority.HIGH -> 3
                    IncidentPriority.MEDIUM -> 2
                    IncidentPriority.LOW -> 1
                }

                filtered.sortedWith(compareByDescending<Incident> { prioRank(it.priority) }
                    .thenByDescending { it.timeReported.time })
                    .take(1)
            }

            val assignedListForRole = if (showAssignedAfterNotification) {
                assignedCandidateForRole
            } else {
                emptyList()
            }

            // Ensure On Scene starts disabled for any newly displayed assignment.
            assignedListForRole.firstOrNull()?.id?.let { id ->
                if (!onSceneEnabledMap.containsKey(id)) {
                    onSceneEnabledMap[id] = false
                }
            }

            // Notify before showing the newly assigned incident.
            LaunchedEffect(assignedCandidateForRole.firstOrNull()?.id) {
                val inc = assignedCandidateForRole.firstOrNull() ?: return@LaunchedEffect
                if (lastNotifiedIncidentId == inc.id) return@LaunchedEffect

                lastNotifiedIncidentId = inc.id
                try { prefs.edit().putString("last_notified_incident_id", inc.id).apply() } catch (_: Exception) { /* ignore */ }
                val loc = inc.location.ifBlank { "Unknown location" }
                newIncidentMessage = "New ${inc.type.displayName} incident assigned: $loc"
                showAssignedAfterNotification = false
                showNewIncidentNotification = true
                // Auto-dismiss after 3 seconds, then allow the incident to appear.
                delay(3000L)
                showNewIncidentNotification = false
                showAssignedAfterNotification = true
            }

            // Coroutine scope for backup requests
            val scope = rememberCoroutineScope()

            fun sendBackupMessageToDepartment(departmentKey: String) {
                val backupMessage = "I need backup on scene"
                val deptName = when (departmentKey.lowercase()) {
                    "fire" -> "Fire Department"
                    "medical" -> "Medical Department"
                    "crime" -> "Crime Department"
                    else -> "Emergency Services"
                }

                scope.launch {
                    try {
                        // API integration not available in this build; local stub logs and shows a toast.
                        Log.i("HomeScreen", "Backup request (stub) sent to $deptName: $backupMessage")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Backup request sent to $deptName", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Backup request error: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Layout: Top row (Hello + Name left, Avatar right) -> Stat cards -> Assigned incidents -> Map/Incoming buttons -> Settings/More
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (showAlwaysLocationNotice) 132.dp else 0.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Purple header with subtle gradient and rounded bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                ) {
                    // increase top padding so name/profile sit lower and have more breathing room
                    Row(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Hello", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Make the name wrap its content and add a small end padding so the edit icon sits closer
                                Text(text = responderName.ifBlank { "Responder" }, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)

                             }
                         }

                        // Circular profile portrait with themed background ring
                        Box(modifier = Modifier.size(64.dp)) {
                            ResponderAvatar(modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape), imageUri = responderImageUri, drawableRes = responderDrawableRes, status = if (onlineStatus == ResponderOnlineStatus.Online) ResponderOnlineStatus.Online else ResponderOnlineStatus.Offline)
                        }
                    }
                }

                // Put the stat cards overlapping the header slightly — negative offset lifts them over the purple area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp)
                        // reduce overlap so the cards are clear of the name/profile area
                        .offset(y = (42).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        // add extra top padding so the number isn't visually tight against the card top
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = fireCount.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Fire Today", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = medicalCount.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Medical Today", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(6.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = crimeCount.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Crime Today", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(55.dp))

            // Assigned Incidents (card-based list)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Assigned Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)


                if (assignedListForRole.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "No assigned incidents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    for (inc in assignedListForRole) {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            // Map priority to a visible color (High=red, Medium=orange, Low=yellow)
                            val priorityColor = when (inc.priority) {
                                IncidentPriority.HIGH -> Color(0xFFD32F2F)
                                IncidentPriority.MEDIUM -> Color(0xFFFFA000)
                                IncidentPriority.LOW -> Color(0xFFFFEB3B)
                            }

                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Header row: type icon + details (type, location, time & status) + priority badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Type icon: circular badge with initial (F/M/C) to avoid relying on external icon vectors
                                    val typeInitial = when (inc.type) {
                                        IncidentType.FIRE -> "F"
                                        IncidentType.MEDICAL -> "M"
                                        IncidentType.CRIME -> "C"
                                        else -> "?"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(priorityColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = typeInitial, fontWeight = FontWeight.Bold, color = priorityColor)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Top line: incident type + small dot + time reported
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = inc.type.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // time label (relative if recent, otherwise hh:mm AM/PM)
                                            val timeLabel = run {
                                                try {
                                                    val now = System.currentTimeMillis()
                                                    val then = inc.timeReported.time
                                                    val diffMin = ((now - then) / 60000).toInt()
                                                    when {
                                                        diffMin < 1 -> "just now"
                                                        diffMin < 60 -> "${diffMin}m ago"
                                                        diffMin < 1440 -> {
                                                            val hours = diffMin / 60
                                                            "${hours}h ago"
                                                        }
                                                        else -> java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(inc.timeReported)
                                                    }
                                                } catch (_: Exception) { "" }
                                            }

                                            Text(text = timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // status chip
                                            Box(modifier = Modifier.background(Color(0xFFEFEFEF), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text(text = inc.status.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Location (exact / nearest landmark)
                                        Text(text = inc.location.ifBlank { "Unknown location" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    // Priority badge with colored border / subtle background
                                    Box(
                                        modifier = Modifier
                                            .border(2.dp, priorityColor, RoundedCornerShape(8.dp))
                                            .background(priorityColor.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = inc.priority.name.uppercase(), color = priorityColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Visible details / single-line summary
                                inc.description.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Action buttons for assigned incidents: icon + text, aligned right and closer together
                                val buttonScope = rememberCoroutineScope()
                                val showNavLabel = remember(inc.id) { mutableStateOf(false) }
                                val showOnSceneLabel = remember(inc.id + "_scene") { mutableStateOf(false) }
                                val showMarkLabel = remember(inc.id + "_mark") { mutableStateOf(false) }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Navigate icon-only button
                                        OutlinedButton(
                                            onClick = {
                                                val navPrefs = context.getSharedPreferences("nav_prefs", android.content.Context.MODE_PRIVATE)
                                                navPrefs.edit().putString("last_nav_incident_id", inc.id).apply()
                                                onSceneEnabledMap[inc.id] = false
                                                navDestinationIncidentId = inc.id
                                                navDestinationLat = inc.latitude
                                                navDestinationLng = inc.longitude

                                                if (currentLatitude != null && currentLongitude != null && inc.latitude != null && inc.longitude != null) {
                                                    RouteHistoryStore.startRoute(
                                                        context = context,
                                                        incidentId = inc.id,
                                                        startLat = currentLatitude ?: 0.0,
                                                        startLng = currentLongitude ?: 0.0,
                                                        destLat = inc.latitude,
                                                        destLng = inc.longitude
                                                    )
                                                }

                                                if (navDestinationLat != null && navDestinationLng != null) {
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                                        startOnSceneTracking()
                                                    } else {
                                                        onScenePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                                    }
                                                }

                                                navigateToLocation(inc.latitude, inc.longitude, inc.location)
                                            },
                                            modifier = Modifier.size(40.dp).combinedClickable(onClick = {}, onLongClick = {
                                                showNavLabel.value = true
                                                buttonScope.launch { delay(1200); showNavLabel.value = false }
                                            }),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                        }
                                        if (showNavLabel.value) Text(text = "Navigate", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))

                                        // On Scene icon-only button
                                        OutlinedButton(
                                            onClick = { sendOnSceneReport(inc) },
                                            enabled = onSceneEnabledMap[inc.id] == true,
                                            modifier = Modifier.size(40.dp).combinedClickable(onClick = {}, onLongClick = {
                                                showOnSceneLabel.value = true
                                                buttonScope.launch { delay(1200); showOnSceneLabel.value = false }
                                            }),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.MyLocation, contentDescription = "On Scene", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                        }
                                        if (showOnSceneLabel.value) Text(text = "On Scene", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))

                                        // Mark Complete icon-only button (primary)
                                        Button(
                                            onClick = {
                                                markTargetIncidentInc = inc
                                                proofNotes = ""
                                                selectedProofUri = null
                                                showMarkCompleteDialog = true
                                            },
                                            modifier = Modifier.size(40.dp).combinedClickable(onClick = {}, onLongClick = {
                                                showMarkLabel.value = true
                                                buttonScope.launch { delay(1200); showMarkLabel.value = false }
                                            }),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = MaterialTheme.colorScheme.onPrimary),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Done, contentDescription = "Mark Complete", modifier = Modifier.size(20.dp))
                                        }
                                        if (showMarkLabel.value) Text(text = "Mark", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                                    }
                                }
                             }
                         }
                     }
                 }
             }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Incidents (shows currently ongoing incidents: Fire, Medical, Crime)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Active Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Show all active incidents here (no responderRole filtering) so responders can see incidents across departments
                // Exclude DISASTER incidents from the active incidents list per request
                val activeListVisible = remember(activeIncidents) { activeIncidents.filter { it.type != IncidentType.DISASTER } }

                if (activeListVisible.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "No active incidents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    for (inc in activeListVisible) {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    // Incident type icon / label
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = inc.type.displayName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = inc.location.ifBlank { "Unknown location" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }

                                    // Priority badge on the right
                                    val priorityColor = when (inc.priority) {
                                        IncidentPriority.HIGH -> Color(0xFFD32F2F)
                                        IncidentPriority.MEDIUM -> Color(0xFFFFA000)
                                        IncidentPriority.LOW -> Color(0xFFFFEB3B)
                                    }
                                    Box(modifier = Modifier
                                        .border(2.dp, priorityColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(text = inc.priority.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Description / time / status
                                Text(text = inc.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)

                                // Show time reported and duration since reported (if recent)
                                val now = System.currentTimeMillis()
                                val reportedTime = inc.timeReported.time
                                val diffMillis = now - reportedTime
                                val showDuration = diffMillis in 0..(60L * 60L * 1000L) // within 1 hour

                                // Time reported (relative or absolute)
                                val timeReportedText = run {
                                    try {
                                        val diffMin = (diffMillis / 60000).toInt()
                                        when {
                                            diffMin < 1 -> "just now"
                                            diffMin < 60 -> "${diffMin}m ago"
                                            diffMin < 1440 -> {
                                                val hours = diffMin / 60
                                                "${hours}h ago"
                                            }
                                            else -> java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(inc.timeReported)
                                        }
                                    } catch (_: Exception) { "" }
                                }

                                // Duration since reported (if within 1 hour)
                                val durationText = if (showDuration) {
                                    val seconds = (diffMillis / 1000).toInt() % 60
                                    val minutes = (diffMillis / (1000 * 60)).toInt() % 60
                                    val hours = (diffMillis / (1000 * 60 * 60)).toInt()

                                    // Format as "1h 23m" or "45m 30s" etc.
                                    buildString {
                                        if (hours > 0) append("${hours}h ")
                                        if (minutes > 0) append("${minutes}m ")
                                        append("${seconds}s")
                                    }.trim()
                                } else ""

                                // Combine time reported and duration in a single text element
                                Text(
                                    text = "Reported: $timeReportedText${if (durationText.isNotBlank()) " ($durationText)" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                // Buttons intentionally removed for Active Incidents to keep this list read-only (details and priority remain visible)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary action button: Settings
            OutlinedButton(onClick = { showSettingsDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) { Text("Settings") }
            Spacer(modifier = Modifier.height(8.dp)) // add a little space below the Settings button

            // Mark Complete dialog with proof (notes + image). This enforces adding proof before submission.
            if (showMarkCompleteDialog && markTargetIncidentInc != null) {
                val hasProof = selectedProofUri != null
                val hasTwoWords = proofNotes.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 2
                 AlertDialog(
                     onDismissRequest = { showMarkCompleteDialog = false; markTargetIncidentInc = null },
                     title = { Text(text = "Mark Incident Complete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground) },
                     text = {
                         Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             Text(text = "Provide a short note and take a photo as proof that the incident has been completed.", color = MaterialTheme.colorScheme.onBackground)

                             androidx.compose.material3.OutlinedTextField(
                                 value = proofNotes,
                                 onValueChange = { proofNotes = it },
                                 label = { Text("Completion notes") },
                                 modifier = Modifier.fillMaxWidth(),
                                 colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                     focusedTextColor = if (!hasProof) Color.White else MaterialTheme.colorScheme.onBackground,
                                     unfocusedTextColor = if (!hasProof) Color.White else MaterialTheme.colorScheme.onBackground
                                 )
                             )

                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Camera capture button (use primary container so label contrasts)
                                Button(onClick = {
                                    // Launch camera intent (returns small Bitmap in extras)
                                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                    takePictureLauncher.launch(cameraIntent)
                                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Text("Take Photo")
                                }
                                selectedProofUri?.let { uriStr ->
                                     // simple preview: support both content:// and file:// URIs
                                     val bitmap = try {
                                         if (uriStr.startsWith("file://")) {
                                             val path = Uri.parse(uriStr).path
                                             if (path != null) BitmapFactory.decodeFile(path) else null
                                         } else {
                                             val uri = Uri.parse(uriStr)
                                             context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
                                         }
                                     } catch (_: Exception) { null }

                                     if (bitmap != null) {
                                         Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Proof image", modifier = Modifier.size(64.dp))
                                     } else {
                                         Text(text = "Selected: ${uriStr.substringAfterLast('/')}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                     }
                                 }
                             }
                         }
                     },
                     confirmButton = {
                         Button(
                             onClick = {
                                 val inc = markTargetIncidentInc
                                 if (inc != null) {
                                     markIncidentDone(inc, proofNotes, selectedProofUri)
                                     showMarkCompleteDialog = false
                                     markTargetIncidentInc = null
                                     proofNotes = ""
                                     selectedProofUri = null
                                 }
                             },
                             enabled = hasTwoWords && hasProof,
                             colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                         ) { Text("Submit") }
                     },
                     dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showMarkCompleteDialog = false; markTargetIncidentInc = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground) }
                     }
                 )
             }

            // End of main scrollable content column.
        }

            // Keep the dialog outside the main content so it can appear regardless of scroll.
            if (showDepartmentSelection) {
                DepartmentSelectionDialog(
                    onDismiss = { showDepartmentSelection = false },
                    onDepartmentSelected = { departmentKey ->
                        sendBackupMessageToDepartment(departmentKey)
                        showDepartmentSelection = false
                    }
                )
            }

            if (showSettingsDialog) {
                AccountSettingsDialog(
                    fullName = accountFullName,
                    username = accountUsername,
                    email = accountEmail,
                    photoUri = accountPhotoUri,
                    isDarkMode = isDarkMode,
                    onFullNameChange = { accountFullName = it },
                    onUsernameChange = { accountUsername = it },
                    onEmailChange = { accountEmail = it },
                    onDarkModeChange = { enabled ->
                        isDarkMode = enabled
                        prefs.edit().putBoolean("dark_mode", enabled).apply()
                    },
                    onPickPhoto = { pickProfilePhotoLauncher.launch("image/*") },
                    onSave = {
                        prefs.edit()
                            .putString("account_full_name", accountFullName.trim())
                            .putString("account_username", accountUsername.trim())
                            .putString("account_email", accountEmail.trim())
                            .putString("account_photo", accountPhotoUri)
                            .apply()
                        // Update displayed responder name to use the username set in settings.
                        if (!accountUsername.isNullOrBlank()) {
                            responderName = accountUsername.trim()
                        }
                        showSettingsDialog = false
                     },
                     onBack = { showSettingsDialog = false },
                     onLogout = {
                         prefs.edit().clear().apply()
                         context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                         Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                         showSettingsDialog = false
                     }
                 )
             }

            // New incident notification popup (overlay at top)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showNewIncidentNotification,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(top = 16.dp)
                            .combinedClickable(
                                onClick = { showNewIncidentNotification = false },
                                onLongClick = { showNewIncidentNotification = false }
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        elevation = CardDefaults.cardElevation(8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = "New Incident",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New Incident Assigned!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = newIncidentMessage,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { showNewIncidentNotification = false }) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Dismiss",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        } // closes outer Box (content root)
    } // closes Scaffold
} // closes HomeScreen

@Composable
private fun AccountSettingsDialog(
    fullName: String,
    username: String,
    email: String,
    photoUri: String?,
    isDarkMode: Boolean,
    onFullNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Account Settings", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResponderAvatar(
                        modifier = Modifier.size(64.dp),
                        imageUri = photoUri,
                        drawableRes = null,
                        status = ResponderOnlineStatus.Offline,
                        contentDescription = "Profile photo"
                    )
                    OutlinedButton(onClick = onPickPhoto, modifier = Modifier.widthIn(min = 120.dp)) {
                        Text("Add Photo")
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Night mode")
                    androidx.compose.material3.Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange
                    )
                }

                androidx.compose.material3.HorizontalDivider()
                Text("Account Actions", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onLogout, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Logout")
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save Changes") }
        },
        dismissButton = {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    )
}
