package com.comai.contextengine.repository

import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun getUserProfileFlow(): Flow<UserProfile?>
    suspend fun getUserProfile(): UserProfile?
    suspend fun saveUserProfile(userProfile: UserProfile)
}

class UserProfileRepositoryImpl(private val database: ComaiDatabase) : UserProfileRepository {
    override fun getUserProfileFlow(): Flow<UserProfile?> = database.userProfileDao().getUserProfileFlow()

    override suspend fun getUserProfile(): UserProfile? {
        return database.userProfileDao().getUserProfile()
    }

    override suspend fun saveUserProfile(userProfile: UserProfile) {
        database.userProfileDao().insertOrUpdateProfile(userProfile)
    }
}
