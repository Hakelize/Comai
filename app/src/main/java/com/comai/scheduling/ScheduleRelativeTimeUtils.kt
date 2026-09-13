package com.comai.scheduling

import java.util.Calendar
import kotlin.math.max

/**
 * Calculates human-friendly relative remaining time for personal schedule plans.
 * E.g., "in 45 mins", "in 2 hrs 15 mins", "Tomorrow at 9:00 AM • in 9 hrs".
 */
object ScheduleRelativeTimeUtils {

    fun computeRelativeTime(
        timeString: String,
        repeatFrequency: String,
        isEnabled: Boolean = true,
        nowCalendar: Calendar = Calendar.getInstance()
    ): String {
        if (!isEnabled) {
            return "Reminder off"
        }

        val targetMs = try {
            ScheduleAlarmManager.calculateNextTriggerTime(
                timeString = timeString,
                repeatFrequency = repeatFrequency,
                nowCalendar = nowCalendar
            )
        } catch (_: Exception) {
            return ""
        }

        val diffMs = max(0L, targetMs - nowCalendar.timeInMillis)
        val diffMinutes = (diffMs + 30000L) / 60000L // rounded minutes

        val targetCal = (nowCalendar.clone() as Calendar).apply { timeInMillis = targetMs }
        val isToday = targetCal.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) &&
                targetCal.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)
        val isTomorrow = targetCal.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) + 1 &&
                targetCal.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)

        val remainingFormatted = when {
            diffMinutes < 1 -> "in less than a min"
            diffMinutes < 60 -> "in $diffMinutes min${if (diffMinutes > 1) "s" else ""}"
            else -> {
                val hours = diffMinutes / 60
                val remMinutes = diffMinutes % 60
                if (remMinutes == 0L) {
                    "in $hours hr${if (hours > 1) "s" else ""}"
                } else {
                    "in $hours hr${if (hours > 1) "s" else ""} $remMinutes min${if (remMinutes > 1) "s" else ""}"
                }
            }
        }

        return if (isToday) {
            remainingFormatted
        } else if (isTomorrow) {
            "Tomorrow at $timeString • $remainingFormatted"
        } else {
            val dayName = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                Calendar.SUNDAY -> "Sunday"
                else -> "Upcoming"
            }
            "$dayName at $timeString • $remainingFormatted"
        }
    }
}
