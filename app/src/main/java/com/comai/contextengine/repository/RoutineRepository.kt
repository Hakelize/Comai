package com.comai.contextengine.repository

import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.Program
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun getProgramsFlow(): Flow<List<Program>>
    suspend fun getProgramForDay(dayOfWeek: String): Program?
    suspend fun saveProgram(program: Program)
    suspend fun savePrograms(programs: List<Program>)
}

class RoutineRepositoryImpl(private val database: ComaiDatabase) : RoutineRepository {
    override fun getProgramsFlow(): Flow<List<Program>> = database.programDao().getAllProgramsFlow()
    override suspend fun getProgramForDay(dayOfWeek: String): Program? = database.programDao().getProgramForDay(dayOfWeek)
    override suspend fun saveProgram(program: Program) = database.programDao().insertOrUpdateProgram(program)
    override suspend fun savePrograms(programs: List<Program>) = database.programDao().insertPrograms(programs)
}
