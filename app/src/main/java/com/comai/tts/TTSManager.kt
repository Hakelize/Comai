package com.comai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Lifecycle-aware wrapper around Android's [TextToSpeech] engine.
 * Exposes reactive state for UI binding.
 */
open class TTSManager(context: Context? = null) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    var enabled: Boolean = true

    init {
        context?.applicationContext?.let { appContext ->
            tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale.US)
                    if (result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        isInitialized = true
                        _isReady.value = true
                        engine.setSpeechRate(speechRate)
                        engine.setPitch(pitch)
                    }
                }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                android.util.Log.i("TTSManager", "TTS_STARTED: utteranceId=$utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                android.util.Log.i("TTSManager", "TTS_COMPLETED: utteranceId=$utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                android.util.Log.w("TTSManager", "TTS_ERROR: utteranceId=$utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                android.util.Log.w("TTSManager", "TTS_ERROR: utteranceId=$utteranceId, errorCode=$errorCode")
            }
        })
        }
    }

    /** Speak the given text. Stops any currently playing utterance first. */
    open fun speak(text: String): Boolean {
        if (!enabled || !isInitialized || text.isBlank()) {
            android.util.Log.d("TTSManager", "TTS skipped: enabled=$enabled, initialized=$isInitialized")
            return false
        }
        return try {
            android.util.Log.i("TTSManager", "TTS started: length=${text.length}")
            val result = tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString()
            )
            val success = result == TextToSpeech.SUCCESS
            if (!success) {
                android.util.Log.w("TTSManager", "TTS failure: queue returned $result")
            }
            success
        } catch (e: Exception) {
            android.util.Log.w("TTSManager", "TTS failure: exception during speech synthesis: ${e.message}")
            false
        }
    }

    /** Stop any currently playing speech. */
    open fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /** Release TTS resources. Call this when the lifecycle owner is destroyed. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isReady.value = false
        _isSpeaking.value = false
    }
}
