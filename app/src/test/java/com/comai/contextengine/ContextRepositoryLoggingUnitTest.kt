package com.comai.contextengine

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.comai.contextengine.database.ContextLogDao
import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.models.ContextConfidence
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.repository.ContextLogRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextRepositoryLoggingUnitTest {

    private lateinit var mockDao: FakeContextLogDao
    private lateinit var repository: ContextLogRepositoryImpl

    @Before
    fun setUp() {
        mockDao = FakeContextLogDao()
        val mockDb = object : ComaiDatabase() {
            override fun contextLogDao(): ContextLogDao = mockDao
            override fun userProfileDao() = throw UnsupportedOperationException()
            override fun programDao() = throw UnsupportedOperationException()
            override fun dailyLogDao() = throw UnsupportedOperationException()
            override fun proactiveEventDao() = throw UnsupportedOperationException()
            override fun memoryDao() = throw UnsupportedOperationException()
            override fun personalizationStateDao() = throw UnsupportedOperationException()
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): InvalidationTracker = FakeInvalidationTracker(this)
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) = throw UnsupportedOperationException()
        }
        repository = ContextLogRepositoryImpl(mockDb)
    }

    @Test
    fun testSaveContextLog() = runTest {
        val entity = ContextLogEntity(
            timestampMs = 100000L,
            locationPlace = "Office",
            activityType = "STATIONARY",
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = true,
            deviationMinutes = 45,
            confidenceScore = 0.9f,
            confidenceLevel = "HIGH",
            triggerTask = "OVERTIME_CHECKIN",
            eventType = "ROUTINE_DEVIATION",
            isEscalated = true,
            task = "OVERTIME_CHECKIN",
            confidence = 0.9,
            signalsSummary = "Summary"
        )

        val id = repository.saveContextLog(entity)

        assertTrue(id > 0)
        assertEquals(1, mockDao.logs.size)
        assertEquals("Office", mockDao.logs[0].locationPlace)
    }

    @Test
    fun testSaveContextResult() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite()
        val deviceContext = mockProvider.getContextData()

        val userContext = UserContext("OFFICE_LATE_DEPARTURE", "Office", "STATIONARY", "SPEAKER", "BATTERY", "18:45")
        val routineContext = RoutineContext(true, "WORK_DAY_ROUTINE", true, 75, 0.6, 0.9f, "Late")
        val confidence = ContextConfidence(0.95f, ConfidenceLevel.HIGH, listOf("GPS"), emptyList(), emptyList(), 0.95, 1.0, 1.0)
        val contract = com.comai.contextengine.contract.SharedContextContract(
            systemRole = "Comai Proactive Context Engine",
            userState = "awake",
            contextSignals = com.comai.contextengine.contract.ContextSignals("18:45", "Office", true),
            retrievedData = null,
            task = "OVERTIME_CHECKIN",
            constraints = "empathetic"
        )
        val contextInput = com.comai.contextengine.contract.ContextContractMapper.toContextInput(contract)

        val result = ContextProcessingResult(deviceContext, userContext, routineContext, confidence, contract, contextInput)

        val id = repository.saveContextResult(result)

        assertTrue(id > 0)
        assertEquals(1, mockDao.logs.size)
        assertEquals("OVERTIME_CHECKIN", mockDao.logs[0].task)
    }

    @Test
    fun testRetrieveTodayContextLogs() = runTest {
        val now = System.currentTimeMillis()
        mockDao.logs.add(
            ContextLogEntity(
                timestampMs = now,
                locationPlace = "Home",
                activityType = "STILL",
                routineState = "MORNING",
                isRoutineDeviation = false,
                deviationMinutes = 0,
                confidenceScore = 0.9f,
                confidenceLevel = "HIGH",
                triggerTask = "MORNING_REMINDER",
                eventType = "PERIODIC",
                isEscalated = false,
                task = "MORNING_REMINDER",
                confidence = 0.9,
                signalsSummary = "Signals"
            )
        )

        val logs = repository.getTodayContextLogs()

        assertNotNull(logs)
        assertEquals(1, logs.size)
        assertEquals("Home", logs[0].locationPlace)
    }

    @Test
    fun testRetrieveHistoricalContextLogs() = runTest {
        val logs = repository.getHistoricalContextLogs(daysBack = 7)

        assertNotNull(logs)
    }

    @Test
    fun testHandleEmptyDatabaseSafely() = runTest {
        mockDao.logs.clear()

        val logs = repository.getTodayContextLogs()
        val recent = repository.getRecentContextLogs(limit = 10)
        val comparison = repository.compareWithHistoricalPatterns(
            UserContext("HOME_MORNING", "Home", "STILL", "SPEAKER", "BATTERY", "07:30"),
            RoutineContext(true, "MORNING", false, 0, 0.0, 0.9f, "Normal")
        )

        assertNotNull(logs)
        assertTrue(logs.isEmpty())
        assertNotNull(recent)
        assertTrue(recent.isEmpty())
        assertNotNull(comparison)
        assertTrue(comparison.isConsistentWithHistory)
    }
}

class FakeInvalidationTracker(db: RoomDatabase) : InvalidationTracker(db, "context_logs") {
    override fun addObserver(observer: Observer) {}
    override fun removeObserver(observer: Observer) {}
}

class FakeContextLogDao : ContextLogDao {
    val logs = mutableListOf<ContextLogEntity>()

    override suspend fun insertLog(log: ContextLogEntity): Long {
        val newId = (logs.size + 1).toLong()
        logs.add(log.copy(id = newId))
        return newId
    }

    override suspend fun getLastLog(): ContextLogEntity? = logs.lastOrNull()

    override suspend fun getRecentLogsList(limit: Int): List<ContextLogEntity> {
        return logs.takeLast(limit)
    }

    override fun getRecentLogs(limit: Int): Flow<List<ContextLogEntity>> {
        return flowOf(logs.takeLast(limit))
    }

    override suspend fun getTodayLogs(startTimeMs: Long): List<ContextLogEntity> {
        return logs.filter { it.timestampMs >= startTimeMs }
    }

    override fun getTodayLogsFlow(startTimeMs: Long): Flow<List<ContextLogEntity>> {
        return flowOf(logs.filter { it.timestampMs >= startTimeMs })
    }

    override suspend fun getHistoricalLogs(endTimeMs: Long, limit: Int): List<ContextLogEntity> {
        return logs.filter { it.timestampMs < endTimeMs }.take(limit)
    }

    override suspend fun getEscalatedLogs(limit: Int): List<ContextLogEntity> {
        return logs.filter { it.isEscalated }.takeLast(limit)
    }

    override suspend fun getLogsForTimeRange(startTimeMs: Long, endTimeMs: Long): List<ContextLogEntity> {
        return logs.filter { it.timestampMs in startTimeMs..endTimeMs }
    }

    override suspend fun deleteLogsBefore(beforeTimestampMs: Long): Int {
        val initialSize = logs.size
        logs.removeAll { it.timestampMs < beforeTimestampMs }
        return initialSize - logs.size
    }
}
