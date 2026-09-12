package com.comai.contextengine.providers.models

/**
 * Structured data for Time Context.
 */
data class TimeContextData(
    val hour: Int,
    val minute: Int,
    val dayOfWeek: String, // e.g. "MONDAY"
    val isWorkDay: Boolean,
    val formattedTime: String // e.g. "09:30"
)

/**
 * Availability state enumeration for Location Context.
 */
enum class LocationAvailabilityState {
    AVAILABLE,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    NO_FIX_AVAILABLE,
    MOCK_ACTIVE
}

/**
 * Representation of a user routine place (Home, Office, Gym, etc.).
 */
data class RoutinePlace(
    val id: String,
    val name: String,
    val category: String, // "HOME", "OFFICE", "GYM", "ROUTINE_OTHER"
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f
)

/**
 * Structured data for Location Context.
 */
data class LocationContextData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCategory: String = "UNKNOWN", // e.g. "Home", "Office", "Commute", "UNKNOWN"
    val accuracyMeters: Float = 0f,
    val isPermissionGranted: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val availabilityState: LocationAvailabilityState = LocationAvailabilityState.PERMISSION_DENIED,
    val currentPlace: RoutinePlace? = null,
    val nearestKnownRoutinePlace: RoutinePlace? = null,
    val distanceToNearestRoutinePlaceMeters: Float? = null
)

/**
 * Structured data for Geofence Context.
 */
data class GeofenceContextData(
    val currentZone: String = "UNKNOWN", // e.g. "HOME_ZONE", "OFFICE_ZONE", "UNKNOWN"
    val isInsideGeofence: Boolean = false,
    val currentPlace: RoutinePlace? = null,
    val distanceToNearestZoneMeters: Float? = null
)

/**
 * Enum categorizing movement & activity state.
 */
enum class ActivityMovementState {
    STATIONARY,
    WALKING,
    RUNNING,
    IN_VEHICLE,
    ON_BICYCLE,
    UNKNOWN
}

/**
 * Structured data for Activity / Movement Context.
 */
data class ActivityContextData(
    val detectedState: ActivityMovementState = ActivityMovementState.STATIONARY,
    val activityType: String = "STILL", // e.g. "STILL", "WALKING", "RUNNING", "IN_VEHICLE", "ON_BICYCLE"
    val confidencePercentage: Int = 100,
    val isPermissionGranted: Boolean = false,
    val isHardwareSupported: Boolean = true,
    val isActivityAvailable: Boolean = true,
    val durationInCurrentStateMinutes: Long = 0,
    val lastStateTransitionTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Structured data for Routine Context.
 */
data class RoutineContextData(
    val routineState: String = "NORMAL", // e.g. "MORNING_WAKE", "COMMUTE", "WORK", "LUNCH", "EVENING_REST", "SLEEP"
    val routineDeviationScore: Double = 0.0, // 0.0 (normal) to 1.0 (anomalous)
    val isRoutineDeviation: Boolean = false,
    val deviationMinutes: Int = 0,
    val confidence: Float = 1.0f
)

/**
 * Structured data for Battery & Power Context.
 */
data class BatteryContextData(
    val batteryLevel: Int = -1, // 0 to 100
    val isCharging: Boolean = false,
    val plugType: String = "NONE", // e.g. "USB", "AC", "WIRELESS", "NONE"
    val batteryHealth: String = "GOOD", // e.g. "GOOD", "OVERHEAT", "DEAD", "UNKNOWN"
    val isLowBattery: Boolean = false, // batteryLevel < 15
    val isPowerSaverMode: Boolean = false
)

/**
 * Structured data for Network Context.
 */
data class NetworkContextData(
    val networkType: String = "UNKNOWN", // e.g. "WIFI", "CELLULAR", "ETHERNET", "OFFLINE"
    val isConnected: Boolean = true,
    val isMetered: Boolean = false,
    val isVpnConnected: Boolean = false,
    val detailedType: String = "UNKNOWN"
)

/**
 * Enum categorizing primary audio output destination.
 */
enum class AudioOutputDeviceType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH_AUDIO,
    OTHER,
    UNKNOWN,
    NONE
}

/**
 * Structured data for Headphones / Audio Output Context.
 */
data class AudioContextData(
    val isOutputAvailable: Boolean = true,
    val primaryOutputDevice: AudioOutputDeviceType = AudioOutputDeviceType.SPEAKER,
    val isSpeakerActive: Boolean = true,
    val isWiredHeadphonesConnected: Boolean = false,
    val isBluetoothAudioConnected: Boolean = false,
    val isOtherOutputConnected: Boolean = false,
    val isHeadphonesPlugged: Boolean = false, // (isWiredHeadphonesConnected || isBluetoothAudioConnected)
    val ringerMode: String = "NORMAL", // e.g. "NORMAL", "SILENT", "VIBRATE", "UNKNOWN"
    val isMusicActive: Boolean = false,
    val activeOutputDeviceName: String = "Speaker"
)

/**
 * Structured data for Device State Context.
 */
data class DeviceStateContextData(
    val isScreenOn: Boolean = true,
    val isPowerSaveMode: Boolean = false,
    val isDeviceIdle: Boolean = false,
    val isKeyguardLocked: Boolean = false,
    val storageState: String = "NORMAL" // e.g. "NORMAL", "LOW"
)

/**
 * Unified, normalized internal DeviceContext model combining all collected provider signals.
 */
data class DeviceContext(
    val time: TimeContextData,
    val location: LocationContextData,
    val geofence: GeofenceContextData,
    val activity: ActivityContextData,
    val routine: RoutineContextData,
    val battery: BatteryContextData,
    val network: NetworkContextData,
    val audio: AudioContextData,
    val deviceState: DeviceStateContextData,
    val screen: ScreenContextData? = null,
    val notification: NotificationContextData? = null,
    val app: AppContextData? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
