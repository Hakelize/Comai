package com.comai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Lifecycle-aware wrapper around Android's [TextToSpeech] engine.
 * Exposes reactive state for UI binding.
 */
open class TTSManager(context: Context? = null) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var targetLocale: Locale = Locale.US

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
    var onBeforeSpeak: (() -> Unit)? = null
    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    init {
        context?.applicationContext?.let { appContext ->
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { engine ->
                        isInitialized = true
                        _isReady.value = true
                        applyLanguage(targetLocale)
                        engine.setSpeechRate(speechRate)
                        engine.setPitch(pitch)
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
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    android.util.Log.w("TTSManager", "TTS_ERROR: utteranceId=$utteranceId")
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.invoke()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    android.util.Log.w("TTSManager", "TTS_ERROR: utteranceId=$utteranceId, errorCode=$errorCode")
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.invoke()
                    }
                }
            })
        }
    }

    /**
     * Suspends until the TTS engine is fully initialized and ready.
     * Prevents race conditions on cold start.
     */
    suspend fun awaitReady(timeoutMs: Long = 2500L): Boolean {
        if (isInitialized && _isReady.value) return true
        return try {
            withTimeoutOrNull(timeoutMs) {
                _isReady.first { it }
            } ?: (isInitialized && _isReady.value)
        } catch (e: Exception) {
            isInitialized && _isReady.value
        }
    }

    /** Speak the given text. Stops any currently playing utterance first. */
    open fun speak(text: String): Boolean {
        return speak(text, onDone = null)
    }

    open fun speak(text: String, onDone: (() -> Unit)?): Boolean {
        if (!enabled || !isInitialized || text.isBlank()) {
            android.util.Log.d("TTSManager", "TTS skipped: enabled=$enabled, initialized=$isInitialized")
            onDone?.invoke()
            return false
        }
        try {
            onBeforeSpeak?.invoke()
        } catch (e: Exception) {
            android.util.Log.w("TTSManager", "onBeforeSpeak exception: ${e.message}")
        }
        return try {
            android.util.Log.i("TTSManager", "TTS started: length=${text.length}")
            val utteranceId = UUID.randomUUID().toString()
            if (onDone != null) {
                utteranceCallbacks[utteranceId] = onDone
            }
            val result = tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
            val success = result == TextToSpeech.SUCCESS
            if (!success) {
                android.util.Log.w("TTSManager", "TTS failure: queue returned $result")
                utteranceCallbacks.remove(utteranceId)?.invoke()
            }
            success
        } catch (e: Exception) {
            android.util.Log.w("TTSManager", "TTS failure: exception during speech synthesis: ${e.message}")
            onDone?.invoke()
            false
        }
    }

    /**
     * Switch the TTS output language.
     * If TTS is not yet initialized, caches [locale] to apply upon init completion.
     * Checks availability first; falls back to Locale.US if [locale] is unsupported.
     * @return true if the requested locale was set, false if fallback was used or initialization is pending.
     */
    open fun setLanguage(locale: Locale): Boolean {
        targetLocale = locale
        if (!isInitialized) {
            return false
        }
        return applyLanguage(locale)
    }

    private fun applyLanguage(locale: Locale): Boolean {
        val engine = tts ?: return false
        val availability = engine.isLanguageAvailable(locale)
        return if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.setLanguage(locale)
            android.util.Log.i("TTSManager", "TTS_LANGUAGE_SET: ${locale.toLanguageTag()}")
            true
        } else {
            android.util.Log.w("TTSManager", "TTS_LANGUAGE_UNAVAILABLE: ${locale.toLanguageTag()} (code=$availability), falling back to en-US")
            engine.setLanguage(Locale.US)
            false
        }
    }

    /** Stop any currently playing speech. */
    open fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        utteranceCallbacks.clear()
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
