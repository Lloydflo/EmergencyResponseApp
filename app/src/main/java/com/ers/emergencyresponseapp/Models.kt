package com.ers.emergencyresponseapp

import androidx.compose.runtime.Immutable

@Immutable
data class ResponderBrief(
    val id: String,
    val username: String,
    val fullName: String,
    val role: String,
    val status: String,
    val lastMessage: String = "",
    val unreadCount: Int = 0
)

@Immutable
data class DepartmentInfo(
    val name: String,
    val displayName: String,
    val emoji: String,
    val lastMessage: String = "",
    val unreadCount: Int = 0
)

@Immutable
data class ChatMessage(
    val id: String?,
    val senderId: String,
    val senderName: String,
    val role: String,
    val body: String,
    val createdAt: Long,
    val isOwn: Boolean
)

// Holds the logged-in responder's profile data.
// Populated from the login API response and stored in SharedPreferences.
@Immutable
data class LoggedInUser(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val department: String
)
