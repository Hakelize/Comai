package com.comai.digitalactivity.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages local user preferences and privacy settings for Digital Activity monitoring.
 */
class DigitalActivityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "comai_digital_activity_prefs"
        private const val KEY_MONITORING_ENABLED = "key_monitoring_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
    }
}
