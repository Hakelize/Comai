package com.comai.contextengine.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "personalization_state")
data class PersonalizationState(
    @PrimaryKey val dateIso: String,
    val wakeDeviationMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val lunchLogged: Boolean = false,
    val eveningFreeTimeMinutes: Int = 120,
    val overallDeviationScore: Double = 0.0
)

@Dao
interface PersonalizationStateDao {
    @Query("SELECT * FROM personalization_state WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getStateForDate(dateIso: String): PersonalizationState?

    @Query("SELECT * FROM personalization_state ORDER BY dateIso DESC LIMIT 1")
    fun getStateFlow(): Flow<PersonalizationState?>

    @Query("SELECT * FROM personalization_state ORDER BY dateIso DESC LIMIT 1")
    suspend fun getState(): PersonalizationState?

    @Query("SELECT * FROM personalization_state ORDER BY dateIso DESC LIMIT 7")
    suspend fun getRecent7DaysState(): List<PersonalizationState>

    @Query("SELECT * FROM personalization_state ORDER BY dateIso DESC LIMIT 7")
    fun getRecent7DaysStateFlow(): Flow<List<PersonalizationState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: PersonalizationState)
}
