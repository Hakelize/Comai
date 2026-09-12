package com.comai.contextengine.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local personal memory entity for Comai.
 * Represents user preferences, contextual facts, and personal knowledge stored privacy-first in local Room.
 */
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // PREFERENCE, CONTEXT, PERSONAL_KNOWLEDGE, EPISODIC, SESSION
    val key: String,
    val value: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val source: String = "USER_EXPLICIT",
    val confidence: Float = 1.0f,
    val isUserDeletable: Boolean = true
)
