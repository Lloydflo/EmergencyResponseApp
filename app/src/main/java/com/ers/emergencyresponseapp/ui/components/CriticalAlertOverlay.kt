package com.ers.emergencyresponseapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ers.emergencyresponseapp.alert.CriticalAlertCenter
import com.ers.emergencyresponseapp.alert.ProtocolAlertKind
import com.ers.emergencyresponseapp.notification.AppNotificationManager

/**
 * App-wide, hard-to-miss alert for dispatcher-issued protocol events
 * (Emergency Broadcast, Lockdown Protocol, Mass Casualty). Mounted once at
 * the top of [com.ers.emergencyresponseapp.MainActivity], above the nav
 * host, so it renders on top of whatever screen the responder is currently
 * viewing — Home, Coordination, the live map, anywhere.
 *
 * It is intentionally NOT dismissible by tapping outside or pressing back:
 * the responder must read it and tap Acknowledge. This mirrors the matching
 * system notification, which is also non-swipeable until acknowledged here.
 */
@Composable
fun CriticalAlertOverlay() {
    val alerts by CriticalAlertCenter.alerts.collectAsState()
    val current = alerts.firstOrNull() ?: return
    val queuedMore = alerts.size - 1
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* no-op: must be acknowledged, not dismissed */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val visuals = protocolVisuals(current.kind)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(visuals.accent, shape = RoundedCornerShape(29.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visuals.icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = visuals.label,
                    color = visuals.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = current.title.ifBlank { visuals.label },
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                if (current.message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = current.message,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (queuedMore > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "+$queuedMore more active alert" + if (queuedMore == 1) "" else "s",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        AppNotificationManager.clearAlert(context, "protocol:${current.id}")
                        CriticalAlertCenter.acknowledge(current.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = visuals.accent)
                ) {
                    Text("Naintindihan / Acknowledge")
                }
            }
        }
    }
}

private data class ProtocolVisuals(
    val accent: Color,
    val icon: ImageVector,
    val label: String
)

private fun protocolVisuals(kind: ProtocolAlertKind): ProtocolVisuals = when (kind) {
    ProtocolAlertKind.LOCKDOWN -> ProtocolVisuals(
        accent = Color(0xFFD32F2F),
        icon = Icons.Default.Lock,
        label = "LOCKDOWN PROTOCOL"
    )

    ProtocolAlertKind.MASS_CASUALTY -> ProtocolVisuals(
        accent = Color(0xFFC62828),
        icon = Icons.Default.LocalHospital,
        label = "MASS CASUALTY INCIDENT"
    )

    ProtocolAlertKind.BROADCAST -> ProtocolVisuals(
        accent = Color(0xFFEF6C00),
        icon = Icons.Default.Campaign,
        label = "EMERGENCY BROADCAST"
    )
}
