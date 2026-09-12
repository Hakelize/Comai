package com.comai.scheduling

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleAlarmTest {

    @Test
    fun testDailyScheduleFutureToday() {
        // Current time: 8:00 AM Monday
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Plan: 10:00 AM Daily
        val trigger = ScheduleAlarmManager.calculateNextTriggerTime("10:00 AM", "Daily", now)
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

        // Must trigger today at 10:00 AM
        assertEquals(now.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(10, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun testDailySchedulePastTimeAdvancesToTomorrow() {
        // Current time: 11:30 AM Monday
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Plan: 10:00 AM Daily
        val trigger = ScheduleAlarmManager.calculateNextTriggerTime("10:00 AM", "Daily", now)
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

        // Must trigger tomorrow at 10:00 AM
        val expectedCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), triggerCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(10, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun testWeekdaysScheduleSkipsWeekend() {
        // Current time: Friday 7:00 PM
        val now = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Plan: 8:00 AM Weekdays
        val trigger = ScheduleAlarmManager.calculateNextTriggerTime("8:00 AM", "Weekdays", now)
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

        // Must jump to Monday, skipping Saturday and Sunday
        assertEquals(Calendar.MONDAY, triggerCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, triggerCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testMonWedFriSchedule() {
        // Current time: Monday 9:00 PM
        val now = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Plan: 7:30 PM Mon, Wed, Fri
        val trigger = ScheduleAlarmManager.calculateNextTriggerTime("7:30 PM", "Mon, Wed, Fri", now)
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }

        // Must advance to Wednesday
        assertEquals(Calendar.WEDNESDAY, triggerCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(19, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, triggerCal.get(Calendar.MINUTE))
    }
}
