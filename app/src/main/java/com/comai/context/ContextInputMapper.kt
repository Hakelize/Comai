package com.comai.context

import com.comai.contextengine.contract.SharedContextContract
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals as EngineContextSignals
import com.comai.contextengine.contract.ContextSignals as ContractContextSignals

/**
 * Adapter mapping the Context Engine's [SharedContextContract] to the AI Engine's [ContextInput].
 */
object ContextInputMapper {

    /**
     * Converts a [SharedContextContract] from the Context Engine into a [ContextInput] for the AI Engine.
     */
    fun toContextInput(contract: SharedContextContract): ContextInput {
        return ContextInput(
            systemRole = contract.systemRole.ifBlank { "Comai — a warm, proactive AI life companion" },
            userState = contract.userState,
            contextSignals = EngineContextSignals(
                time = contract.contextSignals.time,
                location = contract.contextSignals.location,
                routineDeviation = contract.contextSignals.routineDeviation
            ),
            retrievedData = contract.retrievedData,
            task = contract.task,
            constraints = contract.constraints.ifBlank { "under 30 words, warm tone" }
        )
    }

    /**
     * Converts a [ContextInput] into a [SharedContextContract] if needed for roundtrip serialization.
     */
    fun toSharedContextContract(input: ContextInput): SharedContextContract {
        return SharedContextContract(
            systemRole = input.systemRole,
            userState = input.userState,
            contextSignals = ContractContextSignals(
                time = input.contextSignals.time,
                location = input.contextSignals.location,
                routineDeviation = input.contextSignals.routineDeviation
            ),
            retrievedData = input.retrievedData,
            task = input.task,
            constraints = input.constraints
        )
    }
}
