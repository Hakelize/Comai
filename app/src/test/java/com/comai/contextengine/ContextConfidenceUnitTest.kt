package com.comai.contextengine

import com.comai.contextengine.context.ContextConfidenceEvaluator
import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.mock.MockActivityContextProvider
import com.comai.contextengine.providers.mock.MockLocationContextProvider
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.GeofenceContextData
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData
import com.comai.contextengine.providers.models.RoutinePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextConfidenceUnitTest {

    private lateinit var evaluator: ContextConfidenceEvaluator

    @Before
    fun setUp() {
        evaluator = ContextConfidenceEvaluator()
    }

    @Test
    fun testHighConfidenceWhenAllSignalsAvailable() {
        val mockProvider = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = 37.7890,
                longitude = -122.4010,
                locationCategory = "Office",
                accuracyMeters = 5f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE,
                currentPlace = RoutinePlace("office_place", "Office", "OFFICE", 37.7890, -122.4010, 200f)
            ),
            geofence = GeofenceContextData("OFFICE_ZONE", true, null, 0f),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.STATIONARY,
                activityType = "STILL",
                confidencePercentage = 100,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = true
            )
        )

        val deviceContext = mockProvider.getContextData()
        val routineContext = RoutineContext(
            isWorkDay = true,
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = false,
            deviationMinutes = 0,
            routineDeviationScore = 0.0,
            confidence = 0.95f,
            deviationReason = "Normal"
        )

        val result = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertNotNull(result)
        assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel)
        assertTrue(result.confidenceScore >= 0.85f)
        assertTrue(result.supportingSignals.isNotEmpty())
    }

    @Test
    fun testMediumConfidenceWhenMinorSignalsMissing() {
        val mockProvider = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = 37.7890,
                longitude = -122.4010,
                locationCategory = "Office",
                accuracyMeters = 150f, // Moderate GPS accuracy
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            )
        )

        val deviceContext = mockProvider.getContextData()
        val routineContext = RoutineContext(
            isWorkDay = true,
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = false,
            deviationMinutes = 0,
            routineDeviationScore = 0.0,
            confidence = 0.75f,
            deviationReason = "Moderate accuracy"
        )

        val result = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertNotNull(result)
        assertTrue(result.confidenceScore < 0.90f)
    }

    @Test
    fun testLowConfidenceWhenMajorSignalsMissing() {
        val mockLocation = MockLocationContextProvider()
        mockLocation.simulatePermissionDenied()

        val mockActivity = MockActivityContextProvider()
        mockActivity.simulatePermissionDenied()

        val mockProvider = CompositeDeviceContextProvider(
            timeProvider = com.comai.contextengine.providers.mock.MockTimeContextProvider(),
            locationProvider = mockLocation,
            geofenceProvider = com.comai.contextengine.providers.mock.MockGeofenceContextProvider(),
            activityProvider = mockActivity,
            routineProvider = com.comai.contextengine.providers.mock.MockRoutineContextProvider(),
            batteryProvider = com.comai.contextengine.providers.mock.MockBatteryContextProvider(),
            networkProvider = com.comai.contextengine.providers.mock.MockNetworkContextProvider(),
            audioProvider = com.comai.contextengine.providers.mock.MockAudioContextProvider(),
            deviceStateProvider = com.comai.contextengine.providers.mock.MockDeviceStateContextProvider()
        )

        val deviceContext = mockProvider.getContextData()
        val routineContext = RoutineContext(
            isWorkDay = true,
            routineState = "UNKNOWN",
            isRoutineDeviation = false,
            deviationMinutes = 0,
            routineDeviationScore = 0.0,
            confidence = 0.4f,
            deviationReason = "Permissions missing"
        )

        val result = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertNotNull(result)
        assertEquals(ConfidenceLevel.LOW, result.confidenceLevel)
        assertTrue(result.missingSignals.isNotEmpty())
    }

    @Test
    fun testMissingSignalsTracking() {
        val mockLocation = MockLocationContextProvider()
        mockLocation.simulatePermissionDenied()

        val mockProvider = CompositeDeviceContextProvider(
            timeProvider = com.comai.contextengine.providers.mock.MockTimeContextProvider(),
            locationProvider = mockLocation,
            geofenceProvider = com.comai.contextengine.providers.mock.MockGeofenceContextProvider(),
            activityProvider = com.comai.contextengine.providers.mock.MockActivityContextProvider(),
            routineProvider = com.comai.contextengine.providers.mock.MockRoutineContextProvider(),
            batteryProvider = com.comai.contextengine.providers.mock.MockBatteryContextProvider(),
            networkProvider = com.comai.contextengine.providers.mock.MockNetworkContextProvider(),
            audioProvider = com.comai.contextengine.providers.mock.MockAudioContextProvider(),
            deviceStateProvider = com.comai.contextengine.providers.mock.MockDeviceStateContextProvider()
        )

        val deviceContext = mockProvider.getContextData()
        val routineContext = RoutineContext(true, "WORK_DAY_ROUTINE", false, 0, 0.0, 0.8f, "Normal")

        val result = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertTrue(result.missingSignals.any { it.contains("LOCATION", ignoreCase = true) })
    }

    @Test
    fun testConflictingSignalsTracking() {
        val mockProvider = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = 37.7749,
                longitude = -122.4194,
                locationCategory = "Home",
                accuracyMeters = 800f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            ),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.IN_VEHICLE,
                activityType = "IN_VEHICLE",
                confidencePercentage = 95,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = true
            )
        )

        val deviceContext = mockProvider.getContextData()
        val routineContext = RoutineContext(true, "WORK_DAY_ROUTINE", true, 45, 0.5, 0.7f, "Conflict")

        val result = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertTrue("Conflicting signals should be present or reduce score below HIGH", result.confidenceLevel != ConfidenceLevel.HIGH || result.conflictingSignals.isNotEmpty())
    }
}
