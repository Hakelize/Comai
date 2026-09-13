package com.comai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * - Uses native Android SpeechRecognizer configured on Main Thread with Google Speech Services.
 * - Supports Indian multilingual speech recognition (en-IN, ta-IN, te-IN, hi-IN, ml-IN).
 * - Prevents duplicate startListening() calls.
 * - Feeds final recognized text directly into ChatViewModel's message pipeline.
 * - Prevents audio feedback loops (ensures TTS is fully silent before opening the microphone).
 * - Comprehensive boundary diagnostic logging.
 */
class VoiceInteractionManager(
    private val context: Context,
    private val ttsManager: TTSManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private var onSpeechRecognized: ((String) -> Unit)? = null
) {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _uiLanguage = MutableStateFlow(ComaiLanguage.ENGLISH)
    val uiLanguage: StateFlow<ComaiLanguage> = _uiLanguage.asStateFlow()

    /** Retained for backward compatibility with callers referencing language */
    val language: StateFlow<ComaiLanguage> get() = _uiLanguage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSessionId = 0L

    var onPermissionRequired: (() -> Unit)? = null

    fun setOnSpeechRecognizedListener(listener: (String) -> Unit) {
        this.onSpeechRecognized = listener
    }

    /** Update the UI/TTS language. Recognition remains multilingual. */
    fun setLanguage(lang: ComaiLanguage) {
        _uiLanguage.value = lang
        _errorMessage.value = null
        Log.i(TAG, "UI_LANGUAGE_SET: ${lang.name} (default stt=${lang.sttTag})")
    }

    init {
        // Immediate notification before speech synthesis starts
        ttsManager.onBeforeSpeak = {
            mainHandler.post {
                _state.value = VoiceState.SPEAKING
                destroyRecognizerInternal()
            }
        }

        scope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                if (isSpeaking) {
                    if (_state.value != VoiceState.SPEAKING) {
                        _state.value = VoiceState.SPEAKING
                        destroyRecognizerInternal()
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
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    /**
     * Speaks a short conversational greeting first, and once TTS completes, enters LISTENING state.
     * Prevents any audio feedback loop by keeping the SpeechRecognizer stopped and blocked while speaking.
     */
    fun speakGreetingAndListen(greeting: String) {
        Log.i(TAG, "GREET_AND_LISTEN_REQUESTED: $greeting")

        scope.launch(Dispatchers.Main) {
            // Guard against starting while already speaking
            if (_state.value == VoiceState.SPEAKING) {
                Log.d(TAG, "Already speaking, ignoring duplicate speakGreetingAndListen")
                return@launch
            }

            // Audio feedback prevention: Ensure mic is completely closed and inactive while TTS speaks
            _state.value = VoiceState.SPEAKING
            _errorMessage.value = null
            destroyRecognizerInternal()

            // Wait for TTS engine to finish asynchronous initialization if app just launched
            val isReady = ttsManager.awaitReady(2500L)
            if (!isReady) {
                Log.w(TAG, "TTS not ready within timeout; resetting to IDLE and starting listening.")
                _state.value = VoiceState.IDLE
                startListeningInternal()
                return@launch
            }

            // Confirm we are still in SPEAKING state (user hasn't cancelled or switched tabs)
            if (_state.value != VoiceState.SPEAKING) {
                Log.d(TAG, "State changed from SPEAKING during TTS init wait; aborting greeting speech.")
                return@launch
            }

            val greetingSession = activeSessionId

            val spoken = ttsManager.speak(greeting) {
                // UtteranceProgressListener.onDone() callback from Android TTS engine
                scope.launch(Dispatchers.Main) {
                    if (_state.value == VoiceState.SPEAKING && greetingSession == activeSessionId) {
                        Log.i(TAG, "Greeting TTS completed. Adding 350ms acoustic buffer before mic...")
                        // 350ms acoustic buffer: Wait for physical DAC / audio track playback buffer to flush completely from phone speakers
                        kotlinx.coroutines.delay(350L)
                        if (_state.value == VoiceState.SPEAKING && greetingSession == activeSessionId) {
                            Log.i(TAG, "Acoustic buffer elapsed. Transitioning strictly to LISTENING.")
                            _state.value = VoiceState.IDLE
                            startListeningInternal()
                        }
                    } else {
                        Log.d(TAG, "Greeting completed but state changed or session cancelled; not opening mic.")
                    }
                }
            }

            if (!spoken) {
                Log.w(TAG, "TTS speak returned false; cleanly resetting to IDLE and listening.")
                _state.value = VoiceState.IDLE
                startListeningInternal()
            }
        }
    }

    /**
     * Single-tap start listening handler.
     * Prevents duplicate recognizers or multiple starts.
     */
    fun startListening() {
        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        Log.i(TAG, "MIC_REQUESTED")

        // 1. Guard: If Comai is currently speaking or TTS is active, NEVER open the microphone!
        if (_state.value == VoiceState.SPEAKING || ttsManager.isSpeaking.value) {
            Log.d(TAG, "startListening() rejected: Comai is currently speaking (state=${_state.value})")
            return
        }

        // Guard: Check current state to prevent duplicate start calls
        if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING) {
            Log.d(TAG, "Duplicate startListening() ignored in state: ${_state.value}")
            return
        }

        // Clear previous error and partial text
        _errorMessage.value = null
        _partialText.value = ""

        // 2. Permission check
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "MIC_PERMISSION_DENIED")
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Microphone permission required. Tap to grant."
            onPermissionRequired?.invoke()
            mainHandler.postDelayed({
                if (_state.value == VoiceState.ERROR) {
                    _state.value = VoiceState.IDLE
                }
            }, 2500)
            return
        }

        Log.i(TAG, "MIC_PERMISSION_GRANTED")

        // 3. Audio feedback prevention: Stop any ongoing TTS immediately before opening mic
        ttsManager.stop()

        // 4. Destroy existing recognizer and increment session before building a new clean session
        destroyRecognizerInternal()
        val session = activeSessionId

        // 5. Create SpeechRecognizer instance on Main Thread
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            Log.i(TAG, "SPEECH_RECOGNIZER_CREATED: Standard Android SpeechRecognizer (session=$session)")
        } catch (e: Exception) {
            Log.e(TAG, "SPEECH_ERROR: SpeechRecognizer creation failed", e)
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Failed to create speech recognizer: ${e.message}"
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2500)
            return
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "SPEECH_ERROR: SpeechRecognizer instance is null")
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Speech recognition unavailable on device."
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2500)
            return
        }

        speechRecognizer?.setRecognitionListener(createRecognitionListener(session))

        val uiLang = _uiLanguage.value
        // Decouple UI language from spoken language:
        // Pass all supported Indian languages (en-IN, ta-IN, hi-IN, te-IN, ml-IN) so SpeechRecognizer
        // can decode native speech and code-switching (Tanglish, mixed phrases).
        val allSupportedLocales = listOf("en-IN", "ta-IN", "hi-IN", "te-IN", "ml-IN")
        val primaryTag = uiLang.sttTag

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            // Multilingual recognition extras
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", allSupportedLocales.toTypedArray())
            putStringArrayListExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, ArrayList(allSupportedLocales))
            putExtra("android.speech.extra.EXTRA_ENABLE_LANGUAGE_SWITCH", true)
            putExtra("android.speech.extra.LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS", 5000L)
            // Comfortable listening silence buffers
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        Log.i(TAG, "MULTILINGUAL_STT_INTENT: primary=$primaryTag, additional=$allSupportedLocales")

        _state.value = VoiceState.LISTENING
        Log.i(TAG, "MIC_STARTED")
        Log.i(TAG, "LISTENING_STARTED")

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SPEECH_ERROR: startListening exception", e)
            _state.value = VoiceState.ERROR
            _errorMessage.value = "Failed to start listening: ${e.message}"
            destroyRecognizerInternal()
            mainHandler.postDelayed({ _state.value = VoiceState.IDLE }, 2500)
        }
    }

    /**
     * Stop listening manually if the user taps during listening.
     */
    fun stopListening() {
        mainHandler.post {
            Log.i(TAG, "LISTENING_STOPPED")
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening exception: ${e.message}")
            }
        }
    }

    /**
     * Cancel and release recognizer and TTS resources.
     */
    fun cancel() {
        mainHandler.post {
            destroyRecognizerInternal()
            ttsManager.stop()
            _state.value = VoiceState.IDLE
            _partialText.value = ""
            _errorMessage.value = null
            _rmsDb.value = 0f
        }
    }

    private fun destroyRecognizerInternal() {
        activeSessionId++
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroyRecognizer exception: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun createRecognitionListener(listenerSession: Long) = object : RecognitionListener {
        private fun isSessionInvalid(): Boolean {
            return listenerSession != activeSessionId || _state.value == VoiceState.SPEAKING || ttsManager.isSpeaking.value
        }

        override fun onReadyForSpeech(params: Bundle?) {
            if (isSessionInvalid()) return
            Log.i(TAG, "AUDIO_CAPTURE_STARTED")
        }

        override fun onBeginningOfSpeech() {
            if (isSessionInvalid()) return
            Log.d(TAG, "User started speaking")
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (isSessionInvalid()) return
            _rmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (isSessionInvalid()) return
            Log.d(TAG, "User finished speaking")
        }

        override fun onError(error: Int) {
            if (isSessionInvalid()) {
                Log.d(TAG, "Dropping STT onError($error): session invalid or Comai is speaking")
                return
            }
            val currentUiLang = _uiLanguage.value
            val (errorMsg, isFatal) = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error" to true
                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error" to true
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required" to true
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error" to true
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timeout" to true
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match" to false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy" to true
                SpeechRecognizer.ERROR_SERVER -> "Speech server error" to true
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout" to false
                10 -> "Too many speech requests" to true
                11 -> "Speech server disconnected" to true
                12 -> "Language not supported by speech provider" to true
                13 -> "Language service unavailable" to true
                14 -> "Language check failed" to true
                else -> "Speech recognition error ($error)" to true
            }
            Log.w(TAG, "SPEECH_ERROR: $errorMsg (code=$error, uiLang=${currentUiLang.name})")

            mainHandler.post {
                if (isSessionInvalid()) return@post
                destroyRecognizerInternal()

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
                            _errorMessage.value = null
                        }
                    }, 3000)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (isSessionInvalid()) {
                Log.d(TAG, "Dropping STT onResults: session invalid or Comai is speaking")
                return
            }
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull { it.isNotBlank() }?.trim()

            mainHandler.post {
                if (isSessionInvalid()) return@post
                destroyRecognizerInternal()

                if (!recognizedText.isNullOrBlank()) {
                    Log.i(TAG, "FINAL_RESULT: text=\"$recognizedText\" length=${recognizedText.length}")
                    _state.value = VoiceState.PROCESSING
                    _errorMessage.value = null
                    Log.i(TAG, "TEXT_SUBMITTED")
                    onSpeechRecognized?.invoke(recognizedText)
                } else {
                    Log.w(TAG, "NO_MATCH: Empty recognition results")
                    _state.value = VoiceState.IDLE
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (isSessionInvalid()) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (partial.isNotBlank()) {
                Log.d(TAG, "PARTIAL_RESULT: text=\"$partial\" length=${partial.length}")
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceInteractionManager"
    }
}
