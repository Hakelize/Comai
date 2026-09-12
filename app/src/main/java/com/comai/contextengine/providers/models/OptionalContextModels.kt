package com.comai.contextengine.providers.models

import com.comai.contextengine.providers.annotations.AndroidRestricted
import com.comai.contextengine.providers.annotations.OptionalProvider
import com.comai.contextengine.providers.annotations.PermissionDependent

/**
 * Structured model for optional Screen Context.
 * Clearly marked as OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED.
 * Does NOT claim full screen scraping or Accessibility Service access.
 */
@OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
@PermissionDependent("android.permission.PACKAGE_USAGE_STATS")
@AndroidRestricted("No Accessibility Service full-screen scraping; basic app category and orientation only")
data class ScreenContextData(
    val isAvailable: Boolean = false,
    val isScreenOn: Boolean = true,
    val activeAppPackageName: String? = null,
    val activeAppCategory: String = "UNKNOWN", // e.g. "SOCIAL", "PRODUCTIVITY", "MEDIA", "UNKNOWN"
    val screenOrientation: String = "PORTRAIT",
    val capabilityNotice: String = "OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED"
)

/**
 * Structured model for optional Notification Context.
 * Clearly marked as OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED.
 * Requires user NotificationListenerAccess if enabled in future.
 */
@OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
@PermissionDependent("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
@AndroidRestricted("Requires user to manually grant Notification Listener Access in System Settings")
data class NotificationContextData(
    val isAvailable: Boolean = false,
    val hasNotificationListenerPermission: Boolean = false,
    val activeNotificationCount: Int = 0,
    val hasUnreadMessagingNotification: Boolean = false,
    val recentNotificationCategories: List<String> = emptyList(),
    val capabilityNotice: String = "OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED"
)

/**
 * Structured model for optional App Context.
 * Clearly marked as OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED.
 * Uses UsageStatsManager if enabled in future.
 */
@OptionalProvider("OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED")
@PermissionDependent("android.permission.PACKAGE_USAGE_STATS")
@AndroidRestricted("Requires PACKAGE_USAGE_STATS system permission")
data class AppContextData(
    val isAvailable: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val foregroundPackageName: String? = null,
    val appUsageCategory: String = "UNKNOWN",
    val sessionDurationMinutes: Long = 0,
    val capabilityNotice: String = "OPTIONAL / PERMISSION-DEPENDENT / ANDROID-RESTRICTED"
)
