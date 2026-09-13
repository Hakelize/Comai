package com.comai.digitalactivity.intelligence

import com.comai.digitalactivity.model.DailyUsageSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Internal states inferred from device digital activity signals.
 */
enum class DeviceActivityState(val displayName: String) {
    ACTIVE("Active"),
    RECENTLY_ACTIVE("Recently Active"),
    INACTIVE("Inactive"),
    LIKELY_AWAKE("Likely Awake"),
    LIKELY_ASLEEP("Likely Asleep"),
    UNKNOWN("Unknown")
}

/**
 * Result of the activity inference evaluation.
 */
data class ActivityInferenceResult(
    val state: DeviceActivityState,
    val confidence: Double, // 0.0 to 1.0
    val reason: String,
    val lastActivityTimestampMs: Long,
    val lastActivityFormatted: String
)

/**
 * ActivityInferenceEngine: Combines current time, user's configured schedule,
 * inactivity duration, recent device activity, and historical patterns to infer
 * the user's daily state with explainable confidence.
 *
 * IMPORTANT: Inactivity does NOT claim medical sleep detection. Inferences are
 * strictly probabilistic contextual signals.
 */
object ActivityInferenceEngine {

    fun inferState(
        nowMs: Long = System.currentTimeMillis(),
        snapshot: DailyUsageSnapshot,
        configuredWakeHour: Int = 7,
        configuredWakeMinute: Int = 0,
        configuredSleepHour: Int = 23,
        configuredSleepMinute: Int = 0,
        isMonitoringEnabled: Boolean = true,
        hasPermission: Boolean = true
    ): ActivityInferenceResult {
        if (!isMonitoringEnabled) {
            return ActivityInferenceResult(
                state = DeviceActivityState.UNKNOWN,
                confidence = 0.0,
                reason = "MONITORING_DISABLED",
                lastActivityTimestampMs = 0L,
                lastActivityFormatted = "None"
            )
        }

        if (!hasPermission) {
            return ActivityInferenceResult(
                state = DeviceActivityState.UNKNOWN,
                confidence = 0.0,
                reason = "NO_USAGE_PERMISSION",
                lastActivityTimestampMs = 0L,
                lastActivityFormatted = "None"
            )
        }

        val lastActiveMs = snapshot.lastActiveTimeMs
        if (lastActiveMs <= 0L) {
            return ActivityInferenceResult(
                state = DeviceActivityState.UNKNOWN,
                confidence = 0.0,
                reason = "NO_ACTIVITY_RECORDED",
                lastActivityTimestampMs = 0L,
                lastActivityFormatted = "None"
            )
        }

        val elapsedMinutes = ((nowMs - lastActiveMs) / (1000 * 60)).coerceAtLeast(0L)
        val formattedLastActive = formatTime(lastActiveMs)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        val configuredWakeMinutes = configuredWakeHour * 60 + configuredWakeMinute
        val configuredSleepMinutes = configuredSleepHour * 60 + configuredSleepMinute

        // 1. Device is actively being used right now
        if (elapsedMinutes <= 5) {
            return ActivityInferenceResult(
                state = DeviceActivityState.ACTIVE,
                confidence = 0.95,
                reason = "RECENT_DEVICE_ACTIVITY",
                lastActivityTimestampMs = lastActiveMs,
                lastActivityFormatted = formattedLastActive
            )
        }

        // 2. Device was used within the last 20 minutes
        if (elapsedMinutes <= 20) {
            return ActivityInferenceResult(
                state = DeviceActivityState.RECENTLY_ACTIVE,
                confidence = 0.85,
                reason = "ACTIVITY_WITHIN_LAST_20_MINUTES",
                lastActivityTimestampMs = lastActiveMs,
                lastActivityFormatted = formattedLastActive
            )
        }

        // 3. Morning Awake Inference:
        // Current time is between 5:00 AM and 11:00 AM, and user had device interaction since midnight
        val isMorningWindow = currentHour in 5..11
        if (isMorningWindow && snapshot.firstActiveTimeMs > 0L) {
            val minutesSinceFirstActive = (nowMs - snapshot.firstActiveTimeMs) / (1000 * 60)
            if (minutesSinceFirstActive >= 0 && elapsedMinutes <= 90) {
                // If the activity occurred around or after configured wake time, high confidence
                val diffFromWake = Math.abs(currentTotalMinutes - configuredWakeMinutes)
                val wakeConfidence = if (diffFromWake <= 60) 0.88 else 0.78
                return ActivityInferenceResult(
                    state = DeviceActivityState.LIKELY_AWAKE,
                    confidence = wakeConfidence,
                    reason = "FIRST_MORNING_ACTIVITY",
                    lastActivityTimestampMs = lastActiveMs,
                    lastActivityFormatted = formattedLastActive
                )
            }
        }

        // 4. Night Sleep Inference:
        // Current time is late night (either >= 22:00 or < 05:00) and user has been inactive for >= 45 minutes
        val isNightWindow = currentHour >= 22 || currentHour < 5
        if (isNightWindow && elapsedMinutes >= 45) {
            val sleepConfidence = when {
                elapsedMinutes >= 120 -> 0.89
                elapsedMinutes >= 60 -> 0.82
                else -> 0.74
            }
            return ActivityInferenceResult(
                state = DeviceActivityState.LIKELY_ASLEEP,
                confidence = sleepConfidence,
                reason = "PROLONGED_NIGHT_INACTIVITY",
                lastActivityTimestampMs = lastActiveMs,
                lastActivityFormatted = formattedLastActive
            )
        }

        // 5. Normal daytime inactivity
        return ActivityInferenceResult(
            state = DeviceActivityState.INACTIVE,
            confidence = 0.72,
            reason = if (elapsedMinutes >= 60) "PROLONGED_DAY_INACTIVITY" else "DEVICE_INACTIVE",
            lastActivityTimestampMs = lastActiveMs,
            lastActivityFormatted = formattedLastActive
        )
    }

    private fun formatTime(timeMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }
}
