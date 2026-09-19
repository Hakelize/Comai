package com.comai.memory

import com.comai.contextengine.db.Memory
import java.util.Locale
import java.util.regex.Pattern

/**
 * Conservative, deterministic extractor for explicit user statements.
 *
 * Privacy Guarantees:
 * - Only extracts explicitly stated facts (e.g., usual departure time, workplace, college, music preference, favorite tools/topics).
 * - Rejects temporary statements (e.g. "leaving at 5:30 today") UNLESS it is a remember/save directive.
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
        """(?i)\b(?:i\s+work\s+at|my\s+office\s+is\s+at|i\s+work\s+in)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // College / Study patterns: e.g. "I study at Stanford University", "My college is National Tech Institute"
    private val COLLEGE_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+study\s+at|my\s+college\s+is|i\s+go\s+to\s+college\s+at|i\s+go\s+to\s+university\s+at)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Home / City patterns: e.g. "I live in Bengaluru", "My home is in Chennai"
    private val HOME_LOCATION_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+live\s+in|my\s+home\s+is\s+in|i(?:'m|\s+am)\s+from)\s+([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Music preference patterns: e.g. "I like calm music after work", "I like listening to rock music after work"
    private val MUSIC_PREF_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+like\s+(?:listening\s+to\s+)?([a-zA-Z]+)\s+music\s+after\s+work|my\s+preferred\s+after[- ]work\s+music\s+is\s+([a-zA-Z]+))""",
        Pattern.CASE_INSENSITIVE
    )

    // Name patterns: "my name is X", "I am X", "call me X", "people call me X"
    private val NAME_REGEX = Pattern.compile(
        """(?i)\b(?:my\s+name\s+is|call\s+me|people\s+call\s+me|i\s+go\s+by)\s+([A-Za-z][a-zA-Z\s]{1,30})""",
        Pattern.CASE_INSENSITIVE
    )

    // Age pattern: "I am 25 years old", "I'm 22"
    private val AGE_REGEX = Pattern.compile(
        """(?i)\bi(?:'m|\s+am)\s+([0-9]{1,3})\s*(?:years?\s+old)?""",
        Pattern.CASE_INSENSITIVE
    )

    // Hobby/interest: "I enjoy X", "I love X", "I'm into X"
    private val HOBBY_REGEX = Pattern.compile(
        """(?i)\b(?:i\s+enjoy|i\s+love|i(?:'m|\s+am)\s+into|i\s+like\s+to)\s+([^.!?\n]{3,60})""",
        Pattern.CASE_INSENSITIVE
    )

    // Gender pattern: "I am female", "I'm a woman", "my gender is female", "I am male"
    private val GENDER_REGEX = Pattern.compile(
        """(?i)\b(?:i(?:'m|\s+am)\s+(?:a\s+)?(female|male|woman|man|non-binary|girl|boy)|my\s+gender\s+is\s+(female|male|non-binary))""",
        Pattern.CASE_INSENSITIVE
    )

    // Diet: "I am vegetarian", "I'm vegan", "I don't eat meat"
    private val DIET_REGEX = Pattern.compile(
        """(?i)\bi(?:'m|\s+am)\s+(vegetarian|vegan|non-vegetarian|non-veg|pescatarian|gluten[- ]free|dairy[- ]free)""",
        Pattern.CASE_INSENSITIVE
    )

    // Drink habit: "I drink coffee every morning", "I drink tea"
    private val DRINK_REGEX = Pattern.compile(
        """(?i)\bi\s+(?:drink|have)\s+(coffee|tea|green tea|water|juice|milk)(?:\s+every\s+\w+)?""",
        Pattern.CASE_INSENSITIVE
    )

    // Explicit Remember / Save patterns:
    // "Remember that my sister is Maya", "Please remember I am vegan"
    // "Save this: I prefer window seats", "Save that I drink tea"
    // "Note that I am vegetarian"
    private val REMEMBER_DIRECTIVE_REGEX = Pattern.compile(
        """(?i)\b(?:please\s+)?(?:remember|save(?:\s+this)?(?:\s+memory)?|note)(?:\s+this\s*:\s*|\s+that\s+|\s+)([^.!?\n]+)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Attempts to extract a high-confidence, explicit personal memory from the user's text.
     * Returns null if the text is conversational, temporary, uncertain, or doesn't match an explicit statement.
     */
    fun extract(userText: String): Memory? {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check if it's a save/remember directive first — these bypass most rejections
        val isSaveDirective = lower.startsWith("save ") || lower.startsWith("save this") ||
            lower.startsWith("remember ") || lower.startsWith("note that") || lower.startsWith("please remember") ||
            lower.startsWith("note ") || lower.contains("please remember") || lower.contains("don't forget that")

        if (!isSaveDirective && (
            lower.endsWith("?") || lower.startsWith("what ") || lower.startsWith("where ") ||
            lower.startsWith("who ") || lower.startsWith("how ") || lower.startsWith("when ") ||
            lower.startsWith("why ") || lower.startsWith("can you ") || lower.startsWith("do you ")
        )) {
            return null
        }

        // 2. Check uncertainty rejection
        if (UNCERTAIN_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 3. Check temporary context rejection — ONLY if it's NOT a save directive
        if (!isSaveDirective && TEMPORARY_KEYWORDS.any { lower.contains(it) }) {
            return null
        }

        // 4. Explicit Remember/Save directive — highest priority, catches everything
        val remMatcher = REMEMBER_DIRECTIVE_REGEX.matcher(trimmed)
        if (remMatcher.find()) {
            val fact = remMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (fact.isNotBlank() && fact.length in 4..120) {
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

        // 5. Name
        val nameMatcher = NAME_REGEX.matcher(trimmed)
        if (nameMatcher.find()) {
            val name = nameMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (name.isNotBlank() && name.length <= 40 && !name.contains(" am ") && !name.contains(" is ")) {
                return Memory(
                    type = "PERSONAL_KNOWLEDGE",
                    key = "user_name",
                    value = "User's name is $name",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 5.5 Match Gender: e.g. "I am female", "I'm a woman", "my gender is female", "I am male"
        val genderMatcher = GENDER_REGEX.matcher(trimmed)
        if (genderMatcher.find()) {
            val rawGender = (genderMatcher.group(1) ?: genderMatcher.group(2))?.trim()?.lowercase(Locale.ROOT) ?: ""
            val normalizedGender = when (rawGender) {
                "female", "woman", "girl" -> "Female"
                "male", "man", "boy" -> "Male"
                else -> "Non-binary"
            }
            return Memory(
                type = "PERSONAL_KNOWLEDGE",
                key = "user_gender",
                value = "User's gender is $normalizedGender",
                timestampMs = System.currentTimeMillis(),
                source = "USER_EXPLICIT",
                confidence = 1.0f,
                isUserDeletable = true
            )
        }

        // 6. Match Favorite / Preferences: e.g. "My favorite programming language is Python."
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

        // 7. Match Departure
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

        // 8. Match Baseline Wake
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

        // 9. Match Baseline Sleep
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

        // 10. Match Workplace
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

        // 11. Match College / Study
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

        // 12. Match Home Location
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

        // 13. Match Music Preference
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

        // 14. Diet preference
        val dietMatcher = DIET_REGEX.matcher(trimmed)
        if (dietMatcher.find()) {
            val diet = dietMatcher.group(1)?.trim()?.lowercase(Locale.ROOT) ?: ""
            if (diet.isNotBlank()) {
                return Memory(
                    type = "PREFERENCE",
                    key = "diet_preference",
                    value = "User is $diet",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 15. Drink habit
        val drinkMatcher = DRINK_REGEX.matcher(trimmed)
        if (drinkMatcher.find()) {
            val drink = drinkMatcher.group(1)?.trim()?.lowercase(Locale.ROOT) ?: ""
            if (drink.isNotBlank()) {
                return Memory(
                    type = "PREFERENCE",
                    key = "drink_habit_$drink",
                    value = "User drinks $drink",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 0.9f,
                    isUserDeletable = true
                )
            }
        }

        // 16. Age
        val ageMatcher = AGE_REGEX.matcher(trimmed)
        if (ageMatcher.find() && !lower.contains("at") && !lower.contains("be")) {
            val age = ageMatcher.group(1)?.trim() ?: ""
            val ageInt = age.toIntOrNull()
            if (ageInt != null && ageInt in 10..120) {
                return Memory(
                    type = "PERSONAL_KNOWLEDGE",
                    key = "user_age",
                    value = "User is $age years old",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            }
        }

        // 17. Hobby/interest (only if explicit "I enjoy/love/am into")
        val hobbyMatcher = HOBBY_REGEX.matcher(trimmed)
        if (hobbyMatcher.find()) {
            val hobby = hobbyMatcher.group(1)?.trim()?.removeSuffix(".") ?: ""
            if (hobby.isNotBlank() && hobby.length in 3..60) {
                val words = hobby.lowercase(Locale.ROOT).split("\\s+".toRegex()).take(2).joinToString("_")
                return Memory(
                    type = "PREFERENCE",
                    key = "hobby_$words",
                    value = "User enjoys $hobby",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 0.85f,
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
