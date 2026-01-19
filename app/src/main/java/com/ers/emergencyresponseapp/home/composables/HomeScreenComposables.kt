package com.ers.emergencyresponseapp.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ers.emergencyresponseapp.home.ResponderStatus

@Composable
fun ResponderStatusIndicator(status: ResponderStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(status.color)
        )
        Text(
            text = "Status: ${status.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * QuickActionButtons now accepts callbacks from the parent so the logic (calls, permissions, status updates)
 * can live in the `HomeScreen` composable. This keeps the UI layout unchanged while wiring behavior.
 */
@Composable
fun QuickActionButtons(
    isLocationShared: Boolean,
    onShareLocationToggle: (Boolean) -> Unit,
    onCallCommand: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton(icon = Icons.Default.Call, text = "Call Command", onClick = onCallCommand)

        // Share Location button now delegates toggle behavior to the parent via onShareLocationToggle
        QuickActionButton(icon = Icons.Default.MyLocation, text = if (isLocationShared) "Stop Sharing" else "Share Location", onClick = {
            onShareLocationToggle(!isLocationShared)
        })
    }
}

@Composable
private fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = text, tint = tint)
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MiniMapPreview(
    isSharingLocation: Boolean,
    latitude: Double?,
    longitude: Double?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (isSharingLocation) {
            if (latitude != null && longitude != null) {
                // Placeholder map preview: avoids direct dependency on Google Play services classes
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Map preview: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}")
                }
            } else {
                // Show a loading indicator while fetching location
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Show a placeholder when location sharing is off
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Location sharing is off")
            }
        }
    }
}