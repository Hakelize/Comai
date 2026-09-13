package com.comai.digitalactivity.model

/**
 * Structured model exposing Digital Activity contextual signals to the Context Engine.
 */
data class DigitalActivityContextData(
    val isAvailable: Boolean = false,
    val activityState: String = "UNKNOWN", // e.g. ACTIVE, RECENTLY_ACTIVE, INACTIVE, LIKELY_AWAKE, LIKELY_ASLEEP, UNKNOWN
    val confidence: Double = 0.0,
    val reason: String = "NO_USAGE_PERMISSION",
    val lastActivityTimestampMs: Long = 0L,
    val lastActivityFormatted: String = "",
    val currentOrRecentApp: String? = null,
    val appCategory: String = "OTHER",
    val dailyScreenTimeMinutes: Long = 0L,
    val topUsedApp: String? = null
)
