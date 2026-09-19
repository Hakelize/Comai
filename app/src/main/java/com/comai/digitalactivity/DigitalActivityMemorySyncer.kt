package com.comai.digitalactivity

import android.util.Log
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.Memory
import com.comai.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Continuously learns from digital activity data (screen time, app usage, inferred
 * wake/sleep times) and writes the patterns as [Memory] entities so the AI can
 * reason about the user's actual behaviour.
 *
 * Memory types written:
 * - `INFERRED` — derived from usage patterns, lower confidence than explicit
 * - source = "DIGITAL_ACTIVITY"
 *
 * Runs once per app session. Idempotent — updates existing keys, never duplicates.
 */
object DigitalActivityMemorySyncer {

    private const val TAG = "DigitalActivityMemorySyncer"

    /**
     * Entry point — call from [ComaiApplication.onCreate] after DB is ready.
     */
    fun syncIfNeeded(
        database: ComaiDatabase,
        memoryRepository: MemoryRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            try {
                syncActivityPatterns(database, memoryRepository)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing digital activity to memories: ${e.message}", e)
            }
        }
    }

    private suspend fun syncActivityPatterns(
        database: ComaiDatabase,
        memoryRepository: MemoryRepository
    ) {
        val activityDao = database.dailyActivityDao()
        val now = System.currentTimeMillis()

        // Get last 7 days of activity summaries
        val summaries = try {
            activityDao.getRecentSummaries(7)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read activity summaries: ${e.message}")
            return
        }

        if (summaries.isEmpty()) {
            Log.d(TAG, "No activity summaries found — nothing to sync")
            return
        }

        var synced = 0

        // --- Infer average wake time from first active timestamp ---
        val wakeTimesMs = summaries.mapNotNull { it.inferredWakeTimeMs }.filter { it > 0 }
        if (wakeTimesMs.size >= 2) {
            val avgWakeMs = wakeTimesMs.average().toLong()
            val avgWakeCal = Calendar.getInstance().apply { timeInMillis = avgWakeMs }
            val wakeHour = avgWakeCal.get(Calendar.HOUR_OF_DAY)
            val wakeMin = avgWakeCal.get(Calendar.MINUTE)
            val wakeStr = String.format(Locale.ROOT, "%02d:%02d", wakeHour, wakeMin)
            memoryRepository.saveMemory(
                Memory(
                    type = "INFERRED",
                    key = "inferred_wake_time",
                    value = "Based on phone usage patterns, user typically wakes up around $wakeStr",
                    timestampMs = now,
                    source = "DIGITAL_ACTIVITY",
                    confidence = 0.7f,
                    isUserDeletable = true
                )
            )
            synced++
        }

        // --- Infer average sleep time from last active timestamp ---
        val sleepTimesMs = summaries.mapNotNull { it.inferredSleepTimeMs }.filter { it > 0 }
        if (sleepTimesMs.size >= 2) {
            val avgSleepMs = sleepTimesMs.average().toLong()
            val avgSleepCal = Calendar.getInstance().apply { timeInMillis = avgSleepMs }
            val sleepHour = avgSleepCal.get(Calendar.HOUR_OF_DAY)
            val sleepMin = avgSleepCal.get(Calendar.MINUTE)
            val sleepStr = String.format(Locale.ROOT, "%02d:%02d", sleepHour, sleepMin)
            memoryRepository.saveMemory(
                Memory(
                    type = "INFERRED",
                    key = "inferred_sleep_time",
                    value = "Based on phone usage, user typically stops using phone around $sleepStr",
                    timestampMs = now,
                    source = "DIGITAL_ACTIVITY",
                    confidence = 0.7f,
                    isUserDeletable = true
                )
            )
            synced++
        }

        // --- Infer average daily screen time ---
        val screenTimes = summaries.map { it.totalScreenTimeMinutes }.filter { it > 0 }
        if (screenTimes.isNotEmpty()) {
            val avgScreen = screenTimes.average().toLong()
            val hours = avgScreen / 60
            val mins = avgScreen % 60
            val screenDesc = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            memoryRepository.saveMemory(
                Memory(
                    type = "INFERRED",
                    key = "avg_daily_screen_time",
                    value = "User's average daily screen time is approximately $screenDesc",
                    timestampMs = now,
                    source = "DIGITAL_ACTIVITY",
                    confidence = 0.8f,
                    isUserDeletable = true
                )
            )
            synced++

            // Digital wellbeing context
            val wellbeingNote = when {
                avgScreen > 360L -> "User has high screen time (over 6 hours/day). May benefit from digital wellbeing reminders."
                avgScreen > 240L -> "User has moderate screen time (4-6 hours/day)."
                else -> "User has healthy screen time (under 4 hours/day)."
            }
            memoryRepository.saveMemory(
                Memory(
                    type = "INFERRED",
                    key = "digital_wellbeing_context",
                    value = wellbeingNote,
                    timestampMs = now,
                    source = "DIGITAL_ACTIVITY",
                    confidence = 0.75f,
                    isUserDeletable = true
                )
            )
            synced++
        }

        // --- Top app category ---
        val topCategories = summaries.mapNotNull { it.topCategory.takeIf { c -> c.isNotBlank() } }
        if (topCategories.isNotEmpty()) {
            val mostUsedCategory = topCategories.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (mostUsedCategory != null) {
                val readable = mostUsedCategory.lowercase(Locale.ROOT)
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                memoryRepository.saveMemory(
                    Memory(
                        type = "INFERRED",
                        key = "top_app_category",
                        value = "User's most used app category is $readable",
                        timestampMs = now,
                        source = "DIGITAL_ACTIVITY",
                        confidence = 0.75f,
                        isUserDeletable = true
                    )
                )
                synced++
            }
        }

        // --- Most used single app ---
        val topApps = summaries.mapNotNull { it.topPackageName.takeIf { p -> p.isNotBlank() } }
        if (topApps.isNotEmpty()) {
            val mostUsedApp = topApps.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (mostUsedApp != null) {
                val appName = mostUsedApp.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                memoryRepository.saveMemory(
                    Memory(
                        type = "INFERRED",
                        key = "most_used_app",
                        value = "User's most frequently used app is $appName",
                        timestampMs = now,
                        source = "DIGITAL_ACTIVITY",
                        confidence = 0.7f,
                        isUserDeletable = true
                    )
                )
                synced++
            }
        }

        if (synced > 0) {
            Log.i(TAG, "Synced $synced digital activity insights to memories")
        } else {
            Log.d(TAG, "No new digital activity patterns to sync")
        }
    }
}
