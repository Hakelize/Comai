package com.comai.contextengine.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "community_events")
data class CommunityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventName: String,
    val category: String,
    val locationArea: String,
    val applicableOnWorkDay: Boolean,
    val dayOfWeekApplicability: String = "ALL",
    val timeWindowStart: String = "17:00",
    val timeWindowEnd: String = "21:00",
    val description: String
)

@Dao
interface CommunityEventDao {

    @Query("""
        SELECT * FROM community_events 
        WHERE applicableOnWorkDay = :isWorkDay 
        ORDER BY id ASC
    """)
    suspend fun getCandidateEvents(isWorkDay: Boolean): List<CommunityEvent>

    @Query("""
        SELECT * FROM community_events 
        WHERE locationArea LIKE '%' || :locationArea || '%' 
        AND applicableOnWorkDay = :isWorkDay
    """)
    suspend fun findMatchingEvents(isWorkDay: Boolean, locationArea: String): List<CommunityEvent>

    @Query("SELECT * FROM community_events")
    suspend fun getAllEvents(): List<CommunityEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CommunityEvent>)
}
