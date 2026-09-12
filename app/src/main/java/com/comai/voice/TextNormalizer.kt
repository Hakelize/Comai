package com.comai.voice

import android.util.Log

/**
 * Lightweight keyword enricher for multilingual / Tanglish input.
 *
 * Does NOT translate. Appends English intent keywords to the original text
 * so the AI engine's keyword-based matching can fire regardless of input language.
 *
 * The original user text is always preserved as-is in chat bubbles.
 * Only the text sent to AIEngine.process() is enriched.
 *
 * When a real LLM replaces MockEngine this normalizer becomes optional,
 * since LLMs handle multilingual input natively.
 */
class TextNormalizer {

    /**
     * Returns enriched text: original + appended English intent keywords.
     * If no keywords match, returns the original text unchanged.
     */
    fun normalize(text: String, language: ComaiLanguage): String {
        // English input needs no enrichment
        if (language == ComaiLanguage.ENGLISH) return text

        val lower = text.lowercase()
        val matched = mutableSetOf<String>()

        for ((pattern, keywords) in KEYWORD_MAP) {
            if (lower.contains(pattern)) {
                matched.addAll(keywords)
            }
        }

        return if (matched.isEmpty()) {
            Log.d(TAG, "NORMALIZE: no keyword matches for lang=${language.name}")
            text
        } else {
            val enriched = "$text [${matched.joinToString(" ")}]"
            Log.i(TAG, "NORMALIZE: lang=${language.name}, keywords=${matched.joinToString(",")}")
            enriched
        }
    }

    companion object {
        private const val TAG = "TextNormalizer"

        /**
         * Map of Tamil/Hindi/Telugu/Malayalam words/phrases → English intent keywords.
         * Covers the MockEngine's scenario keywords: morning, commute, traffic, food,
         * lunch, medication, medicine, office, work, leaving, evening, event, reminder,
         * memory, remember, sleep, night, tribe, run.
         */
        private val KEYWORD_MAP: Map<String, List<String>> = mapOf(
            // ─── Tamil (script + transliteration) ─────────────────────
            "காலை" to listOf("morning"),
            "kaalaiyil" to listOf("morning"),
            "kalai" to listOf("morning"),
            "காலையில" to listOf("morning"),

            "அலுவலகம்" to listOf("office", "work"),
            "office" to listOf("office", "work"),
            "அலுவலகம" to listOf("office", "work"),

            "கிளம்பு" to listOf("leaving"),
            "kelambu" to listOf("leaving"),
            "kelamburen" to listOf("leaving"),
            "கிளம்புறேன்" to listOf("leaving"),
            "போறேன்" to listOf("leaving"),
            "poren" to listOf("leaving"),

            "சாப்பாடு" to listOf("food", "lunch"),
            "saapadu" to listOf("food", "lunch"),
            "சாப்பிடு" to listOf("food", "lunch", "eat"),
            "saapidu" to listOf("food", "lunch", "eat"),
            "சாப்பிட" to listOf("food", "lunch"),
            "பசிக்குது" to listOf("hungry", "food"),
            "pasikkuthu" to listOf("hungry", "food"),

            "மருந்து" to listOf("medication", "medicine"),
            "marundu" to listOf("medication", "medicine"),
            "மருந்த" to listOf("medication", "medicine"),
            "tablet" to listOf("medication", "medicine"),

            "போக்குவரத்து" to listOf("traffic", "commute"),
            "traffic" to listOf("traffic", "commute"),
            "வழி" to listOf("route", "commute"),
            "vazhi" to listOf("route", "commute"),

            "மாலை" to listOf("evening"),
            "maalai" to listOf("evening"),
            "இரவு" to listOf("night", "sleep"),
            "iravu" to listOf("night"),
            "thoongu" to listOf("sleep", "night"),
            "தூங்கு" to listOf("sleep", "night"),

            "நிகழ்வு" to listOf("event"),
            "nigazhvu" to listOf("event"),
            "ஓடு" to listOf("run"),
            "oodu" to listOf("run"),

            "நினைவு" to listOf("memory", "remember"),
            "ninaivu" to listOf("memory", "remember"),
            "gnyabagam" to listOf("memory", "remember"),
            "ஞாபகம்" to listOf("memory", "remember"),

            "reminder" to listOf("reminder"),
            "நினைவூட்டு" to listOf("reminder"),

            // ─── Hindi ────────────────────────────────────────────────
            "सुबह" to listOf("morning"),
            "subah" to listOf("morning"),
            "ऑफिस" to listOf("office", "work"),
            "दफ्तर" to listOf("office", "work"),
            "daftar" to listOf("office", "work"),
            "निकल" to listOf("leaving"),
            "nikal" to listOf("leaving"),
            "खाना" to listOf("food", "lunch"),
            "khaana" to listOf("food", "lunch"),
            "भूख" to listOf("hungry", "food"),
            "bhookh" to listOf("hungry", "food"),
            "दवाई" to listOf("medication", "medicine"),
            "dawai" to listOf("medication", "medicine"),
            "ट्रैफ़िक" to listOf("traffic", "commute"),
            "शाम" to listOf("evening"),
            "shaam" to listOf("evening"),
            "रात" to listOf("night", "sleep"),
            "raat" to listOf("night"),
            "सोना" to listOf("sleep", "night"),
            "याद" to listOf("memory", "remember"),
            "yaad" to listOf("memory", "remember"),

            // ─── Telugu ───────────────────────────────────────────────
            "ఉదయం" to listOf("morning"),
            "ఆఫీస్" to listOf("office", "work"),
            "బయల్దేరు" to listOf("leaving"),
            "భోజనం" to listOf("food", "lunch"),
            "మందు" to listOf("medication", "medicine"),
            "ట్రాఫిక్" to listOf("traffic", "commute"),
            "సాయంత్రం" to listOf("evening"),
            "రాత్రి" to listOf("night", "sleep"),
            "జ్ఞాపకం" to listOf("memory", "remember"),

            // ─── Malayalam ────────────────────────────────────────────
            "രാവിലെ" to listOf("morning"),
            "ഓഫീസ്" to listOf("office", "work"),
            "പുറപ്പെടു" to listOf("leaving"),
            "ഭക്ഷണം" to listOf("food", "lunch"),
            "മരുന്ന്" to listOf("medication", "medicine"),
            "ട്രാഫിക്" to listOf("traffic", "commute"),
            "വൈകുന്നേരം" to listOf("evening"),
            "രാത്രി" to listOf("night", "sleep"),
            "ഓർമ്മ" to listOf("memory", "remember")
        )
    }
}
