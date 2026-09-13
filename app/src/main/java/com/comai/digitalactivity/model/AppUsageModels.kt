package com.comai.digitalactivity.model

import android.graphics.drawable.Drawable

/**
 * Detailed usage model for an individual application.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageDurationMs: Long,
    val category: AppCategory,
    val icon: Drawable? = null,
    val lastTimeUsedMs: Long = 0L
) {
    val usageMinutes: Long get() = usageDurationMs / (1000 * 60)
    val usageFormatted: String get() = formatDuration(usageDurationMs)

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalMinutes = durationMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "<1m"
            }
        }
    }
}

/**
 * Aggregated category usage summary.
 */
data class CategoryUsageSummary(
    val category: AppCategory,
    val totalDurationMs: Long,
    val percentage: Float // 0.0 to 100.0
) {
    val durationFormatted: String get() = AppUsageInfo.formatDuration(totalDurationMs)
}

/**
 * Snapshot of the user's daily usage.
 */
data class DailyUsageSnapshot(
    val totalScreenTimeMs: Long,
    val appUsageList: List<AppUsageInfo>,
    val categoryBreakdown: List<CategoryUsageSummary>,
    val topUsedApp: AppUsageInfo?,
    val firstActiveTimeMs: Long,
    val lastActiveTimeMs: Long,
    val recentForegroundApp: String?,
    val recentAppCategory: AppCategory
) {
    val totalScreenTimeFormatted: String get() = AppUsageInfo.formatDuration(totalScreenTimeMs)
}
