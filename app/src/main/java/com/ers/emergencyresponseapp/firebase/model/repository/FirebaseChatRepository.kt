package com.ers.emergencyresponseapp.firebase.repository

// ═══════════════════════════════════════════════════════════════════════════════
//  FILE 3 of 8 — FirebaseChatRepository.kt
//  Place in:  app/src/main/java/com/ers/emergencyresponseapp/firebase/repository/
//
//  This file handles ALL Firebase database operations:
//    • Sending messages
//    • Listening for new messages in real-time
//    • Creating/updating chat metadata
//    • Updating message status (delivered / seen)
//    • Saving user info to Firebase after login
// ═══════════════════════════════════════════════════════════════════════════════

import com.ers.emergencyresponseapp.firebase.model.FirebaseChat
import com.ers.emergencyresponseapp.firebase.model.FirebaseMessage
import com.ers.emergencyresponseapp.firebase.model.FirebaseUser
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseChatRepository {

    // ── Get a reference to the Firebase Realtime Database root ───────────────
    // FirebaseDatabase.getInstance() connects to your database using
    // the google-services.json file you placed in the /app folder.
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()

    // ── Shortcuts to each top-level node ─────────────────────────────────────
    private val chatsRef    : DatabaseReference = db.getReference("chats")
    private val messagesRef : DatabaseReference = db.getReference("messages")
    private val usersRef    : DatabaseReference = db.getReference("users")
    private val userChatsRef: DatabaseReference = db.getReference("userChats")

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPER — Generate a consistent chatId for two users
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Creates a unique chatId by sorting both user IDs alphabetically
     * and joining them with "_".
     *
     * WHY SORT? So that user "alice" chatting with user "bob" always gets
     * chatId = "alice_bob", not sometimes "bob_alice".
     * Both users always find the same chat.
     *
     * Example:
     *   buildChatId("alice", "bob")  → "alice_bob"
     *   buildChatId("bob", "alice")  → "alice_bob"  ← same result!
     */
    fun buildChatId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SAVE USER — call this once right after your MySQL login succeeds
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Saves the logged-in user's info to Firebase under /users/{userId}.
     * This lets chat partners see the sender's name and department.
     *
     * Call this in your login screen after a successful API login:
     *   firebaseChatRepository.saveUserToFirebase(
     *       userId = "123",
     *       fullName = "Alice Johnson",
     *       email = "alice@ers.com",
     *       department = "Fire"
     *   )
     */
    suspend fun saveUserToFirebase(
        userId: String,
        fullName: String,
        email: String,
        department: String
    ) {
        val user = FirebaseUser(
            userId     = userId,
            fullName   = fullName,
            email      = email,
            department = department.lowercase(),
            isOnline   = true,
            lastSeen   = System.currentTimeMillis()
        )
        // setValue() writes the whole object to /users/{userId}
        // .await() makes it a suspend function (waits for Firebase to confirm)
        usersRef.child(userId).setValue(user).await()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SET ONLINE STATUS — call on app open / close
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun setOnlineStatus(userId: String, isOnline: Boolean) {
        try {
            val updates = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis(),
                "online"   to null  // deletes the duplicate field
            )
            usersRef.child(userId).updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND MESSAGE
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Sends a message from senderId to receiverId.
     *
     * What this function does step by step:
     *   1. Builds the chatId (e.g. "alice_bob")
     *   2. Generates a unique messageId using Firebase's push() key
     *   3. Saves the message under /messages/{chatId}/{messageId}
     *   4. Updates /chats/{chatId} with the last message text + time
     *   5. Updates /userChats/{senderId} and /userChats/{receiverId}
     *      so both users' inbox lists include this chat
     *
     * Returns: true if successful, false if something went wrong
     */
    suspend fun sendMessage(
        senderId   : String,
        receiverId : String,
        text       : String
    ): Boolean {
        return try {
            val chatId    = buildChatId(senderId, receiverId)
            val timestamp = System.currentTimeMillis()

            // Step 1: Generate a unique key for this message
            // push() creates a new child with an auto-generated key like "-NxABC123"
            val messageKey = messagesRef.child(chatId).push().key
                ?: return false  // push().key is null only if offline — handle gracefully

            // Step 2: Create the message object
            val message = FirebaseMessage(
                messageId  = messageKey,
                senderId   = senderId,
                receiverId = receiverId,
                text       = text,
                timestamp  = timestamp,
                status     = "sent"
            )

            // Step 3: Save message to /messages/{chatId}/{messageKey}
            messagesRef
                .child(chatId)
                .child(messageKey)
                .setValue(message)
                .await()

            // Step 4: Update chat metadata at /chats/{chatId}
            val chatUpdate = mapOf(
                "chatId"          to chatId,
                "lastMessage"     to text,
                "lastMessageTime" to timestamp,
                "participants"    to mapOf(senderId to true, receiverId to true)
            )
            chatsRef.child(chatId).updateChildren(chatUpdate).await()

            // Step 5: Index this chat under both users
            // /userChats/senderId/chatId = true
            // /userChats/receiverId/chatId = true
            userChatsRef.child(senderId).child(chatId).setValue(true).await()
            userChatsRef.child(receiverId).child(chatId).setValue(true).await()

            true // success!

        } catch (e: Exception) {
            // If anything fails (no internet, etc.) return false
            android.util.Log.e("FirebaseChat", "sendMessage failed: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LISTEN FOR MESSAGES  (real-time)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Returns a Flow<List<FirebaseMessage>> that emits the full list of
     * messages whenever a new message is added to the chat.
     *
     * WHAT IS A FLOW?
     * A Flow is like a live stream of data. Every time the database changes,
     * the UI automatically receives the updated list — no manual refresh needed.
     *
     * HOW TO USE IN THE VIEWMODEL:
     *   viewModelScope.launch {
     *       repository.listenToMessages(chatId).collect { messages ->
     *           _messages.value = messages
     *       }
     *   }
     */
    fun listenToMessages(chatId: String): Flow<List<FirebaseMessage>> {
        // callbackFlow converts Firebase's callback-based listener into a Flow
        return callbackFlow {

            val messageList = mutableListOf<FirebaseMessage>()

            // ChildEventListener fires for each child (message) individually:
            //   onChildAdded   → new message arrived
            //   onChildChanged → message was edited/status updated
            //   onChildRemoved → message was deleted
            val listener = object : ChildEventListener {

                override fun onChildAdded(snapshot: DataSnapshot, previousKey: String?) {
                    // Convert the Firebase snapshot to our FirebaseMessage data class
                    val message = snapshot.getValue(FirebaseMessage::class.java)
                    if (message != null) {
                        messageList.add(message)
                        // Send the updated list to the Flow collector
                        trySend(messageList.toList())
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousKey: String?) {
                    // Handles status updates (sent → delivered → seen)
                    val updatedMessage = snapshot.getValue(FirebaseMessage::class.java)
                    if (updatedMessage != null) {
                        val index = messageList.indexOfFirst { it.messageId == updatedMessage.messageId }
                        if (index >= 0) {
                            messageList[index] = updatedMessage
                            trySend(messageList.toList())
                        }
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val removed = snapshot.getValue(FirebaseMessage::class.java)
                    if (removed != null) {
                        messageList.removeAll { it.messageId == removed.messageId }
                        trySend(messageList.toList())
                    }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousKey: String?) { }
                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("FirebaseChat", "Listener cancelled: ${error.message}")
                    close(error.toException()) // close the Flow with an error
                }
            }

            // Attach the listener to /messages/{chatId}
            // orderByChild("timestamp") ensures messages come in chronological order
            val query = messagesRef
                .child(chatId)
                .orderByChild("timestamp")

            query.addChildEventListener(listener)

            // awaitClose is called when the Flow is cancelled (e.g. screen closes)
            // This removes the Firebase listener to prevent memory leaks
            awaitClose {
                query.removeEventListener(listener)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE MESSAGE STATUS  (delivered / seen)
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Marks all unread messages in a chat as "delivered" when the receiver
     * connects, and "seen" when the receiver opens the chat screen.
     *
     * Call markMessagesDelivered() when the app starts (receiver is online).
     * Call markMessagesSeen() when the receiver opens a specific chat.
     */
    suspend fun markMessagesDelivered(chatId: String, receiverId: String) {
        try {
            // Read all messages in this chat once (not a live listener)
            val snapshot = messagesRef.child(chatId).get().await()
            snapshot.children.forEach { child ->
                val msg = child.getValue(FirebaseMessage::class.java) ?: return@forEach
                // Only update messages sent TO this user that are still "sent"
                if (msg.receiverId == receiverId && msg.status == "sent") {
                    child.ref.child("status").setValue("delivered")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseChat", "markDelivered failed: ${e.message}")
        }
    }

    suspend fun markMessagesSeen(chatId: String, receiverId: String) {
        try {
            val snapshot = messagesRef.child(chatId).get().await()
            snapshot.children.forEach { child ->
                val msg = child.getValue(FirebaseMessage::class.java) ?: return@forEach
                // Mark as "seen" only messages sent TO this user
                if (msg.receiverId == receiverId && msg.status != "seen") {
                    child.ref.child("status").setValue("seen")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseChat", "markSeen failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET USER INFO
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Fetches a user's info from Firebase once (not real-time).
     * Used to display the chat partner's name in the top bar.
     */
    suspend fun getUserInfo(userId: String): FirebaseUser? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            snapshot.getValue(FirebaseUser::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Listens for real-time online/offline status of a user.
     * Used to show "Online" or "Last seen X minutes ago" in the chat header.
     */
    fun listenToUserPresence(userId: String): Flow<FirebaseUser?> {
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.getValue(FirebaseUser::class.java))
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            usersRef.child(userId).addValueEventListener(listener)
            awaitClose { usersRef.child(userId).removeEventListener(listener) }
        }
    }
}