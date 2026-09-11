package com.comai.contextengine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.comai.contextengine.contract.SharedContextContract
import java.util.concurrent.TimeUnit

class ContextForegroundService : Service() {

    private val binder = LocalBinder()
    private val outputListeners = mutableListOf<ContextOutputListener>()

    inner class LocalBinder : Binder() {
        fun getService(): ContextForegroundService = this@ContextForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ContextForegroundService creating...")
        startAsForeground()
        scheduleWorkManagerFallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ContextForegroundService started with START_STICKY")
        startAsForeground()
        scheduleWorkManagerFallback()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun registerOutputListener(listener: ContextOutputListener) {
        synchronized(outputListeners) {
            if (!outputListeners.contains(listener)) {
                outputListeners.add(listener)
                Log.d(TAG, "Registered ContextOutputListener for Person 2 LLM Integration")
            }
        }
    }

    fun unregisterOutputListener(listener: ContextOutputListener) {
        synchronized(outputListeners) {
            outputListeners.remove(listener)
        }
    }

    fun emitEscalatedContext(jsonContract: String, contractObject: SharedContextContract) {
        Log.i(TAG, "Escalating context signal to Person 2 LLM. Task: ${contractObject.task}")
        synchronized(outputListeners) {
            for (listener in outputListeners) {
                try {
                    listener.onContextEscalated(jsonContract, contractObject)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying ContextOutputListener", e)
                }
            }
        }
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Comai Context Engine")
            .setContentText("Always-alive background routine & context monitoring")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Comai Context Sensing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Low priority persistent notification keeping Comai Context Engine alive"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun scheduleWorkManagerFallback() {
        val workRequest = PeriodicWorkRequestBuilder<ContextFallbackWorker>(
            WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContextFallbackWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "ContextForegroundService destroyed. Attempting quick restart...")
    }

    companion object {
        private const val TAG = "ContextFGService"
        const val CHANNEL_ID = "comai_context_channel"
        const val NOTIFICATION_ID = 1001
        const val WORKER_INTERVAL_MINUTES = 15L
    }
}
