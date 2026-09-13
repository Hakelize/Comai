package com.comai.digitalactivity

import com.comai.digitalactivity.data.AppLimitEntity
import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.AppUsageInfo
import com.comai.digitalactivity.model.DailyUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductivityLimitTest {

    @Test
    fun testLimitExceededCalculation() {
        val appUsage = listOf(
            AppUsageInfo(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                usageDurationMs = 65 * 60 * 1000L, // 65 mins
                category = AppCategory.ENTERTAINMENT
            ),
            AppUsageInfo(
                packageName = "com.instagram.android",
                appName = "Instagram",
                usageDurationMs = 25 * 60 * 1000L, // 25 mins
                category = AppCategory.SOCIAL
            )
        )

        val usageMap = appUsage.associate { it.packageName to it.usageMinutes }

        val youtubeLimit = AppLimitEntity(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            dailyLimitMinutes = 60,
            isEnabled = true
        )

        val instagramLimit = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyLimitMinutes = 30,
            isEnabled = true
        )

        val youtubeUsed = usageMap[youtubeLimit.packageName] ?: 0L
        val instagramUsed = usageMap[instagramLimit.packageName] ?: 0L

        assertTrue(youtubeUsed >= youtubeLimit.dailyLimitMinutes) // 65 >= 60: EXCEEDED
        assertFalse(instagramUsed >= instagramLimit.dailyLimitMinutes) // 25 < 30: OK
    }
}
