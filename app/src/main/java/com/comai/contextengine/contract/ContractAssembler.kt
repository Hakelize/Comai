package com.comai.contextengine.contract

import com.comai.contextengine.context.UserState
import com.comai.contextengine.rules.RuleEvaluationResult

object ContractAssembler {

    fun assemble(
        ruleResult: RuleEvaluationResult,
        userState: UserState,
        retrievedData: String? = null
    ): Pair<SharedContextContract, String> {
        val contract = SharedContextContract(
            systemRole = ruleResult.systemRole,
            userState = userState.toContractSummary(),
            contextSignals = ruleResult.contextSignals,
            retrievedData = retrievedData,
            task = ruleResult.task,
            constraints = ruleResult.constraints
        )

        ContractValidator.validate(contract)
        val jsonOutput = ContractValidator.toJson(contract)

        return Pair(contract, jsonOutput)
    }
}
