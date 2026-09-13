package com.comai.ui.screens.digitalactivity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comai.digitalactivity.data.AppLimitEntity
import com.comai.digitalactivity.intelligence.ActivityInferenceResult
import com.comai.digitalactivity.intelligence.DeviceActivityState
import com.comai.digitalactivity.intelligence.LearnedRoutineProfile
import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.DailyUsageSnapshot
import com.comai.digitalactivity.repository.AppUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DigitalActivityUiState(
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val isMonitoringEnabled: Boolean = true,
    val snapshot: DailyUsageSnapshot = DailyUsageSnapshot(
        totalScreenTimeMs = 0L,
        appUsageList = emptyList(),
        categoryBreakdown = emptyList(),
        topUsedApp = null,
        firstActiveTimeMs = 0L,
        lastActiveTimeMs = 0L,
        recentForegroundApp = null,
        recentAppCategory = AppCategory.OTHER
    ),
    val inferredState: ActivityInferenceResult = ActivityInferenceResult(
        state = DeviceActivityState.UNKNOWN,
        confidence = 0.0,
        reason = "INITIALIZING",
        lastActivityTimestampMs = 0L,
        lastActivityFormatted = "None"
    ),
    val learnedRoutine: LearnedRoutineProfile = LearnedRoutineProfile(
        typicalMorningStartFormatted = "Gathering data...",
        typicalNightRestFormatted = "Gathering data...",
        observationCount = 0,
        confidence = 0.0f,
        isSufficientData = false
    ),
    val limits: List<AppLimitEntity> = emptyList(),
    val showAddLimitDialog: Boolean = false,
    val showClearHistoryDialog: Boolean = false
)

class DigitalActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppUsageRepository(application)

    private val _uiState = MutableStateFlow(DigitalActivityUiState())
    val uiState: StateFlow<DigitalActivityUiState> = _uiState.asStateFlow()

    init {
        observeLimits()
        refresh()
    }

    private fun observeLimits() {
        viewModelScope.launch {
            repository.getAllLimitsFlow().collect { limitsList ->
                _uiState.update { it.copy(limits = limitsList) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val hasPermission = repository.hasUsagePermission()
            val isMonitoringEnabled = repository.isMonitoringEnabled()

            val snapshot = repository.getDailyUsageSnapshot()
            val inferred = repository.inferActivityState(snapshot)
            val learned = repository.getLearnedRoutine()

            // Check if limits exceeded and post notifications if needed
            repository.checkLimitsAndNotify(snapshot)

            // Persist summary for long-term routine learning
            repository.persistDailySnapshotIfAppropriate(snapshot, inferred)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasPermission = hasPermission,
                    isMonitoringEnabled = isMonitoringEnabled,
                    snapshot = snapshot,
                    inferredState = inferred,
                    learnedRoutine = learned
                )
            }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        repository.setMonitoringEnabled(enabled)
        _uiState.update { it.copy(isMonitoringEnabled = enabled) }
        refresh()
    }

    fun addOrUpdateLimit(packageName: String, appName: String, limitMinutes: Int) {
        viewModelScope.launch {
            val limit = AppLimitEntity(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = limitMinutes,
                isEnabled = true
            )
            repository.addOrUpdateLimit(limit)
            refresh()
        }
    }

    fun deleteLimit(packageName: String) {
        viewModelScope.launch {
            repository.deleteLimit(packageName)
        }
    }

    fun toggleLimit(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setLimitEnabled(packageName, enabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            refresh()
        }
    }

    fun setShowAddLimitDialog(show: Boolean) {
        _uiState.update { it.copy(showAddLimitDialog = show) }
    }

    fun setShowClearHistoryDialog(show: Boolean) {
        _uiState.update { it.copy(showClearHistoryDialog = show) }
    }
}
