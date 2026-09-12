package com.comai.contextengine.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for querying, persisting, and comparing historical context engine logs.
 */
@Dao
interface ContextLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ContextLogEntity): Long

    @Query("SELECT * FROM context_logs ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLastLog(): ContextLogEntity?

    @Query("SELECT * FROM context_logs ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<ContextLogEntity>>

    @Query("SELECT * FROM context_logs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentLogsList(limit: Int = 50): List<ContextLogEntity>

    @Query("SELECT * FROM context_logs WHERE timestampMs >= :startTimeMs ORDER BY timestampMs DESC")
    suspend fun getTodayLogs(startTimeMs: Long): List<ContextLogEntity>

    @Query("SELECT * FROM context_logs WHERE timestampMs >= :startTimeMs ORDER BY timestampMs DESC")
    fun getTodayLogsFlow(startTimeMs: Long): Flow<List<ContextLogEntity>>

    @Query("SELECT * FROM context_logs WHERE timestampMs < :endTimeMs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getHistoricalLogs(endTimeMs: Long, limit: Int = 100): List<ContextLogEntity>

    @Query("SELECT * FROM context_logs WHERE isEscalated = 1 ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getEscalatedLogs(limit: Int = 20): List<ContextLogEntity>

    @Query("SELECT * FROM context_logs WHERE timestampMs >= :startTimeMs AND timestampMs <= :endTimeMs ORDER BY timestampMs DESC")
    suspend fun getLogsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<ContextLogEntity>

    @Query("DELETE FROM context_logs WHERE timestampMs < :beforeTimestampMs")
    suspend fun deleteLogsBefore(beforeTimestampMs: Long): Int
}
