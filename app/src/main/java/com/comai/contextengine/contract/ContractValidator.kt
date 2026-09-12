package com.comai.contextengine.contract

import com.comai.engine.models.ContextInput
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

class InvalidContractException(message: String) : Exception(message)

object ContractValidator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

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

    fun validateContextInput(input: ContextInput): ContextInput {
        if (input.systemRole.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'system_role' cannot be blank.")
        if (input.userState.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'user_state' cannot be blank.")
        if (input.contextSignals.time.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'context_signals.time' cannot be blank.")
        if (input.contextSignals.location.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'context_signals.location' cannot be blank.")
        if (input.task.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'task' cannot be blank.")
        if (input.constraints.isBlank()) throw InvalidContractException("ContextInput Validation Error: 'constraints' cannot be blank.")
        return input
    }

    fun toJson(contract: SharedContextContract): String {
        validate(contract)
        return gson.toJson(contract)
    }

    fun fromJson(jsonStr: String): SharedContextContract {
        return try {
            val contract = gson.fromJson(jsonStr, SharedContextContract::class.java)
                ?: throw InvalidContractException("Deserialized contract object is null.")
            validate(contract)
        } catch (e: JsonSyntaxException) {
            throw InvalidContractException("Malformed JSON contract syntax: ${e.message}")
        }
    }
}
