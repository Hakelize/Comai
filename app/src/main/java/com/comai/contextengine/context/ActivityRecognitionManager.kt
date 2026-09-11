package com.comai.contextengine.context

import android.content.Context
import android.util.Log

class ActivityRecognitionManager(private val context: Context) {

    val detector = WakeSleepDetector()
    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered) return
        Log.i(TAG, "Starting activity & sleep recognition monitoring (with heuristic fallback)...")
        detector.onEventDetected(
            DetectedContextEvent(
                timestampMs = System.currentTimeMillis(),
                type = DetectedEventType.STILL,
                confidence = 100,
                details = "Initial baseline state"
            )
        )
        isRegistered = true
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        Log.i(TAG, "Stopping activity recognition monitoring.")
        isRegistered = false
    }

    fun injectSimulatedEvent(event: DetectedContextEvent) {
        Log.i(TAG, "Simulated context event injected: ${event.type}")
        detector.onEventDetected(event)
    }

    fun getCurrentUserState(currentTimeMs: Long = System.currentTimeMillis()): UserState {
        return detector.getCurrentUserState(currentTimeMs)
    }

    companion object {
        private const val TAG = "ActivityRecManager"
    }
}
