package com.comai.contextengine.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_log")
data class DailyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String,
    val location: String = "Home",
    val routineDeviation: Boolean = false
)

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_log ORDER BY timestampMs DESC")
    suspend fun getAllLogs(): List<DailyLog>

    @Query("SELECT * FROM daily_log ORDER BY timestampMs DESC")
    fun getAllLogsFlow(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_log WHERE eventType = :type ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getLogsByType(type: String, limit: Int = 20): List<DailyLog>

    @Query("SELECT * FROM daily_log WHERE timestampMs >= :startTimeMs ORDER BY timestampMs ASC")
    suspend fun getLogsSince(startTimeMs: Long): List<DailyLog>

    @Query("SELECT * FROM daily_log WHERE timestampMs >= :startTimeMs AND timestampMs <= :endTimeMs ORDER BY timestampMs DESC")
    suspend fun getLogsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<DailyLog>

    @Query("SELECT * FROM daily_log WHERE timestampMs >= :startTimeMs ORDER BY timestampMs DESC")
    fun getTodayLogsFlow(startTimeMs: Long): Flow<List<DailyLog>>

    @Insert
    suspend fun insertLog(log: DailyLog)
}
