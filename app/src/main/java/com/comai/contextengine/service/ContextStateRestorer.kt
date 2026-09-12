package com.comai.contextengine.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persistent state restorer for Person 1's Context Engine.
 * Manages atomic service running state, last evaluation timestamp, and duplicate service prevention.
 */
class ContextStateRestorer(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    var lastEvaluationTimestampMs: Long
        get() = prefs.getLong(KEY_LAST_EVALUATION_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_EVALUATION_MS, value).apply()

    var lastBroadContextTag: String
        get() = prefs.getString(KEY_LAST_BROAD_TAG, "GENERAL_DAILY") ?: "GENERAL_DAILY"
        set(value) = prefs.edit().putString(KEY_LAST_BROAD_TAG, value).apply()

    fun recordEvaluationSuccess(broadTag: String, timestampMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_EVALUATION_MS, timestampMs)
            .putString(KEY_LAST_BROAD_TAG, broadTag)
            .apply()
        Log.d(TAG, "Recorded evaluation state: tag=$broadTag at $timestampMs")
    }

    companion object {
        private const val TAG = "ContextStateRestorer"
        private const val PREFS_NAME = "comai_context_state_prefs"
        private const val KEY_SERVICE_RUNNING = "is_service_running"
        private const val KEY_LAST_EVALUATION_MS = "last_evaluation_ms"
        private const val KEY_LAST_BROAD_TAG = "last_broad_tag"
    }
}
