package com.comai.data.models

import java.util.UUID

enum class ReminderType {
    NOTIFICATION,
    ALARM
}

/**
 * Represents a personal user plan or time-based reminder.
 */
data class PersonalPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val time: String, // e.g. "10:00 AM"
    val date: String? = null, // e.g. "2026-09-13" (ISO yyyy-MM-dd)
    val repeatFrequency: String = "Daily", // e.g. "Once", "Daily", "Weekdays", "Weekends", "Mon, Wed, Fri"
    val reminderType: ReminderType = ReminderType.NOTIFICATION,
    val isEnabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
