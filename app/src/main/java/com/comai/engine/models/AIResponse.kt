package com.comai.engine.models

/**
 * Structured response from the AI engine.
 * Every field is typed — no free-text parsing needed.
 */
data class AIResponse(
    /** Action type: "greeting", "tribe_event", "reminder", "check_in",
     *  "medication", "memory_recall", "commute", "general" */
    val action: String,

    /** TTS-ready plain text (no emoji, no markdown). */
    val speech: String,

    /** Display text for the chat UI (may include emoji or rich formatting hints). */
    val displayText: String,

    /** Optional key-value metadata for the dashboard inspector. */
    val metadata: Map<String, String>? = null
)
