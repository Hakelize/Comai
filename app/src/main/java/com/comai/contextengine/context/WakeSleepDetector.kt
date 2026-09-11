package com.comai.contextengine.context

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DetectedEventType {
    WAKE,
    SLEEP,
    STILL,
    WALKING,
    IN_VEHICLE,
    UNKNOWN
}

data class DetectedContextEvent(
    val timestampMs: Long,
    val type: DetectedEventType,
    val confidence: Int = 100,
    val details: String = ""
)

class WakeSleepDetector {

    private var currentActivity: DetectedEventType = DetectedEventType.STILL
    private var lastWakeTimeMs: Long = System.currentTimeMillis() - (4 * 3600 * 1000)
    private var lastSleepTimeMs: Long? = null
    private var stillStartTimeMs: Long = System.currentTimeMillis()
    private var accumulatedStillMs: Long = 0

    @Synchronized
    fun onEventDetected(event: DetectedContextEvent) {
        Log.d(TAG, "Processing context event: ${event.type} at ${event.timestampMs}")
        when (event.type) {
            DetectedEventType.WAKE -> {
                lastWakeTimeMs = event.timestampMs
                currentActivity = DetectedEventType.STILL
                stillStartTimeMs = event.timestampMs
                accumulatedStillMs = 0
            }
            DetectedEventType.SLEEP -> {
                lastSleepTimeMs = event.timestampMs
                currentActivity = DetectedEventType.SLEEP
            }
            DetectedEventType.STILL -> {
                if (currentActivity != DetectedEventType.STILL) {
                    stillStartTimeMs = event.timestampMs
                }
                currentActivity = DetectedEventType.STILL
            }
            DetectedEventType.WALKING, DetectedEventType.IN_VEHICLE -> {
                if (currentActivity == DetectedEventType.STILL) {
                    accumulatedStillMs += (event.timestampMs - stillStartTimeMs)
                }
                currentActivity = event.type
            }
            DetectedEventType.UNKNOWN -> {}
        }
    }

    fun getCurrentUserState(currentTimeMs: Long = System.currentTimeMillis()): UserState {
        val awakeDurationMinutes = if (lastWakeTimeMs > 0 && currentTimeMs > lastWakeTimeMs) {
            (currentTimeMs - lastWakeTimeMs) / (1000 * 60)
        } else {
            0L
        }

        var totalStillMs = accumulatedStillMs
        if (currentActivity == DetectedEventType.STILL) {
            totalStillMs += (currentTimeMs - stillStartTimeMs)
        }
        val inactiveHours = totalStillMs.toDouble() / (1000.0 * 3600.0)

        return UserState(
            activityType = currentActivity.name,
            awakeDurationMinutes = awakeDurationMinutes,
            inactiveHours = inactiveHours,
            sleepDetectedTimeMs = lastSleepTimeMs,
            wakeDetectedTimeMs = lastWakeTimeMs
        )
    }

    fun getFormattedWakeTime(timeMs: Long = lastWakeTimeMs): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }

    companion object {
        private const val TAG = "WakeSleepDetector"
    }
}
