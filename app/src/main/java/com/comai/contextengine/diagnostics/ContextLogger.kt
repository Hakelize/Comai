package com.comai.contextengine.diagnostics

import android.util.Log
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.repository.ContextLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Diagnostic logger for recording RAM module execution, signal confidence, and rule escalation events.
 */
class ContextLogger(
    private val logRepository: ContextLogRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    fun logContextProcessingResult(result: ContextProcessingResult) {
        Log.d(TAG, "Context Processed: broadTag=${result.userContext.broadContextTag}, deviation=${result.routineContext.isRoutineDeviation}")
        logRepository?.let { repo ->
            scope.launch {
                try {
                    repo.saveContextResult(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist ContextProcessingResult", e)
                }
            }
        }
    }

    fun logEvaluation(task: String, confidence: Double, signalsSummary: String, isEscalated: Boolean) {
        Log.d(TAG, "Context Evaluation: task=$task, confidence=$confidence, escalated=$isEscalated")
        logRepository?.let { repo ->
            scope.launch {
                try {
                    repo.saveContextLog(
                        ContextLogEntity(
                            timestampMs = System.currentTimeMillis(),
                            eventType = if (isEscalated) "RULE_ESCALATED" else "EVALUATION_PASS",
                            triggerTask = task,
                            task = task,
                            confidenceScore = confidence.toFloat(),
                            confidence = confidence,
                            signalsSummary = signalsSummary,
                            isEscalated = isEscalated
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist context log entity", e)
                }
            }
        }
    }

    fun logContractEscalation(contract: SharedContextContract) {
        Log.i(TAG, "Context Escalated -> Task: ${contract.task}, Location: ${contract.contextSignals.location}")
        logEvaluation(
            task = contract.task,
            confidence = 1.0,
            signalsSummary = "Time: ${contract.contextSignals.time}, Location: ${contract.contextSignals.location}, RoutineDeviation: ${contract.contextSignals.routineDeviation}",
            isEscalated = true
        )
    }

    companion object {
        private const val TAG = "ContextLogger"
    }
}
