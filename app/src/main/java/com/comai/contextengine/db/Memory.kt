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
) {
    fun toDisplayString(): String {
        return when {
            value.startsWith("User's", ignoreCase = true) -> value
            key == "preferred_work_departure" -> "Usually leaves work around $value"
            key.startsWith("preferred_") && key.endsWith("_departure") -> {
                val place = key.removePrefix("preferred_").removeSuffix("_departure").replace("_", " ")
                "Usually leaves $place around $value"
            }
            key == "workplace" -> "Workplace: $value"
            key == "college" -> "Studies at $value"
            key == "home_location" -> "Lives in $value"
            key == "after_work_music" -> "Likes $value music after work"
            key.startsWith("favorite_") -> {
                val item = key.removePrefix("favorite_").replace("_", " ")
                "Favorite $item: $value"
            }
            else -> {
                val cleanKey = key.replace("_", " ").replaceFirstChar { it.uppercase() }
                "$cleanKey: $value"
            }
        }
    }

    fun categoryLabel(): String {
        return when (type) {
            "PREFERENCE" -> "Preference"
            "CONTEXT" -> "Context & Routine"
            "PERSONAL_KNOWLEDGE" -> "Personal Knowledge"
            else -> type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

