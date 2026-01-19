package com.ers.emergencyresponseapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )
    object CoordinationPortal : BottomNavItem(
        route = "coordination_portal",
        title = "Coordination Portal",
        icon = Icons.Default.Groups
    )
    object ReviewsFeedback : BottomNavItem(
        route = "reviews_feedback",
        title = "Reviews and Feedback",
        icon = Icons.Default.RateReview
    )
}
