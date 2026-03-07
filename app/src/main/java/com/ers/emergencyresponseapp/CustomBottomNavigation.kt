package com.ers.emergencyresponseapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomBottomNavigation(
    selectedRoute: String,
    onItemSelected: (String) -> Unit
) {

    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.CoordinationPortal,
        BottomNavItem.ReviewsFeedback
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 10.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {

            items.forEach { item ->

                val selected = selectedRoute == item.route

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onItemSelected(item.route) }
                        .background(
                            if (selected) Color(0xFFE9E2F8) else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (selected) Color(0xFF4C8A89) else Color.Gray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.Black else Color.Gray
                    )
                }
            }
        }
    }
}