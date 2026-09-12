package com.comai.contextengine.providers.interfaces

import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.AudioContextData
import com.comai.contextengine.providers.models.AudioOutputDeviceType
import com.comai.contextengine.providers.models.BatteryContextData
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.contextengine.providers.models.DeviceStateContextData
import com.comai.contextengine.providers.models.GeofenceContextData
import com.comai.contextengine.providers.models.LocationContextData
import com.comai.contextengine.providers.models.NetworkContextData
import com.comai.contextengine.providers.models.RoutineContextData
import com.comai.contextengine.providers.models.RoutinePlace
import com.comai.contextengine.providers.models.TimeContextData

/**
 * Generic base interface for all device context providers.
 */
interface ContextProvider<T> {
    val isAvailable: Boolean
    fun getContextData(): T
}

interface TimeContextProvider : ContextProvider<TimeContextData>

interface LocationContextProvider : ContextProvider<LocationContextData> {
    val currentLocation: LocationContextData
        get() = getContextData()

    val currentPlace: RoutinePlace?
        get() = getContextData().currentPlace

    val insideGeofence: Boolean
        get() = getContextData().currentPlace != null

    val nearestKnownRoutinePlace: Pair<RoutinePlace, Float>?
        get() {
            val data = getContextData()
            val place = data.nearestKnownRoutinePlace ?: return null
            val dist = data.distanceToNearestRoutinePlaceMeters ?: return null
            return Pair(place, dist)
        }
}

interface GeofenceContextProvider : ContextProvider<GeofenceContextData> {
    val insideGeofence: Boolean
        get() = getContextData().isInsideGeofence

    val currentZone: String
        get() = getContextData().currentZone
}

interface ActivityContextProvider : ContextProvider<ActivityContextData> {
    val currentActivity: ActivityContextData
        get() = getContextData()

    val currentState: ActivityMovementState
        get() = getContextData().detectedState

    val isStationary: Boolean
        get() = currentState == ActivityMovementState.STATIONARY

    val isMoving: Boolean
        get() = currentState == ActivityMovementState.WALKING || currentState == ActivityMovementState.RUNNING || currentState == ActivityMovementState.ON_BICYCLE

    val isCommuting: Boolean
        get() = currentState == ActivityMovementState.IN_VEHICLE
}

interface RoutineContextProvider : ContextProvider<RoutineContextData>

interface BatteryContextProvider : ContextProvider<BatteryContextData> {
    val batteryLevel: Int
        get() = getContextData().batteryLevel

    val isCharging: Boolean
        get() = getContextData().isCharging

    val isLowBattery: Boolean
        get() = getContextData().isLowBattery
}

interface NetworkContextProvider : ContextProvider<NetworkContextData> {
    val isConnected: Boolean
        get() = getContextData().isConnected

    val networkType: String
        get() = getContextData().networkType

    val isWifi: Boolean
        get() = networkType == "WIFI"

    val isCellular: Boolean
        get() = networkType == "CELLULAR"

    val isMetered: Boolean
        get() = getContextData().isMetered
}

interface AudioContextProvider : ContextProvider<AudioContextData> {
    val primaryDevice: AudioOutputDeviceType
        get() = getContextData().primaryOutputDevice

    val isHeadphonesConnected: Boolean
        get() = getContextData().isHeadphonesPlugged

    val isSilentMode: Boolean
        get() = getContextData().ringerMode == "SILENT" || getContextData().ringerMode == "VIBRATE"

    val isMusicPlaying: Boolean
        get() = getContextData().isMusicActive
}

interface DeviceStateContextProvider : ContextProvider<DeviceStateContextData> {
    val isScreenInteractive: Boolean
        get() = getContextData().isScreenOn

    val isDeviceDoze: Boolean
        get() = getContextData().isDeviceIdle

    val isPowerSave: Boolean
        get() = getContextData().isPowerSaveMode
}

@com.comai.contextengine.providers.annotations.OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
interface ScreenContextProvider : ContextProvider<com.comai.contextengine.providers.models.ScreenContextData>

@com.comai.contextengine.providers.annotations.OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
interface NotificationContextProvider : ContextProvider<com.comai.contextengine.providers.models.NotificationContextData>

@com.comai.contextengine.providers.annotations.OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
interface AppContextProvider : ContextProvider<com.comai.contextengine.providers.models.AppContextData>

interface DeviceContextProvider : ContextProvider<DeviceContext>
