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
 * Represents a user in the chat system.
 * This is stored in Firebase under /users/{userId}
 * so that each chat can display the sender's name and department.
 *
 * NOTE: You already have user data from your MySQL login.
 * You push this to Firebase once after login (see FirebaseChatRepository).
 */
data class FirebaseUser(
    val userId     : String = "",
    val fullName   : String = "",
    val email      : String = "",
    val department : String = "",   // "Fire" | "Medical" | "Police"
    val isOnline   : Boolean = false,
    val lastSeen   : Long = 0L      // Unix timestamp in milliseconds
)

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