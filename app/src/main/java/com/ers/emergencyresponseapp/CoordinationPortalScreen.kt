package com.ers.emergencyresponseapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import androidx.compose.ui.tooling.preview.Preview

private enum class IncidentStatus { Ongoing, Escalated }
private enum class Agency(val title: String, val color: Color, val icon: ImageVector) {
    Police("Police", Color(0xFF0277BD), Icons.Default.LocalPolice),
    Fire("Fire", Color(0xFFD32F2F), Icons.Default.LocalFireDepartment),
    Medical("Medical", Color(0xFF2E7D32), Icons.Default.LocalHospital)
}
private data class AgencyStatus(val agency: Agency, val available: Int, val deployed: Int, val status: String)
private data class FeedMessage(val timestamp: String, val agency: Agency, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinationTopAppBar() {
    TopAppBar(
        title = {
            Column {
                Text("Coordination Portal", fontWeight = FontWeight.Bold)
                Text("Incident #F-78345", fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
            }
        },
        actions = {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text("Ongoing", modifier = Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.surface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Command", modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentOverviewCard() {
    Card(modifier = Modifier.padding(horizontal = 16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STRUCTURAL FIRE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Badge(containerColor = Color(0xFFD32F2F)) { Text("High Priority", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Location: 451 Main Street, Metro City", style = MaterialTheme.typography.bodyMedium)
            Text("Reported: 14:32 (28m ago)", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Agencies Involved:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                AssistChip(onClick = {}, label = { Text("Police") }, leadingIcon = { Icon(Agency.Police.icon, null) })
                AssistChip(onClick = {}, label = { Text("Fire") }, leadingIcon = { Icon(Agency.Fire.icon, null) })
            }
        }
    }
}

@Composable
private fun SharedMapPreviewCard() {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Shared Tactical Map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        Card(elevation = CardDefaults.cardElevation(2.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray)) {
                Icon(Icons.Default.LocationOn, "Incident Location", modifier = Modifier.align(Alignment.Center).size(40.dp), tint = Color.Red)
                Icon(Agency.Police.icon, "Police Unit", modifier = Modifier.align(Alignment.TopStart).padding(16.dp), tint = Agency.Police.color)
                Icon(Agency.Fire.icon, "Fire Unit", modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp), tint = Agency.Fire.color)
                Icon(Agency.Medical.icon, "Medical Unit", modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), tint = Agency.Medical.color)

                Card(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf(Agency.Police, Agency.Fire, Agency.Medical).forEach { agency ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(agency.color, CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text(agency.title, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgencyStatusBoard() {
    val statuses = listOf(
        AgencyStatus(Agency.Police, 12, 4, "Perimeter Control"),
        AgencyStatus(Agency.Fire, 8, 6, "Structure Suppression"),
        AgencyStatus(Agency.Medical, 6, 2, "Triage Center")
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Agency Deployment Status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        Card(elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                statuses.forEach { status -> AgencyStatusRow(status) }
            }
        }
    }
}

@Composable
private fun AgencyStatusRow(status: AgencyStatus) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(status.agency.icon, null, tint = status.agency.color)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status.agency.title, fontWeight = FontWeight.Bold)
            Text(status.status, style = MaterialTheme.typography.bodySmall)
        }
        Text("D: ${status.deployed}", fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text("A: ${status.available}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoordinationFeed() {
    val messages = listOf(
        FeedMessage("14:48", Agency.Fire, "Primary structure fully engulfed. Requesting aerial support."),
        FeedMessage("14:46", Agency.Police, "East perimeter established. Crowd control in effect."),
        FeedMessage("14:42", Agency.Medical, "Triage set up at corner of Main & 2nd. Ready for patients.")
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Inter-Agency Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(messages) { msg -> FeedMessageItem(msg) }
        }
    }
}

@Composable
private fun FeedMessageItem(msg: FeedMessage) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("${msg.timestamp} [${msg.agency.title}]", fontWeight = FontWeight.Bold, color = msg.agency.color, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(msg.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommandActions() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Assign Agency") }
            Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Request Backup") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Update Status") }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Escalate Incident") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinationPortalScreen() {
    Scaffold(
        topBar = { CoordinationTopAppBar() }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(0.dp)) } // For correct spacing at the top
            item { IncidentOverviewCard() }
            item { SharedMapPreviewCard() }
            item { AgencyStatusBoard() }
            item { CoordinationFeed() }
            item { CommandActions() }
            item { Spacer(Modifier.height(16.dp)) } // For padding at the bottom
        }
    }
}

// Preview removed to avoid tooling dependency in this environment
@Preview(showBackground = true)
@Composable
fun CoordinationPortalScreenPreview() {
    EmergencyResponseAppTheme { CoordinationPortalScreen() }
}
