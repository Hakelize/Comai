package com.comai.feature

import com.comai.data.menstrualcycle.MenstrualCycleCalculator
import com.comai.data.menstrualcycle.MenstrualCycleData
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.screens.onboarding.UserOnboardingProfile
import com.comai.voice.ConversationGreetingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NewFeaturesTest {

    @Test
    fun testUserOnboardingProfileDefaultAndCustomGender() {
        val defaultProfile = UserOnboardingProfile()
        assertEquals("Prefer not to say", defaultProfile.gender)

        val femaleProfile = defaultProfile.copy(gender = "Female")
        assertEquals("Female", femaleProfile.gender)

        val maleProfile = defaultProfile.copy(gender = "Male")
        assertEquals("Male", maleProfile.gender)
    }

    @Test
    fun testMenstrualCycleCalculatorUnconfiguredReturnsNull() {
        val unconfigured = MenstrualCycleData(isConfigured = false)
        assertNull(MenstrualCycleCalculator.calculateEstimate(unconfigured))

        val invalidDate = MenstrualCycleData(isConfigured = true, lastPeriodStartDateMs = 0L)
        assertNull(MenstrualCycleCalculator.calculateEstimate(invalidDate))
    }

    @Test
    fun testMenstrualCycleCalculatorProjectsFutureDate() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Last period started on August 10, 2026 with a 28-day cycle
        val lastStartCal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val data = MenstrualCycleData(
            lastPeriodStartDateMs = lastStartCal.timeInMillis,
            typicalCycleLengthDays = 28,
            periodDurationDays = 5,
            isRemindersEnabled = true,
            isConfigured = true
        )

        val estimate = MenstrualCycleCalculator.calculateEstimate(data, nowMs = nowMs)
        assertNotNull(estimate)
        assertTrue(estimate!!.nextEstimatedStartDateMs >= nowMs)
        assertEquals("Estimated based on your saved cycle information.", estimate.disclaimer)
        assertTrue(estimate.daysRemaining >= 0)
    }

    @Test
    fun testPersonalPlanReminderTypeDefaultAndExplicit() {
        val defaultPlan = PersonalPlan(
            title = "Drink water",
            time = "10:00 AM"
        )
        assertEquals(ReminderType.NOTIFICATION, defaultPlan.reminderType)

        val alarmPlan = defaultPlan.copy(reminderType = ReminderType.ALARM)
        assertEquals(ReminderType.ALARM, alarmPlan.reminderType)
    }

    @Test
    fun testConversationGreetingManagerWithDynamicName() {
        val calMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
        }
        val greetingMorning = ConversationGreetingManager.generateGreeting("Sita", isReturning = false, calendar = calMorning)
        assertTrue(greetingMorning.contains("Sita"))
        assertFalse(greetingMorning.contains("Rakesh"))
        assertFalse(greetingMorning.contains("Arun"))

        val calEvening = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
        }
        val greetingEvening = ConversationGreetingManager.generateGreeting("Lakshmi", isReturning = false, calendar = calEvening)
        assertTrue(greetingEvening.contains("Lakshmi"))

        val greetingReturning = ConversationGreetingManager.generateGreeting("Deepa", isReturning = true)
        assertTrue(greetingReturning.contains("Deepa"))
        assertTrue(greetingReturning.startsWith("Welcome back") || greetingReturning.startsWith("Hi Deepa"))
    }

    @Test
    fun testConversationGreetingManagerFallbackWhenBlankName() {
        val calMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
        }
        val greetingMorning = ConversationGreetingManager.generateGreeting("", isReturning = false, calendar = calMorning)
        assertTrue(greetingMorning.startsWith("Good morning") || greetingMorning.startsWith("Hello"))
        assertFalse(greetingMorning.contains("Rakesh"))
        assertFalse(greetingMorning.contains("Arun"))
        assertFalse(greetingMorning.contains("Priya"))

        val greetingReturning = ConversationGreetingManager.generateGreeting("   ", isReturning = true)
        assertTrue(greetingReturning.startsWith("Welcome back") || greetingReturning.startsWith("Hi there"))
        assertFalse(greetingReturning.contains("Rakesh"))
        assertFalse(greetingReturning.contains("Arun"))
    }
}
