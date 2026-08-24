package com.ers.emergencyresponseapp.coordination.model

import java.util.UUID

// ── Message type ─────────────────────────────────────────────────────────────
enum class MessageType {
    TEXT,
    IMAGE,   // image attachment — rendered inline in the bubble
    FILE,    // any other file — rendered as a file card in the bubble
    SYSTEM   // system / event messages (join, leave, etc.)
}

// ── Message status (for read receipts) ───────────────────────────────────────
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

// ── Per-message emoji reaction ────────────────────────────────────────────────
data class MessageReaction(
    val userId: String,
    val emoji: String
)

// ── Chat message ──────────────────────────────────────────────────────────────
data class ChatMessage(
    val id: String             = UUID.randomUUID().toString(),
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val role: String           = "",
    val type: MessageType      = MessageType.TEXT,
    val text: String?          = null,
    val isOwn: Boolean         = false,
    val createdAt: Long        = System.currentTimeMillis(),
    val status: MessageStatus  = MessageStatus.SENT,
    val reactions: List<MessageReaction> = emptyList(),

    // ── Attachment fields (IMAGE and FILE messages) ───────────────────────────
    // Stored as a String so the data class stays serialisation-friendly.
    // In the UI, pass Uri.parse(attachmentUri) to Coil's AsyncImage.
    val attachmentUri: String?  = null,   // content:// or file:// URI as string
    val attachmentName: String? = null    // original filename shown in FILE bubbles
)