package com.comai.contextengine

import com.comai.contextengine.contract.ContractFixtures
import com.comai.contextengine.contract.ContractValidator
import com.comai.contextengine.contract.InvalidContractException
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput
import com.comai.contextengine.rules.RuleTriggerType
import com.comai.contextengine.testharness.ContextTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextEngineTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        ruleEngine.loadDefaultMvpRules()
    }

    @Test
    fun testWakeTimeDeviationTrigger() {
        val input = RuleEvaluationInput(
            currentTime = "09:00",
            wakeTime = "08:45",
            baselineWakeTime = "07:00",
            isWorkDay = true
        )
        val result = ruleEngine.evaluate(input)
        assertNotNull(result)
        assertEquals(RuleTriggerType.WAKE_TIME_DEVIATION, result?.triggeredRule?.triggerType)
        assertTrue(result?.contextSignals?.routineDeviation == true)
    }

    @Test
    fun testTribeFinderNonWorkDayTrigger() {
        val input = RuleEvaluationInput(
            currentTime = "18:30",
            dayOfWeek = "SATURDAY",
            isWorkDay = false,
            currentLocation = "Downtown"
        )
        val result = ruleEngine.evaluate(input)
        assertNotNull(result)
        assertEquals(RuleTriggerType.TRIBE_FINDER_LOOKUP, result?.triggeredRule?.triggerType)
        assertTrue(result?.retrievedDataLookupRequired == true)
    }

    @Test
    fun testContractValidationSuccess() {
        val validContract = ContractFixtures.FIXTURE_MORNING_WAKE_DEVIATION
        val json = ContractValidator.toJson(validContract)
        assertTrue(json.contains("MORNING_ROUTINE_REMINDER"))
        assertTrue(json.contains("system_role"))
    }

    @Test(expected = InvalidContractException::class)
    fun testContractValidationFailureOnBlankField() {
        val invalidContract = SharedContextContract(
            systemRole = "",
            userState = "awake",
            contextSignals = com.comai.contextengine.contract.ContextSignals("09:00", "Home", true),
            task = "TASK",
            constraints = "CONSTRAINTS"
        )
        ContractValidator.validate(invalidContract)
    }

    @Test
    fun testFullTestHarnessSimulationSequence() {
        val harness = ContextTestHarness()
        val outputs = harness.runFullSimulationSequence()
        assertEquals(4, outputs.size)
    }
}
