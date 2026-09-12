package com.comai.contextengine.data

/**
 * Snapshot of physical and system device context state gathered locally.
 */
data class DeviceContextSnapshot(
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val networkType: String = "UNKNOWN",
    val isScreenOn: Boolean = true,
    val audioMode: String = "NORMAL",
    val timestampMs: Long = System.currentTimeMillis(),
    val freshnessScore: Float = 1.0f
)
