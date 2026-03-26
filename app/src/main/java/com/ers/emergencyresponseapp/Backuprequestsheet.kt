package com.ers.emergencyresponseapp.home.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*


// ─────────────────────────────────────────────
//  DATA MODEL
// ─────────────────────────────────────────────

enum class BackupDepartment(
    val displayName: String,
    val shortCode: String,
    val accent: Color,
    val icon: ImageVector
) {
    FIRE(
        displayName = "Fire",
        shortCode   = "FIRE",
        accent      = Color(0xFFE53935),
        icon        = Icons.Default.LocalFireDepartment
    ),
    MEDICAL(
        displayName = "Medical",
        shortCode   = "MED",
        accent      = Color(0xFF1E88E5),
        icon        = Icons.Default.LocalHospital
    ),
    POLICE(
        displayName = "Police",
        shortCode   = "POL",
        accent      = Color(0xFF5C6BC0),
        icon        = Icons.Default.Security
    )
}

data class BackupResource(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val department: BackupDepartment
)

// ──── Resource catalogue per department ────
val BACKUP_RESOURCES: Map<BackupDepartment, List<BackupResource>> = mapOf(

    BackupDepartment.FIRE to listOf(
        BackupResource("fire_fighters",    "Firefighters",     Icons.Default.People,         BackupDepartment.FIRE),
        BackupResource("fire_truck",       "Fire Truck",       Icons.Default.LocalShipping,  BackupDepartment.FIRE),
        BackupResource("rescue_equipment", "Rescue Equipment", Icons.Default.Build,          BackupDepartment.FIRE),
        BackupResource("aerial_ladder",    "Aerial Ladder",    Icons.Default.ArrowUpward,    BackupDepartment.FIRE),
        BackupResource("hazmat_team",      "HazMat Team",      Icons.Default.Warning,        BackupDepartment.FIRE)
    ),

    BackupDepartment.MEDICAL to listOf(
        BackupResource("paramedics",    "Paramedics",    Icons.Default.PersonPin,      BackupDepartment.MEDICAL),
        BackupResource("ambulance",     "Ambulance",     Icons.Default.LocalShipping,  BackupDepartment.MEDICAL),
        BackupResource("medical_kit",   "Medical Kit",   Icons.Default.MedicalServices,BackupDepartment.MEDICAL),
        BackupResource("trauma_team",   "Trauma Team",   Icons.Default.Group,          BackupDepartment.MEDICAL),
        BackupResource("defibrillator", "Defibrillator", Icons.Default.Bolt,           BackupDepartment.MEDICAL)
    ),

    BackupDepartment.POLICE to listOf(
        BackupResource("officers",     "Officers",     Icons.Default.Badge,          BackupDepartment.POLICE),
        BackupResource("patrol_car",   "Patrol Car",   Icons.Default.DirectionsCar,  BackupDepartment.POLICE),
        BackupResource("k9_unit",      "K9 Unit",      Icons.Default.Pets,           BackupDepartment.POLICE),
        BackupResource("swat",         "SWAT",         Icons.Default.Shield,         BackupDepartment.POLICE),
        BackupResource("negotiator",   "Negotiator",   Icons.Default.RecordVoiceOver,BackupDepartment.POLICE)
    )
)

// ──── Request payload that gets sent to dispatch ────
data class BackupRequest(
    val fromIncidentId: String,
    val department: BackupDepartment,
    val resources: List<BackupResource>,
    val isFullBackup: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)


// ─────────────────────────────────────────────
//  DEPARTMENT SELECTION DIALOG
//  (replaces the existing DepartmentSelectionDialog)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentSelectionDialog(
    onDismiss: () -> Unit,
    // ★ NEW: receives the full structured request with dept + resources
    onBackupRequestReady: (BackupRequest) -> Unit = {},
    // Legacy string-key callback kept for backward-compat
    onDepartmentSelected: (departmentKey: String) -> Unit = {}
) {
    var selectedDepartment by remember { mutableStateOf<BackupDepartment?>(null) }

    if (selectedDepartment == null) {
        // ── Step 1: pick a department ──
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFFF8F9FB),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = Color(0xFF4C8A89),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Request Backup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select a department to request assistance from.",
                        color = Color(0xFF575757),
                        fontSize = 13.sp
                    )
                    BackupDepartment.entries.forEach { dept ->
                        DepartmentTile(
                            department = dept,
                            onClick = { selectedDepartment = dept }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    } else {
        // ── Step 2: resource picker sheet ──
        BackupResourceSheet(
            department = selectedDepartment!!,
            onDismiss  = onDismiss,
            onSend     = { request ->
                // Fire the full payload callback (used by HomeScreen)
                onBackupRequestReady(request)
                // Also fire legacy string-key callback for any older callers
                onDepartmentSelected(request.department.shortCode)
                onDismiss()
            },
            onBack = { selectedDepartment = null }
        )
    }
}


// ─────────────────────────────────────────────
//  DEPARTMENT TILE  (Step 1 row)
// ─────────────────────────────────────────────

@Composable
private fun DepartmentTile(
    department: BackupDepartment,
    onClick: () -> Unit
) {
    val accent = department.accent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.08f), Color.White)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = department.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = department.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF171717)
                )
                Text(
                    text = "${BACKUP_RESOURCES[department]?.size ?: 0} resources available",
                    fontSize = 12.sp,
                    color = Color(0xFF575757)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


// ─────────────────────────────────────────────
//  BACKUP RESOURCE BOTTOM-SHEET  (Step 2)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupResourceSheet(
    department: BackupDepartment,
    incidentId: String = "",
    onDismiss: () -> Unit,
    onSend: (BackupRequest) -> Unit,
    onBack: (() -> Unit)? = null          // null = show as standalone sheet
) {
    val accent     = department.accent
    val resources  = BACKUP_RESOURCES[department] ?: emptyList()
    val selected   = remember { mutableStateListOf<String>() }   // stores resource ids
    var fullBackup by remember { mutableStateOf(false) }

    // sync full-backup toggle
    LaunchedEffect(fullBackup) {
        if (fullBackup) {
            selected.clear()
            selected.addAll(resources.map { it.id })
        } else if (selected.size == resources.size) {
            // user untoggled full backup → keep selection but unmark flag
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color(0xFFF8F9FB),
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEEEEE))
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(department.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${department.displayName} Backup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF171717)
                    )
                    Text(
                        text = "Select needed resources",
                        fontSize = 12.sp,
                        color = Color(0xFF575757)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE5E5E5))

            // ── Full Backup quick button ──
            FullBackupChip(
                accent     = accent,
                active     = fullBackup,
                onToggle   = {
                    fullBackup = !fullBackup
                    if (!fullBackup) selected.clear()
                }
            )

            // ── Resource chips grid ──
            Text(
                text = "Or choose specific resources",
                fontSize = 12.sp,
                color = Color(0xFF575757),
                fontWeight = FontWeight.Medium
            )

            // 2-column grid via chunked rows
            val rows = resources.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { resource ->
                            val isSelected = resource.id in selected
                            ResourceChip(
                                resource   = resource,
                                accent     = accent,
                                isSelected = isSelected,
                                modifier   = Modifier.weight(1f),
                                onClick    = {
                                    if (isSelected) {
                                        selected.remove(resource.id)
                                        if (fullBackup) fullBackup = false
                                    } else {
                                        selected.add(resource.id)
                                        if (selected.size == resources.size) fullBackup = true
                                    }
                                }
                            )
                        }
                        // pad last row if odd number of items
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Selection summary ──
            AnimatedVisibility(visible = selected.isNotEmpty()) {
                Text(
                    text = "Requesting: ${selected.size} resource${if (selected.size > 1) "s" else ""}",
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Send button ──
            Button(
                onClick = {
                    val selectedResources = resources.filter { it.id in selected }
                    onSend(
                        BackupRequest(
                            fromIncidentId = incidentId,
                            department     = department,
                            resources      = selectedResources,
                            isFullBackup   = fullBackup
                        )
                    )
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    disabledContainerColor = Color(0xFFBDBDBD)
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (fullBackup) "Send Full Backup Request" else "Send Request",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}


// ─────────────────────────────────────────────
//  FULL BACKUP CHIP
// ─────────────────────────────────────────────

@Composable
private fun FullBackupChip(
    accent: Color,
    active: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) accent.copy(alpha = 0.13f) else Color.White
        ),
        border = BorderStroke(
            width = if (active) 2.dp else 1.dp,
            color = if (active) accent else Color(0xFFE5E5E5)
        ),
        elevation = CardDefaults.cardElevation(if (active) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) accent.copy(0.18f) else Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (active) accent else Color(0xFF9E9E9E),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Full Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (active) accent else Color(0xFF171717)
                )
                Text(
                    "Request all available resources at once",
                    fontSize = 11.sp,
                    color = Color(0xFF757575)
                )
            }

            if (active) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


// ─────────────────────────────────────────────
//  INDIVIDUAL RESOURCE CHIP
// ─────────────────────────────────────────────

@Composable
private fun ResourceChip(
    resource: BackupResource,
    accent: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val animBg by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.12f) else Color.White,
        animationSpec = tween(160),
        label = "bg"
    )
    val animBorder by animateColorAsState(
        targetValue = if (isSelected) accent else Color(0xFFE0E0E0),
        animationSpec = tween(160),
        label = "border"
    )
    val animIconTint by animateColorAsState(
        targetValue = if (isSelected) accent else Color(0xFF9E9E9E),
        animationSpec = tween(160),
        label = "icon"
    )

    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animBg),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = animBorder
        ),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = resource.icon,
                contentDescription = resource.label,
                tint = animIconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = resource.label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accent else Color(0xFF424242),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}


// ─────────────────────────────────────────────
//  HOW TO USE IN HomeScreen.kt
// ─────────────────────────────────────────────
//
//  1. Replace the existing FAB onClick with:
//
//       onClick = { showDepartmentSelection = true }
//
//  2. In the dialogs section, keep your existing block:
//
//       if (showDepartmentSelection) {
//           DepartmentSelectionDialog(
//               onDismiss = { showDepartmentSelection = false },
//               onDepartmentSelected = { departmentKey ->
//                   sendBackupMessageToDepartment(departmentKey)   // your existing handler
//                   showDepartmentSelection = false
//               }
//           )
//       }
//
//     The new DepartmentSelectionDialog is a drop-in replacement — same signature.
//
//  3. When you're ready to wire real dispatch logic, update sendBackupMessageToDepartment
//     to accept a full BackupRequest instead of just a String key. Example:
//
//       fun sendBackupRequest(request: BackupRequest) {
//           val tag = request.department.shortCode      // e.g. "FIRE"
//           val resources = request.resources.joinToString { it.label }
//           val payload = mapOf(
//               "department"  to tag,
//               "resources"   to resources,
//               "full_backup" to request.isFullBackup,
//               "incident_id" to request.fromIncidentId,
//               "timestamp"   to request.timestamp
//           )
//           // POST payload to your backend / Firebase / etc.
//       }
//
//  4. On the receiving side (other responders' devices), filter incoming backup
//     notifications by matching the "department" tag against each responder's
//     stored department in SharedPreferences ("department" key). Only show the
//     notification if the tags match.