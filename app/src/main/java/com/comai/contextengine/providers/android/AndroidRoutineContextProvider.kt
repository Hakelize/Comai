package com.comai.contextengine.providers.android

import com.comai.contextengine.context.RoutineEngine
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.providers.interfaces.RoutineContextProvider
import com.comai.contextengine.providers.models.RoutineContextData

/**
 * Android implementation for Routine & Routine Deviation Context.
 */
class AndroidRoutineContextProvider(
    private val database: ComaiDatabase? = null,
    private val routineEngine: RoutineEngine = RoutineEngine(database)
) : RoutineContextProvider {

    override val isAvailable: Boolean = true

    override fun getContextData(): RoutineContextData {
        return RoutineContextData(
            routineState = "WORK_DAY_ROUTINE",
            routineDeviationScore = 0.0,
            isRoutineDeviation = false,
            deviationMinutes = 0,
            confidence = 1.0f
        )
    }
}
