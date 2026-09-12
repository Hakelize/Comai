package com.comai.contextengine.context

import com.comai.contextengine.data.RoutineProfile
import kotlin.math.abs

/**
 * Analyzes current user signals against a RoutineProfile to calculate deviation severity.
 */
class RoutineDeviationAnalyzer {

    private val normalizer = ContextNormalizer()

    /**
     * Calculates routine deviation score between 0.0 (perfect match) and 1.0 (extreme anomaly).
     */
    fun analyzeDeviation(
        currentTimeStr: String,
        userActivity: String,
        locationCategory: String,
        routineProfile: RoutineProfile,
        isAwake: Boolean
    ): Double {
        val currentMinutes = normalizer.parseTimeToMinutes(currentTimeStr)
        var deviation = 0.0

        // Check wake time deviation
        if (isAwake) {
            val wakeDiff = currentMinutes - routineProfile.expectedWakeTimeMinutes
            if (wakeDiff > 30) {
                // User woke up >30 mins after expected wake time
                deviation += (wakeDiff - 30) * 0.005
            }
        }

        // Check non-workday commute anomaly
        if (!routineProfile.isWorkDay && (locationCategory == "Office" || userActivity == "IN_VEHICLE")) {
            deviation += 0.4
        }

        // Check lunch break gap (no break taken during lunch window 12:30..14:00)
        if (currentMinutes in 780..840 && userActivity == "STILL" && locationCategory == "Office") {
            deviation += 0.2
        }

        // Check late office departure (still at office past expected departure)
        if (routineProfile.isWorkDay && currentMinutes > (routineProfile.expectedOfficeDepartureMinutes + 60) && locationCategory == "Office") {
            deviation += 0.35
        }

        return deviation.coerceIn(0.0, 1.0)
    }
}
