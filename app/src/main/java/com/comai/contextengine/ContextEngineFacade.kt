package com.comai.contextengine

import android.content.Context
import com.comai.contextengine.context.ContextConfidenceEvaluator
import com.comai.contextengine.context.ContextNormalizer
import com.comai.contextengine.context.DeviceContextManager
import com.comai.contextengine.context.RoutineDetector
import com.comai.contextengine.context.RoutineDeviationAnalyzer
import com.comai.contextengine.context.UserState
import com.comai.contextengine.contract.ContractAssembler
import com.comai.contextengine.contract.ContextContractMapper
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.data.DeviceContextSnapshot
import com.comai.contextengine.db.ComaiDatabase
import com.comai.contextengine.diagnostics.ContextLogger
import com.comai.contextengine.diagnostics.ContextMetrics
import com.comai.contextengine.models.ContextProcessingResult
import com.comai.contextengine.providers.interfaces.DeviceContextProvider
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.contextengine.repository.ContextLogRepositoryImpl
import com.comai.contextengine.repository.ContextRepositoryImpl
import com.comai.contextengine.rules.EventTriggerManager
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput
import com.comai.contextengine.service.ContextOutputListener
import com.comai.contextengine.service.ContextScheduler
import com.comai.engine.models.ContextInput

/**
 * Main Facade / API Entry Point for Person 1's RAM Module (Context Engine).
 * Consolidates device context, rule evaluation, database repositories, routine detection,
 * diagnostics, provider layer, central context engine, and contract mapping for Person 2's AI Engine.
 */
class ContextEngineFacade(
    private val context: Context,
    customProvider: DeviceContextProvider? = null
) {

    val database: ComaiDatabase by lazy { ComaiDatabase.getInstance(context) }
    val deviceContextManager: DeviceContextManager by lazy {
        if (customProvider != null) DeviceContextManager(context, customProvider) else DeviceContextManager(context)
    }
    val confidenceEvaluator: ContextConfidenceEvaluator by lazy { ContextConfidenceEvaluator() }
    val normalizer: ContextNormalizer by lazy { ContextNormalizer() }
    val routineDetector: RoutineDetector by lazy { RoutineDetector() }
    val routineDeviationAnalyzer: RoutineDeviationAnalyzer by lazy { RoutineDeviationAnalyzer() }
    val routineEngine: com.comai.contextengine.context.RoutineEngine by lazy { com.comai.contextengine.context.RoutineEngine(database) }
    val ruleEngine: RuleEngine by lazy { RuleEngine() }
    val eventTriggerManager: EventTriggerManager by lazy { EventTriggerManager() }
    val scheduler: ContextScheduler by lazy { ContextScheduler(context) }
    val logger: ContextLogger by lazy { ContextLogger(ContextLogRepositoryImpl(database)) }
    val contextRepository: ContextRepositoryImpl by lazy { ContextRepositoryImpl(deviceContextManager, database, routineDetector) }
    val centralContextEngine: CentralContextEngine by lazy {
        CentralContextEngine(context, deviceContextManager.provider, database)
    }

    /**
     * Initializes background scheduling and registers core triggers.
     */
    fun initialize() {
        scheduler.scheduleFallbackWorker()
        scheduler.scheduleBackgroundAlarm()
    }

    /**
     * Executes the central ContextEngine pipeline deterministically.
     */
    suspend fun processContext(): ContextProcessingResult {
        return centralContextEngine.processContext()
    }

    /**
     * Executes the central ContextEngine pipeline using a mock/custom provider for testing.
     */
    suspend fun processContextWithMock(mockProvider: DeviceContextProvider): ContextProcessingResult {
        val testEngine = CentralContextEngine(context, mockProvider, database)
        return testEngine.processContext()
    }

    /**
     * Retrieves the normalized structured DeviceContext model from providers.
     */
    fun getDeviceContext(): DeviceContext {
        return deviceContextManager.getDeviceContext()
    }

    /**
     * Captures a live snapshot of system & physical device context.
     */
    fun getLatestSnapshot(): DeviceContextSnapshot {
        return deviceContextManager.captureSnapshot()
    }

    /**
     * Registers a listener (such as Person 2's ContextBridge) for escalated context contracts.
     */
    fun registerOutputListener(listener: ContextOutputListener) {
        eventTriggerManager.addListener(listener)
    }

    /**
     * Unregisters an output listener.
     */
    fun unregisterOutputListener(listener: ContextOutputListener) {
        eventTriggerManager.removeListener(listener)
    }

    /**
     * Evaluates current context signals against the rule engine.
     * Returns the escalated [SharedContextContract] if a rule triggered, or null if no escalation.
     */
    suspend fun evaluateCurrentContext(): SharedContextContract? {
        ContextMetrics.recordEvaluation()
        val snapshot = getLatestSnapshot()
        val deviceContext = getDeviceContext()

        val timeStr = deviceContext.time.formattedTime
        val profile = database.userProfileDao().getUserProfile()

        val evalInput = RuleEvaluationInput(
            currentTime = timeStr,
            currentLocation = deviceContext.location.locationCategory,
            wakeTime = profile?.baselineWakeTime ?: "07:00",
            baselineWakeTime = profile?.baselineWakeTime ?: "07:00",
            medicationFlag = profile?.medicationFlag ?: true,
            historicalOfficeDepartureTime = profile?.historicalOfficeDepartureTime ?: "17:30"
        )

        val result = ruleEngine.evaluate(evalInput)

        if (result != null) {
            val userState = UserState(
                activityType = deviceContext.activity.activityType,
                awakeDurationMinutes = 15,
                wakeDetectedTimeMs = System.currentTimeMillis() - (15 * 60 * 1000L)
            )
            val (contract, jsonOutput) = ContractAssembler.assemble(result, userState)

            ContextMetrics.recordEscalation()
            logger.logContractEscalation(contract)
            eventTriggerManager.triggerEvent(jsonOutput, contract)
            return contract
        } else {
            logger.logEvaluation(
                task = "NONE",
                confidence = snapshot.freshnessScore.toDouble(),
                signalsSummary = "Battery: ${snapshot.batteryLevel}%, ScreenOn: ${snapshot.isScreenOn}, Network: ${snapshot.networkType}",
                isEscalated = false
            )
            return null
        }
    }

    /**
     * Converts a Person 1 [SharedContextContract] into Person 2's [ContextInput] AI boundary object.
     */
    fun getContextInputForAIEngine(contract: SharedContextContract): ContextInput {
        return ContextContractMapper.toContextInput(contract)
    }
}
