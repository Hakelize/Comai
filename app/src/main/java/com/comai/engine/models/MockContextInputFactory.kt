package com.comai.engine.models

/**
 * Mock factory creating realistic ContextInput instances for Person 2's AIEngine testing.
 */
object MockContextInputFactory {

    fun createMockOvertimeInput(): ContextInput = ContextInput(
        systemRole = "Comai Proactive Context Engine",
        userState = "BroadContext: OFFICE_LATE_DEPARTURE | Place: Office | Activity: STATIONARY | Audio: SPEAKER",
        contextSignals = ContextSignals(time = "19:30", location = "Office", routineDeviation = true),
        retrievedData = null,
        task = "OVERTIME_CHECKIN",
        constraints = "Keep response empathetic, concise (max 2 sentences)."
    )

    fun createMockMorningInput(): ContextInput = ContextInput(
        systemRole = "Comai Proactive Context Engine",
        userState = "BroadContext: HOME_MORNING | Place: Home | Activity: STATIONARY | Audio: SPEAKER",
        contextSignals = ContextSignals(time = "07:15", location = "Home", routineDeviation = false),
        retrievedData = null,
        task = "MORNING_ROUTINE_REMINDER",
        constraints = "Keep response empathetic, concise (max 2 sentences)."
    )

    fun createMockLunchInput(): ContextInput = ContextInput(
        systemRole = "Comai Proactive Context Engine",
        userState = "BroadContext: LUNCH_BREAK | Place: Office | Activity: STATIONARY | Audio: SPEAKER",
        contextSignals = ContextSignals(time = "13:00", location = "Office", routineDeviation = false),
        retrievedData = null,
        task = "LUNCH_CHECKIN",
        constraints = "Prompt gentle lunch break reminder."
    )

    fun createMockWindDownInput(): ContextInput = ContextInput(
        systemRole = "Comai Proactive Context Engine",
        userState = "BroadContext: NIGHT_SLEEP | Place: Home | Activity: STATIONARY | Audio: SPEAKER",
        contextSignals = ContextSignals(time = "21:30", location = "Home", routineDeviation = false),
        retrievedData = null,
        task = "NIGHT_MEDICATION_REMINDER",
        constraints = "Remind user of wind-down routine and medication."
    )

    fun createMockDeviationInput(): ContextInput = ContextInput(
        systemRole = "Comai Proactive Context Engine",
        userState = "BroadContext: GENERAL_DAILY | Place: Unknown | Activity: IN_VEHICLE | Audio: SPEAKER",
        contextSignals = ContextSignals(time = "15:00", location = "Unknown", routineDeviation = true),
        retrievedData = null,
        task = "ROUTINE_DEVIATION_ALERT",
        constraints = "Inquire gently about schedule adjustment."
    )
}
