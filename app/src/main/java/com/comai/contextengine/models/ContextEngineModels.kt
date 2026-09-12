package com.comai.contextengine.models

import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.engine.models.ContextInput

/**
 * Broad user context representation inferred deterministically from device signals.
 */
data class UserContext(
    val broadContextTag: String = "GENERAL_DAILY", // e.g. "OFFICE_LATE_DEPARTURE", "HOME_MORNING", "COMMUTE", "LUNCH_TIME", "RESTING", "SLEEP"
    val locationPlaceName: String = "Home",
    val movementSummary: String = "STATIONARY",
    val deviceAudioSummary: String = "SPEAKER",
    val devicePowerSummary: String = "NORMAL",
    val timeOfDayFormatted: String = "09:00"
)

/**
 * Routine context evaluation model tracking daily baseline adherence and deviation.
 */
data class RoutineContext(
    val isWorkDay: Boolean = true,
    val routineState: String = "WORK_DAY_ROUTINE",
    val isRoutineDeviation: Boolean = false,
    val deviationMinutes: Int = 0,
    val routineDeviationScore: Double = 0.0, // 0.0 (normal) to 1.0 (anomalous)
    val confidence: Float = 1.0f,
    val deviationReason: String = "No deviation detected"
)

enum class ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Calculated confidence assessment score for context evaluation.
 */
data class ContextConfidence(
    val confidenceScore: Float = 1.0f,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.HIGH,
    val supportingSignals: List<String> = emptyList(),
    val missingSignals: List<String> = emptyList(),
    val conflictingSignals: List<String> = emptyList(),
    val overallConfidence: Double = confidenceScore.toDouble(),
    val signalQualityScore: Double = 1.0,
    val freshnessScore: Double = 1.0
)

/**
 * Combined processing output result produced by the ContextEngine.
 */
data class ContextProcessingResult(
    val deviceContext: DeviceContext,
    val userContext: UserContext,
    val routineContext: RoutineContext,
    val confidence: ContextConfidence,
    val contract: SharedContextContract,
    val contextInput: ContextInput
)
