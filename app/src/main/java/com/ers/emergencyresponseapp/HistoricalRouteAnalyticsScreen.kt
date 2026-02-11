package com.ers.emergencyresponseapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.analytics.RouteHistoryEntry
import com.ers.emergencyresponseapp.analytics.RouteHistoryStore
import java.util.concurrent.TimeUnit

@Composable
fun HistoricalRouteAnalyticsScreen() {
    val context = LocalContext.current
    val history = RouteHistoryStore.getHistory(context)
    val total = history.size

    val avgDurationMillis = if (total > 0) history.sumOf { it.durationMillis } / total else 0L
    val avgDistanceMeters = if (total > 0) history.sumOf { it.straightLineMeters.toDouble() } / total else 0.0
    val avgSpeedMps = if (avgDurationMillis > 0) avgDistanceMeters / (avgDurationMillis / 1000.0) else 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Historical Route Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        AnalyticsSummaryCard(
            totalRoutes = total,
            avgDurationMillis = avgDurationMillis,
            avgDistanceMeters = avgDistanceMeters,
            avgSpeedMps = avgSpeedMps
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Recent Routes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history.takeLast(10).reversed()) { entry ->
                RouteHistoryCard(entry)
            }
        }
    }
}

@Composable
private fun AnalyticsSummaryCard(
    totalRoutes: Int,
    avgDurationMillis: Long,
    avgDistanceMeters: Double,
    avgSpeedMps: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Total routes: $totalRoutes", fontWeight = FontWeight.SemiBold)
            Text("Avg duration: ${formatDuration(avgDurationMillis)}")
            Text("Avg distance: ${String.format("%.1f", avgDistanceMeters)} m")
            Text("Avg speed: ${String.format("%.2f", avgSpeedMps)} m/s")
        }
    }
}

@Composable
private fun RouteHistoryCard(entry: RouteHistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Incident: ${entry.incidentId}", fontWeight = FontWeight.SemiBold)
            Text("Duration: ${formatDuration(entry.durationMillis)}")
            Text("Distance: ${String.format("%.1f", entry.straightLineMeters)} m")
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
