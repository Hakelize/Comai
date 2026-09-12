package com.comai.contextengine.context

import android.util.Log
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.DailyLog
import com.comai.contextengine.db.Program
import com.comai.contextengine.db.UserProfile
import com.comai.contextengine.models.RoutineEvaluation
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.DeviceContext

/**
 * RoutineEngine - Local, non-cloud routine detection engine.
 * Compares current user context against baseline programs and observed Room DB historical logs.
 * Evaluates routine signals:
 * - usual wake period
 * - usual sleep period
 * - usual office arrival period
 * - usual office leaving period
 * - usual commute period
 * - recurring activity/time patterns
 *
 * Answers:
 * - isNormalRoutine: Boolean
 * - routineDeviation: Boolean
 * - deviationMinutes: Int
 * - confidence: Float
 * - deviationReason: String
 */
class RoutineEngine(
    private val database: ComaiDatabase? = null
) {
    private val normalizer = ContextNormalizer()

    /**
     * Evaluates the current [DeviceContext] against Room baseline programs & historical observation logs.
     */
    suspend fun evaluateRoutine(deviceContext: DeviceContext): RoutineEvaluation {
        val dayOfWeek = deviceContext.time.dayOfWeek
        val isWorkDay = deviceContext.time.isWorkDay
        val currentTimeMinutes = deviceContext.time.hour * 60 + deviceContext.time.minute

        // Fetch profile & programs from Room DB if available
        val profile: UserProfile? = database?.userProfileDao()?.getUserProfile()
        val program: Program? = database?.programDao()?.getProgramForDay(dayOfWeek)
        val historicalLogs: List<DailyLog> = database?.dailyLogDao()?.getAllLogs() ?: emptyList()

        return evaluateInternal(
            currentTimeMinutes = currentTimeMinutes,
            dayOfWeek = dayOfWeek,
            isWorkDay = isWorkDay,
            locationCategory = deviceContext.location.locationCategory,
            activityState = deviceContext.activity.detectedState,
            profile = profile,
            program = program,
            historicalLogs = historicalLogs
        )
    }

    /**
     * Pure evaluation logic using supplied profile, program, and historical logs.
     * Can be easily tested with mock historical data.
     */
    fun evaluateInternal(
        currentTimeMinutes: Int,
        dayOfWeek: String,
        isWorkDay: Boolean,
        locationCategory: String,
        activityState: ActivityMovementState,
        profile: UserProfile? = null,
        program: Program? = null,
        historicalLogs: List<DailyLog> = emptyList()
    ): RoutineEvaluation {
        // 1. Determine target expected windows (dynamically adjusted via Room historical logs if available)
        val wakeMinutes = computeDynamicBaseline(
            fallbackTimeMinutes = normalizer.parseTimeToMinutes(program?.expectedWakeTime ?: profile?.baselineWakeTime ?: "07:00"),
            eventType = "WAKE_UP",
            historicalLogs = historicalLogs
        )

        val sleepMinutes = computeDynamicBaseline(
            fallbackTimeMinutes = normalizer.parseTimeToMinutes(program?.expectedSleepTime ?: profile?.baselineSleepTime ?: "23:00"),
            eventType = "SLEEP",
            historicalLogs = historicalLogs
        )

        val commuteStartMinutes = computeDynamicBaseline(
            fallbackTimeMinutes = normalizer.parseTimeToMinutes(program?.expectedCommuteStartTime ?: "08:15"),
            eventType = "COMMUTE_START",
            historicalLogs = historicalLogs
        )

        val officeArrivalMinutes = computeDynamicBaseline(
            fallbackTimeMinutes = 510, // 08:30 AM
            eventType = "OFFICE_ARRIVAL",
            historicalLogs = historicalLogs
        )

        val officeDepartureMinutes = computeDynamicBaseline(
            fallbackTimeMinutes = normalizer.parseTimeToMinutes(program?.expectedDepartureTime ?: profile?.historicalOfficeDepartureTime ?: "17:30"),
            eventType = "OFFICE_DEPARTURE",
            historicalLogs = historicalLogs
        )

        // 2. Evaluate deviation signals
        var maxDeviationMinutes = 0
        var isDeviationDetected = false
        var reason = "Normal routine adherence"

        // Signal A: Non-work day office visit or commute
        if (!isWorkDay && (locationCategory.equals("Office", ignoreCase = true) || activityState == ActivityMovementState.IN_VEHICLE)) {
            isDeviationDetected = true
            maxDeviationMinutes = 60
            reason = "Unexpected office activity or commute on non-work day ($dayOfWeek)"
        }

        // Signal B: Office Departure Period (Staying late at office past expected departure + tolerance)
        if (isWorkDay && locationCategory.equals("Office", ignoreCase = true) && currentTimeMinutes > (officeDepartureMinutes + 30)) {
            val delta = currentTimeMinutes - officeDepartureMinutes
            if (delta > maxDeviationMinutes) {
                maxDeviationMinutes = delta
                isDeviationDetected = true
                reason = "Staying late at office ($delta minutes past expected departure at ${formatMinutes(officeDepartureMinutes)})"
            }
        }

        // Signal C: Office Arrival Period / Delayed Commute (Work day morning, past expected office arrival, still at Home)
        if (isWorkDay && locationCategory.equals("Home", ignoreCase = true) && currentTimeMinutes > (officeArrivalMinutes + 30) && currentTimeMinutes < 720) {
            val delta = currentTimeMinutes - officeArrivalMinutes
            if (delta > maxDeviationMinutes) {
                maxDeviationMinutes = delta
                isDeviationDetected = true
                reason = "Delayed office arrival ($delta minutes past expected arrival at ${formatMinutes(officeArrivalMinutes)})"
            }
        }

        // Signal D: Wake Period (Late wake up past expected window)
        if (currentTimeMinutes in (wakeMinutes + 30)..720 && activityState == ActivityMovementState.STATIONARY && locationCategory.equals("Home", ignoreCase = true)) {
            val delta = currentTimeMinutes - wakeMinutes
            if (delta > 30 && delta > maxDeviationMinutes) {
                maxDeviationMinutes = delta
                isDeviationDetected = true
                reason = "Late wake period ($delta minutes past expected wake time ${formatMinutes(wakeMinutes)})"
            }
        }

        // Signal E: Sleep Period (Late active past expected sleep time)
        if (currentTimeMinutes > (sleepMinutes + 30) || currentTimeMinutes < (wakeMinutes - 120)) {
            val currentAdjusted = if (currentTimeMinutes < (wakeMinutes - 120)) currentTimeMinutes + 1440 else currentTimeMinutes
            val delta = currentAdjusted - sleepMinutes
            if (delta > 30 && delta > maxDeviationMinutes) {
                maxDeviationMinutes = delta
                isDeviationDetected = true
                reason = "Late activity past expected sleep time ($delta minutes past ${formatMinutes(sleepMinutes)})"
            }
        }

        // 3. Compute confidence dynamically based on historical sample size & signal quality
        val sampleSize = historicalLogs.size
        val confidenceScore = when {
            sampleSize >= 10 -> 0.95f
            sampleSize >= 3 -> 0.88f
            profile != null -> 0.82f
            else -> 0.75f
        }

        return RoutineEvaluation(
            isNormalRoutine = !isDeviationDetected,
            routineDeviation = isDeviationDetected,
            deviationMinutes = maxDeviationMinutes,
            confidence = confidenceScore,
            deviationReason = reason
        )
    }

    /**
     * Dynamically adjusts baseline target time based on observed local Room DB logs.
     */
    private fun computeDynamicBaseline(
        fallbackTimeMinutes: Int,
        eventType: String,
        historicalLogs: List<DailyLog>
    ): Int {
        val matchingLogs = historicalLogs.filter { it.eventType.equals(eventType, ignoreCase = true) }
        if (matchingLogs.isEmpty()) return fallbackTimeMinutes

        val timesInMinutes = matchingLogs.map { log ->
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = log.timestampMs }
            calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        }

        return timesInMinutes.average().toInt()
    }

    private fun formatMinutes(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }
}
