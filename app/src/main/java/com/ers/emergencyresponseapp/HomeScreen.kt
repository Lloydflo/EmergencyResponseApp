@file:OptIn(ExperimentalFoundationApi::class)
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.analytics.RouteHistoryStore
import com.ers.emergencyresponseapp.home.composables.BackupRequest
import com.ers.emergencyresponseapp.home.composables.DepartmentSelectionDialog
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CameraAlt
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import androidx.navigation.NavHostController
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ers.emergencyresponseapp.features.assigned.AssignedIncidentsViewModel
import com.ers.emergencyresponseapp.features.assigned.toDomain
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Dialog
import android.os.VibrationEffect
import android.os.Vibrator
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.MarkRouteArrivedRequest




// ─────────────────────────────────────────────────────────────────────────────
// APP COLORS
// ─────────────────────────────────────────────────────────────────────────────

private object AppColors {
    val Primary       = Color(0xFF4C8A89)
    val Secondary     = Color(0xFF3A506B)
    val Tertiary      = Color(0xFF1C2541)
    val Dark          = Color(0xFF0B132B)
    val Text          = Color(0xFF171717)
    val TextSecondary = Color(0xFF575757)
    val Border        = Color(0xFFE5E5E5)
    val Bg            = Color(0xFFFFFFFF)
    val FooterBg      = Color(0xFFFAFAFA)
}

// FIX 2: Hoist stable Brush objects to top-level constants so they are never
// recreated during recomposition. Brushes are immutable value types — making
// them top-level is safe and eliminates per-frame allocation.
private val HeaderBrush = Brush.verticalGradient(
    listOf(AppColors.Primary, AppColors.Secondary, AppColors.Tertiary)
)
private val AssignedCardBrush = Brush.verticalGradient(
    listOf(Color.White, AppColors.Primary.copy(0.04f))
)
private val AssignedBarBrush = Brush.verticalGradient(
    listOf(AppColors.Primary, AppColors.Secondary)
)

// FIX 3: Pre-compute per-type accent brushes as stable top-level objects.
// Previously these were created inside items{} lambdas on every scroll frame.
private val FireCardBrush    = Brush.verticalGradient(listOf(Color(0xFFE53935).copy(0.07f), Color.White))
private val MedicalCardBrush = Brush.verticalGradient(listOf(Color(0xFF1E88E5).copy(0.07f), Color.White))
private val CrimeCardBrush   = Brush.verticalGradient(listOf(Color(0xFF6D4C41).copy(0.07f), Color.White))

private val FireBarBrush     = Brush.horizontalGradient(listOf(Color(0xFFE53935).copy(0.4f), Color(0xFFE53935)))
private val MedicalBarBrush  = Brush.horizontalGradient(listOf(Color(0xFF1E88E5).copy(0.4f), Color(0xFF1E88E5)))
private val CrimeBarBrush    = Brush.horizontalGradient(listOf(Color(0xFF6D4C41).copy(0.4f), Color(0xFF6D4C41)))

private val cardBrushesStable = listOf(FireCardBrush, MedicalCardBrush, CrimeCardBrush)
private val barBrushesStable  = listOf(FireBarBrush,  MedicalBarBrush,  CrimeBarBrush)


// ─────────────────────────────────────────────────────────────────────────────
// PRIVATE HELPERS / SMALL TYPES
// ─────────────────────────────────────────────────────────────────────────────

private fun isDeviceLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

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
    val address: String? = null,
    val description: String? = null,
    val status: String = "Reported"
)

private fun saveUriToAppStorage(ctx: Context, uri: Uri): String? {
    return try {
        val dir = File(ctx.filesDir, "profile_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "profile_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        Log.e("HomeScreen", "Failed to persist profile photo: ${e.message}")
        null
    }
}

private fun demoFeedIncomingRequests(list: MutableList<EmergencyRequest>) {
    val types      = listOf("Medical", "Fire", "Crime", "Disaster")
    val priorities = EmergencyPriority.entries
    val random     = java.util.Random()
    while (list.size < 6) {
        val id       = list.size + 1
        val type     = types[random.nextInt(types.size)]
        val priority = priorities[random.nextInt(priorities.size)]
        val dist     = "${(1..20).random()} km"
        val ts       = "${(1..12).random()}:${(0..59).random().toString().padStart(2,'0')} ${if (random.nextBoolean()) "AM" else "PM"}"
        val lat      = random.nextDouble() * 180.0 - 90.0
        val lng      = random.nextDouble() * 360.0 - 180.0
        list.add(EmergencyRequest(id, type, dist, ts, priority, lat, lng, "Random $type $id St", "Description for ${type.lowercase()} incident #$id"))
    }
}

private fun startRouteUpdateMonitoring(
    context: Context,
    incidentId: String,
    destLat: Double?,
    destLng: Double?,
    destAddress: String?
) {
    Log.d("RouteMonitor", "Starting service for incident=$incidentId")
    Log.d("LiveGPS", "Calling startForegroundService incident=$incidentId")
    Toast.makeText(context, "Starting monitor…", Toast.LENGTH_SHORT).show()
    val intent = Intent(context, RouteMonitoringService::class.java).apply {
        putExtra(RouteMonitoringService.EXTRA_INCIDENT_ID,   incidentId)
        putExtra(RouteMonitoringService.EXTRA_DEST_LAT,      destLat ?: Double.NaN)
        putExtra(RouteMonitoringService.EXTRA_DEST_LNG,      destLng ?: Double.NaN)
        putExtra(RouteMonitoringService.EXTRA_DEST_ADDRESS,  destAddress ?: "")
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun openMapPin(context: Context, lat: Double?, lng: Double?, address: String?) {
    try {
        val primaryUri = when {
            !address.isNullOrBlank() -> "geo:0,0?q=${Uri.encode(address)}".toUri()
            lat != null && lng != null -> "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(address ?: "Incident")})".toUri()
            else -> null
        }
        if (primaryUri != null) {
            val mapIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply { setPackage("com.google.android.apps.maps") }
            if (context.packageManager.resolveActivity(mapIntent, 0) != null) { context.startActivity(mapIntent); return }
            val fallback = Intent(Intent.ACTION_VIEW, primaryUri)
            if (context.packageManager.resolveActivity(fallback, 0) != null) { context.startActivity(fallback); return }
        }
        Toast.makeText(context, "No map application available", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open map", Toast.LENGTH_SHORT).show()
    }
}

private fun formatUnitStatus(status: String): String {
    return when (status.lowercase()) {
        "available" -> "Available"
        "assigned" -> "Assigned"
        "received" -> "Assigned"
        "en_route" -> "En Route"
        "on_scene" -> "On Scene"
        "completed" -> "Available"
        else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

private fun timeAgoLabel(timeReported: java.util.Date): String {
    val diffMin = ((System.currentTimeMillis() - timeReported.time) / 60000).toInt()
    return when {
        diffMin < 1    -> "just now"
        diffMin < 60   -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else           -> java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(timeReported)
    }
}

private enum class ActivePriorityFilter { ALL, HIGH, MEDIUM, LOW }

// FIX 4: Stable incident sort comparator hoisted to top-level so it is not
// re-allocated on every recomposition that calls sortedWith().
private val incidentPriorityComparator: Comparator<Incident> =
    compareByDescending<Incident> {
        when (it.priority) { IncidentPriority.HIGH -> 3; IncidentPriority.MEDIUM -> 2; IncidentPriority.LOW -> 1 }
    }.thenByDescending { it.timeReported.time }


// ─────────────────────────────────────────────────────────────────────────────
// RESPONDER AVATAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)

@Composable
private fun ResponderAvatar(
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    drawableRes: Int? = null,
    status: ResponderOnlineStatus = ResponderOnlineStatus.Offline,
    contentDescription: String = "Responder avatar"
) {
    val context = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.Center) {
        when {
            drawableRes != null -> Image(
                painter = painterResource(id = drawableRes),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            imageUri != null -> {
                val bitmap = remember(imageUri) {
                    try {
                        if (imageUri.startsWith("file://")) {
                            Uri.parse(imageUri).path?.let { BitmapFactory.decodeFile(it) }
                        } else {
                            context.contentResolver.openInputStream(imageUri.toUri())
                                ?.use { BitmapFactory.decodeStream(it) }
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
                    Icon(Icons.Default.AccountCircle, contentDescription, modifier = Modifier.fillMaxSize())
                }
            }
            else -> Icon(Icons.Default.AccountCircle, contentDescription, modifier = Modifier.fillMaxSize())
        }
        val dotColor = if (status == ResponderOnlineStatus.Online) Color(0xFF98EE9C) else Color.Gray
        Box(
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .padding(2.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ASSIGNED ACTION BUTTONS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AssignedActionButtons(
    inc: Incident,
    context: Context,
    navController: NavHostController,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onSceneEnabled: Boolean,
    setOnSceneEnabled: (Boolean) -> Unit,
    setNavTarget: (id: String, lat: Double?, lng: Double?) -> Unit,
    startOnSceneTracking: () -> Unit,
    requestOnScenePermission: () -> Unit,
    navigateToLocation: (Double?, Double?, String?) -> Unit,
    sendOnSceneReport: (Incident) -> Unit,
    openMarkDone: (Incident) -> Unit,
    onNavigateStatusUpdate: (Incident) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_nav_incident_id", inc.id)
                    .apply()

                setOnSceneEnabled(false)
                setNavTarget(inc.id, inc.latitude, inc.longitude)

                startRouteUpdateMonitoring(
                    context,
                    inc.id,
                    inc.latitude,
                    inc.longitude,
                    inc.location
                )
                onNavigateStatusUpdate(inc)
                startOnSceneTracking()
                setOnSceneEnabled(false)

                navigateToLocation(inc.latitude, inc.longitude, inc.location)
                context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pending_en_route_check", true)
                    .putString("pending_en_route_incident_id", inc.id)
                    .commit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Navigate to Incident", fontWeight = FontWeight.SemiBold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { sendOnSceneReport(inc) },
                enabled = onSceneEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("On Scene", fontSize = 13.sp)
            }

            Button(
                onClick = { openMarkDone(inc) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Complete", fontSize = 13.sp)
            }
        }
    }
}




// ─────────────────────────────────────────────────────────────────────────────
// EMERGENCY REQUEST CARD  (incoming backup requests)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyRequestCard(request: EmergencyRequest, showBackupBadge: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, contentDescription = request.type, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(request.type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (showBackupBadge) {
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Backup", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Badge(containerColor = request.priority.color) {
                    Text(request.priority.name, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${request.distance} • ${request.timestamp}", color = MaterialTheme.colorScheme.onSurface)
            if (showBackupBadge) {
                Spacer(Modifier.height(4.dp))
                Text(request.address ?: "Address unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val ctx = LocalContext.current
                OutlinedButton(onClick = { openMapPin(ctx, request.latitude, request.longitude, request.address) }, modifier = Modifier.height(36.dp)) { Text("View") }
                if (!showBackupBadge) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { }, modifier = Modifier.height(36.dp)) { Text("Accept") }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    responderRole: String? = null,
    onLogout: () -> Unit,
    assignedVm: AssignedIncidentsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCancelRouteDialog by remember { mutableStateOf(false) }
    var pendingRouteIncidentId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                val pending = navPrefs.getBoolean("pending_en_route_check", false)
                val incidentId = navPrefs.getString("pending_en_route_incident_id", null)

                if (pending && !incidentId.isNullOrBlank()) {
                    pendingRouteIncidentId = incidentId
                    showCancelRouteDialog = true
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var isLocationMonitoringEnabled by remember { mutableStateOf(false) }



    val storedPrefs      = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val storedDepartment = storedPrefs.getString("department", null)
    val responderId = storedPrefs.getString("user_id", "")?.toIntOrNull() ?: 0
    val unitCode = storedPrefs.getString("unit_code", "") ?: ""
    val unitType = storedPrefs.getString("unit_type", "") ?: ""
    var unitStatus by remember {
        mutableStateOf(storedPrefs.getString("unit_status", "available") ?: "available")
    }

    val assignedUi by assignedVm.ui.collectAsState()
    LaunchedEffect(assignedUi.incidents) {
        val latestStatus = assignedUi.incidents.firstOrNull()?.unit_status

        if (!latestStatus.isNullOrBlank()) {
            unitStatus = latestStatus

            storedPrefs.edit()
                .putString("unit_status", latestStatus)
                .apply()
        }
    }
    LaunchedEffect(responderId) {
        if (responderId <= 0) return@LaunchedEffect

        while (true) {
            assignedVm.load(responderId)
            assignedVm.loadActive(responderId)
            delay(5000L)
        }
    }
    val effectiveRole    = storedDepartment?.lowercase() ?: responderRole?.takeIf { it.isNotBlank() }
    val departmentFilter: IncidentType? = when (effectiveRole?.trim()?.lowercase()) {
        "fire"    -> IncidentType.FIRE
        "medical" -> IncidentType.MEDICAL
        "crime"   -> IncidentType.CRIME
        else      -> null
    }

    val prefs                        = context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
    val locationMonitoringEnabledKey = "location_monitoring_enabled"

    // Account state
    var accountFullName by remember { mutableStateOf(prefs.getString("account_full_name", "") ?: "") }
    var accountUsername by remember { mutableStateOf(prefs.getString("account_username", "") ?: "") }
    var accountEmail    by remember { mutableStateOf(prefs.getString("account_email", "") ?: "") }
    var accountPhotoUri by remember { mutableStateOf(prefs.getString("account_photo", null)) }
    var isDarkMode      by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    val pickProfilePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val stored = saveUriToAppStorage(context, uri) ?: uri.toString()
            accountPhotoUri = stored
            try { prefs.edit().putString("account_photo", accountPhotoUri).apply() } catch (_: Exception) {}
        }
    }

    val responderImageUri = accountPhotoUri
    val responderDrawable by remember { mutableStateOf<Int?>(null) }
    var responderName by remember {
        mutableStateOf(prefs.getString("account_username", prefs.getString("responder_name", "Name") ?: "Name") ?: "Name")
    }

    LaunchedEffect(accountUsername) {
        if (accountUsername.isNotBlank()) responderName = accountUsername
    }

    var selectedActiveIncident by remember { mutableStateOf<Incident?>(null) }
    var showActiveDetailsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var onlineStatus     by remember { mutableStateOf(ResponderOnlineStatus.Online) }
    val incomingRequests  = remember { mutableStateListOf<EmergencyRequest>() }

    var showNewIncidentNotification by remember { mutableStateOf(false) }
    var newIncidentMessage by remember { mutableStateOf("") }
    var showAssignedAfterNotification by remember { mutableStateOf(false) }
    var lastShownApiAssignedId by remember {
        mutableStateOf(prefs.getString("last_shown_api_assigned_id", null))
    }
    var lastNotifiedIncidentId        by remember { mutableStateOf(prefs.getString("last_notified_incident_id", null)) }
    var lastAssignedIncidentId        by remember { mutableStateOf(prefs.getString("last_assigned_incident_id", null)) }
    var navDestinationIncidentId      by remember { mutableStateOf<String?>(null) }
    var navDestinationLat             by remember { mutableStateOf<Double?>(null) }
    var navDestinationLng             by remember { mutableStateOf<Double?>(null) }
    val onSceneEnabledMap             = remember { mutableStateMapOf<String, Boolean>() }

    // ── DIALOG FLAGS ──
    var showDepartmentSelection by remember { mutableStateOf(false) }
    var showSettingsDialog      by remember { mutableStateOf(false) }
    var showAllActiveDialog     by remember { mutableStateOf(false) }
    var activeFilter            by remember { mutableStateOf(ActivePriorityFilter.ALL) }

    // Mark-complete state
    var showMarkCompleteDialog by remember { mutableStateOf(false) }
    var markTargetIncidentInc  by remember { mutableStateOf<Incident?>(null) }
    var proofNotes             by remember { mutableStateOf("") }
    var selectedProofUri       by remember { mutableStateOf<String?>(null) }

    var notificationCount by remember { mutableStateOf(0) }


    fun saveBitmapToCache(bitmap: Bitmap, ctx: Context): String? {
        return try {
            val file = File(ctx.cacheDir, "proof_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) { Log.e("HomeScreen", "Failed to save bitmap: ${e.message}"); null }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val bmp = result.data?.extras?.get("data") as? Bitmap
            if (bmp != null) { val uri = saveBitmapToCache(bmp, context); if (uri != null) selectedProofUri = uri }
        }
    }

    val activeIncidents = assignedUi.activeIncidents.map { it.toDomain() }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refreshHomeData() {
        if (isRefreshing) return

        scope.launch {
            isRefreshing = true
            assignedVm.load(responderId)
            assignedVm.loadActive(responderId)
            delay(500)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        assignedVm.load(responderId)
        assignedVm.loadActive(responderId)
        isLoading = false

        if (incomingRequests.isEmpty()) {
            demoFeedIncomingRequests(incomingRequests)
        }
    }

    // Location
    var isLocationShared by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var currentLatitude  by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }
    val fusedClient       = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { currentLatitude = it.latitude; currentLongitude = it.longitude }
            }
        }
    }
    val onSceneLocationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                val destLat = navDestinationLat ?: return
                val destLng = navDestinationLng ?: return
                val destId  = navDestinationIncidentId?.takeIf { it.isNotBlank() } ?: return
                val current = r.lastLocation ?: return
                val res     = FloatArray(1)
                Location.distanceBetween(current.latitude, current.longitude, destLat, destLng, res)
                onSceneEnabledMap[destId] = res[0] <= 50f
            }
        }
    }
    var deviceLocationEnabled by remember {
        mutableStateOf(isDeviceLocationEnabled(context))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val enabledNow = isDeviceLocationEnabled(context)

            deviceLocationEnabled = enabledNow

            if (!enabledNow) {
                isLocationMonitoringEnabled = false
                isLocationShared = false

                prefs.edit()
                    .putBoolean(locationMonitoringEnabledKey, false)
                    .apply()
            }

            delay(1000)
        }
    }

    @Suppress("DEPRECATION")
    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            fusedClient.requestLocationUpdates(LocationRequest.Builder(10000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build(), locationCallback, null)
        } catch (se: SecurityException) { Log.d("HomeScreen", "Location updates failed: ${se.message}") }
    }

    @Suppress("DEPRECATION")
    fun startOnSceneTracking() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            fusedClient.requestLocationUpdates(LocationRequest.Builder(5000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).build(), onSceneLocationCallback, null)
        } catch (se: SecurityException) { Log.d("HomeScreen", "On-scene tracking failed: ${se.message}") }
    }

    fun stopLocationUpdates() { fusedClient.removeLocationUpdates(locationCallback); currentLatitude = null; currentLongitude = null }

    fun hasAlwaysLocationPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun navigateToLocation(lat: Double?, lng: Double?, address: String?) {
        try {
            val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            if (lat != null && lng != null) navPrefs.edit().putString("last_nav_lat", lat.toString()).putString("last_nav_lng", lng.toString()).putString("last_nav_addr", address).apply()
            else if (!address.isNullOrBlank()) navPrefs.edit().putString("last_nav_lat", "").putString("last_nav_lng", "").putString("last_nav_addr", address).apply()

            val rLat  = lat  ?: navPrefs.getString("last_nav_lat", "")?.toDoubleOrNull()
            val rLng  = lng  ?: navPrefs.getString("last_nav_lng", "")?.toDoubleOrNull()
            val rAddr = address ?: navPrefs.getString("last_nav_addr", null)

            if (rLat != null && rLng != null) {
                val intent = Intent(Intent.ACTION_VIEW, "google.navigation:q=$rLat,$rLng".toUri()).apply { setPackage("com.google.android.apps.maps") }
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    val act = context as? Activity
                    if (act != null) act.startActivity(intent) else { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }
                    return
                }
                val geo = Intent(Intent.ACTION_VIEW, "geo:$rLat,$rLng?q=$rLat,$rLng(${Uri.encode(rAddr ?: "Incident")})".toUri())
                if (context.packageManager.resolveActivity(geo, 0) != null) { context.startActivity(geo); return }
            }
            if (!rAddr.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, "google.navigation:q=${Uri.encode(rAddr)}".toUri()).apply { setPackage("com.google.android.apps.maps") }
                if (context.packageManager.resolveActivity(intent, 0) != null) { context.startActivity(intent); return }
            }
            Toast.makeText(context, "No location available for navigation", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show() }
    }

    fun sendOnSceneReport(incident: Incident) {
        try {
            assignedVm.updateStatus(
                assignmentId = incident.id,
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
                .commit()

            assignedVm.load(responderId)
            assignedVm.loadActive(responderId)
            RouteHistoryStore.completeRoute(context, incident.id)

            scope.launch {
                try {
                    val response = RetrofitProvider.incidentApi.markRouteArrived(
                        MarkRouteArrivedRequest(
                            incident_id = incident.id.toIntOrNull() ?: 0,
                            responder_id = responderId
                        )
                    )

                    Log.d(
                        "LiveGPS",
                        "Route arrived save: success=${response.success}, message=${response.message}"
                    )

                } catch (e: Exception) {
                    Log.e(
                        "LiveGPS",
                        "Route arrived save failed: ${e.message}"
                    )
                }
            }

            context.stopService(
                Intent(context, RouteMonitoringService::class.java)
            )

            context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pending_en_route_check", false)
                .remove("pending_en_route_incident_id")
                .apply()

            fusedClient.removeLocationUpdates(onSceneLocationCallback)

            Toast.makeText(
                context,
                "On-scene reported to command",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) { Toast.makeText(context, "Failed to send on-scene report", Toast.LENGTH_SHORT).show() }
    }

    fun markIncidentDone(incident: Incident, notes: String, proofUri: String?) {
        try {
            assignedVm.updateStatus(
                assignmentId = incident.id,
                status = "completed",
                responderId = responderId
            )
            context.stopService(
                Intent(context, RouteMonitoringService::class.java)
            )

            context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pending_en_route_check", false)
                .remove("pending_en_route_incident_id")
                .commit()

            assignedVm.load(responderId)
            assignedVm.loadActive(responderId)

            if (lastNotifiedIncidentId == incident.id) {
                lastNotifiedIncidentId = null
                prefs.edit().remove("last_notified_incident_id").apply()
            }

            if (lastAssignedIncidentId == incident.id) {
                lastAssignedIncidentId = null
                prefs.edit().remove("last_assigned_incident_id").apply()
            }

            Toast.makeText(
                context,
                "Incident marked completed",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Failed to mark complete",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var showAlwaysLocationNotice by remember { mutableStateOf(false) }

    val backgroundLocationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showAlwaysLocationNotice = !granted
        Toast.makeText(context, if (granted) "Always-on location enabled" else "Please enable 'Allow all the time' in App settings", if (granted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    val locationSettingsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

            deviceLocationEnabled =
                isDeviceLocationEnabled(context)

            if (deviceLocationEnabled && hasLocationPermission) {

                showAlwaysLocationNotice = false

                isLocationMonitoringEnabled = true
                isLocationShared = true

                prefs.edit()
                    .putBoolean(locationMonitoringEnabledKey, true)
                    .apply()

                startLocationUpdates()
            }
        }

    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            hasLocationPermission = true
            if (isDeviceLocationEnabled(context)) {
                Toast.makeText(context, "Turn on Location to start live monitoring", Toast.LENGTH_LONG).show()
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            isLocationShared = true; isLocationMonitoringEnabled = true
            prefs.edit().putBoolean(locationMonitoringEnabledKey, true).apply()
            startLocationUpdates(); showAlwaysLocationNotice = !hasAlwaysLocationPermission()
        } else {
            hasLocationPermission = false
            isLocationShared = false; isLocationMonitoringEnabled = false
            prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val onScenePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startOnSceneTracking()
        else Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
    }



    fun sendBackupRequest(request: BackupRequest) {
        val deptName = when (request.department.shortCode) {
            "FIRE" -> "Fire Department"
            "MED" -> "Medical Department"
            "POL" -> "Police Department"
            else -> "Emergency Services"
        }

        val resourceList = if (request.isFullBackup) {
            "Full Backup"
        } else {
            request.resources.joinToString { it.label }
        }

        scope.launch {
            try {
                val repo = com.ers.emergencyresponseapp.data.IncidentRepository()

                val success = repo.sendBackupRequest(
                    responderId = responderId,
                    responderName = responderName,
                    department = effectiveRole ?: "",
                    requestedDepartment = deptName,
                    resources = resourceList,
                    isFullBackup = request.isFullBackup,
                    incidentId = request.fromIncidentId
                )

                if (success) {
                    Toast.makeText(
                        context,
                        "Backup request sent to $deptName",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Failed to send backup request",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val gpsEnabled = deviceLocationEnabled

    val gpsStatusText = when {
        gpsEnabled && hasLocationPermission && isLocationMonitoringEnabled -> "GPS Active"
        gpsEnabled && hasLocationPermission && !isLocationMonitoringEnabled -> "GPS Available"
        else -> "GPS Disabled"
    }

    val gpsStatusColor = when {
        gpsEnabled && hasLocationPermission && isLocationMonitoringEnabled -> Color(0xFF4CAF50)
        gpsEnabled && hasLocationPermission && !isLocationMonitoringEnabled -> Color(0xFFFFC107)
        else -> Color.Red
    }


    // ─────────────────────────────────────────────────────────────────────────
    // SCAFFOLD
    // ─────────────────────────────────────────────────────────────────────────
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {},
        floatingActionButton = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            if (showAlwaysLocationNotice) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEEF3)
                        ),
                        border = BorderStroke(
                            1.dp,
                            Color(0xFFFFC2D1)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Enable background location",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = AppColors.Text
                                )

                                Text(
                                    "Recommended for safer tracking.",
                                    fontSize = 11.sp,
                                    color = AppColors.TextSecondary
                                )
                            }

                            TextButton(
                                onClick = { showAlwaysLocationNotice = false }
                            ) {
                                Text("Later")
                            }

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            )
                                        )
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        backgroundLocationPermLauncher.launch(
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    "Open",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // DITO ILAGAY
            if (showNewIncidentNotification) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF263238)
                    ),
                    elevation = CardDefaults.cardElevation(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFF018786),
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = "New Incident Assigned",
                                color = Color(0xFF018786),
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = newIncidentMessage,
                                color = Color.White,
                                maxLines = 2
                            )
                        }

                        TextButton(
                            onClick = {
                                showNewIncidentNotification = false

                                scope.launch {
                                    listState.animateScrollToItem(1)
                                }
                            }
                        ) {
                            Text("View")
                        }

                        IconButton(
                            onClick = {
                                showNewIncidentNotification = false
                            }
                        ) {
                            Text(
                                "×",
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }


            // FIX 7: Use derivedStateOf for count values so recomposition only triggers
            // when the actual count changes, not on every activeIncidents reference change.
            val fireCount    by remember { derivedStateOf { activeIncidents.count { it.type == IncidentType.FIRE } } }
            val medicalCount by remember { derivedStateOf { activeIncidents.count { it.type == IncidentType.MEDICAL } } }
            val crimeCount   by remember { derivedStateOf { activeIncidents.count { it.type == IncidentType.CRIME } } }


            val assignedListForRole =
                assignedUi.incidents.map { it.toDomain() }

            assignedListForRole.firstOrNull()?.id?.let { id -> if (!onSceneEnabledMap.containsKey(id)) onSceneEnabledMap[id] = false }

            LaunchedEffect(assignedUi.incidents.firstOrNull()?.id) {
                val incident = assignedUi.incidents.firstOrNull() ?: return@LaunchedEffect

                if (lastShownApiAssignedId == incident.id) {
                    return@LaunchedEffect
                }

                lastShownApiAssignedId = incident.id
                prefs.edit()
                    .putString("last_shown_api_assigned_id", incident.id)
                    .apply()

                newIncidentMessage =
                    "New ${incident.type.uppercase()} incident assigned at ${incident.location}"

                notificationCount += 1
                showAssignedAfterNotification = false
                showNewIncidentNotification = true

                vibratePhone(context)

                showAssignedIncidentNotification(
                    context = context,
                    title = "New Incident Assigned",
                    message = newIncidentMessage
                )

                delay(5000L)

                showNewIncidentNotification = false
            }

            // FIX 8: Removed the duplicate outer `activeListVisible` that was shadowed
            // by an identical `remember` inside the LazyColumn item below.
            // There is now only ONE derivation, inside the item where it is used.



            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(
                    top = if (showAlwaysLocationNotice) 76.dp else 0.dp,
                    bottom = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                // HEADER
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth().height(155.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        // FIX 2: Use top-level HeaderBrush constant instead of inline Brush.verticalGradient
                        Box(modifier = Modifier.fillMaxSize().background(HeaderBrush)) {
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {

                                    Text(
                                        "Hello",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        responderName.ifBlank { "Responder" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        text = "${effectiveRole ?: "Responder"} • ${formatUnitStatus(unitStatus)}",
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 13.sp
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(gpsStatusColor)
                                        )

                                        Spacer(Modifier.width(6.dp))

                                        Text(
                                            text = when {
                                                !gpsEnabled -> "GPS Disabled • Tap to open settings"
                                                !hasLocationPermission -> "GPS Permission Needed"
                                                isLocationMonitoringEnabled -> "GPS Active • Tap to disable"
                                                else -> "GPS Available • Tap to enable"
                                            },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable {
                                                when {
                                                    !gpsEnabled -> {
                                                        locationSettingsLauncher.launch(
                                                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                                        )
                                                    }

                                                    !hasLocationPermission -> {
                                                        locationPermLauncher.launch(
                                                            Manifest.permission.ACCESS_FINE_LOCATION
                                                        )
                                                    }

                                                    isLocationMonitoringEnabled -> {
                                                        isLocationMonitoringEnabled = false
                                                        isLocationShared = false

                                                        prefs.edit()
                                                            .putBoolean(locationMonitoringEnabledKey, false)
                                                            .apply()

                                                        stopLocationUpdates()

                                                        Toast.makeText(
                                                            context,
                                                            "GPS monitoring disabled",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }

                                                    else -> {
                                                        isLocationMonitoringEnabled = true
                                                        isLocationShared = true

                                                        prefs.edit()
                                                            .putBoolean(locationMonitoringEnabledKey, true)
                                                            .apply()

                                                        startLocationUpdates()

                                                        Toast.makeText(
                                                            context,
                                                            "GPS monitoring enabled",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .clickable {
                                                showNotificationsDialog = true

                                                notificationCount = 0
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = Color.White
                                        )
                                    }

                                    Box(
                                        modifier = Modifier.size(70.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.18f))
                                                .clickable { showSettingsDialog = true }
                                                .padding(3.dp)
                                        ) {
                                            ResponderAvatar(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.65f),
                                                        CircleShape
                                                    ),
                                                imageUri = responderImageUri,
                                                drawableRes = responderDrawable,
                                                status = if (onlineStatus == ResponderOnlineStatus.Online)
                                                    ResponderOnlineStatus.Online
                                                else
                                                    ResponderOnlineStatus.Offline,
                                                contentDescription = "Open account settings"
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 1.dp, y = 1.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1976D2))
                                                .border(1.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "⚙",
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                item { Spacer(Modifier.height(4.dp)) }

                // ASSIGNED INCIDENTS
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Assigned Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (assignedListForRole.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(3.dp),
                                border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.14f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(Color(0xFFFFF3E0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalFireDepartment,
                                                contentDescription = null,
                                                tint = Color(0xFFEF6C00),
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = unitCode.ifBlank { "Unit not assigned" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp,
                                                color = AppColors.Text
                                            )

                                            Text(
                                                text = unitType.ifBlank { "Responder unit" },
                                                color = AppColors.TextSecondary,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF43A047))
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Text(
                                                text = "Available for Dispatch",
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "No assigned incident yet. You’ll be notified once the dispatch center assigns an incident.",
                                        color = AppColors.TextSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                        else {
                            assignedListForRole.forEach { inc ->
                                val diffMin   = ((System.currentTimeMillis() - inc.timeReported.time) / 60000).toInt()
                                val timeLabel = when { diffMin < 1 -> "just now"; diffMin < 60 -> "${diffMin}m ago"; diffMin < 1440 -> "${diffMin / 60}h ago"; else -> java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(inc.timeReported) }
                                val priorityColor = when (inc.priority) { IncidentPriority.HIGH -> Color(0xFFD32F2F); IncidentPriority.MEDIUM -> Color(0xFFFFA000); IncidentPriority.LOW -> Color(0xFFFFEB3B) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                                ) {
                                    Column(
                                        // FIX 2: Use stable top-level AssignedCardBrush constant
                                        modifier = Modifier.fillMaxWidth().background(AssignedCardBrush).padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // FIX 2: Use stable top-level AssignedBarBrush constant
                                            Box(modifier = Modifier.width(7.dp).height(54.dp).clip(RoundedCornerShape(99.dp)).background(AssignedBarBrush))
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(inc.type.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.Text)
                                                Spacer(Modifier.height(4.dp))
                                                Text(inc.location.ifBlank { "Unknown location" }, fontSize = 13.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(priorityColor.copy(0.12f)).border(1.dp, priorityColor.copy(0.6f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                Text(inc.priority.name.uppercase(), color = priorityColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(timeLabel, fontSize = 12.sp, color = AppColors.TextSecondary)
                                            Spacer(Modifier.width(10.dp))
                                            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AppColors.Secondary.copy(0.10f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                                Text(
                                                    text = when (inc.status.name.lowercase()) {
                                                        "reported" -> "Received"
                                                        "assigned" -> "Assigned"
                                                        "received" -> "Received"
                                                        "en_route" -> "En Route"
                                                        "on_scene" -> "On Scene"
                                                        "completed" -> "Completed"
                                                        else -> inc.status.displayName
                                                    },
                                                    fontSize = 12.sp,
                                                    color = AppColors.Secondary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        inc.description.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = AppColors.Text.copy(0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis) }

                                        Surface(
                                            color = Color(0xFFEFF5F5),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    "Assigned Unit",
                                                    fontSize = 11.sp,
                                                    color = AppColors.TextSecondary
                                                )

                                                Text(
                                                    "${unitCode.ifBlank { "Unit" }} • ${unitType.ifBlank { "Responder Unit" }}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppColors.Text,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        AssignedActionButtons(
                                            inc = inc, context = context, navController = navController,
                                            currentLatitude = currentLatitude, currentLongitude = currentLongitude,
                                            onSceneEnabled = (onSceneEnabledMap[inc.id] == true),
                                            setOnSceneEnabled = { e -> onSceneEnabledMap[inc.id] = e },
                                            setNavTarget = { id, lat, lng -> navDestinationIncidentId = id; navDestinationLat = lat; navDestinationLng = lng },
                                            startOnSceneTracking = { startOnSceneTracking() },
                                            requestOnScenePermission = { onScenePermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                            navigateToLocation = { lat, lng, addr -> navigateToLocation(lat, lng, addr) },
                                            sendOnSceneReport = { sendOnSceneReport(it) },
                                            openMarkDone = { markTargetIncidentInc = it; proofNotes = ""; selectedProofUri = null; showMarkCompleteDialog = true },
                                            onNavigateStatusUpdate = {
                                                assignedVm.updateStatus(
                                                    assignmentId = it.id,
                                                    status = "en_route",
                                                    responderId = responderId
                                                )
                                            }
                                        )

                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showDepartmentSelection = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.Primary
                            )
                        ) {
                            Icon(
                                Icons.Default.LocalHospital,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "Request Backup",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }

                // ACTIVE INCIDENTS HEADER + FILTERS
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    assignedVm.loadActive(responderId)
                                    Toast.makeText(context, "Refreshing incidents...", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh incidents",
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = if (isRefreshing) "Refreshing..." else "Refresh",
                                    color = AppColors.Primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            @Composable
                            fun chip(label: String, selected: Boolean, onClick: () -> Unit) {
                                OutlinedButton(
                                    onClick = onClick, shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) AppColors.Primary.copy(0.12f) else Color.Transparent, contentColor = if (selected) AppColors.Primary else AppColors.Text),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AppColors.Primary.copy(0.55f) else AppColors.Border)
                                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                            chip("All",    activeFilter == ActivePriorityFilter.ALL)    { activeFilter = ActivePriorityFilter.ALL }
                            chip("High",   activeFilter == ActivePriorityFilter.HIGH)   { activeFilter = ActivePriorityFilter.HIGH }
                            chip("Medium", activeFilter == ActivePriorityFilter.MEDIUM) { activeFilter = ActivePriorityFilter.MEDIUM }
                            chip("Low",    activeFilter == ActivePriorityFilter.LOW)    { activeFilter = ActivePriorityFilter.LOW }
                        }
                    }
                }

                // ACTIVE INCIDENTS LIST
                item {
                    // FIX 8: Single derivation with correct keys (activeIncidents + activeFilter).
                    // Previously this was computed twice — once as a stale outer var and once here.
                    val activeListVisible = remember(activeIncidents, activeFilter) {
                        activeIncidents.filter { it.type != IncidentType.DISASTER }.filter { inc ->
                            when (activeFilter) {
                                ActivePriorityFilter.ALL    -> true
                                ActivePriorityFilter.HIGH   -> inc.priority == IncidentPriority.HIGH
                                ActivePriorityFilter.MEDIUM -> inc.priority == IncidentPriority.MEDIUM
                                ActivePriorityFilter.LOW    -> inc.priority == IncidentPriority.LOW
                            }
                        }.sortedWith(incidentPriorityComparator)  // FIX 4: stable comparator
                    }

                    // FIX 10: Added a dedicated rememberLazyListState for the nested LazyColumn
                    // so Compose can reuse item positions across recompositions instead of
                    // treating it as a new list every time.
                    val activeListState = rememberLazyListState()
                    if (isLoading && activeIncidents.isEmpty()) {
                        Column (
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(3) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFEAEAEA)
                                    )
                                ) {}
                            }
                        }
                    } else if (activeListVisible.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(2.dp),
                            border = BorderStroke(
                                1.dp,
                                AppColors.Primary.copy(alpha = 0.16f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(AppColors.Primary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = null,
                                        tint = AppColors.Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = "No active incidents",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Text
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = "Other assigned incidents will appear here for awareness.",
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(16.dp))

                                Surface(
                                    color = AppColors.Primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "Monitoring dispatch updates",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        color = AppColors.Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 320.dp),
                            shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(0.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                        ) {
                            LazyColumn(
                                state = activeListState,  // FIX 10: pass stable state
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(
                                    items = activeListVisible,
                                    key = { it.id }  // FIX 11: stable item keys eliminate full re-layout on list changes
                                ) { inc ->
                                    val priorityColor = when (inc.priority) { IncidentPriority.HIGH -> Color(0xFFD32F2F); IncidentPriority.MEDIUM -> Color(0xFFFFA000); IncidentPriority.LOW -> Color(0xFF388E3C) }
                                    val accent        = when (inc.type)     { IncidentType.FIRE -> Color(0xFFE53935); IncidentType.MEDICAL -> Color(0xFF1E88E5); IncidentType.CRIME -> Color(0xFF6D4C41); IncidentType.DISASTER -> Color(0xFF8E24AA) }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true },
                                            onLongClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true }
                                        ),
                                        shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(0.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.width(6.dp).height(54.dp).clip(RoundedCornerShape(999.dp)).background(accent.copy(0.9f)))
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val incidentIcon = when (inc.type) {
                                                            IncidentType.FIRE -> Icons.Default.LocalFireDepartment
                                                            IncidentType.MEDICAL -> Icons.Default.LocalHospital
                                                            IncidentType.CRIME -> Icons.Default.Security
                                                            IncidentType.DISASTER -> Icons.Default.Warning
                                                        }

                                                        Icon(
                                                            imageVector = incidentIcon,
                                                            contentDescription = inc.type.displayName,
                                                            tint = accent,
                                                            modifier = Modifier.size(18.dp)
                                                        )

                                                        Spacer(Modifier.width(6.dp))

                                                        Text(
                                                            inc.type.displayName,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 16.sp,
                                                            color = AppColors.Text
                                                        )
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(inc.location.ifBlank { "Unknown location" }, fontSize = 13.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {

                                                    val isNewIncident =
                                                        (System.currentTimeMillis() - inc.timeReported.time) < 300000

                                                    if (isNewIncident) {

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(Color(0xFF4CAF50))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                "NEW",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(priorityColor.copy(0.12f))
                                                            .border(
                                                                1.dp,
                                                                priorityColor.copy(0.55f),
                                                                RoundedCornerShape(999.dp)
                                                            )
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            inc.priority.name.uppercase(),
                                                            color = priorityColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Reported ${timeAgoLabel(inc.timeReported)}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                            }
                                            Text(inc.description, fontSize = 13.sp, color = AppColors.Text.copy(0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "View details",
                                                color = AppColors.Primary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }

                // COUNTS
                item {
                    // FIX 3: Use stable top-level brush lists instead of inline Brush.* calls.
                    // FIX 9: Static label/icon/accent lists moved to remember{} so they are not
                    // re-allocated on every recomposition of this item.
                    val typeLabels  = remember { listOf("Fire", "Medical", "Crime") }
                    val typeAccents = remember { listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF6D4C41)) }
                    val typeIcons   = remember { listOf(Icons.Default.LocalFireDepartment, Icons.Default.LocalHospital, Icons.Default.Security) }
                    // typeCounts references derivedStateOf vars — no extra remember needed
                    val typeCounts  = listOf(fireCount, medicalCount, crimeCount)

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeAccents.forEachIndexed { i, accent ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.13f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBrushesStable[i])
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accent.copy(0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            typeIcons[i],
                                            typeLabels[i],
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Column {
                                        Text(
                                            typeCounts[i].toString(),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp,
                                            color = AppColors.Text
                                        )

                                        Text(
                                            typeLabels[i],
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } // end LazyColumn

            if (showCancelRouteDialog && pendingRouteIncidentId != null) {
                Dialog(
                    onDismissRequest = { showCancelRouteDialog = false }
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
                                imageVector = Icons.Default.Navigation,
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
                                text = "We detected that you exited navigation.\nAre you still responding to this incident?",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 15.sp
                            )

                            Spacer(Modifier.height(24.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val id = pendingRouteIncidentId ?: return@OutlinedButton

                                        assignedVm.updateStatus(
                                            assignmentId = id,
                                            status = "received",
                                            responderId = responderId
                                        )

                                        showCancelRouteDialog = false

                                        context.stopService(
                                            Intent(context, RouteMonitoringService::class.java)
                                        )

                                        fusedClient.removeLocationUpdates(onSceneLocationCallback)

                                        context.getSharedPreferences(
                                            "nav_prefs",
                                            Context.MODE_PRIVATE
                                        )
                                            .edit()
                                            .putBoolean("pending_en_route_check", false)
                                            .remove("pending_en_route_incident_id")
                                            .commit()
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
                                        showCancelRouteDialog = false

                                        context.getSharedPreferences(
                                            "nav_prefs",
                                            Context.MODE_PRIVATE
                                        )
                                            .edit()
                                            .remove("pending_en_route_check")
                                            .remove("pending_en_route_incident_id")
                                            .apply()
                                    }
                                ) {
                                    Text("Continue")
                                }
                            }
                        }
                    }
                }
            }

            // ── ALL ACTIVE DIALOG ──
            if (showAllActiveDialog) {
                AlertDialog(
                    onDismissRequest = { showAllActiveDialog = false },
                    title = { Text("All Active Incidents") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                items = activeIncidents.filter { it.type != IncidentType.DISASTER },
                                key = { it.id }  // FIX 11: stable keys
                            ) { inc ->
                                val priorityColor = when (inc.priority) { IncidentPriority.HIGH -> Color(0xFFD32F2F); IncidentPriority.MEDIUM -> Color(0xFFFFA000); IncidentPriority.LOW -> Color(0xFF388E3C) }
                                Card(
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true; showAllActiveDialog = false },
                                        onLongClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true; showAllActiveDialog = false }
                                    ),
                                    shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp), colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(inc.type.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(priorityColor.copy(0.12f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text(inc.priority.name, color = priorityColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp)); Text(inc.location.ifBlank { "Unknown location" })
                                        Spacer(Modifier.height(6.dp)); Text(inc.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(6.dp)); Text(timeAgoLabel(inc.timeReported), fontSize = 12.sp, color = AppColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showAllActiveDialog = false }) { Text("Close") } }
                )
            }

            // ── MARK COMPLETE DIALOG ──
            if (showMarkCompleteDialog && markTargetIncidentInc != null) {
                val hasProof = selectedProofUri != null

                AlertDialog(
                    onDismissRequest = {
                        showMarkCompleteDialog = false
                        markTargetIncidentInc = null
                    },
                    title = {
                        Text(
                            "Complete Incident",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Photo proof is required. Completion notes are optional.",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp
                            )

                            OutlinedTextField(
                                value = proofNotes,
                                onValueChange = { proofNotes = it },
                                label = { Text("Completion notes (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )

                            Button(
                                onClick = {
                                    takePictureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(if (hasProof) "Retake Photo" else "Take Required Photo")
                            }

                            if (!hasProof) {
                                Text(
                                    "Required before submitting",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 12.sp
                                )
                            }

                            selectedProofUri?.let { uriStr ->
                                val bitmap = try {
                                    val opts = BitmapFactory.Options().apply {
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }

                                    if (uriStr.startsWith("file://")) {
                                        Uri.parse(uriStr).path?.let {
                                            BitmapFactory.decodeFile(it, opts)
                                        }
                                    } else {
                                        context.contentResolver
                                            .openInputStream(Uri.parse(uriStr))
                                            ?.use { BitmapFactory.decodeStream(it, null, opts) }
                                    }
                                } catch (_: Exception) {
                                    null
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Proof photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                1.dp,
                                                AppColors.Border,
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        "Photo captured ✓",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val inc = markTargetIncidentInc
                                if (inc != null) {
                                    markIncidentDone(
                                        inc,
                                        proofNotes,
                                        selectedProofUri
                                    )
                                }

                                showMarkCompleteDialog = false
                                markTargetIncidentInc = null
                                proofNotes = ""
                                selectedProofUri = null
                            },
                            enabled = hasProof,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showMarkCompleteDialog = false
                                markTargetIncidentInc = null
                                proofNotes = ""
                                selectedProofUri = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showNotificationsDialog) {

                val notificationItems = buildList {
                    assignedListForRole.firstOrNull()?.let { inc ->
                        add("New assigned ${inc.type.displayName} incident at ${inc.location}")
                        add("Assigned unit: ${unitCode.ifBlank { "Unit" }} • ${unitType.ifBlank { "Responder Unit" }}")
                    }

                    if (activeIncidents.isNotEmpty()) {
                        add("${activeIncidents.size} active incident${if (activeIncidents.size > 1) "s" else ""} being monitored")
                    }

                    if (!gpsEnabled) {
                        add("GPS is disabled. Enable GPS for safer responder tracking.")
                    } else if (!isLocationMonitoringEnabled) {
                        add("GPS is available but monitoring is not active.")
                    }
                }

                AlertDialog(
                    onDismissRequest = { showNotificationsDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            "Notifications",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (notificationItems.isEmpty()) {
                                Text(
                                    "No new notifications.",
                                    color = AppColors.TextSecondary
                                )
                            } else {
                                notificationItems.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = AppColors.Primary,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(Modifier.width(8.dp))

                                        Text(
                                            text = item,
                                            color = AppColors.Text,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                notificationCount = 0
                                showNotificationsDialog = false
                            }
                        ) {
                            Text("Close")
                        }
                    }
                )
            }

            // ── ACTIVE DETAILS SHEET ──
            if (showActiveDetailsSheet && selectedActiveIncident != null) {
                val inc = selectedActiveIncident!!

                val accent = when (inc.type) {
                    IncidentType.FIRE -> Color(0xFFE53935)
                    IncidentType.MEDICAL -> Color(0xFF1E88E5)
                    IncidentType.CRIME -> Color(0xFF6D4C41)
                    IncidentType.DISASTER -> Color(0xFF8E24AA)
                }

                val incidentIcon = when (inc.type) {
                    IncidentType.FIRE -> Icons.Default.LocalFireDepartment
                    IncidentType.MEDICAL -> Icons.Default.LocalHospital
                    IncidentType.CRIME -> Icons.Default.Security
                    IncidentType.DISASTER -> Icons.Default.Warning
                }

                val priorityColor = when (inc.priority) {
                    IncidentPriority.HIGH -> Color(0xFFD32F2F)
                    IncidentPriority.MEDIUM -> Color(0xFFFFA000)
                    IncidentPriority.LOW -> Color(0xFF388E3C)
                }

                ModalBottomSheet(
                    onDismissRequest = {
                        showActiveDetailsSheet = false
                        selectedActiveIncident = null
                    },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    incidentIcon,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${inc.type.displayName} Incident",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Text
                                )

                                Text(
                                    "Reported ${timeAgoLabel(inc.timeReported)}",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(priorityColor.copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        priorityColor.copy(alpha = 0.55f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    inc.priority.name.uppercase(),
                                    color = priorityColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF7F7F7)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Location",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Text
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = AppColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    Text(
                                        inc.location.ifBlank { "Unknown location" },
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF7F7F7)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Description",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Text
                                )

                                Text(
                                    inc.description.ifBlank { "No description provided." },
                                    color = AppColors.Text.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    openMapPin(
                                        context,
                                        inc.latitude,
                                        inc.longitude,
                                        inc.location
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                Text("View Location")
                            }

                            Button(
                                onClick = {
                                    showActiveDetailsSheet = false
                                    selectedActiveIncident = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Primary
                                )
                            ) {
                                Text("Close")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ── DEPARTMENT SELECTION DIALOG ──
            if (showDepartmentSelection) {
                DepartmentSelectionDialog(
                    onDismiss = { showDepartmentSelection = false },
                    onDepartmentSelected = { showDepartmentSelection = false },
                    onBackupRequestReady = { request ->
                        sendBackupRequest(request)
                        showDepartmentSelection = false
                    }
                )
            }

            // ── SETTINGS DIALOG ──
            if (showSettingsDialog) {
                AccountSettingsDialog(
                    fullName = accountFullName, username = accountUsername, email = accountEmail,
                    photoUri = accountPhotoUri, isDarkMode = isDarkMode,
                    onFullNameChange = { accountFullName = it }, onUsernameChange = { accountUsername = it }, onEmailChange = { accountEmail = it },
                    onDarkModeChange = { e -> isDarkMode = e; prefs.edit().putBoolean("dark_mode", e).apply() },
                    onPickPhoto = { pickProfilePhotoLauncher.launch("image/*") },
                    onSave = {
                        prefs.edit().putString("account_full_name", accountFullName.trim()).putString("account_username", accountUsername.trim()).putString("account_email", accountEmail.trim()).putString("account_photo", accountPhotoUri).apply()
                        if (accountUsername.isNotBlank()) responderName = accountUsername.trim()
                        showSettingsDialog = false
                    },
                    onBack = { showSettingsDialog = false },
                    onLogout = { Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show(); showSettingsDialog = false; onLogout() }
                )
            }

            // ── NEW INCIDENT NOTIFICATION ──
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showNewIncidentNotification,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit  = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.95f).padding(top = 16.dp).combinedClickable(onClick = { showNewIncidentNotification = false }, onLongClick = { showNewIncidentNotification = false }),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)), elevation = CardDefaults.cardElevation(0.dp), shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocalHospital, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("New Incident Assigned!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(newIncidentMessage, color = Color.White.copy(0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { showNewIncidentNotification = false }) {
                                Icon(Icons.Default.Done, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ACCOUNT SETTINGS DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountSettingsDialog(
    fullName: String, username: String, email: String, photoUri: String?,
    isDarkMode: Boolean,
    onFullNameChange: (String) -> Unit, onUsernameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit, onPickPhoto: () -> Unit,
    onSave: () -> Unit, onBack: () -> Unit, onLogout: () -> Unit
) {
    var showProfilePreview by remember { mutableStateOf(false) }

    if (showProfilePreview) {
        AlertDialog(
            onDismissRequest = { showProfilePreview = false },
            title = { Text("Profile Photo", fontWeight = FontWeight.SemiBold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(240.dp).clip(CircleShape).border(1.dp, AppColors.Border, CircleShape)) {
                        ResponderAvatar(modifier = Modifier.fillMaxSize(), imageUri = photoUri, status = ResponderOnlineStatus.Offline)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProfilePreview = false }) { Text("Close") } }
        )
    }

    AlertDialog(
        onDismissRequest = onBack,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFFF7F5F9),
        title = { Text("Account Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(88.dp).clip(CircleShape).border(1.dp, AppColors.Border, CircleShape).clickable { showProfilePreview = true }) {
                            ResponderAvatar(modifier = Modifier.fillMaxSize(), imageUri = photoUri, status = ResponderOnlineStatus.Offline)
                        }
                        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(30.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(AppColors.Primary), contentAlignment = Alignment.Center) {
                            IconButton(onClick = onPickPhoto, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = fullName,  onValueChange = onFullNameChange, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username,  onValueChange = onUsernameChange, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email,     onValueChange = onEmailChange,    label = { Text("Email") },    singleLine = true, modifier = Modifier.fillMaxWidth())
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Night mode", fontWeight = FontWeight.SemiBold, color = AppColors.Text); Text("Reduce glare in low light", fontSize = 12.sp, color = AppColors.TextSecondary) }
                        Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                    }
                }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.35f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Logout", fontWeight = FontWeight.SemiBold, color = AppColors.Text); Text("Sign out of this device", fontSize = 12.sp, color = AppColors.TextSecondary) }
                        TextButton(onClick = onLogout) { Text("Logout", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary), modifier = Modifier.height(44.dp)) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp)) { Text("Cancel") }
        }
    )
}

private fun vibratePhone(context: Context) {
    val vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(
                    0,
                    300,
                    150,
                    300,
                    150,
                    500
                ),
                -1
            )
        )
    } else {
        vibrator.vibrate(1200)
    }
}
private fun showAssignedIncidentNotification(
    context: Context,
    title: String,
    message: String
) {

    val intent =
        Intent(context, MainActivity::class.java)

    intent.flags =
        Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP

    val pendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

    val notification =
        NotificationCompat.Builder(
            context,
            "emergency_incidents"
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        NotificationManagerCompat
            .from(context)
            .notify(
                System.currentTimeMillis().toInt(),
                notification
            )
    }
}