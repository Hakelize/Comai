package com.comai.ui.screens.memory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.contextengine.db.Memory
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.data.repository.PersonalPlanRepository
import com.comai.memory.MemoryRepository
import com.comai.nearby.NearbyContext
import com.comai.nearby.NearbyContextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Memory Tab.
 * Integrates:
 * 1. Personal Memories (Preferences, Context, Knowledge, Inferred, Episodic)
 * 2. Real Calendar & Schedules (reading from single source of truth PersonalPlanRepository)
 * 3. Nearby Context Engine (Location, Traffic, Local News, Events, and Offline state)
 */
class MemoryViewModel(
    private val memoryRepository: MemoryRepository,
    private val personalPlanRepository: PersonalPlanRepository? = null,
    private val nearbyContextProvider: NearbyContextProvider? = null
) : ViewModel() {

    // Single source of truth for personal memories
    val memories: StateFlow<List<Memory>> = memoryRepository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Single source of truth for plans / schedules (Room/repository)
    val plans: StateFlow<List<PersonalPlan>> = personalPlanRepository?.plans
        ?: MutableStateFlow(emptyList())

    // Nearby Context Engine State
    val nearbyContext: StateFlow<NearbyContext> = nearbyContextProvider?.nearbyContext
        ?: MutableStateFlow(NearbyContext())

    private val _isRefreshingNearby = MutableStateFlow(false)
    val isRefreshingNearby: StateFlow<Boolean> = _isRefreshingNearby.asStateFlow()

    /**
     * Whether location permission is currently missing.
     * Exposed so the UI can show a "Grant Location Access" CTA.
     */
    private val _isLocationPermissionMissing = MutableStateFlow(false)
    val isLocationPermissionMissing: StateFlow<Boolean> = _isLocationPermissionMissing.asStateFlow()

    init {
        refreshNearbyContext()
    }

    /**
     * Refreshes nearby context. If provider reports permission missing,
     * sets [isLocationPermissionMissing] = true so the UI can show a CTA.
     */
    fun refreshNearbyContext() {
        if (nearbyContextProvider == null) return
        viewModelScope.launch {
            _isRefreshingNearby.value = true
            try {
                val result = nearbyContextProvider.refreshContext(plans.value)
                _isLocationPermissionMissing.value = !result.location.isPermissionGranted && result.source == "Permission Required"
            } catch (_: Exception) {
                _isLocationPermissionMissing.value = false
            } finally {
                _isRefreshingNearby.value = false
            }
        }
    }

    /**
     * Called from the UI after the user grants location permission.
     * Immediately triggers a context refresh to load real location data.
     */
    fun onLocationPermissionGranted() {
        _isLocationPermissionMissing.value = false
        refreshNearbyContext()
    }

    fun addPlan(
        title: String,
        time: String,
        repeatFrequency: String = "Daily",
        reminderType: ReminderType = ReminderType.NOTIFICATION,
        date: String? = null
    ) {
        personalPlanRepository?.addPlan(title, time, repeatFrequency, reminderType, date)
        refreshNearbyContext()
    }

    fun updatePlan(
        id: String,
        title: String,
        time: String,
        repeatFrequency: String,
        reminderType: ReminderType = ReminderType.NOTIFICATION,
        isEnabled: Boolean,
        date: String? = null
    ) {
        personalPlanRepository?.updatePlan(id, title, time, repeatFrequency, reminderType, isEnabled, date)
        refreshNearbyContext()
    }

    fun deletePlan(id: String) {
        personalPlanRepository?.deletePlan(id)
        refreshNearbyContext()
    }

    fun togglePlan(id: String) {
        personalPlanRepository?.togglePlan(id)
        refreshNearbyContext()
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            memoryRepository.clearAll()
        }
    }
}
