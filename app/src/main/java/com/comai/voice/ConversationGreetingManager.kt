package com.comai.voice

import java.util.Calendar

/**
 * Generates dynamic, context-aware conversational spoken greetings for Comai voice interaction.
 *
 * Rules:
 * - Follows the SELECTED UI LANGUAGE by default.
 * - The user's name is dynamically sourced from the saved user profile.
 * - NEVER hardcodes any specific user name (e.g. Arun, Rakesh, Priya).
 * - If profile name is blank/empty, uses a friendly natural fallback in the selected language.
 * - Greeting language setting does NOT restrict what the user can speak afterward.
 */
object ConversationGreetingManager {

    fun generateGreeting(
        userName: String,
        uiLanguage: ComaiLanguage = ComaiLanguage.ENGLISH,
        isReturning: Boolean = false,
        calendar: Calendar = Calendar.getInstance()
    ): String {
        val cleanName = userName.trim()
        val hasName = cleanName.isNotBlank()

        return when (uiLanguage) {
            ComaiLanguage.TAMIL -> {
                if (hasName) {
                    if (isReturning) {
                        listOf(
                            "வணக்கம் $cleanName, நான் எப்படி உதவலாம்?",
                            "மீண்டும் நல்வரவு $cleanName, நான் எப்படி உதவலாம்?",
                            "வணக்கம் $cleanName, உங்களுக்கு என்ன உதவி வேண்டும்?"
                        ).random()
                    } else {
                        "வணக்கம் $cleanName, நான் எப்படி உதவலாம்?"
                    }
                } else {
                    "வணக்கம், நான் எப்படி உதவலாம்?"
                }
            }
            ComaiLanguage.HINDI -> {
                if (hasName) {
                    if (isReturning) {
                        listOf(
                            "नमस्ते $cleanName, मैं आपकी कैसे मदद कर सकता हूँ?",
                            "वापसी पर स्वागत है $cleanName, मैं आपकी कैसे मदद कर सकता हूँ?",
                            "नमस्ते $cleanName, मैं आपकी क्या सहायता कर सकता हूँ?"
                        ).random()
                    } else {
                        "नमस्ते $cleanName, मैं आपकी कैसे मदद कर सकता हूँ?"
                    }
                } else {
                    "नमस्ते, मैं आपकी कैसे मदद कर सकता हूँ?"
                }
            }
            ComaiLanguage.TELUGU -> {
                if (hasName) {
                    if (isReturning) {
                        listOf(
                            "నమస్కారం $cleanName, నేను ఎలా సహాయం చేయగలను?",
                            "మళ్లీ స్వాగతం $cleanName, నేను ఎలా సహాయం చేయగలను?",
                            "నమస్కారం $cleanName, మీకు ఏమి సహాయం కావాలి?"
                        ).random()
                    } else {
                        "నమస్కారం $cleanName, నేను ఎలా సహాయం చేయగలను?"
                    }
                } else {
                    "నమస్కారం, నేను ఎలా సహాయం చేయగలను?"
                }
            }
            ComaiLanguage.MALAYALAM -> {
                if (hasName) {
                    if (isReturning) {
                        listOf(
                            "നമസ്കാരം $cleanName, ഞാൻ എങ്ങനെ സഹായിക്കാം?",
                            "വീണ്ടും സ്വാഗതം $cleanName, ഞാൻ എങ്ങനെ സഹായിക്കാം?",
                            "നമസ്കാരം $cleanName, എനിക്ക് എന്താണ് ചെയ്യേണ്ടത്?"
                        ).random()
                    } else {
                        "നമസ്കാരം $cleanName, ഞാൻ എങ്ങനെ സഹായിക്കാം?"
                    }
                } else {
                    "നമസ്കാരം, ഞാൻ എങ്ങനെ സഹായിക്കാം?"
                }
            }
            ComaiLanguage.ENGLISH, ComaiLanguage.TANGLISH -> {
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                if (hasName) {
                    if (isReturning) {
                        listOf(
                            "Welcome back $cleanName, how can I help?",
                            "Hello $cleanName, how may I help you?",
                            "Hi $cleanName, what can I do for you?"
                        ).random()
                    } else {
                        when (hour) {
                            in 5..11 -> "Good morning $cleanName, how may I help you?"
                            in 12..16 -> "Good afternoon $cleanName, how may I help you?"
                            in 17..21 -> "Good evening $cleanName, how may I help you?"
                            else -> "Hello $cleanName, how may I help you?"
                        }
                    }
                } else {
                    if (isReturning) {
                        "Welcome back, how may I help you?"
                    } else {
                        "Hello, how may I help you?"
                    }
                }
            }
        }
    }
}
