package com.ers.emergencyresponseapp

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.data.OperationalRepository
import com.ers.emergencyresponseapp.data.RouteAnalyticsEntry
import com.ers.emergencyresponseapp.data.RouteAnalyticsResult
import com.ers.emergencyresponseapp.ui.components.AppPullToRefresh
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Server-backed route analytics.
 *
 * Data is calculated from responder_route_history/responder_route_summary by
 * get-route-analytics.php. No operational route records are stored locally.
 */
@Composable
fun HistoricalRouteAnalyticsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { OperationalRepository() }
    val responderId = remember {
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("user_id", "")
            ?.toIntOrNull()
            ?: 0
    }

    var analytics by remember { mutableStateOf<RouteAnalyticsResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        if (responderId <= 0) {
            analytics = null
            errorMessage = "Your responder session is missing. Sign in again."
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = null

        repository.getRouteAnalytics(responderId)
            .onSuccess { analytics = it }
            .onFailure { error ->
                errorMessage = error.message ?: "Unable to load route analytics."
            }

        isLoading = false
    }

    LaunchedEffect(responderId) {
        refresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }

                    Spacer(Modifier.size(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Route Analytics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Verified navigation records from the response server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { scope.launch { refresh() } },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh route analytics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppPullToRefresh(
            isRefreshing = isLoading,
            onRefresh = { scope.launch { refresh() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
            isLoading && analytics == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null && analytics == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            else -> {
                val current = analytics
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 104.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    errorMessage?.let { warning ->
                        item(key = "stale_warning") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "$warning Showing the last loaded result.",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    current?.let { data ->
                        item(key = "summary") {
                            RouteSummaryCard(data)
                        }

                        item(key = "recent_title") {
                            Text(
                                text = "Recent completed routes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (data.routes.isEmpty()) {
                            item(key = "empty") {
                                EmptyRouteHistory()
                            }
                        } else {
                            items(
                                items = data.routes,
                                key = { route -> route.id.takeIf { it > 0L }
                                    ?: "${route.incidentId}_${route.arrivedAt}" }
                            ) { route ->
                                RouteHistoryCard(route)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(analytics: RouteAnalyticsResult) {
    val summary = analytics.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalyticsMetric(
                    icon = Icons.Default.Directions,
                    label = "Completed",
                    value = summary.totalRoutes.toString(),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetric(
                    icon = Icons.Default.Timer,
                    label = "Avg. time",
                    value = formatDurationSeconds(summary.averageDurationSeconds),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalyticsMetric(
                    icon = Icons.Default.Route,
                    label = "Avg. distance",
                    value = formatDistance(summary.averageDistanceMeters),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetric(
                    icon = Icons.Default.Speed,
                    label = "Avg. speed",
                    value = formatSpeed(summary.averageSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Total tracked distance: ${formatDistance(summary.totalDistanceMeters)}"
                        + " • Peak speed: ${formatSpeed(summary.maxSpeedKmh)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalyticsMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(9.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteHistoryCard(entry: RouteAnalyticsEntry) {
    val title = entry.referenceNo.ifBlank { "Incident #${entry.incidentId}" }
    val type = entry.incidentType.ifBlank { "Emergency response" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = formatServerDate(entry.arrivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry.locationAddress.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = entry.locationAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteValue(
                    label = "Duration",
                    value = formatDurationSeconds(entry.durationSeconds.toDouble()),
                    modifier = Modifier.weight(1f)
                )
                RouteValue(
                    label = "Distance",
                    value = formatDistance(entry.totalDistanceMeters),
                    modifier = Modifier.weight(1f)
                )
                RouteValue(
                    label = "Avg. speed",
                    value = formatSpeed(entry.averageSpeedKmh),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "${entry.totalPoints} GPS point${if (entry.totalPoints == 1) "" else "s"}"
                        + " • Peak ${formatSpeed(entry.maxSpeedKmh)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RouteValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyRouteHistory() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No completed route records yet",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A route appears here after GPS points are saved and arrival is confirmed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDurationSeconds(seconds: Double): String {
    val safe = seconds.coerceAtLeast(0.0).toLong()
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remainingSeconds = safe % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainingSeconds}s"
        else -> "${remainingSeconds}s"
    }
}

private fun formatDistance(meters: Double): String {
    val safe = meters.coerceAtLeast(0.0)
    return if (safe >= 1000.0) {
        String.format(Locale.getDefault(), "%.2f km", safe / 1000.0)
    } else {
        "${NumberFormat.getIntegerInstance().format(safe)} m"
    }
}

private fun formatSpeed(kmh: Double): String =
    String.format(Locale.getDefault(), "%.1f km/h", kmh.coerceAtLeast(0.0))

private fun formatServerDate(raw: String): String {
    if (raw.isBlank()) return ""

    val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    )

    val parsed = inputFormats.firstNotNullOfOrNull { format ->
        runCatching {
            format.timeZone = TimeZone.getTimeZone("Asia/Manila")
            format.parse(raw)
        }.getOrNull()
    } ?: return raw

    return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(parsed)
}
