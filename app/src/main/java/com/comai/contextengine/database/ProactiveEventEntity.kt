package com.comai.contextengine.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing historical proactive events triggered by the RuleEngine.
 */
@Entity(tableName = "proactive_events")
data class ProactiveEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val eventId: String,
    val eventType: String,
    val priority: String = "MEDIUM",
    val targetTask: String,
    val targetConstraints: String,
    val contextSummary: String,
    val isAcknowledged: Boolean = false
)
