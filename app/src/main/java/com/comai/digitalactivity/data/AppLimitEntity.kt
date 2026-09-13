package com.comai.digitalactivity.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a daily app usage limit set by the user.
 */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean = true,
    val lastNotifiedDate: String = "", // e.g. "2026-09-12" to rate-limit notifications to once per day
    val createdAtMs: Long = System.currentTimeMillis()
)
