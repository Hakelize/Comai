package com.comai.digitalactivity.repository

import android.content.Context
import com.comai.contextengine.db.ComaiDatabase
import com.comai.digitalactivity.data.AppLimitDao
import com.comai.digitalactivity.data.AppLimitEntity
import com.comai.digitalactivity.data.DailyActivitySummaryDao
import com.comai.digitalactivity.data.DailyActivitySummaryEntity
import com.comai.digitalactivity.data.DigitalActivityPreferences
import com.comai.digitalactivity.intelligence.ActivityInferenceEngine
import com.comai.digitalactivity.intelligence.ActivityInferenceResult
import com.comai.digitalactivity.intelligence.LearnedRoutineProfile
import com.comai.digitalactivity.intelligence.RoutineLearningEngine
import com.comai.digitalactivity.limits.ProductivityLimitManager
import com.comai.digitalactivity.model.DailyUsageSnapshot
import com.comai.digitalactivity.monitoring.DigitalActivityMonitor
import com.comai.digitalactivity.monitoring.UsagePermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppUsageRepository: Central repository coordinating digital activity monitoring,
 * awake/sleep inference, routine learning persistence, and productivity limits.
 */
class AppUsageRepository(
    private val context: Context,
    private val database: ComaiDatabase = ComaiDatabase.getInstance(context),
    private val monitor: DigitalActivityMonitor = DigitalActivityMonitor(context),
    private val preferences: DigitalActivityPreferences = DigitalActivityPreferences(context)
) {

    private val appLimitDao: AppLimitDao = database.appLimitDao()
    private val summaryDao: DailyActivitySummaryDao = database.dailyActivityDao()
    private val limitManager: ProductivityLimitManager = ProductivityLimitManager(context, appLimitDao, preferences)

    fun hasUsagePermission(): Boolean = UsagePermissionManager.hasUsagePermission(context)

    fun isMonitoringEnabled(): Boolean = preferences.isMonitoringEnabled

    fun setMonitoringEnabled(enabled: Boolean) {
        preferences.isMonitoringEnabled = enabled
    }

    suspend fun getDailyUsageSnapshot(): DailyUsageSnapshot = withContext(Dispatchers.IO) {
        if (!preferences.isMonitoringEnabled) {
            DailyUsageSnapshot(
                totalScreenTimeMs = 0L,
                appUsageList = emptyList(),
                categoryBreakdown = emptyList(),
                topUsedApp = null,
                firstActiveTimeMs = 0L,
                lastActiveTimeMs = 0L,
                recentForegroundApp = null,
                recentAppCategory = com.comai.digitalactivity.model.AppCategory.OTHER
            )
        } else {
            monitor.getDailyUsageSnapshot()
        }
    }

    suspend fun inferActivityState(
        snapshot: DailyUsageSnapshot,
        configuredWakeHour: Int = 7,
        configuredWakeMinute: Int = 0,
        configuredSleepHour: Int = 23,
        configuredSleepMinute: Int = 0
    ): ActivityInferenceResult = withContext(Dispatchers.Default) {
        ActivityInferenceEngine.inferState(
            nowMs = System.currentTimeMillis(),
            snapshot = snapshot,
            configuredWakeHour = configuredWakeHour,
            configuredWakeMinute = configuredWakeMinute,
            configuredSleepHour = configuredSleepHour,
            configuredSleepMinute = configuredSleepMinute,
            isMonitoringEnabled = preferences.isMonitoringEnabled,
            hasPermission = hasUsagePermission()
        )
    }

    suspend fun getLearnedRoutine(): LearnedRoutineProfile = withContext(Dispatchers.IO) {
        val summaries = summaryDao.getRecentSummaries(14)
        RoutineLearningEngine.computeLearnedRoutine(summaries)
    }

    fun getAllLimitsFlow(): Flow<List<AppLimitEntity>> = appLimitDao.getAllLimitsFlow()

    suspend fun addOrUpdateLimit(limit: AppLimitEntity) = withContext(Dispatchers.IO) {
        appLimitDao.insertOrUpdate(limit)
    }

    suspend fun deleteLimit(packageName: String) = withContext(Dispatchers.IO) {
        appLimitDao.deleteLimit(packageName)
    }

    suspend fun setLimitEnabled(packageName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        appLimitDao.setLimitEnabled(packageName, enabled)
    }

    suspend fun checkLimitsAndNotify(snapshot: DailyUsageSnapshot) = withContext(Dispatchers.IO) {
        limitManager.checkLimitsAndNotify(snapshot)
    }

    suspend fun persistDailySnapshotIfAppropriate(snapshot: DailyUsageSnapshot, inferred: ActivityInferenceResult) = withContext(Dispatchers.IO) {
        if (!preferences.isMonitoringEnabled || snapshot.totalScreenTimeMs <= 0L) return@withContext

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val topApp = snapshot.topUsedApp

        val summary = DailyActivitySummaryEntity(
            date = todayDate,
            totalScreenTimeMinutes = snapshot.totalScreenTimeMs / (1000 * 60),
            firstActiveTimeMs = snapshot.firstActiveTimeMs,
            lastActiveTimeMs = snapshot.lastActiveTimeMs,
            topPackageName = topApp?.packageName ?: "none",
            topCategory = topApp?.category?.name ?: "OTHER",
            inferredWakeTimeMs = if (inferred.state.name == "LIKELY_AWAKE") snapshot.firstActiveTimeMs else null,
            inferredSleepTimeMs = if (inferred.state.name == "LIKELY_ASLEEP") snapshot.lastActiveTimeMs else null
        )
        summaryDao.insertOrUpdate(summary)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        summaryDao.clearAllSummaries()
    }
}
