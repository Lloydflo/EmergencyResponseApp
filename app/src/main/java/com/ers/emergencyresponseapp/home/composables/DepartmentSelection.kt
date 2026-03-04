package com.ers.emergencyresponseapp.home.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.material3.ripple


private object BackupColors {
    val Primary = Color(0xFF4C8A89)
    val Border = Color(0xFFE5E5E5)
    val Text = Color(0xFF171717)
    val SubText = Color(0xFF6B6B6B)
    val DialogBg = Color(0xFFF7F5F9)
}

private data class DepartmentItem(
    val key: String,
    val name: String,
    val icon: @Composable () -> Unit
)
@Composable
fun DepartmentSelectionDialog(
    onDismiss: () -> Unit,
    onDepartmentSelected: (String) -> Unit
) {
    val departments = listOf(
        DepartmentItem(
            key = "fire",
            name = "City Fire Department",
            icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = BackupColors.Primary) }
        ),
        DepartmentItem(
            key = "medical",
            name = "Medical Department",
            icon = { Icon(Icons.Default.LocalHospital, contentDescription = null, tint = BackupColors.Primary) }
        ),
        DepartmentItem(
            key = "crime",
            name = "Police Department",
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = BackupColors.Primary) }
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = BackupColors.DialogBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Request Backup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = BackupColors.Text
                    )
                    Text(
                        text = "Select a department to notify.",
                        fontSize = 12.sp,
                        color = BackupColors.SubText
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = BackupColors.SubText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Divider(color = BackupColors.Border)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(departments) { dept ->
                        DepartmentRow(
                            name = dept.name,
                            leadingIcon = dept.icon,
                            onSend = { onDepartmentSelected(dept.key) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BackupColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun DepartmentRow(
    name: String,
    leadingIcon: @Composable () -> Unit,
    onSend: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(), // ✅ Material3 ripple
            ) { onSend() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BackupColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        BackupColors.Primary.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = BackupColors.Text
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Tap to send backup request",
                    fontSize = 12.sp,
                    color = BackupColors.SubText
                )
            }
        }
    }
}