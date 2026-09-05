package com.comai.contextengine.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val baselineWakeTime: String = "07:00",
    val baselineSleepTime: String = "23:00",
    val medicationFlag: Boolean = true,
    val workDaysPattern: String = "MON,TUE,WED,THU,FRI",
    val historicalOfficeDepartureTime: String = "17:30",
    val preferredEventCategories: String = "OUTDOORS,COMMUNITY,FITNESS",
    val homeLocationTag: String = "Home Area",
    val workLocationTag: String = "Tech Hub Office"
)

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)

    @Update
    suspend fun updateProfile(profile: UserProfile)
}
