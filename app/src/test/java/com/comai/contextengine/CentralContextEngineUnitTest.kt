package com.comai.contextengine

import com.comai.contextengine.context.ContextNormalizer
import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.mock.MockActivityContextProvider
import com.comai.contextengine.providers.mock.MockBatteryContextProvider
import com.comai.contextengine.providers.mock.MockGeofenceContextProvider
import com.comai.contextengine.providers.mock.MockLocationContextProvider
import com.comai.contextengine.providers.mock.MockNetworkContextProvider
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.providers.mock.MockRoutineContextProvider
import com.comai.contextengine.providers.mock.MockTimeContextProvider
import com.comai.contextengine.providers.models.ActivityContextData
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.BatteryContextData
import com.comai.contextengine.providers.models.GeofenceContextData
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData
import com.comai.contextengine.providers.models.NetworkContextData
import com.comai.contextengine.providers.models.RoutineContextData
import com.comai.contextengine.providers.models.RoutinePlace
import com.comai.contextengine.providers.models.TimeContextData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CentralContextEngineUnitTest {

    @Test
    fun testCorrectContextWhenAllSignalsAvailable() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(9, 0, "MONDAY", true, "09:00"),
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
            ),
            routine = RoutineContextData("WORK_DAY_ROUTINE", 0.0, false),
            battery = BatteryContextData(90, false, "NONE"),
            network = NetworkContextData("WIFI", true, false)
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull(result)
        assertEquals("Office", result.deviceContext.location.locationCategory)
        assertEquals("09:00", result.deviceContext.time.formattedTime)
        assertEquals(ConfidenceLevel.HIGH, result.confidence.confidenceLevel)
        assertFalse(result.routineContext.isRoutineDeviation)
    }

    @Test
    fun testMissingOrUnavailableSignals() = runTest {
        val mockLocationProvider = MockLocationContextProvider()
        mockLocationProvider.simulatePermissionDenied()

        val mockActivityProvider = MockActivityContextProvider()
        mockActivityProvider.simulatePermissionDenied()

        val mockProvider = CompositeDeviceContextProvider(
            timeProvider = MockTimeContextProvider(),
            locationProvider = mockLocationProvider,
            geofenceProvider = MockGeofenceContextProvider(),
            activityProvider = mockActivityProvider,
            routineProvider = MockRoutineContextProvider(),
            batteryProvider = MockBatteryContextProvider(),
            networkProvider = MockNetworkContextProvider(),
            audioProvider = com.comai.contextengine.providers.mock.MockAudioContextProvider(),
            deviceStateProvider = com.comai.contextengine.providers.mock.MockDeviceStateContextProvider()
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull("Engine must not crash when permissions are missing", result)
        assertTrue(result.confidence.missingSignals.isNotEmpty())
        assertTrue("Confidence score must be penalized for missing permissions", result.confidence.confidenceScore < 0.90f)
    }

    @Test
    fun testConflictingSignals() = runTest {
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

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull(result)
        assertTrue("Conflicting signals must be recorded or penalize confidence", result.confidence.confidenceScore < 0.90f || result.confidence.conflictingSignals.isNotEmpty())
    }

    @Test
    fun testCorrectContextNormalization() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(18, 43, "FRIDAY", true, "18:43"),
            location = LocationContextData(
                latitude = 37.7890,
                longitude = -122.4010,
                locationCategory = "Office",
                accuracyMeters = 3f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            )
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        val normalized = ContextNormalizer().normalizeDeviceContext(result.deviceContext)
        assertEquals("18:43", normalized.time.formattedTime)
        assertEquals("Office", normalized.location.locationCategory)
        assertEquals("OFFICE_LATE_DEPARTURE", result.userContext.broadContextTag)
    }
}
