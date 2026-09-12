package com.comai.contextengine.data

/**
 * Baseline user routine parameters computed from activity patterns and preferences.
 */
data class RoutineProfile(
    val expectedWakeTimeMinutes: Int = 420, // 07:00 AM (minutes from midnight)
    val expectedCommuteStartMinutes: Int = 510, // 08:30 AM
    val expectedLunchStartMinutes: Int = 780, // 01:00 PM
    val expectedOfficeDepartureMinutes: Int = 1080, // 06:00 PM
    val expectedMedicationMinutes: Int = 1260, // 09:00 PM
    val expectedSleepMinutes: Int = 1380, // 11:00 PM
    val isWorkDay: Boolean = true
)
