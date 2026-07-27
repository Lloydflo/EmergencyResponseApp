package com.ers.emergencyresponseapp.firebase.repository

import com.ers.emergencyresponseapp.firebase.model.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Firebase responder-directory and presence operations.
 *
 * Private messages are handled by CoordinationViewModel using the single
 * /threads + /messages schema. Keeping this repository presence-only prevents
 * the app from writing a second, incompatible /chats + /userChats schema.
 */
class FirebaseChatRepository {
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")

    suspend fun saveUserToFirebase(
        userId: String,
        fullName: String,
        email: String,
        department: String
    ) {
        require(userId.isNotBlank()) { "Missing responder ID" }

        val user = FirebaseUser(
            userId = userId,
            fullName = fullName.trim(),
            email = email.trim(),
            department = department.trim().lowercase(),
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )
        usersRef.child(userId).setValue(user).await()
        usersRef.child(userId).child("isOnline").onDisconnect().setValue(false)
        usersRef.child(userId).child("lastSeen").onDisconnect()
            .setValue(System.currentTimeMillis())
    }

    suspend fun setOnlineStatus(userId: String, isOnline: Boolean) {
        if (userId.isBlank()) return
        usersRef.child(userId).updateChildren(
            mapOf(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis(),
                // Remove the old duplicate property if it exists.
                "online" to null
            )
        ).await()
    }
}
