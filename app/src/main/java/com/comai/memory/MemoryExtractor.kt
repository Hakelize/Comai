package com.comai.memory

import com.comai.contextengine.db.Memory
import java.util.Locale
import java.util.regex.Pattern

/**
 * Conservative, deterministic extractor for explicit user statements.
 *
 * Privacy Guarantees:
 * - Only extracts explicitly stated facts (e.g., usual departure time, workplace, college, music preference, favorite tools/topics).
 * - Rejects temporary statements (e.g. "leaving at 5:30 today").
 * - Rejects uncertain statements (e.g. "I think I usually leave around 5:30").
 * - Rejects general questions and conversational chit-chat.
 * - Never infers psychological states, emotions, mental health, or personality traits.
 */
object MemoryExtractor {

    private val UNCERTAIN_KEYWORDS = listOf("i think", "maybe", "probably", "not sure", "might", "perhaps", "guess")
    private val TEMPORARY_KEYWORDS = listOf("today", "right now", "just for now", "tonight", "this evening", "this morning", "tomorrow")

    // Favorite patterns: e.g. "My favorite programming language is Python", "My favorite food is Dosa"
    private val FAVORITE_REGEX = Pattern.compile(
        """(?i)\bmy\s+favorite\s+([a-zA-Z\s]{2,30})\s+is\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Departure patterns: e.g. "I usually leave college at 5 PM", "I usually leave work around 5:30", "My usual office departure is 6 PM"
    private val DEPARTURE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+usually\s+leave\s+(work|office|college|school|campus|home)\s+(?:around|at)|my\s+usual\s+(work|office|college|school|campus|home)\s+departure\s+is)\s+([0-9]{1,2}(?::[0-9]{2})?(?:\s*(?:am|pm))?)""",
        Pattern.CASE_INSENSITIVE
    )

    // Wake / Sleep baseline patterns
    private val WAKE_REGEX = Pattern.compile(
        """(?i)\bi\s+usually\s+wake\s+up\s+(?:around|at)\s+([0-9]{1,2}(?::[0-9]{2})?(?:\s*(?:am|pm))?)""",
        Pattern.CASE_INSENSITIVE
    )
    private val SLEEP_REGEX = Pattern.compile(
        """(?i)\bi\s+usually\s+(?:go\s+to\s+bed|sleep)\s+(?:around|at)\s+([0-9]{1,2}(?::[0-9]{2})?(?:\s*(?:am|pm))?)""",
        Pattern.CASE_INSENSITIVE
    )

    // Workplace patterns: e.g. "I work at Tech Hub Office", "My office is at Downtown Tower"
    private val WORKPLACE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+work\s+at|my\s+office\s+is\s+at)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // College / Study patterns: e.g. "I study at Stanford University", "My college is National Tech Institute"
    private val COLLEGE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+study\s+at|my\s+college\s+is|i\s+go\s+to\s+college\s+at)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Home / City patterns: e.g. "I live in Bengaluru", "My home is in Chennai"
    private val HOME_LOCATION_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+live\s+in|my\s+home\s+is\s+in)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Music preference patterns: e.g. "I like calm music after work", "I like listening to rock music after work"
    private val MUSIC_PREF_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+like\s+(?:listening\s+to\s+)?([a-zA-Z]+)\s+music\s+after\s+work|my\s+preferred\s+after[- ]work\s+music\s+is\s+([a-zA-Z]+))""",
        Pattern.CASE_INSENSITIVE
    )

    // Explicit Remember patterns: e.g. "Remember that my sister is Maya", "Please remember I am vegan"
    private val REMEMBER_DIRECTIVE_REGEX = Pattern.compile(
        """(?i)\b(?:please\s+)?remember\s+(?:that\s+)?([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Attempts to extract a high-confidence, explicit personal memory from the user's text.
     * Returns null if the text is conversational, temporary, uncertain, or doesn't match an explicit statement.
     */
    fun extract(userText: String): Memory? {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check question rejection
        if (lower.endsWith("?") || lower.startsWith("what ") || lower.startsWith("where ") ||
            lower.startsWith("who ") || lower.startsWith("how ") || lower.startsWith("when ") ||
            lower.startsWith("why ") || lower.startsWith("can you ") || lower.startsWith("do you ")
        ) {
            return null
        }

        // 2. Check uncertainty rejection
        if (UNCERTAIN_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 3. Check temporary context rejection
        if (TEMPORARY_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 4. Match Favorite / Preferences: e.g. "My favorite programming language is Python."
        val favMatcher = FAVORITE_REGEX.matcher(trimmed)
        if (favMatcher.find()) {
            val itemRaw = favMatcher.group(1)?.trim() ?: ""
            val valRaw = favMatcher.group(2)?.trim()?.removeSuffix(".") ?: ""
            if (itemRaw.isNotBlank() && valRaw.isNotBlank() && valRaw.length <= 60) {
                val cleanKey = "favorite_" + itemRaw.lowercase(Locale.ROOT).replace("\\s+".toRegex(), "_")
                val cleanVal = "User's favorite $itemRaw is $valRaw"
                return Memory(
                    type = "PREFERENCE",
                    key = cleanKey,
                    value = cleanVal,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 5. Match Departure: e.g. "I usually leave college at 5 PM", "I usually leave work around 5:30"
        val depMatcher = DEPARTURE_REGEX.matcher(trimmed)
        if (depMatcher.find()) {
            val place = (depMatcher.group(1) ?: depMatcher.group(2))?.trim()?.lowercase(Locale.ROOT) ?: "work"
            val rawTime = depMatcher.group(3)?.trim() ?: ""
            val normalizedTime = normalizeTime(rawTime)
            if (normalizedTime.isNotBlank()) {
                val isWork = place == "work" || place == "office"
                val key = if (isWork) "preferred_work_departure" else "preferred_${place}_departure"
                val type = if (isWork) "PREFERENCE" else "CONTEXT"
                return Memory(
                    type = type,
                    key = key,
                    value = normalizedTime,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 6. Match Baseline Wake / Sleep
        val wakeMatcher = WAKE_REGEX.matcher(trimmed)
        if (wakeMatcher.find()) {
            val rawTime = wakeMatcher.group(1)?.trim() ?: ""
            val normalized = normalizeTime(rawTime)
            if (normalized.isNotBlank()) {
                return Memory(
                    type = "CONTEXT",
                    key = "baseline_wake_time",
                    value = "User usually wakes up around $normalized",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        val sleepMatcher = SLEEP_REGEX.matcher(trimmed)
        if (sleepMatcher.find()) {
            val rawTime = sleepMatcher.group(1)?.trim() ?: ""
            val normalized = normalizeTime(rawTime)
            if (normalized.isNotBlank()) {
                return Memory(
                    type = "CONTEXT",
                    key = "baseline_sleep_time",
                    value = "User usually sleeps around $normalized",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 7. Match Workplace
        val workMatcher = WORKPLACE_REGEX.matcher(trimmed)
        if (workMatcher.find()) {
            val workplace = workMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (workplace.isNotBlank() && workplace.length <= 60) {
                return Memory(
                    type = "CONTEXT",
                    key = "workplace",
                    value = workplace,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 8. Match College / Study
        val collegeMatcher = COLLEGE_REGEX.matcher(trimmed)
        if (collegeMatcher.find()) {
            val college = collegeMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (college.isNotBlank() && college.length <= 60) {
                return Memory(
                    type = "CONTEXT",
                    key = "college",
                    value = college,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 9. Match Home Location
        val homeMatcher = HOME_LOCATION_REGEX.matcher(trimmed)
        if (homeMatcher.find()) {
            val location = homeMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (location.isNotBlank() && location.length <= 60) {
                return Memory(
                    type = "CONTEXT",
                    key = "home_location",
                    value = location,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 10. Match Music Preference
        val musicMatcher = MUSIC_PREF_REGEX.matcher(trimmed)
        if (musicMatcher.find()) {
            val style = (musicMatcher.group(1) ?: musicMatcher.group(2))?.trim()?.lowercase(Locale.ROOT) ?: ""
            if (style.isNotBlank()) {
                return Memory(
                    type = "PREFERENCE",
                    key = "after_work_music",
                    value = style,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 11. Match Explicit "Remember that..."
        val remMatcher = REMEMBER_DIRECTIVE_REGEX.matcher(trimmed)
        if (remMatcher.find()) {
            val fact = remMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (fact.isNotBlank() && fact.length in 4..100) {
                val words = fact.lowercase(Locale.ROOT).split("\\s+".toRegex()).take(3).joinToString("_")
                return Memory(
                    type = "PERSONAL_KNOWLEDGE",
                    key = "note_$words",
                    value = "User noted: $fact",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        return null
    }

    /**
     * Normalizes time strings like "5:30", "5:30 pm", "17:30", "5" to 24-hour HH:mm.
     */
    fun normalizeTime(raw: String): String {
        val clean = raw.lowercase(Locale.ROOT).trim()
        val isPm = clean.contains("pm")
        val isAm = clean.contains("am")
        val timePart = clean.replace("am", "").replace("pm", "").trim()

        val parts = timePart.split(":")
        var hours = parts[0].toIntOrNull() ?: return ""
        val minutes = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0

        if (isPm && hours < 12) {
            hours += 12
        } else if (isAm && hours == 12) {
            hours = 0
        } else if (!isPm && !isAm && hours in 1..7) {
            // Context heuristic: typical departure between 1 and 7 without am/pm means PM (13:00 - 19:00)
            hours += 12
        }

        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}

