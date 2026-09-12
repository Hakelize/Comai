package com.comai.contextengine.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Handles scheduling of background context sampling via AlarmManager and WorkManager fallback.
 */
class ContextScheduler(private val context: Context) {

    /**
     * Schedules periodic fallback work using WorkManager (every 15 minutes).
     */
    fun scheduleFallbackWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ContextFallbackWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Scheduled ContextFallbackWorker successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager fallback worker", e)
        }
    }

    /**
     * Schedules exact or repeating background alarm for context checks.
     */
    fun scheduleBackgroundAlarm(intervalMs: Long = 15 * 60 * 1000L) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ContextForegroundService::class.java).apply {
                action = ContextForegroundService.ACTION_TRIGGER_EVALUATION
            }
            
            val pendingIntent = PendingIntent.getService(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + intervalMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.i(TAG, "Scheduled background context alarm for +${intervalMs / 1000}s.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background alarm", e)
        }
    }

    companion object {
        private const val TAG = "ContextScheduler"
        private const val WORK_NAME = "comai_context_fallback_work"
    }
}
