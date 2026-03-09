package com.ers.emergencyresponseapp.coordination.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val role: String,
    val type: MessageType = MessageType.TEXT,
    val text: String? = null,
    val imageUrl: String? = null,
    val location: LocationMessage? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: MutableList<MessageReaction> = mutableListOf(),
    val isOwn: Boolean = false
) {
    val body: String
        get() = when (type) {
            MessageType.TEXT -> text ?: ""
            MessageType.IMAGE -> "📷 Image"
            MessageType.LOCATION -> "📍 Location shared"
            MessageType.SYSTEM -> text ?: "System message"
        }
}

enum class MessageType {
    TEXT,
    IMAGE,
    LOCATION,
    SYSTEM
}

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}

data class LocationMessage(
    val latitude: Double,
    val longitude: Double
)

data class MessageReaction(
    val userId: String,
    val emoji: String
)