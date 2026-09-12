package com.comai.contextengine

import android.content.Context
import android.util.Log
import com.comai.contextengine.context.ContextConfidenceEvaluator
import com.comai.contextengine.context.ContextNormalizer
import com.comai.contextengine.context.RoutineDetector
import com.comai.contextengine.context.RoutineDeviationAnalyzer
import com.comai.contextengine.context.UserState
import com.comai.contextengine.contract.ContractAssembler
import com.comai.contextengine.contract.ContractValidator
import com.comai.contextengine.contract.ContextContractMapper
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.models.ContextConfidence
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import com.comai.contextengine.providers.composite.CompositeDeviceContextProvider
import com.comai.contextengine.providers.interfaces.DeviceContextProvider
import com.comai.contextengine.providers.models.ActivityMovementState
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.contextengine.rules.RuleDefinition
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput

/**
 * Central Context Engine for Person 1.
 * Aggregates device signals from providers, normalizes data, determines broad user context,
 * evaluates routine deviations, calculates context confidence, and compresses context into
 * structured contracts ready for Person 2's AIEngine.
 */
import com.comai.contextengine.providers.mock.MockProviderFactory

class CentralContextEngine(
    private val context: Context? = null,
    private val provider: DeviceContextProvider = if (context != null) CompositeDeviceContextProvider.createDefault(context) else MockProviderFactory.createMockComposite(),
    private val database: ComaiDatabase? = if (context != null) ComaiDatabase.getInstance(context) else null
) {

    private val confidenceEvaluator = ContextConfidenceEvaluator()
    private val normalizer = ContextNormalizer()
    private val routineEngine = com.comai.contextengine.context.RoutineEngine(database)
    private val ruleEngine = RuleEngine()

    /**
     * Executes the end-to-end Context Engine pipeline deterministically.
     */
    suspend fun processContext(): ContextProcessingResult {
        // 1. Collect normalized provider outputs
        val deviceContext = provider.getContextData()

        // 2. Remove duplicate / unnecessary info and determine broad context
        val userContext = determineUserContext(deviceContext)

        // 3. Analyze routine context & detect deviations
        val routineContext = analyzeRoutineContext(deviceContext)

        // 4. Calculate context confidence
        val confidence = confidenceEvaluator.evaluateConfidence(deviceContext, routineContext)

        // 5. Build rule evaluation input & evaluate rules
        val evalInput = RuleEvaluationInput(
            currentTime = deviceContext.time.formattedTime,
            dayOfWeek = deviceContext.time.dayOfWeek,
            isWorkDay = deviceContext.time.isWorkDay,
            currentLocation = deviceContext.location.locationCategory,
            routineDeviation = routineContext.isRoutineDeviation
        )

        val ruleResult = ruleEngine.evaluate(evalInput)

        // 6. Compress result into structured SharedContextContract
        val task = ruleResult?.task ?: determineDefaultTask(userContext, routineContext)
        val systemRole = ruleResult?.systemRole ?: "Comai Proactive Context Engine"
        val constraints = ruleResult?.constraints ?: "Keep response empathetic, concise (max 2 sentences)."

        val userStateObj = UserState(
            activityType = deviceContext.activity.detectedState.name,
            awakeDurationMinutes = 30,
            wakeDetectedTimeMs = System.currentTimeMillis() - (30 * 60 * 1000L)
        )

        val (contract, jsonStr) = com.comai.contextengine.contract.ContextCompressor.compress(
            deviceContext = deviceContext,
            userContext = userContext,
            routineContext = routineContext,
            ruleResult = ruleResult
        )

        // 7. Map contract to Person 2's ContextInput boundary
        val contextInput = ContextContractMapper.toContextInput(contract)

        Log.i(TAG, "Context Processing Complete: broadTag=${userContext.broadContextTag}, deviation=${routineContext.isRoutineDeviation}, confidence=${confidence.overallConfidence}")

        return ContextProcessingResult(
            deviceContext = deviceContext,
            userContext = userContext,
            routineContext = routineContext,
            confidence = confidence,
            contract = contract,
            contextInput = contextInput
        )
    }

    private fun determineUserContext(deviceContext: DeviceContext): UserContext {
        val time = deviceContext.time
        val location = deviceContext.location
        val activity = deviceContext.activity
        val audio = deviceContext.audio
        val battery = deviceContext.battery

        val broadTag = when {
            location.locationCategory.equals("Office", ignoreCase = true) && time.isWorkDay && time.hour >= 18 -> "OFFICE_LATE_DEPARTURE"
            activity.detectedState == ActivityMovementState.IN_VEHICLE || location.locationCategory.equals("Commute", ignoreCase = true) -> "MORNING_COMMUTE"
            location.locationCategory.equals("Office", ignoreCase = true) && time.hour in 12..13 -> "LUNCH_BREAK"
            location.locationCategory.equals("Home", ignoreCase = true) && (time.hour >= 23 || time.hour < 6) -> "NIGHT_SLEEP"
            location.locationCategory.equals("Home", ignoreCase = true) && time.hour in 6..9 -> "HOME_MORNING"
            location.locationCategory.equals("Home", ignoreCase = true) -> "RESTING_AT_HOME"
            else -> "GENERAL_DAILY"
        }

        return UserContext(
            broadContextTag = broadTag,
            locationPlaceName = location.currentPlace?.name ?: location.locationCategory,
            movementSummary = activity.detectedState.name,
            deviceAudioSummary = audio.primaryOutputDevice.name,
            devicePowerSummary = if (battery.isCharging) "CHARGING (${battery.batteryLevel}%)" else "BATTERY (${battery.batteryLevel}%)",
            timeOfDayFormatted = time.formattedTime
        )
    }

    private suspend fun analyzeRoutineContext(deviceContext: DeviceContext): RoutineContext {
        val evaluation = routineEngine.evaluateRoutine(deviceContext)

        val routineState = when {
            !deviceContext.time.isWorkDay -> "OFF_DAY_ROUTINE"
            deviceContext.time.hour in 6..8 -> "MORNING_ROUTINE"
            deviceContext.time.hour in 9..17 -> "WORK_DAY_ROUTINE"
            deviceContext.time.hour in 18..22 -> "EVENING_ROUTINE"
            else -> "NIGHT_ROUTINE"
        }

        return RoutineContext(
            isWorkDay = deviceContext.time.isWorkDay,
            routineState = routineState,
            isRoutineDeviation = evaluation.routineDeviation,
            deviationMinutes = evaluation.deviationMinutes,
            routineDeviationScore = (evaluation.deviationMinutes / 120.0).coerceIn(0.0, 1.0),
            confidence = evaluation.confidence,
            deviationReason = evaluation.deviationReason
        )
    }

    private fun determineDefaultTask(userContext: UserContext, routineContext: RoutineContext): String {
        return when (userContext.broadContextTag) {
            "OFFICE_LATE_DEPARTURE" -> "OVERTIME_CHECKIN"
            "HOME_MORNING" -> "MORNING_ROUTINE_REMINDER"
            "LUNCH_BREAK" -> "LUNCH_CHECKIN"
            "NIGHT_SLEEP" -> "NIGHT_MEDICATION_REMINDER"
            else -> if (routineContext.isRoutineDeviation) "ROUTINE_DEVIATION_ALERT" else "GENERAL_CHECKIN"
        }
    }

    companion object {
        private const val TAG = "CentralContextEngine"
    }
}
