package com.comai.memory

import com.comai.contextengine.db.Memory
import com.comai.contextengine.db.MemoryDao
import com.comai.engine.MockEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit test suite for Comai Phase 2 Local Personal Memory.
 */
class MemoryPipelineTest {

    private lateinit var fakeDao: FakeMemoryDao
    private lateinit var repository: MemoryRepository
    private lateinit var mockEngine: MockEngine

    @Before
    fun setup() {
        fakeDao = FakeMemoryDao()
        repository = MemoryRepository(fakeDao)
        mockEngine = MockEngine()
    }

    @Test
    fun testMemoryInsertAndRetrieval() = runTest {
        val memory = Memory(
            type = "PREFERENCE",
            key = "preferred_work_departure",
            value = "17:30",
            source = "USER_EXPLICIT"
        )
        val id = repository.saveMemory(memory)
        assertTrue(id > 0)

        val retrieved = repository.getMemoryByKey("preferred_work_departure")
        assertNotNull(retrieved)
        assertEquals("17:30", retrieved?.value)
        assertEquals("PREFERENCE", retrieved?.type)
        assertEquals("USER_EXPLICIT", retrieved?.source)
    }

    @Test
    fun testMemoryRetrievalByType() = runTest {
        repository.saveMemory(Memory(type = "PREFERENCE", key = "after_work_music", value = "calm"))
        repository.saveMemory(Memory(type = "CONTEXT", key = "workplace", value = "Tech Hub Office"))

        val preferences = repository.getMemoriesByType("PREFERENCE")
        val contexts = repository.getMemoriesByType("CONTEXT")

        assertEquals(1, preferences.size)
        assertEquals("after_work_music", preferences[0].key)
        assertEquals(1, contexts.size)
        assertEquals("workplace", contexts[0].key)
    }

    @Test
    fun testMemoryUpdateAndUpsertBehavior() = runTest {
        // Initial insert: 17:30
        val id1 = repository.saveMemory(
            Memory(type = "PREFERENCE", key = "preferred_work_departure", value = "17:30")
        )

        // User later says new time: 18:00
        val id2 = repository.saveMemory(
            Memory(type = "PREFERENCE", key = "preferred_work_departure", value = "18:00")
        )

        assertEquals("Upsert should maintain the same record ID", id1, id2)

        val all = repository.getAllMemories()
        assertEquals("Should not create duplicate conflicting records for singleton keys", 1, all.size)
        assertEquals("18:00", all[0].value)
    }

    @Test
    fun testMemoryDeletion() = runTest {
        val id = repository.saveMemory(Memory(type = "CONTEXT", key = "workplace", value = "Tech Hub Office"))
        assertNotNull(repository.getMemoryById(id))

        repository.deleteMemory(id)
        assertNull(repository.getMemoryById(id))
        assertTrue(repository.getAllMemories().isEmpty())
    }

    @Test
    fun testMemoryClear() = runTest {
        repository.saveMemory(Memory(type = "PREFERENCE", key = "pref_1", value = "val_1"))
        repository.saveMemory(Memory(type = "CONTEXT", key = "ctx_1", value = "val_2"))
        assertEquals(2, repository.getAllMemories().size)

        repository.clearAll()
        assertTrue(repository.getAllMemories().isEmpty())
    }

    @Test
    fun testExplicitUserStatementExtraction() {
        // Departure statement
        val mem1 = MemoryExtractor.extract("I usually leave work around 5:30.")
        assertNotNull(mem1)
        assertEquals("preferred_work_departure", mem1?.key)
        assertEquals("17:30", mem1?.value)
        assertEquals("PREFERENCE", mem1?.type)
        assertEquals("USER_EXPLICIT", mem1?.source)
        assertEquals(1.0f, mem1?.confidence)

        // Workplace statement
        val mem2 = MemoryExtractor.extract("I work at Tech Hub Office.")
        assertNotNull(mem2)
        assertEquals("workplace", mem2?.key)
        assertEquals("Tech Hub Office", mem2?.value)
        assertEquals("CONTEXT", mem2?.type)

        // Music preference statement
        val mem3 = MemoryExtractor.extract("I like calm music after work.")
        assertNotNull(mem3)
        assertEquals("after_work_music", mem3?.key)
        assertEquals("calm", mem3?.value)
        assertEquals("PREFERENCE", mem3?.type)
    }

    @Test
    fun testConservativeExtractionRejectsNonExplicitOrTemporaryStatements() {
        // Temporary / today only
        assertNull(MemoryExtractor.extract("I'm leaving work at 5:30 today."))
        assertNull(MemoryExtractor.extract("I will leave work at 6 tonight."))

        // Uncertain statement
        assertNull(MemoryExtractor.extract("I think I usually leave around 5:30."))
        assertNull(MemoryExtractor.extract("Maybe I work at Tech Hub."))

        // Emotional / psychological state (must not infer preferences)
        assertNull(MemoryExtractor.extract("I feel so tired and stressed today."))
        assertNull(MemoryExtractor.extract("I am feeling lonely."))

        // Random chat
        assertNull(MemoryExtractor.extract("Hello Comai, how are you?"))
    }

    @Test
    fun testMemoryRetrievalForMemoryRecall() = runTest {
        repository.saveMemory(Memory(type = "PREFERENCE", key = "preferred_work_departure", value = "17:30"))
        repository.saveMemory(Memory(type = "CONTEXT", key = "workplace", value = "Tech Hub Office"))

        val retrieved = repository.retrieveRelevantMemories("What do you remember about me?")
        assertNotNull(retrieved)
        assertTrue(retrieved!!.contains("Preferences:"))
        assertTrue(retrieved.contains("Usually leaves work around 17:30"))
        assertTrue(retrieved.contains("Context:"))
        assertTrue(retrieved.contains("Workplace: Tech Hub Office"))
    }

    @Test
    fun testContextAndMemoryEnrichmentInMockEngine() = runTest {
        // Given stored memory
        repository.saveMemory(Memory(type = "PREFERENCE", key = "preferred_work_departure", value = "17:30"))

        val relevant = repository.retrieveRelevantMemories("OVERTIME_CHECKIN", ContextSignals(location = "Office", routineDeviation = true))
        val input = ContextInput(
            task = "OVERTIME_CHECKIN",
            contextSignals = ContextSignals(time = "18:43", location = "Office", routineDeviation = true),
            retrievedData = relevant
        )

        val response = mockEngine.process(input)
        assertEquals("check_in", response.action)
        assertEquals("You're leaving later than usual today. How was work?", response.displayText)
    }

    @Test
    fun testUserDeletionRemovesMemoryFromRetrieval() = runTest {
        val id = repository.saveMemory(Memory(type = "PREFERENCE", key = "preferred_work_departure", value = "17:30"))
        assertNotNull(repository.retrieveRelevantMemories("what do you remember about me"))

        // User deletes memory
        repository.deleteMemory(id)

        // Memory recall should now return null for relevant memories
        val retrieved = repository.retrieveRelevantMemories("what do you remember about me")
        assertNull(retrieved)

        // MockEngine reflects empty state
        val input = ContextInput(task = "what do you remember about me", retrievedData = retrieved)
        val response = mockEngine.process(input)
        assertEquals("false", response.metadata?.get("has_memories"))
        assertTrue(response.displayText.contains("saved memories", ignoreCase = true))
    }

    @Test
    fun testEmptyMemoryStateHandling() = runTest {
        val retrieved = repository.retrieveRelevantMemories("what do you remember about me")
        assertNull(retrieved)

        val input = ContextInput(task = "what do you remember about me", retrievedData = null)
        val response = mockEngine.process(input)
        assertTrue(response.displayText.contains("saved memories", ignoreCase = true))
    }

    @Test
    fun testRelevanceFilteringDoesNotExposeUnrelatedRecords() = runTest {
        // Save music preference
        repository.saveMemory(Memory(type = "PREFERENCE", key = "after_work_music", value = "calm"))

        // Query about commute departure time
        val retrieved = repository.retrieveRelevantMemories("what time do I usually leave work")
        assertNull("Should not dump unrelated music preference into departure time query", retrieved)
    }

    @Test
    fun testEndToEndMemoryRecallAndDeletionCycle() = runTest {
        // 1. User says statement
        val extracted = repository.extractAndStoreMemory("I usually leave work around 5:30.")
        assertNotNull(extracted)

        // 2. Query recall
        val retrieved1 = repository.retrieveRelevantMemories("what do you remember about me")
        assertNotNull(retrieved1)
        val response1 = mockEngine.process(ContextInput(task = "what do you remember about me", retrievedData = retrieved1))
        assertEquals("true", response1.metadata?.get("has_memories"))
        assertTrue(response1.displayText.contains("17:30"))

        // 3. User deletes from Room
        repository.deleteMemory(extracted!!.id)

        // 4. Query recall again -> verify it's gone
        val retrieved2 = repository.retrieveRelevantMemories("what do you remember about me")
        assertNull(retrieved2)
        val response2 = mockEngine.process(ContextInput(task = "what do you remember about me", retrievedData = retrieved2))
        assertEquals("false", response2.metadata?.get("has_memories"))
    }
}

/**
 * In-memory test double for [MemoryDao] to provide deterministic JVM unit tests.
 */
class FakeMemoryDao : MemoryDao {
    private val memoryList = mutableListOf<Memory>()
    private val flow = MutableStateFlow<List<Memory>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(memory: Memory): Long {
        val id = if (memory.id > 0) memory.id else nextId++
        val item = memory.copy(id = id)
        memoryList.removeIf { it.id == id }
        memoryList.add(item)
        emit()
        return id
    }

    override suspend fun update(memory: Memory) {
        val index = memoryList.indexOfFirst { it.id == memory.id }
        if (index >= 0) {
            memoryList[index] = memory
            emit()
        }
    }

    override fun getAllAsFlow(): Flow<List<Memory>> = flow.asStateFlow()

    override suspend fun getAll(): List<Memory> = memoryList.toList()

    override suspend fun getById(id: Long): Memory? = memoryList.firstOrNull { it.id == id }

    override suspend fun getByType(type: String): List<Memory> = memoryList.filter { it.type == type }

    override suspend fun getByKey(key: String): Memory? = memoryList.firstOrNull { it.key == key }

    override suspend fun delete(memory: Memory) {
        memoryList.removeIf { it.id == memory.id }
        emit()
    }

    override suspend fun deleteById(id: Long) {
        memoryList.removeIf { it.id == id }
        emit()
    }

    override suspend fun clear() {
        memoryList.clear()
        emit()
    }

    private fun emit() {
        flow.value = memoryList.toList()
    }
}
