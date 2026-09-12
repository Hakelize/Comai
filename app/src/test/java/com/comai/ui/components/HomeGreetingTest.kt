package com.comai.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class HomeGreetingTest {

    private fun calendarAt(hourOfDay: Int, minute: Int = 0): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
    }

    @Test
    fun testTimeOfDayRanges() {
        // Morning: 5..11
        assertEquals("Good morning, Arun", HomeGreetingUtils.computeGreeting("Arun", calendarAt(5)))
        assertEquals("Good morning, Arun", HomeGreetingUtils.computeGreeting("Arun", calendarAt(9)))
        assertEquals("Good morning, Arun", HomeGreetingUtils.computeGreeting("Arun", calendarAt(11, 59)))

        // Afternoon: 12..16
        assertEquals("Good afternoon, Priya", HomeGreetingUtils.computeGreeting("Priya", calendarAt(12)))
        assertEquals("Good afternoon, Priya", HomeGreetingUtils.computeGreeting("Priya", calendarAt(14)))
        assertEquals("Good afternoon, Priya", HomeGreetingUtils.computeGreeting("Priya", calendarAt(16, 59)))

        // Evening: 17..21
        assertEquals("Good evening, Karthik", HomeGreetingUtils.computeGreeting("Karthik", calendarAt(17)))
        assertEquals("Good evening, Karthik", HomeGreetingUtils.computeGreeting("Karthik", calendarAt(19)))
        assertEquals("Good evening, Karthik", HomeGreetingUtils.computeGreeting("Karthik", calendarAt(21, 59)))

        // Night: 22..4
        assertEquals("Good night, Rahul", HomeGreetingUtils.computeGreeting("Rahul", calendarAt(22)))
        assertEquals("Good night, Rahul", HomeGreetingUtils.computeGreeting("Rahul", calendarAt(0)))
        assertEquals("Good night, Rahul", HomeGreetingUtils.computeGreeting("Rahul", calendarAt(3)))
        assertEquals("Good night, Rahul", HomeGreetingUtils.computeGreeting("Rahul", calendarAt(4, 59)))
    }

    @Test
    fun testBlankNameFallbackHasNoHardcodedName() {
        // If name is blank, must gracefully fall back to time greeting alone, NEVER "Rakesh", "User", "null", or "there"
        val morning = HomeGreetingUtils.computeGreeting("", calendarAt(8))
        assertEquals("Good morning", morning)
        assertFalse(morning.contains("Rakesh"))
        assertFalse(morning.contains("null"))
        assertFalse(morning.contains("User"))
        assertFalse(morning.contains("there"))

        val afternoon = HomeGreetingUtils.computeGreeting("   ", calendarAt(14))
        assertEquals("Good afternoon", afternoon)

        val evening = HomeGreetingUtils.computeGreeting("", calendarAt(18))
        assertEquals("Good evening", evening)

        val night = HomeGreetingUtils.computeGreeting("", calendarAt(23))
        assertEquals("Good night", night)
    }

    @Test
    fun testDynamicNamesEnteredByUser() {
        assertEquals("Good morning, Arun", HomeGreetingUtils.computeGreeting("Arun", calendarAt(9)))
        assertEquals("Good morning, Priya", HomeGreetingUtils.computeGreeting("Priya", calendarAt(9)))
        assertEquals("Good morning, Karthik", HomeGreetingUtils.computeGreeting("Karthik", calendarAt(9)))
    }
}
