package com.comai.voice

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Supported Comai languages with STT/TTS locale mappings.
 *
 * [sttLocale]   – primary locale passed to SpeechRecognizer EXTRA_LANGUAGE.
 * [ttsLocale]   – locale used for TextToSpeech engine.
 * [altLocales]  – alternative BCP-47 tags for EXTRA_LANGUAGE_PREFERENCE (mixed-language fallback).
 */
enum class ComaiLanguage(
    val displayName: String,
    val sttLocale: Locale,
    val ttsLocale: Locale,
    val altLocales: List<String>
) {
    ENGLISH(
        displayName = "English",
        sttLocale = Locale("en", "IN"),
        ttsLocale = Locale.US,
        altLocales = emptyList()
    ),
    TAMIL(
        displayName = "தமிழ்",
        sttLocale = Locale("ta", "IN"),
        ttsLocale = Locale("ta", "IN"),
        altLocales = listOf("en-IN")
    ),
    TELUGU(
        displayName = "తెలుగు",
        sttLocale = Locale("te", "IN"),
        ttsLocale = Locale("te", "IN"),
        altLocales = listOf("en-IN")
    ),
    HINDI(
        displayName = "हिन्दी",
        sttLocale = Locale("hi", "IN"),
        ttsLocale = Locale("hi", "IN"),
        altLocales = listOf("en-IN")
    ),
    MALAYALAM(
        displayName = "മലയാളം",
        sttLocale = Locale("ml", "IN"),
        ttsLocale = Locale("ml", "IN"),
        altLocales = listOf("en-IN")
    ),
    TANGLISH(
        displayName = "Tanglish",
        sttLocale = Locale("en", "IN"),       // en-IN handles Tamil+English code-switching best
        ttsLocale = Locale.US,
        altLocales = listOf("ta-IN")
    );

    /** BCP-47 tag for the primary STT locale (e.g. "ta-IN"). */
    val sttTag: String get() = sttLocale.toLanguageTag()
}

/**
 * Thin SharedPreferences wrapper for persisting the user's preferred language.
 */
class LanguagePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguage(): ComaiLanguage {
        val name = prefs.getString(KEY_LANGUAGE, ComaiLanguage.ENGLISH.name)
        return try {
            ComaiLanguage.valueOf(name ?: ComaiLanguage.ENGLISH.name)
        } catch (_: IllegalArgumentException) {
            ComaiLanguage.ENGLISH
        }
    }

    fun setLanguage(language: ComaiLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "comai_language_prefs"
        private const val KEY_LANGUAGE = "preferred_language"
    }
}
