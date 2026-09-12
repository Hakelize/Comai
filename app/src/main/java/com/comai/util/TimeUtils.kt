package com.comai.util

import java.util.Locale

/**
 * Clean 12-hour representation of time with explicit AM/PM flag.
 * Eliminates railway/24-hour ambiguity and prevents AM/PM conversion bugs.
 */
data class Time12(
    val hour: Int,       // 1..12
    val minute: Int,     // 0..59
    val isAm: Boolean
) {
    /**
     * Formats to clean consumer 12-hour standard: e.g. "7:00 AM", "8:30 AM", "12:00 PM", "6:00 PM".
     */
    fun format(): String {
        val amPmStr = if (isAm) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", hour, minute, amPmStr)
    }

    /**
     * Converts to minutes from midnight (0..1439) for easy chronological sorting.
     */
    fun toMinutesOfDay(): Int {
        val h24 = when {
            isAm && hour == 12 -> 0
            isAm -> hour
            !isAm && hour == 12 -> 12
            else -> hour + 12
        }
        return h24 * 60 + minute
    }
}

object TimeUtils {

    /**
     * Parses standard 12-hour ("7:00 AM", "11:30 PM") or legacy 24-hour ("07:00", "18:00") format.
     */
    fun parseTime(timeStr: String, fallback: Time12 = Time12(8, 0, true)): Time12 {
        val trimmed = timeStr.trim()
        if (trimmed.isBlank()) return fallback

        // 12-hour format regex: "7:00 AM", "07:00 AM", "11:30 PM", "12:00 PM"
        val regex12 = Regex("^(0?[1-9]|1[0-2]):([0-5][0-9])\\s*([AaPp][Mm])$")
        val match12 = regex12.find(trimmed)
        if (match12 != null) {
            val h = match12.groupValues[1].toInt()
            val m = match12.groupValues[2].toInt()
            val isAm = match12.groupValues[3].uppercase(Locale.US) == "AM"
            return Time12(h, m, isAm)
        }

        // 24-hour format regex: "07:00", "7:00", "18:30", "00:00", "23:59"
        val regex24 = Regex("^([0-1]?[0-9]|2[0-3]):([0-5][0-9])$")
        val match24 = regex24.find(trimmed)
        if (match24 != null) {
            val h24 = match24.groupValues[1].toInt()
            val m = match24.groupValues[2].toInt()
            val isAm = h24 < 12
            val h12 = when {
                h24 == 0 -> 12
                h24 > 12 -> h24 - 12
                else -> h24
            }
            return Time12(h12, m, isAm)
        }

        return fallback
    }

    /**
     * Converts any valid time string to the standard 12-hour representation.
     */
    fun normalizeTo12Hour(timeStr: String, defaultIfBlank: String = "8:00 AM"): String {
        if (timeStr.isBlank()) return defaultIfBlank
        return parseTime(timeStr).format()
    }
}
