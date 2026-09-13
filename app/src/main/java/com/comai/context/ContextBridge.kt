package com.comai.context

import android.util.Log
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.service.ContextOutputListener
import com.comai.engine.AIEngine
import com.comai.engine.models.AIResponse
import com.comai.engine.models.ContextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-scoped bridge between the Context Engine (background sensing and rules)
 * and the presentation/AI layer (AIEngine -> ChatViewModel -> UI + TTS).
 *
 * Implements [ContextOutputListener] to consume escalated context contracts from
 * [com.comai.contextengine.service.ContextForegroundService] or direct pipeline evaluations.
 */
class ContextBridge(
    private val aiEngine: AIEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val memoryRepository: com.comai.memory.MemoryRepository? = null,
    private val memoryContextProvider: com.comai.memory.MemoryContextProvider? = null
) : ContextOutputListener {

    private val _proactiveEvents = MutableSharedFlow<AIResponse>(extraBufferCapacity = 16)
    val proactiveEvents: SharedFlow<AIResponse> = _proactiveEvents.asSharedFlow()

    private val _lastEscalatedContract = MutableStateFlow<SharedContextContract?>(null)
    val lastEscalatedContract: StateFlow<SharedContextContract?> = _lastEscalatedContract.asStateFlow()

    // Tracking last trigger time ms per task to enforce cooldowns
    private val taskLastTriggerMs = ConcurrentHashMap<String, Long>()

    /**
     * Cooldown periods per task in milliseconds.
     * Overtime check-in has a 60-minute cooldown to prevent spamming the user.
     */
    fun getCooldownMillis(task: String): Long {
        return when {
            task.contains("OVERTIME", ignoreCase = true) -> 60 * 60 * 1000L // 60 mins
            task.contains("LUNCH", ignoreCase = true) -> 60 * 60 * 1000L    // 60 mins
            task.contains("MEDICATION", ignoreCase = true) -> 60 * 60 * 1000L // 60 mins
            else -> 15 * 60 * 1000L // 15 mins default
        }
    }

    /**
     * Checks whether a task is currently cooled down or eligible to trigger.
     */
    fun isEligibleToTrigger(task: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val lastTrigger = taskLastTriggerMs[task] ?: return true
        val cooldown = getCooldownMillis(task)
        return (currentTimeMs - lastTrigger) >= cooldown
    }

    /**
     * Callback from Context Engine when a rule fires and escalates context.
     */
    override fun onContextEscalated(jsonContract: String, contractObject: SharedContextContract) {
        Log.i(TAG, "Context escalated: task=${contractObject.task}, location=${contractObject.contextSignals.location}")
        _lastEscalatedContract.value = contractObject

        val taskKey = contractObject.task
        val now = System.currentTimeMillis()

        if (!isEligibleToTrigger(taskKey, now)) {
            val elapsedSecs = (now - (taskLastTriggerMs[taskKey] ?: 0L)) / 1000
            Log.i(TAG, "Duplicate suppressed: task=$taskKey (cooldown active, elapsed: ${elapsedSecs}s)")
            return
        }

        taskLastTriggerMs[taskKey] = now

        scope.launch {
            try {
                var contextInput = ContextInputMapper.toContextInput(contractObject)
                val provider = memoryContextProvider ?: memoryRepository?.let { com.comai.memory.MemoryContextProvider(it) }
                if (provider != null) {
                    val relevant = provider.getFormattedMemoryContext(
                        task = contextInput.task,
                        signals = contextInput.contextSignals
                    )
                    val memList = provider.getMemoryContextList(
                        task = contextInput.task,
                        signals = contextInput.contextSignals
                    )
                    if (!relevant.isNullOrBlank() || memList.isNotEmpty()) {
                        contextInput = contextInput.copy(
                            retrievedData = relevant,
                            memoryContext = memList
                        )
                    }
                }
                Log.i(TAG, "Context mapped: task=${contractObject.task} -> ContextInput")
                Log.i(TAG, "AI processing: task=${contextInput.task}")
                val aiResponse = aiEngine.process(contextInput)
                Log.i(TAG, "AI response: action=${aiResponse.action}")
                _proactiveEvents.emit(aiResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing escalated context through AI Engine", e)
            }
        }
    }

    /**
     * Direct dispatch method for tests and UI simulations that bypasses or checks cooldown.
     */
    suspend fun dispatchDirect(
        contract: SharedContextContract,
        bypassCooldown: Boolean = false,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AIResponse? {
        val taskKey = contract.task
        if (!bypassCooldown && !isEligibleToTrigger(taskKey, currentTimeMs)) {
            Log.i(TAG, "Duplicate suppressed: task=$taskKey (cooldown active)")
            return null
        }

        taskLastTriggerMs[taskKey] = currentTimeMs
        _lastEscalatedContract.value = contract

        var contextInput = ContextInputMapper.toContextInput(contract)
        val provider = memoryContextProvider ?: memoryRepository?.let { com.comai.memory.MemoryContextProvider(it) }
        if (provider != null) {
            val relevant = provider.getFormattedMemoryContext(
                task = contextInput.task,
                signals = contextInput.contextSignals
            )
            val memList = provider.getMemoryContextList(
                task = contextInput.task,
                signals = contextInput.contextSignals
            )
            if (!relevant.isNullOrBlank() || memList.isNotEmpty()) {
                contextInput = contextInput.copy(
                    retrievedData = relevant,
                    memoryContext = memList
                )
            }
        }
        Log.i(TAG, "Context mapped: task=${contract.task} -> ContextInput")
        Log.i(TAG, "AI processing: task=${contextInput.task}")
        val aiResponse = aiEngine.process(contextInput)
        Log.i(TAG, "AI response: action=${aiResponse.action}")
        _proactiveEvents.emit(aiResponse)
        return aiResponse
    }

    /**
     * Reset cooldowns (primarily used for unit testing).
     */
    fun clearCooldowns() {
        taskLastTriggerMs.clear()
    }

    companion object {
        private const val TAG = "ContextBridge"
    }
}
