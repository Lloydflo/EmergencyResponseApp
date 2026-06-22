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
import com.ers.emergencyresponseapp.home.IncidentStore
import com.ers.emergencyresponseapp.home.IncidentStatus
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

// ─── Design tokens ────────────────────────────────────────────────────────────
private object RFColors {
    val Primary       = Color(0xFF4C8A89)
    val Secondary     = Color(0xFF3A506B)
    val Tertiary      = Color(0xFF1C2541)
    val Text          = Color(0xFF171717)
    val TextSecondary = Color(0xFF575757)
    val Border        = Color(0xFFE5E5E5)
    val Bg            = Color(0xFFFFFFFF)
    val SurfaceBg     = Color(0xFFF6F8FA)
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
private fun decodeBitmap(ctx: Context, uriStr: String): Bitmap? =
    runCatching {
        if (uriStr.startsWith("file://"))
            BitmapFactory.decodeFile(Uri.parse(uriStr).path)
        else
            ctx.contentResolver.openInputStream(Uri.parse(uriStr))
                ?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

/**
 * Decode for full-screen preview: reads the file twice —
 * first pass gets the native dimensions, second pass decodes at exactly
 * the largest power-of-2 sample that keeps the image >= screen size,
 * preventing both blur-from-upscaling AND oom-from-giant-bitmap.
 */
private fun decodeBitmapHighQuality(ctx: Context, uriStr: String): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

    // First pass — measure only
    if (uriStr.startsWith("file://")) {
        BitmapFactory.decodeFile(Uri.parse(uriStr).path, options)
    } else {
        ctx.contentResolver.openInputStream(Uri.parse(uriStr))
            ?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    val srcW = options.outWidth
    val srcH = options.outHeight

    // Screen size (pixels)
    val dm    = ctx.resources.displayMetrics
    val reqW  = dm.widthPixels
    val reqH  = dm.heightPixels

    // Largest power-of-2 inSampleSize that keeps image ≥ screen dimensions
    var sample = 1
    while ((srcW / (sample * 2)) >= reqW && (srcH / (sample * 2)) >= reqH) {
        sample *= 2
    }

    // Second pass — full decode at chosen sample
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize        = sample
        inPreferredConfig   = android.graphics.Bitmap.Config.ARGB_8888  // best colour depth
    }

    if (uriStr.startsWith("file://")) {
        BitmapFactory.decodeFile(Uri.parse(uriStr).path, decodeOpts)
    } else {
        ctx.contentResolver.openInputStream(Uri.parse(uriStr))
            ?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
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
            contentColor   = if (selected) accentColor else RFColors.TextSecondary
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
private fun FullScreenImageDialog(uriStr: String, onDismiss: () -> Unit) {
    val ctx    = LocalContext.current
    val bitmap = remember(uriStr) { decodeBitmapHighQuality(ctx, uriStr) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = true,
            decorFitsSystemWindows  = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = "Full proof image",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 16.dp),
                    contentScale       = ContentScale.Fit,       // never upscale beyond native size
                    filterQuality      = androidx.compose.ui.graphics.FilterQuality.High  // bilinear filtering
                )
            } else {
                Text("Image not available", color = Color.White, fontSize = 16.sp)
            }

            // Close button top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = RFColors.Primary)
                            Text(incident.date, fontSize = 13.sp, color = RFColors.TextSecondary)
                        }
                        incident.completionNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = RFColors.Primary)
                                Text(notes, fontSize = 12.sp, color = RFColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Proof thumbnail — tap to open full-screen
                    incident.proofUri?.let { uriStr ->
                        val ctx    = LocalContext.current
                        val bitmap = remember(uriStr) { decodeBitmap(ctx, uriStr) }
                        if (bitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)                            // slightly bigger thumb
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, RFColors.Border, RoundedCornerShape(12.dp))
                                    .clickable { onViewImage(uriStr) }
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Proof photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Zoom hint overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f))
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(RFColors.Primary.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                }
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

    val maxQty = category.maxQty
    val isFormValid = resourceName.isNotBlank() && quantity > 0 && location.isNotBlank()

    // When category changes → reset resource name to first item of new category
    LaunchedEffect(category) {
        resourceName = category.items.first()
        if (quantity > category.maxQty) quantity = 1
    }

    // GPS auto-fill — getLastLocation first (instant), then getCurrentLocation as fallback
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
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
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            "Request Resources",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = RFColors.Text
                        )
                        Text(
                            "Fill in the details below",
                            fontSize = 13.sp,
                            color = RFColors.TextSecondary
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            if (!isSending) onDismiss()
                        },
                        enabled = !isSending
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = RFColors.TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = RFColors.Border)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepChip("1", "Resource", formStep == 1, Modifier.weight(1f))
                    StepChip("2", "Details", formStep == 2, Modifier.weight(1f))
                }

                if (formStep == 1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        // ── Category chips ────────────────────────────────────────────

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Category",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RFColors.TextSecondary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(ResCategory.entries) { _, cat ->
                                    val isSel = category == cat
                                    OutlinedButton(
                                        onClick = { category = cat },
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSel) RFColors.Primary.copy(alpha = 0.13f) else Color.Transparent,
                                            contentColor = if (isSel) RFColors.Primary else RFColors.TextSecondary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSel) RFColors.Primary.copy(alpha = 0.6f) else RFColors.Border
                                        )
                                    ) {
                                        Icon(
                                            cat.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
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


                        // ── Resource Name dropdown ────────────────────────────────────

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Resource Name *",
                                fontSize = 13.sp,
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


                        // ── Quantity stepper + Urgency ────────────────────────────────

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            // Quantity stepper (–  N  +) with max limit
                            Column(
                                modifier = Modifier.width(130.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Quantity * (max $maxQty)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RFColors.TextSecondary
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, RFColors.Border, RoundedCornerShape(12.dp))
                                        .background(RFColors.Bg),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Minus
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 12.dp,
                                                    bottomStart = 12.dp
                                                )
                                            )
                                            .background(
                                                if (quantity > 1) RFColors.Primary.copy(alpha = 0.10f) else RFColors.Border.copy(
                                                    alpha = 0.4f
                                                )
                                            )
                                            .clickable(enabled = quantity > 1) { quantity-- },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = if (quantity > 1) RFColors.Primary else RFColors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = quantity.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = RFColors.Text
                                    )
                                    // Plus
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    topEnd = 12.dp,
                                                    bottomEnd = 12.dp
                                                )
                                            )
                                            .background(
                                                if (quantity < maxQty) RFColors.Primary.copy(
                                                    alpha = 0.10f
                                                ) else RFColors.Border.copy(alpha = 0.4f)
                                            )
                                            .clickable(enabled = quantity < maxQty) { quantity++ },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = if (quantity < maxQty) RFColors.Primary else RFColors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                // limit hint
                                if (quantity >= maxQty) {
                                    Text(
                                        "Max limit reached",
                                        fontSize = 11.sp,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                            }

                            // Urgency selector
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Urgency",
                                    fontSize = 12.sp,
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



                if (formStep == 2) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        item {
                            // ── Incident ID ───────────────────────────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Incident ID (optional)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RFColors.TextSecondary
                                )
                                OutlinedTextField(
                                    value = incidentId, onValueChange = { incidentId = it },
                                    placeholder = {
                                        Text(
                                            "e.g. INC-20240727",
                                            color = RFColors.TextSecondary.copy(alpha = 0.6f)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Badge,
                                            contentDescription = null,
                                            tint = RFColors.Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }


                            // ── Delivery Location (GPS auto-fill) ─────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Delivery Location *",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RFColors.TextSecondary
                                    )
                                    // Use My Location button
                                    OutlinedButton(
                                        onClick = { fetchLocation() },
                                        enabled = !isLocating,
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 10.dp,
                                            vertical = 4.dp
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RFColors.Primary),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            RFColors.Primary.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        if (isLocating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = RFColors.Primary
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.MyLocation,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isLocating) "Locating…" else "Use My Location",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = location,
                                    onValueChange = { location = it },
                                    placeholder = {
                                        Text(
                                            "Street, barangay, landmark…",
                                            color = RFColors.TextSecondary.copy(alpha = 0.6f)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 2,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = RFColors.Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                if (location.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            "Location set",
                                            fontSize = 11.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }


                            // ── Additional Notes ──────────────────────────────────────────

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Additional Notes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RFColors.TextSecondary
                                )
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    placeholder = {
                                        Text(
                                            "Any special instructions or context…",
                                            color = RFColors.TextSecondary.copy(alpha = 0.6f)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 3,
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Notes,
                                            contentDescription = null,
                                            tint = RFColors.Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = RFColors.SurfaceBg
                                )
                            ) {

                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {

                                    InfoRow(
                                        label = "Requested By",
                                        value = responderName
                                    )

                                    InfoRow(
                                        label = "Department",
                                        value = "Responder"
                                    )

                                    InfoRow(
                                        label = "Date",
                                        value = SimpleDateFormat(
                                            "MMM dd, yyyy hh:mm a",
                                            Locale.getDefault()
                                        ).format(Date())
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Validation hint ───────────────────────────────────────────
                if (formStep == 2 && !isFormValid) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF3E0)).padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Resource name, quantity, and location are required.",
                            fontSize = 12.sp,
                            color = Color(0xFFF57C00)
                        )
                    }
                }

                // ── Navigation / Submit buttons ───────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    if (formStep == 2) {
                        OutlinedButton(
                            onClick = { if (!isSending) formStep = 1 },
                            enabled = !isSending,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
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

                            val req = ResRequest(
                                resourceName = resourceName.trim(),
                                category = category,
                                quantity = quantity,
                                urgency = urgency,
                                incidentId = incidentId.trim().ifBlank { "N/A" },
                                location = location.trim(),
                                notes = notes.trim(),
                                requestedBy = responderName
                            )

                            try {
                                Log.i(
                                    "ResourceRequest",
                                    "Sending to admin: ${req.id} — ${req.resourceName} x${req.quantity}"
                                )

                                val json = """
                                    {
                                      "id": "${req.id}",
                                      "resourceName": "${req.resourceName}",
                                      "category": "${req.category.displayName}",
                                      "quantity": ${req.quantity},
                                      "urgency": "${req.urgency.displayName}",
                                      "incidentId": "${req.incidentId}",
                                      "location": "${req.location}",
                                      "notes": "${req.notes}",
                                      "requestedBy": "${req.requestedBy}",
                                      "timestamp": ${req.timestamp},
                                      "status": "Pending"
                                    }
                                    """.trimIndent()

                                val prefs = context.getSharedPreferences(
                                    "resource_requests",
                                    Context.MODE_PRIVATE
                                )

                                val old = prefs.getStringSet(
                                    "requests",
                                    emptySet()
                                ) ?: emptySet()

                                prefs.edit()
                                    .putStringSet("requests", old + json)
                                    .apply()

                                Toast.makeText(
                                    context,
                                    "Request submitted for admin review",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isSending = false
                                onSubmit()

                            } catch (e: Exception) {
                                Log.e("ResourceRequest", "Failed: ${e.message}")

                                Toast.makeText(
                                    context,
                                    "Failed to send request",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isSending = false
                            }
                        },
                        enabled = if (formStep == 1) {
                            resourceName.isNotBlank() && quantity > 0
                        } else {
                            isFormValid && !isSending
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
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
                        }
                    }
                }
            }
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
    var selectedRequest by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var requestToCancel by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var showAllRequests by remember { mutableStateOf(false) }

    fun submitReview(incident: ReviewableIncident, text: String) {
        val plainId = incident.id.removePrefix("#")
        try {
            IncidentStore.submitReview(plainId, text)
            Toast.makeText(context, "Review submitted", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to submit review", Toast.LENGTH_SHORT).show()
        }
    }

    val candidateNames = listOfNotNull(
        prefs.getString("account_username", null),
        prefs.getString("responder_name", null),
        prefs.getString("account_full_name", null)
    ).map { it.trim().lowercase() }.toSet()

    val derivedIncidents = IncidentStore.incidents
        .filter { inc ->
            val assigned = inc.assignedTo?.trim()?.lowercase()
            val matchesAssigned = if (candidateNames.isNotEmpty()) assigned != null && candidateNames.contains(assigned) else assigned != null
            matchesAssigned && (inc.status == IncidentStatus.PENDING_REVIEW || inc.status == IncidentStatus.SUBMITTED_REVIEW || inc.status == IncidentStatus.RESOLVED)
        }
        .map { inc ->
            val proof        = runCatching { IncidentStore.getProofUri(inc.id) }.getOrNull()
            val notes        = runCatching { IncidentStore.getCompletionNotes(inc.id) }.getOrNull()
            val completionTs = runCatching { IncidentStore.getCompletionTime(inc.id) }.getOrNull()
            val status = when (inc.status) {
                IncidentStatus.PENDING_REVIEW   -> ReviewStatus.Pending
                IncidentStatus.SUBMITTED_REVIEW -> ReviewStatus.Submitted
                IncidentStatus.RESOLVED         -> if (completionTs != null) ReviewStatus.Completed else ReviewStatus.Submitted
                else -> ReviewStatus.Pending
            }
            val dateStr = completionTs?.let { java.text.SimpleDateFormat("yyyy-MM-dd · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
                ?: java.text.SimpleDateFormat("yyyy-MM-dd · HH:mm", java.util.Locale.getDefault()).format(inc.timeReported)
            ReviewableIncident("#${inc.id}", inc.type.displayName, dateStr, status, proof, notes)
        }

    val allIncidents = derivedIncidents

    val pendingCount   = allIncidents.count { it.status == ReviewStatus.Pending }
    val submittedCount = allIncidents.count { it.status == ReviewStatus.Submitted }
    val completedCount = allIncidents.count { it.status == ReviewStatus.Completed }
    val filtered       = allIncidents.filter { it.status == selected }

    val resourcePrefs = context.getSharedPreferences("resource_requests", Context.MODE_PRIVATE)

    val reviewPrefs = context.getSharedPreferences("review_ratings", Context.MODE_PRIVATE)
    var ratingRefreshKey by remember { mutableIntStateOf(0) }

    val parsedRequests = remember(requestRefreshKey) {
        parseSavedResourceRequests(
            resourcePrefs.getStringSet("requests", emptySet()) ?: emptySet()
        ).sortedByDescending { it.timestamp }
    }


    val resourceRequests = parsedRequests.size

    val submittedReviews = allIncidents.filter {
        it.status == ReviewStatus.Submitted || it.status == ReviewStatus.Completed
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

    fun cancelResourceRequest(requestId: String) {
        val old = resourcePrefs.getStringSet("requests", emptySet()) ?: emptySet()

        val updated = old.map { json ->
            if (
                json.contains("\"id\": \"$requestId\"") ||
                json.contains("\"id\":\"$requestId\"")
            ) {
                json.replace(
                    Regex("\"status\"\\s*:\\s*\".*?\""),
                    "\"status\": \"Cancelled\""
                )
            } else {
                json
            }
        }.toSet()

        resourcePrefs.edit()
            .putStringSet("requests", updated)
            .apply()

        requestRefreshKey++

        Toast.makeText(
            context,
            "Resource request cancelled",
            Toast.LENGTH_SHORT
        ).show()
    }

    val recentRequests = parsedRequests.take(3)
    val filteredRequests = parsedRequests.filter {
        it.resourceName.contains(
            requestSearch,
            ignoreCase = true
        ) ||
                it.id.contains(
                    requestSearch,
                    ignoreCase = true
                )
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
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$pendingCount pending • $submittedCount submitted • $completedCount completed",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth().padding(horizontal = 12.dp)
                        .shadow(10.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(RFColors.Primary, RFColors.Secondary)))
                        .clickable { showResourceForm = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Request Resources", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("Submit a resource request to admin", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                        }
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Recent Resource Requests",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RFColors.Text
                            )

                            Text(
                                "Last updated ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())}",
                                fontSize = 11.sp,
                                color = RFColors.TextSecondary
                            )
                        }

                        Row {
                            TextButton(
                                onClick = { showAllRequests = true },
                                enabled = parsedRequests.isNotEmpty()
                            ) {
                                Text("View All")
                            }

                            IconButton(
                                onClick = { requestRefreshKey++ }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = RFColors.Primary
                                )
                            }
                        }
                    }

                    if (recentRequests.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(RFColors.Primary.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = RFColors.Primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Text(
                                    "No resource requests yet",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = RFColors.Text
                                )

                                Text(
                                    "Requests you submit will appear here for tracking.",
                                    fontSize = 12.sp,
                                    color = RFColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                OutlinedButton(
                                    onClick = { showResourceForm = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Request")
                                }
                            }
                        }
                    } else {
                        recentRequests.forEach { request ->
                            ResourceRequestHistoryCard(
                                request = request,
                                onClick = {
                                    selectedRequest = request
                                },
                                onCancel = {
                                    requestToCancel = request
                                }
                            )
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
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${selected.label} (${filtered.size})", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = RFColors.Text)
                    Text("Sorted by date", fontSize = 12.sp, color = RFColors.TextSecondary)
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
            itemsIndexed(filtered) { index, incident ->
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
                            val plainId = inc.id.removePrefix("#")
                            detailsText.value = runCatching { IncidentStore.getSubmittedReview(plainId) }.getOrNull()
                                ?: runCatching { IncidentStore.getCompletionNotes(plainId) }.getOrNull()
                                        ?: "No review details available."
                            detailsTarget.value = inc
                            showFullReviewDetails = true
                        },
                        onViewImage = { uri -> fullScreenImageUri = uri }   // ← use full-screen dialog
                    )
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
                            val fullReview = """
                        Outcome: $selectedOutcome

                        Response Time Rating: $responseRating/5
                        Communication Rating: $communicationRating/5
                        Professionalism Rating: $professionalismRating/5

                        Feedback:
                        ${reviewText.value}
                    """.trimIndent()

                            submitReview(target, fullReview)

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

                            reviewPrefs.edit()
                                .putStringSet("ratings", oldRatings + ratingJson)
                                .apply()

                            ratingRefreshKey++

                            showSubmitReviewConfirm = false
                            showComposeDialog.value = false
                            composeTarget.value = null
                            reviewText.value = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                    ) {
                        Text("Yes, Submit")
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
                                    "${parsedRequests.size} total requests",
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
                                    }
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
                responderName = responderName,
                onDismiss     = { showResourceForm = false },
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
    return when (status.lowercase()) {
        "pending" -> Color(0xFFE65100) to Color(0xFFFFF3E0)
        "approved" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        "rejected" -> Color(0xFFD32F2F) to Color(0xFFFFEBEE)
        "cancelled" -> Color(0xFF616161) to Color(0xFFF5F5F5)
        else -> RFColors.TextSecondary to RFColors.SurfaceBg
    }
}

@Composable
private fun ResourceRequestHistoryCard(
    request: SavedResourceRequest,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    val urgencyColor = when (request.urgency.lowercase()) {
        "critical" -> Color(0xFF6A1B9A)
        "high" -> Color(0xFFD32F2F)
        "medium" -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }

    val statusColor = when (request.status.lowercase()) {
        "approved" -> Color(0xFF2E7D32)
        "rejected" -> Color(0xFFD32F2F)
        "cancelled" -> Color(0xFF757575)
        else -> Color(0xFFF57C00)
    }

    val statusBg = statusColor.copy(alpha = 0.12f)
    val statusColors = resourceStatusColors(request.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            if (request.status.equals("Pending", ignoreCase = true)) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = Color(0xFFD32F2F))
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