package com.comai.contextengine.context

import com.comai.contextengine.data.ContextCompressionSummary
import com.comai.contextengine.db.DailyLog

/**
 * Compresses daily context logs into concise summaries to save memory and token bandwidth.
 */
class ContextCompressor {

    /**
     * Summarizes a list of daily logs into a compressed text representation and summary object.
     */
    fun compressDailyLogs(logs: List<DailyLog>): ContextCompressionSummary {
        if (logs.isEmpty()) {
            return ContextCompressionSummary(
                summaryText = "No historical context logs recorded.",
                timeSpanMinutes = 0,
                dominantActivity = "STILL",
                primaryLocation = "Home",
                totalDeviationsCount = 0,
                confidenceAverage = 1.0
            )
        }

        val totalDeviations = logs.count { it.routineDeviation }
        val primaryLoc: String = logs.groupingBy { it.location }.eachCount().maxByOrNull { it.value }?.key ?: "Home"
        val dominantAct: String = logs.groupingBy { it.eventType }.eachCount().maxByOrNull { it.value }?.key ?: "STILL"

        val summary = "Span: ${logs.size} log entries over past cycle. Dominant location: $primaryLoc ($dominantAct). Deviations: $totalDeviations."

        return ContextCompressionSummary(
            summaryText = summary,
            timeSpanMinutes = logs.size * 15, // assuming ~15 minute sample intervals
            dominantActivity = dominantAct,
            primaryLocation = primaryLoc,
            totalDeviationsCount = totalDeviations,
            confidenceAverage = 0.95
        )
    }
}
