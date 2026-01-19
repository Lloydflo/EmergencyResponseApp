package com.ers.emergencyresponseapp.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentType

@Composable
fun IncidentTypeSummary(incidents: List<Incident>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Filter out the DISASTER type before creating the summary cards
        IncidentType.values().filter { it != IncidentType.DISASTER }.forEach { type ->
            val count = incidents.count { it.type == type }
            IncidentTypeSummaryCard(type = type, count = count)
        }
    }
}

@Composable
fun IncidentTypeSummaryCard(type: IncidentType, count: Int) {
    Card(
        modifier = Modifier.padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = type.displayName, style = MaterialTheme.typography.titleMedium)
            Text(text = count.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun MiniMapPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Text("Map Preview", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun MyAssignedIncident(incident: Incident?, onNavigate: () -> Unit = {}, onUpdateStatus: () -> Unit = {}) {
    if (incident != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "My Assigned Incident", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                IncidentDetails(incident = incident)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onNavigate() }) {
                        Text("Navigate")
                    }
                    Button(onClick = { onUpdateStatus() }) {
                        Text("Update Status")
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveIncidents(incidents: List<Incident>) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = "Active Incidents", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        incidents.forEach { incident ->
            IncidentCard(incident = incident)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun IncidentCard(incident: Incident) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                IncidentDetails(incident = incident)
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxWidth()
                    .background(incident.priority.color)
            )
        }
    }
}

@Composable
fun IncidentDetails(incident: Incident) {
    Text(text = incident.type.displayName, style = MaterialTheme.typography.titleMedium)
    Text(text = "Priority: ${incident.priority.displayName}", color = incident.priority.color)
    Text(text = "Location: ${incident.location}", style = MaterialTheme.typography.bodyMedium)
    Text(text = "Reported: ${incident.timeReported}", style = MaterialTheme.typography.bodySmall)
    Text(text = "Status: ${incident.status.displayName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}
