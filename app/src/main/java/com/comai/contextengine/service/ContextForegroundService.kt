package com.comai.contextengine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.comai.contextengine.ContextEngineFacade
import com.comai.contextengine.context.ActivityRecognitionManager
import com.comai.contextengine.contract.ContractAssembler
import com.comai.contextengine.contract.ContractValidator
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput
import com.comai.contextengine.rules.RuleEvaluationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Android Foreground Service running Person 1's background context engine.
 * Periodically senses device context, evaluates routine adherence and proactive rules,
 * persists context events locally in Room, and exposes contracts via listeners for Person 2's AI Engine.
 */
class ContextForegroundService : Service() {

    private val binder = LocalBinder()
    private val outputListeners = mutableListOf<ContextOutputListener>()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var collectionJob: Job? = null

    val facade: ContextEngineFacade by lazy { ContextEngineFacade(this) }
    val stateRestorer: ContextStateRestorer by lazy { ContextStateRestorer(this) }
    val ruleEngine: RuleEngine get() = facade.ruleEngine
    val activityManager by lazy { ActivityRecognitionManager(this) }

    inner class LocalBinder : Binder() {
        fun getService(): ContextForegroundService = this@ContextForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ContextForegroundService creating...")
        stateRestorer.isServiceRunning = true
        startAsForeground()
        scheduleWorkManagerFallback()

        facade.ruleEngine.loadDefaultRules()
        activityManager.startMonitoring()
        startPeriodicContextCollection()

        (application as? com.comai.ComaiApplication)?.contextBridge?.let { bridge ->
            registerOutputListener(bridge)
            Log.i(TAG, "Successfully auto-registered ContextBridge with ContextForegroundService")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ContextForegroundService onStartCommand with START_STICKY")
        startAsForeground()
        scheduleWorkManagerFallback()

        if (intent?.action == ACTION_TRIGGER_EVALUATION) {
            val time = intent.getStringExtra(EXTRA_TIME) ?: "18:43"
            val location = intent.getStringExtra(EXTRA_LOCATION) ?: "Office"
            val departure = intent.getStringExtra(EXTRA_DEPARTURE_TIME) ?: "17:30"
            val isWork = intent.getBooleanExtra(EXTRA_IS_WORKDAY, true)

            Log.i(TAG, "Received trigger action for time: $time at $location")
            val customInput = RuleEvaluationInput(
                currentTime = time,
                currentLocation = location,
                historicalOfficeDepartureTime = departure,
                isWorkDay = isWork
            )
            serviceScope.launch {
                evaluateAndEscalate(customInput)
            }
        }

        return START_STICKY
    }

    /**
     * Non-blocking periodic loop collecting context and running rule evaluation every 15 minutes.
     * Prevents battery drain and avoids tight loops.
     */
    private fun startPeriodicContextCollection() {
        collectionJob?.cancel()
        collectionJob = serviceScope.launch {
            Log.i(TAG, "Starting background periodic context collection loop (15-min interval)")
            while (serviceJob.isActive) {
                try {
                    val result: ContextProcessingResult = facade.processContext()
                    stateRestorer.recordEvaluationSuccess(result.userContext.broadContextTag)
                    facade.logger.logContextProcessingResult(result)
                    Log.i(TAG, "Background context evaluation completed: broadTag=${result.userContext.broadContextTag}, deviation=${result.routineContext.isRoutineDeviation}, confidence=${result.confidence.confidenceScore}")

                    if (result.routineContext.isRoutineDeviation || result.contract.task != "GENERAL_CHECKIN") {
                        emitEscalatedContext(ContractValidator.toJson(result.contract), result.contract)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic context collection", e)
                }
                delay(COLLECTION_INTERVAL_MS)
            }
        }
    }

    /**
     * On-demand Context Engine evaluation pipeline execution.
     */
    suspend fun evaluateAndEscalate(input: RuleEvaluationInput? = null): RuleEvaluationResult? {
        val userState = activityManager.getCurrentUserState()
        val evaluationInput = input ?: run {
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdfDay = SimpleDateFormat("EEEE", Locale.US)
            val now = Date()
            val timeStr = sdfTime.format(now)
            val dayStr = sdfDay.format(now).uppercase(Locale.US)
            val isWork = dayStr !in listOf("SATURDAY", "SUNDAY")
            RuleEvaluationInput(
                currentTime = timeStr,
                dayOfWeek = dayStr,
                isWorkDay = isWork,
                currentLocation = "Office",
                wakeTime = activityManager.detector.getFormattedWakeTime()
            )
        }

        Log.d(TAG, "Context generated: time=${evaluationInput.currentTime}, location=${evaluationInput.currentLocation}")

        val ruleResult = ruleEngine.evaluate(evaluationInput)
        if (ruleResult != null) {
            Log.i(TAG, "Rule triggered: ${ruleResult.triggeredRule.name}, task: ${ruleResult.task}")
            val (contract, jsonOutput) = ContractAssembler.assemble(ruleResult, userState)
            ContractValidator.validate(contract)
            emitEscalatedContext(jsonOutput, contract)
        } else {
            Log.d(TAG, "No rule triggered for context at ${evaluationInput.currentTime}")
        }
        return ruleResult
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed startForeground with type, falling back: ${e.message}")
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        Log.w(TAG, "ContextForegroundService destroyed. Cleaning up background jobs...")
        stateRestorer.isServiceRunning = false
        collectionJob?.cancel()
        serviceScope.cancel()
        (application as? com.comai.ComaiApplication)?.contextBridge?.let { bridge ->
            unregisterOutputListener(bridge)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ContextFGService"
        const val CHANNEL_ID = "comai_context_channel"
        const val NOTIFICATION_ID = 1001
        const val WORKER_INTERVAL_MINUTES = 15L
        private const val COLLECTION_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        const val ACTION_TRIGGER_EVALUATION = "com.comai.action.TRIGGER_CONTEXT_EVALUATION"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_DEPARTURE_TIME = "extra_departure_time"
        const val EXTRA_IS_WORKDAY = "extra_is_workday"
    }
}
