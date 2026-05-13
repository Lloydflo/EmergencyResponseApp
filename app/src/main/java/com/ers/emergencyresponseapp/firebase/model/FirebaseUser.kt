package com.ers.emergencyresponseapp.firebase.model

data class FirebaseUser(
    val userId     : String  = "",
    val fullName   : String  = "",
    val email      : String  = "",
    val department : String  = "",
    val isOnline   : Boolean = false,  // must match exactly what ViewModel reads
    val lastSeen   : Long    = 0L
)