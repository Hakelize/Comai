package com.comai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.comai.tts.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Robust native Android voice foundation manager.
 *
 * Responsibilities:
 * - Single-tap push-to-talk voice capture.
 * - Prioritizes on-device SpeechRecognizer (API 31+) with automatic fallback to standard SpeechRecognizer
 *   when on-device language models or permissions are unavailable on specific OEM hardware (Error 13).
 * - Prevents duplicate startListening() calls.
 * - Feeds final recognized text directly into ChatViewModel's message pipeline.
 * - Prevents audio feedback loops (stops TTS before listening; ignores mic while speaking).
 * - Comprehensive boundary diagnostic logging.
 */
class VoiceInteractionManager(
    private val context: Context,
    private val ttsManager: TTSManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val onSpeechRecognized: (String) -> Unit = {}
) {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isUsingOnDeviceRecognizer: Boolean = false
    private var forceStandardRecognizer: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    var onPermissionRequired: (() -> Unit)? = null

    init {
        scope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                if (isSpeaking) {
                    if (_state.value == VoiceState.PROCESSING || _state.value == VoiceState.LISTENING) {
                        _state.value = VoiceState.SPEAKING
                    }
                } else if (_state.value == VoiceState.SPEAKING) {
                    _state.value = VoiceState.IDLE
                }
            }
        }
    }

    /**
     * Checks whether native speech recognition is available on this device.
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Checks whether offline/on-device recognition is supported (API 31+).
     */
    fun isOnDeviceAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    }

    /**
     * Single-tap start listening handler.
     * Prevents duplicate recognizers or multiple starts.
     */
    fun startListening() {
        startListeningInternal(isFallbackAttempt = false)
    }

    private fun startListeningInternal(isFallbackAttempt: Boolean) {
        Log.i(TAG, "MIC_REQUESTED (fallbackMode=$forceStandardRecognizer, isFallbackAttempt=$isFallbackAttempt)")

        // 1. Guard: Check current state to prevent duplicate start calls unless this is an immediate internal fallback
        if (!isFallbackAttempt && (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING)) {
            Log.d(TAG, "Duplicate startListening() ignored in state: ${_state.value}")
            return
        }

        // 2. Permission check
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "MIC_PERMISSION_DENIED")
            _state.value = VoiceState.ERROR
            onPermissionRequired?.invoke()
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 1500)
            return
        }

        Log.i(TAG, "MIC_PERMISSION_GRANTED")

        // 3. Audio feedback prevention: Stop any ongoing TTS immediately
        ttsManager.stop()

        // 4. Create SpeechRecognizer instance (prefer on-device if available and not explicitly forced to standard)
        destroyRecognizer()

        val canTryOnDevice = !forceStandardRecognizer && isOnDeviceAvailable()
        if (canTryOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "SPEECH_RECOGNIZER_CREATED: On-Device SpeechRecognizer (API 31+)")
            isUsingOnDeviceRecognizer = true
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            Log.i(TAG, "SPEECH_RECOGNIZER_CREATED: Standard Android SpeechRecognizer")
            isUsingOnDeviceRecognizer = false
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "SPEECH_ERROR: SpeechRecognizer instance creation failed")
            if (isUsingOnDeviceRecognizer) {
                Log.w(TAG, "SPEECH_FALLBACK: On-device creation null. Retrying with standard SpeechRecognizer.")
                forceStandardRecognizer = true
                startListeningInternal(isFallbackAttempt = true)
                return
            }
            _state.value = VoiceState.ERROR
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 1500)
            return
        }

        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        Log.i(TAG, "MIC_STARTED")
        Log.i(TAG, "LISTENING_STARTED")
        _state.value = VoiceState.LISTENING
        _partialText.value = ""

        mainHandler.post {
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "SPEECH_ERROR: startListening exception: ${e.message}")
                if (isUsingOnDeviceRecognizer) {
                    Log.w(TAG, "SPEECH_FALLBACK: startListening failed on-device. Retrying with standard SpeechRecognizer.")
                    forceStandardRecognizer = true
                    destroyRecognizer()
                    startListeningInternal(isFallbackAttempt = true)
                    return@post
                }
                _state.value = VoiceState.ERROR
                destroyRecognizer()
                mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 1500)
            }
        }
    }

    /**
     * Stop listening manually if the user taps during listening.
     */
    fun stopListening() {
        Log.i(TAG, "LISTENING_STOPPED")
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening exception: ${e.message}")
        }
    }

    /**
     * Cancel and release recognizer resources.
     */
    fun cancel() {
        destroyRecognizer()
        _state.value = VoiceState.IDLE
        _partialText.value = ""
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroyRecognizer exception: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "AUDIO_CAPTURE_STARTED (OnDevice=$isUsingOnDeviceRecognizer)")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "User started speaking")
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "User finished speaking")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)"
                12 -> "Language model not supported (ERROR_LANGUAGE_NOT_SUPPORTED)"
                13 -> "Language model unavailable on-device (ERROR_LANGUAGE_UNAVAILABLE)"
                else -> "Error code: $error"
            }
            Log.w(TAG, "SPEECH_ERROR: $errorMsg ($error), onDevice=$isUsingOnDeviceRecognizer")

            // Check if fallback to standard recognizer should be triggered for OEM on-device errors (e.g. Error 13)
            if (isUsingOnDeviceRecognizer && (error == 13 || error == 12 || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER)) {
                Log.w(TAG, "SPEECH_FALLBACK: On-device recognizer failed with error $error ($errorMsg). Retrying immediately with standard SpeechRecognizer.")
                forceStandardRecognizer = true
                destroyRecognizer()
                startListeningInternal(isFallbackAttempt = true)
                return
            }

            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.i(TAG, "NO_MATCH: No speech detected or timeout")
                _state.value = VoiceState.IDLE
            } else {
                _state.value = VoiceState.ERROR
                mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 1500)
            }

            destroyRecognizer()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()

            destroyRecognizer()

            if (!recognizedText.isNullOrBlank()) {
                Log.i(TAG, "FINAL_RESULT: length=${recognizedText.length}")
                _state.value = VoiceState.PROCESSING
                Log.i(TAG, "TEXT_SUBMITTED")
                onSpeechRecognized(recognizedText)
            } else {
                Log.w(TAG, "NO_MATCH: Empty recognition results")
                _state.value = VoiceState.IDLE
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                Log.d(TAG, "PARTIAL_RESULT: length=${partial.length}")
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceInteractionManager"
    }
}
