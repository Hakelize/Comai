package com.comai.contextengine.data

/**
 * Normalized context signal representation for evaluation by rules and engines.
 */
data class NormalizedContextSignal(
    val timeOfDayNormalized: Double = 0.0, // 0.0 to 1.0 representing 00:00 to 23:59
    val locationCategory: String = "UNKNOWN",
    val userActivity: String = "STILL",
    val isWorkDay: Boolean = true,
    val routineDeviationScore: Double = 0.0, // 0.0 (normal) to 1.0 (highly anomalous)
    val hasRoutineDeviation: Boolean = false,
    val signalConfidence: Double = 1.0 // 0.0 to 1.0 confidence score
)
