package com.ers.emergencyresponseapp

import android.location.Geocoder
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.UUID
import java.util.Locale
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import coil.compose.AsyncImage

// ─── Design tokens ────────────────────────────────────────────────────────────
private object RFColors {
    val Primary: Color get() = Color(0xFF4C8A89)
    val Secondary: Color get() = Color(0xFF3A506B)
    val Tertiary: Color get() = Color(0xFF1C2541)
    val Text: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFFAFAFA) else Color(0xFF171717)
    val TextSecondary: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFA1A1AA) else Color(0xFF575757)
    val Border: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF27272A) else Color(0xFFE5E5E5)
    val Bg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val SurfaceBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF0A0A0A) else Color(0xFFF6F8FA)
}

// ─── Review status ────────────────────────────────────────────────────────────
private enum class ReviewStatus(val label: String, val color: Color, val bgColor: Color) {
    Pending("Pending Review", Color(0xFFE65100), Color(0xFFFFF3E0)),
    Submitted("Submitted",    Color(0xFF0277BD), Color(0xFFE1F5FE)),
    Completed("Completed",    Color(0xFF2E7D32), Color(0xFFE8F5E9))
}

private data class ReviewableIncident(
    val id: String,
    val type: String,
    val date: String,
    val status: ReviewStatus,
    val proofUri: String? = null,
    val completionNotes: String? = null
)

// ─── Resource request models ──────────────────────────────────────────────────
private enum class ResCategory(
    val displayName: String,
    val icon: ImageVector,
    val items: List<String>,        // dropdown options
    val maxQty: Int                 // sensible upper limit per request
) {
    MEDICAL(
        "Medical Supplies", Icons.Default.LocalHospital,
        listOf("Oxygen Tank", "Stretcher", "Defibrillator", "First Aid Kit",
            "Blood Pressure Monitor", "IV Drip Set", "Wheelchair", "Trauma Bag",
            "Splint", "Cervical Collar", "Pulse Oximeter", "Suction Machine"),
        maxQty = 20
    ),
    VEHICLE(
        "Vehicles", Icons.Default.DirectionsCar,
        listOf("Ambulance", "Fire Truck", "Police Car", "Rescue Van",
            "Water Tanker", "Command Vehicle", "Motorcycle (Responder)",
            "Utility Truck", "Evacuation Bus"),
        maxQty = 5
    ),
    EQUIPMENT(
        "Equipment", Icons.Default.Construction,
        listOf("Fire Extinguisher", "Hydraulic Cutter", "Generator",
            "Rope & Harness", "Thermal Camera", "Search Light",
            "Drone (Surveillance)", "Ladder", "Chainsaw", "Water Pump",
            "Hazmat Suit", "Breathing Apparatus"),
        maxQty = 15
    ),
    PERSONNEL(
        "Personnel", Icons.Default.Group,
        listOf("Paramedic", "Firefighter", "Police Officer", "Search & Rescue",
            "Nurse", "Doctor", "Psychosocial Support", "Volunteer Responder"),
        maxQty = 30
    ),
    OTHER(
        "Other", Icons.Default.Inventory,
        listOf("Food Pack", "Water (Gallon)", "Blanket", "Tent",
            "Sandbag", "Portable Toilet", "Tarpaulin", "Other (Specify in Notes)"),
        maxQty = 100
    )
}

private enum class ResUrgency(val displayName: String, val color: Color, val bgColor: Color) {
    LOW("Low",           Color(0xFF388E3C), Color(0xFFE8F5E9)),
    MEDIUM("Medium",     Color(0xFFF57C00), Color(0xFFFFF3E0)),
    HIGH("High",         Color(0xFFD32F2F), Color(0xFFFFEBEE)),
    CRITICAL("Critical", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
}

private data class ResRequest(
    val id: String           = UUID.randomUUID().toString().take(8).uppercase(),
    val resourceName: String,
    val category: ResCategory,
    val quantity: Int,
    val urgency: ResUrgency,
    val incidentId: String,
    val location: String,
    val notes: String,
    val requestedBy: String,
    val timestamp: Long      = System.currentTimeMillis()
)
private data class SavedResourceRequest(
    val id: String,
    val resourceName: String,
    val category: String,
    val quantity: String,
    val urgency: String,
    val status: String,
    val timestamp: Long
)

private data class SavedReviewRating(
    val incidentId: String,
    val responseRating: Int,
    val communicationRating: Int,
    val professionalismRating: Int,
    val outcome: String,
    val timestamp: Long
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Decode a file:// or content:// URI string to a Bitmap safely */
private const val SERVER_BASE_URL = "https://emergency-response.alertaraqc.com"

/** Turns a server-relative path like "/uploads/x.jpg" into a fetchable https URL.
 *  Leaves file://, content://, and already-absolute http(s):// URIs untouched. */
private fun resolveImageUri(uriStr: String): String {
    val cleanUri = uriStr.trim()

    return when {
        cleanUri.startsWith("http://") ||
                cleanUri.startsWith("https://") -> cleanUri

        cleanUri.startsWith("file://") ||
                cleanUri.startsWith("content://") -> cleanUri

        cleanUri.startsWith("/") ->
            "$SERVER_BASE_URL$cleanUri"

        cleanUri.isNotBlank() ->
            "$SERVER_BASE_URL/${cleanUri.trimStart('/')}"

        else -> ""
    }
}

private fun decodeBitmap(ctx: Context, uriStr: String): Bitmap? =
    runCatching {
        val resolved = resolveImageUri(uriStr)
        when {
            resolved.startsWith("file://") ->
                BitmapFactory.decodeFile(Uri.parse(resolved).path)
            resolved.startsWith("http://") || resolved.startsWith("https://") ->
                java.net.URL(resolved).openStream().use { BitmapFactory.decodeStream(it) }
            else ->
                ctx.contentResolver.openInputStream(Uri.parse(resolved))
                    ?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

/**
 * Decode for full-screen preview: reads the file twice —
 * first pass gets the native dimensions, second pass decodes at exactly
 * the largest power-of-2 sample that keeps the image >= screen size,
 * preventing both blur-from-upscaling AND oom-from-giant-bitmap.
 */
private fun decodeBitmapHighQuality(
    ctx: Context,
    uriStr: String
): Bitmap? = runCatching {

    val resolved = resolveImageUri(uriStr)

    val isRemote =
        resolved.startsWith("http://") ||
                resolved.startsWith("https://")

    val bytes: ByteArray? = if (isRemote) {
        java.net.URL(resolved)
            .openStream()
            .use { it.readBytes() }
    } else {
        null
    }

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    when {
        resolved.startsWith("file://") -> {
            BitmapFactory.decodeFile(
                Uri.parse(resolved).path,
                options
            )
        }

        isRemote -> {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes!!.size,
                options
            )
        }

        else -> {
            ctx.contentResolver
                .openInputStream(Uri.parse(resolved))
                ?.use { inputStream ->
                    BitmapFactory.decodeStream(
                        inputStream,
                        null,
                        options
                    )
                }
        }
    }

    val srcW = options.outWidth
    val srcH = options.outHeight

    val displayMetrics = ctx.resources.displayMetrics
    val requiredWidth = displayMetrics.widthPixels
    val requiredHeight = displayMetrics.heightPixels

    var sampleSize = 1

    while (
        srcW / (sampleSize * 2) >= requiredWidth &&
        srcH / (sampleSize * 2) >= requiredHeight
    ) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    when {
        resolved.startsWith("file://") -> {
            BitmapFactory.decodeFile(
                Uri.parse(resolved).path,
                decodeOptions
            )
        }

        isRemote -> {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes!!.size,
                decodeOptions
            )
        }

        else -> {
            ctx.contentResolver
                .openInputStream(Uri.parse(resolved))
                ?.use { inputStream ->
                    BitmapFactory.decodeStream(
                        inputStream,
                        null,
                        decodeOptions
                    )
                }
        }
    }
}.getOrNull()
/**
 * Reverse-geocode lat/lng into a formatted string:
 * "Location: Commonwealth Ave, Quezon City\nCoordinates: 14.65723, 121.04310"
 * Falls back to coordinates-only if Geocoder is unavailable or returns no results.
 */
private fun buildLocationString(ctx: Context, lat: Double, lng: Double): String {
    val coords = "Coordinates: ${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
    return try {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            // Build a readable street + city string
            val parts = listOfNotNull(
                addr.thoroughfare,           // street name
                addr.subLocality,            // district / barangay
                addr.locality,               // city
                addr.adminArea               // province / region
            ).filter { it.isNotBlank() }
            val addressLine = if (parts.isNotEmpty()) parts.joinToString(", ") else (addr.getAddressLine(0) ?: "")
            if (addressLine.isNotBlank()) "Location: $addressLine\n$coords"
            else coords
        } else {
            coords
        }
    } catch (_: Exception) {
        coords
    }
}

// ─── Stat card ────────────────────────────────────────────────────────────────
@Composable
private fun ReviewStatCard(value: Int, label: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.shadow(8.dp, RoundedCornerShape(20.dp)),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border   = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = RFColors.Text)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = RFColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(3.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.7f)))
        }
    }
}

// ─── Filter pill ──────────────────────────────────────────────────────────────
@Composable
private fun FilterPill(label: String, icon: ImageVector, selected: Boolean, accentColor: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.13f) else Color.Transparent,
            contentColor = if (ThemeController.isDarkMode.value) RFColors.Primary else RFColors.Secondary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accentColor.copy(alpha = 0.6f) else RFColors.Border)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─── Full-screen image preview dialog ────────────────────────────────────────
@Composable
private fun FullScreenImageDialog(
    uriStr: String,
    onDismiss: () -> Unit
) {
    val resolvedImageUrl = remember(uriStr) {
        resolveImageUri(uriStr)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = resolvedImageUrl,
                contentDescription = "Full completion image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp, top = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .clickable {
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ─── Review card ──────────────────────────────────────────────────────────────
@Composable
private fun ReviewCard(
    incident: ReviewableIncident,
    index: Int,
    onWriteReview: (ReviewableIncident) -> Unit,
    onViewDetails: (ReviewableIncident) -> Unit,
    onViewImage: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        visible = true
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }) {
        Card(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = RFColors.Bg),
            border   = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.White, RFColors.Primary.copy(alpha = 0.03f))))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(6.dp).height(52.dp).clip(RoundedCornerShape(999.dp))
                        .background(Brush.verticalGradient(listOf(RFColors.Primary, RFColors.Secondary))))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(incident.type, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = RFColors.Text)
                        Spacer(Modifier.height(3.dp))
                        Text("ID: ${incident.id}", fontSize = 12.sp, color = RFColors.TextSecondary)
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(incident.status.bgColor)
                            .border(1.dp, incident.status.color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(incident.status.label, color = incident.status.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = RFColors.Border, thickness = 0.8.dp)

                // Date + proof
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = RFColors.Primary
                        )

                        Text(
                            incident.date,
                            fontSize = 13.sp,
                            color = RFColors.TextSecondary
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = RFColors.SurfaceBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            RFColors.Border
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = RFColors.Primary
                                )

                                Text(
                                    "Completion Notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RFColors.Text
                                )
                            }

                            Text(
                                text = incident.completionNotes
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "No completion notes were provided.",
                                fontSize = 12.sp,
                                color = RFColors.TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    incident.proofUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { uriStr ->

                            val resolvedImageUrl = resolveImageUri(uriStr)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        RFColors.Border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onViewImage(resolvedImageUrl)
                                    }
                            ) {
                                AsyncImage(
                                    model = resolvedImageUrl,
                                    contentDescription = "Completion image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    contentScale = ContentScale.Fit
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(
                                            horizontal = 9.dp,
                                            vertical = 5.dp
                                        )
                                ) {
                                    Text(
                                        "Tap to view",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                }

                // CTA
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (incident.status == ReviewStatus.Pending) {
                        Button(
                            onClick = { onWriteReview(incident) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Write Review", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onViewDetails(incident) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RFColors.Secondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Secondary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View Details", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Resource Request Form Sheet ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceRequestFullScreenDialog(
    responderId: Int,
    responderName: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current

    // Form state
    var category by rememberSaveable { mutableStateOf(ResCategory.MEDICAL) }
    var resourceName by rememberSaveable { mutableStateOf(ResCategory.MEDICAL.items.first()) }
    var formStep by rememberSaveable { mutableIntStateOf(1) }
    var showItemDropdown by remember { mutableStateOf(false) }

    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var urgency by rememberSaveable { mutableStateOf(ResUrgency.MEDIUM) }
    var incidentId by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var isLocating by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()


    val maxQty = category.maxQty
    val isFormValid = resourceName.isNotBlank() && quantity > 0 && location.isNotBlank()

    LaunchedEffect(category) {
        resourceName = category.items.first()
        if (quantity > category.maxQty) quantity = 1
    }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                isLocating = true
                try {
                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            isLocating = false
                            location = buildLocationString(context, loc.latitude, loc.longitude)
                        } else {
                            val cts = CancellationTokenSource()
                            try {
                                fusedClient.getCurrentLocation(
                                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                    cts.token
                                )
                                    .addOnSuccessListener { freshLoc ->
                                        isLocating = false
                                        if (freshLoc != null) {
                                            location = buildLocationString(
                                                context,
                                                freshLoc.latitude,
                                                freshLoc.longitude
                                            )
                                        } else {
                                            isLocating = false
                                            Toast.makeText(
                                                context,
                                                "Location unavailable. Enable GPS.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        isLocating = false; Toast.makeText(
                                        context,
                                        "Error: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    }
                            } catch (se: SecurityException) {
                                isLocating = false
                                Toast.makeText(
                                    context,
                                    "Location permission required.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }.addOnFailureListener { e ->
                        isLocating = false; Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    }
                } catch (se: SecurityException) {
                    isLocating = false
                    Toast.makeText(context, "Location permission required.", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    fun fetchLocation() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            isLocating = true
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        isLocating = false
                        location = buildLocationString(context, loc.latitude, loc.longitude)
                    } else {
                        val cts = CancellationTokenSource()
                        try {
                            fusedClient.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                cts.token
                            )
                                .addOnSuccessListener { freshLoc ->
                                    isLocating = false
                                    if (freshLoc != null) {
                                        location = buildLocationString(
                                            context,
                                            freshLoc.latitude,
                                            freshLoc.longitude
                                        )
                                    } else {
                                        isLocating = false
                                        Toast.makeText(
                                            context,
                                            "Location unavailable. Enable GPS.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isLocating = false; Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                }
                        } catch (se: SecurityException) {
                            isLocating = false
                            Toast.makeText(
                                context,
                                "Location permission required.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.addOnFailureListener { e ->
                    isLocating = false; Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                }
            } catch (se: SecurityException) {
                isLocating = false
                Toast.makeText(context, "Location permission required.", Toast.LENGTH_SHORT).show()
            }
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isSending) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = RFColors.SurfaceBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Fixed header ──────────────────────────────────────────
                Surface(
                    color = RFColors.Bg,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(RFColors.Primary, RFColors.Secondary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Request Resources",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = RFColors.Text
                                )
                                Text(
                                    "Step $formStep of 2",
                                    fontSize = 12.sp,
                                    color = RFColors.TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { if (!isSending) onDismiss() },
                                enabled = !isSending,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(RFColors.SurfaceBg)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = RFColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Progress indicator — connected track, not disjoint pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProgressNode(number = 1, label = "Resource", state = when {
                                formStep > 1 -> ProgressNodeState.DONE
                                else -> ProgressNodeState.ACTIVE
                            })
                            ProgressConnector(filled = formStep > 1)
                            ProgressNode(number = 2, label = "Details", state = when {
                                formStep == 2 -> ProgressNodeState.ACTIVE
                                else -> ProgressNodeState.PENDING
                            })
                        }
                    }
                }

                // ── Scrollable content — both steps live in the same LazyColumn so
                //    layout behavior (and dead-space handling) is identical for both ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (formStep == 1) {
                        item {
                            SectionCard(title = "Category", icon = Icons.Default.Category) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(ResCategory.entries) { _, cat ->
                                        val isSel = category == cat
                                        OutlinedButton(
                                            onClick = { category = cat },
                                            shape = RoundedCornerShape(999.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSel) RFColors.Primary.copy(alpha = 0.13f) else Color.Transparent,
                                                contentColor = if (isSel) RFColors.Primary else RFColors.TextSecondary
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSel) RFColors.Primary.copy(alpha = 0.6f) else RFColors.Border
                                            )
                                        ) {
                                            Icon(cat.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                cat.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SectionCard(title = "Resource Details", icon = Icons.Default.Inventory2) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "Resource Name *",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RFColors.TextSecondary
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = showItemDropdown,
                                            onExpandedChange = { showItemDropdown = it }
                                        ) {
                                            OutlinedTextField(
                                                value = resourceName,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.menuAnchor(
                                                    MenuAnchorType.PrimaryNotEditable,
                                                    enabled = true
                                                ).fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                leadingIcon = {
                                                    Icon(
                                                        category.icon,
                                                        contentDescription = null,
                                                        tint = RFColors.Primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showItemDropdown)
                                                },
                                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = showItemDropdown,
                                                onDismissRequest = { showItemDropdown = false },
                                                modifier = Modifier.background(RFColors.Bg)
                                            ) {
                                                category.items.forEach { item ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                item,
                                                                fontSize = 14.sp,
                                                                fontWeight = if (item == resourceName) FontWeight.SemiBold else FontWeight.Normal,
                                                                color = if (item == resourceName) RFColors.Primary else RFColors.Text
                                                            )
                                                        },
                                                        onClick = {
                                                            resourceName = item
                                                            showItemDropdown = false
                                                        },
                                                        leadingIcon = {
                                                            if (item == resourceName) Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = RFColors.Primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = RFColors.Border, thickness = 0.6.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.width(136.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Quantity * (max $maxQty)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RFColors.TextSecondary
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(1.dp, RFColors.Border, RoundedCornerShape(12.dp))
                                                    .background(RFColors.SurfaceBg),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                                        .background(
                                                            if (quantity > 1) RFColors.Primary.copy(alpha = 0.10f) else Color.Transparent
                                                        )
                                                        .clickable(enabled = quantity > 1) { quantity-- },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Remove,
                                                        contentDescription = "Decrease",
                                                        tint = if (quantity > 1) RFColors.Primary else RFColors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = quantity.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = RFColors.Text
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                                                        .background(
                                                            if (quantity < maxQty) RFColors.Primary.copy(alpha = 0.10f) else Color.Transparent
                                                        )
                                                        .clickable(enabled = quantity < maxQty) { quantity++ },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Increase",
                                                        tint = if (quantity < maxQty) RFColors.Primary else RFColors.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (quantity >= maxQty) {
                                                Text("Max limit reached", fontSize = 10.sp, color = Color(0xFFD32F2F))
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Urgency",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RFColors.TextSecondary
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                ResUrgency.entries.chunked(2).forEach { row ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        row.forEach { u ->
                                                            val isSel = u == urgency
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(if (isSel) u.bgColor else RFColors.SurfaceBg)
                                                                    .border(
                                                                        1.dp,
                                                                        if (isSel) u.color.copy(alpha = 0.6f) else RFColors.Border,
                                                                        RoundedCornerShape(10.dp)
                                                                    )
                                                                    .clickable { urgency = u }
                                                                    .padding(vertical = 8.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    u.displayName,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (isSel) u.color else RFColors.TextSecondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Live preview so step 1 doesn't feel empty at the bottom
                            SectionCard(title = "Summary", icon = Icons.Default.Summarize) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoRow("Category", category.displayName)
                                    InfoRow("Resource", resourceName)
                                    InfoRow("Quantity", quantity.toString())
                                    InfoRow("Urgency", urgency.displayName)
                                }
                            }
                        }
                    }

                    if (formStep == 2) {
                        item {
                            SectionCard(title = "Incident Reference", icon = Icons.Default.Badge) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "Incident ID (optional)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RFColors.TextSecondary
                                    )
                                    OutlinedTextField(
                                        value = incidentId, onValueChange = { incidentId = it },
                                        placeholder = {
                                            Text("e.g. INC-20240727", color = RFColors.TextSecondary.copy(alpha = 0.6f))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(Icons.Default.Badge, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            SectionCard(title = "Delivery Location", icon = Icons.Default.LocationOn) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { fetchLocation() },
                                            enabled = !isLocating,
                                            shape = RoundedCornerShape(999.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RFColors.Primary),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Primary.copy(alpha = 0.5f))
                                        ) {
                                            if (isLocating) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = RFColors.Primary)
                                            } else {
                                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(Modifier.width(5.dp))
                                            Text(if (isLocating) "Locating…" else "Use My Location", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        placeholder = {
                                            Text("Street, barangay, landmark…", color = RFColors.TextSecondary.copy(alpha = 0.6f))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 1,
                                        maxLines = 2,
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(18.dp))
                                        },
                                        supportingText = {
                                            if (location.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF2E7D32))
                                                    Text("Location set", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            SectionCard(title = "Additional Notes", icon = Icons.AutoMirrored.Filled.Notes) {
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    placeholder = {
                                        Text("Any special instructions or context…", color = RFColors.TextSecondary.copy(alpha = 0.6f))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 3
                                )
                            }
                        }

                        item {
                            SectionCard(title = "Request Info", icon = Icons.Default.Info) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoRow("Requested By", responderName)
                                    InfoRow("Department", "Responder")
                                    InfoRow("Date", SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date()))
                                }
                            }
                        }

                        if (!isFormValid) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                                    Text(
                                        "Resource name, quantity, and location are required.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFF57C00)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Fixed footer ──────────────────────────────────────────
                Surface(color = RFColors.Bg, shadowElevation = 6.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (formStep == 2) {
                            OutlinedButton(
                                onClick = { if (!isSending) formStep = 1 },
                                enabled = !isSending,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 180f))
                                Spacer(Modifier.width(6.dp))
                                Text("Back")
                            }
                        }

                        Button(
                            onClick = {
                                if (formStep == 1) {
                                    formStep = 2
                                    return@Button
                                }

                                if (!isFormValid) return@Button

                                isSending = true

                                scope.launch {
                                    val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
                                    val result = repo.sendResourceRequest(
                                        responderId = responderId,
                                        responderName = responderName,
                                        category = category.displayName,
                                        resourceName = resourceName.trim(),
                                        quantity = quantity,
                                        urgency = urgency.displayName,
                                        incidentId = incidentId.trim().ifBlank { "N/A" },
                                        location = location.trim(),
                                        notes = notes.trim()
                                    )

                                    result.onSuccess {
                                        Toast.makeText(context, "Request submitted for admin review", Toast.LENGTH_SHORT).show()
                                        isSending = false
                                        onSubmit()
                                    }.onFailure { error ->
                                        Log.e("ResourceRequest", "Failed: ${error.message}")
                                        Toast.makeText(context, "Failed: ${error.message}", Toast.LENGTH_LONG).show()
                                        isSending = false
                                    }
                                }
                            },
                            enabled = if (formStep == 1) {
                                resourceName.isNotBlank() && quantity > 0
                            } else {
                                isFormValid && !isSending
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RFColors.Primary,
                                contentColor = Color.White,
                                disabledContainerColor = RFColors.Primary.copy(alpha = 0.4f)
                            )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    if (formStep == 1) "Next" else "Submit Request",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (formStep == 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── New helper composables for the redesigned form ───────────────────────────

private enum class ProgressNodeState { DONE, ACTIVE, PENDING }

@Composable
private fun RowScope.ProgressNode(number: Int, label: String, state: ProgressNodeState) {
    val circleColor = when (state) {
        ProgressNodeState.DONE -> RFColors.Primary
        ProgressNodeState.ACTIVE -> RFColors.Primary
        ProgressNodeState.PENDING -> RFColors.Border
    }
    val textColor = when (state) {
        ProgressNodeState.DONE, ProgressNodeState.ACTIVE -> RFColors.Text
        ProgressNodeState.PENDING -> RFColors.TextSecondary
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (state == ProgressNodeState.PENDING) Color.Transparent else circleColor)
                .border(1.5.dp, circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (state == ProgressNodeState.DONE) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    number.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state == ProgressNodeState.ACTIVE) Color.White else RFColors.TextSecondary
                )
            }
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun RowScope.ProgressConnector(filled: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 10.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) RFColors.Primary else RFColors.Border)
    )
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RFColors.Text)
            }
            content()
        }
    }
}


// ─── Main screen ──────────────────────────────────────────────────────────────
@Composable
fun ReviewsFeedbackScreen() {
    var requestSearch by rememberSaveable { mutableStateOf("") }
    val context  = LocalContext.current
    val prefs    = context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
    var selected by remember { mutableStateOf(ReviewStatus.Pending) }

    val responderName = prefs.getString("account_username", "Responder") ?: "Responder"

    val storedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val responderId = storedPrefs.getString("user_id", "")?.toIntOrNull() ?: 0

    val showComposeDialog = remember { mutableStateOf(false) }
    val reviewText        = rememberSaveable { mutableStateOf("") }
    var responseRating by rememberSaveable { mutableIntStateOf(3) }
    var communicationRating by rememberSaveable { mutableIntStateOf(3) }
    var professionalismRating by rememberSaveable { mutableIntStateOf(3) }
    var selectedOutcome by rememberSaveable { mutableStateOf("Resolved") }
    val composeTarget     = remember { mutableStateOf<ReviewableIncident?>(null) }
    var showSubmitReviewConfirm by remember { mutableStateOf(false) }

    val showDetailsDialog = remember { mutableStateOf(false) }
    val detailsText       = rememberSaveable { mutableStateOf("") }
    val detailsTarget     = remember { mutableStateOf<ReviewableIncident?>(null) }
    val showReportDialog = remember { mutableStateOf(false) }
    var showFullReviewDetails by remember { mutableStateOf(false) }
    val generatedReport = rememberSaveable { mutableStateOf("") }
    var isExportingPdf by remember { mutableStateOf(false) }

    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }   // ← replaces AlertDialog
    var showResourceForm   by remember { mutableStateOf(false) }
    var requestRefreshKey by remember { mutableIntStateOf(0) }
    var reviewRefreshKey by remember { mutableIntStateOf(0) }
    var selectedRequest by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var requestToCancel by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var showAllRequests by remember { mutableStateOf(false) }
    var showAllReviews by remember { mutableStateOf(false) }

    var isSubmittingReview by remember { mutableStateOf(false) }

    suspend fun submitReview(
        incident: ReviewableIncident,
        text: String,
        responseRatingVal: Int,
        communicationRatingVal: Int,
        professionalismRatingVal: Int,
        outcome: String
    ): Boolean {
        val incidentIdLong = incident.id.removePrefix("#").toLongOrNull()
        if (incidentIdLong == null || responderId <= 0) {
            Toast.makeText(context, "Unable to submit review", Toast.LENGTH_SHORT).show()
            return false
        }
        val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
        val result = repo.submitIncidentReview(
            incidentId = incidentIdLong,
            responderId = responderId,
            responseRating = responseRatingVal,
            communicationRating = communicationRatingVal,
            professionalismRating = professionalismRatingVal,
            outcome = outcome,
            reviewText = text
        )
        return result.onSuccess {
            Toast.makeText(context, "Review submitted for admin verification", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "Failed to submit review: ${error.message}", Toast.LENGTH_LONG).show()
        }.isSuccess
    }

    var serverReviewIncidents by remember { mutableStateOf<List<com.ers.emergencyresponseapp.network.PendingReviewIncidentDto>>(emptyList()) }

    suspend fun refreshReviewIncidents() {
        if (responderId <= 0) return
        val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
        serverReviewIncidents = repo.getPendingReviewIncidents(responderId)
    }

    // Immediate refresh on manual triggers (e.g. right after a review submits)
    LaunchedEffect(reviewRefreshKey, responderId) {
        refreshReviewIncidents()
    }

    // Background poll every 5s so admin-side status changes show up automatically
    LaunchedEffect(responderId) {
        if (responderId <= 0) return@LaunchedEffect
        while (true) {
            delay(5000L)
            refreshReviewIncidents()
        }
    }

    val allIncidents = remember(serverReviewIncidents) {
        serverReviewIncidents.map { dto ->
            val status = when (dto.review_status) {
                "pending_review"   -> ReviewStatus.Pending
                "submitted_review" -> ReviewStatus.Submitted
                "resolved"         -> ReviewStatus.Completed
                else               -> ReviewStatus.Pending
            }
            val dateStr = dto.completed_at?.let { raw ->
                runCatching {
                    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)
                    java.text.SimpleDateFormat("yyyy-MM-dd · HH:mm", Locale.getDefault()).format(parsed!!)
                }.getOrDefault(raw)
            } ?: "—"

            ReviewableIncident(
                id = "#${dto.id}",
                type = dto.type.replaceFirstChar { it.uppercase() },
                date = dateStr,
                status = status,
                proofUri = dto.completion_image_path,
                completionNotes = dto.completion_notes
            )
        }
    }

    val pendingCount   = allIncidents.count { it.status == ReviewStatus.Pending }
    val submittedCount = allIncidents.count { it.status == ReviewStatus.Submitted }
    val completedCount = allIncidents.count { it.status == ReviewStatus.Completed }

    val filtered = allIncidents
        .filter { it.status == selected }

    val reviewsShownOnMainScreen = when (selected) {
        ReviewStatus.Pending -> filtered
        ReviewStatus.Submitted,
        ReviewStatus.Completed -> filtered.take(2)
    }

    val hasMoreReviews = when (selected) {
        ReviewStatus.Pending -> false
        ReviewStatus.Submitted,
        ReviewStatus.Completed -> filtered.size > 2
    }

    val resourcePrefs = context.getSharedPreferences("resource_requests", Context.MODE_PRIVATE)

    val reviewPrefs = context.getSharedPreferences("review_ratings", Context.MODE_PRIVATE)
    var ratingRefreshKey by remember { mutableIntStateOf(0) }



    var parsedRequests by remember { mutableStateOf<List<SavedResourceRequest>>(emptyList()) }

    suspend fun refreshResourceRequests() {
        if (responderId <= 0) return
        val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
        val serverRequests = repo.getMyResourceRequests(responderId)
        parsedRequests = serverRequests.map {
            SavedResourceRequest(
                id = it.id.toString(),
                resourceName = it.resource_name,
                category = it.category,
                quantity = it.quantity.toString(),
                urgency = it.urgency,
                status = it.status.replaceFirstChar { c -> c.uppercase() },
                timestamp = 0L
            )
        }
    }
    // Requests actually shown in the UI: cancelled requests disappear 24hrs after
    // their last status change so the list doesn't fill up with dead clutter.
    val visibleResourceRequests = remember(parsedRequests) {
        val now = System.currentTimeMillis()
        parsedRequests.filter { req ->
            if (req.status.equals("Cancelled", ignoreCase = true)) {
                val hoursSinceUpdate = (now - req.timestamp) / (1000 * 60 * 60)
                hoursSinceUpdate < 24
            } else {
                true
            }
        }
    }

    // Latest request — highlighted in the list, no separate duplicate card needed anymore
    val latestResourceRequest = visibleResourceRequests.firstOrNull()

    val resourceRequests = visibleResourceRequests.size

// Immediate refresh on manual triggers (submit, cancel, pull-to-refresh button)
    LaunchedEffect(requestRefreshKey, responderId) {
        refreshResourceRequests()
    }

// Background poll every 5s so status changes made by admin show up automatically
    LaunchedEffect(responderId) {
        if (responderId <= 0) return@LaunchedEffect
        while (true) {
            delay(5000L)
            refreshResourceRequests()
        }
    }


    val savedRatings = remember(ratingRefreshKey) {
        parseSavedReviewRatings(
            reviewPrefs.getStringSet("ratings", emptySet()) ?: emptySet()
        )
    }

    val avgRating = if (savedRatings.isNotEmpty()) {
        val totalAverage = savedRatings.map {
            (it.responseRating + it.communicationRating + it.professionalismRating) / 3f
        }.average()

        String.format(Locale.getDefault(), "%.1f", totalAverage).toFloat()
    } else {
        0f
    }
    val scope = rememberCoroutineScope()

    fun cancelResourceRequest(requestId: String) {
        val idInt = requestId.toIntOrNull() ?: return

        scope.launch {
            val repo = com.ers.emergencyresponseapp.data.IncidentRepository()
            val result = repo.cancelResourceRequest(idInt, responderId)

            result.onSuccess {
                requestRefreshKey++
                Toast.makeText(context, "Resource request cancelled", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Failed to cancel: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val recentRequests = visibleResourceRequests.take(1)
    val filteredRequests = visibleResourceRequests.filter {
        it.resourceName.contains(requestSearch, ignoreCase = true) ||
                it.id.contains(requestSearch, ignoreCase = true)
    }

    Scaffold(containerColor = RFColors.SurfaceBg) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding      = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── HEADER ────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(160.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .shadow(14.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.verticalGradient(listOf(RFColors.Primary, RFColors.Secondary, RFColors.Tertiary)))
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent), radius = 600f)))
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("My Reviews", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Reviews & Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(
                                "Monitor reviews, reports, and resource requests",
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.size(56.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }


            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewStatCard(pendingCount,   "Pending",   Color(0xFFE65100), Modifier.weight(1f))
                    ReviewStatCard(submittedCount, "Submitted", Color(0xFF0277BD), Modifier.weight(1f))
                    ReviewStatCard(completedCount, "Completed", Color(0xFF2E7D32), Modifier.weight(1f))
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Performance Overview",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RFColors.Text
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalyticsCard(
                            title = "Avg Rating",
                            value = if (avgRating > 0f) "⭐ ${String.format(Locale.getDefault(), "%.1f", avgRating)} / 5" else "No ratings",
                            modifier = Modifier.weight(1f)
                        )

                        AnalyticsCard(
                            title = "Resource Requests",
                            value = resourceRequests.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── REQUEST RESOURCES BUTTON ──────────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(RFColors.Primary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = RFColors.Primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "Resource Requests",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RFColors.Text
                                    )
                                    Text(
                                        if (visibleResourceRequests.isEmpty()) "No requests yet"
                                        else "${visibleResourceRequests.size} total • updated ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())}",
                                        fontSize = 11.sp,
                                        color = RFColors.TextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { requestRefreshKey++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = RFColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = RFColors.Border, thickness = 0.8.dp)

                        // Action row — single, unambiguous entry point
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showResourceForm = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RFColors.Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Request", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { showAllRequests = true },
                                enabled = visibleResourceRequests.size > 1,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (ThemeController.isDarkMode.value) RFColors.Primary else RFColors.Secondary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                            ) {
                                Text("View All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // List or empty state
                        if (recentRequests.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = RFColors.TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Requests you submit will appear here",
                                    fontSize = 12.sp,
                                    color = RFColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentRequests.forEach { request ->
                                    ResourceRequestHistoryCard(
                                        request = request,
                                        onClick = { selectedRequest = request },
                                        onCancel = { requestToCancel = request },
                                        isLatest = request.id == latestResourceRequest?.id
                                    )
                                }
                            }
                        }
                    }
                }
            }


            // ── FILTER PILLS ──────────────────────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter by Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RFColors.Text, modifier = Modifier.padding(start = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterPill("Pending",   Icons.Default.HourglassTop,            selected == ReviewStatus.Pending,   Color(0xFFE65100)) { selected = ReviewStatus.Pending } }
                        item { FilterPill("Submitted", Icons.AutoMirrored.Filled.Send,         selected == ReviewStatus.Submitted, Color(0xFF0277BD)) { selected = ReviewStatus.Submitted } }
                        item { FilterPill("Completed", Icons.Default.CheckCircle,              selected == ReviewStatus.Completed, Color(0xFF2E7D32)) { selected = ReviewStatus.Completed } }
                    }
                }
            }

            // ── SECTION HEADER ────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RFColors.Bg
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        RFColors.Border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                selected.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = RFColors.Text
                            )

                            Text(
                                "${filtered.size} review${if (filtered.size == 1) "" else "s"}",
                                fontSize = 12.sp,
                                color = RFColors.TextSecondary
                            )
                        }

                        if (hasMoreReviews) {
                            TextButton(
                                onClick = {
                                    showAllReviews = true
                                }
                            ) {
                                Text(
                                    "View All",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RFColors.Primary
                                )

                                Spacer(Modifier.width(4.dp))

                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RFColors.Primary
                                )
                            }
                        } else {
                            Text(
                                "Latest first",
                                fontSize = 12.sp,
                                color = RFColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // ── EMPTY STATE ───────────────────────────────────────────────
            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).shadow(6.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(RFColors.Primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(32.dp), tint = RFColors.Primary)
                            }
                            Text("No ${selected.label.lowercase()} incidents", fontWeight = FontWeight.SemiBold, color = RFColors.Text)
                            Text("Items will appear here once available", fontSize = 13.sp, color = RFColors.TextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // ── REVIEW CARDS ──────────────────────────────────────────────
            itemsIndexed(reviewsShownOnMainScreen) { index, incident ->
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ReviewCard(
                        incident      = incident,
                        index         = index,
                        onWriteReview = { inc ->
                            composeTarget.value = inc
                            reviewText.value = ""
                            responseRating = 3
                            communicationRating = 3
                            professionalismRating = 3
                            selectedOutcome = "Resolved"
                            showComposeDialog.value = true
                        },
                        onViewDetails = { inc ->
                            detailsText.value = inc.completionNotes?.takeIf { it.isNotBlank() }
                                ?: "No review details available."
                            detailsTarget.value = inc
                            showFullReviewDetails = true
                        },
                        onViewImage = { uri -> fullScreenImageUri = uri }   // ← use full-screen dialog
                    )
                }
            }
        }

        if (showAllReviews) {
            Dialog(
                onDismissRequest = {
                    showAllReviews = false
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RFColors.SurfaceBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Surface(
                            color = RFColors.Bg,
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "All ${selected.label}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = RFColors.Text
                                    )

                                    Text(
                                        "${filtered.size} total reviews",
                                        fontSize = 12.sp,
                                        color = RFColors.TextSecondary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        showAllReviews = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = RFColors.TextSecondary
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 14.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(filtered) { index, incident ->
                                ReviewCard(
                                    incident = incident,
                                    index = index,
                                    onWriteReview = { inc ->
                                        showAllReviews = false
                                        composeTarget.value = inc
                                        reviewText.value = ""
                                        responseRating = 3
                                        communicationRating = 3
                                        professionalismRating = 3
                                        selectedOutcome = "Resolved"
                                        showComposeDialog.value = true
                                    },
                                    onViewDetails = { inc ->
                                        detailsText.value =
                                            inc.completionNotes
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "No review details available."

                                        detailsTarget.value = inc
                                        showFullReviewDetails = true
                                    },
                                    onViewImage = { uri ->
                                        fullScreenImageUri = uri
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── COMPOSE REVIEW DIALOG ─────────────────────────────────────────
        if (showSubmitReviewConfirm && composeTarget.value != null) {
            val target = composeTarget.value!!

            AlertDialog(
                onDismissRequest = {
                    showSubmitReviewConfirm = false
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = RFColors.Bg,
                title = {
                    Text(
                        "Submit Review?",
                        fontWeight = FontWeight.Bold,
                        color = RFColors.Text
                    )
                },
                text = {
                    Text(
                        "Please confirm that your ratings and feedback are final before submitting.",
                        fontSize = 14.sp,
                        color = RFColors.TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSubmittingReview) return@Button
                            val fullReview = """
                        Outcome: $selectedOutcome

                        Response Time Rating: $responseRating/5
                        Communication Rating: $communicationRating/5
                        Professionalism Rating: $professionalismRating/5

                        Feedback:
                        ${reviewText.value}
                    """.trimIndent()

                            isSubmittingReview = true
                            scope.launch {
                                val success = submitReview(
                                    target, fullReview, responseRating,
                                    communicationRating, professionalismRating, selectedOutcome
                                )
                                isSubmittingReview = false
                                if (success) {
                                    val ratingJson = """
                                {
                                  "incidentId": "${target.id.removePrefix("#")}",
                                  "responseRating": $responseRating,
                                  "communicationRating": $communicationRating,
                                  "professionalismRating": $professionalismRating,
                                  "outcome": "$selectedOutcome",
                                  "timestamp": ${System.currentTimeMillis()}
                                }
                            """.trimIndent()
                                    val oldRatings = reviewPrefs.getStringSet("ratings", emptySet()) ?: emptySet()
                                    reviewPrefs.edit().putStringSet("ratings", oldRatings + ratingJson).apply()
                                    ratingRefreshKey++
                                    reviewRefreshKey++

                                    showSubmitReviewConfirm = false
                                    showComposeDialog.value = false
                                    composeTarget.value = null
                                    reviewText.value = ""
                                }
                            }
                        },
                        enabled = !isSubmittingReview,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                    ) {
                        if (isSubmittingReview) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Yes, Submit")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showSubmitReviewConfirm = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Review Again")
                    }
                }
            )
        }

        if (showFullReviewDetails && detailsTarget.value != null) {
            val inc = detailsTarget.value!!

            Dialog(
                onDismissRequest = {
                    showFullReviewDetails = false
                    detailsTarget.value = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RFColors.SurfaceBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Review Details",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = RFColors.Text
                                )

                                Text(
                                    "${inc.type} • ${inc.id}",
                                    fontSize = 13.sp,
                                    color = RFColors.Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = {
                                    showFullReviewDetails = false
                                    detailsTarget.value = null
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = RFColors.TextSecondary
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoRow("Status", inc.status.label)
                                InfoRow("Date", inc.date)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                item {
                                    Text(
                                        detailsText.value,
                                        fontSize = 14.sp,
                                        color = RFColors.Text,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                generatedReport.value = """
                            INCIDENT REPORT

                            Incident ID: ${inc.id}
                            Incident Type: ${inc.type}
                            Date Completed: ${inc.date}

                            Status: ${inc.status.label}

                            Review Details:
                            ${detailsText.value}

                            Generated By:
                            $responderName

                            Generated On:
                            ${
                                    SimpleDateFormat(
                                        "MMM dd, yyyy hh:mm a",
                                        Locale.getDefault()
                                    ).format(Date())
                                }
                        """.trimIndent()

                                showReportDialog.value = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── WRITE REVIEW FORM DIALOG (the missing piece) ────────────────
        if (showComposeDialog.value && composeTarget.value != null) {
            val target = composeTarget.value!!

            Dialog(
                onDismissRequest = { showComposeDialog.value = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = RFColors.SurfaceBg) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Write Review", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = RFColors.Text)
                                Text("${target.type} • ${target.id}", fontSize = 13.sp, color = RFColors.Primary, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { showComposeDialog.value = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = RFColors.TextSecondary)
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                SectionCard(title = "Ratings", icon = Icons.Default.Star) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RatingSelector("Response Time", responseRating) { responseRating = it }
                                        RatingSelector("Communication", communicationRating) { communicationRating = it }
                                        RatingSelector("Professionalism", professionalismRating) { professionalismRating = it }
                                    }
                                }
                            }
                            item {
                                SectionCard(title = "Outcome", icon = Icons.Default.Flag) {
                                    OutcomeSelector(selectedOutcome) { selectedOutcome = it }
                                }
                            }
                            item {
                                SectionCard(title = "Feedback", icon = Icons.AutoMirrored.Filled.Notes) {
                                    OutlinedTextField(
                                        value = reviewText.value,
                                        onValueChange = { reviewText.value = it },
                                        placeholder = { Text("Describe how the response went…") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showSubmitReviewConfirm = true },
                            enabled = reviewText.value.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                        ) {
                            Text("Review Summary", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showReportDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    if (!isExportingPdf) showReportDialog.value = false
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = RFColors.Bg,
                title = {
                    Text(
                        "Incident Report",
                        fontWeight = FontWeight.Bold,
                        color = RFColors.Text
                    )
                },
                text = {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RFColors.SurfaceBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .padding(14.dp)
                        ) {
                            item {
                                Text(
                                    generatedReport.value,
                                    fontSize = 13.sp,
                                    color = RFColors.Text,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isExportingPdf = true

                            val success = runCatching {
                                exportIncidentReportPdf(
                                    context = context,
                                    reportText = generatedReport.value
                                )
                            }.isSuccess

                            isExportingPdf = false

                            Toast.makeText(
                                context,
                                if (success) "PDF ready to share" else "Failed to export PDF",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showReportDialog.value = false },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            )
        }

        // ── FULL-SCREEN IMAGE PREVIEW (replaces the old small AlertDialog) ──
        fullScreenImageUri?.let { uri ->
            FullScreenImageDialog(uriStr = uri, onDismiss = { fullScreenImageUri = null })
        }

        if (showAllRequests) {
            Dialog(
                onDismissRequest = { showAllRequests = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = RFColors.SurfaceBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "All Resource Requests",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = RFColors.Text
                                )
                                Text(
                                    "${visibleResourceRequests.size} total requests",
                                    fontSize = 13.sp,
                                    color = RFColors.TextSecondary
                                )
                            }

                            IconButton(onClick = { showAllRequests = false }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = RFColors.TextSecondary
                                )
                            }
                        }

                        // ── View All Requests dialog ────────────────────────────────────────────────
                        OutlinedTextField(
                            value = requestSearch,
                            onValueChange = {
                                requestSearch = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            placeholder = {
                                Text("Search resource name or ID")
                            }
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            if (filteredRequests.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.SearchOff,
                                                contentDescription = null,
                                                tint = RFColors.TextSecondary
                                            )

                                            Spacer(Modifier.height(8.dp))

                                            Text(
                                                "No matching requests found",
                                                color = RFColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            items(filteredRequests.size) { index ->
                                val request = filteredRequests[index]

                                ResourceRequestHistoryCard(
                                    request = request,
                                    onClick = {
                                        selectedRequest = request
                                    },
                                    onCancel = {
                                        requestToCancel = request
                                    },
                                    isLatest = request.id == latestResourceRequest?.id
                                )
                            }
                        }
                    }
                }
            }
        }

        requestToCancel?.let { req ->
            AlertDialog(
                onDismissRequest = {
                    requestToCancel = null
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = RFColors.Bg,
                title = {
                    Text(
                        "Cancel Request?",
                        fontWeight = FontWeight.Bold,
                        color = RFColors.Text
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to cancel ${req.resourceName}? This action will mark the request as Cancelled.",
                        fontSize = 14.sp,
                        color = RFColors.TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            cancelResourceRequest(req.id)
                            requestToCancel = null
                            selectedRequest = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Yes, Cancel")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            requestToCancel = null
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Request")
                    }
                }
            )
        }

        selectedRequest?.let { req ->
            ResourceRequestDetailsSheet(
                request = req,
                onDismiss = {
                    selectedRequest = null
                },
                onCancel = {
                    requestToCancel = req
                }
            )
        }

        // ── RESOURCE REQUEST FORM SHEET ───────────────────────────────────
        if (showResourceForm) {
            ResourceRequestFullScreenDialog(
                responderId = responderId,
                responderName = responderName,
                onDismiss = {
                    showResourceForm = false
                },
                onSubmit = {
                    showResourceForm = false
                    requestRefreshKey++
                }
            )
        }
    }
}

@Composable
private fun RatingSelector(
    title: String,
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RFColors.TextSecondary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { value ->
                Icon(
                    imageVector = if (value <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (value <= rating) Color(0xFFFFB300) else RFColors.Border,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onRatingChange(value) }
                )
            }
        }
    }
}

@Composable
private fun OutcomeSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    val outcomes = listOf("Resolved", "Partially Resolved", "Escalated", "False Alarm")

    Column {
        Text(
            "Incident Outcome",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RFColors.TextSecondary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(outcomes.size) { index ->
                val item = outcomes[index]
                FilterChip(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    label = { Text(item, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RFColors.Text
            )

            Spacer(Modifier.height(4.dp))

            Text(
                title,
                fontSize = 12.sp,
                color = RFColors.TextSecondary
            )
        }
    }
}

private fun exportIncidentReportPdf(
    context: Context,
    reportText: String
) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)

    val canvas = page.canvas
    val paint = Paint().apply {
        textSize = 14f
        color = android.graphics.Color.BLACK
    }

    var y = 60f
    reportText.lines().forEach { line ->
        canvas.drawText(line, 40f, y, paint)
        y += 22f
    }

    pdfDocument.finishPage(page)

    val file = File(
        context.cacheDir,
        "incident_report_${System.currentTimeMillis()}.pdf"
    )

    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        android.content.Intent.createChooser(intent, "Share Incident Report")
    )
}

@Composable
private fun StepChip(
    number: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) RFColors.Primary else RFColors.SurfaceBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) RFColors.Primary else RFColors.Border
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "$number. $label",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else RFColors.TextSecondary
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            label,
            color = RFColors.TextSecondary
        )

        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = RFColors.Text
        )
    }
}

private fun parseSavedResourceRequests(raw: Set<String>): List<SavedResourceRequest> {
    return raw.mapNotNull { json ->
        runCatching {
            fun pick(key: String): String {
                return Regex("\"$key\"\\s*:\\s*\"(.*?)\"")
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?: ""
            }

            fun pickNumber(key: String): String {
                return Regex("\"$key\"\\s*:\\s*(\\d+)")
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?: "0"
            }

            SavedResourceRequest(
                id = pick("id"),
                resourceName = pick("resourceName"),
                category = pick("category"),
                quantity = pickNumber("quantity"),
                urgency = pick("urgency"),
                status = pick("status").ifBlank { "Pending" },
                timestamp = pickNumber("timestamp").toLongOrNull() ?: 0L
            )
        }.getOrNull()
    }.sortedByDescending { it.timestamp }
}

private fun parseSavedReviewRatings(raw: Set<String>): List<SavedReviewRating> {
    return raw.mapNotNull { json ->
        runCatching {
            fun pick(key: String): String {
                return Regex("\"$key\"\\s*:\\s*\"(.*?)\"")
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?: ""
            }

            fun pickNumber(key: String): Int {
                return Regex("\"$key\"\\s*:\\s*(\\d+)")
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: 0
            }

            SavedReviewRating(
                incidentId = pick("incidentId"),
                responseRating = pickNumber("responseRating"),
                communicationRating = pickNumber("communicationRating"),
                professionalismRating = pickNumber("professionalismRating"),
                outcome = pick("outcome"),
                timestamp = pickNumber("timestamp").toLong()
            )
        }.getOrNull()
    }
}

private fun resourceStatusColors(status: String): Pair<Color, Color> {
    val dark = ThemeController.isDarkMode.value
    return when (status.lowercase()) {
        "pending" -> Color(0xFFE65100) to (if (dark) Color(0xFF3D2A14) else Color(0xFFFFF3E0))
        "approved" -> Color(0xFF2E7D32) to (if (dark) Color(0xFF16311A) else Color(0xFFE8F5E9))
        "rejected" -> Color(0xFFD32F2F) to (if (dark) Color(0xFF3A1616) else Color(0xFFFFEBEE))
        "cancelled" -> (if (dark) Color(0xFFB0B0B0) else Color(0xFF616161)) to (if (dark) Color(0xFF262626) else Color(0xFFF5F5F5))
        else -> RFColors.TextSecondary to RFColors.SurfaceBg
    }
}

@Composable
private fun ResourceRequestHistoryCard(
    request: SavedResourceRequest,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    isLatest: Boolean = false
) {
    val statusColors = resourceStatusColors(request.status)
    val isRejected  = request.status.equals("Rejected", ignoreCase = true)
    val isApproved  = request.status.equals("Approved", ignoreCase = true)
    val isCancelled = request.status.equals("Cancelled", ignoreCase = true)

    val steps = listOf("Sent", "Under Review", "Approved")
    val currentStepIndex = when (request.status.lowercase()) {
        "pending"  -> 1
        "approved" -> 2
        else       -> 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLatest && !isCancelled && !isRejected) RFColors.Primary.copy(alpha = 0.4f) else RFColors.Border
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Top row: icon, name, urgency, status badge ─────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(RFColors.Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        tint = RFColors.Primary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${request.resourceName} x${request.quantity}",
                        fontWeight = FontWeight.SemiBold,
                        color = RFColors.Text,
                        fontSize = 14.sp
                    )
                    Text(
                        "${request.category} • ${request.urgency} urgency",
                        color = RFColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusColors.second)
                        .border(
                            1.dp,
                            statusColors.first.copy(alpha = 0.45f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        request.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColors.first
                    )
                }
            }

            // ── Progress stepper — replaces the old standalone status card ──
            when {
                isRejected -> {
                    Text(
                        "Request declined by dispatch",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                isCancelled -> {
                    Text(
                        "This request was cancelled",
                        color = RFColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { index, label ->
                            val isDone = index <= currentStepIndex
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) RFColors.Primary else RFColors.Border)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    label,
                                    fontSize = 9.sp,
                                    color = if (isDone) RFColors.Primary else RFColors.TextSecondary
                                )
                            }
                            if (index < steps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.dp)
                                        .background(if (index < currentStepIndex) RFColors.Primary else RFColors.Border)
                                )
                            }
                        }
                    }
                    if (isApproved) {
                        Text(
                            "Your request has been approved ✓",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Cancel action (pending only) ────────────────────────────
            if (request.status.equals("Pending", ignoreCase = true)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceRequestDetailsSheet(
    request: SavedResourceRequest,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val statusColor = when (request.status.lowercase()) {
        "approved" -> Color(0xFF2E7D32)
        "rejected" -> Color(0xFFD32F2F)
        else -> Color(0xFFF57C00)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = RFColors.SurfaceBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                "Resource Request Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RFColors.Text
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("Request ID", request.id)
                    InfoRow("Resource", request.resourceName)
                    InfoRow("Category", request.category)
                    InfoRow("Quantity", request.quantity)
                    InfoRow("Urgency", request.urgency)
                    InfoRow("Status", request.status)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    request.status,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            if (request.status.equals("Pending", ignoreCase = true)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text("Cancel Request")
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RFColors.Primary
                )
            ) {
                Text("Close")
            }
        }
    }
}