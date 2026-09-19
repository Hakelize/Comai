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
     * Retrieves structured list of relevant memories tailored specifically to the given task or context signals.
     */
    suspend fun getRelevantMemoriesList(task: String, signals: ContextSignals? = null): List<Memory> {
        val lowerTask = task.lowercase(Locale.ROOT)
        val allMemories = memoryDao.getAll()
        if (allMemories.isEmpty()) return emptyList()

        val isBroadRecall = lowerTask.contains("remember") ||
                lowerTask.contains("know about me") ||
                lowerTask.contains("what do you know") ||
                lowerTask.contains("my memories") ||
                lowerTask.contains("recall") ||
                lowerTask.contains("who am i") ||
                lowerTask.contains("my preferences") ||
                lowerTask.contains("about me") ||
                lowerTask.contains("myself") ||
                lowerTask.contains("today") ||
                lowerTask.contains("plan") ||
                lowerTask.contains("schedule") ||
                lowerTask.contains("iqoo") ||
                lowerTask.contains("reskill") ||
                lowerTask.contains("chennai") ||
                lowerTask.contains("tell me") ||
                lowerTask.contains("what am i") ||
                lowerTask.contains("what's my") ||
                lowerTask.contains("whats my") ||
                lowerTask.contains("comai") ||
                lowerTask.contains("event")

        if (isBroadRecall) {
            return allMemories
        }

        val isDepartureQuery = lowerTask.contains("leave work") ||
                lowerTask.contains("usual departure") ||
                lowerTask.contains("leave office") ||
                lowerTask.contains("leave college") ||
                lowerTask.contains("what time do i")

        val isOvertimeOrCommute = lowerTask.contains("overtime") ||
                lowerTask.contains("commute") ||
                lowerTask.contains("leaving") ||
                (signals?.location?.equals("office", ignoreCase = true) == true) ||
                (signals?.routineDeviation == true)

        return when {
            isDepartureQuery -> {
                allMemories.filter { it.key.startsWith("preferred_") && it.key.endsWith("_departure") }
            }
            isOvertimeOrCommute -> {
                allMemories.filter { (it.key.startsWith("preferred_") && it.key.endsWith("_departure")) || it.key == "workplace" }
            }
            lowerTask.contains("music") -> {
                allMemories.filter { it.key == "after_work_music" }
            }
            lowerTask.contains("programming") || lowerTask.contains("language") || lowerTask.contains("python") || lowerTask.contains("code") -> {
                allMemories.filter { it.key.contains("programming") || it.key.contains("language") || it.value.contains("Python", ignoreCase = true) }
            }
            lowerTask.contains("college") || lowerTask.contains("study") -> {
                allMemories.filter { it.key == "college" || it.key.contains("college") }
            }
            lowerTask.contains("food") || lowerTask.contains("eat") -> {
                allMemories.filter { it.key == "favorite_food" || it.key.contains("food") }
            }
            else -> {
                val taskTokens = lowerTask.split("\\W+".toRegex()).filter { it.length >= 3 }
                val matches = allMemories.filter { mem ->
                    val memText = "${mem.key} ${mem.value}".lowercase(Locale.ROOT)
                    taskTokens.any { token -> memText.contains(token) }
                }
                matches
            }
        }
    }

    /**
     * Retrieves relevant memories tailored specifically to the given task or context signals.
     * Formats output compactly to avoid dumping the whole database into AI input.
     */
    suspend fun retrieveRelevantMemories(task: String, signals: ContextSignals? = null): String? {
        val relevant = getRelevantMemoriesList(task, signals)
        if (relevant.isEmpty()) return null

        // Compact, structured formatting grouped by type
        val preferences = relevant.filter { it.type == "PREFERENCE" }
        val context = relevant.filter { it.type == "CONTEXT" }
        val personal = relevant.filter { it.type == "PERSONAL_KNOWLEDGE" }
        val inferred = relevant.filter { it.type == "INFERRED" }
        val episodic = relevant.filter { it.type == "EPISODIC" }
        val other = relevant.filter { it.type !in setOf("PREFERENCE", "CONTEXT", "PERSONAL_KNOWLEDGE", "INFERRED", "EPISODIC") }

        val sb = StringBuilder()
        if (preferences.isNotEmpty()) {
            sb.append("Preferences:\n")
            for (p in preferences) { sb.append("- ").append(p.toDisplayString()).append("\n") }
        }

        if (context.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Context:\n")
            for (c in context) { sb.append("- ").append(c.toDisplayString()).append("\n") }
        }

        if (personal.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Personal Knowledge:\n")
            for (k in personal) { sb.append("- ").append(k.toDisplayString()).append("\n") }
        }

        if (inferred.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Inferred from Activity:\n")
            for (i in inferred) { sb.append("- ").append(i.value).append("\n") }
        }

        if (episodic.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Recent Conversation Context:\n")
            for (e in episodic.take(3)) { sb.append("- ").append(e.value).append("\n") }
        }

        if (other.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Other Information:\n")
            for (o in other) { sb.append("- ").append(o.toDisplayString()).append("\n") }
        }

        return sb.toString().trim().ifEmpty { null }
    }

    companion object {
        private const val TAG = "MemoryRepository"
    }
}

