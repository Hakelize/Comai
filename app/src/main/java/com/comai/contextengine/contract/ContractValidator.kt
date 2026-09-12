package com.comai.contextengine.contract

import com.google.gson.Gson
import com.google.gson.GsonBuilder

class InvalidContractException(message: String) : Exception(message)

object ContractValidator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun validate(contract: SharedContextContract): SharedContextContract {
        if (contract.systemRole.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'system_role' cannot be blank.")
        }
        if (contract.userState.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'user_state' cannot be blank.")
        }
        if (contract.contextSignals.time.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'context_signals.time' cannot be blank.")
        }
        if (contract.contextSignals.location.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'context_signals.location' cannot be blank.")
        }
        if (contract.task.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'task' cannot be blank.")
        }
        if (contract.constraints.isBlank()) {
            throw InvalidContractException("Contract Validation Error: 'constraints' cannot be blank.")
        }
        return contract
    }

    fun toJson(contract: SharedContextContract): String {
        validate(contract)
        return gson.toJson(contract)
    }
}
