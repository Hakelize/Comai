package com.comai.data.menstrualcycle

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Privacy-first, on-device calculation of non-medical cycle estimates.
 * All computations are purely local approximations and clearly labeled as estimates.
 */
object MenstrualCycleCalculator {

    const val DISCLAIMER = "Estimated based on your saved cycle information."

    /**
     * Calculates the next estimated cycle start date and window based on the user's saved data.
     * Returns null if unconfigured or invalid.
     */
    fun calculateEstimate(
        data: MenstrualCycleData,
        nowMs: Long = System.currentTimeMillis()
    ): MenstrualCycleEstimate? {
        if (!data.isConfigured || data.lastPeriodStartDateMs <= 0L || data.typicalCycleLengthDays <= 0) {
            return null
        }

        val cycleLength = if (data.typicalCycleLengthDays in 15..60) data.typicalCycleLengthDays else 28
        val duration = if (data.periodDurationDays in 1..15) data.periodDurationDays else 5

        val cal = Calendar.getInstance().apply {
            timeInMillis = data.lastPeriodStartDateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Project into future if the last start date was in the past
        while (cal.timeInMillis < todayCal.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, cycleLength)
        }

        val nextEstimatedMs = cal.timeInMillis

        // Window end date
        val windowEndCal = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, duration)
        }
        val estimatedWindowEndMs = windowEndCal.timeInMillis

        val diffMs = nextEstimatedMs - todayCal.timeInMillis
        val daysRemaining = max(0, TimeUnit.MILLISECONDS.toDays(diffMs).toInt())

        val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val formattedDate = formatter.format(Date(nextEstimatedMs))

        return MenstrualCycleEstimate(
            nextEstimatedStartDateMs = nextEstimatedMs,
            formattedEstimatedDate = formattedDate,
            daysRemaining = daysRemaining,
            estimatedWindowEndMs = estimatedWindowEndMs,
            disclaimer = DISCLAIMER
        )
    }
}
