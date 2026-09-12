package com.comai.memory

import android.util.Log
import com.comai.contextengine.db.Memory
import com.comai.contextengine.db.MemoryDao
import com.comai.engine.models.ContextSignals
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/**
 * Concrete repository for personal memories stored in local Room storage.
 *
 * Responsibilities:
 * - Reactive memory observation via Flow for the UI.
 * - Upserting logical singleton facts (e.g., preferred_work_departure, workplace, after_work_music).
 * - Conservative deterministic extraction from user conversation.
 * - Relevance-based retrieval for compact insertion into ContextInput.retrievedData.
 */
class MemoryRepository(
    private val memoryDao: MemoryDao
) {

    /**
     * Observable flow of all stored memories for the UI.
     */
    fun getAllMemoriesFlow(): Flow<List<Memory>> = memoryDao.getAllAsFlow()

    /**
     * Synchronously/suspendingly get all memories.
     */
    suspend fun getAllMemories(): List<Memory> = memoryDao.getAll()

    suspend fun getMemoryById(id: Long): Memory? = memoryDao.getById(id)

    suspend fun getMemoriesByType(type: String): List<Memory> = memoryDao.getByType(type)

    suspend fun getMemoryByKey(key: String): Memory? = memoryDao.getByKey(key)

    /**
     * Inserts or updates a memory entity.
     * For logical singleton keys, updates the existing row to prevent conflicting duplicates.
     */
    suspend fun saveMemory(memory: Memory): Long {
        val existing = memoryDao.getByKey(memory.key)
        return if (existing != null) {
            val updated = existing.copy(
                value = memory.value,
                timestampMs = System.currentTimeMillis(),
                source = memory.source,
                confidence = memory.confidence
            )
            memoryDao.update(updated)
            Log.d(TAG, "Updated existing memory key=${memory.key}")
            existing.id
        } else {
            val id = memoryDao.insert(memory)
            Log.d(TAG, "Inserted new memory key=${memory.key}, id=$id")
            id
        }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
        Log.d(TAG, "Deleted memory id=$id")
    }

    suspend fun clearAll() {
        memoryDao.clear()
        Log.d(TAG, "Cleared all memories")
    }

    /**
     * Deterministically extracts explicit facts from user conversation and upserts into Room.
     * Returns the stored/updated Memory if extraction succeeded, or null otherwise.
     */
    suspend fun extractAndStoreMemory(userText: String): Memory? {
        val memory = MemoryExtractor.extract(userText) ?: return null
        val id = saveMemory(memory)
        return memory.copy(id = id)
    }

    /**
     * Retrieves relevant memories tailored specifically to the given task or context signals.
     * Formats output compactly to avoid dumping the whole database into AI input.
     */
    suspend fun retrieveRelevantMemories(task: String, signals: ContextSignals? = null): String? {
        val lowerTask = task.lowercase(Locale.ROOT)
        val allMemories = memoryDao.getAll()
        if (allMemories.isEmpty()) return null

        val isBroadRecall = lowerTask.contains("remember") ||
                lowerTask.contains("know about me") ||
                lowerTask.contains("what do you know") ||
                lowerTask.contains("my memories")

        val isDepartureQuery = lowerTask.contains("leave work") ||
                lowerTask.contains("usual departure") ||
                lowerTask.contains("leave office") ||
                lowerTask.contains("what time do i")

        val isOvertimeOrCommute = lowerTask.contains("overtime") ||
                lowerTask.contains("commute") ||
                lowerTask.contains("leaving") ||
                (signals?.location?.equals("office", ignoreCase = true) == true) ||
                (signals?.routineDeviation == true)

        val relevant = when {
            isBroadRecall -> {
                // Broad personal memory retrieval
                allMemories
            }
            isDepartureQuery -> {
                // Only departure-related memory
                allMemories.filter { it.key == "preferred_work_departure" }
            }
            isOvertimeOrCommute -> {
                // Workplace and departure context
                allMemories.filter { it.key == "preferred_work_departure" || it.key == "workplace" }
            }
            lowerTask.contains("music") -> {
                allMemories.filter { it.key == "after_work_music" }
            }
            else -> {
                // Do not dump unrelated records into random tasks
                emptyList()
            }
        }

        if (relevant.isEmpty()) return null

        // Compact, structured formatting
        val preferences = relevant.filter { it.type == "PREFERENCE" }
        val context = relevant.filter { it.type == "CONTEXT" }
        val personal = relevant.filter { it.type == "PERSONAL_KNOWLEDGE" }

        val sb = StringBuilder()
        if (preferences.isNotEmpty()) {
            sb.append("Preferences:\n")
            for (p in preferences) {
                when (p.key) {
                    "preferred_work_departure" -> sb.append("- Usually leaves work around ${p.value}\n")
                    "after_work_music" -> sb.append("- Likes ${p.value} music after work\n")
                    else -> sb.append("- ${p.key}: ${p.value}\n")
                }
            }
        }

        if (context.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Context:\n")
            for (c in context) {
                when (c.key) {
                    "workplace" -> sb.append("- Workplace: ${c.value}\n")
                    else -> sb.append("- ${c.key}: ${c.value}\n")
                }
            }
        }

        if (personal.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Personal Knowledge:\n")
            for (k in personal) {
                sb.append("- ${k.key}: ${k.value}\n")
            }
        }

        return sb.toString().trim().ifEmpty { null }
    }

    companion object {
        private const val TAG = "MemoryRepository"
    }
}
