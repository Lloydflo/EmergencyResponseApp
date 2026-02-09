package com.ers.emergencyresponseapp.home.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.config.AgencyInfo
import com.ers.emergencyresponseapp.config.CallConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentSelectionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val departments = listOf(
        CallConfig.agencyForKey("fire"),
        CallConfig.agencyForKey("medical"),
        CallConfig.agencyForKey("crime")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Department to Call", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp)
            ) {
                items(departments) { agency ->
                    DepartmentCallCard(agency = agency) {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${agency.dialNumber}"))
                        context.startActivity(dialIntent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DepartmentCallCard(
    agency: AgencyInfo,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left content takes available space so the Call button won't overlap
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Show a professional vector icon based on the agency key instead of emoji
                val icon: ImageVector = when (agency.key.lowercase()) {
                    "fire" -> Icons.Default.LocalFireDepartment
                    "medical" -> Icons.Default.LocalHospital
                    "crime" -> Icons.Default.LocalPolice
                    "disaster" -> Icons.Default.Warning
                    else -> Icons.Default.Warning
                }

                Icon(
                    imageVector = icon,
                    contentDescription = agency.displayTitle,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(agency.agencyName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                    Text(agency.hotlineDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Ensure the call button has a reasonable minimum width so labels don't overlap
            Button(onClick = onCall, modifier = Modifier.widthIn(min = 88.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text("Call")
            }
        }
    }
}
