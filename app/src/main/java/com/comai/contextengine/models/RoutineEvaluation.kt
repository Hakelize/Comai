package com.comai.contextengine.models

/**
 * Structured evaluation result comparing user's current context against locally observed routine patterns.
 */
data class RoutineEvaluation(
    val isNormalRoutine: Boolean = true,
    val routineDeviation: Boolean = false,
    val deviationMinutes: Int = 0,
    val confidence: Float = 1.0f,
    val deviationReason: String = "Normal routine adherence"
)
