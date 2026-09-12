package com.comai.contextengine.repository

import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.db.PersonalizationState
import kotlinx.coroutines.flow.Flow

interface PersonalizationRepository {
    fun getPersonalizationStateFlow(): Flow<PersonalizationState?>
    suspend fun getPersonalizationState(): PersonalizationState?
    suspend fun savePersonalizationState(state: PersonalizationState)
}

class PersonalizationRepositoryImpl(private val database: ComaiDatabase) : PersonalizationRepository {
    override fun getPersonalizationStateFlow(): Flow<PersonalizationState?> = database.personalizationStateDao().getStateFlow()
    override suspend fun getPersonalizationState(): PersonalizationState? = database.personalizationStateDao().getState()
    override suspend fun savePersonalizationState(state: PersonalizationState) = database.personalizationStateDao().insertOrUpdateState(state)
}
