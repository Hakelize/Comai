package com.comai.capability

enum class CapabilityType(val displayName: String) {
    PLATFORM_HARDWARE("Platform / Hardware"),
    RUNTIME_PERMISSION("Runtime Permission"),
    SPECIAL_ACCESS("Special User-Granted Access"),
    SYSTEM_RESTRICTION("Restricted Android Behavior")
}

enum class CapabilityStatus {
    AVAILABLE,
    GRANTED,
    NOT_GRANTED,
    UNSUPPORTED,
    RESTRICTED
}

data class DeviceCapability(
    val name: String,                  // Capability
    val permissionOrAccess: String,    // Permission / Access
    val type: CapabilityType,          // Category
    val status: CapabilityStatus,      // Status
    val details: String                // Details
)

data class DeviceInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val totalRam: String,
    val availableRam: String
)
