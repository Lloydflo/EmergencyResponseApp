@file:OptIn(ExperimentalFoundationApi::class)
package com.ers.emergencyresponseapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ers.emergencyresponseapp.data.BroadcastNotice
import com.ers.emergencyresponseapp.data.HomeDataCache
import com.ers.emergencyresponseapp.data.NotificationRepository
import com.ers.emergencyresponseapp.features.assigned.AssignedIncidentsViewModel
import com.ers.emergencyresponseapp.notification.AppNotificationManager
import com.ers.emergencyresponseapp.notification.NotificationDestination
import com.ers.emergencyresponseapp.notification.NotificationNavigation
import com.ers.emergencyresponseapp.ui.components.AppPullToRefresh
import com.ers.emergencyresponseapp.features.assigned.toDomain
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPrimaryAction
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentStatus
import com.ers.emergencyresponseapp.home.IncidentType
import com.ers.emergencyresponseapp.home.ResponderWorkflowPolicy
import com.ers.emergencyresponseapp.home.composables.BackupIncidentOption
import com.ers.emergencyresponseapp.home.composables.BackupRequest
import com.ers.emergencyresponseapp.home.composables.DepartmentSelectionDialog
import com.ers.emergencyresponseapp.network.ConnectivityStatus
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.stringToRequestBody
import com.ers.emergencyresponseapp.network.uriStringToMultipartPart
import com.ers.emergencyresponseapp.network.uriToProfileImagePart
import com.ers.emergencyresponseapp.network.userIdToRequestBody
import com.ers.emergencyresponseapp.routing.ActiveRouteSessionMetadata
import com.ers.emergencyresponseapp.routing.ActiveRouteSessionStore
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
import androidx.compose.material.icons.filled.Info


private fun decodeSampledProofBitmap(context: Context, uriString: String): Bitmap? {
    val uri = Uri.parse(uriString)

    fun decode(options: BitmapFactory.Options): Bitmap? = if (uri.scheme == "file") {
        uri.path?.let { BitmapFactory.decodeFile(it, options) }
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val targetWidth = 1_280
        val targetHeight = 720
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }

        decode(
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    }.getOrNull()
}


// ─────────────────────────────────────────────────────────────────────────────
// APP COLORS
// ─────────────────────────────────────────────────────────────────────────────

private object AppColors {
    private val isDark: Boolean get() = ThemeController.isDarkMode.value

    val Primary: Color get() = Color(0xFF4C8A89)
    val Secondary: Color get() = Color(0xFF3A506B)
    val Tertiary: Color get() = Color(0xFF1C2541)
    val Dark: Color get() = Color(0xFF0B132B)

    val Text: Color get() = if (isDark) Color(0xFFFAFAFA) else Color(0xFF171717)
    val TextSecondary: Color get() = if (isDark) Color(0xFFB7BAC2) else Color(0xFF575757)
    val Border: Color get() = if (isDark) Color(0xFF343840) else Color(0xFFE5E5E5)
    val Bg: Color get() = if (isDark) Color(0xFF0A0A0A) else Color(0xFFF7F9F9)
    val CardBg: Color get() = if (isDark) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val HeaderBg: Color get() = if (isDark) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val FooterBg: Color get() = if (isDark) Color(0xFF16181D) else Color(0xFFFAFAFA)

    // Semantic surfaces keep cards, warnings, dialogs, and placeholders legible
    // in both themes instead of relying on hardcoded light backgrounds.
    val SubtleSurface: Color get() = if (isDark) Color(0xFF20242B) else Color(0xFFF3F6F6)
    val ElevatedSurface: Color get() = if (isDark) Color(0xFF242830) else Color(0xFFF7F7F7)
    val Skeleton: Color get() = if (isDark) Color(0xFF24272D) else Color(0xFFEAEAEA)

    val DispatchSurface: Color get() = if (isDark) Color(0xFF3A2917) else Color(0xFFFFF3E0)
    val DispatchText: Color get() = if (isDark) Color(0xFFFFBE73) else Color(0xFFEF6C00)

    val SuccessSurface: Color get() = if (isDark) Color(0xFF15351F) else Color(0xFFE8F5E9)
    val SuccessText: Color get() = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)

    val DangerSurface: Color get() = if (isDark) Color(0xFF3B171A) else Color(0xFFFFEBEE)
    val DangerText: Color get() = if (isDark) Color(0xFFFFB4AB) else Color(0xFFC62828)
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
private fun fireCardBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFFEF5350) else Color(0xFFE53935)
    return Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), AppColors.CardBg))
}

private fun medicalCardBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFF64B5F6) else Color(0xFF1E88E5)
    return Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), AppColors.CardBg))
}

private fun crimeCardBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFFBCAAA4) else Color(0xFF6D4C41)
    return Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), AppColors.CardBg))
}

private fun disasterCardBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFFCE93D8) else Color(0xFF8E24AA)
    return Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), AppColors.CardBg))
}

private fun generalCardBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFF90A4AE) else Color(0xFF546E7A)
    return Brush.verticalGradient(listOf(accent.copy(alpha = 0.09f), AppColors.CardBg))
}

private fun fireBarBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFFEF5350) else Color(0xFFE53935)
    return Brush.horizontalGradient(listOf(accent.copy(alpha = 0.4f), accent))
}

private fun medicalBarBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFF64B5F6) else Color(0xFF1E88E5)
    return Brush.horizontalGradient(listOf(accent.copy(alpha = 0.4f), accent))
}

private fun crimeBarBrush(): Brush {
    val accent = if (ThemeController.isDarkMode.value) Color(0xFFBCAAA4) else Color(0xFF6D4C41)
    return Brush.horizontalGradient(listOf(accent.copy(alpha = 0.4f), accent))
}

private fun cardBrushesStable() = listOf(
    fireCardBrush(),
    medicalCardBrush(),
    crimeCardBrush(),
    disasterCardBrush(),
    generalCardBrush()
)
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

private data class PendingRouteLaunch(
    val incidentId: String,
    val assignmentId: String?,
    val updateStatusToEnRoute: Boolean
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


private fun startRouteUpdateMonitoring(
    context: Context,
    incidentId: String,
    assignmentId: String?,
    destLat: Double?,
    destLng: Double?,
    destAddress: String?
): Boolean {
    if (!ResponderWorkflowPolicy.hasValidCoordinates(destLat, destLng)) {
        return false
    }
    Log.d("RouteMonitor", "Starting service for incident=$incidentId")
    Log.d("LiveGPS", "Calling startForegroundService incident=$incidentId")
    val intent = Intent(context, RouteMonitoringService::class.java).apply {
        putExtra(RouteMonitoringService.EXTRA_INCIDENT_ID, incidentId)
        putExtra(RouteMonitoringService.EXTRA_ASSIGNMENT_ID, assignmentId.orEmpty())
        putExtra(RouteMonitoringService.EXTRA_DEST_LAT,      destLat ?: Double.NaN)
        putExtra(RouteMonitoringService.EXTRA_DEST_LNG,      destLng ?: Double.NaN)
        putExtra(RouteMonitoringService.EXTRA_DEST_ADDRESS,  destAddress ?: "")
    }
    return try {
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (error: Exception) {
        Log.e("RouteMonitor", "Unable to start route monitoring", error)
        Toast.makeText(
            context,
            "Unable to start live tracking: ${error.message ?: "service unavailable"}",
            Toast.LENGTH_LONG
        ).show()
        false
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
    if (timeReported.time <= 0L) return "time unavailable"

    val diffMin = ((System.currentTimeMillis() - timeReported.time) / 60000)
        .coerceAtLeast(0L)
        .toInt()
    return when {
        diffMin < 1    -> "just now"
        diffMin < 60   -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else           -> java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(timeReported)
    }
}

private fun formatLastSyncLabel(lastSyncMillis: Long?): String {
    if (lastSyncMillis == null || lastSyncMillis <= 0L) return "Not synced yet"

    val elapsedMinutes = ((System.currentTimeMillis() - lastSyncMillis) / 60_000L)
        .coerceAtLeast(0L)
    return when {
        elapsedMinutes < 1L -> "Last synced just now"
        elapsedMinutes < 60L -> "Last synced ${elapsedMinutes}m ago"
        elapsedMinutes < 1_440L -> "Last synced ${elapsedMinutes / 60L}h ago"
        else -> "Last synced " + java.text.SimpleDateFormat(
            "MMM d, h:mm a",
            Locale.getDefault()
        ).format(java.util.Date(lastSyncMillis))
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
        when (it.priority) {
            IncidentPriority.HIGH -> 3
            IncidentPriority.MEDIUM -> 2
            IncidentPriority.LOW -> 1
            IncidentPriority.UNKNOWN -> 0
        }
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
                .border(1.dp, AppColors.CardBg, CircleShape)
        )
    }
}


@Composable
private fun AssignedIncidentEmptyState(
    networkStatus: ConnectivityStatus,
    loading: Boolean,
    requestCompleted: Boolean,
    serverError: String?,
    lastSyncMillis: Long?,
    onRetry: () -> Unit
) {
    val isOffline = networkStatus == ConnectivityStatus.Offline
    val hasServerError = !isOffline && !serverError.isNullOrBlank()
    val isChecking = !isOffline && !hasServerError && (
        networkStatus == ConnectivityStatus.Checking ||
                loading ||
                !requestCompleted
        )
    val isWaitingOnline = !isOffline && !isChecking && !hasServerError

    val waitingAnimation = rememberInfiniteTransition(label = "assigned_incident_waiting")
    val animatedRingScale by waitingAnimation.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dispatch_ring_scale"
    )
    val animatedRingAlpha by waitingAnimation.animateFloat(
        initialValue = 0.42f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dispatch_ring_alpha"
    )

    val accentColor = when {
        isOffline -> AppColors.DangerText
        hasServerError -> AppColors.DispatchText
        else -> AppColors.Primary
    }
    val icon = when {
        isOffline -> Icons.Default.CloudOff
        hasServerError -> Icons.Default.Warning
        else -> Icons.Default.Notifications
    }
    val title = when {
        isOffline -> "You’re offline"
        hasServerError -> "Dispatch service unavailable"
        isChecking -> "Checking for incident assignments"
        else -> "Waiting for an incident assignment"
    }
    val message = when {
        isOffline ->
            "The app cannot check for new assignments right now. Reconnect to the internet before relying on this list."

        hasServerError ->
            "Your internet connection is active, but the dispatch server did not respond. Your last known information remains visible."

        isChecking ->
            "Contacting dispatch and verifying your latest assignment status."

        else ->
            "No active assignment yet. New incidents will appear here automatically when dispatch assigns your unit."
    }
    val statusText = when {
        isOffline -> "Offline • ${formatLastSyncLabel(lastSyncMillis)}"
        hasServerError -> "Unable to sync • ${formatLastSyncLabel(lastSyncMillis)}"
        isChecking -> "Checking dispatch…"
        else -> "Online • Listening for dispatch"
    }
    val statusSurface = when {
        isOffline -> AppColors.DangerSurface
        hasServerError -> AppColors.DispatchSurface
        isChecking -> AppColors.SubtleSurface
        else -> AppColors.SuccessSurface
    }
    val statusTextColor = when {
        isOffline -> AppColors.DangerText
        hasServerError -> AppColors.DispatchText
        isChecking -> AppColors.TextSecondary
        else -> AppColors.SuccessText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(126.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isWaitingOnline || isChecking) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                scaleX = animatedRingScale
                                scaleY = animatedRingScale
                                alpha = animatedRingAlpha
                            }
                            .border(
                                width = 2.dp,
                                color = accentColor,
                                shape = CircleShape
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.08f))
                )

                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(
                            width = 1.dp,
                            color = accentColor.copy(alpha = 0.30f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                if (isWaitingOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 25.dp, y = (-24).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(AppColors.SuccessText)
                            .border(3.dp, AppColors.CardBg, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = title,
                color = AppColors.Text,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = message,
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                color = statusSurface,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = statusTextColor.copy(alpha = 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusTextColor)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = statusText,
                        color = statusTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (hasServerError) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Retry now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ASSIGNED ACTION BUTTONS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AssignedActionButtons(
    inc: Incident,
    dataFresh: Boolean,
    networkAvailable: Boolean,
    hasStoredRoute: Boolean,
    hasConflictingActiveRoute: Boolean,
    isAnyRouteActionInProgress: Boolean,
    isRouteActionInProgress: Boolean,
    onStartResponse: (Incident) -> Unit,
    onResumeNavigation: (Incident) -> Unit,
    openMarkDone: (Incident) -> Unit,
) {
    val completeColor = if (ThemeController.isDarkMode.value) Color(0xFF81C784) else Color(0xFF2E7D32)
    val coordinatesAvailable = ResponderWorkflowPolicy.hasValidCoordinates(
        inc.latitude,
        inc.longitude
    )
    val action = ResponderWorkflowPolicy.primaryAction(inc.status)
    val actionDataReady = if (
        action == IncidentPrimaryAction.RESUME_NAVIGATION && !networkAvailable
    ) {
        hasStoredRoute
    } else {
        dataFresh
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (action) {
            IncidentPrimaryAction.START_RESPONSE,
            IncidentPrimaryAction.RESUME_NAVIGATION -> {
                val isStart = action == IncidentPrimaryAction.START_RESPONSE
                Button(
                    enabled = actionDataReady &&
                            !hasConflictingActiveRoute &&
                            coordinatesAvailable &&
                            !isAnyRouteActionInProgress &&
                            (!isStart || networkAvailable),
                    onClick = {
                        if (isStart) onStartResponse(inc) else onResumeNavigation(inc)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    if (isRouteActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isRouteActionInProgress -> "Preparing route…"
                            isStart -> "Start Response"
                            else -> "Resume Navigation"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IncidentPrimaryAction.COMPLETE_INCIDENT -> {
                OutlinedButton(
                    enabled = dataFresh &&
                            networkAvailable &&
                            !isAnyRouteActionInProgress,
                    onClick = { openMarkDone(inc) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, completeColor.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = completeColor)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Complete Incident", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            IncidentPrimaryAction.NONE -> {
                Text(
                    text = if (inc.status == IncidentStatus.RESOLVED) {
                        "This response is already closed."
                    } else {
                        "Dispatch status is unavailable. Refresh before taking action."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        val actionWarning = when {
            hasConflictingActiveRoute && action in setOf(
                IncidentPrimaryAction.START_RESPONSE,
                IncidentPrimaryAction.RESUME_NAVIGATION
            ) -> "Another incident route is active. Finish or cancel it before switching responses."
            !actionDataReady -> "Refreshing this assignment with dispatch. Actions are temporarily disabled."
            !coordinatesAvailable && action in setOf(
                IncidentPrimaryAction.START_RESPONSE,
                IncidentPrimaryAction.RESUME_NAVIGATION
            ) -> "Dispatch has not provided valid map coordinates for this incident."
            !networkAvailable && action != IncidentPrimaryAction.RESUME_NAVIGATION ->
                "Reconnect before changing this assignment's status."
            else -> null
        }

        actionWarning?.let { warning ->
            Text(
                text = warning,
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.DangerText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}




// ─────────────────────────────────────────────────────────────────────────────
// EMERGENCY REQUEST CARD  (incoming backup requests)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    borderColor: Color = AppColors.CardBg
) {
    if (count <= 0) return

    val badgeShape = RoundedCornerShape(999.dp)
    val badgeText = if (count > 9) "9+" else count.toString()
    val badgeWidth = if (count > 9) 26.dp else 20.dp

    Box(
        modifier = modifier
            .width(badgeWidth)
            .height(20.dp)
            .shadow(
                elevation = 3.dp,
                shape = badgeShape,
                clip = false
            )
            .clip(badgeShape)
            .background(Color(0xFFE53935))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = badgeShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun HeaderNotificationButton(
    count: Int,
    onClick: () -> Unit
) {
    // The outer Box is intentionally not clipped. The badge can therefore sit
    // outside the circular bell button instead of being cut off inside it.
    Box(
        modifier = Modifier.size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = Color.White
            )
        }

        NotificationCountBadge(
            count = count,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
            borderColor = Color.White
        )
    }
}


@Composable
private fun BackupRequestStatusCard(
    department: String,
    backupNeed: String,
    status: String,
    onCancelClick: () -> Unit,
    onRefreshClick: (() -> Unit)? = null,
    actionsEnabled: Boolean = true
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
    val isDarkTheme = ThemeController.isDarkMode.value

    val (badgeText, badgeColor) = when (status) {
        "pending"   -> "Pending" to if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00)
        "accepted"  -> "Accepted" to if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
        "en_route"  -> "En Route" to if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1E88E5)
        "completed" -> "Completed" to if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
        "declined"  -> "Declined" to if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFD32F2F)
        "cancelled" -> "Cancelled" to AppColors.TextSecondary
        else        -> status.replaceFirstChar { it.uppercase() } to AppColors.TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
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
                    Text(
                        text = "Backup needed: $backupNeed",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onRefreshClick != null) {
                    IconButton(
                        enabled = actionsEnabled,
                        onClick = onRefreshClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh status",
                            tint = if (actionsEnabled) AppColors.TextSecondary else AppColors.TextSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(badgeColor.copy(alpha = if (isDarkTheme) 0.20f else 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isDeclined || isCancelled) {
                Text(
                    if (isDeclined) "Declined by dispatch" else "Request cancelled",
                    color = if (isDeclined) badgeColor else AppColors.TextSecondary,
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
                        color = if (actionsEnabled) Color(0xFFD32F2F) else AppColors.TextSecondary.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(
                            enabled = actionsEnabled,
                            onClick = onCancelClick
                        )
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
    networkStatus: ConnectivityStatus = ConnectivityStatus.Online,
    onLogout: () -> Unit,
    assignedVm: AssignedIncidentsViewModel = viewModel()
) {
    val context = LocalContext.current
    val notificationDestination by NotificationNavigation.destination.collectAsState()
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
    val isNetworkAvailable = networkStatus == ConnectivityStatus.Online
    val homeDataCache = remember(context.applicationContext) {
        HomeDataCache(context.applicationContext)
    }
    val activeRouteStore = remember(context.applicationContext) {
        ActiveRouteSessionStore(context.applicationContext)
    }
    var cachedAssignedSnapshot by remember(responderId) {
        mutableStateOf(homeDataCache.readAssigned(responderId))
    }
    var cachedActiveSnapshot by remember(responderId) {
        mutableStateOf(homeDataCache.readActive(responderId))
    }
    var cachedBackupSnapshot by remember(responderId) {
        mutableStateOf(homeDataCache.readBackupRequests(responderId))
    }
    var assignedEmptySuccessStreak by remember(responderId) { mutableStateOf(0) }
    var activeEmptySuccessStreak by remember(responderId) { mutableStateOf(0) }
    var backupEmptySuccessStreak by remember(responderId) { mutableStateOf(0) }
    val unitCode = storedPrefs.getString("unit_code", "") ?: ""
    val unitType = storedPrefs.getString("unit_type", "") ?: ""
    var unitStatus by remember {
        mutableStateOf(storedPrefs.getString("unit_status", "available") ?: "available")
    }

    LaunchedEffect(responderId, networkStatus) {
        if (responderId <= 0 || !isNetworkAvailable) {
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

    LaunchedEffect(assignedUi.incidents, assignedUi.assignedLastSuccessMillis) {
        AppScreenTracker.currentScreen = "HOME"
        val latestStatus = assignedUi.incidents.firstOrNull()?.unit_status

        if (!latestStatus.isNullOrBlank()) {
            unitStatus = latestStatus

            storedPrefs.edit()
                .putString("unit_status", latestStatus)
                .apply()
        }

        assignedUi.assignedLastSuccessMillis?.let { savedAt ->
            if (assignedUi.incidents.isNotEmpty()) {
                assignedEmptySuccessStreak = 0
                homeDataCache.saveAssigned(responderId, assignedUi.incidents, savedAt)
                cachedAssignedSnapshot = homeDataCache.readAssigned(responderId)
            } else {
                assignedEmptySuccessStreak += 1
                val hasPreviousAssignment =
                    cachedAssignedSnapshot?.items?.isNotEmpty() == true
                if (!hasPreviousAssignment || assignedEmptySuccessStreak >= 2) {
                    homeDataCache.saveAssigned(responderId, emptyList(), savedAt)
                    cachedAssignedSnapshot = homeDataCache.readAssigned(responderId)
                }
            }
        }
    }

    LaunchedEffect(assignedUi.assignedError) {
        if (!assignedUi.assignedError.isNullOrBlank()) {
            assignedEmptySuccessStreak = 0
        }
    }

    LaunchedEffect(assignedUi.activeIncidents, assignedUi.activeLastSuccessMillis) {
        assignedUi.activeLastSuccessMillis?.let { savedAt ->
            if (assignedUi.activeIncidents.isNotEmpty()) {
                activeEmptySuccessStreak = 0
                homeDataCache.saveActive(responderId, assignedUi.activeIncidents, savedAt)
                cachedActiveSnapshot = homeDataCache.readActive(responderId)
            } else {
                activeEmptySuccessStreak += 1
                val hasPreviousActiveIncidents =
                    cachedActiveSnapshot?.items?.isNotEmpty() == true
                if (!hasPreviousActiveIncidents || activeEmptySuccessStreak >= 2) {
                    homeDataCache.saveActive(responderId, emptyList(), savedAt)
                    cachedActiveSnapshot = homeDataCache.readActive(responderId)
                }
            }
        }
    }

    LaunchedEffect(assignedUi.activeError) {
        if (!assignedUi.activeError.isNullOrBlank()) {
            activeEmptySuccessStreak = 0
        }
    }

    LaunchedEffect(assignedUi.actionError) {
        val actionError = assignedUi.actionError ?: return@LaunchedEffect
        Toast.makeText(context, actionError, Toast.LENGTH_LONG).show()
        assignedVm.clearActionError()
    }

    LaunchedEffect(responderId, networkStatus) {
        if (responderId <= 0 || !isNetworkAvailable) return@LaunchedEffect

        while (true) {
            assignedVm.load(responderId)
            assignedVm.loadActive(responderId)
            delay(5000L)
        }
    }

    val effectiveRole    = storedDepartment?.lowercase() ?: responderRole?.takeIf { it.isNotBlank() }
    val departmentFilter: IncidentType? = when (effectiveRole?.trim()?.lowercase()) {
        "fire", "firefighter" -> IncidentType.FIRE
        "medical", "ems", "ambulance", "paramedic" -> IncidentType.MEDICAL
        "crime", "police", "law enforcement", "security" -> IncidentType.CRIME
        "disaster", "rescue" -> IncidentType.DISASTER
        else -> null
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

    val onlineStatus = if (isNetworkAvailable) ResponderOnlineStatus.Online else ResponderOnlineStatus.Offline

    var showNewIncidentNotification by remember { mutableStateOf(false) }
    var newIncidentMessage by remember { mutableStateOf("") }
    var showAssignedAfterNotification by remember { mutableStateOf(false) }
    var lastShownApiAssignedId by remember {
        mutableStateOf(prefs.getString("last_shown_api_assigned_id", null))
    }
    var lastNotifiedIncidentId        by remember { mutableStateOf(prefs.getString("last_notified_incident_id", null)) }
    var lastAssignedIncidentId        by remember { mutableStateOf(prefs.getString("last_assigned_incident_id", null)) }
    // ── DIALOG FLAGS ──
    var showDepartmentSelection by remember { mutableStateOf(false) }
    var showSettingsDialog      by remember { mutableStateOf(false) }
    var showAllActiveDialog     by remember { mutableStateOf(false) }
    var showAllBackupRequestsDialog by remember { mutableStateOf(false) }
    var showCancelBackupConfirm by remember { mutableStateOf(false) }
    var pendingCancelBackupId by remember { mutableStateOf<Int?>(null) }
    var backupSearchQuery by remember { mutableStateOf("") }
    var lastBackupSyncMillis by remember(responderId) {
        mutableStateOf(cachedBackupSnapshot?.savedAtMillis)
    }
    var backupLoadError by remember(responderId) { mutableStateOf<String?>(null) }
    var backupLoading by remember(responderId) { mutableStateOf(false) }
    var hasCompletedBackupRequest by remember(responderId) { mutableStateOf(false) }
    var activeFilter            by remember { mutableStateOf(ActivePriorityFilter.ALL) }
    var backupRequestsList by remember(responderId) {
        mutableStateOf(cachedBackupSnapshot?.items.orEmpty())
    }
    var dismissedBackupIds by remember {
        mutableStateOf(
            (prefs.getStringSet("dismissed_backup_request_ids", emptySet()) ?: emptySet())
                .mapNotNull { it.toIntOrNull() }
                .toMutableSet()
        )
    }

    suspend fun loadBackupRequests(showError: Boolean = false): Boolean {
        if (responderId <= 0 || !isNetworkAvailable) return false

        backupLoading = true
        return try {
            val requests = com.ers.emergencyresponseapp.data.IncidentRepository()
                .getMyBackupRequests(responderId)
            val savedAt = System.currentTimeMillis()
            val hasPreviousRequests =
                backupRequestsList.isNotEmpty() ||
                        cachedBackupSnapshot?.items?.isNotEmpty() == true

            if (requests.isNotEmpty()) {
                backupEmptySuccessStreak = 0
                backupRequestsList = requests
                lastBackupSyncMillis = savedAt
                homeDataCache.saveBackupRequests(responderId, requests, savedAt)
                cachedBackupSnapshot = homeDataCache.readBackupRequests(responderId)
            } else {
                backupEmptySuccessStreak += 1
                if (!hasPreviousRequests || backupEmptySuccessStreak >= 2) {
                    backupRequestsList = emptyList()
                    lastBackupSyncMillis = savedAt
                    homeDataCache.saveBackupRequests(responderId, emptyList(), savedAt)
                    cachedBackupSnapshot = homeDataCache.readBackupRequests(responderId)
                }
            }

            backupLoadError = null
            hasCompletedBackupRequest = true
            true
        } catch (error: Exception) {
            backupEmptySuccessStreak = 0
            backupLoadError = error.message ?: "Unable to load backup requests"
            hasCompletedBackupRequest = true
            if (showError) {
                Toast.makeText(
                    context,
                    "Unable to refresh backup requests",
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        } finally {
            backupLoading = false
        }
    }

    LaunchedEffect(responderId, networkStatus) {
        if (responderId <= 0 || !isNetworkAvailable) return@LaunchedEffect
        while (true) {
            loadBackupRequests()
            delay(5000L)
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
    var isSubmittingCompletion by remember { mutableStateOf(false) }
    var completionError        by remember { mutableStateOf<String?>(null) }
    var pendingRouteLaunch     by remember { mutableStateOf<PendingRouteLaunch?>(null) }
    var isRouteActionIncidentId by remember { mutableStateOf<String?>(null) }

    var pendingCameraUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var pendingCameraFile by remember {
        mutableStateOf<File?>(null)
    }
    var selectedProofFile by remember {
        mutableStateOf<File?>(null)
    }

    var notificationCount by remember { mutableStateOf(0) }
    val notificationRepository = remember { NotificationRepository() }
    var broadcastNotices by remember { mutableStateOf<List<BroadcastNotice>>(emptyList()) }
    var selectedBroadcastId by remember { mutableStateOf<Long?>(null) }
    var hasPrimedBroadcastFeed by remember(responderId) { mutableStateOf(false) }
    val knownBroadcastIds = remember(responderId) { mutableSetOf<Long>() }
    val unreadBroadcastCount = remember(broadcastNotices) {
        broadcastNotices.count { !it.acknowledged }
    }


    val takePictureLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->

            if (success) {
                val file = pendingCameraFile

                if (file != null && file.exists() && file.length() > 0L) {
                    selectedProofFile
                        ?.takeIf { previous -> previous != file }
                        ?.delete()
                    selectedProofFile = file
                    selectedProofUri = Uri.fromFile(file).toString()
                    pendingCameraFile = null
                    pendingCameraUri = null

                    Log.d(
                        "CompletionPhoto",
                        "Full-resolution photo saved: " +
                                "path=${file.absolutePath}, " +
                                "size=${file.length()} bytes"
                    )
                } else {
                    file?.delete()
                    pendingCameraFile = null
                    pendingCameraUri = null

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

    val confirmingAssignedEmpty =
        assignedUi.assignedLastSuccessMillis != null &&
                assignedUi.incidents.isEmpty() &&
                cachedAssignedSnapshot?.items?.isNotEmpty() == true &&
                assignedEmptySuccessStreak < 2
    val confirmingActiveEmpty =
        assignedUi.activeLastSuccessMillis != null &&
                assignedUi.activeIncidents.isEmpty() &&
                cachedActiveSnapshot?.items?.isNotEmpty() == true &&
                activeEmptySuccessStreak < 2

    val displayedAssignedDtos = if (
        assignedUi.assignedLastSuccessMillis == null || confirmingAssignedEmpty
    ) {
        cachedAssignedSnapshot?.items ?: assignedUi.incidents
    } else {
        assignedUi.incidents
    }
    val displayedActiveDtos = if (
        assignedUi.activeLastSuccessMillis == null || confirmingActiveEmpty
    ) {
        cachedActiveSnapshot?.items ?: assignedUi.activeIncidents
    } else {
        assignedUi.activeIncidents
    }
    val assignedLastSyncMillis = if (confirmingAssignedEmpty) {
        cachedAssignedSnapshot?.savedAtMillis
    } else {
        assignedUi.assignedLastSuccessMillis ?: cachedAssignedSnapshot?.savedAtMillis
    }
    val activeLastSyncMillis = if (confirmingActiveEmpty) {
        cachedActiveSnapshot?.savedAtMillis
    } else {
        assignedUi.activeLastSuccessMillis ?: cachedActiveSnapshot?.savedAtMillis
    }
    val isUsingCachedAssigned =
        (assignedUi.assignedLastSuccessMillis == null && cachedAssignedSnapshot != null) ||
                confirmingAssignedEmpty
    val isUsingCachedActive =
        (assignedUi.activeLastSuccessMillis == null && cachedActiveSnapshot != null) ||
                confirmingActiveEmpty
    val activeRequestPending =
        networkStatus != ConnectivityStatus.Offline &&
                assignedUi.activeError.isNullOrBlank() &&
                (
                    networkStatus == ConnectivityStatus.Checking ||
                            assignedUi.loadingActive ||
                            !assignedUi.hasCompletedActiveRequest
                    )
    val backupRequestPending =
        networkStatus != ConnectivityStatus.Offline &&
                backupLoadError.isNullOrBlank() &&
                (
                    networkStatus == ConnectivityStatus.Checking ||
                            backupLoading ||
                            !hasCompletedBackupRequest
                    )

    val assignedSnapshotAgeMillis = assignedUi.assignedLastSuccessMillis?.let {
        System.currentTimeMillis() - it
    }
    val assignedActionsFresh =
        !isUsingCachedAssigned &&
                !confirmingAssignedEmpty &&
                assignedUi.assignedLastSuccessMillis != null &&
                assignedSnapshotAgeMillis != null &&
                assignedSnapshotAgeMillis in 0..30_000L &&
                assignedUi.assignedError.isNullOrBlank()

    LaunchedEffect(
        displayedAssignedDtos,
        assignedUi.assignedLastSuccessMillis,
        assignedActionsFresh,
        responderId
    ) {
        if (!assignedActionsFresh || responderId <= 0) return@LaunchedEffect

        val activeSession = activeRouteStore.read(responderId)
            ?: return@LaunchedEffect
        val matchingIncident = displayedAssignedDtos
            .asSequence()
            .map { it.toDomain() }
            .firstOrNull { incident ->
                ActiveRouteSessionStore.identityFor(
                    incidentId = incident.id,
                    assignmentId = incident.assignmentId
                ) == activeSession.sessionId
            }
        val responseEnded = matchingIncident == null ||
                matchingIncident.status == IncidentStatus.ON_SCENE ||
                matchingIncident.status == IncidentStatus.RESOLVED
        if (!responseEnded) return@LaunchedEffect

        context.stopService(Intent(context, RouteMonitoringService::class.java))
        val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
        if (
            navPrefs.getString("pending_en_route_session_id", null) ==
            activeSession.sessionId
        ) {
            navPrefs.edit()
                .putBoolean("pending_en_route_check", false)
                .remove("pending_en_route_incident_id")
                .remove("pending_en_route_session_id")
                .apply()
        }
        activeRouteStore.clearSession(responderId, activeSession.sessionId)
    }

    val activeIncidents = displayedActiveDtos.map { it.toDomain() }
    var isRefreshing by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dialogTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.Text,
        unfocusedTextColor = AppColors.Text,
        focusedBorderColor = AppColors.Primary,
        unfocusedBorderColor = AppColors.Border,
        focusedLabelColor = AppColors.Primary,
        unfocusedLabelColor = AppColors.TextSecondary,
        cursorColor = AppColors.Primary
    )

    fun cancelBackupRequest(id: Int) {
        if (!isNetworkAvailable) {
            Toast.makeText(
                context,
                "You’re offline. Reconnect before cancelling a backup request.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        scope.launch {
            val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
            val result = repo.cancelBackupRequest(requestId = id, responderId = responderId)

            if (result.isSuccess) {
                loadBackupRequests(showError = true)
                Toast.makeText(context, "Backup request cancelled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "Failed to cancel: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun refreshSingleBackupRequest(requestId: Int) {
        if (!isNetworkAvailable) {
            Toast.makeText(
                context,
                "You’re offline. Showing the last synced request status.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

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
                val savedAt = System.currentTimeMillis()
                lastBackupSyncMillis = savedAt
                backupLoadError = null
                homeDataCache.saveBackupRequests(responderId, backupRequestsList, savedAt)
                cachedBackupSnapshot = homeDataCache.readBackupRequests(responderId)
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

            // Upload to server so profile_image_path gets updated in the DB.
            // The local image remains available even when the device is offline.
            if (responderId > 0 && isNetworkAvailable) {
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
            } else if (responderId > 0) {
                Toast.makeText(
                    context,
                    "Photo saved on this device, but it was not uploaded because you’re offline.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    suspend fun loadBroadcasts(showError: Boolean = false) {
        if (responderId <= 0 || !isNetworkAvailable) return
        notificationRepository.getBroadcasts(responderId)
            .onSuccess { latest ->
                val ordered = latest.sortedWith(
                    compareByDescending<BroadcastNotice> { it.createdAtMillis }
                        .thenByDescending { it.id }
                )

                if (!hasPrimedBroadcastFeed) {
                    // The first load is history, not a new event. Priming prevents a
                    // notification storm after a fresh install or sign-in.
                    knownBroadcastIds.addAll(ordered.map { it.id })
                    hasPrimedBroadcastFeed = true
                } else {
                    ordered.asReversed()
                        .filter { notice ->
                            notice.id > 0L &&
                                    !notice.acknowledged &&
                                    notice.id !in knownBroadcastIds
                        }
                        .forEach { notice ->
                            AppNotificationManager.showBroadcast(
                                context = context.applicationContext,
                                eventKey = "broadcast:${notice.id}",
                                broadcastId = notice.id,
                                incidentId = notice.incidentId,
                                priority = notice.priority,
                                title = buildString {
                                    append("Emergency broadcast")
                                    notice.incidentReference
                                        .takeIf { it.isNotBlank() }
                                        ?.let { append(" • ").append(it) }
                                },
                                body = notice.message
                            )
                        }
                    knownBroadcastIds.addAll(ordered.map { it.id })
                }

                broadcastNotices = ordered
            }
            .onFailure { error ->
                if (showError) {
                    Toast.makeText(
                        context,
                        error.message ?: "Unable to refresh broadcasts",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun refreshHomeData() {
        if (isRefreshing || responderId <= 0) return
        if (!isNetworkAvailable) {
            Toast.makeText(
                context,
                "You’re offline. The app is showing last synced information.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        scope.launch {
            isRefreshing = true
            try {
                assignedVm.load(responderId)
                assignedVm.loadActive(responderId)
                loadBackupRequests(showError = true)
                loadBroadcasts(showError = true)
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(responderId, networkStatus) {
        if (responderId <= 0 || !isNetworkAvailable) {
            return@LaunchedEffect
        }

        loadBroadcasts()

        while (true) {
            delay(30_000L)
            loadBroadcasts()
        }
    }

    LaunchedEffect(notificationDestination, broadcastNotices) {
        val destination = notificationDestination as? NotificationDestination.Broadcast
            ?: return@LaunchedEffect
        val broadcast = broadcastNotices.firstOrNull { it.id == destination.broadcastId }
            ?: return@LaunchedEffect
        selectedBroadcastId = broadcast.id
        showNotificationsDialog = true
        NotificationNavigation.clear(destination)
    }

    LaunchedEffect(notificationDestination, displayedAssignedDtos) {
        val destination = notificationDestination as? NotificationDestination.AssignedIncident
            ?: return@LaunchedEffect
        val incident = displayedAssignedDtos.firstOrNull { candidate ->
            (destination.assignmentId.isNotBlank() &&
                    candidate.assignment_id == destination.assignmentId) ||
                    (destination.incidentId > 0L &&
                            candidate.id.toLongOrNull() == destination.incidentId)
        }

        newIncidentMessage = if (incident != null) {
            "${incident.type.uppercase()} incident assigned at ${incident.location}"
        } else {
            "New incident assigned. Dispatch details are loading."
        }
        showNewIncidentNotification = true
        // The Home screen is the assignment destination even when the server has
        // not returned the row yet. Clear the one-shot route and let normal polling
        // populate the incident card as soon as dispatch data is available.
        NotificationNavigation.clear(destination)
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
    val fusedClient       = remember { LocationServices.getFusedLocationProviderClient(context) }
    val latestNetworkAvailable by rememberUpdatedState(isNetworkAvailable)

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { loc ->
                    // Only push idle presence when NOT actively en route —
                    // RouteMonitoringService already owns the node during navigation.
                    if (
                        !RouteMonitoringService.isRunning &&
                        responderId > 0 &&
                        latestNetworkAvailable
                    ) {
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
                                "connectionState" to "connected",
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                        // A suspended Firebase socket must not change dispatch
                        // availability. Keep the last operational status and mark
                        // only the location feed as disconnected/stale.
                        dbRef.onDisconnect().updateChildren(
                            mapOf(
                                "connectionState" to "disconnected",
                                "updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                        )
                    }
                }
            }
        }
    }
    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {

                resumedFromBackground = true

                val preciseLocationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                hasLocationPermission = preciseLocationGranted
                if (!preciseLocationGranted) {
                    val routeTrackerWasRunning = RouteMonitoringService.isRunning
                    isLocationShared = false
                    isLocationMonitoringEnabled = false
                    prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
                    fusedClient.removeLocationUpdates(locationCallback)
                    // Keep the locked route session for Resume, but stop the
                    // foreground tracker so its notification/location cannot
                    // claim to be live after permission was revoked.
                    context.stopService(
                        Intent(context, RouteMonitoringService::class.java)
                    )
                    if (
                        !routeTrackerWasRunning &&
                        responderId > 0 &&
                        latestNetworkAvailable
                    ) {
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("live_locations")
                            .child("responder_$responderId")
                            .updateChildren(
                                mapOf(
                                    "connectionState" to "disconnected",
                                    "updatedAt" to com.google.firebase.database
                                        .ServerValue.TIMESTAMP
                                )
                            )
                    }
                }

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

    fun stopLocationUpdates() { fusedClient.removeLocationUpdates(locationCallback) }

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
    ): Boolean {
        val navPrefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)

        if (!ResponderWorkflowPolicy.hasValidCoordinates(lat, lng)) {
            Toast.makeText(
                context,
                "This incident does not have valid navigation coordinates.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        if (!viewOnly) {
            navPrefs.edit()
                .putString("last_nav_incident_id", incidentId.orEmpty())
                .putString("last_nav_assignment_id", assignmentId.orEmpty())
                .putString("last_nav_lat", lat.toString())
                .putString("last_nav_lng", lng.toString())
                .putString("last_nav_addr", address.orEmpty())
                .putInt("last_nav_responder_id", responderId)
                .apply()
        }

        navController.navigate(
            "live_map/$lat/$lng/${Uri.encode(address.orEmpty())}" +
                    "?incidentId=${Uri.encode(incidentId.orEmpty())}" +
                    "&assignmentId=${Uri.encode(assignmentId.orEmpty())}" +
                    "&responderId=$responderId&viewOnly=$viewOnly"
        ) {
            launchSingleTop = true
        }
        return true
    }

    fun markIncidentDone(
        incident: Incident,
        notes: String,
        proofUri: String?,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        if (!isNetworkAvailable) {
            onResult(false, "You’re offline. Reconnect before completing this incident.")
            return
        }

        val completionSessionId = ActiveRouteSessionStore.identityFor(
            incidentId = incident.id,
            assignmentId = incident.assignmentId
        )
        val latestIncident = completionSessionId?.let { expectedSessionId ->
            displayedAssignedDtos
                .asSequence()
                .map { it.toDomain() }
                .firstOrNull { latest ->
                    ActiveRouteSessionStore.identityFor(
                        incidentId = latest.id,
                        assignmentId = latest.assignmentId
                    ) == expectedSessionId
                }
        }
        if (
            !assignedActionsFresh ||
            latestIncident == null ||
            ResponderWorkflowPolicy.primaryAction(latestIncident.status) !=
            IncidentPrimaryAction.COMPLETE_INCIDENT
        ) {
            onResult(
                false,
                "This assignment changed. Refresh before submitting completion proof."
            )
            return
        }

        if (proofUri == null) {
            onResult(false, "Photo proof is required.")
            return
        }

        scope.launch {
            try {
                // markIncidentDone(...)
                val assignmentIdBody = stringToRequestBody(
                    latestIncident.assignmentId ?: latestIncident.id
                )
                val responderIdBody = stringToRequestBody(responderId.toString())
                val notesBody = stringToRequestBody(notes)
                val imagePart = uriStringToMultipartPart(context, "proof_image", "completion_proof", proofUri)

                val response = RetrofitProvider.incidentApi.markIncidentComplete(
                    assignmentIdBody, responderIdBody, notesBody, imagePart
                )

                if (response.success) {
                    val navPrefs = context.getSharedPreferences(
                        "nav_prefs",
                        Context.MODE_PRIVATE
                    )
                    val activeSession = activeRouteStore.read(responderId)
                    val pendingSessionId = navPrefs.getString(
                        "pending_en_route_session_id",
                        null
                    )
                    val completionOwnsActiveRoute =
                        activeSession?.sessionId == completionSessionId ||
                                (activeSession == null &&
                                        pendingSessionId == completionSessionId)

                    // Completing incident A must never terminate incident B's
                    // foreground tracking or discard B's locked route.
                    if (completionOwnsActiveRoute) {
                        context.stopService(
                            Intent(context, RouteMonitoringService::class.java)
                        )
                        navPrefs.edit()
                            .putBoolean("pending_en_route_check", false)
                            .remove("pending_en_route_incident_id")
                            .remove("pending_en_route_session_id")
                            .commit()
                        activeSession
                            ?.takeIf { it.sessionId == completionSessionId }
                            ?.let {
                                activeRouteStore.clearSession(responderId, it.sessionId)
                            }
                    }

                    assignedVm.load(responderId, force = true)
                    assignedVm.loadActive(responderId, force = true)

                    if (lastNotifiedIncidentId == latestIncident.id) {
                        lastNotifiedIncidentId = null
                        prefs.edit().remove("last_notified_incident_id").apply()
                    }
                    if (lastAssignedIncidentId == latestIncident.id) {
                        lastAssignedIncidentId = null
                        prefs.edit().remove("last_assigned_incident_id").apply()
                    }

                    Toast.makeText(context, "Incident marked completed", Toast.LENGTH_SHORT).show()
                    onResult(true, null)
                } else {
                    onResult(false, response.message ?: "Dispatch rejected the completion update.")
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Mark complete failed: ${e.message}")
                onResult(false, e.message ?: "Unable to submit completion proof.")
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
            } else if (pendingRouteLaunch != null) {
                pendingRouteLaunch = null
                Toast.makeText(
                    context,
                    "Turn on device location before starting navigation.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val preciseLocationGranted =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (preciseLocationGranted) {
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
            pendingRouteLaunch = null
            prefs.edit().putBoolean(locationMonitoringEnabledKey, false).apply()
            Toast.makeText(context, "Location permission denied - GPS features will not work", Toast.LENGTH_LONG).show()
        }
    }

    fun activateRoute(incident: Incident): Boolean {
        val latitude = incident.latitude
        val longitude = incident.longitude
        if (!ResponderWorkflowPolicy.hasValidCoordinates(latitude, longitude)) {
            Toast.makeText(
                context,
                "Dispatch has not provided valid coordinates for this incident.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val requestedSessionId = ActiveRouteSessionStore.identityFor(
            incidentId = incident.id,
            assignmentId = incident.assignmentId
        )
        val existingSession = activeRouteStore.read(responderId)
        if (
            existingSession != null &&
            existingSession.sessionId != requestedSessionId
        ) {
            Toast.makeText(
                context,
                "Another incident route is active. Finish or cancel it before switching responses.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val session = activeRouteStore.beginSession(
            ActiveRouteSessionMetadata(
                incidentId = incident.id,
                assignmentId = incident.assignmentId,
                responderId = responderId,
                destinationLat = latitude!!,
                destinationLng = longitude!!,
                destinationAddress = incident.location
            )
        )
        if (session == null) {
            Toast.makeText(
                context,
                "Unable to create a safe route session for this incident.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val serviceStarted = startRouteUpdateMonitoring(
            context = context,
            incidentId = session.incidentId,
            assignmentId = session.assignmentId,
            destLat = session.destinationLat,
            destLng = session.destinationLng,
            destAddress = session.destinationAddress
        )
        if (!serviceStarted) {
            if (existingSession == null) {
                activeRouteStore.clearSession(responderId, session.sessionId)
            }
            return false
        }

        context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pending_en_route_check", true)
            .putString("pending_en_route_incident_id", session.incidentId)
            .putString("pending_en_route_session_id", session.sessionId)
            .apply()

        return navigateToLocation(
            lat = session.destinationLat,
            lng = session.destinationLng,
            address = session.destinationAddress,
            incidentId = session.incidentId,
            assignmentId = session.assignmentId
        )
    }

    LaunchedEffect(
        pendingRouteLaunch,
        hasLocationPermission,
        deviceLocationEnabled
    ) {
        val pending = pendingRouteLaunch ?: return@LaunchedEffect
        val requestedSessionId = ActiveRouteSessionStore.identityFor(
            incidentId = pending.incidentId,
            assignmentId = pending.assignmentId
        )
        val incident = requestedSessionId?.let { sessionId ->
            displayedAssignedDtos
                .asSequence()
                .map { it.toDomain() }
                .firstOrNull { latest ->
                    ActiveRouteSessionStore.identityFor(
                        incidentId = latest.id,
                        assignmentId = latest.assignmentId
                    ) == sessionId
                }
        }
        val expectedAction = if (pending.updateStatusToEnRoute) {
            IncidentPrimaryAction.START_RESPONSE
        } else {
            IncidentPrimaryAction.RESUME_NAVIGATION
        }
        val currentActiveRoute = activeRouteStore.read(responderId)
        val hasMatchingStoredRoute =
            currentActiveRoute?.sessionId == requestedSessionId
        val hasConflictingActiveRoute =
            currentActiveRoute != null &&
                    currentActiveRoute.sessionId != requestedSessionId
        val routeActionAllowed = when (expectedAction) {
            IncidentPrimaryAction.START_RESPONSE ->
                assignedActionsFresh &&
                        isNetworkAvailable &&
                        !hasConflictingActiveRoute

            IncidentPrimaryAction.RESUME_NAVIGATION ->
                if (hasConflictingActiveRoute) {
                    false
                } else if (isNetworkAvailable) {
                    assignedActionsFresh
                } else {
                    hasMatchingStoredRoute
                }

            else -> false
        }

        if (
            incident == null ||
            ResponderWorkflowPolicy.primaryAction(incident.status) != expectedAction ||
            !routeActionAllowed
        ) {
            pendingRouteLaunch = null
            Toast.makeText(
                context,
                "This assignment changed while navigation was being prepared. Refresh and try again.",
                Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }

        if (!ResponderWorkflowPolicy.hasValidCoordinates(incident.latitude, incident.longitude)) {
            pendingRouteLaunch = null
            Toast.makeText(
                context,
                "Dispatch has not provided valid coordinates for this incident.",
                Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }

        if (!hasLocationPermission) {
            hasPromptedLocationOnce = true
            prefs.edit().putBoolean("location_permission_prompted", true).apply()
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
            return@LaunchedEffect
        }

        if (!deviceLocationEnabled) {
            locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return@LaunchedEffect
        }

        pendingRouteLaunch = null
        isRouteActionIncidentId = incident.id

        if (pending.updateStatusToEnRoute) {
            assignedVm.updateStatus(
                assignmentId = incident.assignmentId ?: incident.id,
                status = "en_route",
                responderId = responderId
            ) { success, _ ->
                if (success) {
                    activateRoute(incident)
                }
                isRouteActionIncidentId = null
            }
        } else {
            activateRoute(incident)
            isRouteActionIncidentId = null
        }
    }



    fun sendBackupRequest(request: BackupRequest) {
        if (!isNetworkAvailable) {
            Toast.makeText(
                context,
                "You’re offline. Reconnect before requesting backup.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!assignedActionsFresh) {
            Toast.makeText(
                context,
                "Refresh assignments before requesting incident backup.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (request.fromIncidentId.isBlank()) {
            Toast.makeText(
                context,
                "Select an active incident before requesting backup.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val currentIncident = displayedAssignedDtos
            .asSequence()
            .map { it.toDomain() }
            .firstOrNull { it.id == request.fromIncidentId }
        if (
            currentIncident == null ||
            ResponderWorkflowPolicy.primaryAction(currentIncident.status) ==
            IncidentPrimaryAction.NONE
        ) {
            Toast.makeText(
                context,
                "That incident is no longer an active assignment. Refresh and select again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val deptName = when (request.department.shortCode) {
            "FIRE" -> "Fire Department"
            "MED" -> "Medical Department"
            "POL" -> "Police Department"
            else -> "Emergency Services"
        }

        val selectedBackupNeeds = if (request.isFullBackup) {
            "Full ${request.department.displayName} response package"
        } else {
            request.resources.joinToString(", ") { it.label }
        }
        val backupNeedSummary = buildString {
            append(selectedBackupNeeds)
            request.specificDetails.trim().takeIf { it.isNotBlank() }?.let { details ->
                append(" • Details: ")
                append(details)
            }
        }

        scope.launch {
            try {
                val repo = com.ers.emergencyresponseapp.data.IncidentRepository()

                val result = repo.sendBackupRequest(
                    responderId = responderId,
                    responderName = responderName,
                    department = effectiveRole ?: "",
                    requestedDepartment = deptName,
                    resources = backupNeedSummary,
                    isFullBackup = request.isFullBackup,
                    incidentId = request.fromIncidentId
                )

                if (result.isSuccess) {
                    loadBackupRequests(showError = true)
                    Toast.makeText(context, "Backup request sent: $selectedBackupNeeds", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("BackupRequest", "Failed: responderId=$responderId, backupNeed=$backupNeedSummary, error=${error?.message}")
                    Toast.makeText(context, "Failed: ${error?.message}", Toast.LENGTH_LONG).show()
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
        containerColor = AppColors.Bg,
        contentColor = AppColors.Text,
        topBar = {},
        floatingActionButton = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
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
                                containerColor = AppColors.DangerSurface
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
                                    tint = AppColors.DangerText,
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "⚠️ GPS Location Disabled",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = AppColors.DangerText
                                    )

                                    Text(
                                        "Turn ON location in device settings. GPS is required for emergency response.",
                                        fontSize = 11.sp,
                                        color = AppColors.DangerText,
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
                    containerColor = AppColors.CardBg,
                    titleContentColor = AppColors.Text,
                    textContentColor = AppColors.Text,
                    icon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppColors.Primary)
                    },
                    title = { Text("GPS Location Required", fontWeight = FontWeight.Bold, color = AppColors.DangerText) },
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
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
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
            val disasterCount = remember(activeIncidents) { activeIncidents.count { it.type == IncidentType.DISASTER } }
            val generalCount = remember(activeIncidents) { activeIncidents.count { it.type == IncidentType.GENERAL } }


            val assignedListForRole =
                displayedAssignedDtos.map { it.toDomain() }

            val backupIncidentOptions = remember(assignedListForRole) {
                assignedListForRole
                    .filter { it.status !in setOf(IncidentStatus.RESOLVED, IncidentStatus.UNKNOWN) }
                    .map { incident ->
                        BackupIncidentOption(
                            incidentId = incident.id,
                            label = "#${incident.id} • ${incident.type.displayName} • ${incident.location}"
                        )
                    }
            }

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

                val assignmentKey = incident.assignment_id.orEmpty()
                    .ifBlank { incident.id }
                val assignmentEventKey = "assigned:$assignmentKey"

                if (isOnHomeScreen) {
                    // Reserve the same event key used by FCM. Whichever source
                    // arrives first (push or polling) owns the one visible alert.
                    if (AppNotificationManager.claimEvent(context, assignmentEventKey)) {
                        showNewIncidentNotification = true
                        vibratePhone(context)
                    }
                } else {
                    // OTHER SCREEN / BACKGROUND = Android notification only
                    showNewIncidentNotification = false

                    AppNotificationManager.showAssignedIncident(
                        context = context,
                        eventKey = assignmentEventKey,
                        assignmentId = incident.assignment_id.orEmpty(),
                        incidentId = incident.id.toLongOrNull() ?: 0L,
                        incidentReference = incident.id,
                        incidentType = incident.type,
                        priority = incident.priority.orEmpty(),
                        location = incident.location,
                        body = newIncidentMessage
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


            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = ::refreshHomeData,
                modifier = Modifier.fillMaxSize(),
                indicatorTopPadding = when {
                    showCriticalGpsWarning -> 112.dp
                    showNewIncidentNotification -> 120.dp
                    else -> 8.dp
                }
            ) {
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
                                    HeaderNotificationButton(
                                        count = notificationCount + unreadBroadcastCount,
                                        onClick = {
                                            showNotificationsDialog = true
                                            notificationCount = 0
                                        }
                                    )

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Assigned Incidents",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Text,
                                modifier = Modifier.weight(1f)
                            )

                            if (assignedListForRole.isNotEmpty()) {
                                Surface(
                                    color = AppColors.Primary.copy(
                                        alpha = if (ThemeController.isDarkMode.value) 0.22f else 0.10f
                                    ),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = assignedListForRole.size.toString(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = if (ThemeController.isDarkMode.value) {
                                            Color(0xFF9AD2D0)
                                        } else {
                                            AppColors.Primary
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (assignedListForRole.isEmpty()) {
                            AssignedIncidentEmptyState(
                                networkStatus = networkStatus,
                                loading = assignedUi.loadingAssigned,
                                requestCompleted = assignedUi.hasCompletedAssignedRequest,
                                serverError = assignedUi.assignedError,
                                lastSyncMillis = assignedLastSyncMillis,
                                onRetry = ::refreshHomeData
                            )
                        } else {
                            if (
                                isUsingCachedAssigned ||
                                networkStatus == ConnectivityStatus.Offline ||
                                !assignedUi.assignedError.isNullOrBlank()
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (networkStatus == ConnectivityStatus.Offline) {
                                        AppColors.DangerSurface
                                    } else {
                                        AppColors.DispatchSurface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (networkStatus == ConnectivityStatus.Offline) {
                                            AppColors.DangerText.copy(alpha = 0.22f)
                                        } else {
                                            AppColors.DispatchText.copy(alpha = 0.22f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (networkStatus == ConnectivityStatus.Offline) {
                                                Icons.Default.CloudOff
                                            } else {
                                                Icons.Default.Warning
                                            },
                                            contentDescription = null,
                                            tint = if (networkStatus == ConnectivityStatus.Offline) {
                                                AppColors.DangerText
                                            } else {
                                                AppColors.DispatchText
                                            },
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Showing last known assignments",
                                                color = AppColors.Text,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${formatLastSyncLabel(assignedLastSyncMillis)}. Confirm urgent changes with dispatch.",
                                                color = AppColors.TextSecondary,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                            assignedListForRole.forEach { inc ->
                                val timeLabel = timeAgoLabel(inc.timeReported)
                                val incidentSessionId = ActiveRouteSessionStore.identityFor(
                                    incidentId = inc.id,
                                    assignmentId = inc.assignmentId
                                )
                                val activeSessionForActions = activeRouteStore.read(responderId)
                                val hasStoredRoute = incidentSessionId != null &&
                                        activeSessionForActions?.sessionId == incidentSessionId
                                val hasConflictingActiveRoute =
                                    activeSessionForActions != null &&
                                            activeSessionForActions.sessionId != incidentSessionId

                                val isDarkTheme = ThemeController.isDarkMode.value
                                val priorityColor = when (inc.priority) {
                                    IncidentPriority.HIGH -> if (isDarkTheme) {
                                        Color(0xFFFF8A80)
                                    } else {
                                        Color(0xFFD32F2F)
                                    }
                                    IncidentPriority.MEDIUM -> if (isDarkTheme) {
                                        Color(0xFFFFCC80)
                                    } else {
                                        Color(0xFFEF6C00)
                                    }
                                    IncidentPriority.LOW -> if (isDarkTheme) {
                                        Color(0xFF81C784)
                                    } else {
                                        Color(0xFF2E7D32)
                                    }
                                    IncidentPriority.UNKNOWN -> AppColors.TextSecondary
                                }
                                val incidentAccent = when (inc.type) {
                                    IncidentType.FIRE -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFE53935)
                                    IncidentType.MEDICAL -> if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1E88E5)
                                    IncidentType.CRIME -> if (isDarkTheme) Color(0xFFBCAAA4) else Color(0xFF6D4C41)
                                    IncidentType.DISASTER -> if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF8E24AA)
                                    IncidentType.GENERAL -> AppColors.Primary
                                }
                                val incidentIcon = when (inc.type) {
                                    IncidentType.FIRE -> Icons.Default.LocalFireDepartment
                                    IncidentType.MEDICAL -> Icons.Default.LocalHospital
                                    IncidentType.CRIME -> Icons.Default.Security
                                    IncidentType.DISASTER -> Icons.Default.Warning
                                    IncidentType.GENERAL -> Icons.Default.Info
                                }
                                val statusLabel = when (inc.status.name.lowercase()) {
                                    "reported" -> "Received"
                                    "assigned" -> "Assigned"
                                    "received" -> "Received"
                                    "en_route" -> "En Route"
                                    "on_scene" -> "On Scene"
                                    "completed" -> "Completed"
                                    else -> inc.status.displayName
                                }
                                val statusColor = when (inc.status.name.lowercase()) {
                                    "en_route" -> if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1565C0)
                                    "on_scene" -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    "completed" -> AppColors.TextSecondary
                                    else -> if (isDarkTheme) Color(0xFF80CBC4) else AppColors.Primary
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                                    elevation = CardDefaults.cardElevation(1.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        priorityColor.copy(alpha = if (isDarkTheme) 0.42f else 0.28f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(assignedCardBrush())
                                            .padding(16.dp),
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
                                                    .background(
                                                        incidentAccent.copy(
                                                            alpha = if (isDarkTheme) 0.20f else 0.11f
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = incidentIcon,
                                                    contentDescription = inc.type.displayName,
                                                    tint = incidentAccent,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${inc.type.displayName} Incident",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = AppColors.Text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(Modifier.height(3.dp))

                                                Text(
                                                    text = "Reported $timeLabel",
                                                    fontSize = 12.sp,
                                                    color = AppColors.TextSecondary
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(
                                                        priorityColor.copy(
                                                            alpha = if (isDarkTheme) 0.20f else 0.11f
                                                        )
                                                    )
                                                    .border(
                                                        1.dp,
                                                        priorityColor.copy(alpha = 0.62f),
                                                        RoundedCornerShape(999.dp)
                                                    )
                                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = inc.priority.name.uppercase(),
                                                    color = priorityColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = incidentAccent.copy(
                                                alpha = if (isDarkTheme) 0.13f else 0.07f
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                incidentAccent.copy(
                                                    alpha = if (isDarkTheme) 0.35f else 0.20f
                                                )
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 13.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            incidentAccent.copy(
                                                                alpha = if (isDarkTheme) 0.22f else 0.12f
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = incidentAccent,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }

                                                Spacer(Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "INCIDENT LOCATION",
                                                        color = incidentAccent,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        letterSpacing = 0.5.sp
                                                    )

                                                    Spacer(Modifier.height(3.dp))

                                                    Text(
                                                        text = inc.location.ifBlank { "Unknown location" },
                                                        color = AppColors.Text,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 21.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (inc.latitude == null || inc.longitude == null) {
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = "Map coordinates are not yet available",
                                                            color = AppColors.TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = statusColor.copy(
                                                    alpha = if (isDarkTheme) 0.19f else 0.10f
                                                ),
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    statusColor.copy(alpha = 0.38f)
                                                )
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    modifier = Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 5.dp
                                                    ),
                                                    color = statusColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(Modifier.weight(1f))

                                            Text(
                                                text = "Incident #${inc.id}",
                                                color = AppColors.TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "INCIDENT DETAILS",
                                                color = AppColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.4.sp
                                            )

                                            Text(
                                                text = inc.description.ifBlank {
                                                    "No additional incident details were provided."
                                                },
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                color = AppColors.Text.copy(alpha = 0.92f),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        HorizontalDivider(color = AppColors.Border)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Assigned unit",
                                                color = AppColors.TextSecondary,
                                                fontSize = 11.sp
                                            )

                                            Spacer(Modifier.width(8.dp))

                                            Text(
                                                text = "${unitCode.ifBlank { "Unit" }} • " +
                                                        unitType.ifBlank { "Responder Unit" },
                                                modifier = Modifier.weight(1f),
                                                color = AppColors.TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        AssignedActionButtons(
                                            inc = inc,
                                            dataFresh = assignedActionsFresh,
                                            networkAvailable = isNetworkAvailable,
                                            hasStoredRoute = hasStoredRoute,
                                            hasConflictingActiveRoute =
                                                hasConflictingActiveRoute,
                                            isAnyRouteActionInProgress =
                                                pendingRouteLaunch != null ||
                                                    isRouteActionIncidentId != null,
                                            isRouteActionInProgress =
                                                pendingRouteLaunch?.incidentId == inc.id ||
                                                    isRouteActionIncidentId == inc.id,
                                            onStartResponse = { incident ->
                                                pendingRouteLaunch = PendingRouteLaunch(
                                                    incidentId = incident.id,
                                                    assignmentId = incident.assignmentId,
                                                    updateStatusToEnRoute = true
                                                )
                                            },
                                            onResumeNavigation = { incident ->
                                                pendingRouteLaunch = PendingRouteLaunch(
                                                    incidentId = incident.id,
                                                    assignmentId = incident.assignmentId,
                                                    updateStatusToEnRoute = false
                                                )
                                            },
                                            openMarkDone = {
                                                selectedProofFile?.delete()
                                                selectedProofFile = null
                                                markTargetIncidentInc = it
                                                proofNotes = ""
                                                selectedProofUri = null
                                                isSubmittingCompletion = false
                                                completionError = null
                                                showMarkCompleteDialog = true
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
                        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
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
                                        "${visibleBackupRequests.size} total • ${formatLastSyncLabel(lastBackupSyncMillis)}",
                                        fontSize = 11.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                IconButton(
                                    enabled = isNetworkAvailable,
                                    onClick = {
                                        scope.launch { loadBackupRequests(showError = true) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Action buttons row
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = isNetworkAvailable &&
                                            assignedActionsFresh &&
                                            backupIncidentOptions.isNotEmpty(),
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(1.dp, AppColors.Border),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppColors.Text
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "View All",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )

                                        NotificationCountBadge(
                                            count = pendingExtraBackupCount,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = (-5).dp, y = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (
                                networkStatus == ConnectivityStatus.Offline ||
                                backupLoadError != null ||
                                backupEmptySuccessStreak == 1
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (networkStatus == ConnectivityStatus.Offline) {
                                        AppColors.DangerSurface
                                    } else {
                                        AppColors.DispatchSurface
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            networkStatus == ConnectivityStatus.Offline ->
                                                "Offline • Showing last synced backup requests"
                                            backupLoadError != null ->
                                                "Unable to refresh • Showing last known request status"
                                            else ->
                                                "Confirming dispatch update • Showing last known requests"
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = if (networkStatus == ConnectivityStatus.Offline) {
                                            AppColors.DangerText
                                        } else {
                                            AppColors.DispatchText
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (visibleBackupRequests.isEmpty()) {
                                Text(
                                    text = when {
                                        networkStatus == ConnectivityStatus.Offline && cachedBackupSnapshot == null ->
                                            "Backup request history is unavailable offline."
                                        backupLoadError != null && cachedBackupSnapshot == null ->
                                            "Unable to load backup requests from the server."
                                        backupRequestPending ->
                                            "Checking backup requests…"
                                        else ->
                                            "No backup requests yet."
                                    },
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    visibleBackupRequests.take(1).forEach { req ->
                                        BackupRequestStatusCard(
                                            department = req.requested_department,
                                            backupNeed = req.resources,
                                            status = req.status,
                                            onCancelClick = {
                                                pendingCancelBackupId = req.id
                                                showCancelBackupConfirm = true
                                            },
                                            onRefreshClick = { refreshSingleBackupRequest(req.id) },
                                            actionsEnabled = isNetworkAvailable
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
                                    refreshHomeData()
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

                        if (
                            isUsingCachedActive ||
                            networkStatus == ConnectivityStatus.Offline ||
                            !assignedUi.activeError.isNullOrBlank()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (networkStatus == ConnectivityStatus.Offline) {
                                    AppColors.DangerSurface
                                } else {
                                    AppColors.DispatchSurface
                                }
                            ) {
                                Text(
                                    text = "Showing last known active incidents • ${formatLastSyncLabel(activeLastSyncMillis)}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = if (networkStatus == ConnectivityStatus.Offline) {
                                        AppColors.DangerText
                                    } else {
                                        AppColors.DispatchText
                                    },
                                    fontSize = 11.sp,
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
                        activeIncidents.filter { inc ->
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
                    if (activeRequestPending && activeIncidents.isEmpty()) {
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
                                        containerColor = AppColors.Skeleton
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
                                        imageVector = when {
                                            networkStatus == ConnectivityStatus.Offline -> Icons.Default.CloudOff
                                            !assignedUi.activeError.isNullOrBlank() -> Icons.Default.Warning
                                            else -> Icons.Default.Done
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            networkStatus == ConnectivityStatus.Offline -> AppColors.DangerText
                                            !assignedUi.activeError.isNullOrBlank() -> AppColors.DispatchText
                                            else -> AppColors.Primary
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = when {
                                        networkStatus == ConnectivityStatus.Offline -> "Active incidents unavailable offline"
                                        !assignedUi.activeError.isNullOrBlank() -> "Unable to refresh active incidents"
                                        else -> "No active incidents"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Text
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = when {
                                        networkStatus == ConnectivityStatus.Offline ->
                                            "Reconnect to verify whether other incidents are currently active."
                                        !assignedUi.activeError.isNullOrBlank() ->
                                            "The dispatch server did not respond. Try again when the service is available."
                                        else ->
                                            "Other assigned incidents will appear here for awareness."
                                    },
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(16.dp))

                                Surface(
                                    color = when {
                                        networkStatus == ConnectivityStatus.Offline -> AppColors.DangerSurface
                                        !assignedUi.activeError.isNullOrBlank() -> AppColors.DispatchSurface
                                        else -> AppColors.Primary.copy(alpha = 0.08f)
                                    },
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = when {
                                            networkStatus == ConnectivityStatus.Offline -> "Offline • ${formatLastSyncLabel(activeLastSyncMillis)}"
                                            !assignedUi.activeError.isNullOrBlank() -> "Unable to sync • ${formatLastSyncLabel(activeLastSyncMillis)}"
                                            else -> "Monitoring dispatch updates"
                                        },
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        color = when {
                                            networkStatus == ConnectivityStatus.Offline -> AppColors.DangerText
                                            !assignedUi.activeError.isNullOrBlank() -> AppColors.DispatchText
                                            else -> AppColors.Primary
                                        },
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
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
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
                                    val isDarkTheme = ThemeController.isDarkMode.value
                                    val priorityColor = when (inc.priority) {
                                        IncidentPriority.HIGH -> if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFD32F2F)
                                        IncidentPriority.MEDIUM -> if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00)
                                        IncidentPriority.LOW -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                                        IncidentPriority.UNKNOWN -> AppColors.TextSecondary
                                    }
                                    val accent = when (inc.type) {
                                        IncidentType.FIRE -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFE53935)
                                        IncidentType.MEDICAL -> if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1E88E5)
                                        IncidentType.CRIME -> if (isDarkTheme) Color(0xFFBCAAA4) else Color(0xFF6D4C41)
                                        IncidentType.DISASTER -> if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF8E24AA)
                                        IncidentType.GENERAL -> AppColors.Primary
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true },
                                            onLongClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true }
                                        ),
                                        shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(0.dp),colors = CardDefaults.cardColors(containerColor = AppColors.CardBg), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
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
                                                            IncidentType.GENERAL -> Icons.Default.Info
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
                                                        inc.timeReported.time > 0L &&
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
                    val isDarkTheme = ThemeController.isDarkMode.value
                    val typeLabels = remember { listOf("Fire", "Medical", "Crime", "Disaster", "General") }
                    val typeAccents = remember(isDarkTheme) {
                        if (isDarkTheme) {
                            listOf(
                                Color(0xFFEF5350),
                                Color(0xFF64B5F6),
                                Color(0xFFBCAAA4),
                                Color(0xFFCE93D8),
                                Color(0xFF90A4AE)
                            )
                        } else {
                            listOf(
                                Color(0xFFE53935),
                                Color(0xFF1E88E5),
                                Color(0xFF6D4C41),
                                Color(0xFF8E24AA),
                                Color(0xFF546E7A)
                            )
                        }
                    }
                    val typeIcons = remember {
                        listOf(
                            Icons.Default.LocalFireDepartment,
                            Icons.Default.LocalHospital,
                            Icons.Default.Security,
                            Icons.Default.Warning,
                            Icons.Default.Info
                        )
                    }
                    // typeCounts references derived state; no extra remember is needed.
                    val typeCounts = listOf(
                        fireCount,
                        medicalCount,
                        crimeCount,
                        disasterCount,
                        generalCount
                    )
                    val cardBrushes = remember(isDarkTheme) { cardBrushesStable() }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(typeAccents.size) { i ->
                            val accent = typeAccents[i]
                            Card(
                                modifier = Modifier.width(132.dp),
                                shape = RoundedCornerShape(22.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
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
            }


            // ── ALL ACTIVE DIALOG ──
            if (showAllActiveDialog) {
                AlertDialog(
                    onDismissRequest = { showAllActiveDialog = false },
                    containerColor = AppColors.CardBg,
                    titleContentColor = AppColors.Text,
                    textContentColor = AppColors.Text,
                    title = { Text("All Active Incidents", color = AppColors.Text) },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                items = activeIncidents,
                                key = { it.id }  // FIX 11: stable keys
                            ) { inc ->
                                val isDarkTheme = ThemeController.isDarkMode.value
                                val priorityColor = when (inc.priority) {
                                    IncidentPriority.HIGH -> if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFD32F2F)
                                    IncidentPriority.MEDIUM -> if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00)
                                    IncidentPriority.LOW -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    IncidentPriority.UNKNOWN -> AppColors.TextSecondary
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true; showAllActiveDialog = false },
                                        onLongClick = { selectedActiveIncident = inc; showActiveDetailsSheet = true; showAllActiveDialog = false }
                                    ),
                                    shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp),colors = CardDefaults.cardColors(containerColor = AppColors.CardBg)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(inc.type.displayName, fontWeight = FontWeight.SemiBold, color = AppColors.Text, modifier = Modifier.weight(1f))
                                            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(priorityColor.copy(0.12f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text(inc.priority.name, color = priorityColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp)); Text(inc.location.ifBlank { "Unknown location" }, color = AppColors.Text)
                                        Spacer(Modifier.height(6.dp)); Text(inc.description, color = AppColors.Text.copy(alpha = 0.90f), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        color = AppColors.Bg,
                        contentColor = AppColors.Text
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
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.Text)
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = backupSearchQuery,
                                onValueChange = { backupSearchQuery = it },
                                placeholder = {
                                    Text(
                                        "Search department or backup need",
                                        color = AppColors.TextSecondary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = AppColors.TextSecondary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = dialogTextFieldColors
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
                                        backupNeed = req.resources,
                                        status = req.status,
                                        onCancelClick = {
                                            pendingCancelBackupId = req.id
                                            showCancelBackupConfirm = true
                                        },
                                        onRefreshClick = { refreshSingleBackupRequest(req.id) },
                                        actionsEnabled = isNetworkAvailable
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
                    containerColor = AppColors.CardBg,
                    titleContentColor = AppColors.Text,
                    textContentColor = AppColors.Text,
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.DangerText) },
                    title = { Text("Cancel Backup Request?", fontWeight = FontWeight.Bold, color = AppColors.Text) },
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
                        if (!isSubmittingCompletion) {
                            pendingCameraFile?.delete()
                            selectedProofFile?.delete()
                            pendingCameraFile = null
                            selectedProofFile = null
                            pendingCameraUri = null
                            showMarkCompleteDialog = false
                            markTargetIncidentInc = null
                            proofNotes = ""
                            selectedProofUri = null
                            completionError = null
                        }
                    },
                    containerColor = AppColors.CardBg,
                    titleContentColor = AppColors.Text,
                    textContentColor = AppColors.Text,
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
                                enabled = !isSubmittingCompletion,
                                label = { Text("Completion notes (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                colors = dialogTextFieldColors
                            )

                            Button(
                                onClick = {
                                    openCompletionCamera()
                                },
                                enabled = !isSubmittingCompletion,
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
                                    color = AppColors.DangerText,
                                    fontSize = 12.sp
                                )
                            }

                            completionError?.let { message ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppColors.DangerSurface
                                ) {
                                    Text(
                                        text = "$message Your photo and notes were kept; you can retry.",
                                        modifier = Modifier.padding(12.dp),
                                        color = AppColors.DangerText,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            selectedProofUri?.let { uriStr ->
                                val bitmap = remember(uriStr) {
                                    decodeSampledProofBitmap(context, uriStr)
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
                                        color = AppColors.SuccessText,
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
                                    isSubmittingCompletion = true
                                    completionError = null
                                    markIncidentDone(
                                        inc,
                                        proofNotes,
                                        selectedProofUri
                                    ) { success, message ->
                                        isSubmittingCompletion = false
                                        if (success) {
                                            pendingCameraFile?.delete()
                                            selectedProofFile?.delete()
                                            pendingCameraFile = null
                                            selectedProofFile = null
                                            pendingCameraUri = null
                                            showMarkCompleteDialog = false
                                            markTargetIncidentInc = null
                                            proofNotes = ""
                                            selectedProofUri = null
                                            completionError = null
                                        } else {
                                            completionError = message
                                                ?: "Unable to complete this incident."
                                        }
                                    }
                                }
                            },
                            enabled = hasProof && !isSubmittingCompletion,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isSubmittingCompletion) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Submitting…")
                            } else {
                                Text(if (completionError == null) "Submit" else "Retry")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !isSubmittingCompletion,
                            onClick = {
                                pendingCameraFile?.delete()
                                selectedProofFile?.delete()
                                pendingCameraFile = null
                                selectedProofFile = null
                                pendingCameraUri = null
                                showMarkCompleteDialog = false
                                markTargetIncidentInc = null
                                proofNotes = ""
                                selectedProofUri = null
                                completionError = null
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
                        add("${inc.type.displayName} incident assigned • ${inc.location.ifBlank { "Unknown location" }}")
                        inc.description.takeIf { it.isNotBlank() }?.let(::add)
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

                val orderedBroadcasts = remember(broadcastNotices, selectedBroadcastId) {
                    broadcastNotices.sortedWith(
                        compareByDescending<BroadcastNotice> { it.id == selectedBroadcastId }
                            .thenBy { it.acknowledged }
                            .thenByDescending { it.createdAtMillis }
                            .thenByDescending { it.id }
                    )
                }

                Dialog(
                    onDismissRequest = {
                        showNotificationsDialog = false
                        selectedBroadcastId = null
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.88f)
                            .padding(horizontal = 14.dp, vertical = 18.dp),
                        color = AppColors.CardBg,
                        contentColor = AppColors.Text,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, AppColors.Border),
                        shadowElevation = 14.dp
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(
                                        "$unreadBroadcastCount unacknowledged broadcast${if (unreadBroadcastCount == 1) "" else "s"}",
                                        color = AppColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = {
                                    showNotificationsDialog = false
                                    selectedBroadcastId = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }

                            HorizontalDivider(color = AppColors.Border)

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (orderedBroadcasts.isNotEmpty()) {
                                    item(key = "broadcast_header") {
                                        Text(
                                            "EMERGENCY BROADCASTS",
                                            color = AppColors.DispatchText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    items(orderedBroadcasts, key = { "broadcast_${it.id}" }) { broadcast ->
                                        BroadcastNoticeCard(
                                            broadcast = broadcast,
                                            highlighted = broadcast.id == selectedBroadcastId,
                                            onAcknowledge = {
                                                if (!broadcast.acknowledged) {
                                                    if (!isNetworkAvailable) {
                                                        Toast.makeText(
                                                            context,
                                                            "You’re offline. Reconnect before acknowledging this broadcast.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        scope.launch {
                                                            notificationRepository
                                                                .acknowledgeBroadcast(responderId, broadcast.id)
                                                                .onSuccess {
                                                                    broadcastNotices = broadcastNotices.map { current ->
                                                                        if (current.id == broadcast.id) {
                                                                            current.copy(acknowledged = true)
                                                                        } else current
                                                                    }
                                                                }
                                                                .onFailure { error ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        error.message ?: "Unable to acknowledge broadcast",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                if (notificationItems.isNotEmpty()) {
                                    item(key = "operational_header") {
                                        Text(
                                            "OPERATIONAL STATUS",
                                            color = AppColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(top = if (orderedBroadcasts.isEmpty()) 0.dp else 6.dp)
                                        )
                                    }
                                    items(notificationItems, key = { "status_${it.hashCode()}" }) { item ->
                                        Surface(
                                            color = AppColors.SubtleSurface,
                                            shape = RoundedCornerShape(13.dp),
                                            border = BorderStroke(1.dp, AppColors.Border)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(11.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    Icons.Default.Notifications,
                                                    contentDescription = null,
                                                    tint = AppColors.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(item, color = AppColors.Text, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                if (orderedBroadcasts.isEmpty() && notificationItems.isEmpty()) {
                                    item(key = "empty_notifications") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 38.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = AppColors.TextSecondary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("No notifications.", color = AppColors.TextSecondary)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = AppColors.Border)
                            TextButton(
                                onClick = {
                                    notificationCount = 0
                                    selectedBroadcastId = null
                                    showNotificationsDialog = false
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("Close", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── ACTIVE DETAILS SHEET ──
            if (showActiveDetailsSheet && selectedActiveIncident != null) {
                val inc = selectedActiveIncident!!
                val isDarkTheme = ThemeController.isDarkMode.value

                val accent = when (inc.type) {
                    IncidentType.FIRE -> if (isDarkTheme) Color(0xFFEF5350) else Color(0xFFE53935)
                    IncidentType.MEDICAL -> if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1E88E5)
                    IncidentType.CRIME -> if (isDarkTheme) Color(0xFFBCAAA4) else Color(0xFF6D4C41)
                    IncidentType.DISASTER -> if (isDarkTheme) Color(0xFFCE93D8) else Color(0xFF8E24AA)
                    IncidentType.GENERAL -> AppColors.Primary
                }

                val incidentIcon = when (inc.type) {
                    IncidentType.FIRE -> Icons.Default.LocalFireDepartment
                    IncidentType.MEDICAL -> Icons.Default.LocalHospital
                    IncidentType.CRIME -> Icons.Default.Security
                    IncidentType.DISASTER -> Icons.Default.Warning
                    IncidentType.GENERAL -> Icons.Default.Info
                }

                val priorityColor = when (inc.priority) {
                    IncidentPriority.HIGH -> if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFD32F2F)
                    IncidentPriority.MEDIUM -> if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00)
                    IncidentPriority.LOW -> if (isDarkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                    IncidentPriority.UNKNOWN -> AppColors.TextSecondary
                }

                ModalBottomSheet(
                    onDismissRequest = {
                        showActiveDetailsSheet = false
                        selectedActiveIncident = null
                    },
                    sheetState = sheetState,
                    containerColor = AppColors.CardBg,
                    contentColor = AppColors.Text
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
                                containerColor = AppColors.SubtleSurface
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
                                        color = AppColors.Text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 20.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AppColors.SubtleSurface
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
                    incidentOptions = backupIncidentOptions,
                    onDepartmentSelected = { _: String -> showDepartmentSelection = false },
                    onBackupRequestReady = { request: BackupRequest ->
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
                    onOpenGuide = {
                        showSettingsDialog = false
                        navController.navigate("how_to_use") {
                            launchSingleTop = true
                        }
                    },
                    onSave = {
                        prefs.edit()
                            .putString("account_full_name", accountFullName.trim())
                            .putString("account_username", accountUsername.trim())
                            .putString("account_email", accountEmail.trim())
                            .putString("account_photo", accountPhotoUri)
                            .apply()
                        if (accountUsername.isNotBlank()) responderName = accountUsername.trim()

                        if (responderId > 0 && isNetworkAvailable) {
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
                        } else if (responderId > 0) {
                            Toast.makeText(
                                context,
                                "Profile changes were saved on this device, but not synced because you’re offline.",
                                Toast.LENGTH_LONG
                            ).show()
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


@Composable
private fun BroadcastNoticeCard(
    broadcast: BroadcastNotice,
    highlighted: Boolean,
    onAcknowledge: () -> Unit
) {
    val dark = ThemeController.isDarkMode.value
    val priorityColor = when (broadcast.priority.lowercase()) {
        "critical" -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        "urgent" -> if (dark) Color(0xFFFFCC80) else Color(0xFFEF6C00)
        else -> if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = priorityColor.copy(alpha = if (dark) 0.16f else 0.07f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            priorityColor.copy(alpha = if (highlighted) 0.82f else 0.36f)
        )
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(priorityColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = priorityColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        broadcast.priority.uppercase(Locale.getDefault()) + " BROADCAST",
                        color = priorityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        listOf(broadcast.incidentType, broadcast.incidentReference)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                            .ifBlank { "Operational broadcast" },
                        color = AppColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (broadcast.acknowledged) {
                    Surface(
                        color = AppColors.SuccessSurface,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            "ACKNOWLEDGED",
                            color = AppColors.SuccessText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                broadcast.message,
                color = AppColors.Text,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            if (broadcast.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = priorityColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        broadcast.location,
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(broadcast.createdByName, broadcast.createdAt)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    color = AppColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!broadcast.acknowledged) {
                    Button(
                        onClick = onAcknowledge,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = priorityColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("Acknowledge", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    context: Context,
    fullName: String, username: String, email: String, photoUri: String?,
    isDarkMode: Boolean,
    onFullNameChange: (String) -> Unit, onUsernameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit, onPickPhoto: () -> Unit,
    onOpenGuide: () -> Unit,
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
        containerColor = AppColors.ElevatedSurface,
        titleContentColor = AppColors.Text,
        textContentColor = AppColors.Text,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Account Settings",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                IconButton(onClick = onOpenGuide) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "How to use this app",
                        tint = AppColors.Primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                HorizontalDivider(color = AppColors.Border)

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
                        containerColor = AppColors.CardBg,
                        titleContentColor = AppColors.Text,
                        textContentColor = AppColors.Text,
                        title = {
                            Text(
                                "Profile Photo",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Text
                            )
                        },
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


                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            }
                            runCatching { context.startActivity(settingsIntent) }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        "Open system settings to manage app notifications.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alerts & vibration", fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                            Text(
                                "Manage chat, broadcast, and assigned-incident alert channels",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                        Text("Manage", color = AppColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.CardBg), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Night mode", fontWeight = FontWeight.SemiBold, color = AppColors.Text); Text("Reduce glare in low light", fontSize = 12.sp, color = AppColors.TextSecondary) }
                        Switch(
                            checked = ThemeController.isDarkMode.value,
                            onCheckedChange = { enabled ->
                                onDarkModeChange(enabled)
                                ThemeController.setDarkMode(context, enabled)
                            }
                        )
                    }
                }
                Text(
                    text = "Emergency Response App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.CardBg), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.35f))) {
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
