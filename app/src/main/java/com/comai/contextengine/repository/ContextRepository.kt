package com.comai.contextengine.repository

import com.comai.contextengine.context.DeviceContextManager
import com.comai.contextengine.context.RoutineDetector
import com.comai.contextengine.data.DeviceContextSnapshot
import com.comai.contextengine.data.RoutineProfile
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.DailyLog

interface ContextRepository {
    fun getCurrentDeviceSnapshot(): DeviceContextSnapshot
    suspend fun getDailyLogs(): List<DailyLog>
    suspend fun getCurrentRoutineProfile(isWorkDay: Boolean): RoutineProfile
}

class ContextRepositoryImpl(
    private val deviceContextManager: DeviceContextManager,
    private val database: ComaiDatabase,
    private val routineDetector: RoutineDetector = RoutineDetector()
) : ContextRepository {

    override fun getCurrentDeviceSnapshot(): DeviceContextSnapshot {
        return deviceContextManager.captureSnapshot()
    }

    override suspend fun getDailyLogs(): List<DailyLog> {
        return database.dailyLogDao().getAllLogs()
    }

    override suspend fun getCurrentRoutineProfile(isWorkDay: Boolean): RoutineProfile {
        val profile = database.userProfileDao().getUserProfile()
        val programs = database.programDao().getAllPrograms()
        val program = programs.firstOrNull { it.isWorkDay == isWorkDay }
        return routineDetector.computeRoutineProfile(profile, program, isWorkDay)
    }
}
