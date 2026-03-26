package com.ers.emergencyresponseapp.home.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import com.ers.emergencyresponseapp.home.Incident
import com.ers.emergencyresponseapp.home.IncidentPriority
import com.ers.emergencyresponseapp.home.IncidentType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@OptIn(ExperimentalFoundationApi::class)

@Composable
fun IncidentCard(
    inc: Incident,
    onClick: () -> Unit
) {   // ✅ TAMA NA

    val priorityColor = when (inc.priority) {
        IncidentPriority.HIGH -> Color(0xFFD32F2F)
        IncidentPriority.MEDIUM -> Color(0xFFFFA000)
        IncidentPriority.LOW -> Color(0xFF388E3C)
    }

    val accent = when (inc.type) {
        IncidentType.FIRE -> Color(0xFFE53935)
        IncidentType.MEDICAL -> Color(0xFF1E88E5)
        IncidentType.CRIME -> Color(0xFF6D4C41)
        IncidentType.DISASTER -> Color(0xFF8E24AA)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(inc.type.displayName, fontWeight = FontWeight.SemiBold)
            Text(inc.location)
            Text(inc.description, maxLines = 2)
        }
    }
}