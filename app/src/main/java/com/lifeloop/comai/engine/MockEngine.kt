package com.lifeloop.comai.engine

import com.lifeloop.comai.engine.models.AIResponse
import com.lifeloop.comai.engine.models.ContextInput
import kotlinx.coroutines.delay

/**
 * Mock implementation of [AIEngine] that returns hardcoded JSON responses
 * for each MVP demo scenario. Used for UI development and testing
 * without draining battery on real NPU inference.
 *
 * Scenario coverage matches the MVP Demo Story:
 * 1. Morning greeting / routine adjustment
 * 2. Commute / traffic check
 * 3. Tribe Finder event suggestion
 * 4. Lunch reminder
 * 5. Evening check-in
 * 6. Night / medication reminder
 * 7. Memory recall
 */
class MockEngine : AIEngine {

    override suspend fun process(context: ContextInput): AIResponse {
        // Simulate realistic inference latency
        delay((400L..900L).random())

        val task = context.task.lowercase()
        val time = context.contextSignals.time.lowercase()

        return when {
            // --- Morning ---
            task.contains("morning") || task.contains("wake") ||
            task.contains("greet") || time == "morning" ->
                AIResponse(
                    action = "greeting",
                    speech = "Good morning! You're up a bit earlier than usual today. Want me to adjust your morning routine?",
                    displayText = "Good morning! ☀️ You're up a bit earlier than usual today. Want me to adjust your morning routine?",
                    metadata = mapOf("scenario" to "morning", "deviation" to "early_wake")
                )

            // --- Commute / Traffic ---
            task.contains("commute") || task.contains("traffic") ||
            task.contains("route") || task.contains("drive") ->
                AIResponse(
                    action = "commute",
                    speech = "Based on current traffic, I'd suggest leaving by 8:15. Your usual route has a 12 minute delay near the highway exit.",
                    displayText = "🚗 Based on current traffic, I'd suggest leaving by **8:15**. Your usual route has a 12-min delay near the highway exit.",
                    metadata = mapOf("scenario" to "commute", "delay_minutes" to "12", "suggested_departure" to "08:15")
                )

            // --- Tribe Finder events ---
            task.contains("tribe") || task.contains("event") ||
            task.contains("community") || task.contains("club") ||
            task.contains("meetup") || task.contains("run") ->
                AIResponse(
                    action = "tribe_event",
                    speech = "There's a running club meetup near your office at 6 PM today. 14 people have already signed up. Want me to set a reminder?",
                    displayText = "🏃 There's a **running club** meetup near your office at 6 PM today. 14 people signed up!\n\nWant me to set a reminder?",
                    metadata = mapOf("scenario" to "tribe_event", "event_name" to "Evening Run Club", "attendees" to "14", "time" to "18:00")
                )

            // --- Lunch ---
            task.contains("lunch") || task.contains("food") ||
            task.contains("eat") || task.contains("hungry") ||
            time == "noon" ->
                AIResponse(
                    action = "reminder",
                    speech = "It's lunch time! You usually eat around now. Want me to suggest something nearby, or help you order from your usual place?",
                    displayText = "🍽️ It's lunch time! You usually eat around now.\n\nWant me to suggest something nearby, or help you order from your usual spot?",
                    metadata = mapOf("scenario" to "lunch", "routine_time" to "12:30")
                )

            // --- Evening check-in ---
            task.contains("evening") || task.contains("check-in") ||
            task.contains("leaving") || task.contains("late") ||
            time == "evening" ->
                AIResponse(
                    action = "check_in",
                    speech = "Hey, I noticed you're still at the office. It's a bit later than your usual time. Everything okay? Don't forget you have that running club at 6.",
                    displayText = "👋 Hey, I noticed you're still at the office — a bit later than usual.\n\nEverything okay? Don't forget the running club at 6 PM!",
                    metadata = mapOf("scenario" to "evening_check_in", "deviation" to "late_office")
                )

            // --- Night / Medication ---
            task.contains("night") || task.contains("sleep") ||
            task.contains("medication") || task.contains("medicine") ||
            task.contains("bed") || time == "night" ->
                AIResponse(
                    action = "medication",
                    speech = "Time to wind down. Don't forget your evening medication. I've dimmed the suggestion tone. Sleep well!",
                    displayText = "🌙 Time to wind down. Don't forget your evening medication.\n\nI've dimmed the suggestion tone. Sleep well! 💤",
                    metadata = mapOf("scenario" to "night", "medication_due" to "true")
                )

            // --- Memory recall ---
            task.contains("memory") || task.contains("remember") ||
            task.contains("know about me") || task.contains("what do you") ->
                AIResponse(
                    action = "memory_recall",
                    speech = "Here's what I remember: you wake up around 7 AM, prefer coffee over tea, commute to MG Road office, and joined a running club last week. You can delete any of these.",
                    displayText = "🧠 Here's what I remember about you:\n\n• Wake time: ~7:00 AM\n• Preference: Coffee over tea\n• Commute: MG Road office\n• Recent: Joined running club last week\n\n🔒 You can delete any of these anytime.",
                    metadata = mapOf("scenario" to "memory_recall", "items" to "4")
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

            // --- Default / general ---
            else ->
                AIResponse(
                    action = "general",
                    speech = "I'm here to help! I can check your schedule, suggest events, manage reminders, or just chat. What would you like?",
                    displayText = "I'm here to help! 😊 I can:\n\n• Check your schedule\n• Suggest nearby events\n• Manage reminders\n• Just chat\n\nWhat would you like?",
                    metadata = mapOf("scenario" to "general", "fallback" to "true")
                )
        }
    }

    override fun isReady(): Boolean = true

    override fun engineName(): String = "MockEngine v1.0"
}
