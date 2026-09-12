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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _language = MutableStateFlow(ComaiLanguage.ENGLISH)
    val language: StateFlow<ComaiLanguage> = _language.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isUsingOnDeviceRecognizer: Boolean = false
    private var forceStandardRecognizer: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    var onPermissionRequired: (() -> Unit)? = null

    /** Update the STT language. Takes effect on the next startListening() call. */
    fun setLanguage(lang: ComaiLanguage) {
        _language.value = lang
        _errorMessage.value = null
        Log.i(TAG, "LANGUAGE_SET: ${lang.name} (stt=${lang.sttTag})")
    }

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

        // Clear previous error
        _errorMessage.value = null

        // 2. Permission check
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "MIC_PERMISSION_DENIED")
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Microphone permission denied. Please grant permission."
            onPermissionRequired?.invoke()
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2000)
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
            Log.e(TAG, "SPEECH_ERROR: SpeechRecognizer instance creation returned null")
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Speech recognizer initialization failed. Tap orb to retry."
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2000)
            return
        }

        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val lang = _language.value
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.sttTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.sttTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (lang.altLocales.isNotEmpty()) {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", lang.altLocales.toTypedArray())
            }
        }
        Log.i(TAG, "STT_LOCALE: ${lang.sttTag} (alt=${lang.altLocales})")

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
                _errorMessage.value = "Failed to start speech capture: ${e.message}"
                destroyRecognizer()
                mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2000)
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
        _errorMessage.value = null
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
            val currentLang = _language.value
            val (errorMsg, isFatal) = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error (Code 3)" to true
                SpeechRecognizer.ERROR_CLIENT -> "Client error (Code 5)" to true
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required (Code 9)" to true
                SpeechRecognizer.ERROR_NETWORK -> "Network error (Code 2)" to true
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout (Code 1)" to true
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match (Code 7)" to false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy (Code 8)" to true
                SpeechRecognizer.ERROR_SERVER -> "Server error (Code 4)" to true
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout / silence (Code 6)" to false
                10 -> "Too many requests (Code 10)" to true
                11 -> "Server disconnected (Code 11)" to true
                12 -> "Language not supported on device: ${currentLang.displayName} (Code 12)" to true
                13 -> "Language unavailable: ${currentLang.displayName} (Code 13)" to true
                14 -> "Cannot check language support (Code 14)" to true
                else -> "Speech error: Code $error" to true
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

            if (!isFatal) {
                Log.i(TAG, "NO_MATCH: No speech detected or timeout")
                _state.value = VoiceState.IDLE
                _errorMessage.value = null
            } else {
                _state.value = VoiceState.ERROR
                _errorMessage.value = "$errorMsg. Tap orb to retry."
                mainHandler.postDelayed({
                    if (_state.value == VoiceState.ERROR) {
                        _state.value = VoiceState.IDLE
                    }
                }, 3000)
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
                _errorMessage.value = null
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
