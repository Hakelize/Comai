package com.comai.digitalactivity.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {

    @Query("SELECT * FROM app_limits ORDER BY appName ASC")
    fun getAllLimitsFlow(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun getAllLimits(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getLimitForPackage(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(limit: AppLimitEntity)

    @Update
    suspend fun update(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteLimit(packageName: String)

    @Query("UPDATE app_limits SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setLimitEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE app_limits SET lastNotifiedDate = :date WHERE packageName = :packageName")
    suspend fun updateLastNotifiedDate(packageName: String, date: String)
}

@Dao
interface DailyActivitySummaryDao {

    @Query("SELECT * FROM daily_activity_summaries ORDER BY date DESC")
    fun getAllSummariesFlow(): Flow<List<DailyActivitySummaryEntity>>

    @Query("SELECT * FROM daily_activity_summaries ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int): List<DailyActivitySummaryEntity>

    @Query("SELECT * FROM daily_activity_summaries WHERE date = :date LIMIT 1")
    suspend fun getSummaryForDate(date: String): DailyActivitySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(summary: DailyActivitySummaryEntity)

    @Query("DELETE FROM daily_activity_summaries")
    suspend fun clearAllSummaries()
}
