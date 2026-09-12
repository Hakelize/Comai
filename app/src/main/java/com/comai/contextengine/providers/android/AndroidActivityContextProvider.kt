package com.comai.contextengine.providers.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.comai.contextengine.context.ActivityRecognitionManager
import com.comai.contextengine.providers.interfaces.ActivityContextProvider
import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState

/**
 * Android implementation for Activity / Movement Context.
 * Features:
 * - Version-aware permission handling (API 29+ ACTIVITY_RECOGNITION).
 * - Checks hardware sensor availability safely (never crashes).
 * - Maps movement states (STATIONARY, WALKING, RUNNING, IN_VEHICLE, ON_BICYCLE, UNKNOWN).
 * - Returns confidence ratings and on-device state tracking.
 */
class AndroidActivityContextProvider(
    private val context: Context,
    private val activityManager: ActivityRecognitionManager? = null
) : ActivityContextProvider {

    private var lastState: ActivityMovementState = ActivityMovementState.STATIONARY
    private var lastTransitionTimeMs: Long = System.currentTimeMillis()

    override val isAvailable: Boolean
        get() {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val hasSensors = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            if (!hasSensors) return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

    override fun getContextData(): ActivityContextData {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasSensors = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasSensors) {
            Log.d(TAG, "Hardware sensors for activity recognition unavailable")
            return ActivityContextData(
                detectedState = ActivityMovementState.UNKNOWN,
                activityType = "UNKNOWN",
                confidencePercentage = 0,
                isPermissionGranted = hasPermission,
                isHardwareSupported = false,
                isActivityAvailable = false
            )
        }

        if (!hasPermission) {
            Log.d(TAG, "ACTIVITY_RECOGNITION permission not granted")
            return ActivityContextData(
                detectedState = ActivityMovementState.UNKNOWN,
                activityType = "UNKNOWN",
                confidencePercentage = 0,
                isPermissionGranted = false,
                isHardwareSupported = true,
                isActivityAvailable = false
            )
        }

        return try {
            val userState = activityManager?.getCurrentUserState()
            val rawType = userState?.activityType ?: "STILL"
            
            val state = when (rawType.uppercase()) {
                "STILL", "STATIONARY" -> ActivityMovementState.STATIONARY
                "WALKING" -> ActivityMovementState.WALKING
                "RUNNING" -> ActivityMovementState.RUNNING
                "IN_VEHICLE", "COMMUTE" -> ActivityMovementState.IN_VEHICLE
                "ON_BICYCLE", "CYCLING" -> ActivityMovementState.ON_BICYCLE
                else -> ActivityMovementState.STATIONARY
            }

            val now = System.currentTimeMillis()
            if (state != lastState) {
                lastState = state
                lastTransitionTimeMs = now
            }

            val durationMins = (now - lastTransitionTimeMs) / (1000 * 60)

            ActivityContextData(
                detectedState = state,
                activityType = rawType,
                confidencePercentage = 90,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = true,
                durationInCurrentStateMinutes = durationMins,
                lastStateTransitionTimestampMs = lastTransitionTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring activity context", e)
            ActivityContextData(
                detectedState = ActivityMovementState.UNKNOWN,
                activityType = "UNKNOWN",
                confidencePercentage = 0,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = false
            )
        }
    }

    companion object {
        private const val TAG = "AndroidActivityProvider"
    }
}
