package com.comai.digitalactivity.intelligence

import com.comai.digitalactivity.data.DailyActivitySummaryEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the learned digital routine statistics.
 */
data class LearnedRoutineProfile(
    val typicalMorningStartFormatted: String,
    val typicalNightRestFormatted: String,
    val observationCount: Int,
    val confidence: Float,
    val isSufficientData: Boolean
)

/**
 * RoutineLearningEngine: Observes morning first activity and night last activity patterns
 * over multiple days to compute an approximate moving average digital routine.
 *
 * Strictly avoids jumping to strong conclusions with insufficient observations.
 */
object RoutineLearningEngine {

    private const val MIN_OBSERVATIONS_FOR_CONFIDENCE = 3

    fun computeLearnedRoutine(summaries: List<DailyActivitySummaryEntity>): LearnedRoutineProfile {
        val validSummaries = summaries.filter { it.firstActiveTimeMs > 0L && it.lastActiveTimeMs > 0L }
        val count = validSummaries.size

        if (count < MIN_OBSERVATIONS_FOR_CONFIDENCE) {
            return LearnedRoutineProfile(
                typicalMorningStartFormatted = if (validSummaries.isNotEmpty()) formatTime12(validSummaries.first().firstActiveTimeMs) else "Gathering data...",
                typicalNightRestFormatted = if (validSummaries.isNotEmpty()) formatTime12(validSummaries.first().lastActiveTimeMs) else "Gathering data...",
                observationCount = count,
                confidence = if (count == 0) 0.0f else (count / 5.0f).coerceAtMost(0.5f),
                isSufficientData = false
            )
        }

        // Calculate average minutes from midnight for morning start
        var totalMorningMinutes = 0
        var totalNightMinutes = 0

        for (summary in validSummaries) {
            totalMorningMinutes += getMinutesFromMidnight(summary.firstActiveTimeMs)
            totalNightMinutes += getMinutesFromMidnight(summary.lastActiveTimeMs)
        }

        val avgMorningMinutes = totalMorningMinutes / count
        val avgNightMinutes = totalNightMinutes / count

        val confidence = (0.5f + (count.coerceAtMost(10) * 0.05f)).coerceAtMost(0.95f)

        return LearnedRoutineProfile(
            typicalMorningStartFormatted = formatMinutesTo12Hour(avgMorningMinutes),
            typicalNightRestFormatted = formatMinutesTo12Hour(avgNightMinutes),
            observationCount = count,
            confidence = confidence,
            isSufficientData = true
        )
    }

    private fun getMinutesFromMidnight(timeMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun formatMinutesTo12Hour(totalMinutes: Int): String {
        val hours24 = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        val isPm = hours24 >= 12
        val hours12 = when (hours24 % 12) {
            0 -> 12
            else -> hours24 % 12
        }
        val amPm = if (isPm) "PM" else "AM"
        return String.format(Locale.US, "%d:%02d %s", hours12, minutes, amPm)
    }

    private fun formatTime12(timeMs: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }
}
