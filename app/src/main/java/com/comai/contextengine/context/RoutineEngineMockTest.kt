package com.comai.contextengine.context

import com.comai.contextengine.db.DailyLog
import com.comai.contextengine.db.Program
import com.comai.contextengine.db.UserProfile
import com.comai.contextengine.models.RoutineEvaluation
import com.comai.contextengine.providers.models.ActivityMovementState

/**
 * Utility for running mock routine detection tests against RoutineEngine with simulated historical data.
 */
object RoutineEngineMockTest {

    fun createMockHistoricalLogs(): List<DailyLog> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return listOf(
            DailyLog(timestampMs = now - (1 * oneDayMs), eventType = "WAKE_UP", details = "Woke up at 07:05 AM", location = "Home"),
            DailyLog(timestampMs = now - (2 * oneDayMs), eventType = "WAKE_UP", details = "Woke up at 07:00 AM", location = "Home"),
            DailyLog(timestampMs = now - (3 * oneDayMs), eventType = "WAKE_UP", details = "Woke up at 07:10 AM", location = "Home"),
            DailyLog(timestampMs = now - (1 * oneDayMs), eventType = "OFFICE_ARRIVAL", details = "Arrived at 08:30 AM", location = "Office"),
            DailyLog(timestampMs = now - (2 * oneDayMs), eventType = "OFFICE_ARRIVAL", details = "Arrived at 08:35 AM", location = "Office"),
            DailyLog(timestampMs = now - (1 * oneDayMs), eventType = "OFFICE_DEPARTURE", details = "Left office at 17:30 PM", location = "Office"),
            DailyLog(timestampMs = now - (2 * oneDayMs), eventType = "OFFICE_DEPARTURE", details = "Left office at 17:35 PM", location = "Office"),
            DailyLog(timestampMs = now - (1 * oneDayMs), eventType = "SLEEP", details = "Sleep at 23:00 PM", location = "Home"),
            DailyLog(timestampMs = now - (2 * oneDayMs), eventType = "SLEEP", details = "Sleep at 23:15 PM", location = "Home")
        )
    }

    fun runTests(): List<Pair<String, RoutineEvaluation>> {
        val engine = RoutineEngine()
        val profile = UserProfile(baselineWakeTime = "07:00", baselineSleepTime = "23:00")
        val program = Program(dayOfWeek = "MONDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30")
        val logs = createMockHistoricalLogs()

        val results = mutableListOf<Pair<String, RoutineEvaluation>>()

        // Test 1: Normal Office Day (10:00 AM at Office)
        val test1 = engine.evaluateInternal(
            currentTimeMinutes = 600, // 10:00 AM
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY,
            profile = profile,
            program = program,
            historicalLogs = logs
        )
        results.add(Pair("Test 1: Normal Office Day (10:00 AM at Office)", test1))

        // Test 2: Overtime Office Departure (19:30 PM at Office)
        val test2 = engine.evaluateInternal(
            currentTimeMinutes = 1170, // 19:30 PM
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY,
            profile = profile,
            program = program,
            historicalLogs = logs
        )
        results.add(Pair("Test 2: Overtime Office Departure (19:30 PM at Office)", test2))

        // Test 3: Delayed Commute (09:45 AM still at Home)
        val test3 = engine.evaluateInternal(
            currentTimeMinutes = 585, // 09:45 AM
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Home",
            activityState = ActivityMovementState.STATIONARY,
            profile = profile,
            program = program,
            historicalLogs = logs
        )
        results.add(Pair("Test 3: Delayed Commute (09:45 AM still at Home)", test3))

        // Test 4: Sunday Office Visit (14:00 PM on Sunday)
        val test4 = engine.evaluateInternal(
            currentTimeMinutes = 840, // 14:00 PM
            dayOfWeek = "SUNDAY",
            isWorkDay = false,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY,
            profile = profile,
            program = Program(dayOfWeek = "SUNDAY", isWorkDay = false),
            historicalLogs = logs
        )
        results.add(Pair("Test 4: Sunday Office Visit (14:00 PM on Sunday)", test4))

        return results
    }
}
