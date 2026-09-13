package com.comai.memory

import com.comai.contextengine.db.Memory
import com.comai.engine.models.ContextSignals

/**
 * Clean context provider that supplies persistent Room memories to the LLM and Context pipeline.
 *
 * Provides:
 * 1. [getRelevantMemories]: Raw Memory entity list for processing.
 * 2. [getFormattedMemoryContext]: Formatted markdown/bullet summary for system prompt / retrieved_data.
 * 3. [getMemoryContextList]: List of clean string statements for memory_context JSON contract.
 */
class MemoryContextProvider(
    private val memoryRepository: MemoryRepository
) {

    /**
     * Retrieves the relevant stored memory entities from Room for a given task/signals.
     */
    suspend fun getRelevantMemories(task: String, signals: ContextSignals? = null): List<Memory> {
        return memoryRepository.getRelevantMemoriesList(task, signals)
    }

    /**
     * Returns a formatted text summary of relevant memories for prompt injection.
     */
    suspend fun getFormattedMemoryContext(task: String, signals: ContextSignals? = null): String? {
        return memoryRepository.retrieveRelevantMemories(task, signals)
    }

    /**
     * Returns a list of discrete memory statements matching the required memory_context schema:
     * [
     *   "User's favorite programming language is Python",
     *   "Usually leaves college around 17:00",
     *   ...
     * ]
     */
    suspend fun getMemoryContextList(task: String, signals: ContextSignals? = null): List<String> {
        val memories = getRelevantMemories(task, signals)
        return memories.map { it.toDisplayString() }
    }
}
