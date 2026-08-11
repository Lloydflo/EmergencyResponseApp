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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ers.emergencyresponseapp.ui.components.AppPullToRefresh
import com.ers.emergencyresponseapp.ui.components.QuickTemplateBar
import com.ers.emergencyresponseapp.ui.theme.ThemeController
import com.ers.emergencyresponseapp.data.AfterActionReportRecord
import com.ers.emergencyresponseapp.data.OperationalRepository
import com.ers.emergencyresponseapp.data.SaveAfterActionReportRequest
import coil.compose.AsyncImage

// ─── Design tokens ────────────────────────────────────────────────────────────
private object RFColors {
    val Primary: Color get() = Color(0xFF4C8A89)
    val Secondary: Color get() = Color(0xFF3A506B)
    val Tertiary: Color get() = Color(0xFF1C2541)

    val Text: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFFAFAFA) else Color(0xFF171717)
    val TextSecondary: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFB6BAC3) else Color(0xFF5F6368)
    val TextMuted: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF858A94) else Color(0xFF7A7F87)

    val Border: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF30343B) else Color(0xFFE1E5EA)
    val Bg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF17191E) else Color(0xFFFFFFFF)
    val ElevatedBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF202329) else Color(0xFFFFFFFF)
    val SurfaceBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF0D0F12) else Color(0xFFF4F6F8)
    val MutedBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF22262D) else Color(0xFFF7F8FA)
    val InputBg: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF12151A) else Color(0xFFF8FAFB)

    val Success: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF76D39A) else Color(0xFF247A47)
    val Warning: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFFFC36A) else Color(0xFFA45B00)
    val Danger: Color get() = if (ThemeController.isDarkMode.value) Color(0xFFFF8A89) else Color(0xFFB3261E)
    val Info: Color get() = if (ThemeController.isDarkMode.value) Color(0xFF74C8D0) else Color(0xFF2E777B)
}

private fun tonalSurface(accent: Color, lightAlpha: Float = 0.10f, darkAlpha: Float = 0.20f): Color {
    return accent.copy(alpha = if (ThemeController.isDarkMode.value) darkAlpha else lightAlpha)
}

/**
 * An approved report remains in the active Approved queue for one full day so
 * the responder can easily see the admin decision. It is then shown in the
 * read-only monthly history instead of the active review-status queue.
 */
private const val APPROVED_HISTORY_DELAY_MILLIS = 24L * 60L * 60L * 1000L

// ─── Completed incident ──────────────────────────────────────────────────────
private data class CompletedIncident(
    val id: String,
    val type: String,
    val date: String,
    val proofUri: String? = null,
    val completionNotes: String? = null,
    val completedAtMillis: Long = 0L
)

// ─── Resource request models ──────────────────────────────────────────────────
private enum class ResCategory(
    val displayName: String,
    val icon: ImageVector,
    val items: List<String>,
    val maxQty: Int
) {
    MEDICAL_SUPPLIES(
        "Medical Supplies", Icons.Default.LocalHospital,
        listOf(
            "Oxygen Cylinder", "First Aid Kit", "Trauma Dressings", "IV Administration Set",
            "Splint Set", "Cervical Collar", "Pulse Oximeter", "Suction Kit",
            "AED Pads", "Burn Dressing Pack", "Medical Gloves", "Trauma Bag Refill"
        ),
        maxQty = 50
    ),
    FIRE_RESCUE_EQUIPMENT(
        "Fire & Rescue Equipment", Icons.Default.Construction,
        listOf(
            "Fire Extinguisher", "Fire Hose", "SCBA Cylinder", "Hydraulic Cutter/Spreader",
            "Rope and Harness Set", "Thermal Camera", "Portable Water Pump", "Rescue Ladder",
            "Chainsaw", "Salvage Cover", "Search Light", "Rescue Tool Kit"
        ),
        maxQty = 20
    ),
    PPE_SAFETY(
        "PPE & Safety Gear", Icons.Default.HealthAndSafety,
        listOf(
            "Protective Helmet", "Respirator / Mask", "HazMat Suit", "Reflective Safety Vest",
            "Protective Gloves", "Safety Goggles", "Protective Boots", "Hearing Protection",
            "Fall-Arrest Harness", "Disposable Coverall"
        ),
        maxQty = 50
    ),
    COMMUNICATION_POWER(
        "Communication & Power", Icons.Default.Bolt,
        listOf(
            "Handheld Radio", "Spare Radio Battery", "Portable Radio Repeater",
            "Portable Generator", "Power Bank", "Charging Station", "Extension Cable",
            "Portable Floodlight", "Megaphone", "Satellite Communication Kit"
        ),
        maxQty = 20
    ),
    LOGISTICS_SUPPLIES(
        "Logistics & Consumables", Icons.Default.Inventory2,
        listOf(
            "Drinking Water", "Food Pack", "Blanket", "Emergency Tent", "Tarpaulin",
            "Sandbag", "Sanitation Kit", "Fuel Container", "Cleaning / Decontamination Supply",
            "Batteries", "Barrier Tape", "Evidence / Documentation Supply"
        ),
        maxQty = 200
    ),
    OTHER(
        "Other Equipment / Supply", Icons.Default.Inventory,
        listOf("Other Equipment (Specify in Notes)", "Other Supply (Specify in Notes)"),
        maxQty = 100
    )
}

private enum class ResUrgency(val displayName: String, val color: Color, val bgColor: Color) {
    LOW("Low",           Color(0xFF388E3C), Color(0xFFE8F5E9)),
    MEDIUM("Medium",     Color(0xFFF57C00), Color(0xFFFFF3E0)),
    HIGH("High",         Color(0xFFD32F2F), Color(0xFFFFEBEE)),
    CRITICAL("Critical", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
}

private data class SavedResourceRequest(
    val id: String,
    val resourceName: String,
    val category: String,
    val quantity: String,
    val urgency: String,
    val status: String,
    val incidentId: String,
    val location: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)



private enum class ReportHubTab(val label: String, val icon: ImageVector) {
    INCIDENTS("Reports", Icons.Default.Description),
    HISTORY("History", Icons.Default.History),
    RESOURCES("Equipment", Icons.Default.Inventory2)
}

private enum class OperationalReportStatus(val label: String, val color: Color) {
    /** Saved locally/server-side but not yet sent to admin review. */
    PENDING("Pending", Color(0xFFB86B12)),
    /** Submitted by the responder and locked while an authorized admin reviews it. */
    SUBMITTED("Submitted", Color(0xFF2E777B)),
    /** Approved by an authorized admin. The database keeps the legacy value `verified`. */
    APPROVED("Approved", Color(0xFF2E8B57)),
    /** Returned by admin and editable again; grouped under Pending in the responder hub. */
    RETURNED("Needs Revision", Color(0xFFB3261E))
}

private enum class ReportWorkflowFilter(val label: String) {
    PENDING("Pending"),
    SUBMITTED("Submitted"),
    APPROVED("Approved")
}

/**
 * Unit-level responder documentation persisted through the PHP/MySQL API.
 * Incident command/admin may approve or return the submitted report.
 */
private data class AfterActionReport(
    val incidentId: String,
    val incidentType: String,
    val responderName: String,
    val operationalOutcome: String,
    val incidentSummary: String,
    val actionsTaken: String,
    val personsAssisted: Int,
    val injuries: Int,
    val fatalities: Int,
    val resourcesUsed: String,
    val agenciesInvolved: String,
    val handoffDetails: String,
    val safetyIssues: String,
    val followUpRequired: Boolean,
    val followUpDetails: String,
    val lessonsLearned: String,
    val reviewerNotes: String,
    val status: OperationalReportStatus,
    val approvedAt: Long,
    val isInHistory: Boolean,
    val sourceCompletedAt: Long,
    val sourceCompletionNotes: String,
    val sourceCompletionProofUri: String?,
    val updatedAt: Long
)

private data class ArchivedReportEntry(
    val incident: CompletedIncident,
    val report: AfterActionReport
)

private data class ReportHistoryMonth(
    val key: String,
    val label: String,
    val count: Int
)

private fun normalizedIncidentId(raw: String): String = raw.removePrefix("#").trim()

private const val AFTER_ACTION_DRAFT_PREFS = "after_action_report_drafts"

private data class AfterActionDraftSnapshot(
    val operationalOutcome: String,
    val incidentSummary: String,
    val actionsTaken: String,
    val personsAssisted: String,
    val injuries: String,
    val fatalities: String,
    val resourcesUsed: String,
    val agenciesInvolved: String,
    val handoffDetails: String,
    val safetyIssues: String,
    val followUpRequired: Boolean,
    val followUpDetails: String,
    val lessonsLearned: String
)

private data class StoredAfterActionDraft(
    val savedAt: Long,
    val step: Int,
    val snapshot: AfterActionDraftSnapshot
)

private val afterActionDraftKeys = listOf(
    "saved_at",
    "step",
    "operational_outcome",
    "incident_summary",
    "actions_taken",
    "persons_assisted",
    "injuries",
    "fatalities",
    "resources_used",
    "agencies_involved",
    "handoff_details",
    "safety_issues",
    "follow_up_required",
    "follow_up_details",
    "lessons_learned"
)

private fun afterActionDraftPrefix(responderId: Int, incidentId: String): String =
    "responder_${responderId}_incident_${normalizedIncidentId(incidentId)}."

private fun readAfterActionDraft(
    context: Context,
    responderId: Int,
    incidentId: String
): StoredAfterActionDraft? {
    if (responderId <= 0) return null
    val prefs = context.getSharedPreferences(AFTER_ACTION_DRAFT_PREFS, Context.MODE_PRIVATE)
    val prefix = afterActionDraftPrefix(responderId, incidentId)
    val savedAt = prefs.getLong("${prefix}saved_at", 0L)
    if (savedAt <= 0L) return null

    return StoredAfterActionDraft(
        savedAt = savedAt,
        step = prefs.getInt("${prefix}step", 1).coerceIn(1, 3),
        snapshot = AfterActionDraftSnapshot(
            operationalOutcome = prefs.getString("${prefix}operational_outcome", "Resolved")
                ?: "Resolved",
            incidentSummary = prefs.getString("${prefix}incident_summary", "").orEmpty(),
            actionsTaken = prefs.getString("${prefix}actions_taken", "").orEmpty(),
            personsAssisted = prefs.getString("${prefix}persons_assisted", "0") ?: "0",
            injuries = prefs.getString("${prefix}injuries", "0") ?: "0",
            fatalities = prefs.getString("${prefix}fatalities", "0") ?: "0",
            resourcesUsed = prefs.getString("${prefix}resources_used", "").orEmpty(),
            agenciesInvolved = prefs.getString("${prefix}agencies_involved", "").orEmpty(),
            handoffDetails = prefs.getString("${prefix}handoff_details", "").orEmpty(),
            safetyIssues = prefs.getString("${prefix}safety_issues", "").orEmpty(),
            followUpRequired = prefs.getBoolean("${prefix}follow_up_required", false),
            followUpDetails = prefs.getString("${prefix}follow_up_details", "").orEmpty(),
            lessonsLearned = prefs.getString("${prefix}lessons_learned", "").orEmpty()
        )
    )
}

private fun saveAfterActionDraft(
    context: Context,
    responderId: Int,
    incidentId: String,
    step: Int,
    snapshot: AfterActionDraftSnapshot
) {
    if (responderId <= 0) return
    val prefix = afterActionDraftPrefix(responderId, incidentId)
    context.getSharedPreferences(AFTER_ACTION_DRAFT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong("${prefix}saved_at", System.currentTimeMillis())
        .putInt("${prefix}step", step.coerceIn(1, 3))
        .putString("${prefix}operational_outcome", snapshot.operationalOutcome)
        .putString("${prefix}incident_summary", snapshot.incidentSummary)
        .putString("${prefix}actions_taken", snapshot.actionsTaken)
        .putString("${prefix}persons_assisted", snapshot.personsAssisted)
        .putString("${prefix}injuries", snapshot.injuries)
        .putString("${prefix}fatalities", snapshot.fatalities)
        .putString("${prefix}resources_used", snapshot.resourcesUsed)
        .putString("${prefix}agencies_involved", snapshot.agenciesInvolved)
        .putString("${prefix}handoff_details", snapshot.handoffDetails)
        .putString("${prefix}safety_issues", snapshot.safetyIssues)
        .putBoolean("${prefix}follow_up_required", snapshot.followUpRequired)
        .putString("${prefix}follow_up_details", snapshot.followUpDetails)
        .putString("${prefix}lessons_learned", snapshot.lessonsLearned)
        .apply()
}

private fun clearAfterActionDraft(
    context: Context,
    responderId: Int,
    incidentId: String
) {
    if (responderId <= 0) return
    val prefix = afterActionDraftPrefix(responderId, incidentId)
    val editor = context.getSharedPreferences(AFTER_ACTION_DRAFT_PREFS, Context.MODE_PRIVATE)
        .edit()
    afterActionDraftKeys.forEach { key -> editor.remove("$prefix$key") }
    editor.apply()
}

private fun appendQuickTemplate(current: String, template: String): String {
    val existing = current.trimEnd()
    val addition = template.trim()
    if (addition.isBlank()) return current
    if (existing.isBlank()) return addition
    if (existing.lines().any { it.trim() == addition }) return existing
    return "$existing\n$addition"
}

private fun parseServerTimestamp(raw: String?): Long {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return 0L

    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull()?.let { return it }
    }
    return value.toLongOrNull() ?: 0L
}

private fun formatRequestTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Time unavailable"
    return SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        .format(Date(timestamp))
}

private fun AfterActionReport.historySortTime(): Long =
    approvedAt.takeIf { it > 0L } ?: updatedAt

private fun reportHistoryMonthKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(timestamp.coerceAtLeast(0L)))

private fun reportHistoryMonthLabel(timestamp: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        .format(Date(timestamp.coerceAtLeast(0L)))

private fun AfterActionReport.toHistoryIncidentFallback(): CompletedIncident {
    val displayTimestamp = sourceCompletedAt.takeIf { it > 0L } ?: historySortTime()
    val dateLabel = if (sourceCompletedAt > 0L) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            .format(Date(sourceCompletedAt))
    } else if (displayTimestamp > 0L) {
        "Approved ${SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(displayTimestamp))}"
    } else {
        "Approval date unavailable"
    }

    return CompletedIncident(
        id = "#${normalizedIncidentId(incidentId)}",
        type = incidentType.trim()
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: "General Incident",
        date = dateLabel,
        proofUri = sourceCompletionProofUri,
        completionNotes = sourceCompletionNotes.takeIf { it.isNotBlank() }
            ?: incidentSummary.takeIf { it.isNotBlank() },
        completedAtMillis = sourceCompletedAt.takeIf { it > 0L } ?: displayTimestamp
    )
}

private fun AfterActionReportRecord.toUiReport(): AfterActionReport {
    val uiStatus = when (status.lowercase(Locale.US)) {
        "submitted", "awaiting_review" -> OperationalReportStatus.SUBMITTED
        "verified", "approved" -> OperationalReportStatus.APPROVED
        "returned", "revision_required", "needs_revision" -> OperationalReportStatus.RETURNED
        "draft", "pending" -> OperationalReportStatus.PENDING
        else -> OperationalReportStatus.PENDING
    }
    val resolvedUpdatedAt = updatedAtMillis.takeIf { it > 0L } ?: createdAtMillis
    val resolvedApprovedAt = approvedAtMillis.takeIf { it > 0L }
        ?: if (uiStatus == OperationalReportStatus.APPROVED) resolvedUpdatedAt else 0L
    val historyByAge = uiStatus == OperationalReportStatus.APPROVED &&
            resolvedApprovedAt > 0L &&
            System.currentTimeMillis() - resolvedApprovedAt >= APPROVED_HISTORY_DELAY_MILLIS

    return AfterActionReport(
        incidentId = incidentId.toString(),
        incidentType = incidentType,
        responderName = responderName,
        operationalOutcome = operationalOutcome,
        incidentSummary = incidentSummary,
        actionsTaken = actionsTaken,
        personsAssisted = personsAssisted,
        injuries = injuries,
        fatalities = fatalities,
        resourcesUsed = resourcesUsed,
        agenciesInvolved = agenciesInvolved,
        handoffDetails = handoffDetails,
        safetyIssues = safetyIssues,
        followUpRequired = followUpRequired,
        followUpDetails = followUpDetails,
        lessonsLearned = lessonsLearned,
        reviewerNotes = reviewerNotes,
        status = uiStatus,
        approvedAt = resolvedApprovedAt,
        isInHistory = uiStatus == OperationalReportStatus.APPROVED &&
                (isInHistory || historyByAge),
        sourceCompletedAt = incidentCompletedAtMillis,
        sourceCompletionNotes = incidentCompletionNotes,
        sourceCompletionProofUri = incidentCompletionImagePath.takeIf { it.isNotBlank() },
        updatedAt = resolvedUpdatedAt
    )
}

private fun AfterActionReport.toApiRequest(
    responderId: Int
): SaveAfterActionReportRequest = SaveAfterActionReportRequest(
    incidentId = normalizedIncidentId(incidentId).toLongOrNull() ?: 0L,
    responderId = responderId,
    incidentType = incidentType,
    responderName = responderName,
    operationalOutcome = operationalOutcome,
    incidentSummary = incidentSummary,
    actionsTaken = actionsTaken,
    personsAssisted = personsAssisted,
    injuries = injuries,
    fatalities = fatalities,
    resourcesUsed = resourcesUsed,
    agenciesInvolved = agenciesInvolved,
    handoffDetails = handoffDetails,
    safetyIssues = safetyIssues,
    followUpRequired = followUpRequired,
    followUpDetails = followUpDetails,
    lessonsLearned = lessonsLearned,
    status = when (status) {
        OperationalReportStatus.PENDING,
        OperationalReportStatus.RETURNED -> "draft"
        OperationalReportStatus.SUBMITTED,
        OperationalReportStatus.APPROVED -> "submitted"
    }
)

private fun AfterActionReport.toPrintableText(): String = buildString {
    appendLine("RESPONDER AFTER-ACTION REPORT")
    appendLine()
    appendLine("Incident ID: #${normalizedIncidentId(incidentId)}")
    appendLine("Incident Type: $incidentType")
    appendLine("Prepared By: $responderName")
    appendLine("Operational Outcome: $operationalOutcome")
    appendLine("Report Status: ${status.label}")
    appendLine("Updated: ${SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(updatedAt))}")
    if (approvedAt > 0L) {
        appendLine(
            "Approved: ${SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(approvedAt))}"
        )
    }
    appendLine()
    appendLine("INCIDENT SUMMARY")
    appendLine(incidentSummary.ifBlank { "Not provided" })
    appendLine()
    appendLine("ACTIONS TAKEN")
    appendLine(actionsTaken.ifBlank { "Not provided" })
    appendLine()
    appendLine("PERSONS ASSISTED: $personsAssisted")
    appendLine("INJURIES: $injuries")
    appendLine("FATALITIES: $fatalities")
    appendLine()
    appendLine("RESOURCES USED")
    appendLine(resourcesUsed.ifBlank { "None recorded" })
    appendLine()
    appendLine("AGENCIES / UNITS INVOLVED")
    appendLine(agenciesInvolved.ifBlank { "None recorded" })
    appendLine()
    appendLine("HANDOFF / RECEIVING FACILITY")
    appendLine(handoffDetails.ifBlank { "No handoff recorded" })
    appendLine()
    appendLine("SAFETY ISSUES / NEAR MISSES")
    appendLine(safetyIssues.ifBlank { "None reported" })
    appendLine()
    appendLine("FOLLOW-UP REQUIRED: ${if (followUpRequired) "Yes" else "No"}")
    if (followUpRequired) appendLine(followUpDetails.ifBlank { "Details not provided" })
    appendLine()
    appendLine("LESSONS / RECOMMENDATIONS")
    appendLine(lessonsLearned.ifBlank { "None recorded" })
    if (reviewerNotes.isNotBlank()) {
        appendLine()
        appendLine("REVIEWER NOTES")
        appendLine(reviewerNotes)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Decode a file:// or content:// URI string to a Bitmap safely */
private val SERVER_BASE_URL: String get() = BuildConfig.BASE_URL.trimEnd('/')

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
private fun ReportStatCard(
    value: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick == null) {
        modifier
    } else {
        modifier.clickable(onClick = onClick)
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                tonalSurface(accent, lightAlpha = 0.09f, darkAlpha = 0.18f)
            } else {
                RFColors.Bg
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent.copy(alpha = 0.72f) else RFColors.Border
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = RFColors.Text)
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = if (selected) accent else RFColors.TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .height(3.dp)
                    .fillMaxWidth(0.55f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = if (selected) 1f else 0.78f))
            )
        }
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

// ─── Completed-incident report card ─────────────────────────────────────────
@Composable
private fun IncidentReportCard(
    incident: CompletedIncident,
    index: Int,
    report: AfterActionReport?,
    onOpenReport: (CompletedIncident) -> Unit,
    onViewImage: (String) -> Unit
) {
    var visible by remember(incident.id) { mutableStateOf(false) }
    LaunchedEffect(incident.id) {
        delay((index.coerceAtMost(8)) * 45L)
        visible = true
    }

    val reportAccent = report?.status?.color ?: RFColors.Warning
    val reportLabel = when (report?.status) {
        OperationalReportStatus.PENDING -> "Report status: Pending"
        OperationalReportStatus.SUBMITTED -> "Report status: Submitted"
        OperationalReportStatus.APPROVED -> if (report.isInHistory) {
            "History • Approved by admin"
        } else {
            "Report status: Approved"
        }
        OperationalReportStatus.RETURNED -> "Report status: Needs revision"
        null -> "Report status: Pending • Not started"
    }
    val reportAction = when (report?.status) {
        OperationalReportStatus.PENDING,
        OperationalReportStatus.RETURNED -> "Continue Report"
        OperationalReportStatus.SUBMITTED -> "View Submitted Report"
        OperationalReportStatus.APPROVED -> if (report.isInHistory) {
            "View Final Report"
        } else {
            "View Approved Report"
        }
        null -> "Create Report"
    }
    val reportTimestamp = when {
        report?.status == OperationalReportStatus.APPROVED && report.approvedAt > 0L -> report.approvedAt
        else -> report?.updatedAt ?: 0L
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                RFColors.Bg,
                                RFColors.Primary.copy(
                                    alpha = if (ThemeController.isDarkMode.value) 0.08f else 0.035f
                                )
                            )
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(RFColors.Primary, RFColors.Secondary)
                                )
                            )
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            incident.type,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RFColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${incident.id} • ${incident.date}",
                            fontSize = 11.sp,
                            color = RFColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = tonalSurface(RFColors.Success),
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            RFColors.Success.copy(alpha = 0.40f)
                        )
                    ) {
                        Text(
                            "Completed",
                            color = RFColors.Success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                Surface(
                    color = tonalSurface(reportAccent, lightAlpha = 0.08f, darkAlpha = 0.17f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        reportAccent.copy(alpha = 0.28f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = reportAccent,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            reportLabel,
                            color = reportAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (reportTimestamp > 0L) {
                            Text(
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                    .format(Date(reportTimestamp)),
                                color = RFColors.TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 92.dp),
                        color = RFColors.MutedBg,
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = RFColors.Primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Completion notes",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RFColors.Text
                                )
                            }
                            Text(
                                text = incident.completionNotes?.takeIf { it.isNotBlank() }
                                    ?: "No completion notes were provided.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = RFColors.TextSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    incident.proofUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                        val resolvedImageUrl = resolveImageUri(uriStr)
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(92.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .border(1.dp, RFColors.Border, RoundedCornerShape(13.dp))
                                .clickable { onViewImage(resolvedImageUrl) }
                        ) {
                            AsyncImage(
                                model = resolvedImageUrl,
                                contentDescription = "Completion evidence",
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                                color = Color.Black.copy(alpha = 0.64f)
                            ) {
                                Text(
                                    "View proof",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onOpenReport(incident) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RFColors.Primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        reportAction,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
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
    var category by rememberSaveable { mutableStateOf(ResCategory.MEDICAL_SUPPLIES) }
    var resourceName by rememberSaveable { mutableStateOf(ResCategory.MEDICAL_SUPPLIES.items.first()) }
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
    val requiresItemDescription = category == ResCategory.OTHER
    val isFormValid = resourceName.isNotBlank() &&
            quantity > 0 &&
            location.isNotBlank() &&
            (!requiresItemDescription || notes.isNotBlank())

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
                                    "Request Equipment & Supplies",
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
                            ProgressNode(number = 1, label = "Item", state = when {
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
                            SectionCard(title = "Item Details", icon = Icons.Default.Inventory2) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "Item Name *",
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
                                                                    .background(if (isSel) tonalSurface(u.color, lightAlpha = 0.12f, darkAlpha = 0.22f) else RFColors.MutedBg)
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
                                    InfoRow("Item", resourceName)
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
                                    onValueChange = { notes = it.take(1000) },
                                    placeholder = {
                                        Text("Any special instructions or context…", color = RFColors.TextSecondary.copy(alpha = 0.6f))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    maxLines = 3
                                )
                                QuickTemplateBar(
                                    fieldKey = "resource_request_notes",
                                    onApply = { template ->
                                        notes = appendQuickTemplate(notes, template)
                                    },
                                    enabled = !isSending,
                                    modifier = Modifier.padding(top = 2.dp)
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
                                        .background(tonalSurface(RFColors.Warning, lightAlpha = 0.10f, darkAlpha = 0.20f))
                                        .border(1.dp, RFColors.Warning.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = RFColors.Warning, modifier = Modifier.size(16.dp))
                                    Text(
                                        if (requiresItemDescription) {
                                            "Item name, quantity, delivery location, and an item description in Notes are required."
                                        } else {
                                            "Item name, quantity, and delivery location are required."
                                        },
                                        fontSize = 12.sp,
                                        color = RFColors.Warning
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
                                        Toast.makeText(context, "Equipment/supply request submitted for admin review", Toast.LENGTH_SHORT).show()
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


// ─── Post-incident hub helpers ─────────────────────────────────────────────────
@Composable
private fun PostIncidentHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(RFColors.Primary, RFColors.Secondary, RFColors.Tertiary)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        radius = 560f
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                ) {
                    Text(
                        "RESPONDER OPERATIONS",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Post-Incident Hub",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Unit reports, approval history, completion evidence, and equipment support",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Assignment,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun OperationalWorkflowCard(
    pendingCount: Int,
    submittedCount: Int,
    approvedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tonalSurface(RFColors.Info)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = RFColors.Info,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Who completes the incident report?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RFColors.Text
                    )
                    Text(
                        "Recommended responder workflow",
                        fontSize = 10.sp,
                        color = RFColors.TextSecondary
                    )
                }
                Surface(
                    color = tonalSurface(RFColors.Primary),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        "${pendingCount + submittedCount + approvedCount} active",
                        color = RFColors.Primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            OperationalGuideStep(
                number = "1",
                title = "Responding unit documents",
                detail = "Record actions taken, outcome, handoff, resources used, and safety issues."
            )
            OperationalGuideStep(
                number = "2",
                title = "Team lead or incident command consolidates",
                detail = "Multiple unit entries can be combined into one command-level incident narrative."
            )
            OperationalGuideStep(
                number = "3",
                title = "Authorized admin reviews and approves",
                detail = "Admin checks whether the incident and submitted report are legitimate, then approves or returns the report for revision."
            )

            Surface(
                color = tonalSurface(RFColors.Warning, lightAlpha = 0.08f, darkAlpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Warning.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = RFColors.Warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Use Create Report to document the unit response. Pending reports remain editable; Submitted reports are locked while admin reviews them; Approved reports are final.",
                        color = RFColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalGuideStep(number: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(RFColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = RFColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RFColors.TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun HubTabSwitcher(
    selected: ReportHubTab,
    incidentCount: Int,
    historyCount: Int,
    resourceCount: Int,
    onSelect: (ReportHubTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = RFColors.Bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        TabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = Color.Transparent,
            contentColor = RFColors.Primary,
            divider = { HorizontalDivider(color = RFColors.Border) }
        ) {
            ReportHubTab.entries.forEach { tab ->
                val count = when (tab) {
                    ReportHubTab.INCIDENTS -> incidentCount
                    ReportHubTab.HISTORY -> historyCount
                    ReportHubTab.RESOURCES -> resourceCount
                }
                Tab(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    selectedContentColor = RFColors.Primary,
                    unselectedContentColor = RFColors.TextSecondary,
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (count > 0) {
                                Surface(
                                    color = if (selected == tab) tonalSurface(RFColors.Primary) else RFColors.MutedBg,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        if (count > 99) "99+" else count.toString(),
                                        color = if (selected == tab) RFColors.Primary else RFColors.TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PostIncidentLoadErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tonalSurface(RFColors.Warning)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            RFColors.Warning.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = RFColors.Warning,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                message,
                color = RFColors.Text,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = RFColors.Warning, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportHistoryHeader(
    historyCount: Int,
    latestMonthLabel: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tonalSurface(RFColors.Success)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = RFColors.Success,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Approved Report History",
                        color = RFColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (historyCount == 0) {
                            "No reports have reached the history window yet"
                        } else {
                            "$historyCount finalized report${if (historyCount == 1) "" else "s"}${latestMonthLabel?.let { " • latest: $it" }.orEmpty()}"
                        },
                        color = RFColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Surface(
                    color = tonalSurface(RFColors.Success),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        historyCount.toString(),
                        color = RFColors.Success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            Surface(
                color = tonalSurface(RFColors.Info, lightAlpha = 0.07f, darkAlpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    RFColors.Info.copy(alpha = 0.24f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = RFColors.Info,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "An approved report stays under Approved for 24 hours, then appears here automatically. History records remain final and read-only and are organized by approval month.",
                        color = RFColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportHistoryMonthSelector(
    months: List<ReportHistoryMonth>,
    selectedKey: String?,
    totalCount: Int,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "history-month-all") {
            FilterChip(
                selected = selectedKey == null,
                onClick = { onSelect(null) },
                label = {
                    Text(
                        "All ($totalCount)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = if (selectedKey == null) {
                    {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else null
            )
        }

        items(months, key = { it.key }) { month ->
            FilterChip(
                selected = selectedKey == month.key,
                onClick = { onSelect(month.key) },
                label = {
                    Text(
                        "${month.label} (${month.count})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = if (selectedKey == month.key) {
                    {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun ReportHistoryMonthHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = RFColors.Primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = RFColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count record${if (count == 1) "" else "s"}",
            color = RFColors.TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun EmptyReportHistoryCard(selectedMonthLabel: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(tonalSurface(RFColors.Primary)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = RFColors.Primary,
                    modifier = Modifier.size(29.dp)
                )
            }
            Text(
                selectedMonthLabel?.let { "No approved reports for $it" }
                    ?: "No report history yet",
                color = RFColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Reports will appear here after they have been approved by admin for at least 24 hours.",
                color = RFColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResourceRequestsPanel(
    requests: List<SavedResourceRequest>,
    latestRequest: SavedResourceRequest?,
    onNewRequest: () -> Unit,
    onViewAll: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (SavedResourceRequest) -> Unit,
    onCancel: (SavedResourceRequest) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(tonalSurface(RFColors.Primary)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equipment & Supplies", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RFColors.Text)
                    Text(
                        if (requests.isEmpty()) "No requests submitted" else "${requests.size} request${if (requests.size == 1) "" else "s"} • latest first",
                        fontSize = 10.sp,
                        color = RFColors.TextSecondary
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh requests", tint = RFColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Surface(
                color = RFColors.MutedBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Use this only for equipment, PPE, consumables, medical supplies, and replenishment. For responders, response teams, or emergency vehicles, use Home > Backup Requests.",
                        color = RFColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onNewRequest,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Request", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onViewAll,
                    enabled = requests.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RFColors.Text)
                ) {
                    Text("View All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (latestRequest == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = RFColors.TextMuted, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Submitted requests will appear here.", color = RFColors.TextSecondary, fontSize = 11.sp)
                }
            } else {
                Text("Latest request", color = RFColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                ResourceRequestHistoryCard(
                    request = latestRequest,
                    onClick = { onSelect(latestRequest) },
                    onCancel = { onCancel(latestRequest) },
                    isLatest = true
                )
            }
        }
    }
}

@Composable
private fun ReportStepIndicator(currentStep: Int) {
    val labels = listOf("Outcome", "Operations", "Safety")
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val step = index + 1
            val active = step <= currentStep
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (active) RFColors.Primary else RFColors.MutedBg)
                        .border(1.dp, if (active) RFColors.Primary else RFColors.Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (step < currentStep) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    } else {
                        Text(
                            step.toString(),
                            color = if (active) Color.White else RFColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = if (step == currentStep) RFColors.Primary else RFColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = if (step == currentStep) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (index < labels.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 7.dp)
                        .height(2.dp)
                        .background(if (step < currentStep) RFColors.Primary else RFColors.Border)
                )
            }
        }
    }
}

@Composable
private fun CompactCountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(4)
            onValueChange(digits)
        },
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RFColors.InputBg,
            unfocusedContainerColor = RFColors.InputBg,
            focusedTextColor = RFColors.Text,
            unfocusedTextColor = RFColors.Text,
            focusedBorderColor = RFColors.Primary,
            unfocusedBorderColor = RFColors.Border,
            cursorColor = RFColors.Primary
        )
    )
}

@Composable
private fun OperationalOutcomeSelector(
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    val outcomes = listOf("Resolved", "Stabilized / Handoff", "Escalated", "False Alarm")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(outcomes) { outcome ->
            FilterChip(
                selected = selected == outcome,
                onClick = { onSelect(outcome) },
                enabled = enabled,
                label = { Text(outcome, fontSize = 11.sp) },
                leadingIcon = if (selected == outcome) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun AfterActionReportDialog(
    incident: CompletedIncident,
    responderId: Int,
    responderName: String,
    initialReport: AfterActionReport?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AfterActionReport) -> Unit,
    onExport: (AfterActionReport) -> Unit
) {
    val context = LocalContext.current
    val isReadOnly = initialReport?.status == OperationalReportStatus.SUBMITTED ||
            initialReport?.status == OperationalReportStatus.APPROVED
    val serverSnapshot = remember(incident, initialReport) {
        AfterActionDraftSnapshot(
            operationalOutcome = initialReport?.operationalOutcome ?: "Resolved",
            incidentSummary = initialReport?.incidentSummary ?: incident.completionNotes.orEmpty(),
            actionsTaken = initialReport?.actionsTaken.orEmpty(),
            personsAssisted = (initialReport?.personsAssisted ?: 0).toString(),
            injuries = (initialReport?.injuries ?: 0).toString(),
            fatalities = (initialReport?.fatalities ?: 0).toString(),
            resourcesUsed = initialReport?.resourcesUsed.orEmpty(),
            agenciesInvolved = initialReport?.agenciesInvolved.orEmpty(),
            handoffDetails = initialReport?.handoffDetails.orEmpty(),
            safetyIssues = initialReport?.safetyIssues.orEmpty(),
            followUpRequired = initialReport?.followUpRequired ?: false,
            followUpDetails = initialReport?.followUpDetails.orEmpty(),
            lessonsLearned = initialReport?.lessonsLearned.orEmpty()
        )
    }
    val restoredDraft = remember(
        responderId,
        incident.id,
        initialReport?.updatedAt,
        isReadOnly
    ) {
        if (isReadOnly) {
            null
        } else {
            readAfterActionDraft(context, responderId, incident.id)
                ?.takeIf { it.savedAt > (initialReport?.updatedAt ?: 0L) }
        }
    }
    val initialSnapshot = restoredDraft?.snapshot ?: serverSnapshot
    val stateKey = "${normalizedIncidentId(incident.id)}_${initialReport?.updatedAt ?: 0L}_${restoredDraft?.savedAt ?: 0L}"
    var step by rememberSaveable(stateKey) { mutableIntStateOf(restoredDraft?.step ?: 1) }
    var operationalOutcome by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.operationalOutcome) }
    var incidentSummary by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.incidentSummary) }
    var actionsTaken by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.actionsTaken) }
    var personsAssisted by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.personsAssisted) }
    var injuries by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.injuries) }
    var fatalities by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.fatalities) }
    var resourcesUsed by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.resourcesUsed) }
    var agenciesInvolved by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.agenciesInvolved) }
    var handoffDetails by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.handoffDetails) }
    var safetyIssues by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.safetyIssues) }
    var followUpRequired by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.followUpRequired) }
    var followUpDetails by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.followUpDetails) }
    var lessonsLearned by rememberSaveable(stateKey) { mutableStateOf(initialSnapshot.lessonsLearned) }
    var certified by rememberSaveable(stateKey) { mutableStateOf(false) }
    var attemptedNext by rememberSaveable(stateKey) { mutableStateOf(false) }
    var showClosePrompt by rememberSaveable(stateKey) { mutableStateOf(false) }
    val formEnabled = !isReadOnly && !isSaving

    val currentSnapshot = AfterActionDraftSnapshot(
        operationalOutcome = operationalOutcome,
        incidentSummary = incidentSummary,
        actionsTaken = actionsTaken,
        personsAssisted = personsAssisted,
        injuries = injuries,
        fatalities = fatalities,
        resourcesUsed = resourcesUsed,
        agenciesInvolved = agenciesInvolved,
        handoffDetails = handoffDetails,
        safetyIssues = safetyIssues,
        followUpRequired = followUpRequired,
        followUpDetails = followUpDetails,
        lessonsLearned = lessonsLearned
    )
    val isDirty = !isReadOnly && currentSnapshot != serverSnapshot

    LaunchedEffect(
        currentSnapshot,
        serverSnapshot,
        step,
        isReadOnly,
        isSaving,
        responderId,
        incident.id
    ) {
        if (isReadOnly || isSaving || responderId <= 0) return@LaunchedEffect
        if (!isDirty) {
            clearAfterActionDraft(context, responderId, incident.id)
            return@LaunchedEffect
        }
        delay(350L)
        saveAfterActionDraft(
            context = context,
            responderId = responderId,
            incidentId = incident.id,
            step = step,
            snapshot = currentSnapshot
        )
    }

    fun requestClose() {
        if (isSaving) return
        if (isDirty) {
            showClosePrompt = true
        } else {
            onDismiss()
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = RFColors.InputBg,
        unfocusedContainerColor = RFColors.InputBg,
        focusedTextColor = RFColors.Text,
        unfocusedTextColor = RFColors.Text,
        focusedBorderColor = RFColors.Primary,
        unfocusedBorderColor = RFColors.Border,
        focusedLabelColor = RFColors.Primary,
        unfocusedLabelColor = RFColors.TextSecondary,
        cursorColor = RFColors.Primary,
        errorBorderColor = RFColors.Danger,
        errorLabelColor = RFColors.Danger,
        errorSupportingTextColor = RFColors.Danger
    )

    fun buildReport(status: OperationalReportStatus): AfterActionReport = AfterActionReport(
        incidentId = normalizedIncidentId(incident.id),
        incidentType = incident.type,
        responderName = responderName.ifBlank { "Responder" },
        operationalOutcome = operationalOutcome,
        incidentSummary = incidentSummary.trim(),
        actionsTaken = actionsTaken.trim(),
        personsAssisted = personsAssisted.toIntOrNull() ?: 0,
        injuries = injuries.toIntOrNull() ?: 0,
        fatalities = fatalities.toIntOrNull() ?: 0,
        resourcesUsed = resourcesUsed.trim(),
        agenciesInvolved = agenciesInvolved.trim(),
        handoffDetails = handoffDetails.trim(),
        safetyIssues = safetyIssues.trim(),
        followUpRequired = followUpRequired,
        followUpDetails = followUpDetails.trim(),
        lessonsLearned = lessonsLearned.trim(),
        reviewerNotes = initialReport?.reviewerNotes.orEmpty(),
        status = status,
        approvedAt = initialReport?.approvedAt ?: 0L,
        isInHistory = initialReport?.isInHistory ?: false,
        sourceCompletedAt = initialReport?.sourceCompletedAt ?: incident.completedAtMillis,
        sourceCompletionNotes = initialReport?.sourceCompletionNotes
            ?.takeIf { it.isNotBlank() }
            ?: incident.completionNotes.orEmpty(),
        sourceCompletionProofUri = initialReport?.sourceCompletionProofUri
            ?: incident.proofUri,
        updatedAt = System.currentTimeMillis()
    )

    val stepValid = when (step) {
        1 -> incidentSummary.isNotBlank()
        2 -> actionsTaken.isNotBlank()
        else -> !followUpRequired || followUpDetails.isNotBlank()
    }
    val readyValid = incidentSummary.isNotBlank() &&
            actionsTaken.isNotBlank() &&
            (!followUpRequired || followUpDetails.isNotBlank()) &&
            certified

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = RFColors.SurfaceBg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
                    )
            ) {
                Surface(color = RFColors.Bg, shadowElevation = 2.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(tonalSurface(RFColors.Primary)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Assignment, contentDescription = null, tint = RFColors.Primary, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Responder After-Action Report", color = RFColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${incident.type} • ${incident.id} • Step $step of 3",
                                    color = RFColors.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            initialReport?.let { report ->
                                Surface(
                                    color = tonalSurface(report.status.color),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        report.status.label,
                                        color = report.status.color,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            IconButton(onClick = ::requestClose) {
                                Icon(Icons.Default.Close, contentDescription = "Close report", tint = RFColors.TextSecondary)
                            }
                        }
                        ReportStepIndicator(currentStep = step)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            color = tonalSurface(RFColors.Info, lightAlpha = 0.08f, darkAlpha = 0.17f),
                            shape = RoundedCornerShape(13.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Info.copy(alpha = 0.24f))
                        ) {
                            Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = RFColors.Info, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "This unit-level operational entry is stored in the server database. Submitted reports remain read-only while an authorized admin validates the incident and either approves or returns the report for revision.",
                                    color = RFColors.TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    if (initialReport?.status == OperationalReportStatus.RETURNED &&
                        initialReport.reviewerNotes.isNotBlank()
                    ) {
                        item {
                            Surface(
                                color = tonalSurface(RFColors.Danger, lightAlpha = 0.07f, darkAlpha = 0.16f),
                                shape = RoundedCornerShape(13.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Danger.copy(alpha = 0.30f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.RateReview,
                                            contentDescription = null,
                                            tint = RFColors.Danger,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            "Returned by reviewer",
                                            color = RFColors.Danger,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        initialReport.reviewerNotes,
                                        color = RFColors.TextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    when (step) {
                        1 -> {
                            item {
                                SectionCard(title = "Incident outcome", icon = Icons.Default.Flag) {
                                    OperationalOutcomeSelector(
                                        selected = operationalOutcome,
                                        enabled = formEnabled,
                                        onSelect = { operationalOutcome = it }
                                    )
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = incidentSummary,
                                        onValueChange = { incidentSummary = it },
                                        label = { Text("Operational summary *") },
                                        placeholder = { Text("What happened and what was the final situation?") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4,
                                        maxLines = 7,
                                        isError = attemptedNext && incidentSummary.isBlank(),
                                        supportingText = if (attemptedNext && incidentSummary.isBlank()) {
                                            { Text("An operational summary is required") }
                                        } else null,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_summary",
                                        onApply = { template ->
                                            incidentSummary = appendQuickTemplate(incidentSummary, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            item {
                                SectionCard(title = "People affected", icon = Icons.Default.Groups) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        CompactCountField("Assisted", personsAssisted, { personsAssisted = it }, Modifier.weight(1f), enabled = formEnabled)
                                        CompactCountField("Injured", injuries, { injuries = it }, Modifier.weight(1f), enabled = formEnabled)
                                        CompactCountField("Fatalities", fatalities, { fatalities = it }, Modifier.weight(1f), enabled = formEnabled)
                                    }
                                    Text(
                                        "Use counts only. Do not enter patient names or personal identifiers in this form.",
                                        color = RFColors.TextMuted,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        2 -> {
                            item {
                                SectionCard(title = "Actions taken", icon = Icons.Default.Construction) {
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = actionsTaken,
                                        onValueChange = { actionsTaken = it },
                                        label = { Text("Response actions *") },
                                        placeholder = { Text("Assessment, treatment, suppression, rescue, evacuation, scene control, etc.") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 5,
                                        maxLines = 8,
                                        isError = attemptedNext && actionsTaken.isBlank(),
                                        supportingText = if (attemptedNext && actionsTaken.isBlank()) {
                                            { Text("Actions taken are required") }
                                        } else null,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_actions",
                                        onApply = { template ->
                                            actionsTaken = appendQuickTemplate(actionsTaken, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            item {
                                SectionCard(title = "Operational coordination", icon = Icons.Default.Group) {
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = resourcesUsed,
                                        onValueChange = { resourcesUsed = it },
                                        label = { Text("Resources used") },
                                        placeholder = { Text("Vehicles, equipment, supplies, personnel") },
                                        supportingText = {
                                            Text("Documentation only. Request backup from Home and equipment or supplies from Reports > Equipment.")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_resources_used",
                                        onApply = { template ->
                                            resourcesUsed = appendQuickTemplate(resourcesUsed, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = agenciesInvolved,
                                        onValueChange = { agenciesInvolved = it },
                                        label = { Text("Agencies / units involved") },
                                        placeholder = { Text("Fire, EMS, police, barangay, hospital, utility, etc.") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_agencies",
                                        onApply = { template ->
                                            agenciesInvolved = appendQuickTemplate(agenciesInvolved, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = handoffDetails,
                                        onValueChange = { handoffDetails = it },
                                        label = { Text("Handoff / receiving facility") },
                                        placeholder = { Text("Receiving unit or facility and handoff condition; no patient names") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_handoff",
                                        onApply = { template ->
                                            handoffDetails = appendQuickTemplate(handoffDetails, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        else -> {
                            item {
                                SectionCard(title = "Safety and near misses", icon = Icons.Default.Security) {
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = safetyIssues,
                                        onValueChange = { safetyIssues = it },
                                        label = { Text("Hazards, injuries, equipment issues, or near misses") },
                                        placeholder = { Text("Write 'None' when there were no safety concerns") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_safety",
                                        onApply = { template ->
                                            safetyIssues = appendQuickTemplate(safetyIssues, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            item {
                                SectionCard(title = "Follow-up", icon = Icons.Default.Refresh) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Follow-up action required", color = RFColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Examples: replenishment, investigation, welfare check, maintenance, debrief", color = RFColors.TextSecondary, fontSize = 10.sp)
                                        }
                                        Switch(
                                            checked = followUpRequired,
                                            onCheckedChange = { followUpRequired = it },
                                            enabled = formEnabled
                                        )
                                    }
                                    if (followUpRequired) {
                                        OutlinedTextField(
                                            enabled = formEnabled,
                                            value = followUpDetails,
                                            onValueChange = { followUpDetails = it },
                                            label = { Text("Follow-up details *") },
                                            placeholder = { Text("Who needs to do what and by when?") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            maxLines = 5,
                                            isError = attemptedNext && followUpDetails.isBlank(),
                                            supportingText = if (attemptedNext && followUpDetails.isBlank()) {
                                                { Text("Follow-up details are required") }
                                            } else null,
                                            colors = fieldColors,
                                            shape = RoundedCornerShape(13.dp)
                                        )
                                        QuickTemplateBar(
                                            fieldKey = "aar_followup",
                                            onApply = { template ->
                                                followUpDetails = appendQuickTemplate(followUpDetails, template)
                                            },
                                            enabled = formEnabled,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    OutlinedTextField(
                                        enabled = formEnabled,
                                        value = lessonsLearned,
                                        onValueChange = { lessonsLearned = it },
                                        label = { Text("Lessons learned / recommendations") },
                                        placeholder = { Text("What should be maintained, improved, or changed next time?") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    QuickTemplateBar(
                                        fieldKey = "aar_lessons",
                                        onApply = { template ->
                                            lessonsLearned = appendQuickTemplate(lessonsLearned, template)
                                        },
                                        enabled = formEnabled,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            item {
                                Surface(
                                    color = RFColors.Bg,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (!certified && attemptedNext) RFColors.Danger else RFColors.Border
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = formEnabled) { certified = !certified }
                                            .padding(11.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Checkbox(
                                            checked = certified,
                                            onCheckedChange = { certified = it },
                                            enabled = formEnabled
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Column {
                                            Text("Certification", color = RFColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "I confirm that this unit-level report is accurate to the best of my knowledge and does not include unnecessary personal identifiers.",
                                                color = RFColors.TextSecondary,
                                                fontSize = 10.sp,
                                                lineHeight = 15.sp
                                            )
                                            if (!certified && attemptedNext) {
                                                Text("Certification is required before submitting the report.", color = RFColors.Danger, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(color = RFColors.Bg, shadowElevation = 5.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (step < 3) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                if (step > 1) {
                                    OutlinedButton(
                                        onClick = { attemptedNext = false; step-- },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = 180f))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Back")
                                    }
                                }
                                Button(
                                    onClick = {
                                        attemptedNext = true
                                        if (stepValid) {
                                            attemptedNext = false
                                            step++
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                                ) {
                                    Text("Next", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(5.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            TextButton(
                                onClick = { onSave(buildReport(OperationalReportStatus.PENDING)) },
                                enabled = formEnabled,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Save as Pending and close", color = RFColors.TextSecondary, fontSize = 11.sp)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onSave(buildReport(OperationalReportStatus.PENDING)) },
                                    enabled = formEnabled,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Save Pending", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        attemptedNext = true
                                        if (readyValid) onSave(buildReport(OperationalReportStatus.SUBMITTED))
                                    },
                                    enabled = formEnabled,
                                    modifier = Modifier.weight(1.25f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RFColors.Primary)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Submit Report", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { attemptedNext = false; step = 2 }) {
                                    Text("Back", color = RFColors.TextSecondary, fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        onExport(buildReport(initialReport?.status ?: OperationalReportStatus.PENDING))
                                    },
                                    enabled = !isSaving && (incidentSummary.isNotBlank() || actionsTaken.isNotBlank())
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Export Preview", color = RFColors.Primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClosePrompt) {
            AlertDialog(
                onDismissRequest = { showClosePrompt = false },
                icon = {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = RFColors.Warning
                    )
                },
                title = { Text("Close this report?") },
                text = {
                    Text(
                        "Your unsaved changes are stored as a local draft on this device."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            saveAfterActionDraft(
                                context = context,
                                responderId = responderId,
                                incidentId = incident.id,
                                step = step,
                                snapshot = currentSnapshot
                            )
                            showClosePrompt = false
                            onDismiss()
                        }
                    ) {
                        Text("Keep Draft & Close", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(
                            onClick = {
                                clearAfterActionDraft(context, responderId, incident.id)
                                showClosePrompt = false
                                onDismiss()
                            }
                        ) {
                            Text("Discard Draft", color = RFColors.Danger)
                        }
                        TextButton(onClick = { showClosePrompt = false }) {
                            Text("Continue Editing")
                        }
                    }
                },
                containerColor = RFColors.Bg,
                titleContentColor = RFColors.Text,
                textContentColor = RFColors.TextSecondary
            )
        }
    }
}

// ─── Main screen ──────────────────────────────────────────────────────────────
@Composable
fun ReviewsFeedbackScreen() {
    val context = LocalContext.current
    val accountPrefs = context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
    val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val operationalRepository = remember { OperationalRepository() }
    val incidentRepository = remember { com.ers.emergencyresponseapp.data.IncidentRepository() }

    val responderName = accountPrefs.getString("account_username", "Responder") ?: "Responder"
    val responderId = userPrefs.getString("user_id", "")?.toIntOrNull() ?: 0
    val scope = rememberCoroutineScope()

    var hubTab by rememberSaveable { mutableStateOf(ReportHubTab.INCIDENTS) }
    var selectedReportFilter by rememberSaveable {
        mutableStateOf(ReportWorkflowFilter.PENDING)
    }
    var requestSearch by rememberSaveable { mutableStateOf("") }

    var requestRefreshKey by remember { mutableIntStateOf(0) }
    var reportRefreshKey by remember { mutableIntStateOf(0) }

    var serverCompletedIncidents by remember {
        mutableStateOf<List<com.ers.emergencyresponseapp.network.CompletedIncidentDto>>(emptyList())
    }
    var parsedRequests by remember { mutableStateOf<List<SavedResourceRequest>>(emptyList()) }
    var afterActionReports by remember { mutableStateOf<List<AfterActionReport>>(emptyList()) }
    var completedIncidentsLoadError by remember { mutableStateOf<String?>(null) }
    var resourceRequestsLoadError by remember { mutableStateOf<String?>(null) }
    var afterActionReportsLoadError by remember { mutableStateOf<String?>(null) }
    var hasLoadedCompletedIncidents by remember { mutableStateOf(false) }
    var hasLoadedResourceRequests by remember { mutableStateOf(false) }
    var hasLoadedAfterActionReports by remember { mutableStateOf(false) }
    var isSavingAfterActionReport by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    var showAllIncidents by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }
    var showAllRequests by remember { mutableStateOf(false) }
    var selectedHistoryMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showResourceForm by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var requestToCancel by remember { mutableStateOf<SavedResourceRequest?>(null) }
    var reportTarget by remember { mutableStateOf<CompletedIncident?>(null) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCompletedIncidents() {
        if (responderId <= 0) {
            completedIncidentsLoadError = "Responder session is unavailable. Sign in again."
            return
        }
        try {
            val loadedIncidents = incidentRepository.getCompletedIncidents(responderId)
            serverCompletedIncidents = loadedIncidents
            hasLoadedCompletedIncidents = true
            completedIncidentsLoadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("PostIncidentHub", "Completed incident load failed", error)
            completedIncidentsLoadError = if (hasLoadedCompletedIncidents) {
                "Unable to refresh completed incidents. Previous data is still shown."
            } else {
                "Unable to load completed incidents. Tap Retry."
            }
        }
    }

    suspend fun refreshResourceRequests() {
        if (responderId <= 0) {
            resourceRequestsLoadError = "Responder session is unavailable. Sign in again."
            return
        }
        try {
            val loadedRequests = incidentRepository.getMyResourceRequests(responderId)
                .map { dto ->
                    val createdAt = parseServerTimestamp(dto.created_at)
                    val updatedAt = parseServerTimestamp(dto.updated_at).takeIf { it > 0L }
                        ?: createdAt
                    SavedResourceRequest(
                        id = dto.id.toString(),
                        resourceName = dto.resource_name,
                        category = dto.category,
                        quantity = dto.quantity.toString(),
                        urgency = dto.urgency,
                        status = dto.status.replaceFirstChar { it.uppercase() },
                        incidentId = dto.incident_id.orEmpty().trim(),
                        location = dto.location.trim(),
                        notes = dto.notes.orEmpty().trim(),
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                }
                .sortedWith(
                    compareByDescending<SavedResourceRequest> { it.createdAt }
                        .thenByDescending { it.id.toLongOrNull() ?: 0L }
                )
            parsedRequests = loadedRequests
            hasLoadedResourceRequests = true
            resourceRequestsLoadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("PostIncidentHub", "Equipment request load failed", error)
            resourceRequestsLoadError = if (hasLoadedResourceRequests) {
                "Unable to refresh equipment requests. Previous data is still shown."
            } else {
                "Unable to load equipment requests. Tap Retry."
            }
        }
    }

    suspend fun refreshAfterActionReports() {
        if (responderId <= 0) {
            afterActionReportsLoadError = "Responder session is unavailable. Sign in again."
            return
        }
        try {
            val records = operationalRepository.getAfterActionReports(responderId).getOrThrow()
            afterActionReports = records
                .map { it.toUiReport() }
                .sortedByDescending { it.updatedAt }
            hasLoadedAfterActionReports = true
            afterActionReportsLoadError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("AfterActionReport", "Load failed", error)
            afterActionReportsLoadError = if (hasLoadedAfterActionReports) {
                "Unable to refresh after-action reports. Previous data is still shown."
            } else {
                "Unable to load after-action reports. Tap Retry."
            }
        }
    }

    LaunchedEffect(responderId) { refreshCompletedIncidents() }
    LaunchedEffect(requestRefreshKey, responderId) { refreshResourceRequests() }
    LaunchedEffect(reportRefreshKey, responderId) { refreshAfterActionReports() }

    LaunchedEffect(responderId) {
        if (responderId <= 0) return@LaunchedEffect
        while (true) {
            delay(5000L)
            refreshCompletedIncidents()
            refreshResourceRequests()
            refreshAfterActionReports()
        }
    }

    val allIncidents = remember(serverCompletedIncidents) {
        serverCompletedIncidents.map { dto ->
            val completedAtMillis = parseServerTimestamp(dto.completed_at)
            val dateLabel = if (completedAtMillis > 0L) {
                SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    .format(Date(completedAtMillis))
            } else {
                dto.completed_at?.takeIf { it.isNotBlank() } ?: "Date unavailable"
            }

            CompletedIncident(
                id = "#${dto.id}",
                type = dto.type.trim()
                    .takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "General Incident",
                date = dateLabel,
                proofUri = dto.completion_image_path,
                completionNotes = dto.completion_notes,
                completedAtMillis = completedAtMillis
            )
        }.sortedWith(
            compareByDescending<CompletedIncident> { it.completedAtMillis }
                .thenByDescending { normalizedIncidentId(it.id).toLongOrNull() ?: 0L }
        )
    }

    val visibleResourceRequests = remember(parsedRequests) {
        parsedRequests.filterNot { it.status.equals("Cancelled", ignoreCase = true) }
    }
    val latestResourceRequest = visibleResourceRequests.firstOrNull()
    val filteredRequests = remember(visibleResourceRequests, requestSearch) {
        visibleResourceRequests.filter {
            it.resourceName.contains(requestSearch, ignoreCase = true) ||
                    it.id.contains(requestSearch, ignoreCase = true) ||
                    it.category.contains(requestSearch, ignoreCase = true)
        }
    }

    val reportByIncident = remember(afterActionReports) {
        afterActionReports.associateBy { normalizedIncidentId(it.incidentId) }
    }

    val archivedReports = remember(afterActionReports) {
        afterActionReports
            .filter {
                it.status == OperationalReportStatus.APPROVED && it.isInHistory
            }
            .sortedByDescending { it.historySortTime() }
    }
    val completedIncidentById = remember(allIncidents) {
        allIncidents.associateBy { normalizedIncidentId(it.id) }
    }
    val historyEntries = remember(archivedReports, completedIncidentById) {
        archivedReports.map { report ->
            ArchivedReportEntry(
                incident = completedIncidentById[normalizedIncidentId(report.incidentId)]
                    ?: report.toHistoryIncidentFallback(),
                report = report
            )
        }
    }
    val historyMonths = remember(historyEntries) {
        historyEntries
            .groupBy { reportHistoryMonthKey(it.report.historySortTime()) }
            .map { (key, entries) ->
                ReportHistoryMonth(
                    key = key,
                    label = reportHistoryMonthLabel(entries.first().report.historySortTime()),
                    count = entries.size
                )
            }
            .sortedByDescending { it.key }
    }
    LaunchedEffect(historyMonths, selectedHistoryMonthKey) {
        if (
            selectedHistoryMonthKey != null &&
            historyMonths.none { it.key == selectedHistoryMonthKey }
        ) {
            selectedHistoryMonthKey = null
        }
    }
    val selectedHistoryEntries = remember(
        historyEntries,
        selectedHistoryMonthKey
    ) {
        selectedHistoryMonthKey?.let { selectedMonth ->
            historyEntries.filter {
                reportHistoryMonthKey(it.report.historySortTime()) == selectedMonth
            }
        } ?: historyEntries
    }
    val historyPreviewEntries = selectedHistoryEntries.take(5)
    val hasMoreHistory = selectedHistoryEntries.size > historyPreviewEntries.size
    val selectedHistoryMonthLabel = historyMonths
        .firstOrNull { it.key == selectedHistoryMonthKey }
        ?.label

    val currentIncidents = remember(allIncidents, reportByIncident) {
        allIncidents.filterNot { incident ->
            reportByIncident[normalizedIncidentId(incident.id)]?.isInHistory == true
        }
    }
    val notStartedReportCount = currentIncidents.count {
        reportByIncident[normalizedIncidentId(it.id)] == null
    }
    val savedPendingReportCount = afterActionReports.count {
        it.status == OperationalReportStatus.PENDING ||
                it.status == OperationalReportStatus.RETURNED
    }
    val pendingReportCount = notStartedReportCount + savedPendingReportCount
    val submittedReportCount = afterActionReports.count {
        it.status == OperationalReportStatus.SUBMITTED
    }
    val approvedReportCount = afterActionReports.count {
        it.status == OperationalReportStatus.APPROVED && !it.isInHistory
    }

    val filteredIncidents = remember(
        currentIncidents,
        reportByIncident,
        selectedReportFilter
    ) {
        currentIncidents.filter { incident ->
            when (selectedReportFilter) {
                ReportWorkflowFilter.PENDING -> {
                    when (reportByIncident[normalizedIncidentId(incident.id)]?.status) {
                        null,
                        OperationalReportStatus.PENDING,
                        OperationalReportStatus.RETURNED -> true
                        OperationalReportStatus.SUBMITTED,
                        OperationalReportStatus.APPROVED -> false
                    }
                }

                ReportWorkflowFilter.SUBMITTED -> {
                    reportByIncident[normalizedIncidentId(incident.id)]?.status ==
                            OperationalReportStatus.SUBMITTED
                }

                ReportWorkflowFilter.APPROVED -> {
                    val report = reportByIncident[normalizedIncidentId(incident.id)]
                    report?.status == OperationalReportStatus.APPROVED &&
                            !report.isInHistory
                }
            }
        }
    }
    val incidentsShown = filteredIncidents.take(5)
    val hasMoreIncidents = filteredIncidents.size > incidentsShown.size

    fun cancelResourceRequest(requestId: String) {
        val id = requestId.toIntOrNull() ?: return
        scope.launch {
            val result = incidentRepository.cancelResourceRequest(id, responderId)
            result.onSuccess {
                requestRefreshKey++
                Toast.makeText(
                    context,
                    "Equipment/supply request cancelled",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Unable to cancel: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun refreshPostIncidentHub() {
        if (isPullRefreshing || responderId <= 0) return
        scope.launch {
            isPullRefreshing = true
            try {
                refreshCompletedIncidents()
                refreshResourceRequests()
                refreshAfterActionReports()
            } finally {
                isPullRefreshing = false
            }
        }
    }

    val incidentReportFirstLoadFailed =
        (!hasLoadedCompletedIncidents && completedIncidentsLoadError != null) ||
                (!hasLoadedAfterActionReports && afterActionReportsLoadError != null)
    val selectedTabLoadError = when (hubTab) {
        ReportHubTab.INCIDENTS -> when {
            completedIncidentsLoadError != null && afterActionReportsLoadError != null ->
                if (incidentReportFirstLoadFailed) {
                    "Unable to load incident report data. Tap Retry."
                } else {
                    "Unable to refresh incident report data. Previous data is still shown."
                }
            completedIncidentsLoadError != null -> completedIncidentsLoadError
            else -> afterActionReportsLoadError
        }
        ReportHubTab.HISTORY -> afterActionReportsLoadError
        ReportHubTab.RESOURCES -> resourceRequestsLoadError
    }
    val selectedTabFirstLoadFailed = when (hubTab) {
        ReportHubTab.INCIDENTS -> incidentReportFirstLoadFailed
        ReportHubTab.HISTORY ->
            !hasLoadedAfterActionReports && afterActionReportsLoadError != null
        ReportHubTab.RESOURCES ->
            !hasLoadedResourceRequests && resourceRequestsLoadError != null
    }
    val selectedTabHasSuccessfulLoad = when (hubTab) {
        ReportHubTab.INCIDENTS ->
            hasLoadedCompletedIncidents && hasLoadedAfterActionReports
        ReportHubTab.HISTORY -> hasLoadedAfterActionReports
        ReportHubTab.RESOURCES -> hasLoadedResourceRequests
    }

    Scaffold(containerColor = RFColors.SurfaceBg) { paddingValues ->
        AppPullToRefresh(
            isRefreshing = isPullRefreshing,
            onRefresh = ::refreshPostIncidentHub,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { PostIncidentHeader() }

                if (hasLoadedCompletedIncidents && hasLoadedAfterActionReports) {
                    item {
                        OperationalWorkflowCard(
                            pendingCount = pendingReportCount,
                            submittedCount = submittedReportCount,
                            approvedCount = approvedReportCount
                        )
                    }
                }

                item {
                    HubTabSwitcher(
                        selected = hubTab,
                        incidentCount = currentIncidents.size,
                        historyCount = historyEntries.size,
                        resourceCount = visibleResourceRequests.size,
                        onSelect = { selectedTab ->
                            hubTab = selectedTab
                            showAllIncidents = false
                            showAllHistory = false
                        }
                    )
                }

                selectedTabLoadError?.let { message ->
                    item {
                        PostIncidentLoadErrorCard(
                            message = message,
                            onRetry = ::refreshPostIncidentHub
                        )
                    }
                }

                if (!selectedTabHasSuccessfulLoad && selectedTabLoadError == null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = RFColors.Primary
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Loading ${hubTab.label.lowercase(Locale.US)}...",
                                color = RFColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (selectedTabHasSuccessfulLoad && !selectedTabFirstLoadFailed) when (hubTab) {
                    ReportHubTab.INCIDENTS -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Report review status",
                                    color = RFColors.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportStatCard(
                                        value = pendingReportCount,
                                        label = "Pending",
                                        accent = RFColors.Warning,
                                        modifier = Modifier.weight(1f),
                                        selected = selectedReportFilter == ReportWorkflowFilter.PENDING,
                                        onClick = {
                                            selectedReportFilter = ReportWorkflowFilter.PENDING
                                            showAllIncidents = false
                                        }
                                    )
                                    ReportStatCard(
                                        value = submittedReportCount,
                                        label = "Submitted",
                                        accent = RFColors.Info,
                                        modifier = Modifier.weight(1f),
                                        selected = selectedReportFilter == ReportWorkflowFilter.SUBMITTED,
                                        onClick = {
                                            selectedReportFilter = ReportWorkflowFilter.SUBMITTED
                                            showAllIncidents = false
                                        }
                                    )
                                    ReportStatCard(
                                        value = approvedReportCount,
                                        label = "Approved",
                                        accent = RFColors.Success,
                                        modifier = Modifier.weight(1f),
                                        selected = selectedReportFilter == ReportWorkflowFilter.APPROVED,
                                        onClick = {
                                            selectedReportFilter = ReportWorkflowFilter.APPROVED
                                            showAllIncidents = false
                                        }
                                    )
                                }
                                Text(
                                    when (selectedReportFilter) {
                                        ReportWorkflowFilter.PENDING ->
                                            "Pending includes reports not started, saved for later, or returned for revision."
                                        ReportWorkflowFilter.SUBMITTED ->
                                            "Submitted reports are locked while an authorized admin validates the incident and report."
                                        ReportWorkflowFilter.APPROVED ->
                                            "Approved reports stay here for 24 hours, then are filed automatically under History."
                                    },
                                    color = RFColors.TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "${selectedReportFilter.label} reports",
                                        color = RFColors.Text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        when (selectedReportFilter) {
                                            ReportWorkflowFilter.PENDING -> "Create, continue, or revise an incident report"
                                            ReportWorkflowFilter.SUBMITTED -> "Awaiting authorized admin review"
                                            ReportWorkflowFilter.APPROVED -> "Approved within the last 24 hours"
                                        },
                                        color = RFColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    "Latest first",
                                    color = RFColors.Primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (currentIncidents.isEmpty() || filteredIncidents.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = RFColors.Bg),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        RFColors.Border
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 34.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(19.dp))
                                                .background(tonalSurface(RFColors.Primary)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Inbox,
                                                contentDescription = null,
                                                tint = RFColors.Primary,
                                                modifier = Modifier.size(29.dp)
                                            )
                                        }
                                        Text(
                                            if (currentIncidents.isEmpty()) {
                                                "No active completed incidents"
                                            } else {
                                                "No ${selectedReportFilter.label.lowercase(Locale.US)} reports"
                                            },
                                            color = RFColors.Text,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            if (currentIncidents.isEmpty()) {
                                                if (historyEntries.isEmpty()) {
                                                    "An incident will appear here after it is completed by this responder."
                                                } else {
                                                    "All finalized reports are already filed under History."
                                                }
                                            } else {
                                                when (selectedReportFilter) {
                                                    ReportWorkflowFilter.PENDING -> "All available incident reports have already been submitted or approved."
                                                    ReportWorkflowFilter.SUBMITTED -> "No report is currently waiting for admin review."
                                                    ReportWorkflowFilter.APPROVED -> "No report was approved in the last 24 hours. Older approvals are available under History."
                                                }
                                            },
                                            color = RFColors.TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = incidentsShown,
                                key = { _, incident -> incident.id }
                            ) { index, incident ->
                                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    IncidentReportCard(
                                        incident = incident,
                                        index = index,
                                        report = reportByIncident[normalizedIncidentId(incident.id)],
                                        onOpenReport = { reportTarget = it },
                                        onViewImage = { fullScreenImageUri = it }
                                    )
                                }
                            }
                            if (hasMoreIncidents) {
                                item {
                                    OutlinedButton(
                                        onClick = { showAllIncidents = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            RFColors.Border
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = RFColors.Primary
                                        )
                                    ) {
                                        Text(
                                            "View all ${filteredIncidents.size} ${selectedReportFilter.label.lowercase(Locale.US)} reports",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ReportHubTab.HISTORY -> {
                        item {
                            ReportHistoryHeader(
                                historyCount = historyEntries.size,
                                latestMonthLabel = historyMonths.firstOrNull()?.label
                            )
                        }

                        if (historyEntries.isNotEmpty()) {
                            item {
                                ReportHistoryMonthSelector(
                                    months = historyMonths,
                                    selectedKey = selectedHistoryMonthKey,
                                    totalCount = historyEntries.size,
                                    onSelect = { selectedHistoryMonthKey = it }
                                )
                            }
                        }

                        if (selectedHistoryEntries.isEmpty()) {
                            item {
                                EmptyReportHistoryCard(
                                    selectedMonthLabel = selectedHistoryMonthLabel
                                )
                            }
                        } else {
                            val previewGroups = historyPreviewEntries.groupBy {
                                reportHistoryMonthKey(it.report.historySortTime())
                            }
                            previewGroups.forEach { (monthKey, entries) ->
                                item(key = "history-preview-month-$monthKey") {
                                    ReportHistoryMonthHeader(
                                        label = historyMonths
                                            .firstOrNull { it.key == monthKey }
                                            ?.label
                                            ?: reportHistoryMonthLabel(
                                                entries.first().report.historySortTime()
                                            ),
                                        count = historyMonths
                                            .firstOrNull { it.key == monthKey }
                                            ?.count
                                            ?: entries.size,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = entries,
                                    key = { _, entry -> "history-preview-${entry.report.incidentId}" }
                                ) { index, entry ->
                                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                        IncidentReportCard(
                                            incident = entry.incident,
                                            index = index,
                                            report = entry.report,
                                            onOpenReport = { reportTarget = it },
                                            onViewImage = { fullScreenImageUri = it }
                                        )
                                    }
                                }
                            }

                            if (hasMoreHistory) {
                                item {
                                    OutlinedButton(
                                        onClick = { showAllHistory = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            RFColors.Border
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = RFColors.Primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "View all ${selectedHistoryEntries.size} history records",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ReportHubTab.RESOURCES -> {
                        item {
                            ResourceRequestsPanel(
                                requests = visibleResourceRequests,
                                latestRequest = latestResourceRequest,
                                onNewRequest = { showResourceForm = true },
                                onViewAll = { showAllRequests = true },
                                onRefresh = ::refreshPostIncidentHub,
                                onSelect = { selectedRequest = it },
                                onCancel = { requestToCancel = it }
                            )
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { incident ->
        val initial = reportByIncident[normalizedIncidentId(incident.id)]
        AfterActionReportDialog(
            incident = incident,
            responderId = responderId,
            responderName = responderName,
            initialReport = initial,
            isSaving = isSavingAfterActionReport,
            onDismiss = { if (!isSavingAfterActionReport) reportTarget = null },
            onSave = { report ->
                if (!isSavingAfterActionReport) {
                    isSavingAfterActionReport = true
                    scope.launch {
                        try {
                            operationalRepository.upsertAfterActionReport(
                                report.toApiRequest(responderId)
                            ).onSuccess {
                                clearAfterActionDraft(context, responderId, report.incidentId)
                                refreshAfterActionReports()
                                reportRefreshKey++
                                reportTarget = null
                                Toast.makeText(
                                    context,
                                    if (report.status == OperationalReportStatus.PENDING) {
                                        "Report saved as Pending"
                                    } else {
                                        "Report submitted for admin review"
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    "Report was not saved: ${error.message ?: "server error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            isSavingAfterActionReport = false
                        }
                    }
                }
            },
            onExport = { report ->
                runCatching { exportIncidentReportPdf(context, report.toPrintableText()) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            "Unable to export report",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        )
    }

    if (showAllIncidents) {
        Dialog(
            onDismissRequest = { showAllIncidents = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = RFColors.SurfaceBg) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Surface(color = RFColors.Bg, shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${selectedReportFilter.label} Reports",
                                    color = RFColors.Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${filteredIncidents.size} records • latest first",
                                    color = RFColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { showAllIncidents = false }) {
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        itemsIndexed(
                            items = filteredIncidents,
                            key = { _, incident -> incident.id }
                        ) { index, incident ->
                            IncidentReportCard(
                                incident = incident,
                                index = index,
                                report = reportByIncident[normalizedIncidentId(incident.id)],
                                onOpenReport = {
                                    showAllIncidents = false
                                    reportTarget = it
                                },
                                onViewImage = { fullScreenImageUri = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllHistory) {
        Dialog(
            onDismissRequest = { showAllHistory = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = RFColors.SurfaceBg) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Surface(color = RFColors.Bg, shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tonalSurface(RFColors.Success)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = RFColors.Success,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Approved Report History",
                                    color = RFColors.Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${selectedHistoryEntries.size} records${selectedHistoryMonthLabel?.let { " • $it" }.orEmpty()} • latest approval first",
                                    color = RFColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { showAllHistory = false }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = RFColors.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ReportHistoryMonthSelector(
                        months = historyMonths,
                        selectedKey = selectedHistoryMonthKey,
                        totalCount = historyEntries.size,
                        onSelect = { selectedHistoryMonthKey = it }
                    )
                    Spacer(Modifier.height(6.dp))

                    if (selectedHistoryEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            EmptyReportHistoryCard(
                                selectedMonthLabel = selectedHistoryMonthLabel
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            val historyGroups = selectedHistoryEntries.groupBy {
                                reportHistoryMonthKey(it.report.historySortTime())
                            }
                            historyGroups.forEach { (monthKey, entries) ->
                                item(key = "history-dialog-month-$monthKey") {
                                    ReportHistoryMonthHeader(
                                        label = historyMonths
                                            .firstOrNull { it.key == monthKey }
                                            ?.label
                                            ?: reportHistoryMonthLabel(
                                                entries.first().report.historySortTime()
                                            ),
                                        count = entries.size
                                    )
                                }
                                itemsIndexed(
                                    items = entries,
                                    key = { _, entry -> "history-dialog-${entry.report.incidentId}" }
                                ) { index, entry ->
                                    IncidentReportCard(
                                        incident = entry.incident,
                                        index = index,
                                        report = entry.report,
                                        onOpenReport = {
                                            showAllHistory = false
                                            reportTarget = it
                                        },
                                        onViewImage = { fullScreenImageUri = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fullScreenImageUri?.let { uri ->
        FullScreenImageDialog(uriStr = uri, onDismiss = { fullScreenImageUri = null })
    }

    if (showAllRequests) {
        Dialog(
            onDismissRequest = { showAllRequests = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = RFColors.SurfaceBg) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Surface(color = RFColors.Bg, shadowElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Equipment & Supply Requests",
                                    color = RFColors.Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${visibleResourceRequests.size} requests • latest first",
                                    color = RFColors.TextSecondary,
                                    fontSize = 11.sp
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
                    }

                    OutlinedTextField(
                        value = requestSearch,
                        onValueChange = { requestSearch = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = if (requestSearch.isNotBlank()) {
                            {
                                IconButton(onClick = { requestSearch = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        } else {
                            null
                        },
                        placeholder = { Text("Search item, category, or request ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RFColors.InputBg,
                            unfocusedContainerColor = RFColors.InputBg,
                            focusedTextColor = RFColors.Text,
                            unfocusedTextColor = RFColors.Text,
                            focusedBorderColor = RFColors.Primary,
                            unfocusedBorderColor = RFColors.Border,
                            cursorColor = RFColors.Primary
                        )
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        if (filteredRequests.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = RFColors.Bg
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        RFColors.Border
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = RFColors.TextMuted
                                        )
                                        Spacer(Modifier.height(7.dp))
                                        Text(
                                            "No matching requests",
                                            color = RFColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredRequests, key = { it.id }) { request ->
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
    }

    requestToCancel?.let { request ->
        AlertDialog(
            onDismissRequest = { requestToCancel = null },
            containerColor = RFColors.Bg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Cancel Equipment Request?",
                    color = RFColors.Text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Cancel ${request.resourceName}? Dispatch will see this request as cancelled.",
                    color = RFColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        cancelResourceRequest(request.id)
                        requestToCancel = null
                        selectedRequest = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RFColors.Danger)
                ) {
                    Text("Cancel Request")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { requestToCancel = null },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RFColors.Border)
                ) {
                    Text("Keep Request", color = RFColors.TextSecondary)
                }
            }
        )
    }

    selectedRequest?.let { request ->
        ResourceRequestDetailsSheet(
            request = request,
            onDismiss = { selectedRequest = null },
            onCancel = { requestToCancel = request }
        )
    }

    if (showResourceForm) {
        ResourceRequestFullScreenDialog(
            responderId = responderId,
            responderName = responderName,
            onDismiss = { showResourceForm = false },
            onSubmit = {
                showResourceForm = false
                requestRefreshKey++
            }
        )
    }
}


private fun wrapPdfLine(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return listOf("")

    val result = mutableListOf<String>()
    var remaining = text.trimEnd()

    while (remaining.isNotEmpty()) {
        val measured = paint.breakText(remaining, true, maxWidth, null)
            .coerceAtLeast(1)
            .coerceAtMost(remaining.length)

        var endIndex = measured
        if (endIndex < remaining.length) {
            val lastSpace = remaining.lastIndexOf(' ', startIndex = endIndex - 1)
            if (lastSpace > 0) endIndex = lastSpace
        }

        val line = remaining.substring(0, endIndex).trimEnd()
        result += line
        remaining = remaining.substring(endIndex).trimStart()
    }

    return result
}

private fun exportIncidentReportPdf(
    context: Context,
    reportText: String
) {
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 42f
    val lineHeight = 18f
    val maxTextWidth = pageWidth - (margin * 2)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f
        color = android.graphics.Color.BLACK
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.DKGRAY
    }

    val wrappedLines = reportText.lines().flatMap { line ->
        wrapPdfLine(line, bodyPaint, maxTextWidth)
    }
    val usableHeight = pageHeight - (margin * 2) - 18f
    val linesPerPage = (usableHeight / lineHeight).toInt().coerceAtLeast(1)
    val pages = wrappedLines.chunked(linesPerPage).ifEmpty { listOf(listOf("")) }

    pages.forEachIndexed { pageIndex, lines ->
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        var y = margin

        lines.forEach { line ->
            canvas.drawText(line, margin, y, bodyPaint)
            y += lineHeight
        }

        val footer = "Page ${pageIndex + 1} of ${pages.size}"
        canvas.drawText(
            footer,
            pageWidth - margin - footerPaint.measureText(footer),
            pageHeight - 24f,
            footerPaint
        )
        pdfDocument.finishPage(page)
    }

    val reportDirectory = File(context.cacheDir, "shared_reports").apply { mkdirs() }
    val file = File(reportDirectory, "post_incident_report_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { output -> pdfDocument.writeTo(output) }
    pdfDocument.close()

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        android.content.Intent.createChooser(intent, "Share Post-Incident Document")
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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            color = RFColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = RFColors.Text,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f)
        )
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
                    Text(
                        formatRequestTimestamp(request.createdAt),
                        color = RFColors.TextMuted,
                        fontSize = 10.sp
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
                "Equipment / Supply Request Details",
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
                    InfoRow("Item", request.resourceName)
                    InfoRow("Category", request.category)
                    InfoRow("Quantity", request.quantity)
                    InfoRow("Urgency", request.urgency)
                    InfoRow("Status", request.status)
                    InfoRow(
                        "Incident",
                        request.incidentId.takeIf { it.isNotBlank() && !it.equals("N/A", true) }
                            ?: "Not linked"
                    )
                    InfoRow("Requested", formatRequestTimestamp(request.createdAt))
                    if (request.updatedAt > request.createdAt) {
                        InfoRow("Last updated", formatRequestTimestamp(request.updatedAt))
                    }
                    InfoRow("Location", request.location.ifBlank { "Not provided" })
                    InfoRow("Notes", request.notes.ifBlank { "None" })
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
