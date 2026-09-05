package com.lifeloop.comai.tts

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
class TTSManager(context: Context) {

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
        tts = TextToSpeech(context.applicationContext) { status ->
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
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }
        })
    }

    /** Speak the given text. Stops any currently playing utterance first. */
    fun speak(text: String) {
        if (!enabled || !isInitialized) return
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    /** Stop any currently playing speech. */
    fun stop() {
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
