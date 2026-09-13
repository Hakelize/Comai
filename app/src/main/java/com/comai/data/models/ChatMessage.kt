package com.comai.data.models

import java.util.UUID

/**
 * A single message in the chat conversation with optional local media attachment.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    /** Maps to [AIResponse.action] for AI messages, null for user messages. */
    val action: String? = null,
    /** Local content URI string for attached camera photo or file. */
    val mediaUri: String? = null,
    /** "image" or "document" */
    val mediaType: String? = null,
    /** Optional display filename. */
    val mediaName: String? = null
)
