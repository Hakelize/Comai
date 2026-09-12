package com.comai.contextengine.context

import com.comai.contextengine.data.RoutineProfile
import com.comai.contextengine.db.Program
import com.comai.contextengine.db.UserProfile

/**
 * Detects user baseline daily routines based on profile settings and weekly program schedules.
 */
class RoutineDetector {

    private val normalizer = ContextNormalizer()

    /**
     * Computes the routine profile for a target day of the week.
     */
    fun computeRoutineProfile(userProfile: UserProfile?, program: Program?, isWorkDay: Boolean): RoutineProfile {
        val wakeTimeStr = program?.expectedWakeTime ?: userProfile?.baselineWakeTime ?: "07:00"
        val sleepTimeStr = userProfile?.baselineSleepTime ?: "23:00"

        val wakeMinutes = normalizer.parseTimeToMinutes(wakeTimeStr)
        val sleepMinutes = normalizer.parseTimeToMinutes(sleepTimeStr)

        return RoutineProfile(
            expectedWakeTimeMinutes = wakeMinutes,
            expectedCommuteStartMinutes = if (isWorkDay) (wakeMinutes + 90) else (wakeMinutes + 180),
            expectedLunchStartMinutes = 780, // 13:00 (1:00 PM)
            expectedOfficeDepartureMinutes = 1080, // 18:00 (6:00 PM)
            expectedMedicationMinutes = 1260, // 21:00 (9:00 PM)
            expectedSleepMinutes = sleepMinutes,
            isWorkDay = isWorkDay
        )
    }
}
