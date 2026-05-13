package com.ers.emergencyresponseapp.firebase.model.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.ers.emergencyresponseapp.firebase.model.ResponderProfile
import com.google.firebase.database.FirebaseDatabase

class FirebaseUserRepository {  // ← wrap in a class

    fun observeAllResponders(): Flow<List<ResponderProfile>> = callbackFlow {
        val db = FirebaseDatabase.getInstance().getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val responders = snapshot.children.mapNotNull { child ->
                    try {
                        val fullName = child.child("fullName").getValue(String::class.java)
                        val email = child.child("email").getValue(String::class.java)
                        val department = child.child("department").getValue(String::class.java)

                        // Skip nodes that don't have required fields
                        if (fullName.isNullOrBlank() || email.isNullOrBlank() || department.isNullOrBlank()) {
                            return@mapNotNull null  // ← skips current_user and any bad nodes
                        }

                        ResponderProfile(
                            uid        = child.key ?: "",
                            fullName   = fullName,
                            email      = email,
                            department = department,
                            isOnline   = child.child("isOnline").getValue(Boolean::class.java) ?: false,
                            lastSeen   = child.child("lastSeen").getValue(Long::class.java) ?: 0L,
                            userId     = child.child("userId").getValue(String::class.java) ?: ""
                        )
                    } catch (e: Exception) { null }
                }
                trySend(responders)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        db.addValueEventListener(listener)
        awaitClose { db.removeEventListener(listener) }
    }
}