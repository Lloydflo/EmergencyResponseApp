package com.ers.emergencyresponseapp

object AppState {
    @Volatile
    var isForeground: Boolean = true
}

object AppScreenTracker {
    @Volatile
    var currentScreen: String = "HOME"

    /** Exact PM/group thread currently visible; used to suppress duplicate alerts. */
    @Volatile
    var currentThreadId: String? = null
}
