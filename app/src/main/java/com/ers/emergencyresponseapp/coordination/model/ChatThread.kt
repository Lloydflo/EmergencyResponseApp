package com.ers.emergencyresponseapp.coordination.model

data class ChatThread(
    val id: String,
    val type: ThreadType,
    val name: String,
    val participants: List<String>,
    val lastMessage: String? = null,
    val unreadCount: Int = 0
)

enum class ThreadType {
    PRIVATE,
    DEPARTMENT,
    INCIDENT
}