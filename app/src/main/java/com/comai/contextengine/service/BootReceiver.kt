package com.comai.contextengine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.i(TAG, "BootReceiver triggered with action: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val serviceIntent = Intent(context, ContextForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "ContextForegroundService resurrected on boot/update.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ContextForegroundService from BootReceiver", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
