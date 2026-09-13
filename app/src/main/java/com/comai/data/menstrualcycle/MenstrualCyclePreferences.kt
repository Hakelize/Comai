package com.comai.data.menstrualcycle

import android.content.Context
import android.content.SharedPreferences

/**
 * Local-first, privacy-respecting SharedPreferences persistence for menstrual cycle parameters.
 * Kept entirely on-device and never uploaded to any cloud or external service.
 */
class MenstrualCyclePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCycleData(): MenstrualCycleData {
        val isConfigured = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        val lastStart = prefs.getLong(KEY_LAST_START_DATE_MS, 0L)
        val cycleLength = prefs.getInt(KEY_CYCLE_LENGTH_DAYS, 28)
        val duration = prefs.getInt(KEY_PERIOD_DURATION_DAYS, 5)
        val reminders = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)

        return MenstrualCycleData(
            lastPeriodStartDateMs = lastStart,
            typicalCycleLengthDays = cycleLength,
            periodDurationDays = duration,
            isRemindersEnabled = reminders,
            isConfigured = isConfigured
        )
    }

    fun saveCycleData(data: MenstrualCycleData) {
        prefs.edit()
            .putBoolean(KEY_IS_CONFIGURED, data.isConfigured)
            .putLong(KEY_LAST_START_DATE_MS, data.lastPeriodStartDateMs)
            .putInt(KEY_CYCLE_LENGTH_DAYS, data.typicalCycleLengthDays)
            .putInt(KEY_PERIOD_DURATION_DAYS, data.periodDurationDays)
            .putBoolean(KEY_REMINDERS_ENABLED, data.isRemindersEnabled)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "comai_menstrual_cycle_prefs"
        private const val KEY_IS_CONFIGURED = "key_is_configured"
        private const val KEY_LAST_START_DATE_MS = "key_last_start_date_ms"
        private const val KEY_CYCLE_LENGTH_DAYS = "key_cycle_length_days"
        private const val KEY_PERIOD_DURATION_DAYS = "key_period_duration_days"
        private const val KEY_REMINDERS_ENABLED = "key_reminders_enabled"
    }
}
