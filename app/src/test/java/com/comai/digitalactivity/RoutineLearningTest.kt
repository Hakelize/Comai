package com.comai.digitalactivity

import com.comai.digitalactivity.data.DailyActivitySummaryEntity
import com.comai.digitalactivity.intelligence.RoutineLearningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RoutineLearningTest {

    private fun createSummary(date: String, hourStart: Int, minStart: Int, hourEnd: Int, minEnd: Int): DailyActivitySummaryEntity {
        val calStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourStart)
            set(Calendar.MINUTE, minStart)
            set(Calendar.SECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourEnd)
            set(Calendar.MINUTE, minEnd)
            set(Calendar.SECOND, 0)
        }
        return DailyActivitySummaryEntity(
            date = date,
            totalScreenTimeMinutes = 240,
            firstActiveTimeMs = calStart.timeInMillis,
            lastActiveTimeMs = calEnd.timeInMillis,
            topPackageName = "com.google.android.youtube",
            topCategory = "ENTERTAINMENT"
        )
    }

    @Test
    fun testInsufficientDataWhenFewerThanThreeObservations() {
        val summaries = listOf(
            createSummary("2026-09-10", 7, 10, 23, 30),
            createSummary("2026-09-11", 7, 12, 23, 25)
        )

        val result = RoutineLearningEngine.computeLearnedRoutine(summaries)
        assertFalse(result.isSufficientData)
        assertEquals(2, result.observationCount)
        assertTrue(result.confidence < 0.5f)
    }

    @Test
    fun testSufficientDataAveragesRoutineTimes() {
        val summaries = listOf(
            createSummary("2026-09-08", 7, 10, 23, 30),
            createSummary("2026-09-09", 7, 6, 23, 28),
            createSummary("2026-09-10", 7, 14, 23, 32),
            createSummary("2026-09-11", 7, 8, 23, 30)
        )

        val result = RoutineLearningEngine.computeLearnedRoutine(summaries)
        assertTrue(result.isSufficientData)
        assertEquals(4, result.observationCount)
        assertTrue(result.confidence >= 0.7f)
        // Average morning start: (10 + 6 + 14 + 8) / 4 = 9.5 -> 7:09 AM or 7:10 AM
        assertTrue(result.typicalMorningStartFormatted.contains("7:09") || result.typicalMorningStartFormatted.contains("7:10"))
        // Average night rest: (30 + 28 + 32 + 30) / 4 = 30 -> 11:30 PM
        assertTrue(result.typicalNightRestFormatted.contains("11:30"))
    }
}
