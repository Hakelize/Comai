package com.comai.engine

import com.comai.engine.models.AIResponse
import com.comai.engine.models.ContextInput
import kotlinx.coroutines.delay

/**
 * Mock implementation of [AIEngine] that returns context-aware responses
 * grounded in the user's [ContextInput.retrievedData] and [ContextInput.memoryContext].
 *
 * Priority order:
 * 1. Retrieved personal memories / onboarding data → personalised answer
 * 2. Scenario keyword match → scenario-specific hardcoded response
 * 3. Generic fallback
 *
 * Used when GemmaEngine is not yet loaded (model file missing) or during testing.
 */
class MockEngine : AIEngine {

    override suspend fun process(context: ContextInput): AIResponse {
        // Simulate realistic inference latency
        delay((400L..900L).random())

        val task = context.task.lowercase()
        val time = context.contextSignals.time.lowercase()

        return when {
            // --- Priority 0: Device Time & Date Query ---
            isTimeOrDateQuery(task) -> {
                val now = java.util.Calendar.getInstance()
                val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(now.time)
                val dateStr = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(now.time)
                val tz = java.util.TimeZone.getDefault().getDisplayName(false, java.util.TimeZone.SHORT)
                AIResponse(
                    action = "time_query",
                    speech = "It's $timeStr on $dateStr.",
                    displayText = "🕒 It is currently **$timeStr** ($tz) on **$dateStr**.",
                    metadata = mapOf("scenario" to "current_time", "time" to timeStr, "date" to dateStr)
                )
            }

            // --- Priority 1: User Memory Query (Manual user intent strictly prioritized) ---
            task.contains("memory") || task.contains("remember") ||
            task.contains("know about me") || task.contains("what do you") ||
            task.contains("what time do i") || task.contains("where do i") ||
            task.contains("recall") || task.contains("my work") || task.contains("my office") ||
            task.contains("my schedule") || task.contains("my plan") || task.contains("my routine") ||
            task.contains("my college") || task.contains("who am i") || task.contains("my name") ||
            task.contains("about me") || task.contains("do you know me") -> {
                val retrieved = context.retrievedData?.trim()
                    ?: context.memoryContext?.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" }
                if (!retrieved.isNullOrBlank()) {
                    AIResponse(
                        action = "memory_recall",
                        speech = "Here's what I remember: " + retrieved.replace("\n", ". ").replace("-", "").trim(),
                        displayText = "🧠 Here's what I remember about you:\n\n$retrieved\n\n🔒 You can review or delete any of these in Memory settings.",
                        metadata = mapOf("scenario" to "memory_recall", "has_memories" to "true")
                    )
                } else {
                    AIResponse(
                        action = "memory_recall",
                        speech = "I don't have any saved memories about you yet. You can tell me things like where you work or when you usually leave, and I'll remember them.",
                        displayText = "🧠 I don't have any saved memories about you yet.\n\nYou can tell me things like:\n• \"I usually leave work around 5:30\"\n• \"I work at Tech Hub Office\"\n• \"I like calm music after work\"\n\n🔒 Everything stays on your device and can be deleted anytime.",
                        metadata = mapOf("scenario" to "memory_recall", "has_memories" to "false")
                    )
                }
            }

            // --- Priority 1.5: Multi-Turn Conversational Follow-Up Query ---
            (task.contains("what did i just") || task.contains("what was that") || task.contains("what did you say") || task.contains("repeat that")) &&
            !context.conversationHistory.isNullOrEmpty() -> {
                val lastUserTurn = context.conversationHistory.lastOrNull { it.role == "user" }?.content
                val lastModelTurn = context.conversationHistory.lastOrNull { it.role == "model" }?.content
                val text = if (task.contains("what did i") && lastUserTurn != null) {
                    "Just before this, you said: \"$lastUserTurn\"."
                } else if (lastModelTurn != null) {
                    "Earlier I mentioned: \"$lastModelTurn\"."
                } else {
                    "We were just chatting about your schedule and day."
                }
                AIResponse(
                    action = "conversation_continuity",
                    speech = text,
                    displayText = "💬 $text",
                    metadata = mapOf("scenario" to "multi_turn_continuity")
                )
            }

            // --- Priority 2: Overtime / Late Office Check-in ---
            task.contains("overtime") || task.contains("overtime_checkin") ||
            (context.contextSignals.routineDeviation && (task.contains("check-in") || task.contains("office"))) ->
                AIResponse(
                    action = "check_in",
                    speech = "You're leaving later than usual today. How was work?",
                    displayText = "You're leaving later than usual today. How was work?",
                    metadata = mapOf("scenario" to "overtime_checkin", "deviation" to "true", "location" to context.contextSignals.location)
                )

            // --- Morning / Wake Routine ---
            task.contains("morning") || task.contains("wake") ||
            task.contains("morning_routine") || ((task.isBlank() || task == "greeting") && time.contains("morning")) ->
                AIResponse(
                    action = "greeting",
                    speech = buildPersonalisedGreeting(context),
                    displayText = buildPersonalisedGreeting(context),
                    metadata = mapOf("scenario" to "morning", "deviation" to "early_wake")
                )

            // --- Afternoon Greeting ---
            task.contains("afternoon") || ((task.isBlank() || task == "greeting") && time.contains("afternoon")) ->
                AIResponse(
                    action = "greeting",
                    speech = "Good afternoon! Hope your day is going smoothly. Need any help with your schedule or tasks?",
                    displayText = "☀️ Good afternoon! Hope your day is going smoothly. Need any help with your schedule or tasks?",
                    metadata = mapOf("scenario" to "afternoon")
                )

            // --- Commute / Traffic ---
            task.contains("commute") || task.contains("traffic") ||
            task.contains("route") || task.contains("drive") ->
                AIResponse(
                    action = "commute",
                    speech = buildCommuteResponse(context),
                    displayText = buildCommuteResponse(context),
                    metadata = mapOf("scenario" to "commute", "delay_minutes" to "12", "suggested_departure" to "08:15")
                )

            // --- Tribe Finder events ---
            task.contains("tribe") || task.contains("event") ||
            task.contains("community") || task.contains("club") ||
            task.contains("meetup") || task.contains("running") || task.contains(" run") || task.contains("run ") || task == "run" ->
                AIResponse(
                    action = "tribe_event",
                    speech = "There's a running club meetup near your office at 6 PM today. 14 people have already signed up. Want me to set a reminder?",
                    displayText = "🏃 There's a **running club** meetup near your office at 6 PM today. 14 people signed up!\n\nWant me to set a reminder?",
                    metadata = mapOf("scenario" to "tribe_event", "event_name" to "Evening Run Club", "attendees" to "14", "time" to "18:00")
                )

            // --- Lunch ---
            task.contains("lunch") || task.contains("food") ||
            task.contains("eat") || task.contains("hungry") ||
            ((task.isBlank() || task == "lunch") && time == "noon") ->
                AIResponse(
                    action = "reminder",
                    speech = "It's lunch time! You usually eat around now. Want me to suggest something nearby, or help you order from your usual place?",
                    displayText = "🍽️ It's lunch time! You usually eat around now.\n\nWant me to suggest something nearby, or help you order from your usual spot?",
                    metadata = mapOf("scenario" to "lunch", "routine_time" to "12:30")
                )

            // --- Evening check-in ---
            task.contains("evening") || task.contains("check-in") ||
            task.contains("leaving") || task.contains("late") ||
            ((task.isBlank() || task == "evening") && time == "evening") ->
                AIResponse(
                    action = "check_in",
                    speech = "Hey, I noticed you're still at the office. It's a bit later than your usual time. Everything okay? Don't forget you have that running club at 6.",
                    displayText = "👋 Hey, I noticed you're still at the office — a bit later than usual.\n\nEverything okay? Don't forget the running club at 6 PM!",
                    metadata = mapOf("scenario" to "evening_check_in", "deviation" to "late_office")
                )

            // --- Night / Medication ---
            task.contains("night") || task.contains("sleep") ||
            task.contains("medication") || task.contains("medicine") ||
            task.contains("bed") || ((task.isBlank() || task == "night") && time == "night") ->
                AIResponse(
                    action = "medication",
                    speech = "Time to wind down. Don't forget your evening medication. I've dimmed the suggestion tone. Sleep well!",
                    displayText = "🌙 Time to wind down. Don't forget your evening medication.\n\nI've dimmed the suggestion tone. Sleep well! 💤",
                    metadata = mapOf("scenario" to "night", "medication_due" to "true")
                )

            // --- Reminder set confirmation ---
            task.contains("reminder") || task.contains("set") ||
            task.contains("notify") ->
                AIResponse(
                    action = "reminder",
                    speech = "Done! I've set a reminder for 5:30 PM. I'll also notify you when it's time to leave based on live traffic.",
                    displayText = "✅ Done! Reminder set for **5:30 PM**.\n\nI'll also notify you when it's time to leave based on live traffic.",
                    metadata = mapOf("scenario" to "reminder_set", "reminder_time" to "17:30")
                )

            // --- Default: always try memory-grounded response first ---
            else -> buildDefaultResponse(context)
        }
    }

    /**
     * Builds a memory-grounded response for any query that doesn't match a specific scenario.
     * Extracts relevant facts from retrievedData and memoryContext before falling back to generic.
     */
    private fun buildDefaultResponse(context: ContextInput): AIResponse {
        val retrieved = context.retrievedData?.trim()
        val memList = context.memoryContext

        // If we have retrieved memory context, compose a relevant answer
        if (!retrieved.isNullOrBlank()) {
            val speech = composeGroundedSpeech(context.task, retrieved)
            return AIResponse(
                action = "general",
                speech = speech,
                displayText = "Based on what I know about you:\n\n$retrieved",
                metadata = mapOf("scenario" to "general_with_memory", "has_context" to "true")
            )
        }

        if (!memList.isNullOrEmpty()) {
            val joined = memList.joinToString("\n") { "• $it" }
            val speech = composeGroundedSpeech(context.task, memList.joinToString(". "))
            return AIResponse(
                action = "general",
                speech = speech,
                displayText = "Based on what I know about you:\n\n$joined",
                metadata = mapOf("scenario" to "general_with_memory", "has_context" to "true")
            )
        }

        // Truly no context available
        return AIResponse(
            action = "general",
            speech = "I'm here to help! I can check your schedule, suggest events, manage reminders, or just chat. What would you like?",
            displayText = "I'm here to help! 😊 I can:\n\n• Check your schedule\n• Suggest nearby events\n• Manage reminders\n• Just chat\n\nWhat would you like?",
            metadata = mapOf("scenario" to "general", "fallback" to "true")
        )
    }

    /**
     * Composes a natural grounded speech response from retrieved memory text.
     */
    private fun composeGroundedSpeech(task: String, retrieved: String): String {
        val taskLower = task.lowercase()
        val cleanRetrieved = retrieved.replace("\n", ". ").replace("- ", "").trim()
        return when {
            taskLower.contains("schedule") || taskLower.contains("plan") || taskLower.contains("today") ->
                "Here's what I have for your schedule: $cleanRetrieved"
            taskLower.contains("name") ->
                cleanRetrieved.take(120)
            taskLower.contains("work") || taskLower.contains("office") ->
                "From what I know: $cleanRetrieved"
            else ->
                cleanRetrieved.take(200)
        }
    }

    /**
     * Builds a personalised morning greeting using user's name from memory context if available.
     */
    private fun buildPersonalisedGreeting(context: ContextInput): String {
        val name = extractNameFromContext(context)
        val prefix = if (name != null) "Good morning, $name! ☀️" else "Good morning! ☀️"
        return "$prefix You're up a bit earlier than usual today. Want me to adjust your morning routine?"
    }

    /**
     * Builds a personalised commute response using departure time from memory if available.
     */
    private fun buildCommuteResponse(context: ContextInput): String {
        val departureTime = extractDepartureFromContext(context)
        return if (departureTime != null) {
            "🚗 Based on current traffic and your usual departure at $departureTime, I'd suggest leaving a bit earlier. Your usual route has about a 12-min delay."
        } else {
            "🚗 Based on current traffic, I'd suggest leaving by **8:15**. Your usual route has a 12-min delay near the highway exit."
        }
    }

    /**
     * Extracts the user's name from retrieved data or memory context.
     */
    private fun extractNameFromContext(context: ContextInput): String? {
        val allText = buildString {
            context.retrievedData?.let { append(it) }
            context.memoryContext?.forEach { append(" $it") }
        }.lowercase()

        val nameMatch = Regex("(?:user's name is|name is|call me)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE)
            .find(allText)
        return nameMatch?.groupValues?.getOrNull(1)?.replaceFirstChar { it.uppercase() }
    }

    /**
     * Extracts usual departure time from retrieved context.
     */
    private fun extractDepartureFromContext(context: ContextInput): String? {
        val allText = buildString {
            context.retrievedData?.let { append(it) }
            context.memoryContext?.forEach { append(" $it") }
        }
        val match = Regex("(?:leaves? (?:work|for work|office) around|departure.*?)([0-9]{1,2}:[0-9]{2}(?:\\s*[AP]M)?)", RegexOption.IGNORE_CASE)
            .find(allText)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun isTimeOrDateQuery(task: String): Boolean {
        val t = task.trim()
        if (t.contains("what time do i") || t.contains("when do i") || t.contains("my time")) return false
        return t.contains("what time") || t.contains("current time") || t.contains("what is the time") ||
                t.contains("what's the time") || t.contains("tell me the time") || t.contains("time now") ||
                t == "time" || t.contains("today's date") || t.contains("what date") ||
                t.contains("what is the date") || t.contains("what day is it") || t.contains("what day is today") ||
                t.contains("current date") || t == "date"
    }

    override fun isReady(): Boolean = true

    override fun engineName(): String = "MockEngine v2.0 (context-aware)"
}
