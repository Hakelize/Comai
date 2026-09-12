package com.comai.contextengine.context

import com.comai.contextengine.data.NormalizedContextSignal
import com.comai.contextengine.providers.models.DeviceContext
import java.util.Calendar

/**
 * Normalizes raw time, location, activity, and device signals into standard formats.
 * Removes duplicate data and filters noise to keep only decision-useful context.
 */
class ContextNormalizer {

    /**
     * Converts raw time strings (HH:mm) into minutes elapsed from midnight (0..1439).
     */
    fun parseTimeToMinutes(timeString: String): Int {
        val parts = timeString.split(":")
        if (parts.size < 2) return 540 // Default 09:00 AM
        val hours = parts[0].trim().toIntOrNull() ?: 9
        val minutes = parts[1].trim().toIntOrNull() ?: 0
        return (hours * 60 + minutes).coerceIn(0, 1439)
    }

    /**
     * Converts minutes from midnight (0..1439) to HH:mm string.
     */
    fun formatMinutesToTime(minutes: Int): String {
        val total = minutes.coerceIn(0, 1439)
        val h = total / 60
        val m = total % 60
        return String.format("%02d:%02d", h, m)
    }

    /**
     * Normalizes current time into a fractional value between 0.0 (midnight) and 1.0 (23:59).
     */
    fun getNormalizedTimeOfDay(calendar: Calendar = Calendar.getInstance()): Double {
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return minutes / 1440.0
    }

    /**
     * Normalizes location strings into canonical location categories (Home, Office, Commute, Gym, Unknown).
     */
    fun normalizeLocation(rawLocation: String?): String {
        if (rawLocation.isNullOrBlank()) return "UNKNOWN"
        val lower = rawLocation.trim().lowercase()
        return when {
            lower.contains("home") || lower.contains("residence") -> "Home"
            lower.contains("office") || lower.contains("work") || lower.contains("tech hub") || lower.contains("company") -> "Office"
            lower.contains("commute") || lower.contains("transit") || lower.contains("road") -> "Commute"
            lower.contains("gym") || lower.contains("fitness") -> "Gym"
            else -> rawLocation.trim()
        }
    }

    /**
     * Normalizes raw activity strings into standard uppercase activity tags.
     */
    fun normalizeActivity(rawActivity: String?): String {
        if (rawActivity.isNullOrBlank()) return "STILL"
        val upper = rawActivity.trim().uppercase()
        return when {
            upper.contains("STILL") || upper.contains("STATIONARY") -> "STILL"
            upper.contains("WALK") -> "WALKING"
            upper.contains("RUN") -> "RUNNING"
            upper.contains("VEHICLE") || upper.contains("DRIVING") || upper.contains("CAR") -> "IN_VEHICLE"
            upper.contains("BIKE") || upper.contains("BICYCLE") -> "ON_BICYCLE"
            else -> "UNKNOWN"
        }
    }

    /**
     * Removes duplicate strings and redundant information from a signal list.
     */
    fun deduplicateSignals(signals: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (signal in signals) {
            val clean = signal.trim()
            if (clean.isNotEmpty() && seen.add(clean.lowercase())) {
                result.add(clean)
            }
        }
        return result
    }

    /**
     * Cleans up and normalizes a unified [DeviceContext] object.
     */
    fun normalizeDeviceContext(context: DeviceContext): DeviceContext {
        val normalizedLocCategory = normalizeLocation(context.location.locationCategory)
        val normalizedActType = normalizeActivity(context.activity.activityType)

        return context.copy(
            location = context.location.copy(locationCategory = normalizedLocCategory),
            activity = context.activity.copy(activityType = normalizedActType)
        )
    }

    /**
     * Builds a NormalizedContextSignal object.
     */
    fun buildNormalizedSignal(
        timeString: String,
        rawLocation: String?,
        rawActivity: String?,
        isWorkDay: Boolean,
        deviationScore: Double,
        confidence: Double
    ): NormalizedContextSignal {
        val minutes = parseTimeToMinutes(timeString)
        val timeNormalized = minutes / 1440.0
        val location = normalizeLocation(rawLocation)
        val activity = normalizeActivity(rawActivity)

        return NormalizedContextSignal(
            timeOfDayNormalized = timeNormalized,
            locationCategory = location,
            userActivity = activity,
            isWorkDay = isWorkDay,
            routineDeviationScore = deviationScore,
            hasRoutineDeviation = deviationScore > 0.4,
            signalConfidence = confidence
        )
    }
}
