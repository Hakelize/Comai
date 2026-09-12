package com.comai.contextengine.context

import android.util.Log
import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.models.ContextConfidence
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.contextengine.providers.models.LocationAvailabilityState

/**
 * Transparent Context Confidence Evaluator for Person 1's Context Engine.
 * Evaluates signal quality, freshness, missing permissions, geofence matches, and signal conflicts.
 *
 * Scoring system rules:
 * - Base Score: 1.00 (100%)
 * - Location missing / permission denied: -0.25
 * - Activity missing / permission denied: -0.20
 * - Stale signal (> 5 mins old): -0.20, (> 15 mins old): -0.35
 * - Geofence / Location mismatch: -0.20
 * - Activity / Location stationary conflict: -0.15
 * - Routine anomaly score >= 0.5: -0.15
 *
 * Level rules:
 * - HIGH: score >= 0.85 AND no missing major permissions AND no signal conflicts.
 * - MEDIUM: 0.60 <= score < 0.85
 * - LOW: score < 0.60
 *
 * Major signal missing or conflicting signals cap confidenceLevel to MEDIUM or LOW!
 */
class ContextConfidenceEvaluator {

    /**
     * Evaluates context confidence from a normalized [DeviceContext] and optional [RoutineContext].
     */
    fun evaluateConfidence(
        deviceContext: DeviceContext,
        routineContext: RoutineContext? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ContextConfidence {
        var score = 1.0f
        val supporting = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val conflicting = mutableListOf<String>()

        val location = deviceContext.location
        val geofence = deviceContext.geofence
        val activity = deviceContext.activity

        // 1. GPS & Location Signal Evaluation
        if (!location.isPermissionGranted) {
            score -= 0.25f
            missing.add("Location permission denied")
        } else if (location.availabilityState == LocationAvailabilityState.LOCATION_DISABLED ||
            location.availabilityState == LocationAvailabilityState.NO_FIX_AVAILABLE ||
            location.latitude == null
        ) {
            score -= 0.20f
            missing.add("GPS location fix unavailable (${location.availabilityState})")
        } else {
            supporting.add("Location: Valid GPS fix at ${location.locationCategory}")
        }

        // 2. Geofence Matching & Mismatch Detection
        if (geofence.isInsideGeofence && geofence.currentZone != "UNKNOWN") {
            val isMatch = isGeofenceCategoryMatching(location.locationCategory, geofence.currentZone)
            if (isMatch) {
                supporting.add("Geofence: Verified inside ${geofence.currentZone}")
            } else if (location.locationCategory != "UNKNOWN") {
                score -= 0.20f
                conflicting.add("Geofence mismatch: Location indicates '${location.locationCategory}' but Geofence indicates '${geofence.currentZone}'")
            }
        }

        // 3. Activity Signal Evaluation
        if (!activity.isPermissionGranted) {
            score -= 0.20f
            missing.add("Activity recognition permission denied")
        } else if (!activity.isActivityAvailable || activity.detectedState == ActivityMovementState.UNKNOWN) {
            score -= 0.15f
            missing.add("Activity movement state unknown")
        } else {
            supporting.add("Activity: Confirmed ${activity.activityType} (${activity.confidencePercentage}%)")
        }

        // 4. Activity vs Location Conflict (e.g. Stationary Place like Home/Office + IN_VEHICLE activity with high confidence)
        val isStationaryPlace = location.locationCategory.equals("Home", ignoreCase = true) || location.locationCategory.equals("Office", ignoreCase = true)
        if (isStationaryPlace && activity.detectedState == ActivityMovementState.IN_VEHICLE && activity.confidencePercentage >= 75) {
            score -= 0.15f
            conflicting.add("Movement conflict: Located at stationary '${location.locationCategory}' while activity indicates IN_VEHICLE")
        }

        // 5. Signal Freshness Evaluation
        val signalAgeMs = currentTimeMs - deviceContext.timestampMs
        val ageSeconds = (signalAgeMs / 1000).coerceAtLeast(0)
        val ageMinutes = ageSeconds / 60

        if (signalAgeMs > 900_000L) { // > 15 minutes
            score -= 0.35f
            missing.add("Critically stale signals ($ageMinutes minutes old)")
        } else if (signalAgeMs > 300_000L) { // > 5 minutes
            score -= 0.20f
            missing.add("Stale signal data ($ageMinutes minutes old)")
        } else {
            supporting.add("Freshness: Live signal data (${ageSeconds}s old)")
        }

        // 6. Routine Adherence & Anomaly Check
        if (routineContext != null) {
            if (routineContext.isRoutineDeviation && routineContext.routineDeviationScore >= 0.5) {
                score -= 0.15f
                conflicting.add("Routine anomaly: High schedule deviation score (${routineContext.routineDeviationScore})")
            } else {
                supporting.add("Routine: Aligned with expected baseline")
            }
        }

        // Clamp confidence score between 0.05 and 1.00
        val finalScore = score.coerceIn(0.05f, 1.00f)

        // Determine confidence level
        var level = when {
            finalScore >= 0.85f -> ConfidenceLevel.HIGH
            finalScore >= 0.60f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        // Strict Cap: Do not claim HIGH confidence when major signals are missing or conflicting!
        val hasMajorMissing = missing.any { it.contains("permission denied", ignoreCase = true) || it.contains("unavailable", ignoreCase = true) }
        val hasConflict = conflicting.isNotEmpty()

        if (level == ConfidenceLevel.HIGH && (hasMajorMissing || hasConflict)) {
            level = ConfidenceLevel.MEDIUM
            Log.d(TAG, "Capped confidenceLevel from HIGH to MEDIUM due to missing/conflicting signals.")
        }

        val quality = (1.0 - (missing.size * 0.15)).coerceIn(0.1, 1.0)
        val freshness = if (signalAgeMs > 300_000L) 0.5 else 1.0

        return ContextConfidence(
            confidenceScore = String.format("%.2f", finalScore).toFloat(),
            confidenceLevel = level,
            supportingSignals = supporting,
            missingSignals = missing,
            conflictingSignals = conflicting,
            overallConfidence = finalScore.toDouble(),
            signalQualityScore = quality,
            freshnessScore = freshness
        )
    }

    private fun isGeofenceCategoryMatching(locationCategory: String, geofenceZone: String): Boolean {
        return when {
            locationCategory.equals("Home", ignoreCase = true) && geofenceZone.contains("HOME", ignoreCase = true) -> true
            locationCategory.equals("Office", ignoreCase = true) && geofenceZone.contains("OFFICE", ignoreCase = true) -> true
            else -> false
        }
    }

    companion object {
        private const val TAG = "ContextConfidenceEvaluator"
    }
}
