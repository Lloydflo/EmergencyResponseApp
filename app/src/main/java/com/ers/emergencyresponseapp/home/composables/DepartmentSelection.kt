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
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.config.AgencyInfo
import com.ers.emergencyresponseapp.config.CallConfig

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
        title = { Text("Select Department to Call", fontWeight = FontWeight.Bold) },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(agency.emoji, fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                Column {
                    Text(agency.agencyName, fontWeight = FontWeight.Bold)
                    Text(agency.hotlineDisplay, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onCall) {
                Text("Call")
            }
        }
    }
}
