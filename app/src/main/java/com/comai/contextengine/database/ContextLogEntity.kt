package com.comai.contextengine.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing compact, structured context logs for analysis, diagnostics, and routine learning.
 */
@Entity(tableName = "context_logs")
data class ContextLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val locationPlace: String = "Home",
    val activityType: String = "STILL",
    val routineState: String = "WORK_DAY_ROUTINE",
    val isRoutineDeviation: Boolean = false,
    val deviationMinutes: Int = 0,
    val confidenceScore: Float = 1.0f,
    val confidenceLevel: String = "HIGH",
    val triggerTask: String = "GENERAL_CHECKIN",
    val eventType: String = "PERIODIC_LOG",
    val isEscalated: Boolean = false,
    val task: String = triggerTask,
    val confidence: Double = confidenceScore.toDouble(),
    val signalsSummary: String = "Place: $locationPlace | Activity: $activityType | Confidence: $confidenceLevel"
)
