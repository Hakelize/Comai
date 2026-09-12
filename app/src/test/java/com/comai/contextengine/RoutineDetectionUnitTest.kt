package com.comai.contextengine

import com.comai.contextengine.context.RoutineEngine
import com.comai.contextengine.providers.models.ActivityMovementState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutineDetectionUnitTest {

    private lateinit var routineEngine: RoutineEngine

    @Before
    fun setUp() {
        routineEngine = RoutineEngine(database = null)
    }

    @Test
    fun testNormalRoutine() {
        val result = routineEngine.evaluateInternal(
            currentTimeMinutes = 600, // 10:00 AM
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY
        )

        assertTrue(result.isNormalRoutine)
        assertFalse(result.routineDeviation)
        assertEquals(0, result.deviationMinutes)
    }

    @Test
    fun testEarlyBehavior() {
        val result = routineEngine.evaluateInternal(
            currentTimeMinutes = 390, // 06:30 AM (expected wake 07:00)
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Home",
            activityState = ActivityMovementState.WALKING
        )

        assertNotNull(result)
    }

    @Test
    fun testLateBehavior() {
        val result = routineEngine.evaluateInternal(
            currentTimeMinutes = 1125, // 18:45 PM (expected departure 17:30 PM)
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY
        )

        assertTrue("Staying late at office past expected departure must trigger routine deviation", result.routineDeviation)
        assertTrue(result.deviationMinutes >= 75)
        assertTrue(result.deviationReason.contains("office", ignoreCase = true))
    }

    @Test
    fun testRoutineDeviationOnNonWorkDay() {
        val result = routineEngine.evaluateInternal(
            currentTimeMinutes = 600, // 10:00 AM Saturday
            dayOfWeek = "SATURDAY",
            isWorkDay = false,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY
        )

        assertTrue("Visiting office on non-work day must trigger routine deviation", result.routineDeviation)
        assertTrue(result.deviationReason.contains("non-work day"))
    }

    @Test
    fun testNoHistoricalDataFallback() {
        val result = routineEngine.evaluateInternal(
            currentTimeMinutes = 540, // 09:00 AM
            dayOfWeek = "TUESDAY",
            isWorkDay = true,
            locationCategory = "Office",
            activityState = ActivityMovementState.STATIONARY,
            profile = null,
            program = null,
            historicalLogs = emptyList()
        )

        assertNotNull(result)
        assertTrue("Baseline fallback score when no logs are available", result.confidence >= 0.75f)
    }
}
