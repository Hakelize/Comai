package com.comai.data.models

import java.util.UUID

/**
 * Represents a personal user plan or time-based reminder.
 */
data class PersonalPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val time: String, // e.g. "10:00 AM"
    val repeatFrequency: String = "Daily", // e.g. "Once", "Daily", "Weekdays", "Weekends"
    val isEnabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
