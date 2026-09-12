package com.comai.contextengine.contract

import com.comai.contextengine.context.ContextNormalizer
import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import com.comai.contextengine.providers.mock.MockProviderFactory
import com.google.gson.JsonParser

/**
 * Verification test harness for Context Normalization, Deduplication, and Compression into SharedContextContract.
 */
object ContextNormalizationTest {

    fun runVerification(): Boolean {
        val normalizer = ContextNormalizer()

        // 1. Verify Normalization & Deduplication
        val rawLoc = "   tech hub office   "
        val normLoc = normalizer.normalizeLocation(rawLoc)
        assert(normLoc == "Office")

        val rawSignals = listOf("Home", "home", "  Office ", "office", "COMMUTE")
        val deduplicated = normalizer.deduplicateSignals(rawSignals)
        assert(deduplicated.size == 3)
        assert(deduplicated.contains("Home") && deduplicated.contains("Office") && deduplicated.contains("COMMUTE"))

        // 2. Compress context into SharedContextContract
        val mockComposite = MockProviderFactory.createMockComposite()
        val rawContext = normalizer.normalizeDeviceContext(mockComposite.getContextData())

        val userContext = UserContext(broadContextTag = "OFFICE_LATE_DEPARTURE", locationPlaceName = "Office")
        val routineContext = RoutineContext(isRoutineDeviation = true, deviationMinutes = 90)

        val (contract, jsonStr) = ContextCompressor.compress(rawContext, userContext, routineContext, null)

        // 3. Verify JSON Schema matches EXACT required structure:
        // { "system_role", "user_state", "context_signals": { "time", "location", "routine_deviation" }, "retrieved_data", "task", "constraints" }
        val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

        val hasSystemRole = jsonObj.has("system_role")
        val hasUserState = jsonObj.has("user_state")
        val hasContextSignals = jsonObj.has("context_signals")
        val hasRetrievedData = jsonObj.has("retrieved_data")
        val hasTask = jsonObj.has("task")
        val hasConstraints = jsonObj.has("constraints")

        val signalsObj = jsonObj.getAsJsonObject("context_signals")
        val hasTime = signalsObj.has("time")
        val hasLocation = signalsObj.has("location")
        val hasRoutineDeviation = signalsObj.has("routine_deviation")

        // 4. Verify ContextContract -> ContextInput boundary mapping for Person 2
        val contextInput = ContextContractMapper.toContextInput(contract)
        val validBoundary = contextInput.task == contract.task &&
                contextInput.contextSignals.time == contract.contextSignals.time &&
                contextInput.contextSignals.routineDeviation == contract.contextSignals.routineDeviation

        val isSuccess = hasSystemRole && hasUserState && hasContextSignals &&
                hasRetrievedData && hasTask && hasConstraints &&
                hasTime && hasLocation && hasRoutineDeviation && validBoundary

        println("=== CONTEXT NORMALIZATION & COMPRESSION TEST ===")
        println("Success: $isSuccess")
        println("Generated Shared JSON Contract:\n$jsonStr")

        return isSuccess
    }
}
