package com.comai.digitalactivity

import com.comai.digitalactivity.intelligence.ActivityInferenceEngine
import com.comai.digitalactivity.intelligence.DeviceActivityState
import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.DailyUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ActivityInferenceEngineTest {

    private fun createSnapshot(firstActiveMs: Long, lastActiveMs: Long): DailyUsageSnapshot {
        return DailyUsageSnapshot(
            totalScreenTimeMs = 3600_000L,
            appUsageList = emptyList(),
            categoryBreakdown = emptyList(),
            topUsedApp = null,
            firstActiveTimeMs = firstActiveMs,
            lastActiveTimeMs = lastActiveMs,
            recentForegroundApp = "com.whatsapp",
            recentAppCategory = AppCategory.COMMUNICATION
        )
    }

    @Test
    fun testActiveStateWhenRecentActivityWithin5Minutes() {
        val now = System.currentTimeMillis()
        val snapshot = createSnapshot(now - 100000, now - (2 * 60 * 1000)) // 2 mins ago

        val result = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            hasPermission = true,
            isMonitoringEnabled = true
        )

        assertEquals(DeviceActivityState.ACTIVE, result.state)
        assertEquals(0.95, result.confidence, 0.01)
        assertEquals("RECENT_DEVICE_ACTIVITY", result.reason)
    }

    @Test
    fun testRecentlyActiveStateWithin20Minutes() {
        val now = System.currentTimeMillis()
        val snapshot = createSnapshot(now - 500000, now - (15 * 60 * 1000)) // 15 mins ago

        val result = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            hasPermission = true,
            isMonitoringEnabled = true
        )

        assertEquals(DeviceActivityState.RECENTLY_ACTIVE, result.state)
        assertEquals(0.85, result.confidence, 0.01)
        assertEquals("ACTIVITY_WITHIN_LAST_20_MINUTES", result.reason)
    }

    @Test
    fun testLikelyAwakeStateInMorningWindow() {
        // Set time to 7:30 AM
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val now = cal.timeInMillis

        // First activity at 7:05 AM, last active at 7:15 AM (15 min ago)
        val firstActive = now - (25 * 60 * 1000)
        val lastActive = now - (15 * 60 * 1000)
        val snapshot = createSnapshot(firstActive, lastActive)

        val result = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            configuredWakeHour = 7,
            configuredWakeMinute = 0,
            hasPermission = true,
            isMonitoringEnabled = true
        )

        // Since elapsedMinutes = 15 <= 20, RECENTLY_ACTIVE or LIKELY_AWAKE
        // If elapsed is 30 mins (between 21 and 90 mins in morning):
        val snapshot30 = createSnapshot(firstActive, now - (30 * 60 * 1000))
        val result30 = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot30,
            configuredWakeHour = 7,
            configuredWakeMinute = 0,
            hasPermission = true,
            isMonitoringEnabled = true
        )

        assertEquals(DeviceActivityState.LIKELY_AWAKE, result30.state)
        assertTrue(result30.confidence >= 0.78)
        assertEquals("FIRST_MORNING_ACTIVITY", result30.reason)
    }

    @Test
    fun testLikelyAsleepStateDuringNightInactivity() {
        // Set time to 1:30 AM
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val now = cal.timeInMillis

        // Last activity was 11:30 PM (2 hours = 120 mins ago)
        val lastActive = now - (120 * 60 * 1000)
        val snapshot = createSnapshot(now - (5 * 3600 * 1000), lastActive)

        val result = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            configuredSleepHour = 23,
            configuredSleepMinute = 0,
            hasPermission = true,
            isMonitoringEnabled = true
        )

        assertEquals(DeviceActivityState.LIKELY_ASLEEP, result.state)
        assertTrue(result.confidence >= 0.80)
        assertEquals("PROLONGED_NIGHT_INACTIVITY", result.reason)
    }

    @Test
    fun testUnknownWhenMonitoringDisabledOrNoPermission() {
        val now = System.currentTimeMillis()
        val snapshot = createSnapshot(now - 10000, now - 1000)

        val resultDisabled = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            isMonitoringEnabled = false
        )
        assertEquals(DeviceActivityState.UNKNOWN, resultDisabled.state)
        assertEquals("MONITORING_DISABLED", resultDisabled.reason)

        val resultNoPerm = ActivityInferenceEngine.inferState(
            nowMs = now,
            snapshot = snapshot,
            isMonitoringEnabled = true,
            hasPermission = false
        )
        assertEquals(DeviceActivityState.UNKNOWN, resultNoPerm.state)
        assertEquals("NO_USAGE_PERMISSION", resultNoPerm.reason)
    }
}
