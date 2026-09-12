package com.comai.contextengine.context

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UserState - High level summary of user activity & sleep status.
 * Used directly to populate `user_state` in the Shared JSON Contract.
 */
data class UserState(
    val activityType: String = "STILL",
    val awakeDurationMinutes: Long = 0,
    val inactiveHours: Double = 0.0,
    val sleepDetectedTimeMs: Long? = null,
    val wakeDetectedTimeMs: Long? = null
) {
    /**
     * Formats user state into human-readable contract summary string for Person 2's LLM engine.
     * Example: "awake since 07:15 (4h 15m), inactive 2.5h, current activity: STILL"
     */
    fun toContractSummary(): String {
        val wakeStr = if (wakeDetectedTimeMs != null && wakeDetectedTimeMs > 0) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeFormatted = sdf.format(Date(wakeDetectedTimeMs))
            val hours = awakeDurationMinutes / 60
            val mins = awakeDurationMinutes % 60
            "awake since $timeFormatted (${hours}h ${mins}m)"
        } else {
            "wake state unknown"
        }
        val inactiveStr = if (inactiveHours > 0.1) ", inactive ${"%.1f".format(inactiveHours)}h" else ""
        return "$wakeStr$inactiveStr, current activity: $activityType"
    }
}
