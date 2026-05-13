package com.ers.emergencyresponseapp.firebase.model

data class ResponderProfile(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val department: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val userId: String = ""
)