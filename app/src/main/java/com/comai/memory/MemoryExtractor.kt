package com.comai.memory

import com.comai.contextengine.db.Memory
import java.util.Locale
import java.util.regex.Pattern

/**
 * Conservative, deterministic extractor for explicit user statements.
 *
 * Privacy Guarantees:
 * - Only extracts explicitly stated facts (e.g., usual departure time, workplace, music preference).
 * - Rejects temporary statements (e.g. "leaving at 5:30 today").
 * - Rejects uncertain statements (e.g. "I think I usually leave around 5:30").
 * - Never infers psychological states, emotions, mental health, or personality traits.
 */
object MemoryExtractor {

    private val UNCERTAIN_KEYWORDS = listOf("i think", "maybe", "probably", "not sure", "might", "perhaps", "guess")
    private val TEMPORARY_KEYWORDS = listOf("today", "right now", "just for now", "tonight", "this evening", "this morning")

    // Departure patterns: e.g. "I usually leave work around 5:30", "I usually leave work at 6:00 PM"
    private val WORK_DEPARTURE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+usually\s+leave\s+work\s+(?:around|at)|my\s+usual\s+work\s+departure\s+is)\s+([0-9]{1,2}(?::[0-9]{2})?(?:\s*(?:am|pm))?)""",
        Pattern.CASE_INSENSITIVE
    )

    // Workplace patterns: e.g. "I work at Tech Hub Office", "My office is at Downtown Tower"
    private val WORKPLACE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+work\s+at|my\s+office\s+is\s+at)\s+([^.!?]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Music preference patterns: e.g. "I like calm music after work", "I like listening to calm music after work"
    private val MUSIC_PREF_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+like\s+(?:listening\s+to\s+)?([a-zA-Z]+)\s+music\s+after\s+work|my\s+preferred\s+after[- ]work\s+music\s+is\s+([a-zA-Z]+))""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Attempts to extract a high-confidence, explicit personal memory from the user's text.
     * Returns null if the text is conversational, temporary, uncertain, or doesn't match an explicit statement.
     */
    fun extract(userText: String): Memory? {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check uncertainty rejection
        if (UNCERTAIN_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 2. Check temporary context rejection
        if (TEMPORARY_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 3. Match work departure
        val depMatcher = WORK_DEPARTURE_REGEX.matcher(trimmed)
        if (depMatcher.find()) {
            val rawTime = depMatcher.group(1)?.trim() ?: ""
            val normalizedTime = normalizeTime(rawTime)
            if (normalizedTime.isNotBlank()) {
                return Memory(
                    type = "PREFERENCE",
                    key = "preferred_work_departure",
                    value = normalizedTime,
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 4. Match workplace
        val workMatcher = WORKPLACE_REGEX.matcher(trimmed)
        if (workMatcher.find()) {
            val workplace = workMatcher.group(1)?.trim() ?: ""
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

        // 5. Match music preference
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

        return null
    }

    /**
     * Normalizes time strings like "5:30", "5:30 pm", "17:30", "5" to 24-hour HH:mm.
     */
    private fun normalizeTime(raw: String): String {
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
            // Context heuristic: typical work departure between 1 and 7 without am/pm means PM (13:00 - 19:00)
            hours += 12
        }

        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }
}
