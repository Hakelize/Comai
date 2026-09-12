package com.comai.contextengine.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.comai.contextengine.ContextEngineFacade
import com.comai.contextengine.db.ComaiDatabase

/**
 * WorkManager worker executing periodic background context sensing and routine evaluations.
 * Functions as an OS-resilient fallback when ForegroundService is stopped by Doze mode or OS restrictions.
 */
class ContextFallbackWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "ContextFallbackWorker executing periodic background context evaluation...")

        return try {
            val database = ComaiDatabase.getInstance(applicationContext)
            val facade = ContextEngineFacade(applicationContext)

            // 1. Execute deterministic context processing directly in background worker process
            val result = facade.processContext()
            Log.i(TAG, "WorkManager periodic evaluation complete: tag=${result.userContext.broadContextTag}, confidence=${result.confidence.confidenceScore}")

            // 2. Attempt to resurrect ForegroundService if OS permits
            try {
                val serviceIntent = Intent(applicationContext, ContextForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(serviceIntent)
                } else {
                    applicationContext.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.d(TAG, "ForegroundService resurrection skipped by OS background restriction: ${e.message}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ContextFallbackWorker execution", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ContextFallbackWorker"
        const val WORK_NAME = "comai_context_resiliency_pulse"
    }
}
