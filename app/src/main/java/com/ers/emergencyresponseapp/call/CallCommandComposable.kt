package com.ers.emergencyresponseapp.call

import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.ers.emergencyresponseapp.R
import com.ers.emergencyresponseapp.config.CallConfig
import com.ers.emergencyresponseapp.config.AgencyInfo
import com.ers.emergencyresponseapp.analytics.DefaultAnalyticsLogger

/**
 * Single-file, reusable Call Command composables.
 * - CallCommandButton(): place this on the Home screen.
 * - UnifiedCallCommandDialog(): one dialog showing all departments together.
 * - DepartmentCallCard(): small card for each department.
 *
 * Behavior: tapping CALL opens a confirmation dialog; if confirmed, launches ACTION_DIAL only.
 */

@Composable
fun CallCommandButton(
    modifier: Modifier = Modifier,
    assignedIncidentType: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val agencies = listOf(
        CallConfig.agencyForKey("fire"),
        CallConfig.agencyForKey("medical"),
        CallConfig.agencyForKey("crime"),
        CallConfig.agencyForKey("disaster")
    )

    // helper to close the dialog (keeps analyzer happy)
    fun closeDialog() { showDialog = false }

    Button(onClick = { showDialog = true }, modifier = modifier) {
        Text(text = stringResource(id = R.string.call_command_label))
    }

    if (showDialog) {
        UnifiedCallCommandDialog(
            onDismiss = { closeDialog() },
            assignedIncidentType = assignedIncidentType,
            agencies = agencies
        )
    }
}

@Composable
fun UnifiedCallCommandDialog(
    onDismiss: () -> Unit,
    assignedIncidentType: String? = null,
    agencies: List<AgencyInfo>
) {
    val context = LocalContext.current
    var selectedAgency by remember { mutableStateOf<AgencyInfo?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    // helper to clear local selection state (keeps analyzer happy)
    fun clearSelection() {
        showConfirm = false
        selectedAgency = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.assigned_incident_prefix, assignedIncidentType ?: "N/A"), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Render all agencies in one dialog, visible together
                for ((index, agency) in agencies.withIndex()) {
                    DepartmentCallCard(agency = agency, onCallClick = {
                        selectedAgency = agency
                        showConfirm = true
                        DefaultAnalyticsLogger.logEvent("call_option_selected", mapOf("agency" to agency.key))
                    })

                    if (index < agencies.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(id = R.string.divider))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.close)) }
        }
    )

    if (showConfirm && selectedAgency != null) {
        // Confirmation dialog
        AlertDialog(
            onDismissRequest = { clearSelection() },
            title = { Text(text = stringResource(id = R.string.confirm_call_title, selectedAgency!!.displayTitle), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = "${selectedAgency!!.emoji} ${selectedAgency!!.displayTitle} ${stringResource(id = R.string.emergency_suffix)}", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = stringResource(id = R.string.agency_label, selectedAgency!!.agencyName))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(id = R.string.hotline_label, selectedAgency!!.hotlineDisplay))
                }
            },
            confirmButton = {
                Button(onClick = {
                    // ACTION_DIAL only
                    val dialUri = "tel:${selectedAgency!!.dialNumber}".toUri()
                    val intent = Intent(Intent.ACTION_DIAL, dialUri)
                    try {
                        val resolve = context.packageManager.resolveActivity(intent, 0)
                        if (resolve != null) {
                            DefaultAnalyticsLogger.logEvent("call_now_confirmed", mapOf("agency" to selectedAgency!!.key))
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, context.getString(R.string.no_dialer_available), Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Throwable) {
                        Toast.makeText(context, context.getString(R.string.unable_to_open_dialer), Toast.LENGTH_SHORT).show()
                    } finally {
                        clearSelection()
                        onDismiss()
                    }
                }) { Text(text = stringResource(id = R.string.call_agency_button, selectedAgency!!.callButtonLabel)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { clearSelection() }) { Text(text = stringResource(id = R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DepartmentCallCard(agency: AgencyInfo, onCallClick: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "${agency.emoji} ${agency.displayTitle} ${stringResource(id = R.string.emergency_suffix)}", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(id = R.string.agency_label, agency.agencyName), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(id = R.string.hotline_label, agency.hotlineDisplay), style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = onCallClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text(text = stringResource(id = R.string.call_agency_button, agency.callButtonLabel))
            }
        }
    }
}

// Previews
@Preview(showBackground = true)
@Composable
fun UnifiedCallCommandPreview() {
    val agencies = listOf(
        CallConfig.agencyForKey("fire"),
        CallConfig.agencyForKey("medical"),
        CallConfig.agencyForKey("crime"),
        CallConfig.agencyForKey("disaster")
    )

    UnifiedCallCommandDialog(onDismiss = {}, assignedIncidentType = "FIRE", agencies = agencies)
}

@Preview(showBackground = true)
@Composable
fun CallCommandButtonPreview() {
    CallCommandButton(assignedIncidentType = "FIRE")
}
