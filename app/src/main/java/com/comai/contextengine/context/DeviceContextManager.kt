package com.comai.contextengine.context

import android.content.Context
import com.comai.contextengine.data.DeviceContextSnapshot
import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.interfaces.DeviceContextProvider
import com.comai.contextengine.providers.models.DeviceContext

/**
 * Manages physical and system context acquisition using decoupled provider interfaces.
 */
class DeviceContextManager(
    private val context: Context,
    val provider: DeviceContextProvider = CompositeDeviceContextProvider.createDefault(context)
) {

    /**
     * Retrieves the normalized structured [DeviceContext] model.
     */
    fun getDeviceContext(): DeviceContext {
        return provider.getContextData()
    }

    /**
     * Captures a lightweight snapshot of device context for backward compatibility.
     */
    fun captureSnapshot(): DeviceContextSnapshot {
        val dc = getDeviceContext()
        return DeviceContextSnapshot(
            batteryLevel = dc.battery.batteryLevel,
            isCharging = dc.battery.isCharging,
            networkType = dc.network.networkType,
            isScreenOn = dc.deviceState.isScreenOn,
            audioMode = dc.audio.ringerMode,
            timestampMs = dc.timestampMs,
            freshnessScore = 1.0f
        )
    }
}
