package com.lifeloop.comai

import android.app.Application
import com.lifeloop.comai.engine.AIEngine
import com.lifeloop.comai.engine.MockEngine
import com.lifeloop.comai.tts.TTSManager

class CamoiApplication : Application() {

    lateinit var aiEngine: AIEngine
        private set

    lateinit var ttsManager: TTSManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize MockEngine for Sprint 1
        aiEngine = MockEngine()
        ttsManager = TTSManager(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
