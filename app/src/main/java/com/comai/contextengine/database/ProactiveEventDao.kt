package com.comai.contextengine.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for querying, persisting, and acknowledging proactive events.
 */
@Dao
interface ProactiveEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ProactiveEventEntity): Long

    @Query("SELECT * FROM proactive_events ORDER BY timestampMs DESC")
    fun getAllEventsFlow(): Flow<List<ProactiveEventEntity>>

    @Query("SELECT * FROM proactive_events WHERE timestampMs >= :startTimeMs AND timestampMs <= :endTimeMs ORDER BY timestampMs DESC")
    suspend fun getEventsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<ProactiveEventEntity>

    @Query("SELECT * FROM proactive_events WHERE isAcknowledged = 0 ORDER BY timestampMs DESC")
    fun getUnacknowledgedEventsFlow(): Flow<List<ProactiveEventEntity>>

    @Query("UPDATE proactive_events SET isAcknowledged = 1 WHERE id = :id")
    suspend fun markEventAcknowledged(id: Long)

    @Query("DELETE FROM proactive_events WHERE timestampMs < :beforeTimestampMs")
    suspend fun deleteEventsBefore(beforeTimestampMs: Long): Int
}
