package com.comai.voice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Supported Comai languages with STT/TTS and UI locale mappings.
 *
 * [uiLocaleTag] - BCP-47 tag for UI string resources (e.g. "ta", "hi", "te", "ml", "en").
 * [sttLocale]   - primary locale passed to SpeechRecognizer EXTRA_LANGUAGE.
 * [ttsLocale]   - locale used for TextToSpeech engine.
 * [altLocales]  - alternative BCP-47 tags for multilingual recognition.
 */
enum class ComaiLanguage(
    val displayName: String,
    val uiLocaleTag: String,
    val sttLocale: Locale,
    val ttsLocale: Locale,
    val altLocales: List<String>
) {
    ENGLISH(
        displayName = "English",
        uiLocaleTag = "en",
        sttLocale = Locale("en", "IN"),
        ttsLocale = Locale.US,
        altLocales = emptyList()
    ),
    TAMIL(
        displayName = "தமிழ்",
        uiLocaleTag = "ta",
        sttLocale = Locale("ta", "IN"),
        ttsLocale = Locale("ta", "IN"),
        altLocales = listOf("en-IN", "hi-IN", "te-IN", "ml-IN")
    ),
    TELUGU(
        displayName = "తెలుగు",
        uiLocaleTag = "te",
        sttLocale = Locale("te", "IN"),
        ttsLocale = Locale("te", "IN"),
        altLocales = listOf("en-IN", "ta-IN", "hi-IN", "ml-IN")
    ),
    HINDI(
        displayName = "हिन्दी",
        uiLocaleTag = "hi",
        sttLocale = Locale("hi", "IN"),
        ttsLocale = Locale("hi", "IN"),
        altLocales = listOf("en-IN", "ta-IN", "te-IN", "ml-IN")
    ),
    MALAYALAM(
        displayName = "മലയാളം",
        uiLocaleTag = "ml",
        sttLocale = Locale("ml", "IN"),
        ttsLocale = Locale("ml", "IN"),
        altLocales = listOf("en-IN", "ta-IN", "hi-IN", "te-IN")
    ),
    TANGLISH(
        displayName = "Tanglish",
        uiLocaleTag = "en",
        sttLocale = Locale("en", "IN"),       // en-IN handles Tamil+English code-switching best
        ttsLocale = Locale.US,
        altLocales = listOf("ta-IN", "hi-IN", "te-IN", "ml-IN")
    );

    /** BCP-47 tag for the primary STT locale (e.g. "ta-IN"). */
    val sttTag: String get() = sttLocale.toLanguageTag()

    /** Locale for UI resources. */
    val uiLocale: Locale get() = Locale(uiLocaleTag)
}

/**
 * Manages dynamic runtime application locale switching and synchronization.
 */
object AppLocaleManager {
    private const val TAG = "AppLocaleManager"

    fun applyLocale(context: Context, language: ComaiLanguage) {
        val tag = language.uiLocaleTag
        Log.i(TAG, "APPLYING_LOCALE: tag=$tag for lang=${language.name}")

        try {
            val appLocales = LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(appLocales)
        } catch (e: Exception) {
            Log.w(TAG, "AppCompatDelegate.setApplicationLocales error: ${e.message}")
        }

        try {
            val locale = Locale(tag)
            Locale.setDefault(locale)
            val resources = context.resources
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            Log.w(TAG, "resources.updateConfiguration error: ${e.message}")
        }
    }
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
