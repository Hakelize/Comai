package com.comai.contextengine.db

import com.comai.contextengine.database.ContextLogEntity
import com.comai.contextengine.database.ProactiveEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Development & testing utility populating realistic mock data into Room database.
 * Supports today-vs-history comparisons for Person 3's Dashboard.
 */
object MockDatabaseSeeder {

    suspend fun seedMockData(database: ComaiDatabase) = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // 1. Seed User Profile
        val profile = UserProfile(
            id = 1,
            baselineWakeTime = "07:00",
            baselineSleepTime = "23:00",
            medicationFlag = true,
            workDaysPattern = "MON,TUE,WED,THU,FRI",
            historicalOfficeDepartureTime = "17:30",
            homeLocationTag = "Home Area",
            workLocationTag = "Tech Hub Office"
        )
        database.userProfileDao().insertOrUpdateProfile(profile)

        // 2. Seed Weekly Programs
        val defaultPrograms = listOf(
            Program(dayOfWeek = "MONDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30"),
            Program(dayOfWeek = "TUESDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30"),
            Program(dayOfWeek = "WEDNESDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30"),
            Program(dayOfWeek = "THURSDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30"),
            Program(dayOfWeek = "FRIDAY", isWorkDay = true, expectedWakeTime = "07:00", expectedDepartureTime = "17:30"),
            Program(dayOfWeek = "SATURDAY", isWorkDay = false, expectedWakeTime = "08:30", expectedDepartureTime = "18:00"),
            Program(dayOfWeek = "SUNDAY", isWorkDay = false, expectedWakeTime = "08:30", expectedDepartureTime = "18:00")
        )
        database.programDao().insertPrograms(defaultPrograms)

        // 3. Seed Historical Daily Logs (7 days)
        val dailyLogs = listOf(
            DailyLog(timestampMs = nowMs - (1 * oneDayMs), eventType = "WAKE_UP", details = "Wake at 07:05", location = "Home"),
            DailyLog(timestampMs = nowMs - (2 * oneDayMs), eventType = "WAKE_UP", details = "Wake at 07:00", location = "Home"),
            DailyLog(timestampMs = nowMs - (1 * oneDayMs), eventType = "OFFICE_ARRIVAL", details = "Arrival 08:30", location = "Office"),
            DailyLog(timestampMs = nowMs - (2 * oneDayMs), eventType = "OFFICE_ARRIVAL", details = "Arrival 08:35", location = "Office"),
            DailyLog(timestampMs = nowMs - (1 * oneDayMs), eventType = "OFFICE_DEPARTURE", details = "Departure 17:30", location = "Office"),
            DailyLog(timestampMs = nowMs - (2 * oneDayMs), eventType = "OFFICE_DEPARTURE", details = "Departure 19:30", location = "Office", routineDeviation = true),
            DailyLog(timestampMs = nowMs - (1 * oneDayMs), eventType = "SLEEP", details = "Sleep at 23:00", location = "Home")
        )
        for (log in dailyLogs) {
            database.dailyLogDao().insertLog(log)
        }

        // 4. Seed Context Diagnostic Logs
        val contextLogs = listOf(
            ContextLogEntity(timestampMs = nowMs - 3600000L, eventType = "CONTEXT_EVALUATION", task = "WORK_ROUTINE_RECOGNITION", confidence = 0.95, signalsSummary = "Office, WiFi, Still", isEscalated = false),
            ContextLogEntity(timestampMs = nowMs - 7200000L, eventType = "CONTEXT_ESCALATION", task = "OVERTIME_CHECKIN", confidence = 0.88, signalsSummary = "Office late, stationary, battery 45%", isEscalated = true)
        )
        for (cLog in contextLogs) {
            database.contextLogDao().insertLog(cLog)
        }

        // 5. Seed Proactive Events
        val proactiveEvents = listOf(
            ProactiveEventEntity(timestampMs = nowMs - 3600000L, eventId = "evt_overtime_1", eventType = "OVERTIME_CHECKIN", priority = "HIGH", targetTask = "OVERTIME_CHECKIN", targetConstraints = "Concise empathy", contextSummary = "Office late", isAcknowledged = false)
        )
        for (pEvent in proactiveEvents) {
            database.proactiveEventDao().insertEvent(pEvent)
        }
    }
}
