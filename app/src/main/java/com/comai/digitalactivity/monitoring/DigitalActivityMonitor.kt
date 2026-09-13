package com.comai.digitalactivity.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.AppCategoryManager
import com.comai.digitalactivity.model.AppUsageInfo
import com.comai.digitalactivity.model.CategoryUsageSummary
import com.comai.digitalactivity.model.DailyUsageSnapshot
import java.util.Calendar

/**
 * DigitalActivityMonitor: Responsible for collecting device screen time,
 * per-app usage, recent active app, and morning/night activity timestamps
 * using standard Android UsageStatsManager APIs.
 *
 * Fully privacy-first and local-first: no keystrokes, messages, or content accessed.
 */
class DigitalActivityMonitor(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Captures a complete daily usage snapshot from midnight to the current moment.
     */
    fun getDailyUsageSnapshot(): DailyUsageSnapshot {
        if (!UsagePermissionManager.hasUsagePermission(context) || usageStatsManager == null) {
            return getEmptySnapshot()
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        // 1. Query usage stats for the day
        val statsList = try {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query usage stats: ${e.message}")
            emptyList()
        }

        // Aggregate by package
        val packageDurations = mutableMapOf<String, Long>()
        val packageLastUsed = mutableMapOf<String, Long>()

        for (stat in statsList) {
            val duration = stat.totalTimeInForeground
            if (duration > 0) {
                packageDurations[stat.packageName] = (packageDurations[stat.packageName] ?: 0L) + duration
                if (stat.lastTimeUsed > (packageLastUsed[stat.packageName] ?: 0L)) {
                    packageLastUsed[stat.packageName] = stat.lastTimeUsed
                }
            }
        }

        // Filter out system launchers, comai itself, or negligible usage (< 10 seconds)
        val validApps = packageDurations
            .filter { (pkg, duration) ->
                duration >= 10_000L &&
                        pkg != context.packageName &&
                        !isExcludedSystemPackage(pkg)
            }
            .map { (pkg, duration) ->
                val appName = getAppName(pkg)
                val category = AppCategoryManager.categorizeApp(pkg, packageManager)
                val lastUsed = packageLastUsed[pkg] ?: 0L
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    usageDurationMs = duration,
                    category = category,
                    icon = null, // Loaded on-demand in UI if needed
                    lastTimeUsedMs = lastUsed
                )
            }
            .sortedByDescending { it.usageDurationMs }

        val totalScreenTimeMs = validApps.sumOf { it.usageDurationMs }

        // 2. Compute category breakdowns
        val categoryTotals = mutableMapOf<AppCategory, Long>()
        for (app in validApps) {
            categoryTotals[app.category] = (categoryTotals[app.category] ?: 0L) + app.usageDurationMs
        }

        val categorySummaries = categoryTotals.map { (category, duration) ->
            val percentage = if (totalScreenTimeMs > 0) {
                (duration.toFloat() / totalScreenTimeMs) * 100f
            } else 0f
            CategoryUsageSummary(category, duration, percentage)
        }.sortedByDescending { it.totalDurationMs }

        // 3. Query granular events to find first and last device activity of the day
        val (firstActiveMs, lastActiveMs, recentApp) = queryGranularActivityEvents(startOfDay, now)

        val topUsedApp = validApps.firstOrNull()
        val recentCategory = recentApp?.let { AppCategoryManager.categorizeApp(it, packageManager) } ?: AppCategory.OTHER

        return DailyUsageSnapshot(
            totalScreenTimeMs = totalScreenTimeMs,
            appUsageList = validApps,
            categoryBreakdown = categorySummaries,
            topUsedApp = topUsedApp,
            firstActiveTimeMs = firstActiveMs,
            lastActiveTimeMs = lastActiveMs,
            recentForegroundApp = recentApp,
            recentAppCategory = recentCategory
        )
    }

    /**
     * Queries granular events to determine:
     * 1. Earliest significant activity today (wake up signal).
     * 2. Most recent device activity timestamp.
     * 3. Most recently resumed package.
     */
    private fun queryGranularActivityEvents(startOfDay: Long, now: Long): Triple<Long, Long, String?> {
        var firstActive = 0L
        var lastActive = 0L
        var recentApp: String? = null

        try {
            val events = usageStatsManager?.queryEvents(startOfDay, now)
            if (events != null) {
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)

                    val isInteraction = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                            event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE ||
                            event.eventType == UsageEvents.Event.USER_INTERACTION

                    if (isInteraction) {
                        if (firstActive == 0L && event.timeStamp >= startOfDay) {
                            firstActive = event.timeStamp
                        }
                        if (event.timeStamp > lastActive) {
                            lastActive = event.timeStamp
                        }
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                            event.packageName != context.packageName &&
                            !isExcludedSystemPackage(event.packageName)
                        ) {
                            recentApp = event.packageName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying usage events: ${e.message}")
        }

        return Triple(firstActive, lastActive, recentApp)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun isExcludedSystemPackage(pkg: String): Boolean {
        return pkg.startsWith("com.android.systemui") ||
                pkg.startsWith("com.google.android.inputmethod") ||
                pkg.startsWith("com.android.launcher") ||
                pkg.contains("launcher") ||
                pkg == "android"
    }

    private fun getEmptySnapshot(): DailyUsageSnapshot {
        return DailyUsageSnapshot(
            totalScreenTimeMs = 0L,
            appUsageList = emptyList(),
            categoryBreakdown = emptyList(),
            topUsedApp = null,
            firstActiveTimeMs = 0L,
            lastActiveTimeMs = 0L,
            recentForegroundApp = null,
            recentAppCategory = AppCategory.OTHER
        )
    }

    companion object {
        private const val TAG = "DigitalActivityMonitor"
    }
}
