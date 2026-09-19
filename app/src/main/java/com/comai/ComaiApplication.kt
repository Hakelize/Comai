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

    lateinit var memoryContextProvider: com.comai.memory.MemoryContextProvider
        private set

    override fun onCreate() {
        super.onCreate()
        database = com.comai.contextengine.db.ComaiDatabase.getInstance(this)
        memoryRepository = com.comai.memory.MemoryRepository(database.memoryDao())
        memoryContextProvider = com.comai.memory.MemoryContextProvider(memoryRepository)

        // Initialize osmdroid configuration safely with app-internal storage
        try {
            val osmBase = java.io.File(filesDir, "osmdroid")
            if (!osmBase.exists()) osmBase.mkdirs()
            val osmCache = java.io.File(cacheDir, "osmdroid_tiles")
            if (!osmCache.exists()) osmCache.mkdirs()

            org.osmdroid.config.Configuration.getInstance().apply {
                load(this@ComaiApplication, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
                osmdroidBasePath = osmBase
                osmdroidTileCache = osmCache
                userAgentValue = "ComaiApp/1.0 (Android; ${packageName})"
            }
        } catch (e: Exception) {
            android.util.Log.w("ComaiApplication", "Failed to init osmdroid config: ${e.message}")
        }

        // Seed hardcoded memories (iQOO Reskill Chennai context, today's plan, etc.)
        com.comai.memory.MemorySeeder.seedIfNeeded(memoryRepository)

        // Sync onboarding profile data → memories so LLM can answer personal questions
        val onboardingPreferences = com.comai.ui.screens.onboarding.OnboardingPreferences(this)
        com.comai.memory.OnboardingMemorySyncer.syncIfCompleted(onboardingPreferences, memoryRepository)

        // Continuously learn from digital activity (screen time, inferred wake/sleep, top apps)
        com.comai.digitalactivity.DigitalActivityMemorySyncer.syncIfNeeded(database, memoryRepository)

        // Initialize GemmaEngine with automatic model discovery and mock fallback
        aiEngine = com.comai.engine.gemma.GemmaEngine(this)
        ttsManager = TTSManager(this)
        contextBridge = com.comai.context.ContextBridge(aiEngine, memoryRepository = memoryRepository, memoryContextProvider = memoryContextProvider)
    }

    override fun onTerminate() {
        super.onTerminate()
        (aiEngine as? com.comai.engine.gemma.GemmaEngine)?.close()
        ttsManager.shutdown()
    }
}
