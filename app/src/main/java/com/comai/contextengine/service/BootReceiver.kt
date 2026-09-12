package com.comai.contextengine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver - Resurrects Person 1's Context Engine and restores background work upon device boot or app update.
 * Gracefully handles Android 12+ and 14+ background startup restrictions using WorkManager fallback.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        Log.i(TAG, "BootReceiver triggered with action: $action")

        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validActions) {
            // 1. Recover scheduled background WorkManager & Alarm work
            val scheduler = ContextScheduler(context)
            scheduler.scheduleFallbackWorker()
            scheduler.scheduleBackgroundAlarm()

            // 2. Attempt safe ForegroundService launch
            val serviceIntent = Intent(context, ContextForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "ContextForegroundService started safely from BootReceiver")
            } catch (e: Exception) {
                // OS restricted background FGS launch (Android 12+/14+ restriction). Gracefully degrade to WorkManager fallback.
                Log.w(TAG, "OS restricted direct ForegroundService start from background (${e.message}). WorkManager fallback activated.")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
