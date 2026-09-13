package com.comai.memory

import com.comai.contextengine.db.Memory
import com.comai.contextengine.db.MemoryDao
import com.comai.engine.models.ContextSignals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemorySystemTest {

    private lateinit var fakeDao: FakeMemoryDao
    private lateinit var repository: MemoryRepository
    private lateinit var contextProvider: MemoryContextProvider

    @Before
    fun setup() {
        fakeDao = FakeMemoryDao()
        repository = MemoryRepository(fakeDao)
        contextProvider = MemoryContextProvider(repository)
    }

    @Test
    fun testFavoriteProgrammingLanguageExtraction() {
        val input = "My favorite programming language is Python."
        val memory = MemoryExtractor.extract(input)
        assertNotNull("Should extract favorite programming language", memory)
        assertEquals("PREFERENCE", memory?.type)
        assertEquals("favorite_programming_language", memory?.key)
        assertTrue(memory?.value?.contains("Python") == true)
        assertEquals("User's favorite programming language is Python", memory?.toDisplayString())
    }

    @Test
    fun testCollegeDepartureExtraction() {
        val input = "I usually leave college at 5 PM."
        val memory = MemoryExtractor.extract(input)
        assertNotNull("Should extract college departure", memory)
        assertEquals("CONTEXT", memory?.type)
        assertEquals("preferred_college_departure", memory?.key)
        assertEquals("17:00", memory?.value)
        assertEquals("Usually leaves college around 17:00", memory?.toDisplayString())
    }

    @Test
    fun testRejectionOfQuestionsAndTemporaryStatements() {
        assertNull("Questions should not be stored as memory", MemoryExtractor.extract("What is my favorite programming language?"))
        assertNull("Uncertain statements should be rejected", MemoryExtractor.extract("I think my favorite language is Python."))
        assertNull("Temporary statements should be rejected", MemoryExtractor.extract("I will leave college at 5 PM today."))
        assertNull("Casual greetings should be rejected", MemoryExtractor.extract("Hello Comai!"))
    }

    @Test
    fun testPersistenceAndAppRestartFlow() = runTest {
        // Step 1: User provides memories during session 1
        val mem1 = MemoryExtractor.extract("My favorite programming language is Python.")!!
        val mem2 = MemoryExtractor.extract("I usually leave college at 5 PM.")!!

        repository.saveMemory(mem1)
        repository.saveMemory(mem2)

        // Verify stored in DB
        val storedBeforeRestart = repository.getAllMemories()
        assertEquals(2, storedBeforeRestart.size)

        // Step 2: Simulate App Restart
        // In Android, SQLite DB persists. Re-instantiate repository and contextProvider from the same DB/DAO
        val newRepository = MemoryRepository(fakeDao)
        val newContextProvider = MemoryContextProvider(newRepository)

        // Step 3: Verify memories are loaded from Room after restart
        val storedAfterRestart = newRepository.getAllMemories()
        assertEquals("Memories must persist after app restart", 2, storedAfterRestart.size)

        // Step 4: Verify MemoryContextProvider supplies memory_context list to LLM
        val memoryContextList = newContextProvider.getMemoryContextList("What can you tell me about myself?")
        assertEquals(2, memoryContextList.size)
        assertTrue("Memory list should contain python preference", memoryContextList.any { it.contains("Python") })
        assertTrue("Memory list should contain college departure", memoryContextList.any { it.contains("college") })

        // Step 5: Verify formatted string context
        val formattedContext = newContextProvider.getFormattedMemoryContext("What can you tell me about myself?")
        assertNotNull(formattedContext)
        assertTrue(formattedContext!!.contains("Python"))
        assertTrue(formattedContext.contains("college"))
    }

    // In-memory fake DAO for unit testing
    class FakeMemoryDao : MemoryDao {
        private val storage = mutableMapOf<Long, Memory>()
        private val flow = MutableStateFlow<List<Memory>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(memory: Memory): Long {
            val id = if (memory.id == 0L) nextId++ else memory.id
            val saved = memory.copy(id = id)
            storage[id] = saved
            emit()
            return id
        }

        override suspend fun update(memory: Memory) {
            storage[memory.id] = memory
            emit()
        }

        override fun getAllAsFlow(): Flow<List<Memory>> = flow.asStateFlow()

        override suspend fun getAll(): List<Memory> = storage.values.sortedByDescending { it.timestampMs }

        override suspend fun getById(id: Long): Memory? = storage[id]

        override suspend fun getByType(type: String): List<Memory> =
            storage.values.filter { it.type == type }.sortedByDescending { it.timestampMs }

        override suspend fun getByKey(key: String): Memory? =
            storage.values.firstOrNull { it.key == key }

        override suspend fun delete(memory: Memory) {
            storage.remove(memory.id)
            emit()
        }

        override suspend fun deleteById(id: Long) {
            storage.remove(id)
            emit()
        }

        override suspend fun clear() {
            storage.clear()
            emit()
        }

        private fun emit() {
            flow.value = storage.values.sortedByDescending { it.timestampMs }
        }
    }
}
