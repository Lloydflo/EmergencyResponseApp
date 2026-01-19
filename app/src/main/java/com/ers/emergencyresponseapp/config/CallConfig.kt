package com.ers.emergencyresponseapp.config

// Centralized configuration for agency mapping and hotline numbers.
// Keeps numbers and labels in one place for easier updates and localization.

data class AgencyInfo(
    val key: String,
    val displayTitle: String,
    val agencyName: String,
    val hotlineDisplay: String,
    val dialNumber: String,
    val emoji: String,
    val callButtonLabel: String,
    val role: String? = null
)

object CallConfig {
    // Returns AgencyInfo based on a case-insensitive incident type key.
    fun agencyForKey(keyRaw: String?): AgencyInfo {
        val key = keyRaw?.lowercase()?.trim() ?: "unknown"
        return when (key) {
            "fire", "f" -> AgencyInfo(
                key = "fire",
                displayTitle = "FIRE",
                agencyName = "City Fire Department",
                hotlineDisplay = "911 / 160",
                // Use primary emergency number for ACTION_DIAL
                dialNumber = "911",
                emoji = "🚒",
                callButtonLabel = "FIRE DEPARTMENT",
                role = "PRIMARY"
            )
            "medical", "med" -> AgencyInfo(
                key = "medical",
                displayTitle = "MEDICAL",
                agencyName = "Medical Department",
                hotlineDisplay = "911 / 155",
                dialNumber = "911",
                emoji = "🚑",
                callButtonLabel = "MEDICAL",
                role = "SUPPORT"
            )
            "crime", "police", "criminal" -> AgencyInfo(
                key = "crime",
                displayTitle = "CRIME",
                agencyName = "Police Department",
                hotlineDisplay = "911 / 117",
                dialNumber = "911",
                emoji = "👮",
                callButtonLabel = "POLICE",
                role = "SUPPORT"
            )
            "disaster", "dm" -> AgencyInfo(
                key = "disaster",
                displayTitle = "DISASTER",
                agencyName = "Disaster Management",
                hotlineDisplay = "911 / 161",
                dialNumber = "911",
                emoji = "🌪️",
                callButtonLabel = "DISASTER",
                role = "SUPPORT"
            )
            else -> AgencyInfo(
                key = "unknown",
                displayTitle = (keyRaw ?: "UNKNOWN").uppercase(),
                agencyName = "Emergency Services",
                hotlineDisplay = "911",
                dialNumber = "911",
                emoji = "❗",
                callButtonLabel = "EMERGENCY",
                role = "PRIMARY"
            )
        }
    }
}

