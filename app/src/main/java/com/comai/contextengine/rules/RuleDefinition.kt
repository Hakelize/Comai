package com.comai.contextengine.rules

import com.comai.contextengine.contract.ContextSignals

enum class RuleTriggerType {
    WAKE_TIME_DEVIATION,
    TRIBE_FINDER_LOOKUP,
    LUNCH_REMINDER,
    OVERTIME_CHECKIN,
    MEDICATION_REMINDER
}

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

data class RuleEvaluationResult(
    val triggeredRule: RuleDefinition,
    val contextSignals: ContextSignals,
    val systemRole: String,
    val task: String,
    val constraints: String,
    val retrievedDataLookupRequired: Boolean = false,
    val retrievedDataCategory: String? = null
)
