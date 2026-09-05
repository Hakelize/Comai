package com.comai.contextengine.contract

object ContractFixtures {

    val FIXTURE_MORNING_WAKE_DEVIATION = SharedContextContract(
        systemRole = "Comai Proactive Context Engine",
        userState = "awake since 08:45 (1h 15m), inactive 0.2h, current activity: STILL",
        contextSignals = ContextSignals(
            time = "10:00",
            location = "Home",
            routineDeviation = true
        ),
        retrievedData = null,
        task = "MORNING_ROUTINE_REMINDER",
        constraints = "Keep response empathetic, concise (max 2 sentences), and suggest an adjusted morning wake-up routine focus."
    )

    val FIXTURE_TRIBE_FINDER_COMMUTE = SharedContextContract(
        systemRole = "Comai Proactive Context Engine",
        userState = "awake since 09:00 (9h 30m), inactive 1.5h, current activity: WALKING",
        contextSignals = ContextSignals(
            time = "18:30",
            location = "Downtown",
            routineDeviation = true
        ),
        retrievedData = "[{name: 'Sunset Outdoor Calisthenics Circle', category: 'FITNESS', area: 'Home Area'}, {name: 'Weekend Board Games & Specialty Coffee', category: 'COMMUNITY', area: 'Downtown'}]",
        task = "TRIBE_FINDER_RECOMMENDATION",
        constraints = "Proactively suggest 1-2 local events matching non-workday free evening slot without being pushy."
    )

    val FIXTURE_LUNCH_REMINDER = SharedContextContract(
        systemRole = "Comai Proactive Context Engine",
        userState = "awake since 07:00 (6h 0m), inactive 4.0h, current activity: STILL",
        contextSignals = ContextSignals(
            time = "13:00",
            location = "Office",
            routineDeviation = false
        ),
        retrievedData = null,
        task = "LUNCH_CHECKIN",
        constraints = "Remind user to take a lunch break and log their meal, keep under 15 words."
    )

    val FIXTURE_EVENING_MEDICATION = SharedContextContract(
        systemRole = "Comai Proactive Context Engine",
        userState = "awake since 07:15 (14h 15m), inactive 3.0h, current activity: STILL",
        contextSignals = ContextSignals(
            time = "21:30",
            location = "Home",
            routineDeviation = false
        ),
        retrievedData = "Medication profile flag active: Evening Wellness Supplement",
        task = "NIGHT_MEDICATION_REMINDER",
        constraints = "Warm and gentle reminder to take evening medication before wind-down."
    )

    fun getAllFixtures(): List<Pair<String, SharedContextContract>> {
        return listOf(
            "Scenario 1: Morning Wake Deviation" to FIXTURE_MORNING_WAKE_DEVIATION,
            "Scenario 2: Non-Workday Tribe Finder Commute" to FIXTURE_TRIBE_FINDER_COMMUTE,
            "Scenario 3: Lunch Gap Check-in" to FIXTURE_LUNCH_REMINDER,
            "Scenario 4: Evening Medication Reminder" to FIXTURE_EVENING_MEDICATION
        )
    }
}
