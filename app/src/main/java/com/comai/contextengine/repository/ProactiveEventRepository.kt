package com.comai.contextengine.repository

import com.comai.contextengine.database.ProactiveEventEntity
import com.comai.contextengine.db.ComaiDatabase
import kotlinx.coroutines.flow.Flow

interface ProactiveEventRepository {
    fun getAllEventsFlow(): Flow<List<ProactiveEventEntity>>
    fun getUnacknowledgedEventsFlow(): Flow<List<ProactiveEventEntity>>
    suspend fun getEventsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<ProactiveEventEntity>
    suspend fun logProactiveEvent(event: ProactiveEventEntity): Long
    suspend fun markAcknowledged(id: Long)
}

class ProactiveEventRepositoryImpl(private val database: ComaiDatabase) : ProactiveEventRepository {
    override fun getAllEventsFlow(): Flow<List<ProactiveEventEntity>> = database.proactiveEventDao().getAllEventsFlow()
    override fun getUnacknowledgedEventsFlow(): Flow<List<ProactiveEventEntity>> = database.proactiveEventDao().getUnacknowledgedEventsFlow()
    override suspend fun getEventsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<ProactiveEventEntity> = database.proactiveEventDao().getEventsForTimeRange(startTimeMs, endTimeMs)
    override suspend fun logProactiveEvent(event: ProactiveEventEntity): Long = database.proactiveEventDao().insertEvent(event)
    override suspend fun markAcknowledged(id: Long) = database.proactiveEventDao().markEventAcknowledged(id)
}
