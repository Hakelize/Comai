package com.comai.contextengine

import com.comai.contextengine.context.ContextConfidenceEvaluator
import com.comai.contextengine.contract.ContractValidator
import com.comai.contextengine.contract.ContextContractMapper
import com.comai.contextengine.models.ConfidenceLevel
import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.mock.MockActivityContextProvider
import com.comai.contextengine.providers.mock.MockAudioContextProvider
import com.comai.contextengine.providers.mock.MockBatteryContextProvider
import com.comai.contextengine.providers.mock.MockDeviceStateContextProvider
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
import com.comai.contextengine.rules.RuleEngine
import com.comai.engine.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RamModuleIntegrationTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        ruleEngine.loadDefaultMvpRules()
    }

    @Test
    fun scenario1_NormalMorningRoutineAtHome() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(7, 30, "MONDAY", true, "07:30"),
            location = LocationContextData(
                latitude = 37.7749,
                longitude = -122.4194,
                locationCategory = "Home",
                accuracyMeters = 5f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE,
                currentPlace = RoutinePlace("home_place", "Home", "HOME", 37.7749, -122.4194, 200f)
            ),
            geofence = GeofenceContextData("HOME_ZONE", true, null, 0f),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.STATIONARY,
                activityType = "STILL",
                confidencePercentage = 100,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = true
            ),
            routine = RoutineContextData("MORNING_ROUTINE", 0.0, false),
            battery = BatteryContextData(95, false, "NONE"),
            network = NetworkContextData("WIFI", true, false)
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull("Result must not be null", result)
        assertEquals("HOME_MORNING", result.userContext.broadContextTag)
        assertFalse("Routine deviation must be false for normal morning", result.routineContext.isRoutineDeviation)
        assertEquals(ConfidenceLevel.HIGH, result.confidence.confidenceLevel)

        ContractValidator.validate(result.contract)
        val json = ContractValidator.toJson(result.contract)
        assertTrue("JSON contract must contain task", json.contains("task"))

        val contextInput = ContextContractMapper.toContextInput(result.contract)
        assertEquals("07:30", contextInput.contextSignals.time)
        assertEquals("Home", contextInput.contextSignals.location)
        assertFalse(contextInput.contextSignals.routineDeviation)
    }

    @Test
    fun scenario2_UserCommuting() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(8, 15, "MONDAY", true, "08:15"),
            location = LocationContextData(
                latitude = 37.7800,
                longitude = -122.4100,
                locationCategory = "Commute",
                accuracyMeters = 10f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            ),
            geofence = GeofenceContextData("NONE", false, null, 1500f),
            activity = ActivityContextData(
                detectedState = ActivityMovementState.IN_VEHICLE,
                activityType = "IN_VEHICLE",
                confidencePercentage = 95,
                isPermissionGranted = true,
                isHardwareSupported = true,
                isActivityAvailable = true
            ),
            routine = RoutineContextData("WORK_DAY_ROUTINE", 0.0, false)
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertEquals("MORNING_COMMUTE", result.userContext.broadContextTag)
        assertEquals("IN_VEHICLE", result.userContext.movementSummary)
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario3_UserReachesOffice() = runTest {
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
            )
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertTrue(result.routineContext.isWorkDay)
        assertFalse(result.routineContext.isRoutineDeviation)
        assertEquals("Office", result.deviceContext.location.locationCategory)
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario4_UserLeavesOfficeLaterThanNormal() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(18, 45, "MONDAY", true, "18:45"),
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

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertEquals("OFFICE_LATE_DEPARTURE", result.userContext.broadContextTag)
        assertTrue("Routine deviation must be true when staying late at office", result.routineContext.isRoutineDeviation)
        assertEquals("OVERTIME_CHECKIN", result.contract.task)
        assertTrue(result.contextInput.contextSignals.routineDeviation)
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario5_LocationPermissionDenied() = runTest {
        val mockLocationProvider = MockLocationContextProvider()
        mockLocationProvider.simulatePermissionDenied()

        val mockProvider = CompositeDeviceContextProvider(
            timeProvider = MockTimeContextProvider(),
            locationProvider = mockLocationProvider,
            geofenceProvider = MockGeofenceContextProvider(),
            activityProvider = MockActivityContextProvider(),
            routineProvider = MockRoutineContextProvider(),
            batteryProvider = MockBatteryContextProvider(),
            networkProvider = MockNetworkContextProvider(),
            audioProvider = MockAudioContextProvider(),
            deviceStateProvider = MockDeviceStateContextProvider()
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull("Pipeline must complete safely without crash when location permission denied", result)
        assertTrue(result.confidence.missingSignals.any { it.contains("LOCATION", ignoreCase = true) })
        assertTrue("Confidence level must be penalized when location permission denied", result.confidence.confidenceScore < 0.90f)
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario6_ActivityPermissionUnavailable() = runTest {
        val mockActivityProvider = MockActivityContextProvider()
        mockActivityProvider.simulatePermissionDenied()

        val mockProvider = CompositeDeviceContextProvider(
            timeProvider = MockTimeContextProvider(),
            locationProvider = MockLocationContextProvider(),
            geofenceProvider = MockGeofenceContextProvider(),
            activityProvider = mockActivityProvider,
            routineProvider = MockRoutineContextProvider(),
            batteryProvider = MockBatteryContextProvider(),
            networkProvider = MockNetworkContextProvider(),
            audioProvider = MockAudioContextProvider(),
            deviceStateProvider = MockDeviceStateContextProvider()
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull("Pipeline must complete safely without crash when activity permission unavailable", result)
        assertTrue(result.confidence.missingSignals.any { it.contains("ACTIVITY", ignoreCase = true) })
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario7_NetworkUnavailable() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            network = NetworkContextData("NONE", false, false)
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull("Offline pipeline must complete safely on-device", result)
        assertFalse(result.deviceContext.network.isConnected)
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario8_BatteryLow() = runTest {
        val mockProvider = MockProviderFactory.createMockComposite(
            battery = BatteryContextData(12, false, "NONE")
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        assertNotNull(result)
        assertEquals(12, result.deviceContext.battery.batteryLevel)
        assertTrue(result.userContext.devicePowerSummary.contains("12%"))
        ContractValidator.validate(result.contract)
    }

    @Test
    fun scenario9_MultipleSignalsConflict() = runTest {
        val evaluator = ContextConfidenceEvaluator()

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
        val routineContext = com.comai.contextengine.models.RoutineContext(
            isWorkDay = true,
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = true,
            deviationMinutes = 45,
            routineDeviationScore = 0.5,
            confidence = 0.8f,
            deviationReason = "Conflicting location and movement speed"
        )

        val confidenceResult = evaluator.evaluateConfidence(deviceContext, routineContext)

        assertNotNull(confidenceResult)
        assertTrue("Conflicting signals must penalize confidence score below HIGH", confidenceResult.confidenceScore < 0.90f)
    }

    @Test
    fun scenario10_BackgroundExecutionDecoupledFromUI() = runTest {
        val mockEngine = MockEngine()
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(18, 45, "MONDAY", true, "18:45"),
            location = LocationContextData(
                latitude = 37.7890,
                longitude = -122.4010,
                locationCategory = "Office",
                accuracyMeters = 5f,
                isPermissionGranted = true,
                isLocationEnabled = true,
                availabilityState = LocationAvailabilityState.AVAILABLE
            )
        )

        val centralEngine = CentralContextEngine(provider = mockProvider)
        val result = centralEngine.processContext()

        val contextInput = ContextContractMapper.toContextInput(result.contract)
        val aiResponse = mockEngine.process(contextInput)

        assertNotNull("AI Engine response must be generated headlessly without UI", aiResponse)
        assertEquals("check_in", aiResponse.action)
        assertEquals("OVERTIME_CHECKIN", contextInput.task)
        assertEquals("Comai Proactive Context Engine", contextInput.systemRole)
        assertEquals("18:45", contextInput.contextSignals.time)
        assertEquals("Office", contextInput.contextSignals.location)
        assertTrue(contextInput.contextSignals.routineDeviation)
    }
}
