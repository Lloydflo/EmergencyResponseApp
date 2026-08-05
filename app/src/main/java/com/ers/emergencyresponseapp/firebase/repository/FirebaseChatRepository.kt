package com.ers.emergencyresponseapp.firebase.repository

import com.ers.emergencyresponseapp.firebase.model.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await

/**
 * Firebase responder-directory and presence operations.
 *
 * Private messages are handled by CoordinationViewModel using the single
 * /threads + /messages schema. Keeping this repository presence-only prevents
 * the app from writing a second, incompatible /chats + /userChats schema.
 *
 * `isOnline` represents dispatch reachability, not whether MainActivity is
 * currently visible. A background lease is therefore allowed to stay online.
 * `onlineUntil` lets clients hide stale presence even if Android kills the
 * process before its timeout worker can perform the final write.
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

        val now = System.currentTimeMillis()
        val user = FirebaseUser(
            userId = userId,
            fullName = fullName.trim(),
            email = email.trim(),
            department = department.trim().lowercase(),
            isOnline = true,
            lastSeen = now
        )
        val userRef = usersRef.child(userId)
        userRef.setValue(user).await()
        userRef.updateChildren(
            mapOf(
                "appState" to "foreground",
                // Short bootstrap lease; ResponderPresenceManager renews this
                // immediately after OTP and every five minutes in foreground.
                "onlineUntil" to (now + 12L * 60L * 1_000L),
                "online" to null
            )
        ).await()
        configureDisconnectMetadata(userId)
    }

    suspend fun setOnlineStatus(userId: String, isOnline: Boolean) {
        setPresenceState(
            userId = userId,
            isOnline = isOnline,
            appState = if (isOnline) "foreground" else "offline",
            onlineUntil = 0L
        )
    }

    suspend fun setPresenceState(
        userId: String,
        isOnline: Boolean,
        appState: String,
        onlineUntil: Long
    ) {
        if (userId.isBlank()) return
        val userRef = usersRef.child(userId)
        userRef.updateChildren(
            mapOf(
                "isOnline" to isOnline,
                "lastSeen" to ServerValue.TIMESTAMP,
                "appState" to appState.trim().ifBlank {
                    if (isOnline) "foreground" else "offline"
                },
                "onlineUntil" to onlineUntil.coerceAtLeast(0L),
                // Remove the old duplicate property if it exists.
                "online" to null
            )
        ).await()
        configureDisconnectMetadata(userId)
    }

    /**
     * A Firebase socket disconnect no longer makes the responder immediately
     * unavailable. Android can suspend the socket seconds after Home/Back even
     * though FCM remains reachable. Only metadata is changed here; the central
     * one-hour presence policy owns the `isOnline=false` transition.
     */
    private suspend fun configureDisconnectMetadata(userId: String) {
        val userRef = usersRef.child(userId)
        userRef.child("lastSeen").onDisconnect()
            .setValue(ServerValue.TIMESTAMP)
            .await()
        userRef.child("appState").onDisconnect()
            .setValue("disconnected")
            .await()
    }
}
