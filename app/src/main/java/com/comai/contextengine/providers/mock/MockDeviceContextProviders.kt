package com.comai.contextengine.providers.mock

import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.interfaces.ActivityContextProvider
import com.comai.contextengine.providers.interfaces.AudioContextProvider
import com.comai.contextengine.providers.interfaces.BatteryContextProvider
import com.comai.contextengine.providers.interfaces.DeviceStateContextProvider
import com.comai.contextengine.providers.interfaces.GeofenceContextProvider
import com.comai.contextengine.providers.interfaces.LocationContextProvider
import com.comai.contextengine.providers.interfaces.NetworkContextProvider
import com.comai.contextengine.providers.interfaces.RoutineContextProvider
import com.comai.contextengine.providers.interfaces.TimeContextProvider
import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.AudioContextData
import com.comai.contextengine.providers.models.BatteryContextData
import com.comai.contextengine.providers.models.DeviceStateContextData
import com.comai.contextengine.providers.models.GeofenceContextData
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData
import com.comai.contextengine.providers.models.NetworkContextData
import com.comai.contextengine.providers.models.RoutineContextData
import com.comai.contextengine.providers.models.RoutinePlace
import com.comai.contextengine.providers.models.TimeContextData

class MockTimeContextProvider(var data: TimeContextData = TimeContextData(9, 0, "MONDAY", true, "09:00")) : TimeContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): TimeContextData = data
}

class MockLocationContextProvider(
    var data: LocationContextData = LocationContextData(
        latitude = 37.7749,
        longitude = -122.4194,
        locationCategory = "Home",
        accuracyMeters = 5f,
        isPermissionGranted = true,
        isLocationEnabled = true,
        availabilityState = LocationAvailabilityState.AVAILABLE,
        currentPlace = RoutinePlace("home_place", "Home", "HOME", 37.7749, -122.4194, 200f)
    )
) : LocationContextProvider {
    override val isAvailable: Boolean
        get() = data.availabilityState == LocationAvailabilityState.AVAILABLE || data.availabilityState == LocationAvailabilityState.MOCK_ACTIVE

    override fun getContextData(): LocationContextData = data

    fun simulatePermissionDenied() {
        data = LocationContextData(
            isPermissionGranted = false,
            isLocationEnabled = false,
            availabilityState = LocationAvailabilityState.PERMISSION_DENIED,
            locationCategory = "UNKNOWN"
        )
    }

    fun simulateLocationDisabled() {
        data = LocationContextData(
            isPermissionGranted = true,
            isLocationEnabled = false,
            availabilityState = LocationAvailabilityState.LOCATION_DISABLED,
            locationCategory = "UNKNOWN"
        )
    }
}

class MockGeofenceContextProvider(
    var data: GeofenceContextData = GeofenceContextData(
        currentZone = "HOME_ZONE",
        isInsideGeofence = true,
        currentPlace = RoutinePlace("home_place", "Home", "HOME", 37.7749, -122.4194, 200f),
        distanceToNearestZoneMeters = 0f
    )
) : GeofenceContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): GeofenceContextData = data
}

class MockActivityContextProvider(
    var data: ActivityContextData = ActivityContextData(
        detectedState = ActivityMovementState.STATIONARY,
        activityType = "STILL",
        confidencePercentage = 100,
        isPermissionGranted = true,
        isHardwareSupported = true,
        isActivityAvailable = true
    )
) : ActivityContextProvider {
    override val isAvailable: Boolean
        get() = data.isActivityAvailable

    override fun getContextData(): ActivityContextData = data

    fun simulatePermissionDenied() {
        data = ActivityContextData(
            detectedState = ActivityMovementState.UNKNOWN,
            activityType = "UNKNOWN",
            confidencePercentage = 0,
            isPermissionGranted = false,
            isHardwareSupported = true,
            isActivityAvailable = false
        )
    }

    fun simulateStateChange(state: ActivityMovementState, confidence: Int = 95) {
        val typeStr = when (state) {
            ActivityMovementState.STATIONARY -> "STILL"
            ActivityMovementState.WALKING -> "WALKING"
            ActivityMovementState.RUNNING -> "RUNNING"
            ActivityMovementState.IN_VEHICLE -> "IN_VEHICLE"
            ActivityMovementState.ON_BICYCLE -> "ON_BICYCLE"
            ActivityMovementState.UNKNOWN -> "UNKNOWN"
        }
        data = data.copy(
            detectedState = state,
            activityType = typeStr,
            confidencePercentage = confidence,
            isActivityAvailable = true,
            lastStateTransitionTimestampMs = System.currentTimeMillis()
        )
    }
}

class MockRoutineContextProvider(var data: RoutineContextData = RoutineContextData("WORK_DAY_ROUTINE", 0.0, false)) : RoutineContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): RoutineContextData = data
}

class MockBatteryContextProvider(var data: BatteryContextData = BatteryContextData(85, false, "NONE")) : BatteryContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): BatteryContextData = data
}

class MockNetworkContextProvider(var data: NetworkContextData = NetworkContextData("WIFI", true, false)) : NetworkContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): NetworkContextData = data
}

class MockAudioContextProvider(var data: AudioContextData = AudioContextData()) : AudioContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): AudioContextData = data
}

class MockDeviceStateContextProvider(var data: DeviceStateContextData = DeviceStateContextData(true, false, false)) : DeviceStateContextProvider {
    override val isAvailable: Boolean = true
    override fun getContextData(): DeviceStateContextData = data
}

class MockScreenContextProvider(
    var data: com.comai.contextengine.providers.models.ScreenContextData = com.comai.contextengine.providers.models.ScreenContextData(
        isAvailable = true,
        isScreenOn = true,
        activeAppPackageName = "com.example.app",
        activeAppCategory = "PRODUCTIVITY"
    )
) : com.comai.contextengine.providers.interfaces.ScreenContextProvider {
    override val isAvailable: Boolean get() = data.isAvailable
    override fun getContextData(): com.comai.contextengine.providers.models.ScreenContextData = data
}

class MockNotificationContextProvider(
    var data: com.comai.contextengine.providers.models.NotificationContextData = com.comai.contextengine.providers.models.NotificationContextData(
        isAvailable = true,
        hasNotificationListenerPermission = true,
        activeNotificationCount = 2,
        hasUnreadMessagingNotification = true
    )
) : com.comai.contextengine.providers.interfaces.NotificationContextProvider {
    override val isAvailable: Boolean get() = data.isAvailable
    override fun getContextData(): com.comai.contextengine.providers.models.NotificationContextData = data
}

class MockAppContextProvider(
    var data: com.comai.contextengine.providers.models.AppContextData = com.comai.contextengine.providers.models.AppContextData(
        isAvailable = true,
        hasUsageStatsPermission = true,
        foregroundPackageName = "com.example.app",
        appUsageCategory = "PRODUCTIVITY",
        sessionDurationMinutes = 15
    )
) : com.comai.contextengine.providers.interfaces.AppContextProvider {
    override val isAvailable: Boolean get() = data.isAvailable
    override fun getContextData(): com.comai.contextengine.providers.models.AppContextData = data
}

object MockProviderFactory {
    fun createMockComposite(
        time: TimeContextData = TimeContextData(9, 0, "MONDAY", true, "09:00"),
        location: LocationContextData = LocationContextData(
            latitude = 37.7749,
            longitude = -122.4194,
            locationCategory = "Home",
            accuracyMeters = 5f,
            isPermissionGranted = true,
            isLocationEnabled = true,
            availabilityState = LocationAvailabilityState.AVAILABLE
        ),
        geofence: GeofenceContextData = GeofenceContextData("HOME_ZONE", true, null, 0f),
        activity: ActivityContextData = ActivityContextData(
            detectedState = ActivityMovementState.STATIONARY,
            activityType = "STILL",
            confidencePercentage = 100,
            isPermissionGranted = true,
            isHardwareSupported = true,
            isActivityAvailable = true
        ),
        routine: RoutineContextData = RoutineContextData("WORK_DAY_ROUTINE", 0.0, false),
        battery: BatteryContextData = BatteryContextData(85, false, "NONE"),
        network: NetworkContextData = NetworkContextData("WIFI", true, false),
        audio: AudioContextData = AudioContextData(),
        deviceState: DeviceStateContextData = DeviceStateContextData(true, false, false),
        screen: com.comai.contextengine.providers.models.ScreenContextData? = null,
        notification: com.comai.contextengine.providers.models.NotificationContextData? = null,
        app: com.comai.contextengine.providers.models.AppContextData? = null
    ): CompositeDeviceContextProvider {
        return CompositeDeviceContextProvider(
            timeProvider = MockTimeContextProvider(time),
            locationProvider = MockLocationContextProvider(location),
            geofenceProvider = MockGeofenceContextProvider(geofence),
            activityProvider = MockActivityContextProvider(activity),
            routineProvider = MockRoutineContextProvider(routine),
            batteryProvider = MockBatteryContextProvider(battery),
            networkProvider = MockNetworkContextProvider(network),
            audioProvider = MockAudioContextProvider(audio),
            deviceStateProvider = MockDeviceStateContextProvider(deviceState),
            screenProvider = screen?.let { MockScreenContextProvider(it) },
            notificationProvider = notification?.let { MockNotificationContextProvider(it) },
            appProvider = app?.let { MockAppContextProvider(it) }
        )
    }
}
