package com.comai.ui.screens.onboarding

import android.content.Context
import android.content.SharedPreferences
import com.comai.voice.ComaiLanguage

/**
 * Data class representing the user's initial onboarding profile.
 */
data class UserOnboardingProfile(
    val name: String = "",
    val preferredLanguage: ComaiLanguage = ComaiLanguage.ENGLISH,
    val gender: String = "Prefer not to say",
    val weekdayType: String = "Work",
    val placeName: String = "",
    val workplace: String = "",
    val college: String = "",
    val wakeTime: String = "",
    val leaveHomeTime: String = "",
    val returnHomeTime: String = "",
    val sleepTime: String = "",
    val travelMode: String = "Car",
    val workSchedule: String = if (leaveHomeTime.isNotBlank() && returnHomeTime.isNotBlank()) "$leaveHomeTime - $returnHomeTime" else ""
)

/**
 * Manages onboarding status and basic user profile persistence in SharedPreferences.
 */
open class OnboardingPreferences(context: Context? = null) {

    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    open fun isOnboardingCompleted(): Boolean {
        return prefs?.getBoolean(KEY_ONBOARDING_COMPLETED, false) ?: false
    }

    open fun setOnboardingCompleted(completed: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_COMPLETED, completed)?.apply()
    }

    open fun getProfile(): UserOnboardingProfile {
        val name = prefs?.getString(KEY_USER_NAME, "") ?: ""
        val langStr = prefs?.getString(KEY_PREFERRED_LANGUAGE, ComaiLanguage.ENGLISH.name) ?: ComaiLanguage.ENGLISH.name
        val language = try {
            ComaiLanguage.valueOf(langStr)
        } catch (_: Exception) {
            ComaiLanguage.ENGLISH
        }
        val gender = prefs?.getString(KEY_GENDER, "Prefer not to say") ?: "Prefer not to say"
        val weekdayType = prefs?.getString(KEY_WEEKDAY_TYPE, "Work") ?: "Work"
        val placeName = prefs?.getString(KEY_PLACE_NAME, "") ?: ""
        val workplace = prefs?.getString(KEY_WORKPLACE, placeName) ?: placeName
        val college = prefs?.getString(KEY_COLLEGE, "") ?: ""
        val wakeTime = prefs?.getString(KEY_WAKE_TIME, "") ?: ""
        val leaveHomeTime = prefs?.getString(KEY_LEAVE_HOME_TIME, "") ?: ""
        val returnHomeTime = prefs?.getString(KEY_RETURN_HOME_TIME, "") ?: ""
        val sleepTime = prefs?.getString(KEY_SLEEP_TIME, "") ?: ""
        val travelMode = prefs?.getString(KEY_TRAVEL_MODE, "Car") ?: "Car"

        return UserOnboardingProfile(
            name = name,
            preferredLanguage = language,
            gender = gender,
            weekdayType = weekdayType,
            placeName = placeName.ifBlank { workplace.ifBlank { college } },
            workplace = workplace,
            college = college,
            wakeTime = wakeTime,
            leaveHomeTime = leaveHomeTime,
            returnHomeTime = returnHomeTime,
            sleepTime = sleepTime,
            travelMode = travelMode
        )
    }

    open fun saveProfile(profile: UserOnboardingProfile) {
        prefs?.edit()
            ?.putString(KEY_USER_NAME, profile.name)
            ?.putString(KEY_PREFERRED_LANGUAGE, profile.preferredLanguage.name)
            ?.putString(KEY_GENDER, profile.gender)
            ?.putString(KEY_WEEKDAY_TYPE, profile.weekdayType)
            ?.putString(KEY_PLACE_NAME, profile.placeName)
            ?.putString(KEY_WORKPLACE, profile.workplace)
            ?.putString(KEY_COLLEGE, profile.college)
            ?.putString(KEY_WAKE_TIME, profile.wakeTime)
            ?.putString(KEY_LEAVE_HOME_TIME, profile.leaveHomeTime)
            ?.putString(KEY_RETURN_HOME_TIME, profile.returnHomeTime)
            ?.putString(KEY_SLEEP_TIME, profile.sleepTime)
            ?.putString(KEY_TRAVEL_MODE, profile.travelMode)
            ?.putString(KEY_WORK_SCHEDULE, profile.workSchedule)
            ?.apply()
    }

    open fun reset() {
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        private const val PREFS_NAME = "comai_onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        private const val KEY_GENDER = "gender"
        private const val KEY_WEEKDAY_TYPE = "weekday_type"
        private const val KEY_PLACE_NAME = "place_name"
        private const val KEY_WORKPLACE = "workplace"
        private const val KEY_COLLEGE = "college"
        private const val KEY_WAKE_TIME = "wake_time"
        private const val KEY_LEAVE_HOME_TIME = "leave_home_time"
        private const val KEY_RETURN_HOME_TIME = "return_home_time"
        private const val KEY_SLEEP_TIME = "sleep_time"
        private const val KEY_TRAVEL_MODE = "travel_mode"
        private const val KEY_WORK_SCHEDULE = "work_schedule"
    }
}
