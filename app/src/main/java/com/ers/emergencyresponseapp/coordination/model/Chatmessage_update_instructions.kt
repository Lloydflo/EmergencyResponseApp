// ─────────────────────────────────────────────────────────────────────────────
// ADD THESE TWO FIELDS to your existing ChatMessage data class:
// File: coordination/model/ChatMessage.kt
// ─────────────────────────────────────────────────────────────────────────────
//
// Your ChatMessage data class needs these two new nullable fields:
//
//   val attachmentUri: String? = null,    // stores the Uri as a string for images/files
//   val attachmentName: String? = null,   // filename shown in file bubbles
//
// Also ensure MessageType has IMAGE and FILE values:
//
//   enum class MessageType {
//       TEXT,
//       IMAGE,   // ← add if not present
//       FILE,    // ← add if not present
//       SYSTEM
//   }
//
// Example of the full updated ChatMessage signature:
//
// data class ChatMessage(
//     val id: String = UUID.randomUUID().toString(),
//     val threadId: String,
//     val senderId: String,
//     val senderName: String,
//     val role: String = "",
//     val type: MessageType = MessageType.TEXT,
//     val text: String? = null,
//     val isOwn: Boolean = false,
//     val createdAt: Long = System.currentTimeMillis(),
//     val status: MessageStatus = MessageStatus.SENT,
//     val reactions: List<MessageReaction> = emptyList(),
//     val attachmentUri: String? = null,    // ← NEW
//     val attachmentName: String? = null    // ← NEW
// )
//
// Also add Coil to your build.gradle.kts (app):
//   implementation("io.coil-kt:coil-compose:2.6.0")
// ─────────────────────────────────────────────────────────────────────────────