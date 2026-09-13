package com.comai.nearby

/**
 * Structured model representing location and local environment context.
 * Used by Context Engine and Memory UI.
 */
data class NearbyContext(
    val location: LocationState = LocationState(),
    val traffic: NearbyTraffic = NearbyTraffic(),
    val news: List<NearbyNewsItem> = emptyList(),
    val events: List<NearbyEventItem> = emptyList(),
    val contextualInsight: String? = null,
    val isOffline: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Local Context Engine"
)

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String = "",
    val locality: String = "",
    val accuracyMeters: Float? = null,
    val isPermissionGranted: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    val isPermanentlyDenied: Boolean = false,
    val hasFix: Boolean = false,
    val isLiveFix: Boolean = false
)

enum class TrafficLevel {
    NORMAL,
    MODERATE,
    HEAVY,
    INCIDENT
}

data class NearbyTraffic(
    val level: TrafficLevel = TrafficLevel.NORMAL,
    val summary: String = "",
    val delayMinutes: Int = 0,
    val incidentDescription: String? = null
)

data class NearbyNewsItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String, // "DISRUPTION", "INCIDENT", "CIVIC"
    val locality: String,
    val timestamp: Long
)

data class NearbyEventItem(
    val id: String,
    val title: String,
    val locationName: String,
    val timeDesc: String,
    val category: String // "COMMUNITY", "EXHIBITION", "GATHERING", "TRAFFIC_ALERT"
)
