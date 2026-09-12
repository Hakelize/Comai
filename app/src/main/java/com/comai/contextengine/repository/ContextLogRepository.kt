package com.comai.contextengine.repository

import android.util.Log
import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.models.HistoricalComparisonResult
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

interface ContextLogRepository {
    suspend fun saveContextLog(log: ContextLogEntity): Long
    suspend fun saveContextResult(result: ContextProcessingResult): Long
    suspend fun getTodayContextLogs(): List<ContextLogEntity>
    fun getTodayContextLogsFlow(): Flow<List<ContextLogEntity>>
    suspend fun getHistoricalContextLogs(daysBack: Int = 7): List<ContextLogEntity>
    suspend fun getRecentContextLogs(limit: Int = 50): List<ContextLogEntity>
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<ContextLogEntity>>
    suspend fun compareWithHistoricalPatterns(userContext: UserContext, routineContext: RoutineContext): HistoricalComparisonResult
    suspend fun purgeOldLogs(daysToKeep: Int = 30): Int
}

class ContextLogRepositoryImpl(private val database: ComaiDatabase) : ContextLogRepository {

    override suspend fun saveContextLog(log: ContextLogEntity): Long {
        // Efficiency: Deduplicate identical un-escalated logs within 10 minutes
        val lastLog = database.contextLogDao().getLastLog()
        if (lastLog != null && !log.isEscalated) {
            val deltaMs = log.timestampMs - lastLog.timestampMs
            val isSameState = lastLog.locationPlace == log.locationPlace &&
                    lastLog.activityType == log.activityType &&
                    lastLog.isRoutineDeviation == log.isRoutineDeviation &&
                    lastLog.triggerTask == log.triggerTask

            if (deltaMs < 600_000L && isSameState) { // 10 minutes
                Log.d(TAG, "Skipped duplicate context log insertion (${deltaMs / 1000}s since last log)")
                return lastLog.id
            }
        }

        return database.contextLogDao().insertLog(log)
    }

    override suspend fun saveContextResult(result: ContextProcessingResult): Long {
        val entity = ContextLogEntity(
            timestampMs = System.currentTimeMillis(),
            locationPlace = result.deviceContext.location.locationCategory,
            activityType = result.deviceContext.activity.detectedState.name,
            routineState = result.routineContext.routineState,
            isRoutineDeviation = result.routineContext.isRoutineDeviation,
            deviationMinutes = result.routineContext.deviationMinutes,
            confidenceScore = result.confidence.confidenceScore,
            confidenceLevel = result.confidence.confidenceLevel.name,
            triggerTask = result.contract.task,
            eventType = if (result.routineContext.isRoutineDeviation) "ROUTINE_DEVIATION" else "PERIODIC_LOG",
            isEscalated = result.contract.task != "GENERAL_CHECKIN",
            task = result.contract.task,
            confidence = result.confidence.overallConfidence,
            signalsSummary = "Place: ${result.deviceContext.location.locationCategory} | Activity: ${result.deviceContext.activity.detectedState.name} | Tag: ${result.userContext.broadContextTag}"
        )
        return saveContextLog(entity)
    }

    override suspend fun getTodayContextLogs(): List<ContextLogEntity> {
        val startOfDayMs = getStartOfDayTimestampMs()
        return database.contextLogDao().getTodayLogs(startOfDayMs)
    }

    override fun getTodayContextLogsFlow(): Flow<List<ContextLogEntity>> {
        val startOfDayMs = getStartOfDayTimestampMs()
        return database.contextLogDao().getTodayLogsFlow(startOfDayMs)
    }

    override suspend fun getHistoricalContextLogs(daysBack: Int): List<ContextLogEntity> {
        val cutoffMs = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
        return database.contextLogDao().getLogsForTimeRange(cutoffMs, System.currentTimeMillis())
    }

    override suspend fun getRecentContextLogs(limit: Int): List<ContextLogEntity> {
        return database.contextLogDao().getRecentLogsList(limit)
    }

    override fun getRecentLogsFlow(limit: Int): Flow<List<ContextLogEntity>> {
        return database.contextLogDao().getRecentLogs(limit)
    }

    override suspend fun compareWithHistoricalPatterns(
        userContext: UserContext,
        routineContext: RoutineContext
    ): HistoricalComparisonResult {
        val historicalLogs = getHistoricalContextLogs(daysBack = 14)
        if (historicalLogs.isEmpty()) {
            return HistoricalComparisonResult(
                currentBroadTag = userContext.broadContextTag,
                historicalFrequencyPercentage = 100.0f,
                isConsistentWithHistory = true,
                comparisonSummary = "Insufficient historical logs; assuming baseline consistency."
            )
        }

        val matchingLogs = historicalLogs.filter { log ->
            log.locationPlace.equals(userContext.locationPlaceName, ignoreCase = true)
        }

        val frequencyPct = ((matchingLogs.size.toFloat() / historicalLogs.size.toFloat()) * 100.0f).coerceIn(0.0f, 100.0f)
        val isConsistent = !routineContext.isRoutineDeviation && frequencyPct >= 50.0f

        val summary = if (isConsistent) {
            "Current context (${userContext.broadContextTag} at ${userContext.locationPlaceName}) matches ${String.format("%.1f", frequencyPct)}% of historical observations."
        } else {
            "Current context deviates from historical pattern (${routineContext.deviationReason})."
        }

        return HistoricalComparisonResult(
            currentBroadTag = userContext.broadContextTag,
            historicalFrequencyPercentage = frequencyPct,
            isConsistentWithHistory = isConsistent,
            comparisonSummary = summary
        )
    }

    override suspend fun purgeOldLogs(daysToKeep: Int): Int {
        val cutoffMs = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        return database.contextLogDao().deleteLogsBefore(cutoffMs)
    }

    private fun getStartOfDayTimestampMs(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    companion object {
        private const val TAG = "ContextLogRepository"
    }
}
