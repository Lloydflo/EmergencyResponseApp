package com.ers.emergencyresponseapp

/**
 * Lightweight process state used only to avoid showing a duplicate notification
 * while the responder is already looking at the exact destination. The defaults
 * must represent a background/cold process because Firebase may start the
 * messaging service without creating MainActivity.
 */
object AppState {
    @Volatile
    var isForeground: Boolean = false
}

object AppScreenTracker {
    @Volatile
    var currentScreen: String = "NONE"

    /** Exact PM/group thread currently visible; used to suppress duplicate alerts. */
    @Volatile
    var currentThreadId: String? = null
}
