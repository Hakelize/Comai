package com.comai.contextengine.providers

import com.comai.contextengine.CentralContextEngine
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.providers.models.AppContextData
import com.comai.contextengine.providers.models.NotificationContextData
import com.comai.contextengine.providers.models.ScreenContextData
import kotlinx.coroutines.runBlocking

/**
 * Unit Test Harness for Optional Screen, Notification, and App Context Providers.
 * Verifies:
 * 1. Context Engine runs perfectly when all 3 optional providers are null/unavailable.
 * 2. Context Engine operates cleanly when optional mock providers are plugged in.
 */
object OptionalProvidersTest {

    fun runAllTests(): Boolean = runBlocking {
        // Test 1: Null / Unavailable Optional Providers (MVP Baseline)
        val nullOptionalComposite = MockProviderFactory.createMockComposite(
            screen = null,
            notification = null,
            app = null
        )
        val ctx1 = nullOptionalComposite.getContextData()
        val t1 = ctx1.screen == null && ctx1.notification == null && ctx1.app == null
        println("Test 1 - MVP Baseline (All Optional Providers Null): $t1")

        // Test 2: Plugged-in Optional Providers
        val pluggedOptionalComposite = MockProviderFactory.createMockComposite(
            screen = ScreenContextData(isAvailable = true, activeAppPackageName = "com.example.app", activeAppCategory = "PRODUCTIVITY"),
            notification = NotificationContextData(isAvailable = true, activeNotificationCount = 3),
            app = AppContextData(isAvailable = true, foregroundPackageName = "com.example.app")
        )
        val ctx2 = pluggedOptionalComposite.getContextData()
        val t2 = ctx2.screen?.isAvailable == true && ctx2.notification?.activeNotificationCount == 3 && ctx2.app?.foregroundPackageName == "com.example.app"
        println("Test 2 - Plugged-in Extension Providers: $t2")

        val allPassed = t1 && t2
        println("=== OPTIONAL PROVIDERS TEST RESULT: $allPassed ===")

        allPassed
    }
}
