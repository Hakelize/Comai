package com.comai.memory

import android.util.Log
import com.comai.contextengine.db.Memory
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.screens.onboarding.UserOnboardingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Syncs user onboarding profile fields to the Room `memories` table so the AI engine
 * can answer personal questions ("what's my name?", "when do I usually wake up?") correctly.
 *
 * This is the critical bridge that was missing — onboarding data was saved only to
 * SharedPreferences and never reached the LLM context.
 *
 * Call [syncProfile] on every app start (after onboarding completes) to keep memories fresh.
 * Uses upsert logic so re-syncing is always safe and idempotent.
 */
object OnboardingMemorySyncer {

    private const val TAG = "OnboardingMemorySyncer"

    /**
     * Convenience overload that reads the profile directly from [OnboardingPreferences].
     * Only syncs if onboarding has been completed.
     */
    fun syncIfCompleted(
        preferences: OnboardingPreferences,
        memoryRepository: MemoryRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        if (!preferences.isOnboardingCompleted()) {
            Log.d(TAG, "Onboarding not yet completed — skipping sync")
            return
        }
        val profile = preferences.getProfile()
        syncProfile(profile, memoryRepository, scope)
    }

    /**
     * Converts all non-blank fields of [profile] into [Memory] entities and upserts them.
     */
    fun syncProfile(
        profile: UserOnboardingProfile,
        memoryRepository: MemoryRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            try {
                val memories = buildMemoriesFromProfile(profile)
                var synced = 0
                for (memory in memories) {
                    memoryRepository.saveMemory(memory)
                    synced++
                }
                Log.i(TAG, "Synced $synced onboarding memories to Room")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing onboarding profile to memories: ${e.message}", e)
            }
        }
    }

    /**
     * Converts a [UserOnboardingProfile] into a list of [Memory] entries.
     * Only creates memories for non-blank fields.
     */
    fun buildMemoriesFromProfile(profile: UserOnboardingProfile): List<Memory> {
        val now = System.currentTimeMillis()
        val memories = mutableListOf<Memory>()

        // User name
        if (profile.name.isNotBlank()) {
            memories += Memory(
                type = "PERSONAL_KNOWLEDGE",
                key = "user_name",
                value = "User's name is ${profile.name.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Gender
        if (profile.gender.isNotBlank() && profile.gender != "Prefer not to say") {
            memories += Memory(
                type = "PERSONAL_KNOWLEDGE",
                key = "user_gender",
                value = "User's gender: ${profile.gender.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Weekday type (Work/College/Both/Other)
        if (profile.weekdayType.isNotBlank()) {
            memories += Memory(
                type = "CONTEXT",
                key = "weekday_routine_type",
                value = "User's weekday routine is: ${profile.weekdayType.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Workplace
        if (profile.workplace.isNotBlank()) {
            memories += Memory(
                type = "CONTEXT",
                key = "workplace",
                value = profile.workplace.trim(),
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // College
        if (profile.college.isNotBlank()) {
            memories += Memory(
                type = "CONTEXT",
                key = "college",
                value = profile.college.trim(),
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Wake time
        if (profile.wakeTime.isNotBlank()) {
            memories += Memory(
                type = "PREFERENCE",
                key = "baseline_wake_time",
                value = "User usually wakes up around ${profile.wakeTime.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Leave home time (departure)
        if (profile.leaveHomeTime.isNotBlank()) {
            val placeLabel = when (profile.weekdayType) {
                "College" -> "college"
                "Both" -> "work/college"
                else -> "work"
            }
            memories += Memory(
                type = "PREFERENCE",
                key = "preferred_work_departure",
                value = "User usually leaves for $placeLabel around ${profile.leaveHomeTime.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Return home time
        if (profile.returnHomeTime.isNotBlank()) {
            memories += Memory(
                type = "PREFERENCE",
                key = "preferred_return_home_time",
                value = "User usually returns home around ${profile.returnHomeTime.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Sleep time
        if (profile.sleepTime.isNotBlank()) {
            memories += Memory(
                type = "PREFERENCE",
                key = "baseline_sleep_time",
                value = "User usually goes to sleep around ${profile.sleepTime.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Travel mode
        if (profile.travelMode.isNotBlank() && profile.travelMode != "Car") {
            memories += Memory(
                type = "PREFERENCE",
                key = "travel_mode",
                value = "User prefers to travel by ${profile.travelMode.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        } else if (profile.travelMode == "Car") {
            memories += Memory(
                type = "PREFERENCE",
                key = "travel_mode",
                value = "User travels by car",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // Work schedule summary (if both leave and return times are set)
        if (profile.leaveHomeTime.isNotBlank() && profile.returnHomeTime.isNotBlank()) {
            memories += Memory(
                type = "CONTEXT",
                key = "daily_schedule_summary",
                value = "User's daily schedule: leave at ${profile.leaveHomeTime.trim()}, return home at ${profile.returnHomeTime.trim()}",
                timestampMs = now,
                source = "ONBOARDING",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        return memories
    }
}
