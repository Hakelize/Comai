package com.comai.contextengine.providers.composite

import android.content.Context
import com.comai.contextengine.providers.android.AndroidActivityContextProvider
import com.comai.contextengine.providers.android.AndroidAudioContextProvider
import com.comai.contextengine.providers.android.AndroidBatteryContextProvider
import com.comai.contextengine.providers.android.AndroidDeviceStateContextProvider
import com.comai.contextengine.providers.android.AndroidGeofenceContextProvider
import com.comai.contextengine.providers.android.AndroidLocationContextProvider
import com.comai.contextengine.providers.android.AndroidNetworkContextProvider
import com.comai.contextengine.providers.android.AndroidRoutineContextProvider
import com.comai.contextengine.providers.android.AndroidTimeContextProvider
import com.comai.contextengine.providers.interfaces.ActivityContextProvider
import com.comai.contextengine.providers.interfaces.AudioContextProvider
import com.comai.contextengine.providers.interfaces.BatteryContextProvider
import com.comai.contextengine.providers.interfaces.DeviceContextProvider
import com.comai.contextengine.providers.interfaces.DeviceStateContextProvider
import com.comai.contextengine.providers.interfaces.GeofenceContextProvider
import com.comai.contextengine.providers.interfaces.LocationContextProvider
import com.comai.contextengine.providers.interfaces.NetworkContextProvider
import com.comai.contextengine.providers.interfaces.RoutineContextProvider
import com.comai.contextengine.providers.interfaces.TimeContextProvider
import com.comai.contextengine.providers.models.DeviceContext

/**
 * Composite Device Context Provider aggregating all sub-providers to produce a unified,
 * normalized internal [DeviceContext] model. Fully decoupled via provider interfaces.
 */
class CompositeDeviceContextProvider(
    val timeProvider: TimeContextProvider,
    val locationProvider: LocationContextProvider,
    val geofenceProvider: GeofenceContextProvider,
    val activityProvider: ActivityContextProvider,
    val routineProvider: RoutineContextProvider,
    val batteryProvider: BatteryContextProvider,
    val networkProvider: NetworkContextProvider,
    val audioProvider: AudioContextProvider,
    val deviceStateProvider: DeviceStateContextProvider,
    val screenProvider: com.comai.contextengine.providers.interfaces.ScreenContextProvider? = null,
    val notificationProvider: com.comai.contextengine.providers.interfaces.NotificationContextProvider? = null,
    val appProvider: com.comai.contextengine.providers.interfaces.AppContextProvider? = null
) : DeviceContextProvider {

    override val isAvailable: Boolean
        get() = timeProvider.isAvailable

    override fun getContextData(): DeviceContext {
        return DeviceContext(
            time = timeProvider.getContextData(),
            location = locationProvider.getContextData(),
            geofence = geofenceProvider.getContextData(),
            activity = activityProvider.getContextData(),
            routine = routineProvider.getContextData(),
            battery = batteryProvider.getContextData(),
            network = networkProvider.getContextData(),
            audio = audioProvider.getContextData(),
            deviceState = deviceStateProvider.getContextData(),
            screen = screenProvider?.getContextData(),
            notification = notificationProvider?.getContextData(),
            app = appProvider?.getContextData(),
            timestampMs = System.currentTimeMillis()
        )
    }

    companion object {
        /**
         * Factory function constructing standard Android framework provider instances.
         */
        fun createDefault(context: Context): CompositeDeviceContextProvider {
            val time = AndroidTimeContextProvider()
            val location = AndroidLocationContextProvider(context)
            val geofence = AndroidGeofenceContextProvider(location)
            val activity = AndroidActivityContextProvider(context)
            val routine = AndroidRoutineContextProvider()
            val battery = AndroidBatteryContextProvider(context)
            val network = AndroidNetworkContextProvider(context)
            val audio = AndroidAudioContextProvider(context)
            val deviceState = AndroidDeviceStateContextProvider(context)

            return CompositeDeviceContextProvider(
                timeProvider = time,
                locationProvider = location,
                geofenceProvider = geofence,
                activityProvider = activity,
                routineProvider = routine,
                batteryProvider = battery,
                networkProvider = network,
                audioProvider = audio,
                deviceStateProvider = deviceState
            )
        }
    }
}
