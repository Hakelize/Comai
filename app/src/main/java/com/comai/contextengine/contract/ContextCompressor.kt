package com.comai.contextengine.contract

import com.comai.contextengine.models.RoutineContext
import com.comai.contextengine.models.UserContext
import com.comai.contextengine.providers.models.DeviceContext
import com.comai.contextengine.rules.RuleEvaluationResult

/**
 * ContextCompressor - Normalizes, deduplicates, filters, and compresses complex device signals into a compact SharedContextContract.
 * Enforces the exact required JSON schema structure for Person 2's AIEngine:
 * {
 *   "system_role": "string",
 *   "user_state": "string",
 *   "context_signals": {
 *     "time": "string",
 *     "location": "string",
 *     "routine_deviation": boolean
 *   },
 *   "retrieved_data": "string or null",
 *   "task": "string",
 *   "constraints": "string"
 * }
 */
object ContextCompressor {

    /**
     * Compresses device context, user context, routine context, and optional rule evaluation result
     * into a validated [SharedContextContract] and serialized JSON string.
     */
    fun compress(
        deviceContext: DeviceContext,
        userContext: UserContext,
        routineContext: RoutineContext,
        ruleResult: RuleEvaluationResult?,
        retrievedData: String? = null
    ): Pair<SharedContextContract, String> {
        val systemRole = ruleResult?.systemRole ?: "Comai Proactive Context Engine"

        // Build compressed, deduplicated user_state string
        val userStateStr = buildCompressedUserState(deviceContext, userContext)

        val task = ruleResult?.task ?: determineTaskForContext(userContext, routineContext)
        val constraints = ruleResult?.constraints ?: "Keep response empathetic, concise (max 2 sentences)."

        val signals = ContextSignals(
            time = deviceContext.time.formattedTime,
            location = deviceContext.location.locationCategory,
            routineDeviation = routineContext.isRoutineDeviation
        )

        val contract = SharedContextContract(
            systemRole = systemRole,
            userState = userStateStr,
            contextSignals = signals,
            retrievedData = retrievedData ?: ruleResult?.retrievedDataCategory,
            task = task,
            constraints = constraints
        )

        val jsonStr = ContractValidator.toJson(contract)
        return Pair(contract, jsonStr)
    }

    /**
     * Compacts user state into a deduplicated, decision-useful summary string.
     */
    private fun buildCompressedUserState(
        deviceContext: DeviceContext,
        userContext: UserContext
    ): String {
        val activity = deviceContext.activity.detectedState.name
        val broadTag = userContext.broadContextTag
        val place = deviceContext.location.locationCategory
        val audio = deviceContext.audio.primaryOutputDevice.name
        val base = "BroadContext: $broadTag | Place: $place | Activity: $activity | Audio: $audio"
        val dig = deviceContext.digitalActivity
        return if (dig != null && dig.isAvailable) {
            "$base | DigitalActivity: ${dig.activityState} (conf=${dig.confidence}, reason=${dig.reason})"
        } else {
            base
        }
    }

    private fun determineTaskForContext(
        userContext: UserContext,
        routineContext: RoutineContext
    ): String {
        return when (userContext.broadContextTag) {
            "OFFICE_LATE_DEPARTURE" -> "OVERTIME_CHECKIN"
            "HOME_MORNING" -> "MORNING_ROUTINE_REMINDER"
            "LUNCH_BREAK" -> "LUNCH_CHECKIN"
            "NIGHT_SLEEP" -> "NIGHT_MEDICATION_REMINDER"
            else -> if (routineContext.isRoutineDeviation) "ROUTINE_DEVIATION_ALERT" else "GENERAL_CHECKIN"
        }
    }
}
