package com.comai.memory

import android.util.Log
import com.comai.contextengine.db.Memory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Seeds the memory database with hardcoded contextual facts on first launch.
 *
 * These seeds provide the LLM with grounded, real-world context about the user's
 * current situation so it can answer queries like "what's my plan today?" accurately.
 *
 * Seeds are idempotent: each has a unique key. If the key already exists in Room,
 * it won't be re-inserted (handled by [MemoryRepository.saveMemory] upsert logic).
 */
object MemorySeeder {

    private const val TAG = "MemorySeeder"

    /**
     * The canonical list of seed memories. Add new entries here.
     */
    private val SEEDS = listOf(
        Memory(
            type = "CONTEXT",
            key = "current_event",
            value = "iQOO Reskill Chennai — Android development bootcamp and product sprint",
            source = "SYSTEM_SEED",
            confidence = 1.0f,
            isUserDeletable = true
        ),
        Memory(
            type = "CONTEXT",
            key = "today_plan",
            value = "Comai Android development, team sessions, build & deploy testing, LLM integration, and UI polish at iQOO Reskill Chennai event",
            source = "SYSTEM_SEED",
            confidence = 1.0f,
            isUserDeletable = true
        ),
        Memory(
            type = "CONTEXT",
            key = "current_location_context",
            value = "Chennai, Tamil Nadu, India — at iQOO Reskill venue",
            source = "SYSTEM_SEED",
            confidence = 1.0f,
            isUserDeletable = true
        ),
        Memory(
            type = "PERSONAL_KNOWLEDGE",
            key = "project_name",
            value = "Comai — on-device AI life companion app",
            source = "SYSTEM_SEED",
            confidence = 1.0f,
            isUserDeletable = true
        ),
        Memory(
            type = "PERSONAL_KNOWLEDGE",
            key = "team_context",
            value = "Hakelize team — building Comai with Gemma 4 E4B on-device LLM, Jetpack Compose UI, and MediaPipe inference",
            source = "SYSTEM_SEED",
            confidence = 1.0f,
            isUserDeletable = true
        ),
        Memory(
            type = "PREFERENCE",
            key = "preferred_work_departure",
            value = "6:00 PM",
            source = "SYSTEM_SEED",
            confidence = 0.8f,
            isUserDeletable = true
        ),
        Memory(
            type = "PREFERENCE",
            key = "after_work_music",
            value = "calm lo-fi",
            source = "SYSTEM_SEED",
            confidence = 0.8f,
            isUserDeletable = true
        )
    )

    /**
     * Seeds all hardcoded memories into Room. Safe to call multiple times —
     * existing keys are updated, not duplicated.
     */
    fun seedIfNeeded(memoryRepository: MemoryRepository, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            try {
                val existingKeys = memoryRepository.getAllMemories().map { it.key }.toSet()
                var inserted = 0
                for (seed in SEEDS) {
                    if (seed.key !in existingKeys) {
                        memoryRepository.saveMemory(seed)
                        inserted++
                    }
                }
                if (inserted > 0) {
                    Log.i(TAG, "Seeded $inserted hardcoded memories into Room")
                } else {
                    Log.d(TAG, "All ${SEEDS.size} seed memories already present — skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error seeding hardcoded memories: ${e.message}", e)
            }
        }
    }
}
