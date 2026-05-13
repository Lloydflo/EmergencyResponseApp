package com.ers.emergencyresponseapp.firebase.model

// ═══════════════════════════════════════════════════════════════════════════════
//  FILE 2 of 8 — FirebaseChatModels.kt
//  Place in:  app/src/main/java/com/ers/emergencyresponseapp/firebase/model/
// ═══════════════════════════════════════════════════════════════════════════════

// ── What is a data class? ────────────────────────────────────────────────────
// A data class is a simple container that holds values.
// Firebase needs a "no-argument constructor" to deserialize data from the
// database, so every field MUST have a default value (= "" or = 0L etc.)


/**
 * Represents a single chat message.
 * Stored in Firebase under /messages/{chatId}/{messageId}
 *
 * STATUS values:
 *   "sent"       → message saved to Firebase (sender side)
 *   "delivered"  → other user's device received it
 *   "seen"       → other user opened the chat
 */
data class FirebaseMessage(
    val messageId  : String = "",
    val senderId   : String = "",
    val receiverId : String = "",   // for group chats later: use chatId instead
    val text       : String = "",
    val timestamp  : Long   = 0L,
    val status     : String = "sent"  // "sent" | "delivered" | "seen"
)

/**
 * Represents a chat thread between two users.
 * Stored in Firebase under /chats/{chatId}
 *
 * chatId is always:  smaller_userId + "_" + larger_userId
 * Example: user "3" and user "7" → chatId = "3_7"
 * This guarantees both users always get the same chatId.
 */
data class FirebaseChat(
    val chatId          : String = "",
    val lastMessage     : String = "",
    val lastMessageTime : Long   = 0L,
    val participants    : Map<String, Boolean> = emptyMap()
    // participants looks like: { "user1": true, "user2": true }
    // Using Map<String, Boolean> because Firebase stores it as a JSON object
)