@file:OptIn(ExperimentalFoundationApi::class)
package com.ers.emergencyresponseapp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ers.emergencyresponseapp.analytics.RouteHistoryStore
import com.ers.emergencyresponseapp.features.assigned.AssignedIncidentsViewModel
import com.ers.emergencyresponseapp.features.assigned.toDomain
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.home.composables.BackupRequest
import com.ers.emergencyresponseapp.home.composables.DepartmentSelectionDialog
import com.ers.emergencyresponseapp.network.MarkRouteArrivedRequest
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.stringToRequestBody
import com.ers.emergencyresponseapp.network.uriStringToMultipartPart
import com.ers.emergencyresponseapp.network.uriToProfileImagePart
import com.ers.emergencyresponseapp.network.userIdToRequestBody
import com.ers.emergencyresponseapp.routing.RouteMonitoringService
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import androidx.core.content.FileProvider



// ─────────────────────────────────────────────────────────────────────────────
// APP COLORS
// ─────────────────────────────────────────────────────────────────────────────

private object AppColors {
    val Primary: Color get() = Color(0xFF4C8A89)
    val Secondary: Color get() = Color(0xFF3A506B)
    val Tertiary: Color get() = Color(0xFF1C2541)
    val Dark: Color get() = Color(0xFF0B132B)
    val Text: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFFAFAFA) else Color(0xFF171717)
    val TextSecondary: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFA1A1AA) else Color(0xFF575757)
    val Border: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF27272A) else Color(0xFFE5E5E5)
    val Bg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)
    val CardBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val HeaderBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val FooterBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF16181D) else Color(0xFFFAFAFA)

}


// FIX 2: Hoist stable Brush objects to top-level constants so they are never
// recreated during recomposition. Brushes are immutable value types — making
// them top-level is safe and eliminates per-frame allocation.
// Replace these `private val` brushes:
private fun headerBrush() = Brush.verticalGradient(
    listOf(AppColors.Primary, AppColors.Secondary, AppColors.Tertiary)
)
private fun assignedCardBrush() = Brush.verticalGradient(
    listOf(AppColors.CardBg, AppColors.Primary.copy(0.04f))
)
private fun assignedBarBrush() = Brush.verticalGradient(
    listOf(AppColors.Primary, AppColors.Secondary)
)

// FIX 3: Pre-compute per-type accent brushes as stable top-level objects.
// Previously these were created inside items{} lambdas on every scroll frame.
private fun fireCardBrush()    = Brush.verticalGradient(listOf(Color(0xFFE53935).copy(0.07f), AppColors.CardBg))
private fun medicalCardBrush() = Brush.verticalGradient(listOf(Color(0xFF1E88E5).copy(0.07f), AppColors.CardBg))
private fun crimeCardBrush()   = Brush.verticalGradient(listOf(Color(0xFF6D4C41).copy(0.07f), AppColors.CardBg))

private fun fireBarBrush()     = Brush.horizontalGradient(listOf(Color(0xFFE53935).copy(0.4f), Color(0xFFE53935)))
private fun medicalBarBrush()  = Brush.horizontalGradient(listOf(Color(0xFF1E88E5).copy(0.4f), Color(0xFF1E88E5)))
private fun crimeBarBrush()    = Brush.horizontalGradient(listOf(Color(0xFF6D4C41).copy(0.4f), Color(0xFF6D4C41)))

private fun cardBrushesStable() = listOf(fireCardBrush(), medicalCardBrush(), crimeCardBrush())
private fun barBrushesStable()  = listOf(fireBarBrush(),  medicalBarBrush(),  crimeBarBrush())


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

private fun saveUriToAppStorage(ctx: Context, uri: Uri, userId: Int): String? {
    return try {
        val dir = File(ctx.filesDir, "profile_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "profile_$userId.jpg")
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

private fun isOlderThan24Hours(dateString: String?): Boolean {
    if (dateString.isNullOrBlank()) return false
    return try {
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
        var parsedDate: java.util.Date? = null
        for (pattern in formats) {
            try {
                parsedDate = java.text.SimpleDateFormat(pattern, Locale.getDefault()).parse(dateString)
                if (parsedDate != null) break
            } catch (_: Exception) { }
        }
        val date = parsedDate ?: return false
        val diffMs = System.currentTimeMillis() - date.time
        diffMs > (24 * 60 * 60 * 1000L)
    } catch (e: Exception) {
        false
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

    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.Center) {
        when {
            drawableRes != null -> Image(
                painter = painterResource(id = drawableRes),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            imageUri != null -> {
                var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(imageUri) {
                    bitmap = try {
                        when {
                            imageUri.startsWith("http", ignoreCase = true) -> {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    java.net.URL(imageUri).openStream().use { BitmapFactory.decodeStream(it) }
                                }
                            }
                            imageUri.startsWith("file://") -> {
                                Uri.parse(imageUri).path?.let { BitmapFactory.decodeFile(it) }
                            }
                            else -> {
                                context.contentResolver.openInputStream(imageUri.toUri())
                                    ?.use { BitmapFactory.decodeStream(it) }
                            }
                        }
                    } catch (_: Exception) { null }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
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
    // AssignedActionButtons signature
    navigateToLocation: (Double?, Double?, String?, String?, String?) -> Unit,
    sendOnSceneReport: (Incident) -> Unit,
    hasLocationPermission: Boolean,
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
                if (hasLocationPermission)
                    startOnSceneTracking()
                else
                    requestOnScenePermission()
                setOnSceneEnabled(false)

                // inside AssignedActionButtons's "Navigate to Incident" onClick
                navigateToLocation(inc.latitude, inc.longitude, inc.location, inc.id, inc.assignmentId)
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

        Button(
            onClick = { openMarkDone(inc) },
            modifier = Modifier
                .fillMaxWidth()
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




// ─────────────────────────────────────────────────────────────────────────────
// EMERGENCY REQUEST CARD  (incoming backup requests)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackupRequestStatusCard(
    department: String,
    resources: String,
    status: String,
    onCancelClick: () -> Unit,
    onRefreshClick: (() -> Unit)? = null
) {
    val steps = listOf("Sent", "Review", "Approved")
    val currentStepIndex = when (status) {
        "pending"   -> 1
        "accepted"  -> 2
        "en_route"  -> 2
        "completed" -> 2
        else        -> 0
    }
    val isDeclined  = status == "declined"
    val isCancelled = status == "cancelled"

    val (badgeText, badgeColor) = when (status) {
        "pending"   -> "Pending" to Color(0xFFEF6C00)
        "accepted"  -> "Accepted" to Color(0xFF2E7D32)
        "en_route"  -> "En Route" to Color(0xFF1E88E5)
        "completed" -> "Completed" to Color(0xFF2E7D32)
        "declined"  -> "Declined" to Color(0xFFD32F2F)
        "cancelled" -> "Cancelled" to AppColors.TextSecondary
        else        -> status.replaceFirstChar { it.uppercase() } to AppColors.TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
        border = BorderStroke(1.dp, if (status == "pending") AppColors.Primary.copy(alpha = 0.35f) else AppColors.Border)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(AppColors.Primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(department, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.Text)
                    Text(resources, fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                if (onRefreshClick != null) {
                    IconButton(onClick = onRefreshClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status", tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isDeclined || isCancelled) {
                Text(
                    if (isDeclined) "Declined by dispatch" else "Request cancelled",
                    color = if (isDeclined) Color(0xFFD32F2F) else AppColors.TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Dots + connector track
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        steps.forEachIndexed { index, _ ->
                            val isDone = index <= currentStepIndex
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isDone) AppColors.Primary else AppColors.Border)
                            )
                            if (index < steps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.dp)
                                        .background(if (index < currentStepIndex) AppColors.Primary else AppColors.Border)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Labels aligned under each dot
                    Row(modifier = Modifier.fillMaxWidth()) {
                        steps.forEachIndexed { index, label ->
                            val isDone = index <= currentStepIndex
                            Text(
                                label,
                                fontSize = 9.sp,
                                fontWeight = if (index == currentStepIndex) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isDone) AppColors.Primary else AppColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                                textAlign = when (index) {
                                    0 -> TextAlign.Start
                                    steps.size - 1 -> TextAlign.End
                                    else -> TextAlign.Center
                                }
                            )
                        }
                    }
                }
            }

            if (status == "pending") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "Cancel",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onCancelClick() }
                    )
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
    var resumedFromBackground by remember { mutableStateOf(false) }
    var wasGpsEnabled by remember {
        mutableStateOf(isDeviceLocationEnabled(context))
    }
    var deviceLocationEnabled by remember {
        mutableStateOf(isDeviceLocationEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLocationMonitoringEnabled by remember { mutableStateOf(false) }
    val storedPrefs      = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val storedDepartment = storedPrefs.getString("department", null)
    val responderId = storedPrefs.getString("user_id", "")?.toIntOrNull() ?: 0
    val unitCode = storedPrefs.getString("unit_code", "") ?: ""
    val unitType = storedPrefs.getString("unit_type", "") ?: ""
    var unitStatus by remember {
        mutableStateOf(storedPrefs.getString("unit_status", "available") ?: "available")
    }

    LaunchedEffect(responderId) {
        if (responderId <= 0) {
            return@LaunchedEffect
        }

        val repo =
            com.ers.emergencyresponseapp.data.IncidentRepository()

        val latestUnitStatus = repo.setUnitPresence(
            responderId = responderId,
            presence = "online"
        )

        if (!latestUnitStatus.isNullOrBlank()) {
            unitStatus = latestUnitStatus

            storedPrefs.edit()
                .putString("unit_status", latestUnitStatus)
                .apply()
        }
    }


    val assignedUi by assignedVm.ui.collectAsState()
    LaunchedEffect(assignedUi.incidents) {
        AppScreenTracker.currentScreen = "HOME"
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
    var showAllBackupRequestsDialog by remember { mutableStateOf(false) }
    var showCancelBackupConfirm by remember { mutableStateOf(false) }
    var pendingCancelBackupId by remember { mutableStateOf<Int?>(null) }
    var backupSearchQuery by remember { mutableStateOf("") }
    var lastBackupUpdateTime by remember { mutableStateOf(java.util.Date()) }
    var activeFilter            by remember { mutableStateOf(ActivePriorityFilter.ALL) }
    var backupRequestsList by remember { mutableStateOf<List<com.ers.emergencyresponseapp.network.MyBackupRequestDto>>(emptyList()) }
    var dismissedBackupIds by remember {
        mutableStateOf(
            (prefs.getStringSet("dismissed_backup_request_ids", emptySet()) ?: emptySet())
                .mapNotNull { it.toIntOrNull() }
                .toMutableSet()
        )
    }

    LaunchedEffect(responderId) {
        if (responderId <= 0) return@LaunchedEffect
        val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
        while (true) {
            backupRequestsList = repo.getMyBackupRequests(responderId)
            lastBackupUpdateTime = java.util.Date()
            delay(5000)
        }
    }

    val visibleBackupRequests = remember(backupRequestsList, dismissedBackupIds) {
        backupRequestsList.filter { req ->
            val manuallyDismissed = req.id in dismissedBackupIds
            val autoHiddenAsOldCancelled = req.status == "cancelled" && isOlderThan24Hours(req.updated_at)
            !manuallyDismissed && !autoHiddenAsOldCancelled
        }
    }

// Count of extra requests (beyond the one shown on Home) that are still unresolved
    val pendingExtraBackupCount = remember(visibleBackupRequests) {
        visibleBackupRequests.drop(1).count { req ->
            req.status !in setOf("completed", "declined", "cancelled")
        }
    }

    // Mark-complete state
    var showMarkCompleteDialog by remember { mutableStateOf(false) }
    var markTargetIncidentInc  by remember { mutableStateOf<Incident?>(null) }
    var proofNotes             by remember { mutableStateOf("") }
    var selectedProofUri       by remember { mutableStateOf<String?>(null) }

    var pendingCameraUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var pendingCameraFile by remember {
        mutableStateOf<File?>(null)
    }

    var notificationCount by remember { mutableStateOf(0) }


    val takePictureLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            if (success) {
                val file = pendingCameraFile

                if (file != null && file.exists() && file.length() > 0L) {
                    selectedProofUri = Uri.fromFile(file).toString()

                    Log.d(
                        "CompletionPhoto",
                        "Full-resolution photo saved: " +
                                "path=${file.absolutePath}, " +
                                "size=${file.length()} bytes"
                    )
                } else {
                    selectedProofUri = null

                    Toast.makeText(
                        context,
                        "Photo was not saved correctly.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                pendingCameraFile?.delete()
                pendingCameraFile = null
                pendingCameraUri = null
            }
        }

    fun openCompletionCamera() {
        try {
            val imageDirectory = File(
                context.cacheDir,
                "completion_photos"
            ).apply {
                if (!exists()) {
                    mkdirs()
                }
            }

            val imageFile = File.createTempFile(
                "completion_${System.currentTimeMillis()}_",
                ".jpg",
                imageDirectory
            )

            val cameraUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            pendingCameraFile = imageFile
            pendingCameraUri = cameraUri

            takePictureLauncher.launch(cameraUri)

        } catch (e: Exception) {
            Log.e(
                "CompletionPhoto",
                "Unable to open camera",
                e
            )

            Toast.makeText(
                context,
                "Unable to open camera: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val rawActiveIncidents = assignedUi.activeIncidents.map { it.toDomain() }
    var stableActiveIncidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var activeEmptyStreak by remember { mutableStateOf(0) }

    LaunchedEffect(rawActiveIncidents) {
        if (rawActiveIncidents.isNotEmpty()) {
            activeEmptyStreak = 0
            stableActiveIncidents = rawActiveIncidents
        } else {
            activeEmptyStreak++
            // Only treat as "truly empty" after 2 consecutive empty polls (~10s),
            // so a single transient/glitchy empty response doesn't flicker the UI.
            if (activeEmptyStreak >= 2) {
                stableActiveIncidents = emptyList()
            }
        }
    }

    val activeIncidents = stableActiveIncidents
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun cancelBackupRequest(id: Int) {
        scope.launch {
            val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
            val result = repo.cancelBackupRequest(requestId = id, responderId = responderId)

            result.onSuccess {
                backupRequestsList = repo.getMyBackupRequests(responderId)
                lastBackupUpdateTime = java.util.Date()
                Toast.makeText(context, "Backup request cancelled", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Failed to cancel: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshSingleBackupRequest(requestId: Int) {
        scope.launch {
            val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
            val updated = repo.getBackupRequestStatus(requestId, responderId)

            if (updated != null) {
                backupRequestsList = backupRequestsList.map { existing ->
                    if (existing.id == updated.id) {
                        existing.copy(
                            status = updated.status,
                            resources = updated.resources,
                            requested_department = updated.requested_department,
                            is_full_backup = updated.is_full_backup,
                            updated_at = updated.updated_at
                        )
                    } else existing
                }
                lastBackupUpdateTime = java.util.Date()
            } else {
                Toast.makeText(context, "Unable to refresh this request", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickProfilePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val stored = saveUriToAppStorage(context, uri, responderId) ?: uri.toString()
            accountPhotoUri = stored
            try { prefs.edit().putString("account_photo", accountPhotoUri).apply() } catch (_: Exception) {}

            // Upload to server so profile_image_path gets updated in the DB
            if (responderId > 0) {
                scope.launch {
                    try {
                        val userIdBody = userIdToRequestBody(responderId)
                        val imagePart = uriToProfileImagePart(context, uri)

                        val response = RetrofitProvider.authApi.uploadProfileImage(userIdBody, imagePart)

                        if (response.success) {
                            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, response.message ?: "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Profile image upload failed: ${e.message}")
                        Toast.makeText(context, "Failed to upload profile photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


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
    var showLocationRationale by remember { mutableStateOf(false) }
    var showCriticalGpsWarning by remember { mutableStateOf(false) }
    var hasPromptedLocationOnce by remember {
        mutableStateOf(prefs.getBoolean("location_permission_prompted", false))
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission && !hasPromptedLocationOnce) {
            showLocationRationale = true
        }
    }
    var currentLatitude  by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }
    val fusedClient       = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { loc ->
                    currentLatitude = loc.latitude
                    currentLongitude = loc.longitude

                    // Only push idle presence when NOT actively en route —
                    // RouteMonitoringService already owns the node during navigation.
                    if (!RouteMonitoringService.isRunning && responderId > 0) {
                        val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("live_locations")
                            .child("responder_$responderId")
                        dbRef.updateChildren(
                            mapOf(
                                "responderId" to responderId.toString(),
                                "responderName" to responderName,
                                "department" to (effectiveRole ?: ""),
                                "unitCode" to unitCode,
                                "unitType" to unitType,
                                "lat" to loc.latitude,
                                "lng" to loc.longitude,
                                "heading" to loc.bearing,
                                "speed" to loc.speed,
                                "status" to "available",
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        dbRef.onDisconnect().updateChildren(mapOf("status" to "offline"))
                    }
                }
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

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {

                resumedFromBackground = true

                if (!isDeviceLocationEnabled(context)) {

                    showLocationRationale = true

                    showCriticalGpsWarning = false

                }

                resumedFromBackground = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }



    // ── Location Helper Functions ──
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

    LaunchedEffect(Unit) {
        while (true) {

            val enabledNow = isDeviceLocationEnabled(context)

            // GPS turned OFF habang nasa loob ng app
            if (wasGpsEnabled && !enabledNow && !resumedFromBackground) {
                showCriticalGpsWarning = true
                showLocationRationale = false
            }

            // GPS turned back ON
            if (!wasGpsEnabled && enabledNow) {
                showCriticalGpsWarning = false
            }

            wasGpsEnabled = enabledNow
            deviceLocationEnabled = enabledNow

            delay(1000)
        }
    }


    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {

                resumedFromBackground = true

                if (!isDeviceLocationEnabled(context)) {
                    showLocationRationale = true
                    showCriticalGpsWarning = false
                }

                resumedFromBackground = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    fun navigateToLocation(
        lat: Double?,
        lng: Double?,
        address: String?,
        incidentId: String? = null,
        assignmentId: String? = null,
        viewOnly: Boolean = false
    ) {
        val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)

        if (lat != null && lng != null) {
            navPrefs.edit()
                .putString("last_nav_lat", lat.toString())
                .putString("last_nav_lng", lng.toString())
                .putString("last_nav_addr", address)
                .apply()
            navController.navigate(
                "live_map/$lat/$lng/${Uri.encode(address ?: "")}" +
                        "?incidentId=${Uri.encode(incidentId ?: "")}" +
                        "&assignmentId=${Uri.encode(assignmentId ?: "")}" +
                        "&responderId=$responderId&viewOnly=$viewOnly"
            )
            return
        }

        val rLat = navPrefs.getString("last_nav_lat", "")?.toDoubleOrNull()
        val rLng = navPrefs.getString("last_nav_lng", "")?.toDoubleOrNull()
        val rAddr = address ?: navPrefs.getString("last_nav_addr", null)

        if (rLat != null && rLng != null) {
            navController.navigate(
                "live_map/$rLat/$rLng/${Uri.encode(rAddr ?: "")}" +
                        "?incidentId=${Uri.encode(incidentId ?: "")}" +
                        "&assignmentId=${Uri.encode(assignmentId ?: "")}" +
                        "&responderId=$responderId&viewOnly=$viewOnly"
            )
            return
        }

        Toast.makeText(context, "No location available for navigation", Toast.LENGTH_SHORT).show()
    }

    fun sendOnSceneReport(incident: Incident) {
        try {
            // sendOnSceneReport(incident)
            assignedVm.updateStatus(
                assignmentId = incident.assignmentId ?: incident.id,
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

                    Toast.makeText(
                        context,
                        if (response.success) "On-scene reported to command"
                        else response.message ?: "Arrival already recorded",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {
                    Log.e("LiveGPS", "Route arrived save failed: ${e.message}")
                }
            }

            context.stopService(
                Intent(context, RouteMonitoringService::class.java)
            )

            context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("pending_en_route_check", false)
                .remove("pending_en_route_incident_id")
                .commit()

            fusedClient.removeLocationUpdates(onSceneLocationCallback)

        } catch (e: Exception) { Toast.makeText(context, "Failed to send on-scene report", Toast.LENGTH_SHORT).show() }
    }

    fun markIncidentDone(incident: Incident, notes: String, proofUri: String?) {
        if (proofUri == null) {
            Toast.makeText(context, "Photo proof is required", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                // markIncidentDone(...)
                val assignmentIdBody = stringToRequestBody(incident.assignmentId ?: incident.id)
                val responderIdBody = stringToRequestBody(responderId.toString())
                val notesBody = stringToRequestBody(notes)
                val imagePart = uriStringToMultipartPart(context, "proof_image", "completion_proof", proofUri)

                val response = RetrofitProvider.incidentApi.markIncidentComplete(
                    assignmentIdBody, responderIdBody, notesBody, imagePart
                )

                if (response.success) {
                    context.stopService(Intent(context, RouteMonitoringService::class.java))

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

                    Toast.makeText(context, "Incident marked completed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, response.message ?: "Failed to mark complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Mark complete failed: ${e.message}")
                Toast.makeText(context, "Failed to mark complete", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Only show the "please turn location back on" banner once the responder
    // has already been through the initial permission prompt AND granted it.
    // This prevents the banner from competing with the first-run rationale dialog.
    LaunchedEffect(deviceLocationEnabled, hasLocationPermission, hasPromptedLocationOnce, effectiveRole) {
        val isResponder = !effectiveRole.isNullOrBlank()
        showCriticalGpsWarning = isResponder &&
                hasPromptedLocationOnce &&
                hasLocationPermission &&
                !deviceLocationEnabled
    }


    // ── NEW: notification permission, prompted once ──
    var hasPromptedNotifOnce by remember {
        mutableStateOf(prefs.getBoolean("notif_permission_prompted", false))
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        hasPromptedNotifOnce = true
        prefs.edit().putBoolean("notif_permission_prompted", true).apply()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            !hasPromptedNotifOnce &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
// ── end NEW ──

    val locationSettingsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

            deviceLocationEnabled =
                isDeviceLocationEnabled(context)

            if (deviceLocationEnabled && hasLocationPermission) {

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
            if (!isDeviceLocationEnabled(context)) {
                // Location service is NOT enabled - ask user to enable it
                Toast.makeText(context, "Location service is OFF. Please enable Location in Settings to start live monitoring.", Toast.LENGTH_LONG).show()
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                isLocationShared = false
                isLocationMonitoringEnabled = false
                prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
            } else {
                // Location service IS enabled - start monitoring
                isLocationShared = true
                isLocationMonitoringEnabled = true
                prefs.edit().putBoolean(locationMonitoringEnabledKey, true).apply()
                startLocationUpdates()
            }
        } else {
            hasLocationPermission = false
            isLocationShared = false
            isLocationMonitoringEnabled = false
            prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
            Toast.makeText(context, "Location permission denied - GPS features will not work", Toast.LENGTH_LONG).show()
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

                val result = repo.sendBackupRequest(
                    responderId = responderId,
                    responderName = responderName,
                    department = effectiveRole ?: "",
                    requestedDepartment = deptName,
                    resources = resourceList,
                    isFullBackup = request.isFullBackup,
                    incidentId = request.fromIncidentId
                )

                result.onSuccess { newId ->
                    scope.launch {
                        backupRequestsList = com.ers.emergencyresponseapp.data.IncidentRepository().getMyBackupRequests(responderId)
                    }
                    Toast.makeText(context, "Backup request sent to $deptName", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Log.e("BackupRequest", "Failed: responderId=$responderId, resources=$resourceList, error=${error.message}")
                    Toast.makeText(context, "Failed: ${error.message}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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


            // ============ CRITICAL GPS WARNING FOR RESPONDERS ============
            if (showCriticalGpsWarning) {
                Popup(alignment = Alignment.TopCenter) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        ),
                        border = BorderStroke(
                            2.dp,
                            Color(0xFFD32F2F)
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Critical warning",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(24.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "⚠️ GPS Location Disabled",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFD32F2F)
                                )

                                Text(
                                    "Turn ON location in device settings. GPS is required for emergency response.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC62828),
                                    lineHeight = 14.sp
                                )
                            }

                            Button(
                                onClick = {
                                    // Reuse launcher so we reliably return with updated GPS state.
                                    locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Enable", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    }
                }
            }

            if (showLocationRationale) {
                AlertDialog(
                    onDismissRequest = {
                        showLocationRationale = false
                        hasPromptedLocationOnce = true
                        prefs.edit().putBoolean("location_permission_prompted", true).apply()
                    },
                        shape = RoundedCornerShape(20.dp),
                        icon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppColors.Primary)
                        },
                        title = { Text("GPS Location Required", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)) },
                        text = {
                            Text(
                                "🚨 CRITICAL FOR RESPONDERS\n\n" +
                                "This app requires GPS location access to:\n" +
                                "• Enable live tracking during dispatch\n" +
                                "• Navigate to incident locations\n" +
                                "• Verify on-scene arrival\n" +
                                "• Ensure responder safety\n\n" +
                                "Location services must be enabled in your device settings (Settings > Location).",
                                color = AppColors.TextSecondary,
                                lineHeight = 18.sp
                            )
                        },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLocationRationale = false
                                hasPromptedLocationOnce = true
                                prefs.edit().putBoolean("location_permission_prompted", true).apply()
                                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) { Text("Enable GPS Now", color = Color.White) }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showLocationRationale = false
                                    hasPromptedLocationOnce = true
                                    prefs.edit().putBoolean("location_permission_prompted", true).apply()
                                    Toast.makeText(context, "⚠️ GPS is required for emergency response operations", Toast.LENGTH_LONG).show()
                                }
                            ) { Text("Cancel") }
                        }
                    )
                }


            // FIX 7: Recompute counts whenever activeIncidents changes.
            // (Previously this used remember{} with no key, so it captured
            // activeIncidents only once and never updated after that.)
            val fireCount    = remember(activeIncidents) { activeIncidents.count { it.type == IncidentType.FIRE } }
            val medicalCount = remember(activeIncidents) { activeIncidents.count { it.type == IncidentType.MEDICAL } }
            val crimeCount   = remember(activeIncidents) { activeIncidents.count { it.type == IncidentType.CRIME } }


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

                val isOnHomeScreen =
                    AppState.isForeground &&
                            AppScreenTracker.currentScreen == "HOME"

                if (isOnHomeScreen) {
                    // HOME SCREEN = banner only
                    showNewIncidentNotification = true
                    vibratePhone(context)
                } else {
                    // OTHER SCREEN / BACKGROUND = Android notification only
                    showNewIncidentNotification = false

                    showAssignedIncidentNotification(
                        context,
                        "New Incident Assigned",
                        newIncidentMessage
                    )
                }
            }

            AnimatedVisibility(
                visible = showNewIncidentNotification,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFB71C1C)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "EMERGENCY DISPATCH",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )

                            Text(
                                newIncidentMessage,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TextButton(
                            onClick = {
                                showNewIncidentNotification = false
                                scope.launch {
                                    listState.animateScrollToItem(2)
                                }
                            }
                        ) {
                            Text(
                                "VIEW",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LaunchedEffect(showNewIncidentNotification) {
                if (showNewIncidentNotification) {
                    delay(5000)
                    showNewIncidentNotification = false
                }
            }

            // Add near the other LaunchedEffects, after hasLocationPermission/deviceLocationEnabled are declared
            LaunchedEffect(hasLocationPermission, deviceLocationEnabled) {
                if (hasLocationPermission && deviceLocationEnabled) {
                    isLocationMonitoringEnabled = true
                    isLocationShared = true
                    prefs.edit().putBoolean(locationMonitoringEnabledKey, true).apply()
                    startLocationUpdates()
                } else {
                    isLocationMonitoringEnabled = false
                    isLocationShared = false
                    stopLocationUpdates()
                }
            }


             LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(
                    top = when {
                        showCriticalGpsWarning -> 110.dp
                        showNewIncidentNotification -> 118.dp
                        else -> 0.dp
                    },
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
                        Box(modifier = Modifier.fillMaxSize().background(headerBrush())) {
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
                                                !gpsEnabled -> "GPS Disabled"
                                                !hasLocationPermission -> "GPS Permission Needed"
                                                isLocationMonitoringEnabled -> "GPS Active"
                                                else -> "GPS Available"
                                            },
                                            color = Color.White,
                                            fontSize = 12.sp
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
                                        if (notificationCount > 0) {
                                            Badge(
                                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp),
                                                containerColor = Color(0xFFD32F2F),
                                                contentColor = Color.White
                                            ) {
                                                Text(if (notificationCount > 9) "9+" else notificationCount.toString())
                                            }
                                        }
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
                                colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
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
                                    shape = RoundedCornerShape(16.dp),colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
                                    elevation = CardDefaults.cardElevation(0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                                ) {
                                    Column(
                                        // FIX 2: Use stable top-level AssignedCardBrush constant
                                        modifier = Modifier.fillMaxWidth().background(assignedCardBrush()).padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // FIX 2: Use stable top-level AssignedBarBrush constant
                                            Box(modifier = Modifier.width(7.dp).height(54.dp).clip(RoundedCornerShape(99.dp)).background(assignedBarBrush()))
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
                                            hasLocationPermission = hasLocationPermission,
                                            onSceneEnabled = (onSceneEnabledMap[inc.id] == true),
                                            setOnSceneEnabled = { e -> onSceneEnabledMap[inc.id] = e },
                                            setNavTarget = { id, lat, lng -> navDestinationIncidentId = id; navDestinationLat = lat; navDestinationLng = lng },
                                            startOnSceneTracking = { startOnSceneTracking() },
                                            requestOnScenePermission = { onScenePermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                            // HomeScreen.kt wiring
                                            navigateToLocation = { lat, lng, addr, incId, assignId ->
                                                navigateToLocation(lat, lng, addr, incidentId = incId, assignmentId = assignId)
                                            },
                                            sendOnSceneReport = { sendOnSceneReport(it) },
                                            openMarkDone = { markTargetIncidentInc = it; proofNotes = ""; selectedProofUri = null; showMarkCompleteDialog = true },
                                            // AssignedActionButtons's onNavigateStatusUpdate
                                            onNavigateStatusUpdate = {
                                                assignedVm.updateStatus(
                                                    assignmentId = it.assignmentId ?: it.id,
                                                    status = "en_route",
                                                    responderId = responderId
                                                )
                                            }
                                        )

                                    }
                                }
                            }
                        }
                    }
                }

                 item {
                     Card(
                         modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                         shape = RoundedCornerShape(16.dp),
                         colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
                         border = BorderStroke(1.dp, AppColors.Border),
                         elevation = CardDefaults.cardElevation(1.dp)
                     ) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(14.dp),
                             verticalArrangement = Arrangement.spacedBy(10.dp)
                         ) {
                             // Header row
                             Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                 Box(
                                     modifier = Modifier
                                         .size(34.dp)
                                         .clip(RoundedCornerShape(10.dp))
                                         .background(AppColors.Primary.copy(alpha = 0.10f)),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     Icon(Icons.Default.LocalHospital, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                                 }
                                 Spacer(Modifier.width(10.dp))
                                 Column(modifier = Modifier.weight(1f)) {
                                     Text("Backup Requests", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.Text)
                                     Text(
                                         "${visibleBackupRequests.size} total • ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(lastBackupUpdateTime)}",
                                         fontSize = 11.sp,
                                         color = AppColors.TextSecondary
                                     )
                                 }
                                 IconButton(
                                     onClick = {
                                         scope.launch {
                                             backupRequestsList = com.ers.emergencyresponseapp.data.IncidentRepository().getMyBackupRequests(responderId)
                                             lastBackupUpdateTime = java.util.Date()
                                         }
                                     },
                                     modifier = Modifier.size(32.dp)
                                 ) {
                                     Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                                 }
                             }

                             // Action buttons row
                             Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                 Button(
                                     onClick = { showDepartmentSelection = true },
                                     modifier = Modifier.weight(1f).height(38.dp),
                                     shape = RoundedCornerShape(10.dp),
                                     contentPadding = PaddingValues(horizontal = 8.dp),
                                     colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = Color.White)
                                 ) {
                                     Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.size(14.dp))
                                     Spacer(Modifier.width(6.dp))
                                     Text("Request Backup", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                 }
                                 OutlinedButton(
                                     onClick = {
                                         showAllBackupRequestsDialog = true
                                     },
                                     modifier = Modifier.weight(1f).height(38.dp),
                                     shape = RoundedCornerShape(10.dp),
                                     contentPadding = PaddingValues(horizontal = 8.dp),
                                     border = BorderStroke(1.dp, AppColors.Border),
                                     colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Text)
                                 ) {
                                     Box {
                                         Text("View All", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                         if (pendingExtraBackupCount > 0) {
                                             Badge(
                                                 modifier = Modifier.align(Alignment.TopEnd).offset(x = 13.dp, y = (-8).dp),
                                                 containerColor = Color(0xFFD32F2F)
                                             ) {
                                                 Text(if (pendingExtraBackupCount > 9) "9+" else pendingExtraBackupCount.toString())
                                             }
                                         }
                                     }
                                 }
                             }

                             if (visibleBackupRequests.isEmpty()) {
                                 Text(
                                     "No backup requests yet.",
                                     color = AppColors.TextSecondary,
                                     fontSize = 12.sp
                                 )
                             } else {
                                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                     visibleBackupRequests.take(1).forEach { req ->
                                         BackupRequestStatusCard(
                                             department = req.requested_department,
                                             resources = req.resources,
                                             status = req.status,
                                             onCancelClick = {
                                                 pendingCancelBackupId = req.id
                                                 showCancelBackupConfirm = true
                                             },
                                             onRefreshClick = { refreshSingleBackupRequest(req.id) }
                                         )
                                     }
                                 }
                             }
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
                                containerColor = AppColors.CardBg
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
                            colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
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
                                        shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(0.dp),colors = CardDefaults.cardColors(containerColor = AppColors.Bg), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
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
                    val cardBrushes = cardBrushesStable()

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeAccents.forEachIndexed { i, accent ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.Bg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.13f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBrushes[i])
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
                                    shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp),colors = CardDefaults.cardColors(containerColor = AppColors.Bg)
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

            // ── ALL BACKUP REQUESTS DIALOG ──
            if (showAllBackupRequestsDialog) {
                Dialog(
                    onDismissRequest = { showAllBackupRequestsDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFF5F7F7)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text("All Backup Requests", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AppColors.Text)
                                    Text("${visibleBackupRequests.size} total requests", fontSize = 13.sp, color = AppColors.TextSecondary)
                                }
                                IconButton(onClick = { showAllBackupRequestsDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = backupSearchQuery,
                                onValueChange = { backupSearchQuery = it },
                                placeholder = { Text("Search department or resource") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            val filteredRequests = visibleBackupRequests.filter {
                                backupSearchQuery.isBlank() ||
                                        it.requested_department.contains(backupSearchQuery, ignoreCase = true) ||
                                        it.resources.contains(backupSearchQuery, ignoreCase = true)
                            }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filteredRequests, key = { it.id }) { req ->
                                    BackupRequestStatusCard(
                                        department = req.requested_department,
                                        resources = req.resources,
                                        status = req.status,
                                        onCancelClick = {
                                            pendingCancelBackupId = req.id
                                            showCancelBackupConfirm = true
                                        },
                                        onRefreshClick = { refreshSingleBackupRequest(req.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // ── CANCEL BACKUP REQUEST CONFIRMATION ──
            if (showCancelBackupConfirm && pendingCancelBackupId != null) {
                AlertDialog(
                    onDismissRequest = {
                        showCancelBackupConfirm = false
                        pendingCancelBackupId = null
                    },
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F)) },
                    title = { Text("Cancel Backup Request?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "This will cancel your backup request. This action cannot be undone.",
                            color = AppColors.TextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingCancelBackupId?.let { id -> cancelBackupRequest(id) }
                                showCancelBackupConfirm = false
                                pendingCancelBackupId = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Yes, Cancel", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCancelBackupConfirm = false
                                pendingCancelBackupId = null
                            }
                        ) {
                            Text("Keep Request")
                        }
                    }
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
                                    openCompletionCamera()
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
                                    showActiveDetailsSheet = false
                                    selectedActiveIncident = null
                                    navigateToLocation(
                                        lat = inc.latitude,
                                        lng = inc.longitude,
                                        address = inc.location,
                                        incidentId = inc.id,
                                        viewOnly = true
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
                    context = context,
                    fullName = accountFullName, username = accountUsername, email = accountEmail,
                    photoUri = accountPhotoUri, isDarkMode = isDarkMode,
                    onFullNameChange = { accountFullName = it }, onUsernameChange = { accountUsername = it }, onEmailChange = { accountEmail = it },
                    onDarkModeChange = { e -> isDarkMode = e; prefs.edit().putBoolean("dark_mode", e).apply() },
                    onPickPhoto = { pickProfilePhotoLauncher.launch("image/*") },
                    onSave = {
                        prefs.edit()
                            .putString("account_full_name", accountFullName.trim())
                            .putString("account_username", accountUsername.trim())
                            .putString("account_email", accountEmail.trim())
                            .putString("account_photo", accountPhotoUri)
                            .apply()
                        if (accountUsername.isNotBlank()) responderName = accountUsername.trim()

                        if (responderId > 0) {
                            scope.launch {
                                try {
                                    val response = RetrofitProvider.authApi.updateProfile(
                                        userId = responderId,
                                        fullName = accountFullName.trim(),
                                        username = accountUsername.trim(),
                                        email = accountEmail.trim()
                                    )
                                    Toast.makeText(
                                        context,
                                        if (response.success) "Profile updated" else (response.message ?: "Update failed"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Profile update failed: ${e.message}")
                                    Toast.makeText(context, "Failed to update profile on server", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showSettingsDialog = false
                    },
                    onBack = { showSettingsDialog = false },
                    onLogout = { Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show(); showSettingsDialog = false; onLogout() }
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ACCOUNT SETTINGS DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountSettingsDialog(
    context: Context,
    fullName: String, username: String, email: String, photoUri: String?,
    isDarkMode: Boolean,
    onFullNameChange: (String) -> Unit, onUsernameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit, onPickPhoto: () -> Unit,
    onSave: () -> Unit, onBack: () -> Unit, onLogout: () -> Unit
) {

    var showProfilePreview by remember { mutableStateOf(false) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.Text,
        unfocusedTextColor = AppColors.Text,
        focusedBorderColor = AppColors.Primary,
        unfocusedBorderColor = if (ThemeController.isDarkMode.value) Color(0xFF4A4A4E) else AppColors.Border,
        focusedLabelColor = AppColors.Primary,
        unfocusedLabelColor = AppColors.TextSecondary,
        cursorColor = AppColors.Primary
    )

    AlertDialog(
        onDismissRequest = onBack,
        shape = RoundedCornerShape(20.dp),
        containerColor = if (ThemeController.isDarkMode.value) Color(0xFF242426) else Color(0xFFF7F5F9),
        titleContentColor = AppColors.Text,
        textContentColor = AppColors.Text,
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

                OutlinedTextField(
                    value = fullName, onValueChange = onFullNameChange,
                    label = { Text("Full name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                OutlinedTextField(
                    value = username, onValueChange = onUsernameChange,
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                OutlinedTextField(
                    value = email, onValueChange = onEmailChange,
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
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


                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Bg), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Night mode", fontWeight = FontWeight.SemiBold, color = AppColors.Text); Text("Reduce glare in low light", fontSize = 12.sp, color = AppColors.TextSecondary) }
                        Switch(
                            checked = ThemeController.isDarkMode.value,
                            onCheckedChange = { enabled -> ThemeController.setDarkMode(context, enabled) }
                        )
                    }
                }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Bg), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.35f))) {
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