package com.comai.contextengine.diagnostics

import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory diagnostic metrics counter for monitoring RAM module performance.
 */
object ContextMetrics {

    private val totalEvaluations = AtomicLong(0)
    private val totalEscalations = AtomicLong(0)
    private val totalRoutineDeviations = AtomicLong(0)

    fun recordEvaluation() {
        totalEvaluations.incrementAndGet()
    }

    fun recordEscalation() {
        totalEscalations.incrementAndGet()
    }

    fun recordRoutineDeviation() {
        totalRoutineDeviations.incrementAndGet()
    }

    fun getMetricsSummary(): String {
        return "Evaluations: ${totalEvaluations.get()}, Escalations: ${totalEscalations.get()}, Deviations: ${totalRoutineDeviations.get()}"
    }

    fun reset() {
        totalEvaluations.set(0)
        totalEscalations.set(0)
        totalRoutineDeviations.set(0)
    }
}
