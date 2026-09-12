package com.comai.util

import org.junit.Assert.*
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testTwelveHourFormatting() {
        val t1 = Time12(hour = 7, minute = 0, isAm = true)
        assertEquals("7:00 AM", t1.format())

        val t2 = Time12(hour = 12, minute = 0, isAm = false)
        assertEquals("12:00 PM", t2.format())

        val t3 = Time12(hour = 6, minute = 0, isAm = false)
        assertEquals("6:00 PM", t3.format())

        val t4 = Time12(hour = 11, minute = 30, isAm = false)
        assertEquals("11:30 PM", t4.format())

        val t5 = Time12(hour = 12, minute = 0, isAm = true)
        assertEquals("12:00 AM", t5.format())
    }

    @Test
    fun testParseTwelveHourStrings() {
        val p1 = TimeUtils.parseTime("7:00 AM")
        assertEquals(7, p1.hour)
        assertEquals(0, p1.minute)
        assertTrue(p1.isAm)

        val p2 = TimeUtils.parseTime("12:00 PM")
        assertEquals(12, p2.hour)
        assertEquals(0, p2.minute)
        assertFalse(p2.isAm)

        val p3 = TimeUtils.parseTime("6:00 PM")
        assertEquals(6, p3.hour)
        assertEquals(0, p3.minute)
        assertFalse(p3.isAm)

        val p4 = TimeUtils.parseTime("11:30 PM")
        assertEquals(11, p4.hour)
        assertEquals(30, p4.minute)
        assertFalse(p4.isAm)
    }

    @Test
    fun testParseLegacyTwentyFourHourStrings() {
        // "07:00" -> 7:00 AM
        val p1 = TimeUtils.parseTime("07:00")
        assertEquals("7:00 AM", p1.format())

        // "12:00" -> 12:00 PM
        val p2 = TimeUtils.parseTime("12:00")
        assertEquals("12:00 PM", p2.format())

        // "18:00" -> 6:00 PM
        val p3 = TimeUtils.parseTime("18:00")
        assertEquals("6:00 PM", p3.format())

        // "23:30" -> 11:30 PM
        val p4 = TimeUtils.parseTime("23:30")
        assertEquals("11:30 PM", p4.format())

        // "00:00" -> 12:00 AM
        val p5 = TimeUtils.parseTime("00:00")
        assertEquals("12:00 AM", p5.format())
    }

    @Test
    fun testNormalizeTo12Hour() {
        assertEquals("7:00 AM", TimeUtils.normalizeTo12Hour("7:00 AM"))
        assertEquals("12:00 PM", TimeUtils.normalizeTo12Hour("12:00 PM"))
        assertEquals("6:00 PM", TimeUtils.normalizeTo12Hour("6:00 PM"))
        assertEquals("11:30 PM", TimeUtils.normalizeTo12Hour("11:30 PM"))
        assertEquals("6:00 PM", TimeUtils.normalizeTo12Hour("18:00"))
        assertEquals("8:30 AM", TimeUtils.normalizeTo12Hour("08:30"))
        assertEquals("8:00 AM", TimeUtils.normalizeTo12Hour("", "8:00 AM"))
    }

    @Test
    fun testChronologicalOrdering() {
        val tMorning = TimeUtils.parseTime("7:00 AM")
        val tNoon = TimeUtils.parseTime("12:00 PM")
        val tEvening = TimeUtils.parseTime("6:00 PM")
        val tNight = TimeUtils.parseTime("11:30 PM")

        assertTrue(tMorning.toMinutesOfDay() < tNoon.toMinutesOfDay())
        assertTrue(tNoon.toMinutesOfDay() < tEvening.toMinutesOfDay())
        assertTrue(tEvening.toMinutesOfDay() < tNight.toMinutesOfDay())
    }
}
