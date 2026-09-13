package com.comai.data.menstrualcycle

/**
 * User-configured menstrual cycle parameters stored on-device.
 */
data class MenstrualCycleData(
    val lastPeriodStartDateMs: Long = 0L,
    val typicalCycleLengthDays: Int = 28,
    val periodDurationDays: Int = 5,
    val isRemindersEnabled: Boolean = false,
    val isConfigured: Boolean = false
)

/**
 * Non-medical, locally calculated cycle estimates.
 */
data class MenstrualCycleEstimate(
    val nextEstimatedStartDateMs: Long,
    val formattedEstimatedDate: String,
    val daysRemaining: Int,
    val estimatedWindowEndMs: Long,
    val disclaimer: String = "Estimated based on your saved cycle information."
)
