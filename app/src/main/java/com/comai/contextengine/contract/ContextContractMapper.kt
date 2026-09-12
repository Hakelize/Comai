package com.comai.contextengine.contract

import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals as EngineContextSignals
import com.comai.contextengine.contract.ContextSignals as ContractContextSignals

/**
 * Contract Mapper providing clean boundary conversion between Person 1's [SharedContextContract]
 * and Person 2's [ContextInput] AI Engine object.
 */
object ContextContractMapper {

    /**
     * Maps Person 1's [SharedContextContract] into Person 2's [ContextInput].
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
     * Maps Person 2's [ContextInput] into Person 1's [SharedContextContract].
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
