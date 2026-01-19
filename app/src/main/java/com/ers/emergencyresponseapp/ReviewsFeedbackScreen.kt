package com.ers.emergencyresponseapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme

private enum class ReviewStatus(val title: String, val color: Color) {
    Pending("Pending Review", Color(0xFFFFA000)),
    Submitted("Feedback Submitted", Color(0xFF0277BD)),
    Completed("Completed", Color(0xFF388E3C))
}

private data class ReviewableIncident(
    val id: String,
    val type: String,
    val date: String,
    val status: ReviewStatus
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewsTopAppBar() {
    TopAppBar(
        title = { Text("Reviews & Feedback", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = { /* UI Only */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewFilters() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = true,
            onClick = { /* UI Only */ },
            label = { Text("Pending") },
            leadingIcon = { Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = false,
            onClick = { /* UI Only */ },
            label = { Text("Submitted") },
            leadingIcon = { Icon(Icons.Filled.Comment, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = false,
            onClick = { /* UI Only */ },
            label = { Text("Completed") },
            leadingIcon = { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun IncidentReviewList() {
    val incidents = remember {
        listOf(
            ReviewableIncident("#I-9845", "Medical Emergency", "2024-07-28 | 14:30", ReviewStatus.Pending),
            ReviewableIncident("#F-2341", "Structure Fire", "2024-07-28 | 08:15", ReviewStatus.Pending),
            ReviewableIncident("#C-5678", "Robbery", "2024-07-27 | 21:00", ReviewStatus.Submitted),
            ReviewableIncident("#D-1123", "Flood Zone Evac", "2024-07-26 | 11:45", ReviewStatus.Completed)
        )
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(incidents) { incident ->
            IncidentReviewCard(incident = incident)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentReviewCard(incident: ReviewableIncident) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = incident.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Incident ID: ${incident.id}", style = MaterialTheme.typography.bodySmall)
                }
                Badge(containerColor = incident.status.color) {
                    Text(
                        text = incident.status.title,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Date: ${incident.date}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { /* UI Only */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (incident.status == ReviewStatus.Pending) "Write Review" else "View Details")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsFeedbackScreen() {
    Scaffold(
        topBar = { ReviewsTopAppBar() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            ReviewFilters()
            IncidentReviewList()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewsFeedbackScreenPreview() {
    EmergencyResponseAppTheme {
        ReviewsFeedbackScreen()
    }
}
