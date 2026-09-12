package com.comai.engine

import com.comai.engine.models.AIResponse
import com.comai.engine.models.ContextInput

/**
 * The shared integration contract between frontend and AI backend.
 *
 * Implementations:
 * - [MockEngine]: Hardcoded JSON responses for UI development (Rakesh)
 * - HardwareEngine: MediaPipe + Gemma 2B on Snapdragon NPU (Harish — npu-inference branch)
 */
interface AIEngine {

    /**
     * Process a context input and return a structured AI response.
     * Must always return valid JSON-serializable [AIResponse] — never free-text.
     */
    suspend fun process(context: ContextInput): AIResponse

    /** Whether the engine is initialized and ready to accept requests. */
    fun isReady(): Boolean

    /** Human-readable engine identifier for the developer dashboard. */
    fun engineName(): String
}
