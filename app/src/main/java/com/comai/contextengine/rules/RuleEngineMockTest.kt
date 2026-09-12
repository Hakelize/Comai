package com.comai.contextengine.rules

/**
 * Test harness verifying RuleEngine functionality:
 * 1. Wake-time context → morning routine reminder
 * 2. Normal office arrival time → recognize work routine
 * 3. Leaving office later than normal → check-in trigger
 * 4. Lunch period → lunch reminder candidate
 * 5. Night/wind-down period → wind-down candidate
 * 6. Routine deviation → possible proactive event
 * 7. Duplicate event prevention & cooldown enforcement
 */
object RuleEngineMockTest {

    fun runAllTests(): List<Pair<String, Boolean>> {
        val results = mutableListOf<Pair<String, Boolean>>()
        val engine = RuleEngine()
        val baseTimeMs = 1_000_000_000L

        // Test 1: Wake-time context → morning routine reminder
        engine.resetCooldowns()
        val wakeInput = RuleEvaluationInput(
            currentTime = "08:45",
            currentTimeMs = baseTimeMs,
            wakeTime = "08:45",
            baselineWakeTime = "07:00", // 105 mins deviation
            currentLocation = "Home"
        )
        val wakeResult = engine.evaluate(wakeInput)
        val t1 = wakeResult?.proactiveEvent?.eventType == "MORNING_ROUTINE_REMINDER"
        results.add(Pair("Scenario 1: Wake-time context → morning routine reminder", t1))

        // Test 2: Duplicate prevention via Cooldown (same wake input 5s later)
        val duplicateResult = engine.evaluate(wakeInput.copy(currentTimeMs = baseTimeMs + 5_000L))
        val t2 = duplicateResult == null // Must be suppressed by cooldown!
        results.add(Pair("Scenario 2: Duplicate event prevention via Cooldown (suppressed)", t2))

        // Test 3: Normal office arrival time → recognize work routine
        engine.resetCooldowns()
        val arrivalInput = RuleEvaluationInput(
            currentTime = "08:30",
            currentTimeMs = baseTimeMs,
            isWorkDay = true,
            currentLocation = "Office"
        )
        val arrivalResult = engine.evaluate(arrivalInput)
        val t3 = arrivalResult?.proactiveEvent?.eventType == "WORK_ROUTINE_RECOGNITION"
        results.add(Pair("Scenario 3: Normal office arrival → work routine recognition", t3))

        // Test 4: Leaving office later than normal → check-in trigger
        engine.resetCooldowns()
        val overtimeInput = RuleEvaluationInput(
            currentTime = "19:30",
            currentTimeMs = baseTimeMs,
            isWorkDay = true,
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30" // 120 mins overtime
        )
        val overtimeResult = engine.evaluate(overtimeInput)
        val t4 = overtimeResult?.proactiveEvent?.eventType == "OVERTIME_CHECKIN"
        results.add(Pair("Scenario 4: Leaving office later than normal → overtime check-in", t4))

        // Test 5: Lunch period → lunch reminder candidate
        engine.resetCooldowns()
        val lunchInput = RuleEvaluationInput(
            currentTime = "13:00",
            currentTimeMs = baseTimeMs,
            isWorkDay = true,
            currentLocation = "Office",
            loggedLunch = false
        )
        val lunchResult = engine.evaluate(lunchInput)
        val t5 = lunchResult?.proactiveEvent?.eventType == "LUNCH_REMINDER"
        results.add(Pair("Scenario 5: Lunch period → lunch reminder candidate", t5))

        // Test 6: Night/wind-down period → wind-down candidate
        engine.resetCooldowns()
        val windDownInput = RuleEvaluationInput(
            currentTime = "21:30",
            currentTimeMs = baseTimeMs,
            medicationFlag = true,
            currentLocation = "Home"
        )
        val windDownResult = engine.evaluate(windDownInput)
        val t6 = windDownResult?.proactiveEvent?.eventType == "WIND_DOWN_CANDIDATE"
        results.add(Pair("Scenario 6: Night/wind-down period → wind-down candidate", t6))

        // Test 7: Routine deviation → possible proactive event
        engine.resetCooldowns()
        val deviationInput = RuleEvaluationInput(
            currentTime = "15:00",
            currentTimeMs = baseTimeMs,
            routineDeviation = true,
            currentLocation = "Unknown"
        )
        val deviationResult = engine.evaluate(deviationInput)
        val t7 = deviationResult?.proactiveEvent?.eventType == "ROUTINE_DEVIATION_ALERT"
        results.add(Pair("Scenario 7: Routine deviation → routine deviation alert", t7))

        return results
    }
}
