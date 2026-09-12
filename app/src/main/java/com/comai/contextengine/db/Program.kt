package com.comai.contextengine.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "routine_program")
data class Program(
    @PrimaryKey val dayOfWeek: String,
    val isWorkDay: Boolean,
    val expectedWakeTime: String = "07:00",
    val expectedCommuteStartTime: String = "08:15",
    val expectedLunchTime: String = "12:30",
    val expectedDepartureTime: String = "17:30",
    val expectedSleepTime: String = "23:00"
)

@Dao
interface ProgramDao {
    @Query("SELECT * FROM routine_program WHERE dayOfWeek = :day LIMIT 1")
    suspend fun getProgramForDay(day: String): Program?

    @Query("SELECT * FROM routine_program")
    suspend fun getAllPrograms(): List<Program>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgram(program: Program)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<Program>)
}
