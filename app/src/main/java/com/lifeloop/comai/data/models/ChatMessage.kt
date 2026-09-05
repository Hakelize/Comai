package com.lifeloop.comai.data.models

import java.util.UUID

/**
 * A single message in the chat conversation.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    /** Maps to [AIResponse.action] for AI messages, null for user messages. */
    val action: String? = null
)
