package com.comai.digitalactivity.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity storing daily aggregated activity statistics used for routine learning.
 */
@Entity(tableName = "daily_activity_summaries")
data class DailyActivitySummaryEntity(
    @PrimaryKey
    val date: String, // Format: "YYYY-MM-DD"
    val totalScreenTimeMinutes: Long,
    val firstActiveTimeMs: Long,
    val lastActiveTimeMs: Long,
    val topPackageName: String,
    val topCategory: String,
    val inferredWakeTimeMs: Long? = null,
    val inferredSleepTimeMs: Long? = null
)
