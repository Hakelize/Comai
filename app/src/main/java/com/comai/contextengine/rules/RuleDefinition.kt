package com.comai.contextengine.rules

import com.comai.contextengine.contract.ContextSignals

/**
 * Priority levels for proactive context events.
 */
enum class EventPriority(val level: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4)
}

/**
 * Cooldown tracker for preventing repetitive rule triggers.
 */
data class Cooldown(
    val cooldownDurationMs: Long = 300_000L, // Default 5 minutes
    var lastTriggeredTimestampMs: Long = 0L
) {
    fun isCoolingDown(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (lastTriggeredTimestampMs <= 0L) return false
        return (currentTimeMs - lastTriggeredTimestampMs) < cooldownDurationMs
    }

    fun remainingCooldownMs(currentTimeMs: Long = System.currentTimeMillis()): Long {
        if (!isCoolingDown(currentTimeMs)) return 0L
        return cooldownDurationMs - (currentTimeMs - lastTriggeredTimestampMs)
    }

    fun markTriggered(currentTimeMs: Long = System.currentTimeMillis()) {
        lastTriggeredTimestampMs = currentTimeMs
    }

    fun reset() {
        lastTriggeredTimestampMs = 0L
    }
}

/**
 * Threshold configuration model for numeric or condition-based rule triggers.
 */
data class TriggerThreshold(
    val deviationMinutesThreshold: Int = 30,
    val timeWindowStart: String = "00:00",
    val timeWindowEnd: String = "23:59",
    val requiredLocation: String? = null,
    val requiredActivity: String? = null,
    val isWorkDayOnly: Boolean = false,
    val isNonWorkDayOnly: Boolean = false
)

/**
 * Deterministic condition evaluator for proactive context rules.
 */
data class RuleCondition(
    val threshold: TriggerThreshold = TriggerThreshold(),
    val evaluator: ((RuleEvaluationInput) -> Boolean)? = null
) {
    fun evaluate(input: RuleEvaluationInput): Boolean {
        if (evaluator != null) {
            return evaluator.invoke(input)
        }

        if (threshold.isWorkDayOnly && !input.isWorkDay) return false
        if (threshold.isNonWorkDayOnly && input.isWorkDay) return false

        if (threshold.requiredLocation != null &&
            !input.currentLocation.equals(threshold.requiredLocation, ignoreCase = true)
        ) {
            return false
        }

        return true
    }
}

/**
 * Structured proactive event emitted by the RuleEngine to pass to AIEngine.
 */
data class ProactiveEvent(
    val eventId: String,
    val eventType: String, // e.g. "MORNING_ROUTINE_REMINDER", "WORK_ROUTINE_RECOGNITION", "OVERTIME_CHECKIN", "LUNCH_REMINDER", "WIND_DOWN_CANDIDATE", "ROUTINE_DEVIATION_ALERT"
    val priority: EventPriority = EventPriority.MEDIUM,
    val targetTask: String,
    val targetConstraints: String,
    val contextSummary: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Configurable, deterministic rule definition with built-in cooldown tracking.
 */
data class Rule(
    val id: String,
    val name: String,
    val description: String,
    val eventType: String,
    val priority: EventPriority = EventPriority.MEDIUM,
    val condition: RuleCondition,
    val cooldown: Cooldown = Cooldown(),
    val targetTask: String,
    val targetConstraints: String = "Keep response empathetic, concise (max 2 sentences).",
    val enabled: Boolean = true,
    val systemRole: String = "Comai Proactive Context Engine"
) {
    fun isCoolingDown(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return cooldown.isCoolingDown(currentTimeMs)
    }

    fun markTriggered(currentTimeMs: Long = System.currentTimeMillis()) {
        cooldown.markTriggered(currentTimeMs)
    }
}

/**
 * Legacy trigger enum kept for backward compatibility.
 */
enum class RuleTriggerType {
    WAKE_TIME_DEVIATION,
    TRIBE_FINDER_LOOKUP,
    LUNCH_REMINDER,
    OVERTIME_CHECKIN,
    MEDICATION_REMINDER,
    WORK_ROUTINE_RECOGNITION,
    WIND_DOWN_CANDIDATE,
    ROUTINE_DEVIATION_ALERT
}

/**
 * Backward compatible RuleDefinition wrapper model.
 */
data class RuleDefinition(
    val id: String,
    val name: String,
    val description: String,
    val triggerType: RuleTriggerType,
    val enabled: Boolean = true,
    val systemRole: String = "Comai Proactive Context Engine",
    val targetTask: String,
    val targetConstraints: String = "Keep response empathetic, concise (max 2 sentences), and highly actionable.",
    val wakeDeviationThresholdMins: Int = 30,
    val commuteWindowStart: String = "17:00",
    val commuteWindowEnd: String = "20:00",
    val lunchWindowStart: String = "12:30",
    val lunchWindowEnd: String = "14:00",
    val overtimeThresholdMins: Int = 45,
    val nightWindowStart: String = "21:00",
    val nightWindowEnd: String = "23:00"
)

/**
 * Input state provided to RuleEngine for evaluation.
 */
data class RuleEvaluationInput(
    val currentTime: String,
    val currentTimeMs: Long = System.currentTimeMillis(),
    val dayOfWeek: String = "MONDAY",
    val isWorkDay: Boolean = true,
    val currentLocation: String = "Home",
    val wakeTime: String = "07:00",
    val baselineWakeTime: String = "07:00",
    val loggedLunch: Boolean = false,
    val medicationFlag: Boolean = true,
    val historicalOfficeDepartureTime: String = "17:30",
    val routineDeviation: Boolean = false
)

/**
 * Evaluation output produced when a Rule fires.
 */
data class RuleEvaluationResult(
    val triggeredRule: RuleDefinition,
    val contextSignals: ContextSignals,
    val systemRole: String,
    val task: String,
    val constraints: String,
    val retrievedDataLookupRequired: Boolean = false,
    val retrievedDataCategory: String? = null,
    val proactiveEvent: ProactiveEvent? = null
)
