package com.comai.contextengine.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ContextFallbackWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing WorkManager periodic resiliency check for Context Engine...")
        try {
            val serviceIntent = Intent(applicationContext, ContextForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart ContextForegroundService from WorkManager fallback", e)
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        const val TAG = "ContextFallbackWorker"
        const val WORK_NAME = "comai_context_resiliency_pulse"
    }
}
