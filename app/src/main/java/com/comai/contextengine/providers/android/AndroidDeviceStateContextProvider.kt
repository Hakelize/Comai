package com.comai.contextengine.providers.android

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.comai.contextengine.providers.interfaces.DeviceStateContextProvider
import com.comai.contextengine.providers.models.DeviceStateContextData

/**
 * Android implementation for General Device State Context.
 */
class AndroidDeviceStateContextProvider(private val context: Context) : DeviceStateContextProvider {

    override val isAvailable: Boolean = true

    override fun getContextData(): DeviceStateContextData {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

            val isInteractive = pm?.isInteractive ?: true
            val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pm?.isPowerSaveMode ?: false
            } else {
                false
            }
            val isDeviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm?.isDeviceIdleMode ?: false
            } else {
                false
            }

            val isLocked = km?.isKeyguardLocked ?: false

            DeviceStateContextData(
                isScreenOn = isInteractive,
                isPowerSaveMode = isPowerSaveMode,
                isDeviceIdle = isDeviceIdle,
                isKeyguardLocked = isLocked,
                storageState = "NORMAL"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring device state context", e)
            DeviceStateContextData()
        }
    }

    companion object {
        private const val TAG = "AndroidDeviceStateProvider"
    }
}
