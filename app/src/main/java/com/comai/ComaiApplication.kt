package com.comai

import android.app.Application
import com.comai.engine.AIEngine
import com.comai.engine.MockEngine
import com.comai.tts.TTSManager

class ComaiApplication : Application() {

    lateinit var aiEngine: AIEngine
        private set

    lateinit var ttsManager: TTSManager
        private set

    lateinit var contextBridge: com.comai.context.ContextBridge
        private set

    lateinit var database: com.comai.contextengine.db.ComaiDatabase
        private set

    lateinit var memoryRepository: com.comai.memory.MemoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = com.comai.contextengine.db.ComaiDatabase.getInstance(this)
        memoryRepository = com.comai.memory.MemoryRepository(database.memoryDao())
        // Initialize MockEngine for Sprint 1
        aiEngine = MockEngine()
        ttsManager = TTSManager(this)
        contextBridge = com.comai.context.ContextBridge(aiEngine, memoryRepository = memoryRepository)
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
