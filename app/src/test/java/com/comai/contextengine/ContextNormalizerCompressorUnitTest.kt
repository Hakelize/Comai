package com.comai.contextengine

import com.comai.contextengine.context.ContextNormalizer
import com.comai.contextengine.contract.ContextCompressor
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData
import com.comai.contextengine.providers.models.TimeContextData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextNormalizerCompressorUnitTest {

    private lateinit var normalizer: ContextNormalizer

    @Before
    fun setUp() {
        normalizer = ContextNormalizer()
    }

    @Test
    fun testCorrectConversionOfRawContext() {
        val mockProvider = MockProviderFactory.createMockComposite(
            time = TimeContextData(14, 30, "WEDNESDAY", true, "14:30"),
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

        val deviceContext = mockProvider.getContextData()
        val normalized = normalizer.normalizeDeviceContext(deviceContext)

        assertNotNull(normalized)
        assertEquals("14:30", normalized.time.formattedTime)
        assertEquals("Office", normalized.location.locationCategory)
    }

    @Test
    fun testCorrectRoutineDeviationValueInCompressedContext() {
        val mockProvider = MockProviderFactory.createMockComposite()
        val deviceContext = mockProvider.getContextData()

        val userContext = UserContext(
            broadContextTag = "OFFICE_LATE_DEPARTURE",
            locationPlaceName = "Office",
            movementSummary = "STATIONARY",
            deviceAudioSummary = "NONE",
            devicePowerSummary = "BATTERY (85%)",
            timeOfDayFormatted = "18:45"
        )

        val routineContext = RoutineContext(
            isWorkDay = true,
            routineState = "WORK_DAY_ROUTINE",
            isRoutineDeviation = true,
            deviationMinutes = 75,
            routineDeviationScore = 0.625,
            confidence = 0.9f,
            deviationReason = "Staying late at office"
        )

        val (contract, jsonStr) = ContextCompressor.compress(
            deviceContext = deviceContext,
            userContext = userContext,
            routineContext = routineContext,
            ruleResult = null
        )

        assertTrue(contract.contextSignals.routineDeviation)
        assertTrue(jsonStr.contains("routine_deviation"))
    }

    @Test
    fun testMissingValuesHandledSafely() {
        val mockProvider = MockProviderFactory.createMockComposite(
            location = LocationContextData(
                latitude = null,
                longitude = null,
                locationCategory = "UNKNOWN",
                accuracyMeters = 0f,
                isPermissionGranted = false,
                isLocationEnabled = false,
                availabilityState = LocationAvailabilityState.PERMISSION_DENIED
            )
        )

        val deviceContext = mockProvider.getContextData()
        val normalized = normalizer.normalizeDeviceContext(deviceContext)

        assertNotNull(normalized)
        assertEquals("UNKNOWN", normalized.location.locationCategory)
    }

    @Test
    fun testNoUnnecessaryDataIncludedInCompressedContract() {
        val mockProvider = MockProviderFactory.createMockComposite()
        val deviceContext = mockProvider.getContextData()

        val userContext = UserContext("HOME_MORNING", "Home", "STILL", "SPEAKER", "BATTERY (90%)", "07:30")
        val routineContext = RoutineContext(true, "MORNING_ROUTINE", false, 0, 0.0, 0.9f, "Normal")

        val (contract, jsonStr) = ContextCompressor.compress(deviceContext, userContext, routineContext, null)

        assertFalse("Raw GPS lat/long must not leak into compressed JSON contract", jsonStr.contains("37.7749"))
        assertFalse("Raw sensor frequency must not leak into compressed JSON contract", jsonStr.contains("accuracyMeters"))
    }
}
