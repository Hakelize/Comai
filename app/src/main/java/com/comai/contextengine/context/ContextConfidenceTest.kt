package com.comai.contextengine.context

import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.models.ContextConfidence
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.GeofenceContextData
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData

/**
 * Unit Test Harness for Context Confidence Evaluation.
 * Exercises the 5 key test scenarios:
 * 1. All signals available
 * 2. Location unavailable
 * 3. Activity unavailable
 * 4. Conflicting signals
 * 5. Stale data
 */
object ContextConfidenceTest {

    fun runAllTests(): List<Pair<String, ContextConfidence>> {
        val evaluator = ContextConfidenceEvaluator()
        val results = mutableListOf<Pair<String, ContextConfidence>>()
        val nowMs = System.currentTimeMillis()

        // 1. All Signals Available (HIGH confidence)
        val composite1 = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = 37.7749,
                longitude = -122.4194,
                locationCategory = "Home",
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            ),
            geofence = GeofenceContextData(currentZone = "HOME_ZONE", isInsideGeofence = true),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.STATIONARY,
                activityType = "STILL",
                confidencePercentage = 100,
                isPermissionGranted = true,
                isActivityAvailable = true
            )
        )
        val ctx1 = composite1.getContextData()
        val res1 = evaluator.evaluateConfidence(ctx1, RoutineContext(), nowMs)
        results.add(Pair("Test 1: All Signals Available (Expected: HIGH)", res1))

        // 2. Location Unavailable (Permission Denied)
        val composite2 = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                isPermissionGranted = false,
                availabilityState = LocationAvailabilityState.PERMISSION_DENIED,
                locationCategory = "UNKNOWN"
            )
        )
        val ctx2 = composite2.getContextData()
        val res2 = evaluator.evaluateConfidence(ctx2, RoutineContext(), nowMs)
        results.add(Pair("Test 2: Location Unavailable (Expected: MEDIUM/LOW, capped)", res2))

        // 3. Activity Unavailable (Permission Denied)
        val composite3 = MockProviderFactory.createMockComposite(
            activity = ActivityContextData(
                detectedState = ActivityMovementState.UNKNOWN,
                activityType = "UNKNOWN",
                confidencePercentage = 0,
                isPermissionGranted = false,
                isActivityAvailable = false
            )
        )
        val ctx3 = composite3.getContextData()
        val res3 = evaluator.evaluateConfidence(ctx3, RoutineContext(), nowMs)
        results.add(Pair("Test 3: Activity Unavailable (Expected: MEDIUM/LOW, capped)", res3))

        // 4. Conflicting Signals (Location = Home vs Geofence = OFFICE_ZONE)
        val composite4 = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = 37.7749,
                longitude = -122.4194,
                locationCategory = "Home",
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            ),
            geofence = GeofenceContextData(currentZone = "OFFICE_ZONE", isInsideGeofence = true),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.IN_VEHICLE,
                activityType = "IN_VEHICLE",
                confidencePercentage = 95,
                isPermissionGranted = true,
                isActivityAvailable = true
            )
        )
        val ctx4 = composite4.getContextData()
        val res4 = evaluator.evaluateConfidence(ctx4, RoutineContext(), nowMs)
        results.add(Pair("Test 4: Conflicting Signals (Expected: Conflicting Signals logged, Level Capped)", res4))

        // 5. Stale Data (Timestamp 10 minutes old)
        val staleTimestampMs = nowMs - (10 * 60 * 1000L) // 10 minutes ago
        val composite5 = MockProviderFactory.createMockComposite()
        val ctx5 = composite5.getContextData().copy(timestampMs = staleTimestampMs)
        val res5 = evaluator.evaluateConfidence(ctx5, RoutineContext(), nowMs)
        results.add(Pair("Test 5: Stale Data (Expected: Stale signal penalty)", res5))

        return results
    }

    fun verifyAssertions(): Boolean {
        val testResults = runAllTests()

        val t1 = testResults[0].second.confidenceLevel == ConfidenceLevel.HIGH
        val t2 = testResults[1].second.confidenceLevel != ConfidenceLevel.HIGH && testResults[1].second.missingSignals.any { it.contains("Location") }
        val t3 = testResults[2].second.confidenceLevel != ConfidenceLevel.HIGH && testResults[2].second.missingSignals.any { it.contains("Activity") }
        val t4 = testResults[3].second.conflictingSignals.isNotEmpty() && testResults[3].second.confidenceLevel != ConfidenceLevel.HIGH
        val t5 = testResults[4].second.missingSignals.any { it.contains("Stale") || it.contains("minutes old") }

        return t1 && t2 && t3 && t4 && t5
    }
}
