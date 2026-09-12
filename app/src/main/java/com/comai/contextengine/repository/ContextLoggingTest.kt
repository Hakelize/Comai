package com.comai.contextengine.repository

import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unit Test Harness for Context Logging & Historical Comparison.
 * Exercises:
 * 1. saveContextLog
 * 2. getTodayContextLogs
 * 3. getHistoricalContextLogs
 * 4. getRecentContextLogs
 * 5. compareWithHistoricalPatterns
 * 6. Duplicate log throttling
 */
object ContextLoggingTest {

    suspend fun runVerification(database: ComaiDatabase): Boolean = withContext(Dispatchers.IO) {
        val repository: ContextLogRepository = ContextLogRepositoryImpl(database)
        val nowMs = System.currentTimeMillis()

        // 1. Test saveContextLog & Deduplication
        val log1 = ContextLogEntity(
            timestampMs = nowMs,
            locationPlace = "Office",
            activityType = "STILL",
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = false,
            confidenceScore = 0.95f,
            triggerTask = "WORK_ROUTINE_RECOGNITION"
        )
        val id1 = repository.saveContextLog(log1)

        // Duplicate log 5 seconds later should be throttled
        val duplicateLog = log1.copy(timestampMs = nowMs + 5_000L)
        val id2 = repository.saveContextLog(duplicateLog)
        val deduplicated = (id1 == id2)

        // 2. Test getTodayContextLogs & getRecentContextLogs
        val todayLogs = repository.getTodayContextLogs()
        val recentLogs = repository.getRecentContextLogs(limit = 10)

        val hasTodayData = todayLogs.isNotEmpty()
        val hasRecentData = recentLogs.isNotEmpty()

        // 3. Test getHistoricalContextLogs
        val historical = repository.getHistoricalContextLogs(daysBack = 7)

        // 4. Test compareWithHistoricalPatterns
        val userContext = UserContext(broadContextTag = "OFFICE_LATE_DEPARTURE", locationPlaceName = "Office")
        val routineContext = RoutineContext(isRoutineDeviation = false)

        val comparison = repository.compareWithHistoricalPatterns(userContext, routineContext)

        val isSuccess = deduplicated && hasTodayData && hasRecentData && comparison.currentBroadTag == "OFFICE_LATE_DEPARTURE"

        println("=== CONTEXT LOGGING & HISTORICAL COMPARISON TEST ===")
        println("Deduplication Working: $deduplicated")
        println("Today Logs Count: ${todayLogs.size}")
        println("Recent Logs Count: ${recentLogs.size}")
        println("Historical Comparison: ${comparison.comparisonSummary}")
        println("Overall Test Passed: $isSuccess")

        isSuccess
    }
}
